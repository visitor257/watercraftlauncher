// Rename version-bound API call sites in src/main/java to our shims:
//   Text.literal(         -> Txt.of(
//   ButtonWidget.builder( -> Btn.builder(
// Keeps original encoding (UTF-8, BOM preserved if present).
import fs from 'node:fs';
import path from 'node:path';

const root = 'D:\\Development\\watercraftlauncher\\mod\\src\\main\\java\\dev\\mcver';
const dry = process.argv.includes('--dry');

function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (e.name.endsWith('.java')) out.push(p);
  }
  return out;
}

let changed = 0;
for (const file of walk(root)) {
  // The shims themselves delegate to the real APIs; never rewrite them.
  if (/\\(Btn|Txt)\.java$/.test(file)) continue;
  const buf = fs.readFileSync(file);
  const hasBom = buf.length >= 3 && buf[0] === 0xef && buf[1] === 0xbb && buf[2] === 0xbf;
  let text = buf.toString('utf8');
  if (hasBom) text = text.slice(1);
  const before = text;

  text = text.split('Text.literal(').join('Txt.of(');
  text = text.split('ButtonWidget.builder(').join('Btn.builder(');

  if (text !== before) {
    const inUi = /^package\s+dev\.mcver\.ui;/m.test(text);
    if (!inUi) {
      if (text.includes('Txt.of(') && !/^import\s+dev\.mcver\.ui\.Txt;/m.test(text)) {
        text = text.replace(/^(package\s+dev\.mcver;[ \t]*\r?\n)/m, (m) => m + '\nimport dev.mcver.ui.Txt;\n');
      }
      if (text.includes('Btn.builder(') && !/^import\s+dev\.mcver\.ui\.Btn;/m.test(text)) {
        text = text.replace(/^(package\s+dev\.mcver;[ \t]*\r?\n)/m, (m) => m + '\nimport dev.mcver.ui.Btn;\n');
      }
    }
    changed++;
    console.log((dry ? 'would rewrite: ' : 'rewrote: ') + path.relative(root, file));
    if (!dry) fs.writeFileSync(file, Buffer.concat([hasBom ? Buffer.from([0xef, 0xbb, 0xbf]) : Buffer.alloc(0), Buffer.from(text, 'utf8')]));
  }
}
console.log('files changed: ' + changed);
