import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { homedir, tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

// 复用正式教程素材，绘制首次进入时的节点页和完整文档预览。
const output = dirname(fileURLToPath(import.meta.url));
const assets = join(output, '../src/main/assets/welcome/zh-CN');
const content = JSON.parse(readFileSync(join(assets, 'document.json'), 'utf8'));
// 渲染器不支持直接嵌入 WebP，临时解码后再绘制，正式产物统一输出 WebP。
const imageTemporary = mkdtempSync(join(tmpdir(), 'picnote-welcome-image-'));
let picture;
try {
  const decoded = join(imageTemporary, 'launcher.png');
  execFileSync(join(homedir(), 'bin/pastehub-image'), [
    '--input', join(assets, content.imageFileName), '--output', decoded, '--format', 'png',
  ]);
  picture = readFileSync(decoded).toString('base64');
} finally {
  rmSync(imageTemporary, { recursive: true, force: true });
}
const require = createRequire(import.meta.url);
const moduleRoot = process.env.PICNOTE_RENDER_MODULE_ROOT;
const { Resvg } = require(moduleRoot ? join(moduleRoot, '@resvg/resvg-js') : '@resvg/resvg-js');
const C = { ink: '#1F2926', muted: '#748078', line: '#DFE6E1', paper: '#FAFAF8', green: '#2E7D5B' };
const esc = value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
const rect = (x, y, w, h, fill, r = 0, stroke = 'none') => `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}" fill="${fill}" stroke="${stroke}"/>`;
const text = (x, y, value, size = 16, fill = C.ink, weight = 400) => `<text x="${x}" y="${y}" font-size="${size}" fill="${fill}" font-weight="${weight}">${esc(value)}</text>`;
const wrap = (value, count) => Array.from(value).reduce((rows, char, index) => {
  if (index % count === 0) rows.push('');
  rows[rows.length - 1] += char;
  return rows;
}, []);
const lines = (x, y, values, size = 16, fill = C.ink, step = 26) => values.map((value, i) => text(x, y + i * step, value, size, fill)).join('');
const image = (x, y, w, h, crop = false) => `<image x="${x}" y="${y}" width="${w}" height="${h}" preserveAspectRatio="xMidYMid ${crop ? 'slice' : 'meet'}" href="data:image/png;base64,${picture}"/>`;

function header(tab) {
  return text(24, 31, '9:41', 14, C.ink, 600) + text(338, 31, '5G  ▰', 13) +
    text(23, 79, '☰', 24) + text(61, 78, content.title, 20, C.ink, 500) + text(376, 79, '⋯', 26) +
    text(93, 125, '节点', 16, tab === 0 ? C.green : C.muted, 500) +
    text(303, 125, '文档', 16, tab === 1 ? C.green : C.muted, 500) +
    rect(tab * 210 + 91, 137, 30, 3, C.green, 1.5) + rect(0, 145, 420, 1, C.line);
}

function footer() {
  return rect(0, 1135, 420, 65, C.paper) + rect(16, 1135, 388, 1, C.line) +
    text(24, 1172, '相册', 15, C.green) + rect(272, 1146, 128, 43, '#DDEBDD', 22) +
    text(288, 1174, '＋ 添加图片', 15, C.green, 500) + rect(150, 1216, 120, 4, C.ink, 2);
}

function nodeCard(y, body, kind = 'text') {
  return `<circle cx="28" cy="${y + 36}" r="4" fill="${C.paper}" stroke="#9EADA0"/>` +
    rect(52, y, 352, 72, kind === 'chapter' ? '#E5EDDF' : '#F0F3EF', 12) +
    lines(kind === 'introduction' ? 90 : 66, y + 29, body, 15, kind === 'introduction' ? C.muted : C.ink, 24);
}

function nodes() {
  return header(0) + '<path d="M28 206V728" stroke="#D5DFD6" stroke-width="1"/>' +
    nodeCard(170, wrap(content.introduction, 18).slice(0, 2).map((line, i) => i === 1 ? `${line.slice(0, 17)}…` : line), 'introduction') +
    `<circle cx="28" cy="402" r="4" fill="${C.paper}" stroke="#9EADA0"/>` +
    '<defs><clipPath id="welcome-node-image"><rect x="52" y="270" width="352" height="264" rx="12"/></clipPath></defs>' +
    `<g clip-path="url(#welcome-node-image)">${image(52, 270, 352, 264, true)}</g>` +
    nodeCard(562, [content.instruction]) + nodeCard(662, [content.todoTitle], 'chapter') + footer();
}

function document() {
  const pictureY = 400;
  const pictureHeight = 328 * content.imageHeight / content.imageWidth;
  return header(1) + text(24, 197, content.title, 27, C.ink, 600) +
    lines(48, 240, wrap(content.introduction, 22), 15, C.muted, 25) +
    image(46, pictureY, 328, pictureHeight) +
    text(24, pictureY + pictureHeight + 40, content.instruction, 16) +
    text(24, pictureY + pictureHeight + 97, content.todoTitle, 23, C.ink, 600) + footer();
}

function phone(x, label, body) {
  return text(x + 8, 151, label, 22, C.ink, 500) +
    `<g transform="translate(${x} 180)">${rect(0, 0, 420, 1240, C.paper, 30, '#D0DAD2')}${body}</g>`;
}

const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1500" viewBox="0 0 1080 1500">
  <rect width="1080" height="1500" fill="#E9EEEA"/>
  <g font-family="Hiragino Sans GB, PingFang SC, sans-serif">
    ${text(64, 64, '竹节笔记 · 首次使用文档', 34, C.ink, 600)}
    ${text(64, 100, '首次进入即有内容；四个可编辑节点组成同一篇教程。', 18, C.muted)}
    ${phone(64, '节点页', nodes())}
    ${phone(596, '文档页（完整内容示意，可滚动）', document())}
    ${text(64, 1468, '系统中文使用“竹节笔记”配图，其他语言使用“Bambootes”配图；后续内容留在 TODO 章节。', 16, C.muted)}
  </g>
</svg>`;
const renderer = new Resvg(svg, { font: { loadSystemFonts: true }, fitTo: { mode: 'original' } });
const temporary = mkdtempSync(join(tmpdir(), 'picnote-welcome-prototype-'));
try {
  const raster = join(temporary, 'prototype.png');
  writeFileSync(raster, renderer.render().asPng());
  execFileSync(join(homedir(), 'bin/pastehub-image'), [
    '--input', raster, '--output', join(output, '11-首次使用文档.webp'),
    '--format', 'webp', '--quality', '90',
  ], { stdio: 'inherit' });
} finally {
  rmSync(temporary, { recursive: true, force: true });
}
