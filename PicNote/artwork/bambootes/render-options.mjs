import { readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

// 复用 SVG 候选源，生成独立 PNG 和包含小尺寸效果的对比图。
const output = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const moduleRoot = process.env.PICNOTE_RENDER_MODULE_ROOT;
const { Resvg } = require(moduleRoot ? join(moduleRoot, '@resvg/resvg-js') : '@resvg/resvg-js');
const font = { loadSystemFonts: true, defaultFontFamily: 'Hiragino Sans GB' };
const options = [
  { file: '01-bamboo-pen', number: '01', name: '竹节笔', category: '书写 · 简洁 · 鲜明', description: '把竹节化作笔杆，竹叶点出自然气息。', ink: '#257956' },
  { file: '02-bamboo-page', number: '02', name: '竹叶书页', category: '图文 · 清雅 · 亲切', description: '竹节成为书脊，竹叶长在留白的纸页上。', ink: '#255E47' },
  { file: '03-bamboo-slips', number: '03', name: '竹简节点', category: '竹简 · 节点 · 秩序', description: '三段竹简并列，呼应逐节整理的笔记。', ink: '#204D3C' },
];
const sources = options.map(option => readFileSync(join(output, `${option.file}.svg`), 'utf8'));
const text = (x, y, value, size, color = '#193B2D', weight = 400, extra = '') =>
  `<text x="${x}" y="${y}" font-size="${size}" font-weight="${weight}" fill="${color}" ${extra}>${value}</text>`;
const icon = (index, x, y, size) => `<svg x="${x}" y="${y}" width="${size}" height="${size}" viewBox="0 0 512 512">${sources[index].replace(/^<svg[^>]*>/, '').replace(/<\/svg>\s*$/, '')}</svg>`;
const render = (svg, file) => writeFileSync(join(output, file), new Resvg(svg, { font }).render().asPng());

sources.forEach((svg, index) => render(svg, `${options[index].file}.png`));
const cards = options.map((option, index) => {
  const x = 60 + index * 530;
  return `<g transform="translate(${x} 214)">
    <rect width="500" height="756" rx="30" fill="#FFFFFF" stroke="#E5EAE5"/>
    ${text(46, 65, option.number, 20, option.ink, 600, 'font-family="Avenir Next, sans-serif"')}
    ${icon(index, 114, 68, 272)}
    ${text(38, 444, option.name, 30, '#193B2D', 600)}
    ${text(38, 482, option.category, 17, '#778378')}
    ${text(38, 523, option.description, 17, '#56675B')}
    <path d="M38 558H462" stroke="#E9ECE5"/>
    ${icon(index, 38, 588, 64)}
    ${text(119, 615, '竹节笔记', 19, '#193B2D', 500)}
    ${text(119, 643, 'Bambootes', 16, '#718075', 400, 'font-family="Avenir Next, sans-serif"')}
    ${icon(index, 358, 608, 40)}
    ${icon(index, 422, 624, 24)}
    ${text(38, 710, '桌面预览', 14, '#8A958C')}
    ${text(351, 710, '40 px', 13, '#8A958C', 400, 'font-family="Avenir Next, sans-serif"')}
    ${text(414, 710, '24 px', 13, '#8A958C', 400, 'font-family="Avenir Next, sans-serif"')}
  </g>`;
}).join('');
const board = `<svg xmlns="http://www.w3.org/2000/svg" width="1680" height="1040" viewBox="0 0 1680 1040">
  <title>竹节笔记 / Bambootes · 三款纯白背景图标候选</title>
  <rect width="1680" height="1040" fill="#FFFFFF"/>
  <g font-family="Hiragino Sans GB, PingFang SC, sans-serif">
    ${text(60, 67, 'B A M B O O T E S', 17, '#68806C', 500, 'font-family="Avenir Next, sans-serif"')}
    ${text(60, 131, '竹节笔记', 48, '#193B2D', 600)}
    ${text(285, 131, '/ Bambootes', 35, '#68806C', 400, 'font-family="Avenir Next, sans-serif"')}
    ${text(60, 176, '以竹为形，把灵感一节一节记下来。', 20, '#718075')}
    ${text(1620, 131, '纯白背景 · 3 个方向', 18, '#718075', 400, 'text-anchor="end"')}
    ${cards}
    ${text(60, 1010, 'SVG 矢量源  /  独立 PNG  /  桌面与小尺寸对比', 14, '#8A958C')}
  </g>
</svg>`;
writeFileSync(join(output, 'bambootes-icon-options-white.svg'), board);
render(board, 'bambootes-icon-options-white.png');
console.log('Rendered three Bambootes icon options and comparison board.');
