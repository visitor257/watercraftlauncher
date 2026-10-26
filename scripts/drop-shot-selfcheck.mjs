// 删掉游戏内截图自检（Compat.saveScreenshot + MainScreen 的 MCVER_SHOT）。
//
// 原因：截图只是给我验证用的，从来不是给用户看的；界面验证早就换成
// verify-gui.ps1 用 PrintWindow 从真窗口外部抓图（更可信，不受游戏内渲染时机影响）。
// 留着它只有一个后果：saveScreenshot 的签名在 1.21.9 变过，白白多出一处版本断点。
//
// 做法：按行扫描找「方法签名行 -> 配对的大括号」，不用正则（上一条正则把整个类体吃掉了）。
import fs from 'node:fs';

const files = [
  'D:/Development/watercraftlauncher/mod/src/main/java/dev/mcver/core/Compat.java',
  'D:/Development/watercraftlauncher/mod/src/compat/1.16.5/java/dev/mcver/core/Compat.java',
  'D:/Development/watercraftlauncher/mod/src/compat/1.17.1/java/dev/mcver/core/Compat.java',
  'D:/Development/watercraftlauncher/mod/src/compat/1.18.2/java/dev/mcver/core/Compat.java',
];

function stripMethod(text, sigLine) {
  const lines = text.split('\n');
  const i = lines.findIndex(l => l.includes(sigLine));
  if (i < 0) return null;
  let end = -1;
  let depth = 0;
  for (let k = i; k < lines.length; k++) {
    for (const ch of lines[k]) {
      if (ch === '{') depth++;
      else if (ch === '}') depth--;
    }
    if (depth === 0 && k >= i) { end = k; break; }
  }
  if (end < 0) return null;
  // 往上把紧邻的 /** ... */ 注释也删掉
  let start = i;
  for (let k = i - 1; k >= 0; k--) {
    const t = lines[k].trim();
    if (t === '') continue;
    if (t === '*/') {
      let j = k;
      while (j >= 0 && !lines[j].trim().startsWith('/**')) j--;
      if (j >= 0) { start = j; }
      break;
    }
    break;
  }
  // 往上再吃掉一行空行
  while (start > 0 && lines[start - 1].trim() === '') start--;
  lines.splice(start, end - start + 1);
  // 收敛多余空行
  let out = lines.join('\n').replace(/\n{3,}/g, '\n\n');
  return out;
}

for (const p of files) {
  let s = fs.readFileSync(p, 'utf8');
  const crlf = s.includes('\r\n');
  let raw = crlf ? s.replace(/\r\n/g, '\n') : s;
  const out = stripMethod(raw, 'public static void saveScreenshot(String name)');
  if (out === null) {
    console.log(`跳过（没找到方法）：${p.split('/').slice(-4).join('/')}`);
    continue;
  }
  const body = crlf ? out.replace(/\n/g, '\r\n') : out;
  fs.writeFileSync(p, Buffer.from(body, 'utf8'));
  const left = (body.match(/saveScreenshot/g) || []).length;
  console.log(`已删：${p.split('/').slice(-4).join('/')}  残留 saveScreenshot=${left}`);
}
