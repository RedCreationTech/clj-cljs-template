#!/usr/bin/env bash
# 把模板的命名空间 com.ruoyi 与项目名 rouyi 改成你自己的。
#
# 用法:
#   ./scripts/rename-project.sh <new-namespace> <new-project-name>
#   ./scripts/rename-project.sh com.acme.myapp myapp
#
# 做的事(全部是确定性的文本替换 + 目录移动,可用 git diff 审阅):
#   1. 源码/配置/文档里的  com.ruoyi  -> <new-namespace>
#   2. 路径形式            com/ruoyi  -> <new-namespace 的目录形式>(- 转 _)
#   3. 项目名/产物名        rouyi      -> <new-project-name>(jar、db、package.json、日志文案、localStorage 键)
#   4. localStorage 键前缀  ruoyi_     -> <new-project-name>_
#   5. 移动 src/clj、src/cljs、test/clj、env/*/clj 下的 com/ruoyi 目录
#
# 不会改动: RuoYi-Vue 等对参考项目的引用、node_modules、.git、docs/training(培训教材按原样保留)。
set -euo pipefail

NEW_NS="${1:-}"
NEW_NAME="${2:-}"
if [[ -z "$NEW_NS" || -z "$NEW_NAME" ]]; then
  echo "用法: $0 <new-namespace> <new-project-name>   例: $0 com.acme.myapp myapp" >&2
  exit 1
fi
if [[ ! "$NEW_NS" =~ ^[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*)+$ ]]; then
  echo "命名空间需形如 com.acme.myapp(小写字母/数字/连字符,至少两段)" >&2
  exit 1
fi
if [[ ! "$NEW_NAME" =~ ^[a-z][a-z0-9-]*$ ]]; then
  echo "项目名需形如 myapp / my-app(小写字母/数字/连字符)" >&2
  exit 1
fi

OLD_NS="com.ruoyi"
OLD_PATH="com/ruoyi"
OLD_NAME="rouyi"
OLD_KEY_PREFIX="ruoyi_"
NEW_PATH="$(echo "$NEW_NS" | tr '.' '/' | tr '-' '_')"
NEW_KEY_PREFIX="$(echo "$NEW_NAME" | tr '-' '_')_"

cd "$(dirname "$0")/.."
ROOT="$(pwd)"
echo "项目根目录: $ROOT"
echo "  $OLD_NS  -> $NEW_NS"
echo "  $OLD_PATH -> $NEW_PATH"
echo "  $OLD_NAME -> $NEW_NAME"

# ── 1. 文本替换 ────────────────────────────────────────────────────
# 只处理源码/配置/文档;跳过 node_modules、.git、构建产物与培训教材。
# 用 perl 做原地替换(macOS 与 Linux 的 sed -i 语义不同,perl 更稳;不用 mapfile 以兼容 macOS 自带的 bash 3.2)。
find . -type f \
  \( -name '*.clj' -o -name '*.cljs' -o -name '*.cljc' -o -name '*.edn' \
     -o -name '*.md' -o -name '*.org' -o -name '*.sh' -o -name '*.js' -o -name '*.mjs' \
     -o -name '*.html' -o -name '*.json' -o -name '*.sql' -o -name '*.xml' -o -name 'Dockerfile' -o -name 'Makefile' \) \
  -not -path './node_modules/*' -not -path './.git/*' -not -path './target/*' \
  -not -path './.shadow-cljs/*' -not -path './.cpcache/*' -not -path './resources/public/js/*' \
  -not -path './docs/training/*' -not -name 'RUOYI_VUE_COMPARISON.md' \
  -not -name 'rename-project.sh' -print0 \
  | xargs -0 perl -pi -e "s/\Q$OLD_NS\E/$NEW_NS/g; s#\Q$OLD_PATH\E#$NEW_PATH#g; s/\b\Q$OLD_KEY_PREFIX\E/$NEW_KEY_PREFIX/g; s/\b\Q$OLD_NAME\E\b/$NEW_NAME/g"

# build.clj 里的 main class 必须是 munge 后的类名(命名空间中的 - 对应类名中的 _)
NEW_MAIN_CLASS="$(echo "$NEW_NS" | tr '-' '_').core"
perl -pi -e "s/\(def main-cls \"[^\"]+\"\)/(def main-cls \"$NEW_MAIN_CLASS\")/" build.clj

# ── 2. 目录移动 ────────────────────────────────────────────────────
move_tree() {
  local base="$1"
  local src="$base/$OLD_PATH"
  local dst="$base/$NEW_PATH"
  [[ -d "$src" ]] || return 0
  mkdir -p "$(dirname "$dst")"
  if [[ -e "$dst" ]]; then
    echo "目标目录已存在,跳过移动: $dst" >&2
    return 0
  fi
  mv "$src" "$dst"
  # 清理空掉的旧父目录(例如 src/clj/com)
  local parent
  parent="$(dirname "$src")"
  while [[ "$parent" != "$base" && -d "$parent" ]] && [[ -z "$(ls -A "$parent")" ]]; do
    rmdir "$parent"
    parent="$(dirname "$parent")"
  done
  echo "  moved $src -> $dst"
}
for base in src/clj src/cljs test/clj env/dev/clj env/prod/clj env/test/clj; do
  move_tree "$base"
done

# ── 3. 收尾提示 ────────────────────────────────────────────────────
cat <<EOF

完成。建议接着执行:
  grep -rn "$OLD_NS\|$OLD_NAME" --exclude-dir=node_modules --exclude-dir=.git . | grep -v docs/training   # 应为空
  rm -f *.db && clojure -M:test
  npm install && npx shadow-cljs compile app
  # 再改 src/cljs/$NEW_PATH/frontend/config.cljs 里的 app-name / repo-url
EOF
