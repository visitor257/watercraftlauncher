# 进展报告：截至 2026-10-26

> 计划书：`PLAN.md` · 坑位手册：`HANDOVER.md` · 开发流水账：`REPORT.md`
> 验证矩阵（原始记录）：`VERIFY.md` + `verification/results.csv`
> 本报告 = 提炼层：**能宣称什么、不能宣称什么、下一步干什么**。

---

## 0. 一句话

**6 个加载器（Fabric / Quilt / Forge / NeoForge / OptiFine / Iris）在 48 个「MC 版本 × 加载器」
组合上实测到 L2（装完 + 启动到 `Setting user`）**，覆盖 1.7.10 → 1.26 的每个版本纪元断层。
这轮挖出并修掉 **10 个真 bug**（3 个是崩溃/阻塞级：缺 Java 运行时自动下载、老安装器无 `--installClient`、
OptiFine 用错 Java），另外修掉 **3 个会造出假验证结果的工具链坑**。

**仍然不能对外的说法**：「所有版本全支持」。现在能说的是：
**「按版本断层实测 48 个组合，全部通过；其余版本按规则表判定，未逐版本实测」**。

---

## 1. 真实支持表（有日志证据）

判定标准：装完 + 启动日志出现 `Setting user:`（离线账号 401 属正常）。
测试目录 `<项目根>/testgame`，下载源 BMCLAPI，探针走的是**生产代码** `InstallJob` / `Launcher`。

| MC 区间 | 加载器 | 证据 |
|---|---|---|
| 1.7.10 / 1.10.2 / 1.12.2 | Forge | 3 个 L2（Java 8 + legacy install_profile）|
| 1.12.2 | OptiFine | L2 |
| 1.14 / 1.14.4 | Fabric | L2（Fabric 下限）|
| 1.14.4 | Quilt / Forge | L2（Quilt 下限）|
| 1.15.2 | Fabric | L2 |
| 1.16.5 | Fabric / Forge | L2 |
| 1.17 / 1.17.1 | Fabric / Forge | L2 |
| 1.18.2 | Fabric / Quilt / Forge | L2 |
| 1.19.4 | Fabric / Forge | L2 + OptiFine L2 |
| 1.20.1 | Fabric / Quilt / Forge / NeoForge | 4 个 L2 + OptiFine + Iris |
| 1.20.2 / 1.20.4 | Forge / NeoForge | L2 |
| 1.20.5 | Fabric | L2（组件系统断层）|
| 1.20.6 | Fabric / Forge / NeoForge | L2 |
| 1.21.1 / 1.21.4 | Fabric / Forge / NeoForge | L2（+ Quilt 1.21.4、OptiFine、Iris）|
| 1.21.5 / 1.21.8 | Fabric | L2 |
| **26.1** | Fabric / **Forge / NeoForge** | L2（需要 Java 25，启动器会自动下）|

按加载器：Fabric 16 · Forge 15 · NeoForge 7 · Quilt 4 · OptiFine 4 · Iris 2 = **48 个 L2**。

### 1.1 已知例外 / 未验证（别当成支持）

| 项 | 状态 |
|---|---|
| 1.14.1/1.14.2/1.14.3、1.15/1.15.1、1.16.1~1.16.4、1.17 其余、1.20 其余… | 规则表判定可装，**未逐版本实测** |
| 1.14.1/1.14.2/1.14.3、1.15/1.15.1、1.16.1~1.16.4、1.17 其余、1.20 其余… | 规则表判定可装，**未逐版本实测** |
| 快照 / 愚人节版 | 未跑（Fabric/Quilt 规则表标 MAYBE）|
| mod 本体 | **Fabric 9 个包，覆盖 1.14 ~ 1.21.11 全部版本**（mc1.14/mc1.15/mc1.16/mc1.17/mc1.18/mc1.19/mc1.19.3/mc1.20/mc1.21），16 个实例像素级验收全绿（见 §15）。Forge/NeoForge 目前只有 1.20.6/1.21.1 |

> 若要把「全版本」这三个字用起来，得按加载器把 192 个组合各装一遍（估 10+ 小时机器时间）。
> 现在的口径是**断层覆盖**，不是全量验证。

---

## 2. 这轮挖出并修掉的 10 个真 bug

| # | 现象 | 根因 | 修复 |
|---|---|---|---|
| 1 | 26.1 Fabric「找不到 Java 25」 | 只会扫本机 Java，没有下载官方运行时的能力 | 新增 `core/JavaRuntime`（Mojang `all.json` → manifest → 411 文件并行，落到 `<gameDir>/runtime/`），安装与启动两条链路都接 |
| 2 | 1.20.6 Forge 装到一半失败 | 安装器内部下载器直连 forge maven，无镜像/重试/黑名单 | 预取 `version.json` + `install_profile.json` 的库，再 `--offline` 跑，失败退 `--mirror` |
| 3 | NeoForge 1.20.4/1.20.6/1.21.4 装不上 | BMCLAPI → `ipv6.mirrors.ustc.edu.cn`（**只有 AAAA**）→ 本机 IPv6 不通；`maven.neoforged.net` 还是 DNS 污染（解析到 Facebook IP）| maven 候选链加 IPv4 镜像 `mirrors.ustc.edu.cn/bmclapi/`、`cernet...` |
| 4 | 1.14.4 Forge 两个库永远下不到 | `name` 把 version+classifier 混写（`1.0.5-shadowed`），清单 `path` 字段也是错的；且半截下载被当成功 | 用 `url` 的路径生成候选、按 `path` 落盘；加 sha1/size 校验 |
| 5 | 1.7.10 / 1.10.2 Forge `UnrecognizedOptionException` | 那代安装器没有 `--installClient`（客户端安装当年是 GUI）| `installViaLegacyProfile()`：读 `install.filePath` + `versionInfo` 直接铺 |
| 6 | 老版本拿 Java 17 跑 2014 年的安装器 | `Math.max(17, requiredJava)` 写死 | 先按版本要求（1.7.10→8），失败再退 17+ |
| 7 | OptiFine 全挂 NPE | 矩阵没指定版本时 `ofId` 为 null | null → 取推荐版 |
| 8 | OptiFine 1.12.2/1.19.4 `UnsupportedClassVersionError` | 安装器只能在对应 Java 上跑，mod 本体是 Java 21 字节码 | 驱动单独编 **release 8** 成 `optifine-runner.jar` 资源，运行时解出来用目标 Java 起子进程 |
| 9 | **启动验证假通过**：装了加载器却跑的是原版 | 重写「按版本要求选 Java」循环（bug 6）时把 `result = id;` 删了 → 下一步改名把 **原版目录**改成了目标名，日志照样有 `Setting user` | 补回 `result = id;`；`verify-matrix` 加断言「非 NONE 组合的主类不能是 `net.minecraft.client.main.Main`」；重装了 7 个中招组合 |
| 10 | 26.1 Forge 偶发 `installer 退出码 1` | 镜像链某一跳回 403，把错误页（14 KB）当成 `mcp_config` 的 zip 写进了 `libraries/`，同一次安装里被安装器读到 | 安装器失败后先按清单 `purgeInvalidLibraries()` 清坏文件 + 重新预取，再重试一次；26.1 Forge/NeoForge 现已 L2 |

## 3. 验证工具链的 3 个坑（会造假结果）

1. PowerShell 5.1 把原生命令 stderr 当异常 → 一行警告跑挂整批 → 改 .NET `Process` 收流。
2. 矩阵运行期间重编探针 → `.class` 被删 → 后续全 `NoClassDefFoundError`（**造出 11 个假失败**）→ `.run.lock` 互斥。
3. `javapath\java.exe` 是壳，kill 父留僵尸 java 写同一目录 → 用 `JAVA_HOME` 真实路径 + 锁。
4. **只看到 `Setting user` 不算启动成功**：装了加载器却没接上，原版也会打出这句。
   必须同时核主类（Fabric/Quilt→`KnotClient`、Forge/NeoForge→`Bootstrap*`/`modlauncher`、
   OptiFine 1.12.2→`launchwrapper.Launch`），否则会把「装了个原版」当成通过。
5. **不要拿 MC 版本号当版本目录 hint**：`versions/26.1` 这种原版目录也包含 `26.1`，
   按 mtime 取最新就会拿原版去启动。以 `install_profile.json` 的 `version` 字段为准。

---

## 4. 关键结论（工程纪律）

1. **「支持」必须分级**：L0 判定 / L1 安装 / L2 启动 / L3 可复跑。没到 L2 不许说支持。
2. **验证必须可复跑**：探针进仓库（`tools/probe/`），一条命令跑全矩阵，结果落 CSV 可断点续跑。
3. **镜像链要按「实际可达」排**：国内优先 USTC/CERNET 的 IPv4 `/bmclapi/` 路径，官方垫底。
4. **清单里的 `path` / `name` 都不可信**，`downloads.artifact.url` 才权威；下载必须校验 sha1/size。
5. **安装器的 CLI 能力要探测**：`--installClient` 在 1.7.10/1.10.2 不存在、NeoForge 20.2+ 也没有。
6. **Java 版本是独立维度**：安装用一个 Java、启动用另一个、OptiFine 驱动还要另一个。
7. **改完代码必须重建部署 jar**：build → dist → 项目根 → 游戏 mods（本次已做，旧 `mcver-1.0.0.jar` 已 retired）。

---

## 5. 产物与部署

| 东西 | 路径 |
|---|---|
| 主源码 | `mod/src/main/java/dev/mcver/`（31 个 java，约 8300 行）|
| Java 8 驱动 | `mod/runner/dev/mcver/runner/OptifineRunner.java`（单独 release 8 编译）|
| 兼容层 | `mod/src/compat/1.21.1/java/dev/mcver/`（2 个文件）|
| 构建 | `scripts/build-all.ps1`（自动探测 Gradle 8.x，产物进 `dist/`）|
| 验证 | `scripts/build-probe.ps1` + `scripts/verify-matrix.ps1` |
| 产物 | `dist/watercraftlauncher-fabric-1.0.0-mc{1.21.4,1.21.1,1.20.1}.jar`（约 245 KB）|
| 游戏内实际加载 | `<.minecraft>/versions/1.21.4-Fabric 0.19.5/mods/` |
| 版本控制 | **已是 git 仓库**（本地，未配 remote；4 个提交：baseline / 46-L2 修复 / 1.20.1 target / 假通过与 26.x） |

部署三处（少一处就有人测到旧版）：`mod/build/libs` → `dist/` 与项目根 → 游戏 `mods/`。

---

## 6. 还没做到的

### 6.1 验证缺口
- ~~26.1 的 Forge / NeoForge（要 Java 25，用户暂缓；启动器能力其实已具备）~~ —— **2026-10-26 已通**（启动器自动下载 Java 25 运行时）。
- 103 个正式版里只实测了 22 个 MC 版本 × 部分加载器。
- 快照 / 愚人节版一个没跑。

### 6.2 已知不阻塞问题
- `config/mcver.json` 明文存 refresh token（与 PCL/HMCL 一致，开源需在 README 提醒）。
- mod 本体有 1.21.4 / 1.21.1 / 1.20.1 / 1.19.4 四个 target（1.19.4 起走 `ui/Ctx` 适配层，同一套源码可继续往 1.18.2 / 1.17.1 铺）。
- 还没配 git remote（按用户要求：现在不上 GitHub）。

---

## 7. 接下来

| 阶段 | 内容 | 状态 |
|---|---|---|
| P0 验证矩阵 | **48/48 L2** | ✅ 完成（含 26.1 Forge/NeoForge；已修掉一次假通过）|
| P1 现代版本全覆盖 | 1.20.x / 1.21.x 逐版本扫安装 | 未做（断层已覆盖）|
| P2 老版本 target | 1.20.1 ✅（2026-10-26，compat 3 文件 + 1 资源）→ 1.19.4 → 1.18.2 → 1.16.5 / 1.12.2 | **进行中** |
| P4 开源准备 | LICENSE / NOTICE / 文档脱敏 / Actions | 未做（用户：暂不上 GitHub）|

---

## 8. 等用户拍板

1. 26.x 什么时候做（需要装 Java 25，启动器已能自动下）？
2. P1 全量扫（10+ 小时机器时间）要不要做，还是维持「断层覆盖 + 规则外推」？
3. 1.19.4 及更老的 mod target 是否接着推（1.20.1 已通，按预估每个 3~5 个文件）？

## 9. 2026-10-26：mod 本体 1.20.1 / 1.19.4 target 已通

- 做法：单源码 + compat（`src/compat/1.20.1/`），**没有复制工程**。
- 编译器判出来的真实差异只有 4 处 API + 1 处包名 + 1 处资源目录 + Java 版本：
  `Identifier.of` / `Item.Settings.registryKey` / `UseItemCallback` 返回类型 / `Screen.renderBackground` 与
  `mouseScrolled` 的签名 / `Session` 的包名 / `OperatingSystem.open(Path)` / `recipe` vs `recipes` / release 21 vs 17。
- 结构性改动只有一个：Screen 里那两个跨版本改过签名的钩子抽到 `ui/ChromeScreen`，主源码与 compat 各 20 行，
  300 行的 `MainScreen` 不复制。
- 顺带修：`fabric.mod.json` 的依赖改成按 target 自动算并**卡上界**（`>=1.20.1 <1.20.2` / `>=1.21.4 <1.21.5`），
  相邻补丁版本改 API 时不会再“装得上但一进游戏就崩”。
- 验证：`testgame/versions/V1.20.1-FABRIC` + Fabric API 0.92.2+1.20.1，生产代码 `Launcher` 起真进程 →
  `requiredJava=17`（自动选了 JDK 17）、`mcver: 物品已注册` / `客户端初始化完成`、无 Mixin 报错、到达 `Setting user`。
  **GUI 截图没拿到**（这台机器无头起游戏时卡在 atlas 创建后，与 mod 无关）→ 界面级验证留给实机。
- 产物：`dist/watercraftlauncher-fabric-1.0.0-mc1.20.1.jar`（class 版本 61），三个 target 全绿。

## 10. 2026-10-26：1.19.4 target —— 一条 `DrawContext` 引起的重构

原来的预估（「compat ≤ 3 个文件」）**是错的**。硬编译一跑就 37 个错误，全是同一个原因：
**1.19.4 根本没有 `net.minecraft.client.gui.DrawContext`**（1.20 才引入），渲染靠 `MatrixStack`。
也就是说 1.17~1.19 整条线都要改渲染层，不是 1.20 那种「改两个签名」的量级。

做了两件事：

1. **新增 `ui/Ctx` 渲染适配层**。UI 代码其实只碰三个原语：`fill`（25 处）、`drawText`（6 处）、`drawItem`（3 处）。
   把这三个收口到一个类里，页面代码就跟 MC 版本无关了：
   - 主源码（1.20+）：薄包 `DrawContext`；
   - `src/compat/1.19.4`：包 `MatrixStack`（`DrawableHelper.fill` / `TextRenderer.draw` 要 `Matrix4f` /
     `ItemRenderer.renderInGui`），一并把 `ChromeScreen` 里三个钩子改成 `renderBackground(MatrixStack)`（**只有 1 个参数**，
     跟 1.20 的 DrawContext 版、1.21.2 的 5 参数版都不一样）。
2. **把 `render` / `renderBackground` 两个 override 从 MainScreen 上提到 ChromeScreen**，MainScreen 只实现
   `drawChrome(Ctx)` 和 `renderContent(Ctx,…)` —— 否则 `MatrixStack` 这块会让 300 行的 MainScreen 每个旧 target 都得复制一份。

顺带把探针升级成能验界面：`ProbeLaunch` 加了可选第 4 个参数 `waitFor`，到 `Setting user` 后继续等日志里出现指定标记；
`MainScreen` 在 `drawChrome` / 首帧 `renderContent` 各记一条一次性日志（`screen=MAIN chrome 已绘制` / `首帧渲染完成`）。

验证（`testgame/versions/V1.19.4-FABRIC` + Fabric API 0.87.2）：
- `Loading Minecraft 1.19.4 with Fabric Loader 0.20.1`、`- mcver 1.0.0`、`物品已注册`、`客户端初始化完成`；
- `screen=MAIN` → `chrome 已绘制` → `首帧渲染完成`，切 6 个页都正常，**无异常堆栈**；
- **像素级确认（已补）**：当时先误判成「环境限制」，但用户实机一看就发现**面板是透明的**。
  真因：`Screen.render` **只在 1.21.1+ 才会自己调 `renderBackground`**，1.20.1 / 1.19.4 只遍历 drawables，
  而 `drawChrome` 当时写在 `renderBackground` 里 → 面板一次都没画。已在两个 compat 的 `render` 里手动调，
  并用真实窗口 PrintWindow 抓图确认：1.19.4 / 1.20.1 / 1.21.4 中心点都是 `C6C6C6`，命中≈15000 采样（一致）。
- 产物：`dist/watercraftlauncher-fabric-1.0.0-mc1.19.4.jar`（class 版本 61），**四个 target 全绿**（1.19.4 / 1.20.1 / 1.21.1 / 1.21.4）。

## 11. 2026-10-26：1.18.2 target（顺手把 UI 里 164 处版本强绑定 API 收了）

1.18.2 比 1.19.4 又早一步，DrawContext 当然没有，但**真正贵的不是渲染**，是下面这三样：

| 差异 | 1.19.4 | 1.18.2 | 处理 |
|---|---|---|---|
| Text.literal(String) | 有 | **没有**（只有 Text.of / 
ew LiteralText）| 新增 ui/Txt.of 适配层，main 里 **92 处**改调它 |
| ButtonWidget.builder(...) | 有 | **没有**（只能 
ew ButtonWidget(x,y,w,h,text,onPress)）| 新增 ui/Btn.builder 适配层，**72 处**改调它 |
| 注册表入口 | 
et.minecraft.registry.Registries | 
et.minecraft.util.registry.Registry | core/Compat.itemId() |
| Session#getUuidOrNull() | 有 | 没有（getUuid() 直接返回 String）| core/Compat.sessionUuid() |
| TextRenderer#draw 第 8 参 | TextLayerType 枚举 | oolean seeThrough | 写进 compat/1.18.2/ui/Ctx.java |
| ItemRenderer#renderInGui | 收 MatrixStack | **不收**（走 RenderSystem 模型矩阵）| 同上，屏幕渲染时矩阵是单位矩阵，坐标不用换算 |
| ItemGroupEvents（塞创造栏）| 有 | 没有（Fabric API 0.77 只有 FabricItemGroupBuilder）| 1.18.2 自建一个「Water Craft Launcher」创造栏 |
| 配方 json | 有 "category" | **没有** "category" 字段（会被当解析错误）| compat 资源层单独一份 |

> 教训：**版本越老，贵的越不是渲染，而是「到处都用到的小 API」**。所以这轮先把 164 处调用点
> 用脚本（\scripts/rename-shims.mjs\）机械替换成 Txt.of / Btn.builder，再让编译器指路。
> 这两个适配层对 1.17.1 / 1.16.5 同样是必需的，属于一次性投资。

### 验证（\scripts/verify-gui.ps1\，真实窗口 PrintWindow）

| 版本 | 窗口中心点 | #C6C6C6 命中（4px 采样）| children | 结论 |
|---|---|---|---|---|
| 1.21.4 | C6C6C6 | 15092 | 17 | OK |
| 1.21.1 | C6C6C6 | 15092 | 17 | OK |
| 1.20.1 | C6C6C6 | 15061 | 17 | OK |
| 1.19.4 | C6C6C6 | 15349 | 17 | OK |
| **1.18.2** | **C6C6C6** | **15357** | **17** | **OK** |

日志另外确认 1.18.2：\Loading Minecraft 1.18.2 with Fabric Loader 0.20.1\、\- mcver 1.0.0\、
\物品已注册\、\客户端初始化完成\、\screen=MAIN\ → \chrome 已绘制\ → \首帧渲染完成\，无异常栈。

产物：\dist/watercraftlauncher-fabric-1.0.0-mc1.18.2.jar\（class 61），**五个 target 全绿**。

## 12. 2026-10-26：1.17.1 target（顺手补上 ChromeScreen 的「关界面/是否暂停」）

1.17.1 跟 1.18.2 的渲染/文本/按钮层完全一致（shim 直接复用），多出来的只有**钩子名**差异：

| 钩子 | 1.17.1 | 1.18.2 / 1.19.4 / 1.20.1 / 1.21.x |
|---|---|---|
| 关界面 | onClose() | close() |
| 是否暂停 | isPauseScreen() | shouldPause() |
| 取矩阵 | MatrixStack.Entry#getModel() | getPositionMatrix()（1.18 改名）|

处理方式：ChromeScreen 里加一个跟版本无关的 closeSelf()，UI 代码只调它；
两个版本钩子各自 override 转给它。MainScreen 里原本自己写的 close() / shouldPause()
上提到基类 —— 否则每个 target 都得复制一遍这段。

### 还修了一个真 bug（1.17.1 才暴露）
abric.mod.json 里写死 "fabric-api": "*"，但 **Fabric API 的 mod id 在 1.19 之前叫 abric**
（1.17 的 0.46.1 bundle 里 "id": "fabric"）。结果 1.17.1 起游戏直接
HARD_DEP_NO_CANDIDATE mcver ... {depends fabric-api}。
现在 uild.gradle 按 minecraft_version 推：< 1.19 用 abric，否则 abric-api
（1.18.2 的 0.77 同时 provides: ["fabric"]，所以两边都满足）。

### 验证（scripts/verify-gui.ps1，六个目标一次跑完）

| 1.21.4 | 1.21.1 | 1.20.1 | 1.19.4 | 1.18.2 | 1.17.1 |
|---|---|---|---|---|---|
| 15092 | 15092 | 15061 | 15349 | 15357 | 15357 |

中心点全是 C6C6C6，children 全是 17。1.17.1 的实例是用**我们自己的安装器**装的
（ProbeInstall testgame 1.17.1 FABRIC，跑生产代码 InstallJob），顺带又验证了一遍安装链路：
ersions/fabric-loader-0.20.1-1.17.1，Java 16（G:\Program Files\Java\jre-16.0.1-full）。

产物：dist/watercraftlauncher-fabric-1.0.0-mc1.17.1.jar（class 60），**六个 target 全绿**。

## 13. 2026-10-26：1.16.5 target —— 差一步「Java 8 源码兼容」

1.16.5 的渲染 / 文本 / 按钮层跟 1.17.1 完全一致（compat 直接复用），真正的差异是**周边 API**：

| 差异 | 1.17.1 | 1.16.5 |
|---|---|---|
| 日志 | slf4j（Fabric 带进来）| **log4j2，类路径上没有 slf4j** |
| 加控件 | ddDrawableChild(T) | ddChild(T) |
| 清空控件 | clearChildren() | 没有 → 直接 uttons.clear() |
| 切界面 | setScreen(Screen) | openScreen(Screen) |
| 物品比对 | stack.isOf(Item) | stack.getItem() == item |
| 截图 | ScreenshotRecorder | ScreenshotUtils（要自己传宽高）|
| 取矩阵 | Entry#getModel() | 同（1.18 才改名）|

做法：新增 core/Log（slf4j / log4j2 两个实现）、ChromeScreen.addWidget/clearWidgets、
Compat.saveScreenshot，把 9 个调用点收口。**编译已通过，GUI 也验过了（19754 命中，中心 C6C6C6）**。

### 但有个真问题：Java 版本门槛
原版 1.16.5 声明 javaVersion = 8，玩家默认用 Java 8 起。而我们这个 jar 是 **Java 17 字节码**
（Files.writeString / String.isBlank / ar / List.of 这些 Java 9~11 的 API 用了一百多处）。
实测：Java 8 上 loader 0.20.1 能起来，但我们的 mod 直接被
HARD_DEP_INCOMPATIBLE_PRESELECTED ... {depends java @ [>=17]} 挡掉；钉到 Java 17 就一切正常。

两条路（**待拍板**）：
1. **做 Java 8 源码兼容**：ar(31) / Files.writeString(14) / isBlank()(56) / List.of(15) / Map.of(1)
   共 ~117 处，机械但容易出错；好处是 1.12.2 也能用同一套。
2. **维持 Java 17**：发布时写明「1.16.5 需要 Java 17」，并在启动器里对装了本 mod 的 1.16.5 实例把 javaVersion 抬到 17。

产物：dist/watercraftlauncher-fabric-1.0.0-mc1.16.5.jar（class 61）。

### 七目标像素验证（scripts/verify-gui.ps1）

| 1.21.4 | 1.21.1 | 1.20.1 | 1.19.4 | 1.18.2 | 1.17.1 | 1.16.5 |
|---|---|---|---|---|---|---|
| 15092 | 15092 | 15061 | 15349 | 15357 | 15357 | 19754 |

中心点全是 C6C6C6，children 全是 17。（1.16.5 命中更多是因为它的原版背景更亮，面板像素占比更高。）

## 14. 2026-10-26：Java 8 源码兼容做完了 —— 1.16.5 真的能在 Java 8 上跑

上一节留的选择题（做 Java 8 兼容 / 维持 Java 17）选了**做**。理由：1.12.2 反正躲不过 Java 8，
这笔账迟早要付。做完之后主源码整体降到 Java 8 语言级，所有 target 共用同一份，没有任何分叉。

### 实际工作量（比预估的 117 处多得多）

| 类别 | 处数 | 怎么处理 |
|---|---|---|
| `.isBlank()`（Java 11）| 62 | → `.trim().isEmpty()`，脚本替换 |
| 箭头 switch（Java 14）| 69 行 / 14 个 switch | 手工 + 脚本改写；其中 8 个是 **switch 表达式**，要改成 if 链或经典 switch + return |
| `var`（Java 10）| 37 | 逐个写具体类型（JDK 类型写全限定名，省得加 import）|
| `Files.writeString` / `readString`（11）| 23 | → `core/Jdk` 垫片 |
| `record`（Java 16）| 9 | → final class，**保留同名无参方法**，调用点零改动 |
| `List.of()` / `Map.of()`（9）| 16 | → `Jdk.emptyList()/emptyMap()` |
| `Stream#toList()`（16）| 6 | → `.collect(Collectors.toList())` |
| 模式匹配 `instanceof`（16）| 6 | → 显式转型 |
| 文本块（15）| 2 | → 字符串拼接 |
| **`java.net.http.HttpClient`（11）** | 6 文件 | **全部换成已有的 `Net`（HttpURLConnection）** |
| `Process#pid()` / `ProcessHandle`（9）| 5 | `Jdk.pid()`（反射读字段）/ `Jdk.currentExe()`（`java.home` 拼）|
| `InputStream#readAllBytes`（9）| 4 | `Jdk.readAll` / `Jdk.readAllText` |
| `URLEncoder.encode(s, Charset)`（10）| 2 | `Jdk.urlEncode`（Java 8 那个重载会抛受检异常）|
| `Path.of`（11）| 2 | `Paths::get` |
| `Matcher.appendReplacement(StringBuilder,..)`（9）| 1 | 换 `StringBuffer` |
| `Optional.isEmpty`（11）| 1 | `!…isPresent()` |

### 三个真正花了时间的地方（不是机械替换能解决的）
1. **switch 表达式**：`return switch (x) { case A -> … }` 在 Java 8 里没有对应语法，
   必须改成 if 链或「经典 switch + 给局部变量赋值 + return」。共 8 处。
2. **`java.net.http`**：整个包是 Java 11 才有的，而 HTTP 散在 6 个文件里。
   干脆全收口到 `Net`（本来就是 HttpURLConnection，注释里还写着「HttpClient 在游戏 JVM 会卡 7 分钟」）。
   Auth 的 POST 也一起搬过去了：`Net.postForm / postJson / getJson`。
3. **record → class 时不能只换字段**：`LoaderVersions.Lv`、`Sources.Src` 这些都有自定义方法，
   而且原代码里 `found.exe` 和 `found.exe()` 两种写法都有 → 生成的类**同时**给 public final 字段
   和同名无参方法，等于零调用点改动。equals/hashCode/toString 也一并补上，语义跟 record 一致。

### 踩到的坑
- **局部 record 不能改成 `static` class**：`JavaRuntime.Job` 原本声明在方法体里，改写时加了 static 直接编不过。
- **脚本替换箭头 switch 时把行尾 `\n` 一起吃掉**，紧挨着的下一行就匹配不到 —— 要用 lookahead `(?=\n)` 收尾。
- **有的文件是 CRLF 有的是 LF**：不先统一，`\{\n` 一个都匹配不上（VersionsPage 就是这么漏的）。
- **`--release 8` 的 `warning: target value 8 is obsolete`** 是正常的，不用管。

### 验证（三层，缺一层都不算过）
1. **字节码版本**（直接读 class 头）：mc1.16.5 = 52（Java 8）、1.17.1 = 60、1.18.2/1.19.4/1.20.1 = 61、1.21.1/1.21.4 = 65。
2. **真机运行**：1.16.5 实例的 `javaVersion` 改回原版的 8，探针选到 `jre-1.8` →
   日志里没有再出现 `HARD_DEP_INCOMPATIBLE_PRESELECTED`，一路到 `Setting user`，物品注册、`screen=MAIN` 都有。
3. **界面像素**：真实窗口 `PrintWindow` 抓图，中心 `C6C6C6`、命中 19754、children 17 —— **和上一轮 Java 17 跑出来的数字一模一样**。
4. 七个 target 的 `verify-gui.ps1` 全绿：15092 / 15092 / 15061 / 15349 / 15357 / 15357 / 19754。

### HTTP 层回归（重构了就得验）
- 装了一次 1.20.1 + Fabric（`smoke-j8`）：manifest → 版本 json → 66 个库 → 3598 个资源 → Fabric loader → 重命名 → 隔离，
  全程走新的 `Net`，一遍过。
- `Net.postForm/postJson/getJson` 用**本地回环服务**做真实往返（不碰外网）：方法、Content-Type、
  `Authorization: Bearer`、body 长度全部对得上。

### 结论
`fabric.mod.json` 的 `depends java` 对 1.16.5 现在是 `>=1.8`；玩家拿默认的 Java 8 起游戏就能用。
1.12.2 的最大一块前置也顺势清掉了。

## 15. 2026-10-26：Fabric 往下推到 1.3（Legacy Fabric），顺手修掉一个「无限卡死」

Java 8 兼容做完之后，1.12.2 剩的是「老 UI API」。但在动 UI 之前先查了一件事：
**Fabric 官方根本不支持 1.12.2**（meta.fabricmc.net/v2/versions/loader/1.12.2 直接 400，
官方 game 列表最老 1.14）。所以「1.12.2 + Fabric」以前在下载页里是灰的。

真正的答案是 **Legacy Fabric**（另一个项目，meta.legacyfabric.net / maven.legacyfabric.net），
支持 1.3～1.13.2，共 76 个版本，而且 profile json 与官方**结构完全一致** —— 所以安装链路不用分叉。

### 做了什么
1. 新增 `core/FabricMeta`：按 MC 版本分流 meta / maven / 镜像 base（1.13 及以前 → legacy；Legacy 没有 BMCLAPI 镜像，返回 null）。
2. `LoaderVersions.fabric` / `LoaderInstaller.installFabric` 全走它；Legacy 时**不再尝试**官方 fabric-installer
   （它把 meta 写死成 meta.fabricmc.net，只会 400，白跑一次）。
3. `LoaderSupport`：Fabric 规则从「≥1.14」改成「≥1.14 **或** ≥1.3（Legacy）」→ 下载页不再把老版本判成不支持。
4. **`writeProfile` 支持 `natives` 映射**（这是真 bug）：
   `lwjgl-platform:2.9.4+legacyfabric.17` **没有 plain jar**，只有 `-natives-windows.jar`；
   原来不看 `natives`、直接拼 plain 路径 → 404 → 整个安装失败。
   现在带 `natives` 的库只下 classifier 包、解压到 `versions/<id>/natives`，不进 classpath。

### 顺手修掉的两个基础设施问题
- **`Net.download` 有无限卡死的可能**：重跑矩阵时 1.20.5 的库下载卡了**整整一小时**（`.part` 停在 9.5 MB 不动）。
  `setConnectTimeout` 管不住 DNS 解析，`setReadTimeout` 也管不住某些半死连接。
  → 加了**硬超时看门狗**：整个传输放到工作线程，`join(max(90s, connect + 2*read))`，到点 `disconnect()` 并抛错交给上层重试。
- **`scripts/build-probe.ps1` 当时已经坏了**（只是没人重编）：它把 `core` 下所有 .java 一起编，
  而 1.16.5 那轮新加的 `core/Compat.java`（引 net.minecraft）、`core/Log.java`（引 slf4j）在探针环境里没有依赖。
  → 显式排除这两个文件（core 里没有任何代码调用它们）。另加了 `build-probe-alt.ps1`，
  输出到 `tools\probe\out-alt`，好在矩阵跑着的时候单独验新功能。

### 验证
| 项 | 结果 |
|---|---|
| 规则表 | `FABRIC x 1.12.2 => OK`（以前是 NO）|
| 安装 | `ProbeInstall testgame122 1.12.2 FABRIC` → loader 0.20.1，11 个库，**含 natives 1**，`complete=true` |
| 启动 | `ProbeLaunch` → `mainClass=KnotClient`、`java=jre-1.8`、`OK 到达 Setting user` |
| 工具链 | 本仓库 loom 1.9.2 + Gradle 8.14.2 不受影响；Legacy 需要的 loom 1.11.8 在 spike 里单独验证 |

### 1.12.2 的 mod 本体：先不碰，但已经摸清
用 legacy yarn（build.604）逐个核对主源码用到的 26 个 MC 类，**16 个同名**
（`Screen`/`ButtonWidget`/`TextRenderer`/`DrawableHelper`/`MinecraftClient`/`Text`/`Identifier`…）。
缺的是 `DrawContext`/`MatrixStack`（立即模式 GL）、`Registries`/`RegistryKey`（→ `Item.REGISTRY.put`）、
`ItemGroup`（包名不同）、`ClickableWidget`（→ `Screen.buttonClicked` 分派）、`MutableText`（→ `LiteralText`）。
spike 里写了个覆盖这些点的类，在 **loom 1.11.8 + legacy-looming 1.11.8 + `--release 8`** 下编译 + remapJar 都成功。
结论：**是「多一个 compat 目标 + 一个独立 gradle 工程」，不是重写**。细节见 `spike/lf122/VERDICT.md`。

**硬约束**：loom 1.16 要求 Gradle 9.x（本仓库 8.14.2）；而 Legacy Fabric API 聚合包 `1.13.5+1.12.2`
是用 loom 1.15.3 打的，装进 loom 1.11.8 会报 `Mod was built with a newer version of Loom`。
真要做，得先决定升不升 Gradle 9。

### 矩阵重跑结果（Java 8 重构 + Legacy Fabric 之后）
49 个组合全部 `install=OK launch=OK`（新增 `1.12.2-FABRIC-NONE` = Legacy Fabric）：

| 段 | 组合 | 结果 |
|---|---|---|
| Fabric 1.14～26.1 | 16 | 全 OK（1.20.5 那次卡死修好后 61s 通过）|
| Quilt | 4 | 全 OK |
| Forge 1.7.10～26.1 | 15 | 全 OK |
| NeoForge 1.20.1～26.1 | 7 | 全 OK（1.20.1 最久 435s，首次跑安装器 + 预取库）|
| OptiFine | 4 | 全 OK |
| Iris | 2 | 全 OK |
| **Legacy Fabric 1.12.2** | 1 | **OK（新增）** |

`verification/results.csv` 里多轮追加的 156 行已按「每个组合取最后一次」收敛成 49 行，
原始记录留在 `verification/results-history.csv`。

## 还有哪些 MC 版本要弄（mod 本体，2026-10-26 盘点）

### 硬事实
- Mojang release 共 **103** 个，去掉 26.x 五个（1.26 说 26.1/26.1.1/26.1.2/26.2/26.3）→ **98 个**。
- `dist/` 七个 jar 覆盖 **7 个精确版本**（1.16.5 / 1.17.1 / 1.18.2 / 1.19.4 / 1.20.1 / 1.21.1 / 1.21.4）。
- 每个 jar 的 `fabric.mod.json` 都写着**精确区间**（如 `>=1.21.4 <1.21.5`）→ 一个 jar 只能跑一个版本。
  **缺 91 个版本。**

### 按 API 时代分组（要新建 target 的只有「代」，不是「版本」）
| 时代 | 版本数 | 现状 |
|---|---|---|
| 1.0 – 1.12.2 | 52 | 无 target（1.12.2 spike 已通过，等 Gradle 9 决策）|
| 1.13 – 1.13.2 | 3 | 无（扁平化 UI 第一代，无 MatrixStack）|
| 1.14 – 1.14.4 | 5 | 无（无 MatrixStack）|
| 1.15 – 1.16.4 | 8 | 无（1.16.5 的 target 大概率能盖 1.16–1.16.4，1.15 要另验）|
| 1.17 | 1 | 1.17.1 target 可能直接盖（未验）|
| 1.18 – 1.18.1 | 2 | 1.18.2 target 可能直接盖（未验）|
| 1.19 – 1.19.3 | 4 | 无（1.19.3 改了 renderBackground 签名）|
| 1.20 | 1 | 1.20.1 target 可能直接盖（未验）|
| 1.20.2 – 1.20.4 | 3 | 无 |
| 1.20.5 – 1.20.6 | 2 | 无（组件系统断层，最贵）|
| 1.21 | 1 | 见下「1.21 实测」|
| 1.21.2 – 1.21.3 | 2 | 1.21.4 target 可能直接盖（未验）|
| 1.21.5 – 1.21.8 | 4 | 无 |
| 1.21.9 – 1.21.11 | 3 | 无（最新版，价值高）|

### 「放开区间」第一次实测（1.21.1 jar → 1.21）
做法：解包 dist 的 1.21.1 jar，把 `minecraft` 改成 `>=1.21 <1.21.2`，装 1.21+Fabric，塞进 mods 启动。
结果：
- ✅ mod 加载、物品注册（`mcver:download_tool`）、客户端初始化完成、到 `Setting user`，**没有 NoSuchMethodError / NoClassDefFoundError**。
- ❌ 自检钩子（`MCVER_OPEN_GUI` → 等 `TitleScreen` 再开界面）**没触发**，`screen=MAIN` 没出现；`PrintWindow` 抓到黑帧，界面层无法判定。
- 线程栈显示 Render thread 正常在 `RenderSystem.limitDisplayFPS`（不是死锁）。
→ 结论：**核心 API 跨 1.21/1.21.1 兼容，但界面层还没证据**；「一代一 jar」可行但每个版本都得做真窗口像素验证，不是免费的。

### 踩到的两个工具坑（这次才发现的）
1. **`ZipFile.CreateFromDirectory` 写出的 zip 条目用反斜杠**（`dev\mcver\McVerMod.class`），
   Fabric loader 找不到类 → `ClassNotFoundException`。必须手写 `ZipArchive`，把 `\` 换成 `/`。
2. **`Get-Content -Raw` 默认按 ANSI 读**，改 `fabric.mod.json`（含中文 description）会把 UTF-8 读成乱码再写回 →
   `MalformedJsonException: Unterminated object`。要用 `-Encoding UTF8` 或干脆用 node 改。

## 16. 2026-10-26 晚上：1.21.9 / 1.21.10 / 1.21.11 三个新 target（10 个 jar）

用户说「你决定」，选的是**最新三个版本**（人多，且不用碰 1.20.5 的组件断层）。

### 先说结论
三个版本**共用一份 compat**（`src/compat/1.21.9`），因为它们之间零 API 差异。
真窗口像素验证：1.21.9 = 15097 / 1.21.10 = 15096 / 1.21.11 = 15096，中心全 `C6C6C6`，`children=17`。

### 三个版本到底差在哪（只有 3 处，编译器报错照抄）
| 报错 | 差异 | 处理 |
|---|---|---|
| `method keyPressed in class Screen cannot be applied to given types; required: KeyInput` | 1.21.9 起 `Screen#keyPressed` 从 `(int,int,int)` 变成 `keyPressed(KeyInput)` | `ChromeScreen` 里把 `KeyInput` 拆成三个 int，交给新的 `keyPressedDefault` |
| `incompatible types: String cannot be converted to Category` | `new KeyBinding(...)` 第 4 个参数变成 `KeyBinding.Category`（record） | `KeyBinding.Category.create(Identifier.of("mcver","main"))` |
| `no suitable method found for saveScreenshot(File,String,Framebuffer,(t)->{})` | `ScreenshotRecorder.saveScreenshot` 多了第 4 个 int（缩放倍数） | `Compat.saveScreenshot` 传 `1` |

### 顺手把「键盘」也上提到 ChromeScreen（沿用 1.19.4 的教训）
之前 `MainScreen` 自己 `@Override keyPressed(int,int,int)`。这次换了签名，等于每个 target 都得复制 MainScreen。
现在：`ChromeScreen` 提供 `keyPressedDefault(int,int,int)`（稳定签名），版本相关的 `keyPressed` 只在 compat 层出现。
5 个旧 target 的 ChromeScreen 由 `scripts/add-key-hook.mjs` 一次性补上。

### 工具链：loom 1.9.2 → 1.13.6（Gradle 8.14.2 能用的最高档）
- loom 1.9.2：`Unsupported unpick version`（mappings 格式改了）
- loom 1.11.8：`Javadoc provided by mod (fabric-content-registries-v0) must have an intermediary source namespace`
- loom 1.15.3：要求 Gradle 9.2（`org.gradle.plugin.api-version 9.2.0`）→ 本仓库 8.14.2 装不上
- **loom 1.13.6（api-version 8.14）**：可用。10 个 target 全部 BUILD SUCCESSFUL。

### 验证脚本里揪出 3 个「假通过 / 假失败」的坑（都修了）
1. **`( | Where-Object {...}).Count` 在只命中一个对象时返回 ``**
   （PS 5.1，PSCustomObject 没有 Count 属性）→ `-gt 0` 为假 → 明明 FAIL 还打印「界面验证全部通过。」
   **必须 `@(...)` 包一层。** 这是本项目最怕的那类 bug：验证脚本自己撒谎。
2. **`children=` 永远读成 `-`**：解析发生在游戏进程还活着时，日志被独占写，`Select-String` 读不到
   （`-ErrorAction SilentlyContinue` 把异常吞成空）。改成**先抓图 → 杀进程 → 再读日志**，
   并且 `children` 现在参与判定（`-ne '-'`）。
3. **时间基准放错位置**：为了排除上一轮遗留日志，加了 `CreationTime >= launchedAt` 过滤，
   但 `` 是在「窗口出现之后」取的 —— 日志 2~5 秒就创建、窗口 15~20 秒才出现，
   于是永远「太早」被过滤掉。基准必须在 **Start-Process 之前**。
4. 另外：**1.21.9+ 首次启动会先弹 `AccessibilityOnboardingScreen`**（日志里是 `net.minecraft.class_8032`，
   yarn 映射到 `client/gui/screen/AccessibilityOnboardingScreen`），自检钩子只认 `TitleScreen`，
   所以永远等不到界面。`verify-gui.ps1` 现在启动前把 `options.txt` 的 `onboardAccessibility` 改成 `false`
   （只改这一行，其它原样保留）。**这不是产品 bug**，真人会点掉引导；但验证必须可复现。

### 排查手法（值得记）
自检钩子不触发时，光看「没有 screen=MAIN」什么也判断不出来。加了一行 `mcver: cur=<类名>` 日志，
每换一次屏幕打一次 —— 一跑就抓到 `class_8032` = 无障碍引导屏。
**以后新 target 起不来界面，先打印 currentScreen 类名，别猜。**

## 17. 2026-10-26 晚（21:00–23:0x）：一个 jar 盖住整个 1.21 系列（10 个 jar → 6 个）

用户连着两句批评，第一句是对的，第二句是需求：

1. 「你为什么一直在处理高版本？特别是 1.21.x 的」—— 认了。见上一节。
2. 「1.21 怎么可能还会出新版本，最新都到 26.x 了。你就不能直接弄 1.21.x 的通用版本吗？」
   —— 要求：**一个 jar 通吃 1.21 ~ 1.21.11**。

### 先摸清断点（javap 对着 remap 后的 jar，不靠记忆）
| API | 1.21 | 1.21.1 | 1.21.4 | 1.21.11 |
|---|---|---|---|---|
| `Screen.render(DrawContext,int,int,float)` | ✓ | ✓ | ✓ | ✓ |
| `Screen.renderBackground(DrawContext,int,int,float)` | ✓ | ✓ | ✓ | ✓ |
| `Element.mouseScrolled(double,double,double,double)` | ✓ | ✓ | ✓ | ✓ |
| `Screen.shouldPause()` / `close()` | ✓ | ✓ | ✓ | ✓ |
| `Screen.keyPressed(int,int,int)` | ✓ | ✓ | ✓ | ✗ `KeyInput` |
| `Item.Settings.registryKey` | ✗ | ✗ | ✓ | ✓ |
| `KeyBinding(...,String)` | ✓ | ✓ | ✓ | ✗ `Category` |
| `saveScreenshot(File,Framebuffer,Consumer)` | ✓ | ✓ | ✓ | ✓（多 int 的那条是另一条重载） |
| `TypedActionResult` | ✓ | ✓ | ✗ | ✗ |

真正会断的只有 4 处，而且都能绕开：
- `keyPressed` 变了 → **干脆不 override**。ESC 由 `Screen` 自己处理（会调到我们的 `close()`），
  旧 target 各自在 compat 里 override 自己那版签名。
- `saveScreenshot` 变了 → **删掉游戏内截图自检**（用户问「那是截给我看的吗？没必要」，
  确实没必要：界面验收早就换成 `PrintWindow` 从真窗口外部抓，更可信）。这一个删除直接消掉一处断点。
- `KeyBinding` / `UseItemCallback` 变了 → 右键和 M 键改成**每 tick 轮询**
  （`options.useKey.wasPressed()` + `GLFW.glfwGetKey`），两者在整个系列都不变。
- `registryKey` 1.21.2 才有 → 编译基准取**最新**的 1.21.11，旧版本靠 `NoSuchMethodError` 退回。

### 撞到的两个真问题

**① 已被 remap 的环境里，字符串反射查 MC 符号一律不通。**
第一版把 `registryKey` 写成反射（`Class.forName("net.minecraft.registry.RegistryKey")`），
一跑就崩：日志里是「该版本无 Item.Settings#registryKey，跳过」+ `NullPointerException: Item id not set`。
根因：mod 在网络包里被 remap 成 intermediary 名，运行时那个类叫 `class_5321`，方法叫 `method_xxxxx`。
**字符串里有的是 yarn 名，运行时一个都找不到。** 编译期引用（`RegistryKey.class`）才会被正确 remap。

**② `DrawContext#drawText` 的返回类型在系列中间从 int 变成了 void。**
两边 intermediary 名都是 `method_51439`，但描述符是 `...Z)I` 和 `...Z)V`。JVM 按描述符解析，
所以按一端编出来的 jar 在另一端直接 `NoSuchMethodError`（实测在 1.21 上抓到这条）。
同一个 class 文件无法同时引用两种返回类型 —— 于是在 `Ctx` 里改成**运行期按参数类型找方法**：
遍历 `DrawContext.class.getDeclaredMethods()`，匹配 `(TextRenderer, Text, int, int, int, boolean)`，
拿到 `MethodHandle` 再 `invoke`。**不写死方法名**，所以不受 remap 影响；找不到就退化用
`drawTextWithShadow`（三个版本描述符都是 `(...)I`，稳定）。
`fill(int×5,int)`、`drawItem(ItemStack,int,int)` 的 intermediary id 和描述符两端一致，不用管。

### 结果
`dist/` **10 → 6 个 jar**：`mc1.21`（盖 1.21~1.21.11 共 12 个版本）+ `mc1.20.1 / mc1.19.4 /
mc1.18.2 / mc1.17.1 / mc1.16.5`。`src/compat/` 从 7 个目录降到 **5 个**（1.21.1 与 1.21.9 两个目录删掉）。

真窗口像素验证（`verify-gui.ps1` 全量 17 个 target，一次跑完）：

| 版本 | 面板像素 | 中心 | children |
|---|---|---|---|
| 1.21 | 15097 | C6C6C6 | 17 |
| 1.21.1 | 15092 | C6C6C6 | 17 |
| 1.21.2 | 15105 | C6C6C6 | 17 |
| 1.21.3 | 15105 | C6C6C6 | 17 |
| 1.21.4 | 15092 | C6C6C6 | 17 |
| 1.21.5 | 15092 | C6C6C6 | 17 |
| 1.21.6 | 15105 | C6C6C6 | 17 |
| 1.21.7 | 15105 | C6C6C6 | 17 |
| 1.21.8 | 15092 | C6C6C6 | 17 |
| 1.21.9 | 15097 | C6C6C6 | 17 |
| 1.21.10 | 15096 | C6C6C6 | 17 |
| 1.21.11 | 15096 | C6C6C6 | 17 |
| 1.20.1 / 1.19.4 / 1.18.2 / 1.17.1 / 1.16.5 | 15061 / 15349 / 15357 / 15357 / 19754 | 全 C6C6C6 | 全 17 |

老 target 的数字和改造前**一模一样** → `Ctx` / `McVerClient` / `McVerMod` 的改动没有回归。

### `build.gradle` 新增 `-Pwide_mc=1.21`
依赖写成 `>=1.21 <1.22` 且产物名用 `mc1.21`。**不在命令行传 `">=1.21 <1.22"`**：
Windows 会把 `>` 当重定向（这个坑踩过多次）。

### 验证脚本又修了 4 个坑（都是「假通过 / 假失败」类）
1. `powershell -File script.ps1 -Targets 1.21,1.21.1` —— `-File` 不做语法解析，
   整个字符串当**一个** target 传进来 → 全部「跳过」，却仍打印「界面验证全部通过（0 个）」。
   现在脚本自己按逗号拆，并且 **0 个用例直接 exit 1**。
2. 新装的实例**没有 `options.txt`** → 引导屏挡在前面 → 永远等不到 `TitleScreen`。
   `Disable-Onboarding` 现在文件不存在就**创建**它（只写这一行）。
3. 新装的 `fabric-loader-0.20.1-X` 目录**没开版本隔离** → 日志落在 `testgame/logs`、
   `options.txt` 也不生效 → 表现是「没找到本轮日志」。补 `.mcver-isolated` 标记即可。
4. mods 目录里留着旧版本 jar（`mc1.21.9.jar` 和新的 `mc1.21.jar` 同时存在）→ 同一个 mod id 两份。
   `Ensure-Mods` 现在拷完就删掉其它版本的同名 jar。

### 另外两个操作级教训
- `[System.IO.File]::ReadAllText('相对路径')` 用的是**进程工作目录**，不是 PowerShell 的
  `cd` 之后的 `Get-Location` → 相对路径会解析到 workspace 去。一律用绝对路径。
- 用正则删方法时把 `public final class Compat {` 到结尾整段吃掉了（四个文件全废）。
  正确做法：**按行扫描 + 配对大括号**。这一轮直接 `git checkout` 恢复重做，比修补快。

### 待办 / 边界
- 右键打开从「原版物品使用事件」变成「每 tick 查 `useKey`」：行为等价，但**M 键不再能在
  「控制」里改键**（走 `GLFW` 原始按键）。若以后要恢复可配置键位，得为 1.21.9+ 单独做反射桥。
- 26.x 用户说先不管；1.12.2 本体仍卡在「升 Gradle 9」那个决策上。

## 15. 2026-10-27：mod 本体 Fabric 铺满 1.14 ~ 1.21.11（9 个 jar / 16 个实例像素验收）

以前只有 1.16+ 六个 jar。今天补上 **1.15 / 1.14** 两代，并把整条 Fabric 线全部重验一遍。

| 产物 | 覆盖 | 验收实例（center 全部 C6C6C6，children 全部 17）|
|---|---|---|
| mc1.14.jar | 1.14 ~ 1.14.4 | V1.14 15120、V1.14.4 15066 |
| mc1.15.jar | 1.15 ~ 1.15.2 | V1.15.2 15357 |
| mc1.16.jar | 1.16 ~ 1.16.5 | 1.16 19754、1.16.5 19754 |
| mc1.17.jar | 1.17 ~ 1.17.1 | 15357 / 15357 |
| mc1.18.jar | 1.18 ~ 1.18.2 | 1.18.2 PASS |
| mc1.19.jar | 1.19 ~ 1.19.2 | 1.19 15357 |
| mc1.19.3.jar | 1.19.3 ~ 1.19.4 | 1.19.4 15349 |
| mc1.20.jar | 1.20 ~ 1.20.6 | 1.20.1 15061、1.20.4 15092、1.20.6 15092 |
| mc1.21.jar | 1.21 ~ 1.21.11 | 1.21.1、1.21.4、1.21.11 |

hits 的差异只是字体度量不同，量级和 Fabric 历史值一致。

### 1.15 / 1.14 为什么不是「顺手加个 target」

这两代 API 是**倒退**的，比 1.16 少东西：

- **1.15**：`Screen#render(int,int,float)`（没有 MatrixStack）、`renderBackground()` 无参、
  `addButton(AbstractButtonWidget)`、`Screen#font`（1.16 才改名 textRenderer）、`MutableText` 不存在、
  `TextFieldWidget` / `ButtonWidget` 的文案参数是 **String**、`TextRenderer#getWidth` 不存在（只有 `getStringWidth`）、
  `DrawableHelper#fill` 是 5 参静态版、`KeyBinding` 在 `net.minecraft.client.options`、物品图标叫 `renderGuiItem`。
- **1.14**：在上面基础上 `TextRenderer` 连 Matrix4f 长重载都没有（只有 `draw(String,int,int,int)` /
  `drawWithShadow`），`MinecraftClient#getBufferBuilders()` 不存在，`TypedActionResult.success/pass` 是 1.15 才加的
  （只能 `new TypedActionResult<>(ActionResult.X, stack)`，且 `UseItemCallback` 在 1.14 直接返回 `ActionResult`）。

为了让页面代码一行不改，新增三个**跨版本 shim**（每个 target 一份实现）：
`ui/Field`（输入框文案 String/Text）、`Btn.label(button, String)`、`ui/Measure.width(tr, String)`。
同时把 `Page.add` / `MainScreen.add` / `ChromeScreen.addWidget` 的泛型从 `ClickableWidget` 放宽成 `Object`
（1.15 根本没有 ClickableWidget 这个类），返回值本来没人用。

### 踩到的坑

1. **Fabric API 的 maven 包不是分发版**：`net.fabricmc.fabric-api:fabric-api:0.28.5+1.15` 只有 6 个文件（core module），
   运行时 `ClassNotFoundException: FabricItemGroupBuilder`。真正的 bundle（带 `META-INF/jars/*` 33 个模块）
   要从 **Modrinth maven** 拿：`api.modrinth.com/maven/maven/modrinth/fabric-api/<ver>/fabric-api-<ver>.jar`。
2. **Modrinth 上「支持 1.14 的最新 release」是 0.2.7+build.127**（远古版本，缺 ItemGroup API）→ 1.14 崩在客户端入口。
   实际上 **0.28.5+1.14 在 1.14 上照样能用**，map 里手动钉住。
3. 换 MC 版本必须换 fabric-api（同一实例里旧 api 会被 loader 拒载），`verify-fabric-all.mjs` 已做。
4. 26.1 没有对应 jar：`mc1.21.jar` 只声明 `>=1.21 <1.22`，26.1 的新版本号不在其内；
   **mod 本体对 26.1 不做承诺**（启动器侧 26.1 Fabric 是 L2，两回事）。

### 工具

- `scripts/verify-fabric-all.mjs`：按 `fabric.mod.json` 的区间挑 jar + 按版本换 fabric-api + 逐实例跑
  `verify-gui-one.mjs`，输出 PASS/FAIL 汇总。判据仍是「真窗口 + 中心 C6C6C6 + hits>1e4 + children=17」。
- `scripts/prep-1.15.mjs`（机械改 main 里的三处写法定型）、`scripts/fix-compat-shims.mjs`（补 import / Btn.label）。

### 还没做

- **Legacy Fabric（1.3 ~ 1.13.2）**：Fabric 官方到 1.14 为止，再往下要 `net.legacyfabric:yarn` + ornithe-loom，
  且 1.13 之前的 UI 是 `GuiScreen` + `TextComponent` + 无 MatrixStack 的另一套世界 ——
  那不是一个 target，是**另一个代码库**。启动器侧 1.12.2 Legacy Fabric 已经 L2，mod 本体还没有。
- Forge/NeoForge 1.16 ~ 1.20.4（需要 SRG reobf 管线）。

## 16. 2026-10-27 晚：Fabric 全版本 —— 1.7.10 一路铺到 1.13.2（16 个 jar）

这一节接第 15 节。第 15 节写「Legacy Fabric 1.3~1.13.2 不是另一个 target，是另一个代码库」——
**这个判断是错的**，而且错得很贵。真相是：Legacy Fabric 的 yarn 是 **yarn v2 风格的 retrofit 命名**，
1.7.10 往上到 1.13.2 全部是 `net/minecraft/client/gui/screen/Screen`、
`Text`/`LiteralText`、`Item.REGISTRY`、`ButtonWidget` 这套东西，
和 1.12.2 / 1.14 是同量级的差异，不是「远古世界」。

### 现在的覆盖

| 版本 | jar | 实例 | 结果 |
| --- | --- | --- | --- |
| 1.7.10 | mc1.7.10 | V1.7.10-FABRIC | PASS 15128 / children 17 |
| 1.8 · 1.8.8 · 1.8.9 | mc1.8.9（`>=1.8 <1.9`） | V1.8 / V1.8.8 / V1.8.9-FABRIC | PASS 15142 / 15087 / 15087 |
| 1.9.4 | mc1.9.4 | V1.9.4-FABRIC | PASS 15065 |
| 1.10.2 | mc1.10.2 | V1.10.2-FABRIC | PASS 15065 |
| 1.11.2 | mc1.11.2 | V1.11.2-FABRIC | PASS 15065 |
| 1.12.2 | mc1.12.2 | V1.12.2-FABRIC | PASS 15065 |
| 1.13.2 | mc1.13.2 | V1.13.2-FABRIC | PASS 15066 |
| 1.14 ~ 1.21.11 | 9 个（见第 15 节） | 16 个实例 | PASS |

判据照旧：真窗口 `PrintWindow` 抓图 → 中心像素 `C6C6C6`、面板色命中 > 1 万、`children=17`。
一次批量跑（`verify-fabric-all.mjs`）9 个 legacy 实例全 PASS。

**Fabric 官方 + Legacy Fabric 能装 loader 的版本，现在只剩 1.7.2~1.7.9 和 1.6.4 及以下没做。**

### 四个 API 代（每个代一份 compat 目录，页面代码零改动）

- `compat/1.13.2` —— 1.13 的唯一版本（Legacy Fabric 只支持 1.13.2，1.13/1.13.1 没有 loader）。
  GUI 重构后：有 `Element`（`class_4122`）、`Screen#render(int,int,float)` 不自动画背景、
  按钮**没有回调接口**（`PressAction` 是 1.14 才有），点击终点是 `ButtonWidget#method_18374(double,double)`
  → 只能继承一个私有子类覆盖它。`mouseScrolled(double)` 只有一个参数（没有鼠标坐标）。
- `compat/1.11.2` —— 1.11 / 1.12 两代（1.12.2 单独一份，因为它要多一个 `init()` 可见性规则）。
- `compat/1.10.2` —— 1.9 / 1.10：和 1.11 的差别只有两处：`Item#method_11373(ItemStack,World,PlayerEntity,Hand)`
  才是右键钩子；`Screen` 没有 `addButton(T)`，只能 `buttons.add(...)`。
- `compat/1.8.9` —— 1.8 全家：右键是 `Item#onStartUse(ItemStack,World,PlayerEntity)` 返回 ItemStack，
  `ItemRenderer` 的方法叫 `renderInGuiWithOverrides`。一个 jar 声明 `>=1.8 <1.9`。
- `compat/1.7.10` —— 最老一代：`TextFieldWidget` 构造器**没有 id 参数**；
  `TextRenderer#draw` 的坐标是 **int**；`Text` 没有 `getString()`（用 `asUnformattedString()`）；
  `ItemRenderer` 是 `Screen` 上的 **protected static 字段**，画物品要自己传 TextRenderer + TextureManager
  （`method_1549`）；`Item.REGISTRY` 是 raw `SimpleRegistry`，注册是
  `add(int id, String name, Object)`，id 得自己扫空位（从 4000 起）。

### 这轮的坑（都记下来，别再踩）

1. **1.13.2 的 6 参 ButtonWidget 构造器是 `(id, x, y, w, h, msg)`，id 在最前面**（和 1.12 一样，和 1.14 相反）。
   传错 → 按钮宽度变成 id 值，屏幕上出现一片盖住面板的白条，`center=FFFFFF`。定位靠三步二分。
2. **1.13.2 没有 `net.minecraft.client.render.item.ItemRenderer`** —— GUI 画物品挂在
   `HeldItemRenderer#method_19376`（就是老 ItemRenderer 改名）。别顺着类名找。
3. **Legacy Fabric maven 路径多一层**：`repo.legacyfabric.net/legacyfabric/net/legacyfabric/yarn/...`，
   版本号里的 `+` 要 encode 成 `%2B`。
4. **`mk-legacy-named.mjs` 要能找到「合并版」原版 jar**：安装器会把 vanilla 合并进
   `versions/<instance>/<instance>.jar`，`versions/<mc>/<mc>.jar` 那时已经不存在了。
5. **`.mcver-isolated` 是启动时才写的**，装完那一刻没有 → 不能靠它判断 mods 目录，
   干脆 `versions/<id>/mods` 和 `testgame/mods` 两处都放（loader 按 mod id 去重 + 按 depends 过滤）。
6. **共用 mods 目录不能乱清**：`put-mod` 早期版本会删掉别的实例刚装的 jar，
   结果那次验证只加载了 4 个 mod（看起来像「mod 没生效」，其实是自己删的）。
7. **1.7.9 起不来是 Legacy Fabric 自己的问题**：`Mapping source name conflicts detected` →
   `Unfixable conflicts`，发生在 loader 的 game remap 阶段，和本 mod 无关。
8. **1.7.2 的 `MinecraftClient` 没有 `execute(Callable)`**（1.7.10 才有；1.7.2 是
   `func_152344_a(Runnable)` 且 yarn 没命名）。跨 1.7.x 用一个 jar 盖不住 → 暂时只做 1.7.10。

### 工具

- `scripts/mk-legacy-named.mjs <mc> [--instance <id>] [--yarn <ver>]`：通用前置，
  自动取 yarn 版本、下 intermediary + yarn、两步 remap 出 named jar。
- `scripts/build-1132.mjs <mc> [--instance <id>] [--mcrange "<range>"]`：通用 legacy 构建
  （javac --release 8 → jar → remap named→intermediary → dist）。按 MC 版本自动挑 compat 代。
- `scripts/put-mod.mjs <instance> <jar>`：装进实例（两处都放）。
- `scripts/verify-fabric-all.mjs`：矩阵跑，`<1.14` 自动跳过 fabric-api。

## 17. 2026-10-28：Forge / NeoForge 1.20.1 ~ 1.20.4（SRG reobf 管线）

Fabric 侧铺完之后转 Forge。这一轮把「SRG 时代」（运行时方法/字段还是 `m_/f_` 的版本）
打通了，5 个新 target 全部像素验收 PASS。

| target | jar | 实例 | 结果 |
| --- | --- | --- | --- |
| forge-20.4 | `watercraftlauncher-forge-1.0.0-mc1.20.4` | V1.20.4-FORGE | PASS C6C6C6 / 19468 / 17 |
| forge-20.2 | `...-mc1.20.2` | V1.20.2-FORGE | PASS 19468 / 17 |
| forge-20.1 | `...-mc1.20.1` | V1.20.1-FORGE | PASS 19468 / 17 |
| neoforge-20.4 | `...-neoforge-1.0.0-mc1.20.4` | V1.20.4-NEOFORGE | PASS 19467 / 17 |
| neoforge-20.2 | `...-neoforge-1.0.0-mc1.20.2` | V1.20.2-NEOFORGE | PASS 19467 / 17 |

判据照旧：真窗口 `PrintWindow` → 中心 `C6C6C6`、面板色命中 ≈1.9 万、`children=17`。

### 关键判断（先搞清楚「哪一代用什么名字」）

| 时代 | 代表 | 运行时命名 | 怎么编 |
| --- | --- | --- | --- |
| SRG | Forge ≤ 1.20.4、NeoForge 20.1 | 类=官方名，方法/字段=`m_/f_` | 先 mojmap 编，**编完 reobf 回 SRG** |
| 官方名 | Forge ≥ 1.20.6、NeoForge ≥ 20.2 | 全是官方名 | javac 直编，不用 reobf |

> **NeoForge 比 Forge 早一档**：NeoForge 20.2 的 client jar 里已经是 `stacksTo/durability`
> 这种官方名了，而同一版本的 Forge 49.x 还是 `m_41487_`。别用「1.20.5」当分界线。

### SRG reobf 管线（`scripts/mk-srg-mojmap.mjs`）

1. 取 `de.oceanlabs.mcp:mcp_config:<mc>-<ts>` 里的 `*-mappings.tsrg`（官方 → srg 的类 id）。
2. 取 Mojang `client_mappings`（version json 的 `downloads.client_mappings.url`）。
3. 用 obf 名做桥梁算出 **srg → official**，产出 `srg2mojmap.tiny` / `mojmap2srg.tiny`。
4. `client-<mc>-<ts>-srg.jar` → `client-<mc>-mojmap.jar`，`*-universal.jar` 同样转一份。
5. `build-neoforge.mjs` 用这两份编，`jar` 打完再 remap 回 SRG（`mojmap2srg.tiny`）。

### 三个格式事实（不搞清楚会白跑一整天）

1. **Mojang 映射是「左=官方名，右=混淆名」**（类、字段、方法一律）。反过来读 → 77xx 个类只有
   739 个能对上，方法映射只剩 114 条，且会把 `stacksTo` 错配成同 obf 名下的 `durability`。
2. **tsrg2 的方法描述符用 obf 类名**（`(Lcuk;Lhx;)Z`），不是类 id；而它左边那列「srg 类名」是
   `net/minecraft/src/C_182965_` 这种 **id**。但运行时 `client-*-srg.jar` 里的类名是**官方包名**，
   只有成员还叫 `m_/f_` → 类映射是恒等的，真正要换的只有成员。
3. **tiny v2 的成员行是「描述符在前」**：`m <desc> <src> <dst>`。描述符放末尾时，字段名叫 `a`
   这种单字符会被 mapping-io 当成描述符列 → `missing name columns`。tiny v1 更不能用
   （`FIELD owner name desc dst`，我们没有全部字段描述符）。

### 这轮的坑（按耗时）

1. **`Screen#addWidget` 的泛型上限逐版本不同**：1.20.4 是 `GuiEventListener & NarratableEntry`，
   主源码里 `MainScreen.add(Object)` 传 Object 编不过 → overlay 改成 `addWidget(Object)` + 运行时
   `instanceof AbstractWidget`，`Page.add` 自己返回泛型。
2. **`renderBackground` 是版本行为**（Fabric 1.20.1 踩过，这里又踩一次）：1.20.1 的 `Screen#render`
   **不会**自己调 `renderBackground`，而且方法只有**单参** `renderBackground(GuiGraphics)`；
   1.20.2+ 才有 `(GuiGraphics,int,int,float)`。`mouseScrolled` 同理：1.20.1 是 3 参（无 horizontal）。
   → 单独开一份 `modx/shims201/`。
3. **实例 json 常常漏掉 loader 自己的 jar**：Forge 1.20.1/1.20.2 的 json 里没有 `fmlcore`，
   但 `libraries` 里躺着 → `@Mod` / `ModLoadingContext` 整包找不到。现在按
   「目录名 == `<mc>-<loaderVer>` 或 `<loaderVer>`」精确补 classpath。
4. **NeoForge 20.2/20.4 的 `-client.jar` 只有被改过的类**，完整原版要另外给（`client-<mc>-mojmap.jar`），
   否则 `net.minecraft.core.registries` 都不认识。
5. **`mandatory` 字段**：Forge 一律要；NeoForge **20.1~20.4（FML 1.x/2.x）也要**，20.6+（FML 3/4）写了
   反而报未知字段。
6. **`loaderVersion` 别按 MC 版本猜**：NeoForge 20.4 是 FML `[2,)`，20.2 是 `[1,)` → 猜错报
   `Missing language javafml version [2,) ... found 1.0`。
7. **`BuildCreativeModeTabContentsEvent` 在 mod bus**：挂到 `NeoForge.EVENT_BUS`（game bus）会
   `IllegalArgumentException: ... not valid for this bus`，整个 mod 变「broken mod state」。
8. **NeoForge 20.x 的 `@EventBusSubscriber` 各家不一致**：20.1 没有 `Bus` 枚举、20.4 叫 `FORGE` 不叫
   `GAME` → 干脆丢掉注解，构造函数里 `NeoForge.EVENT_BUS.addListener(...)` + `modBus.addListener(...)`。
9. **`verify-gui-one.mjs` 的抓图会卡死**：`capture-window.ps1` 偶发阻塞在 PrintWindow，
   `execFileSync` 没超时 → 整个验收挂住却不出结论。已加 `timeout: 15000`。
10. **抓图偶发 `no-window`**：同一次运行 `children=17`（界面确实画了）但 PrintWindow 拿不到窗口，
    重跑一次就过。看到 `no-window` 且 children=17 先重跑，别急着改代码。

### 没做的

- **NeoForge 20.1**：它就是 Forge 47 换皮——universal jar 里的包名还是 `net.minecraftforge.*`，
  FML 又是独立版本号（47.1.47 / 1.0.16）。要做得另开一路 overlay，收益不大，暂缓。
- **Forge 1.19.4 / 1.18.2 / 1.17.1 / 1.16.5**：SRG 管线本身能复用，但这些版本没有 `GuiGraphics`，
  渲染要走 `PoseStack` → 需要一套和 Fabric 侧 `ui/Ctx` 同级的 mojmap 版适配层，工作量在 compat 上，不在管线。
## 18. 2026-10-28（续）：Forge 一路铺到 1.17.1，外加 1.21.4

§17 之后又把 Forge/NeoForge 的 mod 本体往下铺了 5 个 target，全部像素验收 PASS：

| target | jar | 实例 | 结果 |
| --- | --- | --- | --- |
| forge-19.4 | `watercraftlauncher-forge-1.0.0-mc1.19.4` | V1.19.4-FORGE | PASS C6C6C6 / 19463 / 17 |
| forge-18.2 | `...-mc1.18.2` | V1.18.2-FORGE | PASS 19463 / 17 |
| forge-17.1 | `...-mc1.17.1` | V1.17.1-FORGE | PASS 19463 / 17 |
| forge-21.4 | `...-mc1.21.4` | V1.21.4-FORGE | PASS 19468 / 17 |
| neoforge-21.4 | `...-neoforge-1.0.0-mc1.21.4` | V1.21.4-NEOFORGE | PASS 19471 / 17 |

现在 Forge 侧覆盖 **1.17.1 / 1.18.2 / 1.19.4 / 1.20.1 / 1.20.2 / 1.20.4 / 1.20.6 / 1.21.1 / 1.21.4**，
NeoForge 侧 **20.2 / 20.4 / 20.6 / 21.1 / 21.4**。

### 关键新招：`--patched`

`mk-srg-mojmap.mjs --patched` 改用 Forge 的 **`forge-<mc>-<ver>-client.jar`（打过补丁的那份）**
当输入，而不是 vanilla 的 `client-*-srg.jar`。差别很要命：

- vanilla srg jar 里 `Item implements ItemLike` —— **不**继承 `ForgeRegistryEntry`；
- 那是 Forge 在运行期用 ModLauncher 现场打补丁加的，所以拿 vanilla 编 `DeferredRegister<Item>`
  会卡在 `T extends IForgeRegistryEntry<T>` 上编不过；
- 而「退一步直接 `Registry.register(...)`」也不行：Forge 37 构造期注册表已锁，
  实测 `IllegalStateException: Can not register to a locked registry`。

**1.16.5 / 1.17.1 必须开 `--patched`。**

### PoseStack 时代（1.17.1 ~ 1.19.4）的三层差异

1. **渲染**：没有 `GuiGraphics`，`fill` 走 `GuiComponent.fill(pose,...)`、文字走
   `Font#draw/drawShadow`、物品走 `ItemRenderer#renderAndDecorateItem`。
   注意 **1.18 起 `renderAndDecorateItem` 不收 PoseStack**（1.18 把矩阵改成 RenderSystem 全局，
   1.19 又加回来，1.20 换成 GuiGraphics）—— 一个方法在三个相邻版本里换了三次签名。
2. **钩子**：`render(PoseStack,int,int,float)`、`renderBackground(PoseStack)` 单参、
   `mouseScrolled(3 参)`、`isPauseScreen()`（mojmap 到 1.20 才改名 shouldPause），
   且 `Screen#render` **不会**自己调 `renderBackground` → 面板要在 `render` 里手动先画。
3. **零散 API**（每代都在挪）：
   - `Button.builder` 是 1.19 才有 → 1.18.2 只能用 `new Button(x,y,w,h,Component,OnPress)`；
   - `Component.literal` 是 1.19 才有 → 1.18.2 用 `new TextComponent(...)`；
   - `RegistryObject` 在 1.17.1 还叫 `net.minecraftforge.fmllegacy.RegistryObject`，1.18.2 才回 `registries`；
   - `DeferredRegister.create` 的 `ResourceKey` 重载 1.18.2 才有，1.17.1 只能给 `ForgeRegistries.ITEMS`；
   - `CreativeModeTabEvent` 不存在于 1.18.2，创造栏靠 `Item.Properties#tab(CreativeModeTab.TAB_TOOLS)`
     （这个方法 1.19.4 就删了，1.19.4 又换成 `CreativeModeTabEvent.BuildContents` +
     `CreativeModeTabRegistry.getName`）；
   - **1.17 自带 Java 16** → target 的 `java` 必须是 16，编 17 会
     `UnsupportedClassVersionError: class file version 61.0`。

### 1.21.4：`Item.Properties#setId`

1.21.2 起注册物品必须显式给 id，否则 `NPE: Item id not set`（崩在 `Item.<init>` 里，
堆栈根本不提 setId，容易查半天）。→ `modx/forge214` / `modx/neoforge214` 各覆盖一份 `McVerMod`。

### 验证脚本：日志不是唯一信号

**Forge 1.17.1 的 SLF4J 输出根本不进 stdout 重定向文件**（MC 自己的行有、我们的没有，
`latest.log`/`debug.log` 都是 0 字节）。但 GUI 明明画出来了 —— 所以 `verify-gui-one.mjs` 现在
把 `gameDir/mcver-debug.txt`（MainScreen 顺手写的一行 `screen=MAIN ... children=17`）也当成
「可以去抓图了」的信号。**判据还是像素**，这一点没松。

### 没做

- **Forge 1.16.5**：官方映射在 1.16.5 是**残缺的**（`Font#drawShadow` 之类还是 `func_`），
  所以「mojmap 编 + reobf」这条路走不通；要么改用 **SRG 名编源码**（需要一个源码级
  mojmap→SRG 改名器，按类上下文解析方法名），要么放弃。工作量在后者，不值当，先搁。
- **MC 26.1 / Forge 62**：三个新坑都已探明，写在 `build-neoforge.mjs` 的 TODO 里：
  ① 类是 class 69（Java 25），`--release 21` 会拒读（要 Java 25 的 javac + `--release 25`）；
  ② `ResourceLocation` 改名 `net.minecraft.resources.Identifier`；
  ③ loader 拆成一堆模块，实例 json 列不全，得把「按目录名补 classpath」用到这一代。

## 19. 2026-10-28（第三轮）：Forge 1.16.5 —— 上次「没做」的理由是错的

**先说结论**：Forge 1.16.5（36.2.42）**PASS**，像素验收 `C6C6C6 / 19463 / children=17`。

上一轮我在本文 §18 写了「1.16.5 官方映射残缺（`Font#drawShadow` 还是 `func_`），
所以 mojmap 编 + reobf 走不通」。**这个结论是假的**，起因是我把一次**解析失败**当成了映射缺陷：

- 1.16.5 的 `mcp_config` 给的是 **tsrg v1**（无文件头，`obfClass srgClass` + `\t obf srg`），
  而 `parseTsrg` 只认 tsrg2（要求类行 3 列）→ 每一行都被当垃圾丢掉；
- 于是 `srg2mojmap.tiny` 只剩一个 **20 字节的文件头**，remap 等于空操作，
  产出的「mojmap jar」其实是 SRG jar 原件 —— 里面当然是 `FontRenderer`、`func_xxxx`；
- 我看到那些旧名字就断定「Mojang 在 1.16.5 没给全」，实际上
  `client-mappings-1.16.5.txt` 里 `func_` 计数是 **0**，`Font#drawShadow` 之类全都在。

### 修掉的三件事（都在 `scripts/mk-srg-mojmap.mjs`）

1. **tsrg v1 解析**：类行两列、字段两列、方法三列且**描述符在中**（`obf (desc) srg`）。
   判别式只看有没有 `tsrg2` 文件头。修完 `miss=0`（35253 个方法全中）。
2. **真改类名**（这是 1.16.5 与 1.17+ 的根本差别）：
   - tsrg2 的 srg 类名是 `net/minecraft/src/C_182965_` 类 id，运行时 jar 用官方包名 → **只能恒等**；
   - tsrg v1 的 srg 类名就是 jar 里的真包名（`gui/FontRenderer`、`gui/AbstractGui`），
     而 mojmap 叫 `Font` / `Gui` → **必须真改名**，否则源码连 `Font` 都找不到。
   按世代选「源命名空间」（`isV2 ? moj : srg`），描述符也跟着换命名空间。
3. **反向映射也要一起换**（reobf）：类对换顺序 + 描述符换回 mojmap 命名空间。
   以前恒等，倒名字就够；1.16.5 上漏了这步 → 编译通过、装进去运行期
   `NoClassDefFoundError: net/minecraft/world/item/Item`（我们引的是 mojmap 名，游戏里叫 `net/minecraft/item/Item`）。

### 还有一件 Forge 特有的事：client jar 不一定全量

`--patched` 吃 Forge 的 `forge-<mc>-<ver>-client.jar`：

| 版本 | 补丁包内容 |
| --- | --- |
| Forge 37（1.17.1） | **全量**（整个游戏 + 补丁） |
| Forge 36（1.16.5） | **只有增量补丁**，1275 个类，连 `Font`/`Button` 都没有 |

所以 `--patched` 现在统一走「原版 srg jar remap 出全量 → 补丁包 remap 出覆盖层 → 合并（补丁优先）」，
用 JDK 自带 `jar` 解压/重打，不引第三方 zip 库。症状很有迷惑性：jar 里**有** `Minecraft`，
但 `Font` 一个都没有。

### 适配层：`modx/forge16`（几乎就是 `forge17` 减三处）

渲染钩子跟 1.17.1 一模一样（`PoseStack`、单参 `renderBackground`、三参 `mouseScrolled`、
`isPauseScreen()`），只差控件管理：

- `addRenderableWidget` 是 1.17 才有 → 1.16.5 用 `addButton(T)`；
- `clearWidgets()` 也是 1.17 才有 → 自己清 `children` + `buttons` 两个列表；
- `RegistryObject` 在 `net.minecraftforge.fml`（1.17 挪 fmllegacy、1.18.2 才回 registries）；
- 创造栏 `Item.Properties#tab(CreativeModeTab.TAB_TOOLS)`；
- **`core/Log` 要换 log4j2**（1.16.5 类路径上没有 slf4j，直接 `NoClassDefFoundError: org/slf4j/LoggerFactory`
  崩在 `McVerMod.<clinit>`），复用 Fabric 那份 `mod/src/compat/1.16/java`；
- 1.16.5 跑 Java 8 → target `java: 8`，顺手把 `modx/shims21/ChromeScreen` 的
  `instanceof X y` 模式匹配改成老写法（`-source 8` 不支持），对 1.20+ 无影响。

### 回归

映射管线动了核心，重新生成 1.20.1 的映射确认 `renamed=0`（tsrg2 恒等，符合预期），
并重建 + 重验 Forge 1.17.1（`--patched` + tsrg2 那条路）。

## 20. 2026-10-28（第四轮）：Forge 1.14.4 —— 退到 PoseStack 之前

**结果**：Forge 1.14.4（28.2.30）**PASS**，像素验收 `C6C6C6 / 15066 / children=17`。

命中数比其它 target 少（1.5 万 vs 1.94 万）不是画错了：日志里 `gui=427x240 panel=375x220@26,10`，
这个实例的 GUI 缩放比别的大，同样 870×519 的窗口里面板只占更小的逻辑面积。判据（中心色 + 命中
>1 万 + children=17）都满足。

### 1.14.4 比 1.16.5 又退一整代

| | 1.16.5 / 1.17.1 | **1.14.4** |
| --- | --- | --- |
| 渲染入口 | `render(PoseStack,int,int,float)` | **`render(int,int,float)`**（没有 PoseStack，1.15 才引入） |
| 背景 | `renderBackground(PoseStack)` | **`renderBackground()`** 无参 |
| 填充 | `GuiComponent.fill(pose,…)` | **`GuiComponent.fill(int×5)`** 静态无 pose |
| 文字 | `Font#drawShadow(pose, Component/String,…)` | **`Font#drawShadow(String,…)`**，**没有 Component 重载** |
| 物品 | `renderAndDecorateItem(stack,x,y)` | 同（1.18 起才有别的变化） |
| 按钮 | `new Button(x,y,w,h,Component,OnPress)` | **`new Button(x,y,w,h,String,OnPress)`** |
| 输入框占位符 | `EditBox(...,Component)` | **`EditBox(...,String)`** |
| 文本工厂 | `new TextComponent` / `StringTextComponent`（1.16.5） | **`TextComponent`**，且**没有 `MutableComponent` 接口** |
| 截字 | `Font#plainSubstrByWidth` | **`Font#substrByWidth`** |
| 注册 | `DeferredRegister.create(...)` | **`new DeferredRegister<>(...)`**（静态 create 1.16 才有） |

`modx/forge14` 因此把 `Ctx` 做成**无状态单例**（`Ctx.of()` 不带参数），`ChromeScreen` 自己在
`render` 里先调 `renderBackground()`。

### 新增：`srcReplace`（逐 target 源码级改写）

有些差异小到不值得开一份 overlay（一两句），但共享源码又不能改。`build-neoforge.mjs` 现在支持
target 级 `srcReplace: [[旧, 新], ...]`，在「生成源码 → 覆盖 overlay」之后、编译之前对全部
`.java` 做一次纯文本替换。1.14.4 用了两条：

- `.plainSubstrByWidth(` → `.substrByWidth(`
- `h, Txt.of(placeholder))` → `h, placeholder == null ? "" : placeholder)`（EditBox 收 String）

**注意**：这是「就地改写生成物」，不要拿它盖逻辑差异，语义级差异仍然要 overlay。

## 21. 2026-10-28（第五轮）：切开 1.12.2 那代 —— MCP/SRG 管线 + 两件意外收获

这一轮的产出不是「又一个 target」，而是**一条新管线**和两个被证伪的旧判断。

### 21.1 NeoForge 20.1 白拿

上一轮把 NeoForge 20.1 记成「要另开 `net.minecraftforge.*` 包名 overlay」。**实测不用**：
NeoForge 20.1 的发行物叫 `net.neoforged:forge:1.20.1-47.1.84`，包名仍是 `net.minecraftforge.*`，
`mods.toml` 也还是 `mods.toml`（`neoforge.mods.toml` 是 20.2+ 的事）。

所以直接把 Forge 1.20.1 的 jar 丢进 `V1.20.1-NEOFORGE` 就过：

| target | 判据 |
| --- | --- |
| NeoForge 20.1（复用 `watercraftlauncher-forge-1.0.0-mc1.20.1.jar`） | C6C6C6 / 19467 / children=17 |

**分界线钉住**：NeoForge **20.1 = Forge 47 换皮（同一份 jar 可用）**；
**20.2 起** 才是 `net.neoforged.*` + `neoforge.mods.toml`（那条路已经有自己的 target）。

### 21.2 1.12.2 的映射：没有 mojmap，只有 MCP/SRG

1.12.2 完全没有官方映射（`client_mappings` 是 1.14.4 才开始发的）。Forge 那一代的编译/运行命名空间是
**SRG**：类名已经是真包名（`net/minecraft/client/gui/GuiScreen`），成员名是 `func_xxxxx` / `field_xxxxx`。

映射来源是 `mcp_config` 里的 **`config/joined.tsrg`**（tsrg v1，`obf srg`）。新脚本 `scripts/mk-mcp-srg.mjs`：

1. 从 `mcp_config-<mc>-*.zip` 取出 `config/joined.tsrg`；
2. 解析 v1（类行 `obf srg`，成员行缩进；方法行 `obf (desc) srg`）；
3. **补字段描述符** —— tiny v2 的字段行必须带 desc，而 tsrg 只给名字对。
   于是自己写了个最小 class 文件解析器（跳过常量池/接口表/属性，~30 行），从原版 jar 里读字段描述符；
   1.12.2 上 11915 个字段里有 108 个在 jar 里找不到（继承来的/合成字段），跳过即可；
4. remap 原版 jar → `modx/srg/1.12.2/client-1.12.2-srg.jar`；
5. **顺带把 Forge 的 jar 也 remap**：1.12.2 的 `forge-1.12.2-14.23.5.2864.jar` 是 **notch 名**
   （`setRegistryName(nf)`、`Class<? extends avj>`），跟 1.16+ 的 `forge-*-client.jar`（已 srg）完全不同。
   不转的话拿它编 mod 类型全对不上。

**关键好处**：这条路是「SRG 编 → **不 reobf**」。字节码里引用的就是运行期名字，少一跳就少一类错
（对比 1.16.5 的「mojmap 编 → reobf 回 SRG」）。

### 21.3 原版 `Item` 不实现 `IForgeRegistryEntry`（和 1.16.5 同款坑）

原版 jar 里的 `net.minecraft.item.Item` 并没有 `implements IForgeRegistryEntry` —— 那是 Forge 用
**binpatch** 在运行期加的。于是 `ForgeRegistries.ITEMS.register(item)` 编译期过不了类型约束
（`V extends IForgeRegistryEntry<V>`），而 `(IForgeRegistryEntry) item` 这种强转 javac 也拒绝（两者编译期无关系）。

**解法：经 Object 中转**：

```java
IForgeRegistryEntry entry = (IForgeRegistryEntry) (Object) item;   // 编译过，运行期类已被补丁
entry.setRegistryName(new ResourceLocation("mcver", "download_tool"));
((IForgeRegistry) ForgeRegistries.ITEMS).register(entry);
```

这样就不需要去解 `binpatches.pack.lzma`、也不需要「先造一份打过补丁的 jar」。运行期一切正常。

### 21.4 最重要的前提已验证：mod jar 可以带 `net.minecraft.*` 的类

1.12.2 的 API 名字（`GuiScreen` / `FontRenderer` / `ITextComponent`）和共享页面代码用的 1.14 时代名字
差得**一整个包结构**，直接改名编译不动。唯一能保住「页面代码零改动」的办法是给 1.14 时代那批类名写
一层**垫片**（shim），也就是往自己的 mod jar 里塞 `net.minecraft.client.gui.screens.Screen`、
`net.minecraft.text.Text` 这类类。

风险是「Forge 的 LaunchClassLoader 允不允许」。写了 `modx/spike1122` 做最小验证：

```
mcver: spike shim=ok shimClass=net.minecraft.text.Text loader=net.minecraft.launchwrapper.LaunchClassLoader
mcver: spike registered=mcver:download_tool
```

**允许**。而且顺手证明了 SRG 编译产物能直接用（`func_77655_b` 之类真能调）。

- 构建脚本：`scripts/build-1122forge.mjs`（`--release 8`，classpath = srg client + srg forge + 原版库）
- 验证：`ProbeLaunch testgame 1.12.2-forge-14.23.5.2864 240 "mcver: spike shim"` → `GUI OK`
- 顺带发现：本机 Java 8 在 **`E:\Program Files\Java\jre-1.8`**（启动器也支持自动下 `jre-legacy`）

### 21.5 还没做（下一步就照这个顺序）

真 target 还差四块，全部是机械活：

1. **垫片层** `modx/forge1122/java/net/minecraft/**`：按生成源码里实际出现的 import 写
   （`Screen` / `TextRenderer` / `Text` / `ButtonWidget` / `ClickableWidget` / `TextFieldWidget` /
   `MinecraftClient` / `DrawContext` / `Identifier` / `Registries` / `RegistryKey` / `RegistryKeys` /
   `ItemGroups` / `Hand`）。页面代码只把这些当**类型**用，方法调用集中在 `Btn`/`Field`/`ChromeScreen`
   三个 mcver 包装类里，所以垫片可以很薄。
2. **1.12.2 overlay**：`ChromeScreen`（`func_73863_a` / `func_146278_c` / `buttonList`）、`Ctx`
   （`Gui.func_73734_a` / `FontRenderer.func_175063_a` / `RenderItem.func_175042_a`）、`Btn`
   （`GuiButton`）、`Field`（`GuiTextField`）、`Txt`、`Log`（log4j2）、`McVerMod`（`mcmod.info` + `@Mod`）、
   `McVerClient`（tick 事件 + 键位）。
3. **资源布局**：1.12.2 是 `models/item/x.json` + `textures/items/x.png`（后面才统一成 `textures/item`），
   外加 `mcmod.info`。
4. **新实例**：`V1.12.2-FORGE` 目前这个 json **根本没有 Forge 库**（`mainClass=net.minecraft.client.main.Main`），
   是坏的；真实验证要用 `1.12.2-forge-14.23.5.2864`（`inheritsFrom: 1.12.2`，登录器能正确合并）。

## 22. 2026-10-28（第六轮）：Forge 1.12.2 —— 垫片方案落地，真 target 过了像素验收

**结果**：Forge 1.12.2（14.23.5.2864，实例 `1.12.2-forge-14.23.5.2864`）
**PASS —— `C6C6C6 / 15076 / children=17 / 870x519`**。

§21 那四块全部做完，页面代码**零改动**（41 个共享源文件一行没动）。

### 22.1 做法：垫片 + 源码替换，不动页面

`scripts/build-1122forge.mjs` 的流水线：

```
mk-mojmap-src（yarn → mojmap）
  → 文本替换 net.minecraft.client.Minecraft → dev.mcver.bridge.Minecraft   ← 必须在盖 overlay 之前
  → 盖 shim1122/java（1.14 时代类名）+ forge1122/java（overlay）
  → javac --release 8（classpath = SRG 原版 + SRG Forge + 实例库）
  → 合资源（mcmod.info + textures/items 布局）→ jar
```

**顺序踩过一次**：替换放在盖 overlay 之后，会把 bridge 自己里面
`net.minecraft.client.Minecraft` 也换成 `dev.mcver.bridge.Minecraft`，于是
`dev.mcver.bridge.Minecraft.func_71410_x()` 找不到符号 —— 自我引用。

### 22.2 垫片清单（`modx/shim1122/java`）

| 1.14 时代名字（页面代码在用） | 1.12.2 真实名字 | 备注 |
| --- | --- | --- |
| `net.minecraft.network.chat.Component` | `net.minecraft.util.text.ITextComponent` | 包不存在 → 可安全放垫片 |
| `net.minecraft.client.gui.Font` | `FontRenderer` | width / plainSubstrByWidth 两个方法 |
| `net.minecraft.client.gui.screens.Screen` | `GuiScreen` | 补 `width`/`height`/`font`/`init`/`tick`/`children()` |
| `net.minecraft.client.gui.screens.TitleScreen` | — | 纯占位 |
| `net.minecraft.client.gui.components.AbstractWidget` | — | **接口**（1.12.2 没有 widget 基类） |
| `net.minecraft.client.gui.components.Button` | `GuiButton` | 覆盖 `func_146116_c` 做回调 |
| `net.minecraft.client.gui.components.EditBox` | `GuiTextField` | setText/getText/setMaxLength/setResponder |
| `net.minecraft.world.item.ItemStack` / `Items` | `net.minecraft.item.*` / `init.Items` | Paper 走 `Item.func_111206_d("minecraft:paper")` |
| `net.minecraft.Util` | `net.minecraft.util.Util`（不同包） | openUri 用 AWT Desktop 兜底 |
| **`net.minecraft.client.Minecraft`** | **同名存在 → 不能垫片** | 换 `dev.mcver.bridge.Minecraft` 门面 + 源码替换 |

**唯一撞名的就是 `Minecraft`**。同名垫片进 mod jar 会跟原版抢，而 LaunchClassLoader 的
URL 顺序不保证谁赢 —— 那种 bug 只在某些启动路径上露头，最难查。所以换包 + 文本替换：
`import` 里的全限定名整串换掉，简单名 `Minecraft` 不变，页面代码因此一行都不用改。

### 22.3 SRG 名字速查（这轮实测，别再猜）

- `GuisScreen.func_146280_a(Minecraft,int,int)` **不调** `func_183500_a`：它自己设
  `field_146294_l/m` 然后直接 `func_73866_w_()`。所以想同步自己的 `width/height`，必须
  override `func_146280_a` 并在 **super 之前**赋值，否则 `init()` 里读到 `gui=0x0`、
  `panel=-6x-6`、`children` 少一个（**踩过**）。
- 菜单添加控件：`func_189646_b(T)`（`addButton` 是 1.13+ 的名字）。
- `GuiButton` 点击钩子：`func_146116_c(Minecraft,int,int)`；播放音效 `func_146113_a(SoundHandler)`；
  Minecraft 的 SoundHandler 是 `func_147118_V()`。
- `Item`：`func_77655_b`（setUnlocalizedName）、**`func_77625_d`**（setMaxStackSize，不是 `func_77638_a`）、
  `func_77637_a`（setCreativeTab）、`func_111206_d`（getByNameOrId）。
- `FontRenderer`：`func_175063_a(String,float,float,int)` 带阴影、`func_78276_b` 不带、
  `func_78256_a`（宽）、`func_78262_a(String,int,boolean)`（截字，**三参**）。
- `RenderItem.func_175042_a(ItemStack,int,int)`（Minecraft 的 `func_175599_af()`）。
- `GuiScreen.func_146275_d` / `func_146277_j`（剪贴板 set/get，**静态**）。
- 会话：`Minecraft.func_110432_I()` → `net.minecraft.util.Session`；
  uuid `func_148255_b`、名字 `func_111285_a`、token `func_148254_d`。
- `net.minecraft.util.text.TextComponentString`（**不是** `TextComponent`）。

### 22.4 1.12.2 的 GUI 结构与后面几代的三处根本差异

1. **`func_73863_a` 不画背景**：1.13+ 才有「Screen 自己调 renderBackground」的约定。
   这里面板由 `ChromeScreen` 在 `super` 之前自己铺。
2. **没有滚轮回调**：得覆盖 `func_146274_d()`（handleMouseInput）读
   `Mouse.getEventDWheel()`，再换算成逻辑坐标（`* width / field_71443_c`，Y 轴要翻转）。
3. **输入框不是控件**：`GuiTextField` 不进 `field_146292_n`，`GuiScreen` 既不画它也不转键盘。
   `ChromeScreen` 自己存一份、自己画、自己转（连 `children()` 都得合并成一份视图，
   否则 debug 里的 `children` 比别人少一个，横向判据就断了）。
4. 另外：`creativeTab` 是 `field_78027_g`（TOOLS，按 SRG 字段顺序推的，纯外观），
   `mcmod.info` 取代 `mods.toml`，贴图在 `textures/items/`（复数）而不是 `textures/item/`。

### 22.5 现在累计

- Fabric：16（1.7.10 ~ 1.21.11）
- Forge：**12**（**1.12.2** / 1.14.4 / 1.16.5 / 1.17.1 / 1.18.2 / 1.19.4 / 1.20.1 / 1.20.2 / 1.20.4 / 1.20.6 / 1.21.1 / 1.21.4）
- NeoForge：6（20.1 / 20.2 / 20.4 / 20.6 / 21.1 / 21.4）

### 22.6 还没做

- **Forge 1.7.10**：本地有实例（`1.7.10-Forge10.13.4.1614-1.7.10`），但 FML 包名是
  `cpw.mods.fml`（1.8+ 才是 `net.minecraftforge.fml`），注册表是 `GameRegistry.registerItem`，
  渲染是 `GuiScreen.func_73863_a` + `FontRenderer` 但类在 `net.minecraft.client.gui`。
  再垫一层就能通，但收益低（1.7.10 的启动器需求也少），先记在这儿。
- **Forge 1.15.2**：本地没实例，要先装。
- **MC 26.1 / Forge 62**：class 69 / Java 25、`ResourceLocation`→`Identifier`、loader 拆模块。

## 23. 2026-10-28（第七轮）：Forge 1.7.10 —— 同一套垫片方案再往下一层

**结果**：Forge 1.7.10（10.13.4.1614，实例 `1.7.10-Forge10.13.4.1614-1.7.10`）
**PASS —— `C6C6C6 / 14945 / children=17 / 870x519`**。
页面代码**依旧零改动**（41 个共享源文件一行没动）。

### 23.1 映射：1.7.10 的 srg 是 v0 文本格式，不是 tsrg

新建 `scripts/mk-srg-1710.mjs`。映射来源跟 1.12.2 完全不同：

| | 1.12.2 | 1.7.10 |
| --- | --- | --- |
| 来源 | `mcp_config` zip 的 `config/joined.tsrg` | `de/oceanlabs/mcp/mcp/1.7.10/mcp-1.7.10-srg.zip` 的 `joined.srg` |
| 格式 | tsrg v1：两列，成员行**缩进** | srg v0：`PK:` / `CL:` / `FD:` / `MD:` 前缀行 |
| 成员 owner | 靠缩进 | 写在名字里（`bao/a`） |
| 方法描述符 | 第三列 | `MD:` 行**两个**描述符（obf 和 srg）都给 |

解析结果：`1836 classes, 6900 fields, 13263 methods (skipped 0)`；字段描述符照样得从 jar 里读
（`joined.srg` 不给），102 个字段在 jar 里找不到描述符（继承/合成）→ 跳过并 WARN。
tiny v2 → remap 出 `client-1.7.10-srg.jar`。

### 23.2 Forge universal jar 也是 notch 名

跟 1.12.2 同样的坑，但更容易误判：**1.7.10 的 `forge-1.7.10-10.13.4.1614-1.7.10.jar` 里，
vanilla 引用是混淆名**。证据：`GameRegistry.registerItem(adb, String)`（`adb` = `Item`）。
这是 FML 的 deobf transformer 在运行期把 notch 翻成 srg 留下的痕迹。

所以 `mk-srg-1710.mjs` 第 5 步也给它做一次 remap →
`forge-10.13.4.1614-srg.jar`，签名就对了：`registerItem(net.minecraft.item.Item, String)`。

### 23.3 垫片 `modx/shim1710`（与 shim1122 只差四处）

1. **`Screen` 不能 override `func_183500_a`** —— 那是 1.9 才有的方法。同步
   `width/height/font` 只能靠 `func_146280_a`（它自己设 `field_146294_l/m` 再调 `func_73866_w_()`）。
2. `Component` → `net.minecraft.util.IChatComponent` + **`ChatComponentText`**（1.12.2 叫
   `TextComponentString`），`getString()` = `func_150260_c()`。
3. `ItemStack#isEmpty` → 1.7.10 **没有** `func_190926_b`（Capability 是 1.11+），「空」就是 `null`。
4. `Items.PAPER` → 1.7.10 没有 `Item.getByNameOrId`（同 id `func_111206_d` 在 1.7.10 是
   **setTextureName**），只能查老注册表 `Item.field_150901_e.func_82594_a("minecraft:paper")`。
5. `Button#setWidth` → 1.7.10 没有 `func_175211_a`（1.8 才有），直接写 protected 字段 `field_146120_f`。
6. `EditBox` → 构造器 `(FontRenderer,x,y,w,h)`（**连 id 都没有**），而且 1.7.10
   **没有 `GuiResponder`**（1.8 才有）→ 值变化回调只能每帧 `poll()`。

### 23.4 overlay `modx/forge1710`

- **FML 在 `cpw.mods.fml.*`**（1.8 起才搬去 `net.minecraftforge.fml`）。
- **两个事件总线**：`TickEvent` 走 `FMLCommonHandler.instance().bus()`，**不走**
  `MinecraftForge.EVENT_BUS`。只听后者 → tick 永远收不到，表现是日志里既没有
  「客户端初始化完成」也没有 `cur=`（**这轮唯一的真 bug，卡了一轮**）。
- 注册：`GameRegistry.registerItem(item, "download_tool")`，没有 registry 对象。
- 物品贴图：`setTextureName` 是 **protected** → 得开个 `Item` 子类在构造器里调；
  路径是 `textures/items/`（复数），`assets/mcver/models/item/*.json` **完全不需要**（1.7.10 没有模型系统）。
- `@Mod` **没有 `clientSideOnly` 成员**（1.12.2 那份有），写了直接编译失败。
- 物品渲染：1.7.10 没有 `Minecraft#getItemRenderer()`，要自己 `new RenderItem()`，
  设 `field_77023_b`（zLevel），并且必须自己 `RenderHelper.func_74520_c()` /
  `func_74518_a()` 开关 GUI 光照 —— 否则图标是黑的。之后还得复位
  `glColor4f(1,1,1,1)` + `glDisable(GL_LIGHTING)` + `glEnable(GL_BLEND)`。
- 加控件：1.7.10 **没有** `func_189646_b`（1.8 才有），直接 `field_146292_n.add(...)`。
- 顺手补了一个 1.12.2 也缺的东西：`func_73864_a` 里把鼠标点击转发给 `GuiTextField`
  （`GuiScreen` 不管这些框，1.12.2 那份没转发 → 输入框点不进去）。
- 文本坐标：1.7.10 的 `func_78276_b` / `func_78261_a` 收 **int**（1.12.2 的 `func_175063_a` 收 float）。

### 23.5 累计

- Fabric：16（1.7.10 ~ 1.21.11）
- Forge：**13**（**1.7.10** / 1.12.2 / 1.14.4 / 1.16.5 / 1.17.1 / 1.18.2 / 1.19.4 / 1.20.1 / 1.20.2 / 1.20.4 / 1.20.6 / 1.21.1 / 1.21.4）
- NeoForge：6（20.1 / 20.2 / 20.4 / 20.6 / 21.1 / 21.4）

三个加载器、四代渲染 API、两套 remap 管线，全部像素验收 PASS。

### 23.6 还没做

- **Forge 1.15.2**：本地没实例，要先装（跟 1.16.5 的差别主要是 `EditBox` 占位符之类小项）。
- **MC 26.1 / Forge 62**：class 69 / Java 25、`ResourceLocation`→`Identifier`、loader 拆模块。
- **1.6.4 及以下**：启动器那边连 Forge 安装都还没做，先不谈。

## 24. 2026-10-28（第八轮）：Forge 1.15.2 + 1.10.2

两个都 **PASS**，都是一次编译通过（1.15.2 只修了两处 API 改名）：

| target | 实例 | 结果 |
| --- | --- | --- |
| Forge **1.15.2** | `1.15.2-forge-31.2.62` | `C6C6C6 / 15080 / children=17 / 870x519` |
| Forge **1.10.2** | `1.10.2-forge1.10.2-12.18.3.2511` | `C6C6C6 / 15089 / children=17 / 870x519` |

### 24.1 1.15.2：跟 1.14.4 同代，只有两处改名

先跑 `ProbeInstall`（走**生产代码** InstallJob，不是手搓）装的实例：
`node … ProbeInstall testgame 1.15.2 FORGE - NONE - - 1` → `1.15.2-forge-31.2.62`。

overlay 链 = `shims21/java` + `forge14/java` + **`forge15/java`**，后者只干一件事：

- `Minecraft#window` 在 1.15.2 是 **private**（1.14.4 是 public）→ 按键检测改用
  `client.getWindow().getWindow()`。整份 `McVerClient` 从 forge14 复制过来改这一行。

`srcReplace` 两条（都是纯改名，不值得开 overlay）：
- `Font.plainSubstrByWidth` → **`substrByWidth`**（1.16 才改的名）；
- `EditBox` 占位符仍收 String（1.16 起收 ITextComponent）。

顺便记一笔：1.15.2 的 mojmap 包里，GUI 类**已经是现代包名**了
（`net.minecraft.client.gui.screens.Screen` / `components/Button` / `components/EditBox`），
所以 `shims21` 那层照用；而 1.14.4 的 `Field.of` 是通过 srcReplace 传 String 的，同样照用。

### 24.2 1.10.2：跟 1.12.2 几乎一模一样

映射还是 srg v0（`mcp-1.10.2-srg.zip` 的 `joined.srg`，`2980 classes / 10813 fields / 18571 methods`）。
脚本为此通用化了：**`scripts/mk-srg-srgv0.mjs --mc <mc> --instance <实例> --forge <ver>`**
（`mk-srg-1710.mjs` 就是它的 1.7.10 实例；forge jar 文件名三代三种，脚本自己试）。

实测 1.10.2 与 1.12.2 的 GUI/物品 API **逐条相同**（`func_189646_b`、`func_183500_a`、
`func_175211_a`、`GuiResponder`、`func_175042_a`、`func_175063_a` 全在）—— 只有两处不同：

1. `IForgeRegistry` / `IForgeRegistryEntry` 还在 `net.minecraftforge.fml.common.registry`
   （1.12 才挪到 `net.minecraftforge.registries`）→ `forge102/core/Compat.java` + `McVerMod` 改 import；
2. `ItemStack#isEmpty`：1.10.2 没有 `func_190926_b`（1.11+）→ `shim1102/world/item/ItemStack.java` 用 null 判。

**额外一条**：1.10.2 的物品模型要**显式**登记（1.11 起才按注册名自动找 `models/item/<name>.json`），
所以在 `preInit` 里加了：

```java
ModelLoader.setCustomModelResourceLocation(DOWNLOAD_TOOL, 0,
    new ModelResourceLocation(MOD_ID + ":download_tool", "inventory"));
```

（包在 try/catch 里，纯客户端调用 —— 我们本来就是 `clientSideOnly = true`。）

### 24.3 累计

- Fabric：16（1.7.10 ~ 1.21.11）
- Forge：**15**（1.7.10 / **1.10.2** / 1.12.2 / 1.14.4 / **1.15.2** / 1.16.5 / 1.17.1 / 1.18.2 / 1.19.4 / 1.20.1 / 1.20.2 / 1.20.4 / 1.20.6 / 1.21.1 / 1.21.4）
- NeoForge：6（20.1 / 20.2 / 20.4 / 20.6 / 21.1 / 21.4）

### 24.4 还差

Forge 侧只剩 **1.8.9 / 1.9.4 / 1.11.2 / 1.13.2**（本地都没实例，得先 ProbeInstall）
和 **26.1**（class 69/Java 25 + `ResourceLocation`→`Identifier` + loader 拆模块）。

## 25. 2026-10-28（第九轮）：1.11.2 / 1.9.4 / 1.8.9 —— 1.8~1.12 这一代铺满

| target | 实例 | 结果 |
| --- | --- | --- |
| Forge **1.11.2** | `1.11.2-forge1.11.2-13.20.1.2588` | `C6C6C6 / 15092 / 17 / 870x519` |
| Forge **1.9.4** | `1.9.4-forge1.9.4-12.17.0.2317-1.9.4` | `C6C6C6 / 15100 / 17 / 870x519` |
| Forge **1.8.9** | `1.8.9-forge1.8.9-11.15.1.2318-1.8.9` | `C6C6C6 / 15145 / 17 / 870x519` |

三个实例都是 `ProbeInstall`（生产代码 InstallJob）装的。

### 25.1 新工具：`scripts/build-srg-legacy.mjs`

原来 `build-1122forge.mjs` / `build-102forge.mjs` 各自是复制粘贴的产物，三代之后就受不了了。
现在一个脚本吃参数：

```bash
node scripts/build-srg-legacy.mjs --mc 1.8.9 --forge 11.15.1.2318 \
     --instance 1.8.9-forge1.8.9-11.15.1.2318-1.8.9 \
     --shim shim189 --overlay forge102,forge189
```

overlay 是**有序列表**，每个目录可以带自己的 `resources/`（mcmod.info 的 `mcversion` 就靠它逐代覆盖）。

### 25.2 三代的差别（都是「上一层少一点东西」）

**1.11.2** —— 跟 1.12.2 只差注册表包名：
- 有 `func_190926_b`（isEmpty，1.11 才有）→ 直接用 `shim1122`；
- `IForgeRegistry*` 仍在 `net.minecraftforge.fml.common.registry`（1.12 才挪）；
- 模型**不用**显式登记了（1.11 起按注册名自动找 `models/item/<name>.json`）。

**1.9.4** —— 少两样：
- `GuiScreen` **没有** `func_189646_b`（1.10 才加）→ `field_146292_n.add((GuiButton) w)`
  （注意这个 List 是**有泛型**的，直接 `add(w)` 编不过）；
- `ItemStack` 没有 `func_190926_b` → `shim1102` 的 null 判；
- 模型要显式登记。

**1.8.9** —— 再少两样：
- **没有 `ForgeRegistries` / `IForgeRegistry`**（1.9 才有）→ 老接口
  `GameRegistry.registerItem(item, name)`；
- 模型类在 `net.minecraft.client.resources.model.ModelResourceLocation`
  （1.9 才挪到 `client.renderer.block.model`）；
- 文本是 **`net.minecraft.util.IChatComponent` + `ChatComponentText`**
  （`net.minecraft.util.text` 是 1.9 才有的包）→ 用 `shim1710` 的 `Component`。

所以 `shim189` = `shim1102`（null isEmpty）+ `shim1710` 的 `Component`；
`forge189` 的 `McVerMod`/`Compat` 单独写了一份（注册表和模型路径都不一样）。

### 25.3 累计（Forge 18 个）

- **Forge（18）**：1.7.10 / **1.8.9** / **1.9.4** / 1.10.2 / **1.11.2** / 1.12.2 / 1.14.4 / 1.15.2 /
  1.16.5 / 1.17.1 / 1.18.2 / 1.19.4 / 1.20.1 / 1.20.2 / 1.20.4 / 1.20.6 / 1.21.1 / 1.21.4
- Fabric：16（1.7.10 ~ 1.21.11）
- NeoForge：6（20.1 / 20.2 / 20.4 / 20.6 / 21.1 / 21.4）

### 25.4 只剩两个

- **Forge 1.13.2**：这一代**没有 Mojang 映射**（`client_mappings` 是 1.14.4 才开始发的），
  MCP 1.13 的名字是一套自己的「快照名」，得走 `mk-mcp-srg`（tsrg）那条路 + 全新的语义层。
- **MC 26.1 / Forge 62**：class 69 / Java 25、`ResourceLocation`→`Identifier`、loader 拆模块。

## 26. 2026-10-28（第九轮尾）：回归抽查

加完 1.8.9 / 1.9.4 / 1.10.2 / 1.11.2 / 1.15.2 之后，抽查了老的两头：

| target | 结果 |
| --- | --- |
| Forge **1.14.4**（老版本） | `C6C6C6 / 15079 / 17` ✔ |
| Forge **1.21.4**（新版本，重编后） | `C6C6C6 / 19481 / 17` ✔ |

**顺手记一个环境坑**：`testgame/versions/<实例>/mods` 是空的就等于「没装 mod」。
这轮 1.14.4 第一次跑挂了，日志里一行 `mcver:` 都没有 —— 不是代码问题，是那个实例的
mods 目录被清过。修法就是重跑一次 `put-mod.mjs`。
`verify-gui-one.mjs` 只负责「起游戏 + 抓图」，**不负责装 jar**，所以验收前先确认
`versions/<id>/mods/` 里有对应 jar（`V*` 那批老实例都有，`1.x-forge-*` 那批新实例可能没有）。

## 27. 2026-10-28（第十轮）：下载页四个分类页签 + 全量重建

### 27.1 需求

「下载界面应该有快照、正式、远古、愚人节 4 个分类，就像模组加载器那样，选一个再列出对应版本」。
原来只有一个「快照」开关，远古和愚人节根本没法单独看。

### 27.2 分类怎么判

清单里 Mojang 只给 4 种 `type`：`release` / `snapshot` / `old_beta` / `old_alpha`，
**愚人节版本标记的也是 `snapshot`** —— 所以只能按 id 认。

`VersionList.catOf()` 的顺序很关键：**先判愚人节，再判 old_beta/alpha，最后才 snapshot**，
否则 9 个愚人节版本会全跑进「快照」页签。

愚人节名单（`VersionList.FOOLS`，9 个）：

| id | 发布 | 备注 |
| --- | --- | --- |
| 15w14a | 2015-04-01 | Love and Hugs |
| 1.RV-Pre1 | 2016-03-31 | Trendy 更新（提前一天发） |
| 3D Shareware v1.34 | 2019-04-01 | |
| 20w14infinite | 2020-04-01 | 无限维度 |
| 22w13oneblockatatime | 2022-04-01 | |
| 23w13a_or_b | 2023-04-01 | 投票更新 |
| 24w14potato | 2024-04-01 | 毒马铃薯 |
| 25w14craftmine | 2025-04-01 | |
| 26w14a | 2026-04-01 | 26.x 新命名下唯一一个旧式 id，且 26.2 首张快照 4/7 才出 |

**判定为什么不能只看日期**：`22w13a`（2022-03-31）、`21w13a`、`17w13b` 也是 3 月底发的，
但它们是当年的正常快照（前后有 22w12a → 22w14a 这种邻居），所以不在表里。

实测分布（921 个版本）：**正式 103 / 快照 748 / 远古 61 / 愚人节 9**，四类相加 = 921，
没有漏项（`catOf` 兜底返回 RELEASE，不会丢版本）。

### 27.3 界面

- 搜索框提到第一行（整宽，`contentY+1`），四个页签一行放 `contentY+20`，列表从 `contentY+39` 起；
  页签按 `(w-9)/4` 等分（`contentW=298` → 每个 72px），当前页签前缀 `▶`，标签后面带该分类的版本数。
- 列表行第二列直接写分类名（`VersionList.CAT_LABELS[catOf(v)]`），不再自己 switch type。
- 搜索**只在当前页签里找** —— 页签是显式选择，跨类搜反而意外。空态文案分两种：
  「这个分类里没有版本」/「「快照」里没有匹配的版本」。
- 自检钩子 `MCVER_CAT=愚人节`（跟 `MCVER_TAB` 一样是验收用的），
  另外 `recompute()` 会打一行 `mcver: 分类=X 搜索=Y 共=N 个（总 M）`。

### 27.4 新工具：`scripts/rebuild-all.mjs`

改了共享页面代码（`mod/src/main/java`）就得把 `dist` 里全部 jar 刷一遍，以前是手工一条条敲。
现在：

```bash
node scripts/rebuild-all.mjs            # 31 步全跑（6 legacy forge + 17 forge/neoforge + 7 legacy fabric + 1 gradle + publish）
node scripts/rebuild-all.mjs forge      # 子串过滤
node scripts/rebuild-all.mjs publish    # 只把 modx/out/<target>/*.jar 抄进 dist
```

- 每步单独记日志 `logs/build/rebuild-<name>.log`，一步挂了不影响后面的，最后打总表。
- **`publish` 是必须的**：`build-neoforge.mjs` 只写 `modx/out/<target>/`，从不碰 `dist`；
  Fabric 那几套脚本（`build-1132` / `build-1122` / `build-all.ps1`）才直接写 `dist`。
- **gradle 那步必须带 `-NoDaemon`**：daemon 会继承 stdout，`execFileSync` 等 EOF 会一直挂到超时
  （实测 9 个 target 全构建完、dist 也拷好了，步骤就是不返回）。

### 27.5 顺手修好的两个构建配置

全量重建一下子把两处**陈旧的 target 配置**暴露出来了：

1. **`forge-1.11.2`**：之前记录的 overlay 链少了基础层 —— 正确是
   `--shim shim1122 --overlay forge102,forge111`。`forge111` 里只有 `McVerMod` + `mcmod.info`，
   UI/Compat 来自 `forge102`（1.11.2 的 GUI API 跟 1.10.2 逐条相同）。
   只给 `forge111` 会直接拿 Fabric 主源码去编 → 一堆 `net.fabricmc.api` 找不到。
2. **`neoforge-20.6`**：`build-neoforge.mjs` 里 overlay 指向 `neoforge20/java`，
   那是 `TickEvent` 版（20.2/20.4 用）。**NeoForge 20.5 起 `TickEvent` 被
   `ClientTickEvent.Post` 取代**，20.6 必须用 `neoforge21/java`。
   证据是已经发布的 20.6 jar 反编译出来就是 `onTick(ClientTickEvent$Post)` —— 配置后来被改坏了。

### 27.6 验收

| 实例 | 页面 | 结果 |
| --- | --- | --- |
| `1.21.4-forge-54.1.22` | 下载页（默认正式） | `children=23`，面板像素 19584 |
| `neoforge-20.6.144` | 下载页 · 愚人节 | `children=23`，日志 `共=9`，像素命中 19583 / 中心 `C6C6C6` |
| `1.11.2-forge1.11.2-13.20.1.2588` | 下载页（默认正式） | `children=23`，日志 `共=103` |
| `1.7.10-Forge10.13.4.1614-1.7.10` | 下载页（默认正式） | `children=23`，日志 `共=103` |
| `1.21.4-forge-54.1.22` | **启动页**（老判据） | `C6C6C6 / 19481 / children=17` ✔ |
| `1.7.10-Forge10.13.4.1614-1.7.10` | **启动页**（老判据） | `C6C6C6 / 14945 / children=17` ✔ |

下载页**不用像素判据**：面板中心落在列表按钮上（灰/蓝），中心点不会是 `C6C6C6`。
判据换成 `children=23`（搜索框 + 4 页签 + 9 行 + 2 翻页 + 刷新 + 6 导航）+ 日志里的分类计数。
启动页的老判据仍然全绿，说明这轮改动没碰坏别的页面。

新工具 `scripts/verify-download-page.mjs`：把 `MCVER_TAB=下载` / `MCVER_CAT=<中文>` 用
UTF-16 干净地传给游戏（PowerShell 里直接 `$env:MCVER_CAT='愚人节'` 会被转成 GBK 字节，
游戏里对不上，页签永远停在「正式」）。

### 27.7 产物

`dist/` 现在 **39 个 jar**，全部在 2026-10-28 23:31–23:38 之间重建：
Fabric 16 + Forge 18 + NeoForge 5（NeoForge 20.1 复用 forge-1.20.1 那个 jar）。

## 28. 2026-10-29（第十一轮）：「远古」分类实测 —— 揪出 Launcher 的去重顺序 bug

用户把决定权交给我。决定跑，但目标改成「安装 + 启动」，不是 mod 界面
（b1.x/a1.x 既没有 Fabric 也没有 Forge，mod 本体根本装不上）。

### 28.1 安装：61 个远古版本全走同一条路，实测两个

```bash
java -cp tools\probe\out dev.mcver.probe.ProbeInstall testgame b1.7.3 NONE - NONE - - 1
java -cp tools\probe\out dev.mcver.probe.ProbeInstall testgame a1.2.6 NONE - NONE - - 1
```

两个都 **`state=DONE / complete=true`**，749 个资源文件全部落到
`assets/virtual/pre-1.6`（`assets` 字段是 `pre-1.6`，走 virtual 索引）。
`requiredJava=8`、`missingAssets=0`、`missing files=0` —— 安装路径没问题。

### 28.2 启动：NoClassDefFoundError: org/lwjgl/LWJGLException

第一次跑 b1.7.3 直接挂在：

```
Exception in thread "main" java.lang.NoClassDefFoundError: org/lwjgl/LWJGLException
```

`mcver-args-*.txt` 里 classpath 只有 **6** 项：launchwrapper / jopt-simple / asm-all /
jinput / jutils / 客户端 jar —— **三个 lwjgl 全不在**。natives 目录倒是有 dll。

**根因（在 `Launcher.plan()`）**：库去重是「同一 group:artifact 留版本最高」，
**然后**才按 `rules` 过滤。而 b1.7.3 的 json 里，

```json
{ "name": "org.lwjgl.lwjgl:lwjgl:2.9.1-nightly-20130708-debug3",
  "rules": [ { "action": "allow", "os": { "name": "osx", "version": "^10\\.5\\.\\d$" } } ] }
```

高版本那三个 nightly **只在老 OSX 上允许**。先按版本去重 → 选中 nightly → 再被 rules 刷掉
→ Windows 上 lwjgl / lwjgl_util / lwjgl-platform 三个**一起消失**。
而且 `missing files` 还是 0（被 rules 刷掉的条目压根没进 missing 统计），
所以探针也报「一切正常」—— 只有真起游戏才炸。

**修法**：`rules` 过滤提到去重之前（`byArtifact` 构建循环里先 `continue`）。
真实启动器（官方 / PCL / HMCL）也是这个顺序：先筛平台，再挑版本。

修完 b1.7.3 的 classpath 从 6 项变回 9 项（2.9.0 那套），
日志走到 `Setting gameDir to: ...` + OpenAL 初始化完成。

### 28.3 剩下两个不致命的报错（都不是我们的锅）

1. `javax.imageio.IIOException: Can't read input file!`（`VanillaTweakInjector.loadIcon`）
   —— 它去 `assets/virtual/pre-1.6/icons/icon_16x16.png` 找窗口图标，那批资源里没有 icons。
   被 catch 掉，只影响窗口图标。
2. `java.io.FileNotFoundException: http://s3.amazonaws.com/MinecraftResources/`
   —— b1.7.x 的音效是自己去 S3 拉的，那个桶 Mojang 2020 年就关了。全世界都一样。

a1.2.6 同样能起，实测窗口标题 `Minecraft`（`Get-Process java | MainWindowTitle`）。

### 28.4 回归：这次改的是 core 共享代码，全量重建 + 四个目标重验

`Launcher.java` 是**所有**版本共用的计划器，改完必须全量重建：

```bash
node scripts/rebuild-all.mjs     # 31/31 OK，publish 23 个 jar -> dist
```

| 目标 | 结果 |
| --- | --- |
| Forge 1.21.4 | `C6C6C6 / 19481 / 17` ✔ |
| Fabric 1.21.4 | `C6C6C6 / 15092 / 17` ✔ |
| Forge 1.7.10 | `C6C6C6 / 14945 / 17` ✔ |
| NeoForge 20.6 | `C6C6C6 / 19478 / 17` ✔ |

`dist/` 仍是 **39 个 jar**，全部 2026-10-29 00:06–00:12 重建。

### 28.5 教训

- **「按版本去重」和「按平台过滤」的顺序不能反**。反了以后被过滤掉的那份会顶掉合法的老版本，
  而且**缺失统计看不见**（rules 刷掉的条目不计入 missing）→ 静止检查全绿、只有真跑才炸。
- 远古版本的 json 里，`rules` 不只是「windows/linux/macos 三选一」，
  还有「只在 osx 10.5.x 上用」这种**版本区间**规则。别按「allow 就是通吃」去想当然。
- 探针报 `missing files=0` 不代表 classpath 是对的；`classpath entries=N` 这个数字也要一起看
  （b1.7.3 正常是 9，出 bug 时是 6）。

## 29. 2026-10-29（第十二轮）：右键打不开界面 —— 判据覆盖错了路径

用户实测：1.21.4 上拿着「版本下载器」右键**没反应**。而 39 个 jar 的像素验收全是绿的。

### 29.1 根因：`wasPressed()` / `consumeClick()` 被原版先吃掉了

`McVerClient` 每 tick 轮询：

```java
boolean rightClick = client.options.useKey.wasPressed() && holdingTool(client);   // Fabric
boolean rightClick = client.options.keyUse.consumeClick() && holdingTool(client); // Forge / NeoForge
```

**这行永远返回 false。** 原版 `Minecraft.handleInputEvents()` 每 tick 都会跑：

```java
while (this.options.keyUse.wasPressed()) this.doItemUse();
```

`wasPressed()` 的字节码是 `if (timesPressed == 0) return false; timesPressed--; return true;`
（javap 1.21.4 已核对）。它把按下计数**全部消费干净**，而我们的 tick 回调
（Fabric `END_CLIENT_TICK` / Forge `TickEvent.Phase.END` / NeoForge `ClientTickEvent.Post`）
**都在它之后**执行，此时 `timesPressed` 已经是 0。

`consumeClick()` 同理 —— 它读的也是 `timesPressed`。

### 29.2 为什么 39 个 jar 的验收没抓到

验收走的是 `MCVER_OPEN_GUI=1` 自检钩子：进 `TitleScreen` 就自动 `openMain()`。
**这条路根本不经过右键。** 所以 `C6C6C6 / children=17` 只证明了「界面能画」，
没有一条证明了「物品能用」。

> **教训（写进规矩）：验收判据必须覆盖用户真正走的那条路。**
> 自检后门只能证明「渲染没坏」，不能当功能证据。

### 29.3 修法：对「按住」状态做边沿检测

`KeyBinding.isPressed()`（Yarn）/ `isDown()`（mojmap）读的是 `pressed` 字段，
原版**只 set 从不 clear**，所以 tick 末尾仍然可见：

```java
private static boolean usePrev;          // 新增字段

if (client.player == null) { mDown = false; usePrev = false; return; }
if (client.currentScreen != null) {       // mojmap: client.screen
    mDown = isMDown(client);
    usePrev = client.options.useKey.isPressed();    // mojmap: isDown()
    return;
}

boolean clicked  = client.options.useKey.wasPressed();   // 保留作保险
boolean useDown  = client.options.useKey.isPressed();
boolean edge     = useDown && !usePrev;
usePrev = useDown;
boolean rightClick = (clicked || edge) && holdingTool(client);
```

`clicked` 留着是因为**不同加载器的 tick 事件相对 `handleInputEvents` 的先后并不一致**：
在其前执行的，`wasPressed()` 命中；在其后执行的，`edge` 命中。两种顺序都不漏。

### 29.4 命名对照（这次踩了）

| 语义 | Yarn | mojmap |
| --- | --- | --- |
| 右键绑定 | `options.useKey` | `options.keyUse` |
| 按住中 | `isPressed()` | **`isDown()`** |
| 当前屏 | `client.currentScreen` | `client.screen` |
| 窗口句柄 | `getWindow().getHandle()` | `getWindow().getWindow()` |

第一次写成 `isPressed()`，Forge / NeoForge 集体报 `找不到符号: 方法 isPressed()`，才改的 `isDown()`。

### 29.5 波及面：10 个文件（不是只有 1.21.4）

- `mod/src/main/java/dev/mcver/McVerClient.java`（Fabric 1.21+ 主源码）
- `modx/{forge14,forge15,forge16,forge17,forge18,forge19,forge21}/java/dev/mcver/McVerClient.java`
- `modx/{neoforge20,neoforge21}/java/dev/mcver/McVerClient.java`

**没受影响的**（走的是正规回调，不依赖按键状态）：
Fabric ≤1.20 用 `UseItemCallback`；Forge 1.7.10 / 1.8.9 / 1.9.4 / 1.10.2 / 1.11.2 / 1.12.2
用物品自己的 `onItemRightClick`（`McVerItem#method_13649` / `method_11373` / `onStartUse`）。

### 29.6 新验收工具：`ProbeRightClick`

之前没有任何工具走过右键，所以做了这个：

```bash
java -cp tools\probe\out dev.mcver.probe.ProbeRightClick testgame <实例> <秒> <存档名>
# 或
node scripts/verify-rightclick.mjs <实例> [--world 新的世界] [--timeout 190]
```

流程（全自动，无需人碰键鼠）：

1. `Launcher.plan()` → `launch(..., extraGameArgs)` 追加 `--quickPlaySingleplayer <存档>` 直接进世界；
   为此给 `Launcher.launch` 加了第 6 个参数重载（正常启动传 `null`，签名不兼容改动已避开）。
2. 等日志 `Setting user` → 再等 25s 让区块加载。
3. 一条 PowerShell 里发完整输入序列（不是每键一个进程，那样时序不可控）：
   `WM_KEYDOWN/UP('T')` → 逐字符 `WM_CHAR` 敲 `/give @s mcver:download_tool` → `WM_KEYDOWN/UP(Enter)`
   → 等 2s（物品进背包）→ `WM_RBUTTONDOWN` + `WM_RBUTTONUP`。
   GLFW 的 win32 后端在 `WndProc` 里处理这些消息，`PostMessage` 能真送到回调，**不需要窗口在前台**。
4. **只认日志 `mcver: 右键打开启动器`**（这是 `McVerClient` 右键分支独占的字符串）。
5. `PrintWindow` 抓图 + 中心像素 / 面板色统计。

### 29.7 三个探针的两个坑（都修了）

1. **日志编码**：游戏用平台默认编码写日志（中文 Windows 上是 **GBK**）。用
   `Files.readAllLines(UTF_8)` 不抛异常，只是把中文换成 `U+FFFD` → 按中文标记 grep 永远 false。
   第一次就是这样：界面明明开了，探针报 FAIL。现在先 UTF-8 严格解码（`CodingErrorAction.REPORT`），
   失败再退 GBK。
2. **抓图**：`Add-Type -MemberDefinition` 里带 `struct` 定义编译不过
   （`找不到类型或命名空间 user32`）—— `scripts/verify-gui.ps1` 用的是 `Add-Type -TypeDefinition`
   整类定义。另外路径别手写反斜杠转义（写成 `\\\\` 会变成双反斜杠，`Save()` 报找不到路径，
   被 catch 吃掉还照样打印 `png=...`，**骗人**）。现在落一个临时 `.ps1` 用 `-File` 跑，
   并且**先断言文件真的存在且 >1KB** 才打印路径。

### 29.8 实测结果（全部真右键，不是自检钩子）

| 目标 | 日志证据 | 抓图 |
| --- | --- | --- |
| Forge 1.21.4 | `mcver: 右键打开启动器` → `screen=MAIN` | 中心 `C6C6C6`，面板色命中 75218 |
| Fabric 1.21.4 | 同上 | 中心 `717171`（落在列表按钮上），命中 27204 |
| NeoForge 20.6.144 | 同上 | 抓图 OK |

全量重建 31/31 成功，`publish` 23 个 jar → `dist/`（仍是 39 个 jar）。

### 29.9 规矩补充

- **新增/修改任何「输入触发」功能，必须用 `ProbeRightClick` 那类探针走真实输入验证**，
  不许用 `MCVER_OPEN_GUI` 之类自检钩子代替。
- 日志 grep 一律按 GBK 回退解码，别假设 UTF-8。
- 截图成功与否要断言文件存在，不能只信打印的路径。


## 30. 2026-10-29（第十三轮）：右键复测时揪出 `@argfile` 编码 bug

上一轮改完右键判定后，`1.21.4-forge` 还要用**重编后的新 jar** 再跑一次真右键（§29 结尾留的尾巴）。
结果第一次跑直接 **FAIL**：游戏起来了、到了 `Setting user`，但**世界一直没加载**，停在标题界面。

### 30.1 根因：`@argfile` 写成了 UTF-8，JLI 却按平台编码读

`Launcher.launch` 在 Java 9+ 上走 `java @argfile`（绕 Windows 32k 命令行上限），参数文件原来是：

```java
Jdk.writeString(argFile, sb.toString(), StandardCharsets.UTF_8);
```

但 **JLI 解析 `@argfile` 用的是平台本地编码**（`sun.jnu.encoding`，中文 Windows = GBK），
不是 `file.encoding`。于是任何非 ASCII 参数都被按 GBK 解码：

```
新的世界  →  鏂扮殑涓栫晫
```

证据：`ModLauncher running: args [... --quickPlaySingleplayer, 鏂扮殑涓栫晫]`。
游戏本身完全正常，只是存档名对不上 → quickplay 静默失败 → 停在标题界面。

**这条是真正的用户级 bug**，只是被测试路径撞上了：中文存档名、中文用户名（`C:\Users\张三`）、
中文游戏目录，全都会被搅成乱码。而且是**静默失败**，最容易漏的那种。

### 30.2 修法

```java
private static Charset argFileCharset(String body) {
    String n = System.getProperty("sun.jnu.encoding");   // 就是 JLI 读文件用的编码
    Charset cs = (n != null && !n.trim().isEmpty()) ? Charset.forName(n.trim()) : Charset.defaultCharset();
    // 平台编码装不下的字符（生僻字/emoji）宁可退回 UTF-8，也别写成 '?' 静默丢信息
    return cs.newEncoder().canEncode(body) ? cs : StandardCharsets.UTF_8;
}
```

**Java 8 路线不受影响**（`javaMajor <= 8` 走 `ProcessBuilder(List)`，参数是 UTF-16 直达进程）。

### 30.3 怎么定位的（值得记的手法）

1. 对比两轮启动的 `mcver-args-*.txt` —— **完全一样**，说明不是参数拼错；
2. 看游戏日志里 ModLauncher 打印的 args —— 存档名是**乱码**，一下就锁定了编码；
3. 换成 ASCII 存档名 `rctest` 重跑 —— **PASS**。对照组一立，结论就硬了。

### 30.4 探针又踩了三个坑（都修了）

| 坑 | 现象 | 修法 |
| --- | --- | --- |
| 截图断言用了上一轮的旧图 | `badrect` 之后还打印 `png=...`（文件是上次留下的） | `shoot()` 里先 `Files.deleteIfExists(png)` |
| 单机「失焦即暂停」 | 窗口没焦点 → 暂停 → tick 里 `currentScreen != null` 直接 return → 右键永远看不到（Fabric 1.21.4 假 FAIL） | 发键前 `ShowWindow(SW_RESTORE)` + `SetForegroundWindow`；实例 `options.txt` 里 `pauseOnLostFocus:false` |
| 上一轮被掐断的游戏还活着、攥着 `session.lock` | `Failed to read level ... 另一个程序已锁定文件的一部分` → 世界不加载 | `verify-rightclick.mjs` 起跑前先杀掉窗口标题是 `Minecraft*` 的 java |

### 30.5 新工具：`scripts/mk-world.mjs`

探针要存档，而自己造存档得点 GUI。现在：

```bash
node scripts/mk-world.mjs --mc 1.20.1 --name flat120 --save-into fabric-loader-0.20.1-1.20
```

起一次对应版本的服务端生超平坦世界 → 拷进实例 `saves/<名字>` → **改 `level.dat`**
（`GameType=1` 创造 + `allowCommands=1` 允许作弊，否则 `/give` 没权限）。
`level.dat` 是 gzip(NBT)，脚本里自带一个往返无损的 NBT 编解码器（不是只读）。

### 30.6 复测结果（全部真右键、全部用重编后的新 jar）

| 目标 | 加载器路径 | 结果 |
| --- | --- | --- |
| Forge 1.21.4 | mojmap 轮询 + 边沿检测 | **PASS** |
| NeoForge 20.6.144 | 同上（独立源码副本） | **PASS** |
| Fabric 1.21.4 | Yarn 轮询 + 边沿检测 | **PASS** |
| Fabric ≤1.20 | `UseItemCallback` 事件 | 见 §31 |

全量重建 `node scripts/rebuild-all.mjs` → **31/31 成功**，`dist/` 里 39 个 jar 全部是新的
（`javap` 抽查过 1.21.4-forge 与 1.7.10-forge 都带 `argFileCharset`）。

### 30.7 规矩补充

- **参数文件/配置文件一律按「读者」的编码写**。JVM 的 `@argfile` 是 `sun.jnu.encoding`，
  不是 `file.encoding` —— 写 UTF-8 必炸，而且炸得无声无息。
- **静默失败要造对照组**：换 ASCII 名字能过 → 立刻定位到编码，别在「世界是不是坏了」上耗。

## 31. 2026-10-29（第十三轮续）：Fabric ≤1.20 的真右键 + 探针改用驱动级输入

§30 那张表里 Fabric ≤1.20 还空着（`UseItemCallback` 是**第四条实现路径**，跟轮询那套不同源）。
补齐它花了点功夫，因为**没有 1.20 的存档**，顺便把「造存档」这件事工具化了。

### 31.1 `PostMessage` 在 1.20 上根本不被认（探针第二次假 FAIL）

1.20.0 实例里世界进去了、`/give` 也没执行、Esc 连暂停都没有 —— **键根本没到游戏**。
用 `keybd_event`（驱动级）同一个窗口按 Esc，立刻 `Saving and pausing game...`。

结论：`PostMessage(WM_KEYDOWN)` 在 1.21.x 上能用（GLFW 处理投递消息），**1.20.0 上不行**。
所以探针改成驱动级输入：

- `keybd_event` 打字（`VkKeyScanW` 反查虚拟键 + Shift 状态，`/` `@` `_` 都能正确打出来）
- `mouse_event(MOUSEEVENTF_RIGHTDOWN/UP)` 右键 —— 这才是「用户真按了」的等价物
- 前置 `ShowWindow(SW_RESTORE)` + `SetForegroundWindow`，并**核对 `GetForegroundWindow()`**
  （打不出来要先怀疑窗口没拿到前台）

顺带两个坑：
- **生成的临时 `.ps1` 必须带 UTF-8 BOM**。PowerShell 5.1 读无 BOM 的 .ps1 按 ANSI(GBK) 解，
  脚本里的中文注释会把引号搞崩（`TerminatorExpectedAtEndOfString`）。
- **quickPlay 有固有竞态**：世界可能在资源重载完成前开始 tick。1.21.4 上撞到过一次
  「美西螈咬死鱿鱼 → 掉落表要走 `LootModifierManager` → 还没加载完 → 服务器线程崩」
  （Forge 自己的 bug，跟 mod 无关）。`verify-rightclick.mjs` 现在识别「Game crashed」自动重试一次。

### 31.2 造存档工具化

- `scripts/nbt.mjs`：往返无损的 NBT 编解码（`level.dat` = gzip(NBT)）。
  带类型标签（byte/int/long/float/double/string/list/compound/数组），round-trip 不会把类型改掉。
- `scripts/mk-world.mjs`：起**对应版本服务端**生超平坦世界 → 拷进实例 → 改 `level.dat`
  （创造 + 允许作弊 + 可选 `--dv` 压 DataVersion）。
  实测：1.20.1 服务端 2.3 秒生成完（`Done (2.345s)!`），`GameType 0 -> 1`、`allowCommands 0 -> 1`，
  拿这个干净存档跑右键 **PASS**。
  坑：`spawn` 的 `cwd` 是工作目录，`--server-jar` 传相对路径会 `Unable to access jarfile` —— 现在一律 `path.resolve`。
  下载校验也比 `>1MB` 严了：跟 manifest 里的 `size` 对齐（断点下载断在半路时旧判据会被骗）。
- `scripts/clone-world.mjs`：拿现成存档克隆到别的实例，顺手改 `DataVersion` / `Version.Name` / 游戏模式。
  实测 1.21.4 的存档改 `DataVersion=3463` 塞给 1.20.0，**能正常进世界**
  （刷一堆 `Skipping BlockEntity with id minecraft:trial_spawner`，无所谓）。

### 31.3 结果

| 目标 | 加载器路径 | 结果 |
| --- | --- | --- |
| Fabric 1.20.0（`fabric-loader-0.20.1-1.20`） | `UseItemCallback` 事件 | **PASS**（驱动级右键，抓图 870x519；克隆存档一份、服务端生成一份都跑过） |
| Forge 1.21.4（换驱动级输入后复跑） | mojmap 轮询 + 边沿检测 | **PASS** |

Fabric ≤1.20 的代码只有一处判定，且是事件驱动（不依赖按键状态被谁消费）：

```java
UseItemCallback.EVENT.register((player, world, hand) -> {
    ItemStack stack = player.getStackInHand(hand);
    if (stack.isOf(McVerMod.DOWNLOAD_TOOL)) {
        if (world.isClient()) { LOGGER.info("mcver: 右键打开启动器"); ... }
        return world.isClient() ? TypedActionResult.success(stack) : TypedActionResult.pass(stack);
    }
    return TypedActionResult.pass(stack);
});
```

### 31.4 抓图仍然只能算「尽力而为」

- `PrintWindow(hwnd, dc, 2)` 在 OpenGL 窗口上**经常返回全黑**（1.21.4-forge 那次就是 5.7KB 全黑图，
  但「文件存在且 >1KB」的断言照样绿）。
- 现在先 `Graphics.CopyFromScreen`（窗口刚被叫到前台，这条路通常能拿到真像素），
  再用 24×14 个采样点判断「是不是全黑」，全黑才退回 `PrintWindow`；两种都黑就打印 WARN，
  **不再把图当证据用**。
- 结论：**判据就是日志里那行 `mcver: 右键打开启动器`**，图只用于人眼看。

### 31.5 还没验的：老世代（Forge 1.7.10~1.12.2 / Legacy Fabric）

那些版本**没有 `--quickPlaySingleplayer`**（1.20 才加的），探针进不了世界。
要验得驱动 GUI：标题界面 → 单人 → 存档列表 → 进入（窗口尺寸要固定、按钮坐标要算）。
代码上它们走物品自带的 `onItemRightClick` / `method_13649` / `method_11373` / `onStartUse`，
**不依赖按键状态**，风险与 Fabric ≤1.20 同级（已实测后者），暂按低风险评估。

## 32. 2026-10-29（第十四轮）：下载页选分类必须按「刷新」才生效 —— UX 真 bug

用户实测：下载页四个分类页签（正式 / 快照 / 远古 / 愚人节）**点了之后列表不变**，
必须再按一下「刷新」才显示对应分类。

### 32.1 根因：`initList()` 从不重算列表

```java
// 点页签
add(Btn.builder(Txt.of(label), b -> { cat = c; page = 0; rebuild(); }) ...);

private void initList() {
    ...
    startVersionLoad();     // versionsRequested=true 时直接 return
    int rows = listRows();
    int to = Math.min(shown.size(), from + rows);   // ← 用的是旧的 shown
```

`shown` 只在三处更新：清单加载完成的回调（`recompute()`）、翻页前的 clamp、以及「刷新」按钮
（它先 `versionsRequested = false` 强制重拉清单，重拉完才 `recompute()`）。
页签点击只改了 `cat` 就 `rebuild()`，**没有任何一处重新过滤 `shown`** → 列表画出来的还是上一个分类。

同一个坑还影响**搜索框**：`setChangedListener` 也是「改 query → rebuild」，输入后同样不生效。

**修法**：`initList()` 里渲染前先本地过滤一次。

```java
if (!all.isEmpty()) recompute();   // 纯本地过滤，不联网
```

`recompute()` 只干 `VersionList.filter(all, query, cat)` + 页码收敛，不碰网络，
所以点页签/打字都不会再触发一次清单下载（清单还是由 `startVersionLoad()` 管）。

### 32.2 新验收工具：`ProbeGuiClick` + `verify-download-tab.mjs`

「点一下有没有反应」必须真点，不能靠 children/像素。做法：

1. mod 在列表面板重建时多打一行**布局信息**（GUI 坐标）：
   `mcver: layout tabs@102,60,70,15,4 search@102,41,291,16`
   —— 验收脚本因此不用复制一份布局算式，界面改了也不会悄悄失准。
2. `ProbeGuiClick` 从日志里取 `gui=WxH` 和这行，用 `GetClientRect` + `ClientToScreen`
   把 GUI 坐标换算成屏幕坐标（`scale = 客户区宽 / guiW`，**不是** `GetWindowRect` —— 那个含标题栏，
   差十来个像素就点空）。
3. `SetCursorPos` + `mouse_event(LEFTDOWN/UP)` 真点，连点两次（窗口刚激活时第一次可能被系统吃掉）。
4. **判据**：只认点击**之后**新冒出来的 `mcver: 分类=X 共=N 个`，并且分类名要对、数量要 > 0。

```bash
node scripts/verify-download-tab.mjs V1.21.4-FABRIC --tab 快照
node scripts/verify-download-tab.mjs V1.21.4-FABRIC --tab 愚人节
node scripts/verify-download-tab.mjs V1.21.4-FABRIC --tab 快照     # 反例：临时注释掉修复后应 FAIL
```

### 32.3 反例对照（这次做了）

把 `if (!all.isEmpty()) recompute();` 注释掉重编，再跑同一个探针：

| 版本 | 结果 |
| --- | --- |
| 无修复 | **FAIL**：点击后 `layout` 行出现两次（说明 rebuild 真跑了），但 `分类=` 行一条新的都没有 |
| 有修复 | **PASS**：`分类=快照 共=748 个`（1.0s）、`分类=愚人节 共=9 个`（1.0s） |

有反例才敢说「探针抓得住这个 bug」，也才敢说修复真的生效。

### 32.4 其他

- `SetForegroundWindow` 会静默失败（前台锁），探针改成 `AttachThreadInput` + `BringWindowToTop`
  再抢一次，并打印 `foreground=… target=…` 核对。
- 探针要等清单加载完（至少一条 `分类=` 日志）才点，否则「加载完成那次 recompute」会冒充成点击结果
  —— 第一版就是这么假 FAIL 的。
