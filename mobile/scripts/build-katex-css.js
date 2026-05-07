#!/usr/bin/env node
/**
 * 把 node_modules/katex/dist/katex.min.css 内容打包成
 * src/components/math/katexCss.js 的 export 字符串。
 *
 * KaTeX CDN 改为本地 bundle 后，WebView 内嵌 HTML 需要
 * 同步注入 KaTeX 主样式表才能正确显示公式。
 *
 * @font-face 块被剥离 —— inline-HTML WebView 没有相对路径基准，
 * 引用 fonts/*.woff2 会 404；剥掉后浏览器静默回落到系统字体，
 * 常规公式视觉差异极小（仅 \mathbb / \mathcal / \mathfrak 受影响）。
 *
 * 使用：node scripts/build-katex-css.js
 * 升级 katex 后重新跑一次即可。
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const SRC = path.join(ROOT, 'node_modules', 'katex', 'dist', 'katex.min.css');
const OUT = path.join(ROOT, 'src', 'components', 'math', 'katexCss.js');
const VERSION = require(path.join(ROOT, 'node_modules', 'katex', 'package.json')).version;

const css = fs.readFileSync(SRC, 'utf8');
const stripped = css.replace(/@font-face\s*\{[^}]*\}/g, '');
const banner = `/* AUTO-GENERATED — KaTeX ${VERSION} main CSS, @font-face blocks stripped`
  + ` (fonts would 404 inside inline-HTML WebView). Regenerate via`
  + ` scripts/build-katex-css.js after upgrading katex. */\n`;
const body = `export const KATEX_CSS = ${JSON.stringify(stripped)};\n`;
fs.writeFileSync(OUT, banner + body);
console.log(`✓ Wrote ${OUT} (${(banner + body).length} bytes, KaTeX ${VERSION})`);
