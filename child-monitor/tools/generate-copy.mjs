import { readFileSync, writeFileSync } from 'node:fs';

// 独立工程的文案入口；无需安装 Node 依赖，不读取宿主组件仓库的业务文案。
const root = new URL('../', import.meta.url);
const catalog = JSON.parse(readFileSync(new URL('copy/catalog.json', root), 'utf8'));
if (catalog.formatVersion !== 1) throw new Error('Unsupported catalog version');
const names = new Set();
const ids = new Set();
const escape = (value) => value.replaceAll('&', '&amp;').replaceAll('<', '&lt;')
  .replaceAll('>', '&gt;').replaceAll("'", "\\'").replaceAll('"', '\\"').replaceAll('\n', '\\n');
const lines = catalog.entries.map((entry) => {
  const name = entry.platforms.android;
  if (!/^[a-z][a-z0-9_]*$/.test(name) || names.has(name) || ids.has(entry.id)) {
    throw new Error(`Invalid or duplicate entry: ${entry.id}`);
  }
  names.add(name);
  ids.add(entry.id);
  return `    <string name="${name}">${escape(entry.values['zh-Hans'])}</string>`;
});
const xml = '<?xml version="1.0" encoding="utf-8"?>\n'
  + '<!-- 由 copy/catalog.json 生成，请修改源文案后重新生成。 -->\n'
  + `<resources>\n${lines.join('\n')}\n</resources>\n`;
const output = new URL('androidApp/src/main/res/values/strings.xml', root);
if (process.argv.includes('--check')) {
  if (readFileSync(output, 'utf8') !== xml) throw new Error('Generated strings are out of date');
} else {
  writeFileSync(output, xml);
}
