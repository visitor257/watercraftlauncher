// Patch Forge 1.7.2's CoreModManager.sortTweakList so it stops invalidating
// launchwrapper's iterator on Java 8.
//
//   node scripts/lwfix172.mjs <CoreModManager.class> <stageDir>
//
// Two edits, both minimal:
//   1. six appended constant-pool entries: a Methodref to our helper
//   2. inside sortTweakList()V, the invokestatic operand is retargeted from
//      java/util/Collections.sort(List,Comparator) to that Methodref
// Everything else in the class stays byte-identical, and the helper class goes into
// the same jar (cpw/mods/fml/relauncher/McverSortFix) so no classpath change is needed.
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { execFileSync } from 'node:child_process';

const HELPER_SRC = 'modx/lwfix/cpw/mods/fml/relauncher/McverSortFix.java';
const HELPER_CLASS = 'cpw/mods/fml/relauncher/McverSortFix.class';

const classPath = process.argv[2];
const stageDir = process.argv[3];
if (!classPath) {
  console.error('usage: node scripts/lwfix172.mjs <CoreModManager.class> <stageDir>');
  console.error('       node scripts/lwfix172.mjs --emit-helper <outDir>   # 只编译助手类');
  process.exit(2);
}

function findJavac() {
  const roots = ['D:/Program Files/Java', 'C:/Program Files/Java', 'C:/Program Files/Eclipse Adoptium'];
  const found = [];
  for (const root of roots) {
    let dirs = [];
    try { dirs = fs.readdirSync(root); } catch { continue; }
    for (const d of dirs) {
      const p = path.join(root, d, 'bin', 'javac.exe');
      if (fs.existsSync(p)) found.push(p);
    }
  }
  if (!found.length) throw new Error('no javac found');
  return found.sort().pop();
}

// --- minimal class file reader -------------------------------------------------

function parsePool(buf) {
  const pool = [];
  let p = 8;
  const count = buf.readUInt16BE(p);
  p += 2;
  for (let i = 1; i < count; i++) {
    const start = p;
    const tag = buf[p++];
    switch (tag) {
      case 1: { const len = buf.readUInt16BE(p); p += 2 + len; break; }
      case 7: case 8: case 16: case 19: case 20: p += 2; break;
      case 15: p += 3; break;
      case 3: case 4: case 9: case 10: case 11: case 12: case 17: case 18: p += 4; break;
      case 5: case 6:
        p += 8;
        pool[i] = { tag, start, end: p };
        i++;
        continue;
      default:
        throw new Error(`unsupported constant pool tag ${tag} at entry ${i}`);
    }
    pool[i] = { tag, start, end: p };
  }
  return { pool, poolEnd: p, count };
}

const utf8 = (buf, pool, idx) => {
  if (!idx) return '';
  const e = pool[idx];
  if (!e || e.tag !== 1) throw new Error(`entry ${idx} is not a Utf8`);
  return buf.toString('utf8', e.start + 3, e.end);
};
const className = (buf, pool, idx) => {
  const e = pool[idx];
  if (!e || e.tag !== 7) throw new Error(`entry ${idx} is not a Class`);
  return utf8(buf, pool, buf.readUInt16BE(e.start + 1));
};
const nameAndType = (buf, pool, idx) => {
  const e = pool[idx];
  if (!e || e.tag !== 12) throw new Error(`entry ${idx} is not a NameAndType`);
  return [utf8(buf, pool, buf.readUInt16BE(e.start + 1)), utf8(buf, pool, buf.readUInt16BE(e.start + 3))];
};

/** Returns the {@code methods} table as [{name, desc, attrCount, attrStart}]. */
function readMethods(buf, pool, poolEnd) {
  let p = poolEnd + 6;                             // access, this, super
  const ifCount = buf.readUInt16BE(p); p += 2 + 2 * ifCount;
  let methods = null;
  for (let pass = 0; pass < 2; pass++) {           // pass 0 = fields, pass 1 = methods
    const n = buf.readUInt16BE(p); p += 2;
    const out = [];
    for (let i = 0; i < n; i++) {
      const name = utf8(buf, pool, buf.readUInt16BE(p + 2));
      const desc = utf8(buf, pool, buf.readUInt16BE(p + 4));
      p += 6;
      const attrCount = buf.readUInt16BE(p); p += 2;
      out.push({ name, desc, attrCount, attrStart: p });
      for (let a = 0; a < attrCount; a++) {
        p += 2;
        const len = buf.readUInt32BE(p); p += 4 + len;
      }
    }
    if (pass === 1) methods = out;
  }
  return methods;
}

function findCode(buf, pool, member) {
  let p = member.attrStart;
  for (let a = 0; a < member.attrCount; a++) {
    const nameIdx = buf.readUInt16BE(p);
    const len = buf.readUInt32BE(p + 2);
    const body = p + 6;
    if (utf8(buf, pool, nameIdx) === 'Code') {
      return { codeStart: body + 8, codeLen: buf.readUInt32BE(body + 4) };
    }
    p = body + len;
  }
  return null;
}

const u2 = (v) => { const b = Buffer.alloc(2); b.writeUInt16BE(v); return b; };
const utf8Entry = (s) => { const b = Buffer.from(s, 'utf8'); return [Buffer.from([1]), u2(b.length), b]; };

// --- patch ---------------------------------------------------------------------

// --emit-helper：只把助手类编出来（构建 mod 时随包打进资源）
if (classPath === '--emit-helper') {
  const out = stageDir;
  if (!out) throw new Error('--emit-helper 需要输出目录');
  const javac = findJavac();
  fs.mkdirSync(out, { recursive: true });
  execFileSync(javac, ['--release', '8', '-nowarn', '-encoding', 'UTF-8', '-d', out, HELPER_SRC], { stdio: 'inherit' });
  console.log('helper -> ' + path.join(out, HELPER_CLASS));
  process.exit(0);
}

const buf = fs.readFileSync(classPath);
const { pool, poolEnd, count } = parsePool(buf);

let sortRef = 0;
for (let i = 1; i < count; i++) {
  const e = pool[i];
  if (!e || e.tag !== 10) continue;
  const [name, desc] = nameAndType(buf, pool, buf.readUInt16BE(e.start + 3));
  if (name === 'sort' && desc === '(Ljava/util/List;Ljava/util/Comparator;)V'
      && className(buf, pool, buf.readUInt16BE(e.start + 1)) === 'java/util/Collections') {
    sortRef = i;
    break;
  }
}
if (!sortRef) throw new Error('no Collections.sort(List,Comparator) reference in constant pool');
if (pool[sortRef]) { /* keep for clarity */ }

const target = readMethods(buf, pool, poolEnd).find((m) => m.name === 'sortTweakList' && m.desc === '()V');
if (!target) throw new Error('sortTweakList()V not found');
const code = findCode(buf, pool, target);
if (!code) throw new Error('sortTweakList has no Code attribute');

let operandAt = -1;
for (let i = 0; i + 2 < code.codeLen; i++) {
  if (buf[code.codeStart + i] === 0xb8 && buf.readUInt16BE(code.codeStart + i + 1) === sortRef) {
    operandAt = code.codeStart + i + 1;
    break;
  }
}
if (operandAt < 0) throw new Error('invokestatic Collections.sort not found inside sortTweakList');

const base = count;
const helperRef = base + 5;
const appended = Buffer.concat([
  ...utf8Entry('cpw/mods/fml/relauncher/McverSortFix'),      // base+0
  Buffer.from([7]), u2(base),                                // base+1 Class
  ...utf8Entry('sort'),                                      // base+2
  ...utf8Entry('(Ljava/util/List;Ljava/lang/Object;)V'),     // base+3
  Buffer.from([12]), u2(base + 2), u2(base + 3),             // base+4 NameAndType
  Buffer.from([10]), u2(base + 1), u2(base + 4),             // base+5 Methodref
]);

const header = Buffer.from(buf.subarray(0, 10));             // magic, minor, major, cp_count
header.writeUInt16BE(count + 6, 8);
const poolRaw = buf.subarray(10, poolEnd);
const rest = Buffer.from(buf.subarray(poolEnd));
rest.writeUInt16BE(helperRef, operandAt - poolEnd);

const patched = Buffer.concat([header, poolRaw, appended, rest]);
console.log(`CoreModManager: sortRef #${sortRef} -> McverSortFix.sort #${helperRef}; ${buf.length} -> ${patched.length} bytes`);

// --- compile helper, stage both files ------------------------------------------

const javac = findJavac();
const outDir = path.join(os.tmpdir(), 'mcver-lwfix-' + Date.now());
fs.mkdirSync(outDir, { recursive: true });
execFileSync(javac, ['--release', '8', '-nowarn', '-encoding', 'UTF-8', '-d', outDir, HELPER_SRC], { stdio: 'inherit' });
const helper = fs.readFileSync(path.join(outDir, HELPER_CLASS));
console.log(`helper McverSortFix.class ${helper.length} bytes`);

const target0 = path.join(stageDir, 'cpw/mods/fml/relauncher');
fs.mkdirSync(target0, { recursive: true });
fs.writeFileSync(path.join(stageDir, 'cpw/mods/fml/relauncher/CoreModManager.class'), patched);
fs.writeFileSync(path.join(stageDir, HELPER_CLASS), helper);
fs.writeFileSync(path.join(stageDir, 'mcver-lwfix172.marker'), 'forge172-sortfix\n');
console.log('staged -> ' + stageDir);
