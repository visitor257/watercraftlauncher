/**
 * 最小可用的 NBT 编解码（往返无损），专门伺候 `level.dat`。
 *
 * 为什么自己写：`level.dat` 是 gzip(NBT)，而 node 生态里要么拉个库、要么只有读没有写。
 * 我们要做的只有「解出来改两个字段再原样编回去」，所以保留类型标签就够了：
 * 每个值都是 `{ t: tagType, v: value }`（List 额外带 `et` 元素类型），
 * 这样 round-trip 不会把 byte 写成 int、把 long 写成 double（Minecraft 对类型很敏感的）。
 *
 * 参考：https://minecraft.wiki/w/NBT_format
 */
import zlib from 'node:zlib';
import fs from 'node:fs';

export const TAG = {
  END: 0, BYTE: 1, SHORT: 2, INT: 3, LONG: 4, FLOAT: 5, DOUBLE: 6,
  BYTE_ARRAY: 7, STRING: 8, LIST: 9, COMPOUND: 10, INT_ARRAY: 11, LONG_ARRAY: 12,
};

export const B = (v) => ({ t: 1, v });
export const S = (v) => ({ t: 2, v });
export const I = (v) => ({ t: 3, v });
export const L = (v) => ({ t: 4, v });
export const F = (v) => ({ t: 5, v });
export const D = (v) => ({ t: 6, v });
export const STR = (v) => ({ t: 8, v });
export const LIST = (et, v) => ({ t: 9, et, v });
export const COMPOUND = (obj) => ({ t: 10, v: obj instanceof Map ? obj : new Map(Object.entries(obj || {})) });

/** @returns {{rootName: string, root: {t:number,v:Map}}} */
export function decode(buf) {
  let o = 0;
  const u1 = () => buf[o++];
  const i1 = () => { const v = buf.readInt8(o); o += 1; return v; };
  const i2 = () => { const v = buf.readInt16BE(o); o += 2; return v; };
  const i4 = () => { const v = buf.readInt32BE(o); o += 4; return v; };
  const i8 = () => { const v = buf.readBigInt64BE(o); o += 8; return v; };
  const f4 = () => { const v = buf.readFloatBE(o); o += 4; return v; };
  const f8 = () => { const v = buf.readDoubleBE(o); o += 8; return v; };
  const str = () => { const n = buf.readUInt16BE(o); o += 2; const v = buf.subarray(o, o + n).toString('utf8'); o += n; return v; };

  function payload(t) {
    switch (t) {
      case 1: return B(i1());
      case 2: return S(i2());
      case 3: return I(i4());
      case 4: return L(i8());
      case 5: return F(f4());
      case 6: return D(f8());
      case 7: { const n = i4(); const v = buf.subarray(o, o + n); o += n; return { t: 7, v }; }
      case 8: return STR(str());
      case 9: {
        const et = u1();
        const n = i4();
        const items = [];
        for (let i = 0; i < n; i++) items.push(payload(et));
        return LIST(et, items);
      }
      case 10: return compound();
      case 11: { const n = i4(); const v = []; for (let i = 0; i < n; i++) v.push(i4()); return { t: 11, v }; }
      case 12: { const n = i4(); const v = []; for (let i = 0; i < n; i++) v.push(i8()); return { t: 12, v }; }
      default: throw new Error('未知 NBT tag: ' + t + ' @' + o);
    }
  }
  function compound() {
    const m = new Map();
    for (;;) {
      const t = u1();
      if (t === 0) break;
      const k = str();
      m.set(k, payload(t));
    }
    return COMPOUND(m);
  }

  const tag = u1();
  const rootName = str();
  return { rootName, root: payload(tag) };
}

export function encode({ rootName, root }) {
  const parts = [];
  const u1 = (v) => parts.push(Buffer.from([v & 0xff]));
  const i2 = (v) => { const b = Buffer.alloc(2); b.writeInt16BE(v); parts.push(b); };
  const i4 = (v) => { const b = Buffer.alloc(4); b.writeInt32BE(v); parts.push(b); };
  const i8 = (v) => { const b = Buffer.alloc(8); b.writeBigInt64BE(BigInt(v)); parts.push(b); };
  const f4 = (v) => { const b = Buffer.alloc(4); b.writeFloatBE(v); parts.push(b); };
  const f8 = (v) => { const b = Buffer.alloc(8); b.writeDoubleBE(v); parts.push(b); };
  const str = (s) => { const b = Buffer.from(s, 'utf8'); const n = Buffer.alloc(2); n.writeUInt16BE(b.length); parts.push(n, b); };

  function payload(node) {
    switch (node.t) {
      case 1: u1(node.v); break;
      case 2: i2(node.v); break;
      case 3: i4(node.v); break;
      case 4: i8(node.v); break;
      case 5: f4(node.v); break;
      case 6: f8(node.v); break;
      case 7: i4(node.v.length); parts.push(node.v); break;
      case 8: str(node.v); break;
      case 9: {
        u1(node.et);
        i4(node.v.length);
        for (const it of node.v) payload(it);
        break;
      }
      case 10: {
        for (const [k, v] of node.v) { u1(v.t); str(k); payload(v); }
        u1(0);
        break;
      }
      case 11: i4(node.v.length); for (const x of node.v) i4(x); break;
      case 12: i4(node.v.length); for (const x of node.v) i8(x); break;
      default: throw new Error('未知 NBT tag: ' + node.t);
    }
  }

  u1(root.t);
  str(rootName);
  payload(root);
  return Buffer.concat(parts);
}

export function readLevelDat(file) {
  return decode(zlib.gunzipSync(fs.readFileSync(file)));
}

export function writeLevelDat(file, tree) {
  fs.writeFileSync(file, zlib.gzipSync(encode(tree)));
}

/** 便捷：拿到 Data 那个 compound（Minecraft 的 level.dat 一定是 Root -> Data）。 */
export function dataOf(tree) {
  const data = tree.root.v.get('Data');
  if (!data) throw new Error('level.dat 里没有 Data');
  return data.v;
}
