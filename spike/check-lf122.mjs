// 拿 Legacy Fabric 的 1.12.2 yarn 映射，逐个核对 watercraftlauncher 用到的 MC 类还在不在。
import fs from 'node:fs';

const lines = fs.readFileSync('spike/lf-yarn/mappings.tiny', 'utf8').split('\n');
const named = new Set();
for (const l of lines) {
  if (l.startsWith('c\t')) {
    const p = l.split('\t');
    if (p[2]) named.add(p[2]);
  }
}

const used = [
  'net/minecraft/client/MinecraftClient',
  'net/minecraft/client/font/TextRenderer',
  'net/minecraft/client/gui/DrawableHelper',
  'net/minecraft/client/gui/DrawContext',
  'net/minecraft/client/gui/screen/Screen',
  'net/minecraft/client/gui/screen/TitleScreen',
  'net/minecraft/client/gui/widget/ButtonWidget',
  'net/minecraft/client/gui/widget/ClickableWidget',
  'net/minecraft/client/gui/widget/TextFieldWidget',
  'net/minecraft/client/option/KeyBinding',
  'net/minecraft/client/util/InputUtil',
  'net/minecraft/client/util/math/MatrixStack',
  'net/minecraft/item/Item',
  'net/minecraft/item/ItemGroups',
  'net/minecraft/item/ItemStack',
  'net/minecraft/registry/Registries',
  'net/minecraft/registry/Registry',
  'net/minecraft/registry/RegistryKey',
  'net/minecraft/registry/RegistryKeys',
  'net/minecraft/text/MutableText',
  'net/minecraft/text/Text',
  'net/minecraft/util/ActionResult',
  'net/minecraft/util/Identifier',
  'net/minecraft/util/TypedActionResult',
  'net/minecraft/util/Util',
  'net/minecraft/util/registry/Registry',
];

let have = 0;
for (const u of used) {
  const ok = named.has(u);
  if (ok) have++;
  console.log((ok ? '  有  ' : '  没有') + ' ' + u);
}
console.log(`\n${have}/${used.length} 直接同名`);

// 给几个「没有」的找找 1.12.2 里的等价物
const probe = (re, label) => {
  const hits = [...named].filter((n) => re.test(n)).slice(0, 8);
  console.log(`${label}: ${hits.length ? hits.join(', ') : '（无）'}`);
};
console.log('');
probe(/^net\/minecraft\/client\/gui\/(screen|widget)\/.*(Button|Slider|TextField)/, 'Button 族');
probe(/^net\/minecraft\/text\/.*/, 'Text 族（前 8）');
probe(/^net\/minecraft\/util\/(Identifier|ActionResult|TypedActionResult)/, 'util 关键类');
probe(/^net\/minecraft\/registry\//, 'registry 包（前 8）');
probe(/^net\/minecraft\/client\/util\/math\//, 'math 包（前 8）');
