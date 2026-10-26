// 给 5 个旧 target 的 ChromeScreen 补 keyPressedDefault 钩子。
// 起因：1.21.11 把 Screen#keyPressed 换成了 keyPressed(KeyInput)，为了不让 UI 层知道这事，
// 主 ChromeScreen 把「键盘」收口成 keyPressedDefault(int,int,int)，MainScreen 改成 override 它。
// → 所有 compat 的 ChromeScreen 必须同时提供 keyPressedDefault 和三 int 的 keyPressed。
// 这 5 个版本（1.16.5 ~ 1.20.1）的签名完全一样，所以用同一段代码。
import fs from 'node:fs';

const hook = `
    /** 键盘：UI 层只 override 这个；1.21.11 的 KeyInput 差异在它自己的 compat 里。 */
    protected boolean keyPressedDefault(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return keyPressedDefault(keyCode, scanCode, modifiers);
    }
`;

for (const v of ['1.16.5', '1.17.1', '1.18.2', '1.19.4', '1.20.1']) {
  const p = `D:/Development/watercraftlauncher/mod/src/compat/${v}/java/dev/mcver/ui/ChromeScreen.java`;
  let s = fs.readFileSync(p, 'utf8');
  const crlf = s.includes('\r\n');
  if (crlf) s = s.replace(/\r\n/g, '\n');
  if (s.includes('keyPressedDefault')) {
    console.log(`${v}: 已有，跳过`);
    continue;
  }
  const i = s.lastIndexOf('}');
  if (i < 0) throw new Error(`${v}: 找不到结尾大括号`);
  s = s.slice(0, i).replace(/\n+$/, '\n') + hook + '}\n';
  if (crlf) s = s.replace(/\n/g, '\r\n');
  fs.writeFileSync(p, Buffer.from(s, 'utf8'));
  console.log(`${v}: 已补`);
}
