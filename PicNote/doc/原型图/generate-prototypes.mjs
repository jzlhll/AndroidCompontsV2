import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// 从独立 SVG 源生成升级原型，复用正式竹枝素材；不依赖 Android 构建。
const output = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const moduleRoot = process.env.PICNOTE_RENDER_MODULE_ROOT;
const { Resvg } = require(moduleRoot ? join(moduleRoot, '@resvg/resvg-js') : '@resvg/resvg-js');
// resvg 不直接解码 WebP，在临时目录用系统工具转为 PNG 后内嵌。
const temporary = mkdtempSync(join(tmpdir(), 'picnote-upgrade-prototype-'));
let bamboo;
try {
  const converted = join(temporary, 'node-bamboo.png');
  execFileSync('sips', ['-s', 'format', 'png', resolve(output, '../../src/main/res/drawable-nodpi/node_bamboo_long_branch.webp'), '--out', converted], { stdio: 'ignore' });
  bamboo = readFileSync(converted).toString('base64');
} finally {
  rmSync(temporary, { recursive: true, force: true });
}
const C = {
  paper: '#F8F9F7', ink: '#262B27', muted: '#5D6A62', line: '#D0DED2',
  panel: '#E3ECE4', selected: '#D5E5D6', green: '#326E40', white: '#FFFFFF',
  title: '#735084', titleBg: '#EDE4F0', intro: '#365F82', introBg: '#E1EBF2',
  introText: '#666B65', quote: '#E9ECE6', chapter: '#865D24', chapterBg: '#F2E9D7',
  body: '#365F41', bodyBg: '#E1EBDF', parsed: '#2F6B45', parsing: '#365F82',
  pending: '#8A5B1F', canvas: '#E8EEE8',
};
const esc = v => String(v).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
const rect = (x, y, w, h, fill, r = 0, stroke = '', extra = '') => `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}" fill="${fill}" ${stroke ? `stroke="${stroke}"` : ''} ${extra}/>`;
const text = (x, y, value, size = 14, fill = C.ink, weight = 400, anchor = 'start', extra = '') => `<text x="${x}" y="${y}" font-size="${size}" font-weight="${weight}" fill="${fill}" text-anchor="${anchor}" ${extra}>${esc(value)}</text>`;
const lines = (x, y, values, size = 14, fill = C.ink, step = 23, weight = 400) => values.map((v, i) => text(x, y + i * step, v, size, fill, weight)).join('');
const circle = (x, y, r, fill, stroke = '', sw = 1) => `<circle cx="${x}" cy="${y}" r="${r}" fill="${fill}" ${stroke ? `stroke="${stroke}" stroke-width="${sw}"` : ''}/>`;
const path = (d, stroke, sw = 1, extra = '') => `<path d="${d}" fill="none" stroke="${stroke}" stroke-width="${sw}" ${extra}/>`;
let serial = 0;

function icon(name, x, y, color = C.ink, size = 24) {
  const shapes = {
    menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
    more: '<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
    plus: '<path d="M12 4v16M4 12h16"/>',
    back: '<path d="m12 4-8 8 8 8M4 12h16"/>',
    right: '<path d="m9 5 7 7-7 7"/>',
    up: '<path d="m6 15 6-6 6 6"/>',
    down: '<path d="m6 9 6 6 6-6"/>',
    edit: '<path d="m5 16-1 5 5-1L20 9l-4-4zM13 8l4 4"/>',
    image: '<rect x="3" y="4" width="18" height="16" rx="3"/><circle cx="8" cy="9" r="1.5"/><path d="m3 17 5-5 4 4 4-6 5 7"/>',
    addImage: '<path d="M14 4H5a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-8M3 17l5-5 4 4 4-5 5 7M19 2v6M16 5h6"/><circle cx="8" cy="8" r="1"/>',
    sparkle: '<path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5zM20 2v4M18 4h4"/>',
    check: '<path d="m5 12 4 4L19 6"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7v.1"/>',
  };
  return `<g transform="translate(${x} ${y}) scale(${size / 24})" stroke="${color}" stroke-width="1.7" fill="none" stroke-linecap="round" stroke-linejoin="round">${shapes[name]}</g>`;
}

function status() {
  return text(24, 29, '9:41', 14, C.ink, 600) +
    path('M338 26v-4m5 4v-7m5 7V16m5 10V13', C.ink, 2.4, 'stroke-linecap="round"') +
    path('M363 17q8-6 16 0m-13 3q5-4 10 0m-7 3q2-2 4 0', C.ink, 1.5, 'stroke-linecap="round"') +
    rect(386, 15, 21, 11, 'none', 3, C.ink) + rect(389, 18, 15, 5, C.ink, 1) + rect(408, 18, 2, 5, C.ink, 1);
}

function header(tab) {
  let s = status() + icon('menu', 18, 57, C.ink, 24) + text(59, 77, '摄影学习笔记', 20, C.ink, 500) + icon('more', 378, 57, C.muted);
  ['节点', '文档', '会话'].forEach((label, index) => {
    s += text(70 + index * 140, 124, label, 16, tab === index ? C.green : C.muted, tab === index ? 600 : 400, 'middle');
  });
  return s + rect(44.5 + tab * 140, 143, 51, 3, C.green, 1.5) + rect(0, 147, 420, .65, C.line);
}

function navigation() {
  return rect(0, 1007, 420, 33, C.paper) + rect(150, 1023, 120, 4, C.ink, 2);
}

// 正式原图为 16:9；节点卡用 4:3 裁切，文档和 EditorScreen 保持原比例。
function sourceImage(variant = 0) {
  return `<defs><linearGradient id="sky-${variant}" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#E0E9DB"/><stop offset="1" stop-color="#F4EED7"/></linearGradient><linearGradient id="lake-${variant}" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#B8CDC0"/><stop offset="1" stop-color="#718F7F"/></linearGradient></defs>` +
    rect(0, 0, 800, 450, `url(#sky-${variant})`) +
    circle(592, 102, 38, '#F9EED0') +
    '<path d="M0 250 113 136 204 218 310 109 449 255 568 153 687 249 800 186V450H0Z" fill="#A6B69E"/>' +
    '<path d="m0 286 147-85 119 77 118-87 131 102 142-66 143 73v150H0Z" fill="#708F78"/>' +
    '<path d="M0 322c144-24 195 29 326 14s269-18 474 2v112H0Z" fill="url(#lake-'+variant+')"/>' +
    '<path d="M0 359c97-11 150 12 235 91H0Z" fill="#3E6550"/>' +
    '<path d="M800 349c-96-3-142 35-219 101h219Z" fill="#365B47"/>' +
    '<path d="M529 352v-82m-29 33 29-48 29 48Z" fill="#315841" stroke="#315841" stroke-width="5" stroke-linejoin="round"/>' +
    path('M0 150h800M0 300h800M266.667 0v450M533.333 0v450', '#FFFFFF', 1.3, 'opacity=".62" stroke-dasharray="6 7"') +
    circle(533.333, 300, 11, 'none', '#FCFCF1', 1.5) +
    rect(128, 25, 284, 42, '#203C2D', 7, '', 'fill-opacity=".8"') + text(145, 53, variant ? '留白，让画面呼吸' : '三分法 · 安排画面重心', 20, '#F7FAF2', 500) +
    text(29, 421, 'COMPOSITION / 01', 12, '#F3F7ED', 500, 'start', 'letter-spacing="2"');
}

function picture(x, y, w, h, crop = false, r = 8, variant = 0) {
  const id = `picture-${serial++}`;
  return `<defs><clipPath id="${id}">${rect(x, y, w, h, C.white, r)}</clipPath></defs><g clip-path="url(#${id})"><svg x="${x}" y="${y}" width="${w}" height="${h}" viewBox="0 0 800 450" preserveAspectRatio="xMidYMid ${crop ? 'slice' : 'meet'}">${sourceImage(variant)}</svg></g>`;
}

function marker(y, number, color, height = 72) {
  const top = y + (height - 68) / 2;
  return rect(4, top, 48, 68, C.paper) + `<image x="14.5" y="${top}" width="27" height="48" href="data:image/png;base64,${bamboo}"/>` +
    text(28, top + 63, String(number).padStart(2, '0'), 12, color, 600, 'middle');
}

function editControls(x, y, label, fg, bg) {
  const labelW = label.length * 12 + 8;
  return rect(x, y + 3, labelW, 22, bg, 6) + text(x + 4, y + 18, label, 12, fg, 500) +
    rect(x + labelW + 8, y + 2, 24, 24, bg, 6, fg) + icon('edit', x + labelW + 12, y + 6, fg, 16);
}

function chapterDivider(y, x = 52, width = 352) {
  const mid = x + width / 2;
  return path(`M${x} ${y}h${width / 2 - 39}M${mid + 39} ${y}H${x + width}`, C.line, .9) + text(mid, y + 4.5, '章节01', 13, C.muted, 400, 'middle');
}

function floatingActions(running = false) {
  let s = rect(0, 844, 420, 163, 'url(#dock-fade)');
  if (running) {
    s += `<g filter="url(#small-shadow)">${rect(16, 863, 388, 56, C.selected, 16)}</g>` +
      circle(43, 891, 10, 'none', '#B6C9BD', 2.5) + path('M43 881a10 10 0 0 1 9.4 13.4', C.parsing, 2.5, 'stroke-linecap="round"') +
      text(64, 896, '植物观察 · 解析中 1/3', 15, C.parsing, 500) + icon('right', 373, 881, C.parsing, 20);
  } else {
    s += `<g filter="url(#small-shadow)">${rect(244, 863, 160, 56, C.selected, 16)}</g>` + icon('sparkle', 262, 879, C.green, 24) + text(300, 897, '解析 2', 16, C.green, 600);
  }
  s += `<g filter="url(#small-shadow)">${rect(16, 931, 151, 56, C.panel, 16, C.line)}</g>` + picture(20, 935, 48, 48, true, 12, 1) + text(80, 965, '新增 3', 14, C.ink, 500) + icon('down', 143, 952, C.muted, 15);
  s += `<g filter="url(#small-shadow)">${rect(348, 931, 56, 56, C.green, 16)}</g>` + icon('plus', 364, 947, C.white, 24);
  return s + navigation();
}

function nodes() {
  const clip = `list-${serial++}`;
  let content = path('M28 162V1120', C.line, 1);
  content += rect(52, 162, 352, 72, C.titleBg, 12) + marker(162, 1, C.title) + text(228, 207, '摄影学习笔记', 24, C.ink, 700, 'middle');
  content += rect(52, 246, 352, 72, C.introBg, 12) + marker(246, 2, C.intro) + lines(90, 275, ['记录构图方法，也记下', '自己练习时看到的细节。'], 16, C.introText, 24);
  content += chapterDivider(338) + rect(52, 356, 352, 72, C.chapterBg, 12) + marker(356, 3, C.chapter) + text(66, 400, '构图练习', 20, C.ink, 600);
  content += rect(52, 440, 352, 264, C.bodyBg, 12) + picture(56, 444, 344, 256, true, 8) + marker(440, 4, C.body, 312);
  content += `<path d="M56 632h344v60q0 8-8 8H64q-8 0-8-8z" fill="url(#remark-fade)"/>` +
    lines(68, 667, ['下次练习时，向右再留一点空间。'], 16, C.white, 23) + editControls(312, 449, '正文', C.body, C.bodyBg);
  content += rect(52, 708, 352, 48, C.paper, 0) + rect(52, 717, 74, 28, '#E0ECE3', 6) + circle(64, 731, 3, C.parsed) + text(73, 736, '已解析', 12, C.parsed, 500);
  content += lines(138, 726, ['将主体放在右侧交点，让山脊', '落在下方横线，画面更舒展。'], 14, C.muted, 20);
  content += rect(52, 768, 352, 264, C.bodyBg, 12) + picture(56, 772, 344, 256, true, 8, 1) + marker(768, 5, C.body, 312);
  content += rect(52, 1045, 74, 28, '#F2E9DB', 6) + circle(64, 1059, 3, C.pending) + text(73, 1064, '待解析', 12, C.pending, 500);
  return header(0) + `<defs><clipPath id="${clip}">${rect(0, 148, 420, 859, C.paper)}</clipPath></defs><g clip-path="url(#${clip})">${content}</g>` + floatingActions();
}

function documentPage() {
  let s = header(1) + text(210, 194, '摄影学习笔记', 24, C.ink, 700, 'middle') + rect(24, 210, 372, 1, C.line);
  s += rect(24, 230, 372, 76, C.quote, 8) + lines(48, 260, ['记录构图方法，也记下', '自己练习时看到的细节。'], 16, C.introText, 24);
  s += chapterDivider(334, 24, 372) + text(24, 379, '构图练习', 20, C.ink, 600) + picture(24, 399, 372, 209.25, false, 8);
  s += lines(24, 642, ['将主体放在画面右侧三分之一处，', '让山脊落在下方横线。为视线方向', '保留空间，画面会更舒展。'], 16, C.ink, 26);
  s += text(24, 737, '下次练习时，向右再留一点空间。', 16, C.ink);
  s += picture(24, 765, 372, 209.25, false, 8, 1);
  return s + floatingActions(true);
}

function createNode() {
  let s = status() + icon('back', 20, 57, C.ink) + text(64, 77, '创建正文', 20, C.ink, 500) + icon('addImage', 302, 57, C.muted) + text(388, 77, '保存', 16, C.green, 600, 'end');
  s += `<g filter="url(#image-shadow)">${picture(24, 126, 372, 209.25, false, 2, 1)}</g>`;
  s += icon('up', 285, 352, '#BBC4BC', 22) + icon('down', 329, 352, '#BBC4BC', 22) + icon('more', 373, 352, C.muted, 22);
  // 保留正文编辑器的 240dp 输入区；新节点没有解析结果输入框。
  s += rect(24, 414, 372, 240, C.paper, 8, '#94A798') + rect(36, 406, 74, 17, C.paper) + text(40, 420, '用户备注', 14, C.muted, 500);
  s += lines(40, 453, ['这张图适合对比主体的位置。', '下次练习时，向右再留一点空间。'], 16, C.ink, 27);
  s += text(24, 706, '精细识别', 16, C.ink, 500) + text(24, 732, '公式、代码、表格优先保持清晰', 14, C.muted);
  s += rect(344, 686, 52, 32, C.green, 16) + circle(380, 702, 12, C.white) + icon('check', 373, 695, C.green, 14);
  s += rect(24, 762, 372, .65, C.line) + icon('info', 24, 787, C.muted, 20) + text(55, 802, '保存后加入节点，稍后手动解析', 14, C.muted) + text(55, 826, '保存不会上传图片', 14, C.muted);
  return s + navigation();
}

const screens = [
  ['01-节点页', '节点 · 在原有链上增加解析', '图片备注与解析文字分开呈现', nodes],
  ['02-文档页', '文档 · 阅读已保存的内容', '跨文档任务在正文外明确归属', documentPage],
  ['03-新增节点', '创建正文 · 保存后才入链', '原图、用户备注与精细识别', createNode],
];

function svg(screen) {
  const ui = screen[3]();
  return `<svg xmlns="http://www.w3.org/2000/svg" width="480" height="1156" viewBox="0 0 480 1156" font-family="Hiragino Sans GB, PingFang SC, sans-serif">
    <defs>
      <clipPath id="phone-screen">${rect(30, 30, 420, 1040, C.paper, 32)}</clipPath>
      <linearGradient id="remark-fade" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#182E21" stop-opacity="0"/><stop offset=".6" stop-color="#182E21" stop-opacity=".72"/><stop offset="1" stop-color="#182E21" stop-opacity=".88"/></linearGradient>
      <linearGradient id="dock-fade" x1="0" y1="0" x2="0" y2="1"><stop stop-color="${C.paper}" stop-opacity="0"/><stop offset=".45" stop-color="${C.paper}" stop-opacity=".97"/><stop offset="1" stop-color="${C.paper}"/></linearGradient>
      <filter id="small-shadow" x="-20%" y="-30%" width="140%" height="180%"><feDropShadow dx="0" dy="3" stdDeviation="5" flood-color="#1F3B28" flood-opacity=".09"/></filter>
      <filter id="image-shadow" x="-20%" y="-20%" width="140%" height="160%"><feDropShadow dx="0" dy="6" stdDeviation="8" flood-color="#183723" flood-opacity=".18"/></filter>
      <filter id="phone-shadow" x="-20%" y="-10%" width="140%" height="130%"><feDropShadow dx="0" dy="9" stdDeviation="11" flood-color="#233829" flood-opacity=".15"/></filter>
    </defs>
    ${rect(0, 0, 480, 1156, C.canvas)}
    <g filter="url(#phone-shadow)">${rect(20, 20, 440, 1060, '#23332A', 43)}</g>
    <g clip-path="url(#phone-screen)">${rect(30, 30, 420, 1040, C.paper)}<g transform="translate(30 30)">${ui}</g></g>
    ${text(240, 1114, screen[1], 16, C.ink, 600, 'middle')}
    ${text(240, 1138, screen[2], 13, C.muted, 400, 'middle')}
  </svg>`;
}

for (const screen of screens) {
  const result = new Resvg(svg(screen), {
    fitTo: { mode: 'zoom', value: 2 },
    font: { loadSystemFonts: true, defaultFontFamily: 'Hiragino Sans GB' },
  }).render().asPng();
  writeFileSync(join(output, `${screen[0]}.png`), result);
}
console.log(`Generated ${screens.length} PicNote upgrade prototypes.`);
