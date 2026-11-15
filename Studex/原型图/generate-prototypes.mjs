import { writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

// 使用矢量源绘制静态原型，中文与布局可以直接修改后重新生成。
const require = createRequire(import.meta.url);
const moduleRoot = process.env.STUDEX_RENDER_MODULE_ROOT;
const { Resvg } = require(moduleRoot ? join(moduleRoot, '@resvg/resvg-js') : '@resvg/resvg-js');
const output = dirname(fileURLToPath(import.meta.url));
const C = {
  ink: '#1F2926', muted: '#697570', line: '#DFE6E1', canvas: '#E9EEEA',
  paper: '#FAFAF8', white: '#FFFFFF', green: '#2E7D5B', mint: '#F1F7F3',
  yellow: '#A66A00', yellowLine: '#E8B33A', cream: '#FFF8E6',
  blue: '#4967D6', lavender: '#EEF2FF', red: '#B74636',
};
const esc = value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
const rect = (x, y, w, h, fill, r = 0, stroke = '', extra = '') => `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}" fill="${fill}" ${stroke ? `stroke="${stroke}"` : ''} ${extra}/>`;
const text = (x, y, value, size = 14, fill = C.ink, weight = 400, anchor = 'start') => `<text x="${x}" y="${y}" font-size="${size}" font-weight="${weight}" fill="${fill}" text-anchor="${anchor}">${esc(value)}</text>`;
const lines = (x, y, values, size = 14, fill = C.ink, step = 23, weight = 400) => values.map((value, i) => text(x, y + i * step, value, size, fill, weight)).join('');
const circle = (x, y, r, fill, stroke = '', sw = 1) => `<circle cx="${x}" cy="${y}" r="${r}" fill="${fill}" ${stroke ? `stroke="${stroke}" stroke-width="${sw}"` : ''}/>`;
let nextId = 0;
const uniqueId = prefix => `${prefix}-${nextId++}`;

function icon(name, x, y, color = C.ink, size = 22) {
  const paths = {
    menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
    more: '<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
    arrow: '<path d="M4 12h15m-6-6 6 6-6 6"/>',
    back: '<path d="m14 5-7 7 7 7M7 12h14"/>',
    chevron: '<path d="m9 5 7 7-7 7"/>',
    down: '<path d="m6 9 6 6 6-6"/>',
    plus: '<path d="M12 4v16M4 12h16"/>',
    check: '<path d="m5 12 4 4 10-10"/>',
    lock: '<rect x="5" y="10" width="14" height="11" rx="3"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/>',
    close: '<path d="m6 6 12 12M18 6 6 18"/>',
    file: '<path d="M6 3h8l4 4v14H6zM14 3v5h4M9 12h6M9 16h6"/>',
    memory: '<path d="M8 4h8v16H8zM4 8h4M4 16h4M16 8h4M16 16h4M11 8h2M11 12h2M11 16h2"/>',
    archive: '<path d="M4 4h16v5H4zM6 9v11h12V9M10 13h4"/>',
    pencil: '<path d="m5 16-1 5 5-1L20 9l-4-4zM13 8l4 4"/>',
    share: '<path d="M12 16V3m-4 4 4-4 4 4M6 12H4v9h16v-9h-2"/>',
    refresh: '<path d="M19 8a8 8 0 1 0 1 7M19 3v6h-6"/>',
    trash: '<path d="M4 6h16M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7M14 10v7"/>',
    image: '<rect x="3" y="3" width="18" height="18" rx="4"/><circle cx="8" cy="8" r="1.5"/><path d="m3 17 5-5 4 4 4-6 5 7"/>',
    settings: '<path d="m9 3-1 3-3 1 1 3-2 2 2 2-1 3 3 1 1 3h6l1-3 3-1-1-3 2-2-2-2 1-3-3-1-1-3z"/><circle cx="12" cy="12" r="3"/>',
    up: '<path d="M12 20V4m-6 6 6-6 6 6"/>',
  };
  return `<g transform="translate(${x} ${y}) scale(${size / 24})" stroke="${color}" stroke-width="1.7" fill="none" stroke-linecap="round" stroke-linejoin="round">${paths[name] || paths.file}</g>`;
}

function pill(x, y, w, label, color = C.green, fill = '#E4EFE8', h = 25) {
  return rect(x, y, w, h, fill, h / 2) + text(x + w / 2, y + h / 2 + 4.5, label, 12, color, 500, 'middle');
}

function button(x, y, w, label, fill = C.green, color = C.white, h = 44) {
  return rect(x, y, w, h, fill, 12) + text(x + w / 2, y + h / 2 + 5, label, 15, color, 500, 'middle');
}

function ring(x, y, r, ratio, color = C.blue, sw = 5) {
  const length = 2 * Math.PI * r;
  return circle(x, y, r, 'none', '#E2E8F7', sw) + `<circle cx="${x}" cy="${y}" r="${r}" fill="none" stroke="${color}" stroke-width="${sw}" stroke-linecap="round" stroke-dasharray="${length * ratio} ${length}" transform="rotate(-90 ${x} ${y})"/>`;
}

function thumb(x, y, w, h, kind = 'text', selected = false) {
  let s = rect(x, y, w, h, kind === 'video' ? '#243933' : '#FFFFFF', 9, selected ? C.green : C.line, selected ? 'stroke-width="2"' : '');
  if (w < 100) {
    s += text(x + 9, y + 17, kind === 'qkv' ? 'Q / K / V' : '截图', 9, C.muted);
    [0.85, 0.7, 0.52].forEach((ratio, i) => s += rect(x + 9, y + 26 + i * 8, (w - 18) * ratio, 3, '#D9E0DB', 1));
    if (selected) s += circle(x + w - 12, y + 12, 9, C.green) + icon('check', x + w - 19, y + 5, C.white, 14);
    return s;
  }
  if (kind === 'qkv') {
    s += text(x + 9, y + 17, 'Attention / 注意力', 9, C.muted, 500);
    const bx = x + 11, by = y + h / 2 - 5, gap = (w - 22) / 3;
    ['Q', 'K', 'V'].forEach((label, i) => {
      s += rect(bx + gap * i, by, gap - 7, 20, ['#E8EFFC', '#E9F2E7', '#FAEED6'][i], 4);
      s += text(bx + gap * i + (gap - 7) / 2, by + 14, label, 11, C.ink, 500, 'middle');
    });
    s += text(x + 10, y + h - 8, '线性投影 → 相关性 → 加权', 8, C.muted);
  } else if (kind === 'video') {
    s += text(x + 10, y + 17, '多头注意力 · 动画讲解', 9, '#CCDDD4');
    s += circle(x + w / 2, y + h / 2 + 3, 14, '#E7F0EA');
    s += `<path d="m${x+w/2-3} ${y+h/2-3} 9 6-9 6z" fill="${C.green}"/>`;
  } else if (kind === 'formula') {
    s += text(x + 10, y + 18, 'Scaled dot-product', 9, C.muted);
    s += text(x + w / 2, y + h / 2 + 9, 'softmax(QK^T / sqrt(d_k)) V', 9, C.green, 500, 'middle');
  } else {
    s += text(x + 9, y + 17, '多头注意力的直觉', 9, C.ink, 500);
    [0.87, 0.95, 0.69].forEach((ratio, i) => s += rect(x + 9, y + 27 + i * 10, (w - 18) * ratio, 3, '#D9E0DB', 1));
    if (h > 75) s += text(x + 9, y + h - 9, '每个头学习不同的表示空间', 8, C.muted);
  }
  if (selected) s += circle(x + w - 12, y + 12, 9, C.green) + icon('check', x + w - 19, y + 5, C.white, 14);
  return s;
}

function status() {
  return text(23, 28, '9:41', 14, C.ink, 600) + text(325, 28, '5G', 11, C.ink, 500) +
    rect(347, 18, 3, 9, C.ink, 1) + rect(353, 15, 3, 12, C.ink, 1) + rect(359, 12, 3, 15, C.ink, 1) +
    rect(374, 14, 25, 12, 'none', 3, C.ink) + rect(377, 17, 18, 6, C.ink, 1) + rect(400, 18, 2, 4, C.ink, 1);
}

function header(tab = 0, running = false, title = '注意力机制') {
  let s = status() + icon('menu', 19, 59) + text(58, 78, title, 20, C.ink, 500) + icon('more', 377, 59, C.muted, 22);
  ['节点', '文档', '会话'].forEach((label, i) => {
    s += text(70 + i * 140, 119, label, 15, tab === i ? C.ink : C.muted, tab === i ? 500 : 400, 'middle');
  });
  s += rect(tab * 140 + 57, 130, 26, 2, C.green, 1) + rect(0, 135, 420, .6, C.line);
  if (running) s += ring(25, 158, 6, 1/3, C.blue, 2) + text(42, 163, '正在解析 · 本轮已保存 1/3', 12, C.blue) + text(399, 163, '展开', 12, C.blue, 400, 'end');
  return s;
}

function subHeader(title, subtitle = '') {
  return status() + icon('back', 20, 63) + text(58, 80, title, 22, C.ink, 600) + (subtitle ? text(59, 102, subtitle, 12, C.muted) : '') + rect(0, 115, 420, 1, C.line);
}

function home() { return rect(150, 846, 120, 4, '#24332C', 2); }

function bottomDock(running = false, empty = false) {
  let s = rect(0, 792, 420, 46, C.paper) + rect(16, 792, 388, .6, C.line);
  s += icon('image', 20, 806, C.muted, 19) + text(49, 821, empty ? '添加截图' : '新截图 2', 13, C.muted) + icon('down', 129, 808, C.muted, 15);
  s += button(299, 798, 101, empty ? '解析' : running ? '解析中 1/3' : '解析 2', empty ? '#E9EDEA' : running ? C.lavender : C.green, empty ? '#A1AAA4' : running ? C.blue : C.white, 34);
  return s + home();
}

// 同一张矢量示例原图用于裁切预览和详情，保留真实宽高比例。
function sourceImage(kind = 'qkv') {
  if (kind === 'video') {
    return rect(0, 0, 640, 360, '#E8EDE7') + text(320, 54, '一小时读懂 Transformer', 20, '#63746A', 400, 'middle') +
      text(320, 99, '为什么需要多头注意力？', 27, '#2D4436', 500, 'middle') +
      [0, 1, 2].map(i => rect(123 + i * 137, 143, 112, 75, ['#CDDCEB', '#D5E3D1', '#E8DCBD'][i], 12) + text(179 + i * 137, 188, `Head ${i + 1}`, 20, '#405745', 500, 'middle')).join('') +
      `<path d="M179 225v25h137m0-25v25m137-25v25H316v24" stroke="#9BAB9D" stroke-width="2" fill="none"/>` +
      text(320, 308, '独立投影 · 并行计算 · 拼接', 18, '#63746A', 400, 'middle');
  }
  return rect(0, 0, 360, 480, '#F7F6F1') + text(22, 30, '学习笔记   /   Transformer', 11, '#788379') + rect(22, 48, 316, 1, '#DDE2D8') +
    text(22, 79, '理解 Attention', 25, '#314D3A', 500) + text(22, 99, '从一次查询，到有选择的信息聚合。', 12, '#7B857A') +
    rect(22, 135, 316, 191, '#EBEFE5', 12) + text(43, 165, '找到相关内容，再聚合', 15, '#4C634C', 500) +
    ['Q', 'K', 'V'].map((label, i) => rect(43 + i * 98, 191, 78, 48, ['#D0DDE8', '#D6E2CD', '#E8DEC3'][i], 9) + text(82 + i * 98, 223, label, 25, '#3D5544', 500, 'middle')).join('') +
    text(82, 260, 'Query', 11, '#778274', 400, 'middle') + text(180, 260, 'Key', 11, '#778274', 400, 'middle') + text(278, 260, 'Value', 11, '#778274', 400, 'middle') +
    text(180, 302, '相关性  →  权重  →  加权求和', 13, '#546D51', 500, 'middle') +
    text(22, 359, 'Q 提出问题，K 用于匹配，V 提供内容。', 12, '#596B59') +
    text(22, 383, '匹配程度越高，对应的信息权重越大。', 12, '#596B59') +
    rect(22, 407, 316, 43, '#E8EDE3', 8) + text(180, 433, 'softmax(QK^T / sqrt(d_k)) V', 14, '#3C5A43', 500, 'middle');
}

function framedImage(x, y, w, h, kind = 'qkv', crop = true) {
  const clipId = uniqueId('image');
  const viewBox = kind === 'video' ? '0 0 640 360' : '0 0 360 480';
  return `<defs><clipPath id="${clipId}">${rect(x, y, w, h, C.white, 12)}</clipPath></defs>` +
    `<g clip-path="url(#${clipId})"><svg x="${x}" y="${y}" width="${w}" height="${h}" viewBox="${viewBox}" preserveAspectRatio="xMidYMid ${crop ? 'slice' : 'meet'}">${sourceImage(kind)}</svg></g>`;
}

function imageNode(y, { kind = 'qkv', state = 'parsed', settings = false } = {}) {
  const x = 52, w = 352, h = w * 3 / 4;
  const color = state === 'parsed' ? C.green : state === 'parsing' ? C.blue : C.yellow;
  let s = circle(28, y + h / 2, 5, C.paper, color, 1.4) + framedImage(x, y, w, h, kind);
  const remark = kind === 'video' ? ['多头注意力究竟在并行什么？', '请和单头放在一起比较。'] : ['抓取图中的信息，说明 Q / K / V 的作用，', '保留公式，并补上直觉解释…'];
  s += `<path d="M${x} ${y+h-32}h${w}v20q0 12-12 12H${x+12}q-12 0-12-12z" fill="url(#remark-fade)"/>` + lines(x + 12, y + h - 18, remark, 11.5, C.white, 14);
  if (settings) s += circle(380, y + 26, 16, '#FFFFFF', '#E9EDE5', .5) + icon('settings', 370, y + 16, '#405345', 20);
  if (state === 'parsed') {
    const clipId = uniqueId('result');
    s += `<defs><clipPath id="${clipId}">${rect(x, y + h + 8, w, 48, C.paper)}</clipPath></defs>`;
    s += `<g clip-path="url(#${clipId})">${lines(x, y + h + 22, ['已解析：Q 与 K 计算相关性，对 V 加权求和。', '权重由 softmax 归一化，保留相关的信息。'], 12.5, C.muted, 19)}</g>`;
    s += rect(401, y + h + 12, 2, 39, '#E6EAE5', 1) + rect(401, y + h + 12, 2, 17, '#B7C1B7', 1);
  } else if (state === 'parsing') {
    s += ring(59, y + h + 19, 5, .6, C.blue, 1.5) + text(73, y + h + 23, '解析中…', 12, C.blue) + icon('lock', 386, y + h + 10, C.muted, 15);
  } else s += text(x, y + h + 23, '待解析', 12, C.yellow);
  return s;
}

function nodes(settings = false) {
  return header() + `<path d="M28 150V778" stroke="${C.line}" stroke-width="1"/>` +
    imageNode(150, { settings }) + imageNode(480, { kind: 'video', state: 'pending' }) + bottomDock();
}

function folded() {
  const clipId = uniqueId('node-list');
  return header(0, true) + `<defs><clipPath id="${clipId}">${rect(0, 181, 420, 602, C.paper)}</clipPath></defs>` +
    `<g clip-path="url(#${clipId})"><path d="M28 190V1000" stroke="${C.line}" stroke-width="1"/>${imageNode(190)}${imageNode(520, { kind: 'video', state: 'parsing' })}</g>` + bottomDock(true);
}

function documentPage() {
  let s = header(1) + text(20, 180, '1,842 字 · 4 个来源', 12, C.muted) + text(320, 180, '复制', 12, C.green, 500) + icon('share', 373, 163, C.green, 20);
  s += rect(20, 198, 380, 37, C.cream, 10) + text(34, 222, '有 1 处内容待更新，现有正文已保留', 13, C.yellow);
  s += text(20, 279, '从 Q/K/V 到多头注意力', 24, C.ink, 600);
  s += lines(20, 310, ['把零散截图串起来，理解信息如何被选择、', '组合，并在不同表示空间中并行计算。'], 15, '#56675E', 25);
  s += text(20, 383, '01  注意力如何选择信息', 20, C.ink, 600);
  s += lines(20, 417, ['Q 表示当前查询，K 提供可匹配的线索，', 'V 提供实际内容。相关性越高，对应内容', '在最终表示中的权重越大。'], 15, C.ink, 26);
  s += rect(20, 494, 380, 88, C.mint, 12) + text(36, 519, '缩放点积注意力', 12, C.green, 500) + text(210, 554, 'Attention = softmax(QK^T / sqrt(d_k)) V', 14, C.green, 500, 'middle');
  s += text(400, 613, '来源 2 个节点 ›', 12, C.green, 500, 'end') + rect(20, 632, 380, 1, C.line);
  s += text(20, 673, '02  多头在并行什么', 20, C.ink, 600) + lines(20, 707, ['每个头使用独立的投影矩阵，学习不同', '关系；结果拼接后再进行一次线性变换。', '这使同一组输入可以保留多种关联。'], 15, C.ink, 26);
  s += text(400, 802, '来源 2 个节点 · 1 处待更新 ›', 12, C.yellow, 500, 'end');
  return s + home();
}

function turns() {
  let s = header(2, true) + rect(20, 216, 380, 37, '#EDF0EC', 11) + rect(23, 219, 187, 31, C.white, 8) + text(116, 240, '摘要视图', 13, C.ink, 500, 'middle') + text(304, 240, '原文视图', 13, C.muted, 400, 'middle');
  s += rect(20, 269, 380, 340, C.white, 16, C.line) + text(36, 300, '第 3 轮', 18, C.ink, 600) + text(119, 300, '3 个节点', 12, C.muted) + pill(310, 282, 73, '解析中', C.blue, C.lavender) + text(36, 321, '21:04 · 已运行 12 秒', 12, C.muted);
  s += rect(64, 339, 320, 109, '#EEF2FB', 12) + text(80, 363, '我发出的', 12, C.blue, 500) + text(80, 391, '比较单头和多头的作用。', 15, C.ink) + text(80, 417, '3 个节点 · 5 张截图', 12, C.muted) + text(80, 437, '引用：当前文档相关章节', 11, C.muted);
  s += rect(36, 465, 348, 125, C.mint, 12) + text(52, 490, '模型整理', 12, C.green, 500) + icon('check', 52, 504, C.green, 17) + text(76, 519, '多头的作用 · 已保存', 14, C.ink, 500) + text(52, 546, '正在整理后续 2 个节点…', 13, C.muted) + text(52, 575, '本轮已保存 1/3', 13, C.blue, 500);
  s += rect(20, 629, 380, 133, C.white, 16, C.line) + text(36, 661, '第 2 轮', 18, C.ink, 600) + pill(318, 643, 65, '已完成') + text(36, 688, '昨天 21:04 · 2 个节点 · 18 秒', 12, C.muted) + text(36, 719, '整理了 Q/K/V 与缩放点积注意力。', 14, C.ink) + text(36, 746, '查看这一轮 ›', 12, C.green, 500);
  return s + home();
}

function overlay() {
  let s = folded() + rect(0, 0, 420, 864, '#14221C', 0, '', 'opacity="0.54"');
  s += rect(22, 211, 376, 477, C.white, 22) + text(362, 245, '收起', 14, C.blue, 500, 'end') + icon('down', 368, 231, C.blue, 16);
  s += ring(210, 303, 36, 1/3, C.blue, 5) + text(210, 310, '1/3', 22, C.blue, 600, 'middle') + text(210, 369, '正在写入文档', 22, C.ink, 600, 'middle') + text(210, 393, '本轮已保存 1 个，共 3 个节点', 13, C.muted, 400, 'middle');
  s += rect(42, 415, 336, 174, '#F4F7F3', 13) + icon('check', 57, 430, C.green, 17) + text(81, 444, '多头的作用 · 已保存', 13, C.green, 500) + lines(58, 476, ['每个头有独立的投影矩阵，', '可以在不同子空间关注不同关系。', '结果拼接后，重新映射到输出空间。'], 14, C.ink, 25) + text(58, 568, '正在整理后续内容，尚未保存…', 12, C.muted);
  s += button(42, 616, 139, '停止解析', '#F8ECE7', C.red) + text(375, 644, '已用时 12 秒', 12, C.muted, 400, 'end');
  return s;
}

function drawer() {
  let s = nodes() + rect(0, 0, 420, 864, '#14221C', 0, '', 'opacity="0.28"') + rect(0, 0, 356, 864, C.paper);
  s += text(23, 28, '9:41', 14, C.ink, 600) + text(24, 80, '我的文档', 22, C.ink, 500) + icon('plus', 274, 62, C.green, 18) + text(300, 78, '新建', 14, C.green, 500);
  s += text(24, 135, '最近', 12, C.muted) + rect(12, 154, 332, 88, '#EDF3EE', 12) + text(27, 185, '注意力机制', 17, C.ink, 500) + icon('more', 307, 169, C.muted, 19) + text(27, 212, '7 个节点 · 当前文档', 12, C.muted);
  s += text(27, 285, 'Transformer 基础', 17, C.ink) + text(27, 312, '12 个节点 · 昨天', 12, C.muted) + icon('more', 307, 269, C.muted, 19);
  s += text(27, 382, '线性代数复习', 17, C.ink) + text(27, 409, '6 个节点 · 周一', 12, C.muted) + icon('more', 307, 366, C.muted, 19);
  s += rect(24, 718, 308, .6, C.line) + icon('archive', 25, 741, C.muted, 20) + text(60, 758, '已归档', 14, C.muted);
  s += icon('memory', 25, 793, C.muted, 20) + text(60, 810, '记忆管理', 14, C.muted);
  return s;
}

function edit(scrolled = false) {
  const blurId = uniqueId('editor-blur'), clipId = uniqueId('editor-scroll');
  let s = `<defs><filter id="${blurId}" x="-15%" y="-15%" width="130%" height="130%"><feGaussianBlur stdDeviation="9"/></filter><clipPath id="${clipId}">${rect(32, 108, 356, 718, C.white)}</clipPath></defs>`;
  s += `<g filter="url(#${blurId})">${nodes()}</g>` + rect(0, 0, 420, 864, '#FAFCF9', 0, '', 'opacity="0.88"');
  let content = framedImage(32, 112, 356, 356 * 4 / 3, 'qkv', false);
  content += text(32, 619, '用户备注', 14, C.ink, 500) + rect(32, 636, 356, 80, '#FFFFFF', 10, '#D9E2D7') + lines(46, 660, ['抓取图中的信息，说明 Q / K / V 的作用。', '保留公式，并补上直觉解释。'], 13, C.ink, 23);
  content += text(32, 754, '已解析', 13, C.green, 500) + icon('check', 369, 738, C.green, 18);
  content += text(32, 799, '解析内容', 14, C.ink, 500) + text(388, 799, '可直接修正', 11, C.muted, 400, 'end');
  content += rect(32, 815, 356, 156, '#FFFFFF', 10, '#D9E2D7') + lines(46, 841, ['Q 表示查询，K 用来与查询匹配，', 'V 提供参与加权求和的内容。', '', '将 Q 与 K 的点积缩放并经过 softmax，', '再用得到的权重对 V 求和。'], 13, C.ink, 23);
  content += text(32, 1005, '只修正解析内容时，保留已解析状态。', 11, C.muted) + text(32, 1025, '备注或原图改变后，保存为待解析。', 11, C.muted);
  content += rect(32, 1053, 356, .6, C.line) + text(32, 1087, '添加图片', 13, C.green) + text(388, 1087, '删除节点', 13, C.red, 400, 'end');
  s += `<g clip-path="url(#${clipId})"><g transform="translate(0 ${scrolled ? -380 : 0})">${content}</g></g>`;
  s += rect(0, 0, 420, 104, '#FAFCF9', 0, '', 'opacity="0.90"') + status() + icon('close', 32, 61, C.muted, 21) + text(70, 78, '节点编辑', 18, C.ink, 500) + text(388, 78, '保存', 14, C.green, 500, 'end');
  s += rect(401, 121, 2, 678, '#E4E9E0', 1) + rect(401, scrolled ? 430 : 124, 2, 185, '#B4C0AF', 1);
  return s + home();
}

function firstNode() {
  let s = header(0, false, '未命名文档') + `<path d="M28 150V432" stroke="${C.line}" stroke-width="1"/>` + circle(28, 282, 5, C.paper, '#B9C8BA', 1.4);
  s += rect(52, 150, 352, 264, '#F0F3EC', 12) + text(74, 184, '起始信息', 12, '#7C8A78') + text(74, 225, '这次想学什么？', 23, '#42583F', 500);
  s += lines(74, 263, ['简单说说学习主题、视频或文章，', '以及这篇文档预计记录哪些内容。'], 13, '#7C8977', 24);
  s += icon('pencil', 74, 361, C.green, 17) + text(101, 376, '填写起始信息', 14, C.green, 500);
  s += text(52, 444, '可稍后填写', 12, C.muted);
  return s + bottomDock(false, true);
}

function insertMenu() {
  const shadowId = uniqueId('menu-shadow');
  let s = nodes() + `<defs><filter id="${shadowId}" x="-30%" y="-40%" width="160%" height="200%"><feDropShadow dx="0" dy="6" stdDeviation="10" flood-color="#223729" flood-opacity="0.12"/></filter></defs>`;
  s += circle(28, 282, 11, '#E6EEE4') + circle(28, 282, 5, C.green);
  s += `<g filter="url(#${shadowId})">${rect(45, 259, 231, 113, C.white, 14, '#EDF0EB')}</g>`;
  s += icon('up', 61, 279, C.muted, 18) + text(91, 294, '插入上一个节点', 15, C.ink);
  s += rect(60, 315, 201, .6, '#EDF0EB') + icon('down', 61, 335, C.muted, 18) + text(91, 350, '插入下一个节点', 15, C.ink);
  return s;
}

function memory() {
  let s = subHeader('记忆管理', '注意力机制') + rect(20, 132, 380, 174, C.white, 14, C.line) + text(36, 163, '写作偏好', 18, C.ink, 600) + text(383, 162, '编辑', 13, C.green, 500, 'end');
  s += text(36, 197, '语言', 13, C.muted) + text(151, 197, '简体中文', 14, C.ink) + text(36, 228, '组织方式', 13, C.muted) + text(151, 228, '分节，先解释再举例', 14, C.ink) + text(36, 259, '术语', 13, C.muted) + text(151, 259, '保留原文术语与公式', 14, C.ink) + text(36, 288, '由你设置，仅对后续提交生效', 11, C.muted);
  s += rect(20, 322, 380, 173, C.mint, 14, '#D1E3D8') + text(36, 353, '当前文档记忆', 18, C.ink, 600) + text(36, 383, '已整理 3 个有效节点', 14, C.ink) + pill(36, 399, 116, '1 个来源待更新', C.yellow, '#FAECC9') + text(36, 448, '查看来源 ›', 13, C.green, 500) + button(245, 438, 138, '重新整理记忆', '#E2EDE5', C.green, 35) + text(36, 482, '整理已有摘要，不重新读图或修改正文', 11, C.muted);
  s += rect(20, 512, 380, 268, C.white, 14, C.line) + text(36, 543, '引用历史文档', 18, C.ink, 600);
  s += icon('file', 35, 565, C.green, 20) + text(65, 582, 'Transformer 基础', 15, C.ink, 500) + text(65, 608, '已选择 2 个章节', 12, C.muted) + text(382, 605, '预览', 12, C.green, 500, 'end') + rect(36, 627, 348, 1, C.line);
  s += icon('file', 35, 645, C.muted, 20) + text(65, 662, '线性代数回顾', 15, C.muted) + text(65, 688, '来源已删除 · 不参与后续解析', 12, C.red) + text(382, 686, '移除', 12, C.green, 500, 'end');
  s += button(36, 720, 348, '＋ 引用历史文档', '#E8F1EB', C.green, 40) + text(20, 813, '只引用你选择的文档与章节。', 12, C.muted);
  return s + home();
}

function cancel() {
  let s = folded() + rect(0, 0, 420, 864, '#14221C', 0, '', 'opacity="0.50"') + rect(28, 304, 364, 263, C.white, 22);
  s += text(52, 346, '取消本次解析？', 22, C.ink, 600) + lines(52, 382, ['已保存的 1 个节点及文档内容会保留。', '剩余 2 个节点将回到待解析，', '之后可继续提交。'], 14, C.muted, 25);
  s += rect(52, 453, 316, 31, C.mint, 8) + icon('check', 63, 460, C.green, 17) + text(87, 474, '下一轮新加的截图不受影响', 12, C.green);
  s += button(52, 503, 151, '继续解析', '#E8F1EB', C.green) + button(215, 503, 153, '取消解析', C.red, C.white);
  return s;
}

const screens = [
  ['01-节点页', '简约节点页', '4:3 图片 · 32dp 备注层 · 48dp 解析内容', nodes],
  ['02-文档页', '文档与来源', '旧正文保留，待更新来源可追溯', documentPage],
  ['03-会话页', '轮次回放', '实际输入、模型产出与已保存进度', turns],
  ['04-提交遮罩', '解析展开态', '同一任务可收起，提交成功才计数', overlay],
  ['05-解析收起态', '解析收起态', '沿用图片卡，进度与状态以小字呈现', folded],
  ['06-文档抽屉', '文档导航', '右上角新建，创建后生成第一个节点', drawer],
  ['07-节点编辑', '原图与备注', '全屏背景模糊 · 左右 32dp · 原图比例', edit],
  ['08-记忆管理', '记忆与引用', '显式偏好、有效来源与手动历史引用', memory],
  ['09-取消确认', '取消解析', '保留已保存结果，剩余节点可重试', cancel],
  ['10-新建起始节点', '默认起始节点', '学习主题、视频来源与记录目标', firstNode],
  ['11-节点插入菜单', '节点前后插入', '点击左侧圆点，选择插入上一个或下一个', insertMenu],
  ['12-设置显隐', '图片单击后的设置入口', '0.5s 渐显 → 停留 3s → 0.5s 渐隐', () => nodes(true)],
  ['13-节点编辑滚动态', '备注与解析内容修正', '纵向滚动查看状态，并直接修改解析内容', () => edit(true)],
];

function phone(screen, index) {
  return `<defs><clipPath id="screen-${index}">${rect(30, 30, 420, 864, C.paper, 42)}</clipPath></defs>` +
    rect(20, 20, 440, 884, '#18221D', 52) +
    `<g clip-path="url(#screen-${index})">${rect(30, 30, 420, 864, C.paper)}<g transform="translate(30 30)">${screen[3]()}</g></g>` +
    text(240, 932, `${screen[0].split('-')[0]}  ${screen[1]}`, 16, C.ink, 600, 'middle') +
    text(240, 953, screen[2], 12, C.muted, 400, 'middle');
}

function svg(content, width, height, bg = C.canvas) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" font-family="Hiragino Sans GB, Arial, sans-serif"><defs><linearGradient id="remark-fade" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#152218" stop-opacity="0.76"/><stop offset="1" stop-color="#152218" stop-opacity="0.92"/></linearGradient></defs>${rect(0, 0, width, height, bg)}${content}</svg>`;
}

const render = (source, scale) => new Resvg(source, {
  fitTo: { mode: 'zoom', value: scale },
  font: { loadSystemFonts: true, defaultFontFamily: 'Hiragino Sans GB' },
}).render().asPng();

let board = text(24, 45, 'STUDEX  /  截图驱动的学习笔记', 26, C.ink, 600) + text(24, 77, '简约节点链 · 起始信息 · 原图编辑 · 有来源的文档', 16, C.muted);
screens.forEach((screen, i) => {
  const content = phone(screen, i);
  writeFileSync(join(output, `${screen[0]}.png`), render(svg(content, 480, 970), 2));
  board += `<g transform="translate(${24 + (i % 3) * 504} ${104 + Math.floor(i / 3) * 995})">${content}</g>`;
});
writeFileSync(join(output, '00-原型总览.png'), render(svg(board, 1536, 104 + Math.ceil(screens.length / 3) * 995 + 11), 1));

let detailBoard = text(24, 45, 'STUDEX  /  节点交互', 26, C.ink, 600) + text(24, 77, '从新建与起始信息，到简约浏览、插入与原图编辑', 16, C.muted);
[5, 9, 0, 10, 6, 12].forEach((screenIndex, i) => {
  detailBoard += `<g transform="translate(${24 + (i % 3) * 504} ${104 + Math.floor(i / 3) * 995})">${phone(screens[screenIndex], `detail-${i}`)}</g>`;
});
writeFileSync(join(output, '14-节点交互总览.png'), render(svg(detailBoard, 1536, 2105), 1));
console.log(`Generated ${screens.length} screens and two overviews.`);
