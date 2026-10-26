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
| mod 本体 | 只出 1.21.4 / 1.21.1 / 1.20.1 / 1.19.4 / **1.18.2** 五个包（见 §11）|

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
