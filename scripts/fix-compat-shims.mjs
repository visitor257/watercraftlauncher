// 6 个老 compat 层的收尾：补 ClickableWidget import、给 Btn 补 label(button, String)。
import { readFileSync, writeFileSync, existsSync } from 'node:fs';

const dirs = ['1.16', '1.17', '1.18', '1.19', '1.19.3', '1.20'];
const IMP = 'import net.minecraft.client.gui.widget.ClickableWidget;';

for (const d of dirs) {
  const cs = `mod/src/compat/${d}/java/dev/mcver/ui/ChromeScreen.java`;
  let s = readFileSync(cs, 'utf8');
  if (s.includes('(ClickableWidget)') && !s.includes(IMP)) {
    s = s.replace('import net.minecraft.client.gui.screen.Screen;', 'import net.minecraft.client.gui.screen.Screen;\n' + IMP);
    writeFileSync(cs, s, 'utf8');
    console.log('import ->', d);
  }

  const bp = `mod/src/compat/${d}/java/dev/mcver/ui/Btn.java`;
  if (!existsSync(bp)) continue;
  let b = readFileSync(bp, 'utf8');
  if (!b.includes('static void label')) {
    b = b.replace(
      /(\n    public static Builder builder\()/,
      '\n    /** 改按钮文字（1.16+ 的 setMessage 收 Text，见 dev.mcver.ui.Txt）。 */\n' +
        '    public static void label(ButtonWidget button, String s) {\n' +
        '        button.setMessage(Txt.of(s));\n' +
        '    }\n$1',
    );
    writeFileSync(bp, b, 'utf8');
    console.log('label ->', d);
  }
}
