// 把 yarn 名（Fabric 源）机械转成 mojmap 名（Forge/NeoForge 源）。
//
// 为什么要有这个脚本：Fabric 那套源码是按 yarn 命名写并验证过的（17 个 target 全绿），
// 不想为了 Forge/NeoForge 再手抄一遍页面代码。两个 loader 的差别只在**命名**和入口，
// 页面代码本身（DownloadPage/LaunchPage/...）一行逻辑都不用改，所以机械转换就行。
//
// 用法：
//   node scripts/mk-mojmap-src.mjs <outDir> <root1>[;<root2>...]
// 例：
//   node scripts/mk-mojmap-src.mjs modx/common/java mod/src/main/java
//   node scripts/mk-mojmap-src.mjs modx/common-1.20/java mod/src/main/java;mod/src/compat/1.20/java
//
// 覆盖的输出目录会整个重建（派生产物，不当源拜）。

import { readFileSync, writeFileSync, mkdirSync, rmSync, readdirSync, statSync } from 'node:fs';
import { join, relative, dirname } from 'node:path';

const [outDir, rootsArg] = process.argv.slice(2);
if (!outDir || !rootsArg) {
  console.error('用法: node scripts/mk-mojmap-src.mjs <outDir> <root1>[;<root2>...]');
  process.exit(2);
}
const roots = rootsArg.split(';').filter(Boolean);

// ---- 包路径（必须先做：长前缀优先，避免 net.minecraft.util.Util 被 net.minecraft.util.Hand 之类搅乱）
const PACKAGES = [
  ['net.minecraft.client.MinecraftClient', 'net.minecraft.client.Minecraft'],
  ['net.minecraft.client.font.TextRenderer', 'net.minecraft.client.gui.Font'],
  ['net.minecraft.client.gui.DrawContext', 'net.minecraft.client.gui.GuiGraphics'],
  ['net.minecraft.client.gui.DrawableHelper', 'net.minecraft.client.gui.Gui'],
  ['net.minecraft.client.gui.screen.', 'net.minecraft.client.gui.screens.'],
  ['net.minecraft.client.gui.widget.ButtonWidget', 'net.minecraft.client.gui.components.Button'],
  ['net.minecraft.client.gui.widget.ClickableWidget', 'net.minecraft.client.gui.components.AbstractWidget'],
  ['net.minecraft.client.gui.widget.TextFieldWidget', 'net.minecraft.client.gui.components.EditBox'],
  ['net.minecraft.client.util.math.MatrixStack', 'com.mojang.blaze3d.vertex.PoseStack'],
  ['net.minecraft.client.option.KeyBinding', 'net.minecraft.client.KeyMapping'],
  ['net.minecraft.item.ItemGroups', 'net.minecraft.world.item.CreativeModeTabs'],
  ['net.minecraft.item.ItemStack', 'net.minecraft.world.item.ItemStack'],
  ['net.minecraft.item.Item', 'net.minecraft.world.item.Item'],
  ['net.minecraft.registry.Registries', 'net.minecraft.core.registries.BuiltInRegistries'],
  ['net.minecraft.registry.RegistryKeys', 'net.minecraft.core.registries.Registries'],
  ['net.minecraft.registry.RegistryKey', 'net.minecraft.resources.ResourceKey'],
  ['net.minecraft.registry.Registry', 'net.minecraft.core.Registry'],
  ['net.minecraft.util.registry.Registry', 'net.minecraft.core.Registry'],
  ['net.minecraft.text.MutableText', 'net.minecraft.network.chat.MutableComponent'],
  ['net.minecraft.text.Text', 'net.minecraft.network.chat.Component'],
  ['net.minecraft.util.Hand', 'net.minecraft.world.InteractionHand'],
  ['net.minecraft.util.Identifier', 'net.minecraft.resources.ResourceLocation'],
  ['net.minecraft.util.Util', 'net.minecraft.Util'],
];

// ---- 标识符 / 方法名。顺序要紧：长名字先换，否则 Text 先把 TextFieldWidget 啃掉一半。
const SYMBOLS = [
  ['TextFieldWidget', 'EditBox'],
  ['TextRenderer', 'Font'],
  ['ClickableWidget', 'AbstractWidget'],
  ['ButtonWidget', 'Button'],
  ['MutableText', 'MutableComponent'],
  ['MatrixStack', 'PoseStack'],
  ['DrawContext', 'GuiGraphics'],
  ['MinecraftClient', 'Minecraft'],
  ['Identifier', 'ResourceLocation'],
  ['InteractionHand', 'InteractionHand'], // 已经是新的，占位
  ['Hand.MAIN_HAND', 'InteractionHand.MAIN_HAND'],
  ['Hand.OFF_HAND', 'InteractionHand.OFF_HAND'],
  ['Registries.ITEM', 'BuiltInRegistries.ITEM'],
  [/\bHand\b/, 'InteractionHand'],
  [/\bText\b/, 'Component'],
  [/\bRegistries\b/, 'BuiltInRegistries'],
  // fluent API / 钩子名
  ['Button.PressAction', 'Button.OnPress'],
  ['Button.builder', 'Button.builder'],
  ['.setText(', '.setValue('],
  ['.getText()', '.getValue()'],
  ['.wasPressed()', '.consumeClick()'],
  ['.setChangedListener(', '.setResponder('],
  ['Minecraft.getInstance().getSession().getUsername()', 'dev.mcver.core.Compat.sessionName()'],
  ['.getWidth(', '.width('],
  ['.trimToWidth(', '.plainSubstrByWidth('],
  ['mc.execute(mc::scheduleStop)', 'mc.execute(mc::stop)'],
  ['McVerMod.DOWNLOAD_TOOL', 'McVerMod.tool()'],
  ['addDrawableChild', 'addRenderableWidget'],
  ['clearChildren', 'clearWidgets'],
  ['.textRenderer', '.font'],
  ['.keyboard.setClipboard', '.keyboardHandler.setClipboard'],
  ['Util.getOperatingSystem().open(', 'Util.getPlatform().openUri('],
  ['runDirectory', 'gameDirectory'],
  ['Item.Settings', 'Item.Properties'],
  ['.maxCount(', '.stacksTo('],
  ['Registries.ITEM.getId(', 'BuiltInRegistries.ITEM.getKey('],
  ['Text.literal', 'Component.literal'],
  ['Component.literal', 'Component.literal'],
  ['sessionUuid', 'sessionUuid'],
];

function transform(src) {
  let s = src;
  // 去掉 BOM
  if (s.charCodeAt(0) === 0xfeff) s = s.slice(1);
  for (const [from, to] of PACKAGES) s = s.split(from).join(to);
  for (const [from, to] of SYMBOLS) {
    if (from instanceof RegExp) s = s.replace(new RegExp(from.source, 'g'), to);
    else s = s.split(from).join(to);
  }
  return s;
}

function walk(dir, acc = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p, acc);
    else if (name.endsWith('.java')) acc.push(p);
  }
  return acc;
}

rmSync(outDir, { recursive: true, force: true });
let n = 0;
for (const root of roots) {
  for (const file of walk(root)) {
    const rel = relative(root, file);
    const dest = join(outDir, rel);
    mkdirSync(dirname(dest), { recursive: true });
    writeFileSync(dest, transform(readFileSync(file, 'utf8')), 'utf8');
    n++;
  }
}
console.log(`[mojmap] ${n} 个文件 -> ${outDir}`);
