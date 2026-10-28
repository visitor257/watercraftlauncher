# 当前进度速查（2026-10-29 01:30）

> 这份是**临时状态快照**，给人快速看「现在到哪了 / 下一步该干什么」。
> 详细流水账在 `PROGRESS.md`（§0–§29），坑位手册在 `HANDOVER.md`。

---

## 一句话

**mod 本体已经铺满 39 个 jar（Fabric 16 / Forge 18 / NeoForge 5），全部能进游戏画出界面；
刚修掉一个「右键没反应」的真 bug，三个加载器实测 PASS。**
还有两个已知缺口：Forge 1.13.2、Forge/NeoForge 26.1。

---

## 1. 本轮（今天凌晨）干了三件事

### ① 「远古」版本（b1.x / a1.x，61 个）实测 → 修掉 `Launcher.plan()` 顺序 bug

- b1.7.3 / a1.2.6 用生产代码 `InstallJob` 装：**都 DONE**，749 个 virtual 资源进
  `assets/virtual/pre-1.6`，`missingAssets=0 / missing files=0 / requiredJava=8`。
- 第一次启动 b1.7.3 直接挂 `NoClassDefFoundError: org/lwjgl/LWJGLException`，classpath 只有 6 项。
- **根因**：`plan()` 原来「先按版本去重、再按 rules 过滤」。b1.7.3 里
  `lwjgl:2.9.1-nightly-20130708-debug3` 带一条 `allow + os=osx + os.version=^10\.5\.\d$`
  的 rules（只在老 OSX 上允许）→ 去重选中它 → rules 又把它刷掉 → lwjgl / lwjgl_util /
  lwjgl-platform **三个一起消失**。而且被 rules 刷掉的条目**不计入 missing 统计**，
  所以探针报 `missing files=0` 还是绿的。
- **修法**：rules 过滤提到去重之前（官方启动器也是这顺序）。修完 classpath 6 → 9 项。
- b1.7.3 / a1.2.6 现在都能起（a1.2.6 实测窗口标题 `Minecraft`）。
- 两个残留报错是上游的，可忽略：`IIOException` 找 `icons/*`（pre-1.6 资源没这个）；
  `s3.amazonaws.com/MinecraftResources` 音效（Mojang 2020 年关的桶）。
- 提交 `d6dc12a`，PROGRESS §28。

### ② 用户报「1.21.4 拿着物品右键没反应」→ 真 bug，10 个文件全中

- **根因**：`McVerClient` 每 tick 用 `options.useKey.wasPressed()`（Fabric）/
  `options.keyUse.consumeClick()`（Forge/NeoForge）判断右键。但原版
  `Minecraft.handleInputEvents()` 每 tick 都跑
  `while (keyUse.wasPressed()) doItemUse();`，`wasPressed()` 内部 `timesPressed--`
  把按下计数**全部消费干净**，而我们的 tick 回调在它**之后** → 那行永远 false。
- **为什么 39 个 jar 的像素验收没抓到**：验收走的是 `MCVER_OPEN_GUI=1` 自检钩子
  （进标题界面自动开），**根本没经过右键**。测试后门不能当功能证据。
- **修法**：对「按住」做边沿检测。`isPressed()`（Yarn）/ `isDown()`（mojmap）读
  `pressed` 字段，原版只 set 从不 clear，tick 末尾照样可见。旧的 `wasPressed()`
  留着当保险（不同加载器 tick 事件相对 `handleInputEvents` 的先后不一致，两种都不漏）。
- **命中 10 个文件**：`mod/src/main/.../McVerClient.java` +
  `modx/forge{14,15,16,17,18,19,21}` + `modx/neoforge{20,21}`。
- **没受影响的**：Fabric ≤1.20 用 `UseItemCallback`；Forge 1.7.10~1.12.2 用物品自己的
  `onItemRightClick`。
- **新验收工具**：`tools/probe/.../ProbeRightClick.java` + `scripts/verify-rightclick.mjs`
  —— 真起游戏 → `--quickPlaySingleplayer` 进存档 → 聊天 `/give` → `PostMessage`
  真右键 → 只认日志 `mcver: 右键打开启动器`（这条只有右键分支会打）。
- 提交 `61712e6`，PROGRESS §29。

### ③ 全量重建

`node scripts/rebuild-all.mjs` → **31/31 成功**，publish 到 `dist/`。之后又补编了
forge（23 步）和 fabric-1.x（7 步），因为 `Launcher.launch` 新增了 `extraGameArgs`
重载，要让所有 jar 与源码一致。现在 `dist/` 里 39 个 jar **全部**含这个重载
（`javap` 抽查 5 个，都是 2 个 `launch`）。

---

## 2. 验证现状（诚实版）

| 目标 | 界面（像素） | 真右键 | 备注 |
| --- | --- | --- | --- |
| Forge 1.21.4 | `19481 / 17` ✔ | **PASS** ✔ | 抓图中心 `C6C6C6`，面板色命中 75218 |
| Fabric 1.21.4 | `15092 / 17` ✔ | **PASS** ✔ | 抓图中心 `717171`（落在列表按钮上），命中 27204 |
| NeoForge 20.6 | `19478 / 17` ✔ | **PASS** ✔ | 抓图 OK |
| 其余 36 个 | 像素验收全绿 | **未逐一手测右键** | 代码同源，按加载器分两类实现，风险低 |

**⚠️ 一个待补的小缺口**：上面三次右键 PASS 是在 01:18–01:20 的 jar 上跑的；
之后为了 `extraGameArgs` 重载重编了一版，`1.21.4-forge` 实例里的 jar 已换成新版
（01:27），**新版还没重跑右键验收**（那次跑到一半被我打断）。mod 行为没变，
但严格说这个组合需要重跑一次确认。

**远古版（b1.x / a1.x）**：能装能起，但 **mod 装不上**（没有 Fabric / Forge），
所以没有界面验收这回事。

---

## 3. `dist/` 里有什么（39 个 jar）

```
D:\Development\watercraftlauncher\dist\
  watercraftlauncher-forge-1.0.0-mc{1.7.10,1.8.9,1.9.4,1.10.2,1.11.2,1.12.2,
                                    1.14.4,1.15.2,1.16.5,1.17.1,1.18.2,1.19.4,
                                    1.20.1,1.20.2,1.20.4,1.20.6,1.21.1,1.21.4}.jar   18 个
  watercraftlauncher-fabric-1.0.0-mc{1.7.10,1.8.9,1.9.4,1.10.2,1.11.2,1.12.2,1.13.2,
                                     1.14,1.15,1.16,1.17,1.18,1.19,1.19.3,1.20,1.21}.jar  16 个
  watercraftlauncher-neoforge-1.0.0-mc{1.20.2,1.20.4,1.20.6,1.21.1,1.21.4}.jar           5 个
```

- NeoForge **20.1** 没有独立 jar，直接复用 `forge-...-mc1.20.1.jar`（实测 PASS）。
- 装进实例：`node scripts/put-mod.mjs <实例名> <jar名>`（只认 `dist/` 里的文件）。

---

## 4. 常用命令

```powershell
# 全量重建（31 步 + 自动 publish 到 dist）
node scripts/rebuild-all.mjs            # 全部
node scripts/rebuild-all.mjs forge      # 只跑名字含 "forge" 的步
node scripts/rebuild-all.mjs publish    # 只 publish
node scripts/rebuild-all.mjs --list     # 列步骤

# 装 jar
node scripts/put-mod.mjs 1.21.4-forge-54.1.22 watercraftlauncher-forge-1.0.0-mc1.21.4.jar

# 界面像素验收（走自检钩子，只证明渲染）
node scripts/verify-gui-one.mjs 1.21.4-forge-54.1.22 --timeout 240

# 真右键验收（走用户真实路径）
node scripts/verify-rightclick.mjs 1.21.4-forge-54.1.22 [--world 新的世界] [--timeout 190]

# 下载页四分类验收（判据是 children=23 + 日志分类计数）
node scripts/verify-download-page.mjs 1.21.4-forge-54.1.22 [--cat 愚人节]

# 编译探针（改过 core/ 或 tools/probe/ 之后）
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build-probe.ps1
```

---

## 5. 下一步该干什么（按优先级）

1. **重跑一次 `1.21.4-forge` 的真右键验收**（当前 jar 是重编后的新版，见 §2 的缺口）。约 2 分钟。
2. **抽验两个旧世代的右键**：Forge 1.7.10（走 `McVerItem#onItemRightClick`）和
   Fabric 1.20（走 `UseItemCallback`）—— 这两条是不同的实现路径，代码上应该没事，但没实测过。
3. **等用户验剩下 36 个组合**，或按需批量跑。
4. 两个已知缺口（工作量大，非阻塞）：
   - **Forge 1.13.2**：没有 Mojang 映射（`client_mappings` 从 1.14.4 才开始发），
     MCP 1.13 是自有快照名，要走 `mk-mcp-srg` 的 tsrg 路径 + 全新语义层。
   - **Forge/NeoForge 26.1**：class 69 / Java 25、`ResourceLocation` → `Identifier`、loader 拆模块。
5. 小清理：`workspace/` 下的 `nbt.mjs`（读 `level.dat` 的小工具，可留）。
   `modx/` 里只剩 `jp.mjs`（javap 过滤，建议长期留）。

---

## 6. 这轮学到的规矩（已写进 PROGRESS / MEMORY）

- **验收判据必须覆盖用户真正走的那条路。** 自检钩子（`MCVER_OPEN_GUI`）只能证明
  「渲染没坏」，不能当功能证据。输入类功能一律用 `ProbeRightClick` 那类探针。
- **「按版本去重」不能早于「按平台过滤」**（rules 里的 `os.version` 区间规则会让
  被过滤的条目顶掉合法老版本，而且缺失统计看不见）。
- **日志 grep 要按 GBK 回退解码**：游戏用平台默认编码写日志，`readAllLines(UTF_8)`
  不抛异常、只把中文换成 `U+FFFD`，于是按中文标记永远匹配不上（踩过：界面明明开了，探针报 FAIL）。
- **截图成功要断言文件存在且 >1KB**，不能只信打印出来的路径（路径转义写错时会被 catch 吃掉，骗人）。
- **`Add-Type -MemberDefinition` 带 `struct` 定义编译不过**（`找不到类型或命名空间 user32`），
  要用 `Add-Type -TypeDefinition` 整类定义（`scripts/verify-gui.ps1` 就是这么写的）。
- **命名对照**：Yarn `options.useKey` / `isPressed()` / `currentScreen` / `getWindow().getHandle()`
  vs mojmap `options.keyUse` / **`isDown()`** / `screen` / `getWindow().getWindow()`。
  写成 `isPressed()` 在 Forge/NeoForge 上会「找不到符号」。
