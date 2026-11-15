import { writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

// 使用独立矢量源生成竹节笔记 / Bambootes 原型，图片为本地绘制的示例素材。
const require = createRequire(import.meta.url);
const moduleRoot = process.env.PICNOTE_RENDER_MODULE_ROOT;
const { Resvg } = require(moduleRoot ? join(moduleRoot, '@resvg/resvg-js') : '@resvg/resvg-js');
const output = dirname(fileURLToPath(import.meta.url));
const C = {
  ink: '#1F2926', muted: '#748078', line: '#DFE6E1', canvas: '#E9EEEA',
  paper: '#FAFAF8', white: '#FFFFFF', green: '#2E7D5B',
};
const esc = value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
const rect = (x, y, w, h, fill, r = 0, stroke = '', extra = '') => `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}" fill="${fill}" ${stroke ? `stroke="${stroke}"` : ''} ${extra}/>`;
const text = (x, y, value, size = 14, fill = C.ink, weight = 400, anchor = 'start') => `<text x="${x}" y="${y}" font-size="${size}" font-weight="${weight}" fill="${fill}" text-anchor="${anchor}">${esc(value)}</text>`;
const lines = (x, y, values, size = 14, fill = C.ink, step = 23, weight = 400) => values.map((value, i) => text(x, y + i * step, value, size, fill, weight)).join('');
const circle = (x, y, r, fill, stroke = '', sw = 1) => `<circle cx="${x}" cy="${y}" r="${r}" fill="${fill}" ${stroke ? `stroke="${stroke}" stroke-width="${sw}"` : ''}/>`;
let nextId = 0;

function icon(name, x, y, color = C.ink, size = 22) {
  const paths = {
    menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
    more: '<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
    down: '<path d="m6 9 6 6 6-6"/>',
    up: '<path d="M12 20V4m-6 6 6-6 6 6"/>',
    plus: '<path d="M12 4v16M4 12h16"/>',
    close: '<path d="m6 6 12 12M18 6 6 18"/>',
    archive: '<path d="M4 4h16v5H4zM6 9v11h12V9M10 13h4"/>',
    pencil: '<path d="m5 16-1 5 5-1L20 9l-4-4zM13 8l4 4"/>',
    image: '<rect x="3" y="3" width="18" height="18" rx="4"/><circle cx="8" cy="8" r="1.5"/><path d="m3 17 5-5 4 4 4-6 5 7"/>',
    settings: '<path d="m9 3-1 3-3 1 1 3-2 2 2 2-1 3 3 1 1 3h6l1-3 3-1-1-3 2-2-2-2 1-3-3-1-1-3z"/><circle cx="12" cy="12" r="3"/>',
  };
  return `<g transform="translate(${x} ${y}) scale(${size / 24})" stroke="${color}" stroke-width="1.7" fill="none" stroke-linecap="round" stroke-linejoin="round">${paths[name]}</g>`;
}

function status() {
  return text(23, 28, '9:41', 14, C.ink, 600) + text(325, 28, '5G', 11, C.ink, 500) +
    rect(347, 18, 3, 9, C.ink, 1) + rect(353, 15, 3, 12, C.ink, 1) + rect(359, 12, 3, 15, C.ink, 1) +
    rect(374, 14, 25, 12, 'none', 3, C.ink) + rect(377, 17, 18, 6, C.ink, 1) + rect(400, 18, 2, 4, C.ink, 1);
}

function header(tab = 0, title = '摄影学习笔记') {
  let s = status() + icon('menu', 19, 59) + text(58, 78, title, 20, C.ink, 500) + icon('more', 377, 59, C.muted);
  ['节点', '文档'].forEach((label, i) => {
    s += text(105 + i * 210, 119, label, 15, tab === i ? C.ink : C.muted, tab === i ? 500 : 400, 'middle');
  });
  return s + rect(tab * 210 + 92, 130, 26, 2, C.green, 1) + rect(0, 135, 420, .6, C.line);
}

function home() { return rect(150, 846, 120, 4, '#24332C', 2); }

function bottomDock(empty = false) {
  return rect(0, 792, 420, 46, C.paper) + rect(16, 792, 388, .6, C.line) +
    icon('image', 20, 806, C.muted, 19) + text(49, 821, empty ? '相册' : '新截图 2', 13, C.muted) + icon('down', empty ? 83 : 129, 808, C.muted, 15) +
    icon('plus', 296, 807, C.green, 18) + text(323, 822, '添加图片', 14, C.green) + home();
}

// 示例原图保留各自的真实比例，用于列表裁切、Markdown 和原图遮罩。
function sourceImage(kind = 'landscape') {
  if (kind === 'portrait') {
    return rect(0, 0, 360, 480, '#E8DFCA') + circle(264, 105, 44, '#FAF0D8') +
      '<path d="M0 235 102 120 213 269 292 196 360 264V480H0Z" fill="#ADB7A0"/>' +
      '<path d="M0 319 110 220 225 345 360 266V480H0Z" fill="#718D7D"/>' +
      '<path d="M0 393 111 331 221 405 360 335V480H0Z" fill="#496D5C"/>' +
      '<path d="M0 450 121 417 235 460 360 425V480H0Z" fill="#365747"/>' +
      '<path d="M150 480c-22-44-11-83 28-117 20-18 36-29 43-49" stroke="#D9C8A0" stroke-width="14" fill="none"/>';
  }
  return rect(0, 0, 640, 360, '#DAE4DB') + circle(468, 91, 38, '#F5EBD0') +
    '<path d="M0 211 128 113 254 232 365 139 496 238 640 158V360H0Z" fill="#A8B9A7"/>' +
    '<path d="M0 270 124 188 250 285 379 218 510 299 640 233V360H0Z" fill="#6F8F80"/>' +
    '<path d="M0 292c153-4 186 35 323 23s201-18 317 3v42H0Z" fill="#BCCEC6"/>' +
    '<path d="M0 332c95-22 125-24 204 28H0Z" fill="#365B48"/>' +
    '<path d="M640 308c-95-4-126 17-174 52h174Z" fill="#3D6653"/>' +
    '<path d="M461 280v49m-28-27 28-34 28 34Z" stroke="#355C46" stroke-width="4" fill="#355C46"/>';
}

function framedImage(x, y, w, h, kind = 'landscape', crop = true) {
  const clipId = `image-${nextId++}`;
  const viewBox = kind === 'portrait' ? '0 0 360 480' : '0 0 640 360';
  return `<defs><clipPath id="${clipId}">${rect(x, y, w, h, C.white, 12)}</clipPath></defs>` +
    `<g clip-path="url(#${clipId})"><svg x="${x}" y="${y}" width="${w}" height="${h}" viewBox="${viewBox}" preserveAspectRatio="xMidYMid ${crop ? 'slice' : 'meet'}">${sourceImage(kind)}</svg></g>`;
}

function imageNode(y, kind = 'landscape', settings = false) {
  const x = 52, w = 352, h = w * 3 / 4;
  const remark = kind === 'portrait' ? ['低角度看山，保留前景的层次。', '下次练习时对比不同机位。'] : ['把主体放在交点附近，', '留出视线朝向的空间。'];
  let s = circle(28, y + h / 2, 5, C.paper, '#9EADA0', 1.3) + framedImage(x, y, w, h, kind);
  s += `<path d="M${x} ${y+h-32}h${w}v20q0 12-12 12H${x+12}q-12 0-12-12z" fill="url(#remark-fade)"/>` + lines(x + 12, y + h - 18, remark, 11.5, C.white, 14);
  if (settings) s += circle(380, y + 26, 16, '#FFFFFF', '#E9EDE5', .5) + icon('settings', 370, y + 16, '#405345', 20);
  return s;
}

function textNode(y, values, chapter = false) {
  let s = circle(28, y + 36, 5, C.paper, '#9EADA0', 1.3) + rect(52, y, 352, 72, chapter ? '#EDF1E9' : '#F0F3EF', 12);
  if (chapter) s += text(66, y + 22, '章节', 11, C.muted) + text(66, y + 50, values[0], 17, '#42583F', 500);
  else s += lines(66, y + 28, values, 14, C.ink, 22);
  return s;
}

function nodes(settings = false) {
  const clipId = `node-list-${nextId++}`;
  let content = `<path d="M28 150V1030" stroke="${C.line}" stroke-width="1"/>`;
  content += textNode(150, ['记录视频里的构图方法，', '也记下自己练习时看到的细节。']) + textNode(238, ['构图练习'], true);
  content += imageNode(326, 'landscape', settings) + textNode(606, ['今天先练习三分法。', '拍完后再补充对比。']) + imageNode(694, 'portrait');
  return header() + `<defs><clipPath id="${clipId}">${rect(0, 145, 420, 638, C.paper)}</clipPath></defs><g clip-path="url(#${clipId})">${content}</g>` + bottomDock();
}

function documentPage() {
  let s = header(1) + text(20, 183, '摄影学习笔记', 25, C.ink, 600) + lines(20, 218, ['记录视频里的构图方法，', '也记下自己练习时看到的细节。'], 15, C.muted, 26);
  s += text(20, 286, '构图练习', 21, C.ink, 500) + framedImage(20, 310, 380, 380 * 9 / 16, 'landscape', false);
  s += lines(20, 559, ['把主体放在交点附近，', '留出视线朝向的空间。'], 15, C.ink, 26);
  s += lines(20, 628, ['今天先练习三分法。', '拍完后再补充对比。'], 15, C.ink, 26);
  s += framedImage(20, 688, 380, 380 * 4 / 3, 'portrait', false);
  return s + rect(0, 833, 420, 31, C.paper) + home();
}

function drawer() {
  let s = nodes() + rect(0, 0, 420, 864, '#14221C', 0, '', 'opacity="0.25"') + rect(0, 0, 356, 864, C.paper);
  s += text(23, 28, '9:41', 14, C.ink, 600) + text(24, 80, '我的文档', 22, C.ink, 500) + icon('plus', 274, 62, C.green, 18) + text(300, 78, '新建', 14, C.green, 500);
  s += text(24, 135, '最近', 12, C.muted) + rect(12, 154, 332, 88, '#EDF3EE', 12) + text(27, 185, '摄影学习笔记', 17, C.ink, 500) + icon('more', 307, 169, C.muted, 19) + text(27, 212, '7 个节点 · 当前文档', 12, C.muted);
  s += text(27, 285, '周末徒步', 17, C.ink) + text(27, 312, '12 个节点 · 昨天', 12, C.muted) + icon('more', 307, 269, C.muted, 19);
  s += text(27, 382, '阅读摘录', 17, C.ink) + text(27, 409, '6 个节点 · 周一', 12, C.muted) + icon('more', 307, 366, C.muted, 19);
  return s + rect(24, 754, 308, .6, C.line) + icon('archive', 25, 782, C.muted, 20) + text(60, 799, '已归档', 14, C.muted);
}

function editorHeader() {
  return rect(0, 0, 420, 104, '#F1F3EF') + status() + icon('close', 32, 61, C.muted, 21) + text(70, 78, '节点编辑', 18, C.ink, 500) + icon('more', 327, 61, C.muted, 22) + text(388, 78, '保存', 14, C.green, 500, 'end');
}

function editImage() {
  const shadowId = `picture-shadow-${nextId++}`, clipId = `editor-scroll-${nextId++}`;
  let s = `<defs><filter id="${shadowId}" x="-25%" y="-20%" width="150%" height="150%"><feGaussianBlur in="SourceAlpha" stdDeviation="12"/><feOffset dy="10"/><feComponentTransfer><feFuncA type="linear" slope="0.18"/></feComponentTransfer><feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge></filter><clipPath id="${clipId}">${rect(0, 104, 420, 722, C.paper)}</clipPath></defs>`;
  s += rect(0, 0, 420, 864, '#F1F3EF');
  let content = `<g filter="url(#${shadowId})">${framedImage(32, 112, 356, 356 * 4 / 3, 'portrait', false)}</g>`;
  content += text(32, 619, '用户备注', 14, C.ink, 500) + rect(32, 635, 356, 120, C.white, 10, '#D8E1D8') + lines(46, 662, ['低角度看山，保留前景的层次。', '下次练习时对比不同机位。'], 14, C.ink, 25);
  s += `<g clip-path="url(#${clipId})">${content}</g>`;
  return s + editorHeader() + home();
}

function newDocument() {
  let s = status() + icon('close', 32, 61, C.muted, 21) + text(70, 78, '新建文档', 18, C.ink, 500);
  s += text(32, 148, '标题', 14, C.ink, 500) + text(66, 148, '*', 14, C.green, 500) + rect(32, 166, 356, 56, C.white, 10, '#D8E1D8') + text(46, 201, '摄影学习笔记', 16, C.ink);
  s += text(32, 265, '首段文案', 14, C.ink, 500) + text(101, 265, '（可选）', 12, C.muted);
  s += rect(32, 283, 356, 132, C.white, 10, '#D8E1D8') + lines(46, 312, ['记录视频里的构图方法，', '也记下自己练习时看到的细节。'], 14, C.ink, 25);
  s += rect(32, 456, 356, 48, C.green, 12) + text(210, 486, '创建文档', 15, C.white, 500, 'middle');
  return s + home();
}

function insertMenu() {
  const shadowId = `menu-shadow-${nextId++}`;
  let s = nodes() + `<defs><filter id="${shadowId}" x="-30%" y="-40%" width="160%" height="200%"><feGaussianBlur in="SourceAlpha" stdDeviation="8"/><feOffset dy="5"/><feComponentTransfer><feFuncA type="linear" slope="0.10"/></feComponentTransfer><feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge></filter></defs>`;
  s += circle(28, 458, 11, '#E6EEE4') + circle(28, 458, 5, C.green);
  s += `<g filter="url(#${shadowId})">${rect(45, 425, 275, 225, C.white, 14)}</g>`;
  s += icon('up', 61, 446, C.muted, 18) + text(91, 461, '插入上一个节点', 15) + icon('down', 61, 502, C.muted, 18) + text(91, 517, '插入下一个节点', 15);
  s += rect(60, 537, 245, .6, '#EDF0EB');
  return s + icon('up', 61, 558, C.green, 18) + text(91, 573, '创建章节 · 插在前面', 15) + icon('down', 61, 614, C.green, 18) + text(91, 629, '创建章节 · 插在后面', 15);
}

function editText() {
  let s = rect(0, 0, 420, 864, '#F1F3EF') + text(32, 142, '文字内容', 14, C.ink, 500);
  s += rect(32, 160, 356, 210, C.white, 10, '#D8E1D8') + lines(46, 189, ['今天先练习三分法。', '拍完后再补充对比。'], 14, C.ink, 25);
  return s + editorHeader() + home();
}

function createChapter() {
  let s = rect(0, 0, 420, 864, '#F1F3EF') + status() + icon('close', 32, 61, C.muted, 21) + text(70, 78, '创建章节', 18, C.ink, 500) + text(388, 78, '保存', 14, C.green, 500, 'end');
  s += text(32, 142, '章节标题', 14, C.ink, 500) + rect(32, 160, 356, 56, C.white, 10, '#D8E1D8') + text(46, 195, '构图练习', 16, C.ink);
  s += icon('up', 32, 242, C.muted, 17) + text(58, 256, '插在当前节点前面', 13, C.muted);
  return s + home();
}

const screens = [
  ['01-节点页', '图片、纯文与章节', '纯文与章节固定 72dp · 图片固定 4:3', nodes],
  ['02-文档页', 'Markdown 文档', '本地按链序组织图片和用户文字', documentPage],
  ['03-文档抽屉', '文档导航', 'navMenu 右上角新建文档', drawer],
  ['04-节点编辑', '原图与备注编辑', '原图与阴影 · 备注输入 · 保存', editImage],
  ['05-新建文档', '标题与首段文案', '标题必填 · 首段可选 · 创建后添加素材', newDocument],
  ['06-节点插入菜单', '节点与章节前后插入', '章节固定插在所点节点的前面或后面', insertMenu],
  ['07-设置显隐', '设置入口显现态', '0.5s 渐显 → 停留 3s → 0.5s 渐隐', () => nodes(true)],
  ['08-文字节点编辑', '编辑无图纯文节点', '72dp 卡片预览，点击编辑完整文字', editText],
  ['09-创建章节', '创建章节', '填写标题，保存到选定的相邻位置', createChapter],
];

function phone(screen, index) {
  return `<defs><clipPath id="screen-${index}">${rect(30, 30, 420, 864, C.paper, 42)}</clipPath></defs>` + rect(20, 20, 440, 884, '#18221D', 52) +
    `<g clip-path="url(#screen-${index})">${rect(30, 30, 420, 864, C.paper)}<g transform="translate(30 30)">${screen[3]()}</g></g>` +
    text(240, 932, `${screen[0].split('-')[0]}  ${screen[1]}`, 16, C.ink, 600, 'middle') + text(240, 953, screen[2], 12, C.muted, 400, 'middle');
}

function svg(content, width, height) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" font-family="Hiragino Sans GB, Arial, sans-serif"><defs><linearGradient id="remark-fade" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#152218" stop-opacity="0.64"/><stop offset="1" stop-color="#152218" stop-opacity="0.86"/></linearGradient></defs>${rect(0, 0, width, height, C.canvas)}${content}</svg>`;
}

const render = (source, scale) => new Resvg(source, {
  fitTo: { mode: 'zoom', value: scale },
  font: { loadSystemFonts: true, defaultFontFamily: 'Hiragino Sans GB' },
}).render().asPng();

let board = text(24, 45, '竹节笔记 Bambootes  /  图片与 Markdown', 26, C.ink, 600) + text(24, 77, '标题与可选首段 · 72dp 纯文节点 · 章节前后插入', 16, C.muted);
screens.forEach((screen, i) => {
  const content = phone(screen, i);
  writeFileSync(join(output, `${screen[0]}.png`), render(svg(content, 480, 970), 2));
  board += `<g transform="translate(${24 + (i % 3) * 504} ${104 + Math.floor(i / 3) * 995})">${content}</g>`;
});
writeFileSync(join(output, '00-原型总览.png'), render(svg(board, 1536, 3100), 1));

let detailBoard = text(24, 45, '竹节笔记 Bambootes  /  纯文、章节与新建', 26, C.ink, 600) + text(24, 77, '72dp 纯文卡 · 标题与可选首段 · 章节前后插入', 16, C.muted);
[0, 4, 5].forEach((screenIndex, i) => {
  detailBoard += `<g transform="translate(${24 + i * 504} 104)">${phone(screens[screenIndex], `detail-${i}`)}</g>`;
});
writeFileSync(join(output, '10-纯文与新建总览.png'), render(svg(detailBoard, 1536, 1110), 1));
console.log(`Generated ${screens.length} PicNote screens and two overviews.`);
