// 一次性机械改动：给 1.15 铺路（把 main 里「只在 1.16+ 存在」的三处写法定型成 shim 调用）。
// 1) ClickableWidget 泛型 → Object（1.15 没有 ClickableWidget 这个类）
// 2) TextFieldWidget(..., Text) → Field.of(..., String)（1.15 的 ctor 收 String）
// 3) button.setMessage(Text) → Btn.label(button, String)（1.15 收 String）
// 4) TextRenderer#getWidth(String) → Measure.width(tr, String)（1.15 只有 getStringWidth）
// 幂等：已改过的不会再匹配。
import { readFileSync, writeFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = 'D:/Development/watercraftlauncher/mod/src';
const UI = join(ROOT, 'main/java/dev/mcver/ui');
let changed = 0;
const log = [];

function edit(path, fn) {
  const before = readFileSync(path, 'utf8');
  const after = fn(before);
  if (after !== before) {
    writeFileSync(path, after, 'utf8');
    changed++;
    log.push(path.replace(ROOT, ''));
  }
}

// ---- 1) ChromeScreen overlays + main: addWidget(Object)
const chrome = [
  join(UI, 'ChromeScreen.java'),
  ...['1.15', '1.16', '1.17', '1.18', '1.19', '1.19.3', '1.20']
    .map((t) => join(ROOT, `compat/${t}/java/dev/mcver/ui/ChromeScreen.java`)),
];
for (const p of chrome) {
  try {
    statSync(p);
  } catch {
    continue;
  }
  edit(p, (s) =>
    s.replace(
      /protected <T extends (?:net\.minecraft\.client\.gui\.widget\.)?ClickableWidget> T addWidget\(T w\) \{\n\s*return (addChild|addDrawableChild)\(w\);\n(\s*)\}/,
      (_m, call) => `protected void addWidget(Object w) {\n        ${call}((ClickableWidget) w);\n    }`,
    ),
  );
}

// ---- 2) MainScreen.add / Page.add → Object
edit(join(UI, 'MainScreen.java'), (s) =>
  s.replace(
    /public <T extends ClickableWidget> T add\(T w\) \{\n\s*return addWidget\(w\);\n(\s*)\}/,
    'public void add(Object w) {\n        addWidget(w);\n    }',
  ),
);
edit(join(UI, 'Page.java'), (s) =>
  s.replace(
    /protected <T extends ClickableWidget> T add\(T w\) \{\n\s*return host\.add\(w\);\n(\s*)\}/,
    'protected void add(Object w) {\n        host.add(w);\n    }',
  ),
);

// ---- 3) TextFieldWidget(tr, x, y, w, h, Txt.of("...")) → Field.of(tr, x, y, w, h, "...")
const pageFiles = readdirSync(UI).filter((f) => f.endsWith('.java')).map((f) => join(UI, f));
for (const p of pageFiles) {
  edit(p, (s) =>
    s.replace(
      /new TextFieldWidget\((this\.tr\(\)|tr\(\)), ([^;]*?), (Txt\.of\([^;]*\))\);/g,
      (_m, tr, dims, txt) => `Field.of(${tr}, ${dims}, ${txt.replace(/^Txt\.of\(/, '').replace(/\)$/, '')});`,
    ),
  );
}

// ---- 4) x.setMessage(Txt.of("...")) → Btn.label(x, "...")
for (const p of pageFiles) {
  edit(p, (s) =>
    s.replace(/(\b\w+)\.setMessage\(Txt\.of\((.*)\)\);/g, (_m, v, t) => `Btn.label(${v}, ${t});`),
  );
}

// ---- 5) tr().getWidth(s) / this.textRenderer.getWidth(s) → Measure.width(tr, s)
for (const p of pageFiles) {
  edit(p, (s) =>
    s
      .replace(/this\.textRenderer\.getWidth\((\w+)\)/g, 'Measure.width(this.textRenderer, $1)')
      .replace(/tr\(\)\.getWidth\((\w+)\)/g, 'Measure.width(tr(), $1)'),
  );
}

console.log(`改到 ${changed} 个文件`);
for (const l of log) console.log('  ' + l);
