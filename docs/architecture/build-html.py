#!/usr/bin/env python3
"""把 c4-model.org 导出为自包含的 c4-model.html。

- mermaid 块用 mermaid-cli 渲染成 SVG 内联到页面(离线可看),源码折叠在图下方;
- sh / clojure 块在仓库根目录实际执行(跳过 :eval never),把输出作为"生成时的执行结果"附在代码块下;
- 其余内容(标题、表格、引用、行内标记)由 pandoc 从 org 转成 HTML。

依赖:pandoc、node(npx @mermaid-js/mermaid-cli)。
用法:python3 docs/architecture/build-html.py [--no-exec] [--mmdc <path>] [--puppeteer-config <json>]
"""
import argparse
import datetime as dt
import html
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
ORG = HERE / "c4-model.org"
OUT = HERE / "c4-model.html"

BLOCK_RE = re.compile(r"^#\+begin_src\s+(\S+)([^\n]*)\n(.*?)^#\+end_src", re.S | re.M | re.I)


def parse_blocks(text):
    blocks = []
    for m in BLOCK_RE.finditer(text):
        lang, args, body = m.group(1).lower(), m.group(2).strip(), m.group(3)
        blocks.append({"lang": lang, "args": args, "body": body})
    return blocks


def org_meta(text, key):
    m = re.search(rf"^#\+{key}:\s*(.+)$", text, re.M | re.I)
    return m.group(1).strip() if m else ""


def render_mermaid(mmdc, puppeteer_cfg, src, svg_id):
    with tempfile.TemporaryDirectory() as td:
        inp = Path(td) / "d.mmd"
        outp = Path(td) / "d.svg"
        inp.write_text(src, encoding="utf-8")
        cmd = [*mmdc, "-i", str(inp), "-o", str(outp), "-b", "transparent", "-I", svg_id]
        if puppeteer_cfg:
            cmd += ["-p", str(puppeteer_cfg)]
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=180)
        if r.returncode != 0 or not outp.exists():
            return None, (r.stderr or r.stdout)[-800:]
        svg = outp.read_text(encoding="utf-8")
        svg = re.sub(r"^<\?xml[^>]*\?>\s*", "", svg)
        return svg, ""


def run_block(lang, args, body, timeout):
    """在仓库根目录执行 sh 块;返回 (stdout+stderr, returncode)。"""
    if lang != "sh":
        return None, None
    if ":eval never" in args:
        return None, None
    r = subprocess.run(["bash", "-lc", body], cwd=ROOT, capture_output=True, text=True, timeout=timeout)
    out = (r.stdout + ("\n" + r.stderr if r.stderr.strip() else "")).strip()
    return out, r.returncode


def pandoc_body(org_text):
    """pandoc 会按 :exports results 把 mermaid 块整个丢掉,先把它们改成 :exports code 让源码进入 HTML,再由本脚本换成 SVG;
    紧跟其后的 #+RESULTS: 图片链接(给 Emacs 导出用的缓存)则去掉,避免图出现两次。"""
    pre = re.sub(r"^(#\+begin_src mermaid[^\n]*):exports results", r"\1:exports code", org_text, flags=re.M | re.I)
    pre = re.sub(r"^#\+RESULTS:[^\n]*\n\[\[file:[^\]]+\]\]\n?", "", pre, flags=re.M)
    with tempfile.NamedTemporaryFile("w", suffix=".org", dir=HERE, delete=False, encoding="utf-8") as f:
        f.write(pre)
        tmp = Path(f.name)
    try:
        r = subprocess.run(
            ["pandoc", "-f", "org", "-t", "html5", "--toc", "--toc-depth=3", "--wrap=none",
             "--standalone", "--template", str(HERE / "_pandoc-template.html"), str(tmp)],
            capture_output=True, text=True, check=True)
    finally:
        tmp.unlink(missing_ok=True)
    return r.stdout


TEMPLATE = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>$title$</title>
<meta name="generator" content="pandoc + mermaid-cli (docs/architecture/build-html.py)">
<!--CSS-->
</head>
<body>
<header class="hero">
  <h1 class="title">$title$</h1>
  <p class="meta">$author$ · $date$ · <span class="gen">HTML 生成于 <!--GEN--></span></p>
</header>
<div class="layout">
<nav id="toc"><div class="toc-title">目录</div>
$toc$
</nav>
<main id="content">
$body$
</main>
</div>
</body>
</html>
"""

CSS = """
<style>
:root{--fg:#1f2328;--muted:#59636e;--bg:#fff;--panel:#f6f8fa;--line:#d0d7de;--accent:#0969da;--ok:#1a7f37;--warn:#9a6700;--code:#f6f8fa;}
@media (prefers-color-scheme: dark){:root{--fg:#e6edf3;--muted:#9198a1;--bg:#0d1117;--panel:#161b22;--line:#30363d;--accent:#4493f8;--ok:#3fb950;--warn:#d29922;--code:#161b22;} figure.diagram{background:#fff;color:#1f2328;border-radius:8px;}}
*{box-sizing:border-box}
body{margin:0;font:16px/1.7 -apple-system,BlinkMacSystemFont,"PingFang SC","Hiragino Sans GB","Microsoft YaHei","Segoe UI",Roboto,sans-serif;color:var(--fg);background:var(--bg)}
.hero{padding:32px 24px 8px;border-bottom:1px solid var(--line)}
.hero .title{margin:0;font-size:28px;line-height:1.3}
.hero .meta{margin:8px 0 0;color:var(--muted);font-size:14px}
.layout{display:grid;grid-template-columns:280px minmax(0,1fr);gap:0;max-width:1440px;margin:0 auto}
#toc{position:sticky;top:0;align-self:start;max-height:100vh;overflow:auto;padding:16px 8px 16px 24px;border-right:1px solid var(--line);font-size:13.5px}
#toc .toc-title{font-weight:600;margin:0 0 6px;color:var(--muted);font-size:12px;letter-spacing:.06em;text-transform:uppercase}
#toc ul{list-style:none;padding-left:0;margin:0}
#toc ul ul{padding-left:14px}
#toc li{margin:2px 0}
#toc a{color:var(--fg);text-decoration:none;display:block;padding:2px 6px;border-radius:4px}
#toc a:hover{background:var(--panel);color:var(--accent)}
#content{padding:16px 40px 80px;min-width:0}
h1,h2,h3{line-height:1.3;scroll-margin-top:12px}
h1{font-size:24px;margin:48px 0 12px;padding-bottom:6px;border-bottom:2px solid var(--line)}
h2{font-size:19px;margin:32px 0 10px}
h3{font-size:16px;margin:20px 0 8px}
a{color:var(--accent)}
code{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,"Noto Sans Mono CJK SC",monospace;font-size:.9em;background:var(--code);padding:1px 5px;border-radius:4px;border:1px solid var(--line)}
pre{background:var(--code);border:1px solid var(--line);border-radius:8px;padding:12px 14px;overflow:auto;font-size:13px;line-height:1.55}
pre code{background:none;border:0;padding:0;font-size:inherit}
blockquote{margin:16px 0;padding:10px 18px;border-left:4px solid var(--accent);background:var(--panel);border-radius:0 8px 8px 0}
table{border-collapse:collapse;width:100%;margin:12px 0 18px;font-size:14px}
th,td{border:1px solid var(--line);padding:6px 10px;vertical-align:top;text-align:left}
th{background:var(--panel)}
tr:nth-child(even) td{background:color-mix(in srgb,var(--panel) 50%,transparent)}
figure.diagram{margin:16px 0 24px;padding:12px;border:1px solid var(--line);border-radius:8px;background:#fff;overflow:auto}
figure.diagram svg{max-width:100%;height:auto;display:block;margin:0 auto}
figure.diagram details{margin-top:8px;color:var(--muted);font-size:13px}
figure.diagram details pre{margin-top:6px}
.src{margin:12px 0 20px}
.src .head{display:flex;gap:10px;align-items:center;font-size:12px;color:var(--muted);margin:0 0 -1px 0}
.badge{display:inline-block;padding:1px 8px;border-radius:999px;border:1px solid var(--line);background:var(--panel);font-size:11.5px;letter-spacing:.02em}
.badge.ok{color:var(--ok);border-color:var(--ok)}
.badge.warn{color:var(--warn);border-color:var(--warn)}
.result{border:1px dashed var(--line);border-radius:8px;padding:8px 14px;margin-top:6px;background:var(--panel)}
.result .label{font-size:12px;color:var(--muted);margin-bottom:4px}
.result pre{margin:0;background:transparent;border:0;padding:0;white-space:pre-wrap;word-break:break-all}
.diag-error{color:#b3261e;font-size:13px}
@media (max-width: 960px){.layout{grid-template-columns:1fr}#toc{position:static;max-height:none;border-right:0;border-bottom:1px solid var(--line)}#content{padding:16px 16px 60px}}
@media print{#toc{display:none}.layout{display:block}figure.diagram{break-inside:avoid}}
</style>
"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-exec", action="store_true", help="不执行 sh 块,只渲染图")
    ap.add_argument("--mmdc", default="", help="mermaid-cli 可执行文件;默认 npx @mermaid-js/mermaid-cli")
    ap.add_argument("--puppeteer-config", default="", help="传给 mmdc -p 的 puppeteer 配置")
    ap.add_argument("--timeout", type=int, default=600, help="单个 sh 块的超时秒数")
    a = ap.parse_args()

    if not shutil.which("pandoc"):
        sys.exit("需要 pandoc")
    mmdc = a.mmdc.split() if a.mmdc else ["npx", "-y", "@mermaid-js/mermaid-cli"]

    text = ORG.read_text(encoding="utf-8")
    blocks = parse_blocks(text)
    title = org_meta(text, "TITLE")
    author = org_meta(text, "AUTHOR")
    date = org_meta(text, "DATE")

    tpl_path = HERE / "_pandoc-template.html"
    tpl_path.write_text(TEMPLATE, encoding="utf-8")
    try:
        page = pandoc_body(text)
    finally:
        tpl_path.unlink(missing_ok=True)

    # pandoc 把每个 src 块渲染成 <div class="sourceCode" ...><pre class="sourceCode LANG"><code ...>...</code></pre></div>
    # 已知语言:<div class="sourceCode" ...><pre class="sourceCode bash"><code>…</code></pre></div>
    # 未知语言(mermaid):<pre class="mermaid" ...><code>…</code></pre>
    pre_re = re.compile(
        r'<div class="sourceCode"[^>]*>\s*<pre class="sourceCode [\w-]+"><code[^>]*>.*?</code></pre>\s*</div>'
        r'|<pre class="mermaid"[^>]*><code[^>]*>.*?</code></pre>', re.S)
    found = pre_re.findall(page)
    if len(found) != len(blocks):
        sys.exit(f"块数不一致:org {len(blocks)} vs pandoc {len(found)};请检查 org 文件")

    counter = {"i": 0}
    diag_n = {"i": 0}

    def replace(m):
        i = counter["i"]
        counter["i"] += 1
        blk = blocks[i]
        lang, args, body = blk["lang"], blk["args"], blk["body"]
        original = m.group(0)
        if lang == "mermaid":
            diag_n["i"] += 1
            svg_id = f"c4-diagram-{diag_n['i']}"
            svg, err = render_mermaid(mmdc, a.puppeteer_config or None, body, svg_id)
            fm = re.search(r":file\s+(\S+)", args)
            if svg and fm:  # 同步写出 :file 指向的 svg,供 Emacs/pandoc 直接导出时引用
                target = (HERE / fm.group(1)).resolve()
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(svg, encoding="utf-8")
            src_html = f"<details><summary>mermaid 源码</summary><pre><code>{html.escape(body)}</code></pre></details>"
            if svg:
                return f'<figure class="diagram">{svg}{src_html}</figure>'
            return f'<figure class="diagram"><p class="diag-error">图渲染失败:{html.escape(err)}</p>{src_html}</figure>'
        # sh / clojure / emacs-lisp
        head = [f'<span class="badge">{html.escape(lang)}</span>']
        if ":eval never" in args:
            head.append('<span class="badge warn">:eval never — 只可手动执行</span>')
        elif lang == "sh" and not a.no_exec:
            head.append('<span class="badge ok">生成时已执行</span>')
        result_html = ""
        if lang == "sh" and not a.no_exec and ":eval never" not in args:
            print(f"  执行 sh 块 #{i+1} …", file=sys.stderr, flush=True)
            try:
                out, rc = run_block(lang, args, body, a.timeout)
            except subprocess.TimeoutExpired:
                out, rc = "(超时)", -1
            label = "输出" if rc == 0 else f"输出(exit {rc})"
            result_html = f'<div class="result"><div class="label">{label}</div><pre>{html.escape(out or "(无输出)")}</pre></div>'
        return f'<div class="src"><div class="head">{"".join(head)}</div>{original}{result_html}</div>'

    page = pre_re.sub(replace, page)
    page = page.replace("<!--CSS-->", CSS)
    page = page.replace("<!--GEN-->", dt.datetime.now().strftime("%Y-%m-%d %H:%M"))
    OUT.write_text(page, encoding="utf-8")
    print(f"写入 {OUT.relative_to(ROOT)}({OUT.stat().st_size // 1024} KB),图 {diag_n['i']} 张,代码块 {len(blocks)} 个", file=sys.stderr)


if __name__ == "__main__":
    main()
