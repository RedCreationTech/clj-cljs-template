const fs = require('fs');

async function installFonts(page) {
  const candidates = [
    process.env.MOBILE_CJK_FONT,
    '/System/Library/Fonts/Supplemental/Arial Unicode.ttf',
    '/System/Library/Fonts/Hiragino Sans GB.ttc',
    '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc',
    'resources/public/fonts/NotoSansSC-Regular.ttf',
  ].filter(Boolean);
  const font = candidates.find(file => fs.existsSync(file));
  if (!font) return;
  const bytes = fs.readFileSync(font);
  await page.route('**fonts.gstatic.com/**', route => route.fulfill({
    body: bytes, headers: { 'content-type': 'font/ttf', 'access-control-allow-origin': '*' },
  }));
}

module.exports = { installFonts };
