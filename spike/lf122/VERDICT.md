# Spike：1.12.2（Legacy Fabric）能不能做？

## Verdict: VALIDATED（做得到，但有明确代价）

问题：Water Craft Launcher 的 mod 本体能不能支持 MC 1.12.2？如果不能，1.12.2 的「安装」链路能不能单独支持？

## 结论一：1.12.2 没有官方 Fabric，但有 Legacy Fabric

- `meta.fabricmc.net/v2/versions/loader/1.12.2` → **HTTP 400**；官方 game 列表最老 = 1.14。
- `meta.legacyfabric.net/v2/versions/loader/1.12.2` → **HTTP 200**，57 个 yarn 构建、76 个游戏版本（最老 1.3）。
- Legacy Fabric 的 profile json 与官方**结构完全一致**（每个 library 带 `url`），所以安装链路不用分叉。

## 结论二：安装链路已经能装 1.12.2 + Fabric（本次已实现并实测）

`ProbeInstall testgame122 1.12.2 FABRIC - NONE - legacy-fabric 1` 全绿：

```
[probe] 规则表 FABRIC x 1.12.2 => OK
[job] Fabric loader 0.20.1 / MC 1.12.2
[job] profile 里有 11 个库
[job] 库下载完成：新下 1（含 natives 1），已有 10
[probe] complete=true
```

`ProbeLaunch testgame122 legacy-fabric`：

```
[probe] mainClass=net.fabricmc.loader.impl.launch.knot.KnotClient
[probe] requiredJava=8
[probe] java=E:\Program Files\Java\jre-1.8\bin\java.exe
[probe] OK 到达 Setting user
```

关键坑：`org.lwjgl.lwjgl:lwjgl-platform:2.9.4+legacyfabric.17` **没有 plain jar**，只有
`-natives-windows.jar`。`writeProfile` 原来不看 `natives` 映射 → 404 → 整个安装失败。
修法：带 `natives` 的库只下 classifier 包并解压到 `versions/<id>/natives`，不进 classpath。

## 结论三：mod 本体上 1.12.2 = 一层新 compat（不是重写，但也不小）

拿 legacy yarn（1.12.2+build.604）逐个核对主源码用到的 26 个 MC 类：**16 个同名**。

| 主源码用的 | 1.12.2 | 处理 |
|---|---|---|
| `Screen` / `ButtonWidget` / `TextFieldWidget` / `TextRenderer` / `DrawableHelper` / `MinecraftClient` / `Text` / `Identifier` / `Util` | 同名 | 直接用 |
| `LiteralText` | 同名（`MutableText` 不存在）| `Txt` 适配 |
| `ItemStack` / `Item` | 同名 | `new Item()`（**没有** `Item.Settings`）+ `setItemGroup` |
| `Registries.REGISTRY` / `RegistryKey(s)` | 不存在 | `Item.REGISTRY.put(Identifier, item)`（`SimpleRegistry`）|
| `ItemGroup` | `net.minecraft.item.itemgroup.ItemGroup` | 包名不同 |
| `DrawContext` / `MatrixStack` | 不存在（立即模式 GL）| `Ctx` 用 `DrawableHelper.fill` + `TextRenderer.draw(String,…)` |
| `ClickableWidget` | 不存在 | `Btn` 用 `ButtonWidget` + `Screen.buttonClicked` 分派 |
| `InputUtil` | 不存在 | 按键要另找（Legacy API 的 keybinding 模块）|
| `Screen` 钩子 | `render(int,int,float)` / `init()` / `renderBackground()` / `shouldPauseGame()` / `removed()` | `ChromeScreen` 第四套实现 |

实测编译：一个覆盖这些点的 `SpikeScreen` 类在
`fabric-loom 1.11.8 + legacy-looming 1.11.8 + Gradle 8.14.2 + --release 8` 下 **BUILD SUCCESSFUL**，
`remapJar` 也成功（产物 2.7 KB）。

## 结论四：工具链有个硬约束

- **loom 1.16 需要 Gradle 9.x**（`org.gradle.plugin.api-version 9.4.0`），本仓库是 Gradle 8.14.2 → 不能用。
- `legacy-looming 1.11.8` + `fabric-loom 1.11.8` + Gradle 8.14.2 **可以**。
- 但 **Legacy Fabric API 聚合包 `1.13.5+1.12.2` 是用 loom 1.15.3 打的**，装进 loom 1.11.8 会报
  `Mod was built with a newer version of Loom (1.15.3)`。要么升 Gradle 9 + loom 1.16，要么挑一个旧版 API。
- 纯 MC 侧（不挂 Fabric API）编译没问题 → 说明「能不能做」的答案是能。

## 代价估计

| 项 | 量级 |
|---|---|
| 新增 compat 目标（Ctx/Txt/Btn/ChromeScreen/Compat + 资源） | 和 1.16.5 那轮同量级 |
| 独立 gradle 构建（不同 loom 插件 + 不同 Gradle） | 新工程 `build-1.12.2/`，因为 plugin 版本不能共存 |
| 页面代码 | **不用改**（已经收口在 `Ctx.fill/drawText/drawItem` 三个原语上）|
| 资源（物品模型）| 1.12.2 是 `models/item/*.json` + 贴图，比 1.16.5 简单 |
| 按键 | 需要 Legacy API 的 keybinding 模块，得先确认名字 |

## 建议

1. **先只做「安装」**（已完成）：1.3～1.13.2 的 Fabric 安装/启动现在全部可用，覆盖了 PCL/HMCL 用户最常见的 1.12.2 整合包场景。
2. **mod 本体上 1.12.2 单独排期**：它是「多一个 compat 目标 + 一个独立 gradle 工程」，不是重写；
   关键前提是先决定要不要为它升级到 Gradle 9（否则只能锁在 loom 1.11 生态里挑旧 API）。
