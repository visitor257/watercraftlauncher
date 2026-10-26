# McVer 项目报告

> 报告时间：2026-10-23 00:52
> 产物：`D:\Development\mcvermod\mcver-1.0.0.jar`（198.8 KB）
> 源码：`D:\Development\mcvermod\mod\src\main\java\dev\mcver\`，26 个文件 / 5117 行

---

## 一、目标

### 1.1 起点

最初的问题：PCL2 / HMCL 的内置下载功能在 2026-10-20 同日下线了（合规原因），想知道是不是服务端也挂了。查清结论：**服务端全部正常（HTTP 200），只是客户端把入口摘了**。

### 1.2 目标演变

| 阶段 | 目标 | 状态 |
|---|---|---|
| ① 查证 | PCL2/HMCL 下载功能为什么消失 | 已完成 |
| ② 补齐 | 自己做一个能下载安装 MC 版本的工具 | 已完成 |
| ③ 迁移 | 从独立 Java 程序改成**游戏内的 mod** | 已完成 |
| ④ 扩展 | 从「只装不启动」→ **游戏内的完整 MC 启动器** | 已完成 |
| ⑤ 对齐 | 参考 PCL2 的功能面，补齐差距 | 进行中 |

**当前目标（一句话）**：在 Minecraft 游戏内做一个 mod 形态的启动器——能下载安装任意版本（含模组加载器/光影加载器）、管理账号、启动其他 MC 版本，UI 采用原版容器视觉语言。

### 1.3 硬性约束（用户明确要求）

- **UI**：屏幕中央的灰色面板（原版箱子/背包那种），不要全屏铺开
- **文字**：不能有阴影重影 → 所有 `drawText(..., false)`
- **行为**：对齐 PCL/HMCL——静默下载、必要时无头跑安装器、不弹窗外窗
- **布局**：装成标准 vanilla 目录结构，官方启动器/PCL/HMCL 都能识别
- **合规**：不抄 PCL 代码，只参考功能面
- **平台**：Fabric 客户端 mod，编译目标 1.21.4，需 Fabric API，JDK 21

---

## 二、交付物

### 2.1 产物清单

```
D:\Development\mcvermod\
├── mcver-1.0.0.jar          198.8 KB   ← 可直接丢进 mods/
├── README.md                 13.2 KB   ← 完整文档（功能/坑/钩子表）
├── REPORT.md                            ← 本文件
└── mod\                                 ← Gradle 工程
    ├── build.gradle
    ├── gradle.properties                (MC 1.21.4 / yarn build.8 / loader 0.16.14 / loom 1.9.2)
    └── src\main\java\dev\mcver\         (26 文件 / 5117 行)
```

### 2.2 代码结构

```
dev.mcver
├── McVerMod.java          29  物品注册
├── McVerClient.java       92  右键 / 快捷键 M / 调试钩子
├── core\
│   ├── Installer.java    672  原版安装器（manifest→json→client→libs→natives→assets）
│   ├── Launcher.java     462  classpath 拼装 + argfile + 子进程启动
│   ├── LoaderInstaller.java 394  Fabric/Quilt/Forge/NeoForge/OptiFine/Iris 安装
│   ├── Auth.java         248  微软设备码登录 + token 续期
│   ├── Json.java         208  极简 JSON 解析/写出
│   ├── InstallJob.java   193  安装流水线（后台线程）
│   ├── JavaFinder.java   154  按 major version 找 JDK
│   ├── Dirs.java         146  目录/隔离/trash
│   ├── Config.java       137  全局配置 + 账号库
│   ├── LoaderVersions.java 139  各加载器版本列表
│   ├── Net.java          112  HttpURLConnection 封装（硬超时 + 镜像回退 + jar 校验）
│   ├── VersionList.java   70  原版版本清单（920 个）
│   ├── Modrinth.java      58  Modrinth API（Iris/Sodium）
│   ├── VersionMeta.java   56  命名建议 / 改名 / 建隔离目录
│   ├── Account.java       56  账号数据
│   └── Jobs.java          43  后台任务登记
└── ui\
    ├── DownloadPage.java 610  下载页（LIST / DETAIL / PROGRESS 三视图）
    ├── VersionsPage.java 278  版本管理
    ├── LaunchPage.java   262  启动页
    ├── AccountsPage.java 243  账号页
    ├── MainScreen.java   218  面板外壳 + 导航 + ✕
    ├── SettingsPage.java 159  设置页
    ├── Page.java          61  页基类（无阴影文字工具）
    └── UiState.java       17  跨页共享状态
```

---

## 三、现在做了什么

### 3.1 原版安装（`core/Installer.java`）

不依赖任何第三方库，自己走完整链路：

```
version_manifest_v2.json
  → versions/<id>/<id>.json
  → <id>.jar（client）
  → libraries/**（sha1 校验、16 线程并发）
  → natives 解压
  → assets/indexes/<index>.json + assets/objects/**
```

- 官方源失败自动回退 BMCLAPI
- **下载失败重试 3 轮 + 收尾 sha1 全量校验**，仍缺直接抛异常（不假报成功）
- 实测：1.7.10（686 assets / 107MB）、1.20.1（3598 assets / 622MB）、1.21.4（403MB）

### 3.2 加载器 / 光影安装（`core/LoaderInstaller.java`）

| 组件 | 实现方式 |
|---|---|
| Fabric | **不走 installer**，直接取 `profile/json`，自下 8 个库再写版本 json |
| Quilt | 同上（镜像 `bmclapi2/quilt-meta`） |
| Forge | 官方 installer CLI `--installClient` 无头运行 |
| NeoForge | 同上 |
| OptiFine | 下载 jar → `URLClassLoader` 反射调 `optifine.Installer.doInstall(File)` |
| Iris | Modrinth API 下载，落进 `mods/`，顺带 Sodium |

安装顺序固定：**原版 → 模组加载器 → 光影 → 改名 → 隔离目录**。

### 3.3 启动（`core/Launcher.java`）

- 递归合并 `inheritsFrom`（libraries 按 `group:artifact:classifier` 覆盖，arguments 父前子后）
- rules 过滤 → classpath
- ≤1.18 natives 走 classifiers 解压；≥1.19 natives jar 直接上 classpath
- 占位符替换（`${auth_player_name}`、`${game_directory}`、`${assets_root}` …）
- **argfile 方案**（`java @file`）绕开 Windows 32767 字符命令行上限
- `ProcessBuilder` 起子进程，日志落到版本目录
- 启动前自检：缺库 / 缺 client.jar / 缺资源对象 → 界面直接报，不硬启

### 3.4 账号（`core/Auth.java`）

- **离线账号**：`UUID v3("OfflinePlayer:<name>")`
- **微软正版**：设备码登录，走 legacy Live 端点
  ```
  https://login.live.com/oauth20_connect.srf
  scope: service::user.auth.xboxlive.com::MBI_SSL
  → XBL (RpsTicket 前缀 t=，失败退 d=)
  → XSTS → Minecraft services → profile
  ```
- token 自动续期
- 存 `config/mcver.json`（明文，同 PCL/HMCL 做法）

> **关键发现**：公开 client id `00000000402b5328` 走 AAD 新端点
> `login.microsoftonline.com/consumers/oauth2/v2.0/devicecode` 会报
> `AADSTS700016: Application ... was not found`，必须走 legacy Live 端点。

### 3.5 UI（`ui/`）

**面板外壳**：`min(400, 屏宽×88%) × min(232, 屏高×92%)`，实测 GUI 427×240 下 `375x220@26,10`。
背景压暗 + 模糊，四周能看到世界。左边导航，右边是页，**右上角 ✕**。

| 页 | 功能 |
|---|---|
| 启动 | 已装版本列表（滚轮）、内存、启动后关当前、启动、下载版本 |
| 下载 | **两级**：版本列表 → 点进版本 → 名称 + 加载器 + 光影 + 各自版本 |
| 账号 | 离线 / 微软登录 / 设为当前 / 删除 |
| 版本 | 重命名、删除（移 trash 不真删）、补全文件、打开目录、一键开隔离 |
| 设置 | 内存、JVM/游戏参数、窗口大小/全屏、下载线程/源、指定 Java、游戏目录、安装后隔离 |

**下载页两层结构**（本轮重做）：

1. 第一层：一行一个版本 `✔ 1.21.4  正式  2024-12-03`，带搜索、快照开关、翻页、刷新
2. 第二层：版本名（自动填 `1.21.4-Fabric_0.20.1`，可改）+ 模组加载器 + 光影加载器 + 版本列表 + 开始安装

**✕ 按钮语义**：只收界面，不杀任务。安装跑在独立 daemon 线程，头部显示 `后台 xxx 42%`。

**文字无阴影**：1.21.4 的 `DrawContext` 只有带 shadow 参数的 `drawText`，没有无阴影的居中版本，所以居中自己按 `getWidth()/2` 算，全部传 `false`。

---

## 四、验证证据

### 4.1 功能验证

| 项 | 结果 |
|---|---|
| 四页渲染 | children = 16 / 20 / 9 / 11 / 15 |
| 下载页 | 列表 children=20，详情 children=23 |
| 面板几何 | `panel=375x220@26,10`（gui=427x240） |
| 完整安装链路 | `1.21.4 → Fabric 0.20.1 → 改名 mcver-test → 隔离目录`，≈40s |
| 关界面不断下载 | `MCVER_AUTO_CLOSE` 开装 4s 后关屏，任务仍跑到「安装完成」 |
| 启动装好的版本 | 子进程进到标题界面（日志到 `Created: gui.png-atlas`） |
| OptiFine 静默装 | 1.20.1 生成 `1.20.1-OptiFine_HD_U_I6` + `libraries/optifine/*` |
| 微软登录 | 真拿到设备码 `USER_CODE=RXPXGPFL` |

### 4.2 像素断言（因为模型看不了图，改用采样）

截游戏窗口必须用**游戏自己的** `ScreenshotRecorder.saveScreenshot()`——外部 `CopyFromScreen` 会被遮挡/失焦窗口骗到，出来全白。

| 采样点 | 期望 | 实测 |
|---|---|---|
| 面板体 | `#C6C6C6` | `ffc6c6c6` ✓ |
| 外框 | `#000000` | `ff000000` ✓ |
| 按钮 | `#6F6F6F` | `ff6f6f6f` ✓ |
| 输入框 | 深色 | `ff000000` ✓ |
| **标题最暗像素** | `0x40`（正文字色） | `0x40` ✓ 无阴影 |

> 有阴影时标题附近会出现 ~`0x10` 的暗像素，这是判定阴影的可靠信号。

### 4.3 实跑日志片段

```
[00:29:02] 1.21.4 安装完成 -> ...\versions\1.21.4
[00:29:02] ---- 安装 Fabric 0.20.1 ----
[00:29:02] profile 里有 8 个库
[00:29:05] 库下载完成：新下 1，已有 7
[00:29:05] 写入 fabric-loader-0.20.1-1.21.4.json
[00:29:05] 版本已命名为 mcver-test
[00:29:05] 版本隔离已开启 -> ...\versions\mcver-test
[00:29:05] 安装完成: mcver-test
```

---

## 五、踩过的坑（重要，按严重度排序）

| # | 坑 | 修法 |
|---|---|---|
| 1 | **`Installer.downloadAll` 静默丢文件**：单文件异常只打 stderr，1.20.1 丢 775/3598 个资源仍报「完成」，启动报 `NoSuchFileException` | 失败收集 + 重试 3 轮 + 收尾 sha1 全量校验 + 仍缺抛异常 |
| 2 | **游戏 JVM 里 `java.net.http.HttpClient` 卡死**：`meta/maven.fabricmc.net`（Cloudflare）在 `send()` 里不返回，request timeout 也不生效（jstack 卡 `CompletableFuture.get` 7 分钟）；PowerShell 同 URL 1 秒 | 小请求全换 `HttpURLConnection`（6s/15s 硬超时）+ 镜像回退；默认**镜像优先** |
| 3 | **半截 jar 让启动崩**：`intermediary-1.21.4.jar` 只有 147KB（正常 701KB），报 `zip END header not found` | `Net.isJar()` 用 `ZipFile` 真开一次校验；`Launcher.plan()` 也校验，坏文件算进 missing |
| 4 | **继承版本找不到 client jar**：Fabric 版本 json 只有 `inheritsFrom`，合并后该字段消失，按 `versions/<id>/<id>.jar` 找必然失败 | 新增 `resolveJarId()` 顺 `inheritsFrom` 往上爬 |
| 5 | **微软登录不能用 AAD 端点**：`AADSTS700016` | 改走 legacy `login.live.com/oauth20_connect.srf` |
| 6 | **面板被背景盖掉**：`Screen.render()` 先跑 `renderBackground()` 再画控件 | 面板画在 `renderBackground()` 里，先 `super` 再画 |
| 7 | **OptiFine 受不了 BOM**：`launcher_profiles.json` 带 BOM 时它自带解析器报 `Unexpected character (?) at position 0` | 写文件时确保无 BOM |
| 8 | **Windows 32767 命令行上限**：classpath 太长起不来 | `java @argfile` |
| 9 | 1.21.4 物品注册 NPE | `Item.Settings` 必须 `.registryKey(RegistryKey.of(RegistryKeys.ITEM, ITEM_ID))` |
| 10 | 进度条完成态为空 | `InstallJob` 末尾补 `done = 1; total = 1;` |
| 11 | 两实例共用 `logs/latest.log` 打 ERROR（不致命） | 开版本隔离即消失 |

---

## 六、已知限制

1. **不能热切换版本**：JVM classpath / natives 定死，只能新开子进程。这就是 PCL/HMCL 的做法，不是缺陷。
2. **物品是客户端注册的**：单人正常；多人服务器不认这个 id，别带进服务器/扔箱子，多人用快捷键 `M`。
3. **联机（EasyTier/Terracotta）不做**：塞进游戏进程不现实。
4. **CurseForge 不做**：要 API key + 条款限制。资源下载走 Modrinth（免费无 key）。
5. 离线环境会打 `Failed to verify authentication (401)`、`Failed to retrieve profile key pair`，属正常噪音。

---

## 七、还没做（PCL 有，这里没有）

| 优先级 | 功能 | 说明 |
|---|---|---|
| ① | **Modrinth 下载中心** | Mod / 资源包 / 光影 / 整合包，搜索 + 按版本和加载器过滤，直接装进当前实例 |
| ② | **Mod 管理** | 列出 `mods/`、读 `fabric.mod.json` / `mods.toml` 拿真名、`.disabled` 启停、删除、更新 |
| ③ | `.mrpack` 导入导出 | 纯 zip + `modrinth.index.json` + overrides，实现最简 |
| ④ | Java 自动下载 | Adoptium API |
| ⑤ | 存档备份 | |
| ⑥ | 崩溃日志分析 | |

`DownloadPage.java` 已经有 610 行、五个视图的骨架，① 可以直接在同一个页面体系里加一个导航项。

---

## 八、风险

1. **FDev/Mojang 服务条款**：游戏内启动器本身不违规（跟 PCL/HMCL 同类），但绕过官方启动器的行为在 ToS 灰色地带。
2. **合规定位**：PCL 下线的直接原因是「遵守相关法律法规」，说明「提供 MC 下载」这件事本身在当前环境有监管风险。这个 mod 只在本机自用、不分发的话问题不大；**如果要发布，需要先想清楚这一点**。
3. **明文存 token**：`config/mcver.json` 明文存 refresh token，跟 PCL/HMCL 一致。自用可接受，别同步到公共云盘。

---

## 九、一句话总结

从「PCL 为什么不能下载 MC 了」这个问题出发，做出了一个 **198.8 KB 的游戏内 MC 启动器**：能装任意原版版本、能装 Fabric/Quilt/Forge/NeoForge/Iris/OptiFine、能登录微软账号、能启动别的 MC 版本，UI 是原版风格的居中灰面板、文字无阴影，右上角 ✕ 收界面而不断下载。核心链路全部实测跑通。

---

## 十、2026-10-23 下午：多目录 / Java 列表 / 启动诊断

### 改了什么

1. **下载页去掉底部目录显示**：顶上 header 已经有当前目录，底部只留翻页和数量。
   安装详情页原来那行 `→ D:\...  [隔离开]` 也简化成 `隔离：开/关`。
2. **多游戏目录**（对齐 PCL/HMCL）：
   - 启动页第一行 `[◀] [目录名] [▶] [＋]`，`◀ ▶` 直接切，切换后版本列表/下载目标/安装目录全跟随。
   - `＋`/点目录名进「目录管理」：已知目录列表（点一行切换）、`扫描磁盘`、
     底部粘路径添加（目录不存在自动建）、`✕` 移出列表。
   - 持久化：`config/mcver.json` 的 `dirs` + `lastDir`；下次启动回到上次的目录。
   - 自动发现：history、`~/.minecraft`、`%APPDATA%\.minecraft`、XDG、macOS、
     当前工作目录，以及扫描时任何 `.minecraft` / 带 `launcher_profiles.json` 的目录。
   - `Dirs.historyFile()` 从相对路径 `config/` 改成 `Config.dir()`，避免 CWD 不同时丢历史。
3. **「Java」页**（新导航项）：
   - `JavaFinder` 重写：不再「每个 major 只留一个」，全部列出（按 major 倒序、同版本 java.exe 优先），
     并记录哪些是手动加的。
   - 扫描范围扩大：extra、当前进程、JAVA_HOME、各家 Program Files（Temurin/BellSoft/Microsoft/Zulu/Corretto/Semeru）、
     `~/.jdks`、`~/.sdkman`、`/usr/lib/jvm`、macOS、PCL 配置、`runtime/`（APPDATA / home / CWD）。
   - 页面：`▶ 自动（按版本挑）` + 每个 Java 一行（版本 / JDK 名 / 完整路径）+ `重新扫描`；
     点一行=指定；底部输入框可粘 `java.exe`/`javaw.exe`/`bin`/JDK 根目录/macOS Home。
   - Linux/macOS：没有文件选择框，统一用粘路径（`.../bin/java`），自动扫描一样有效。
4. **启动失败可诊断 / 修老版本启动**：
   - `Launcher.plan` 里 ≤1.18 的 natives 库原来直接 `continue`，**普通 jar 没进 classpath**
     （旧版 LWJGL2 会 `NoClassDefFoundError`）→ 现在解压 natives 后仍然把 artifact 加进 classpath。
   - 启动后等 1.8s：进程立刻退出就把**退出码 + 日志尾部**抛到界面上（以前只显示“已启动”）。
   - 「启动后关当前」改为**先等 6 秒确认子进程活着**再关；死了就不关，显示日志路径。
   - 「版本」页新增 **日志** 按钮：打开最近一次 `mcver-launch-*.log`。
   - `Config.javaPath` 现在接受目录 / `javaw.exe`（`JavaFinder.resolveExe`），不再要求必须是 `java.exe`。
   - Java 版本明显不匹配时在启动行提示（例如用 Java 21 跑要求 8 的版本）。
   - `Json.parse` 去掉 UTF-8 BOM：手工编辑过的 `mcver.json` 带 BOM 不会再整份读失败。

### 实测

- `gradle build` 通过；产物 186 KB（`mcver-1.0.0.jar`）。
- 端到端：1.21.4 → Fabric 0.20.1 → 命名 `mcver-e2e` → 隔离目录，约 65s；
  随后从启动页启动该版本，子进程正常加载 Fabric 0.20.1 并进到标题界面。
- 用真实目录 `D:\Program Files\Minecraft Java Edition (pcl2)\.minecraft` 启动
  `1.20.1-Fabric 0.19.5`（Java 17）和 `1.20.1-Forge_47.2.31`（Java 17），都进到标题界面。
- 截图：`_keep/screenshots/mcver-{启动,下载,账号,版本,Java,设置}.png`；目录管理视图另有
  `_keep/screenshots/mcver-启动-目录.png`。

### 调试钩子新增

`MCVER_DIRS=1`（直接进目录管理）、`MCVER_TAB=Java`（直接开 Java 页）。
---

## 十一、14:05 补丁：Java 页翻页 + 目录列表被替换

**1. Java 页改成翻页**

原来只有滚轮（mouseScrolled 直接改 scroll），页面上没有任何页码暗示，看起来就像「只列了一屏」。
现在和「下载」页统一：顶部 ◀ ▶ 两个按钮 + 右侧 1/2 页码，滚轮顺手也能翻页，
底部状态行显示 第 1/2 页 · 共 14 个。实测 Java 页 children 从 18 → 20（多出 ◀ ▶）。

**2. 「添加」把原来的目录替换掉了**

真因不是 Dirs.add 写错，而是**当前目录根本没进 Config.dirs**：

- MainScreen.restoreDir() 只用 UiState.setDir() 恢复上次目录，没登记；
- Dirs.known() = Config.dirs → Config.lastDir → 自动扫到的；
- 于是这个目录**只靠 lastDir 活着**。用户一点「添加」，switchDir 把 lastDir 改成新目录，
  旧目录立刻从 known() 里消失 → 看起来就是「被替换掉了」。
- （只有之前用 ◀ ▶ 切过一次的目录才会进 Config.dirs，所以这个 bug 时灵时不灵。）

修法三处：

1. 
estoreDir() 恢复后调 Dirs.add(p)，开机就把当前目录登记进 dirs；
   兜底分支用 Dirs.touchHistory() 只写历史。
2. Dirs.add(Path p, Path alsoKeep) 重载：登记新目录的同时把**切换前那个**也留在列表里
   （顺序 [新, 旧, 其余…]）；老的单参版本转调 dd(p, null)。
3. UiState.switchDir() 先 Path old = dir() 再改 lastDir，把 old 传给 dd。
   （顺序很关键：先设 lastDir 再 dd 的话，add 里读到的「上一个」已经是新值了。）

顺带 UX：添加 之后**留在管理页**不跳回启动页，列表里能直接看到两行 +
状态提示「已添加并切到 X（原来的目录还在下面）」；右上角显示 N 个。

实测（config 里 lastDir = PCL 目录、dirs = []，复现最坏情况）：
添加 D:\Development\mcvermod\testdir2 后落盘成
dirs: ["…\testdir2", "…\Minecraft Java Edition (pcl2)\.minecraft"]，两个都在；
日志 mcver: 目录列表 [testdir2, PCL 目录, %APPDATA%\.minecraft, mod\run]。

**新调试钩子**：MCVER_DIR_ADD=<路径>（自动粘路径 + 点「添加」，用于回归验证）。
---

## 十二、15:10 六项新需求

1. **改名 Water Craft Launcher**：abric.mod.json 的 
ame、物品 lang（item.mcver.download_tool）、
   创造栏分类、按键分类、界面左上角标题都改成 Water Craft Launcher。
   mod id 仍然是 mcver（改 id 会让 config/mcver.json、物品 id 全部失效，不值得）。
2. **物品图标换成流动的水**：ssets/mcver/models/item/download_tool.json 的 layer0
   直接指到 minecraft:block/water_flow（原版流动水贴图，自带 mcmeta 动画）。
   自己那张 download_tool.png 删了。
   —— 比复制一张水贴图强：跟着资源包走，别人换材质包图标也跟着变。
3. **版本删除改真删 + 二次确认**：删掉 	rash-mcver 逻辑，改成 Files.walk 递归删；
   点「删除」先进入确认态，底部只留「确定永久删除 / 取消」，红色警告写着不可恢复。
4. **修版本隔离**（用户说的「开关有问题」）：
   - 根因：Plan 里明明有 
unDir 字段，但 plan() 始终把 
unDir = gameDir，
     **启动时根本没把 --gameDir 换到 versions/<id>**，所以隔离只是建了几个空目录。
   - 改成：Launcher.isIsolated()/setIsolated() + ersions/<id>/.mcver-isolated 标记文件；
     plan() 见到标记就把 p.runDir 指到版本目录（assets / libraries 仍共用）。
   - 开关现在能关：关掉只删标记文件，用户存进去的存档/Mod 一个不删。
   - 实测：1.19.3 开隔离后启动日志落在 ersions/1.19.3/logs/，游戏正常进到图集创建。
5. **「安装后隔离」→「版本隔离」**：设置页和下载页的文案/提示统一。
6. **Forge / NeoForge 版本列表修好了**（是真 bug + 网络双杀）：
   - NeoForge 官方 maven maven.neoforged.net 在本机 DNS 被污染（返回 Facebook 的假 IP，直接超时）；
     而且旧代码用 p[1]+"."+p[2]+"." 推前缀，对 MC 1.20.1 会算成 20.1.（实际 NeoForge 是 47.x）→ 永远空列表。
   - 现在优先用 BMCLAPI 的按 MC 版本接口：
     GET https://bmclapi2.bangbang93.com/neoforge/list/<mc>、.../forge/minecraft/<mc>，
     官方 maven 只当兜底；1.20.1 的前缀特判成 47.。
   - 实测：NeoForge 1.20.1 → 60 个版本（首个 47.1.84）；Forge 1.20.1 → 137 个版本（首个 47.4.28）。
   - 安装器 jar 下载也按「当前源 → BMCLAPI → 官方」三档依次试。
7. **下载源可配置**（新增 core/Sources.java）：
   - 默认**只有官方**；Config.downloadSources 存用户加的镜像（名字=根地址）。
   - 设置页新增「下载源」视图：列表点选、✕ 删除、底部两个输入框（名字 + 根地址）+「添加」（先探测一次再存），
     还有一个「＋ BMCLAPI」一键预设。
   - 镜像按 BMCLAPI 约定映射：{root}/mc/game/version_manifest_v2.json、{root}/maven/...、{root}/assets/xx/yy；
     Installer.rewrite() 从写死的 BMCLAPI 换成 Sources.Src.rewrite()。
   - Net.getMirrored() 改成「先当前源 → 再官方」，Fabric/Quilt meta 也走同一套。
   - 实测：写入 BMCLAPI|https://bmclapi2.bangbang93.com → 探测通过 → 落盘
     downloadSource: "BMCLAPI", downloadSources: ["BMCLAPI=https://bmclapi2.bangbang93.com"]。

### 新调试钩子

MCVER_SOURCES=1（设置页直接进下载源视图）、MCVER_SRC_ADD="名字|根地址"（自动添加）、
MCVER_VSEL=<id>（版本页选中）、MCVER_VDEL=1 / MCVER_VDEL_OK=1（进确认态 / 直接删）、
MCVER_VISO=1（切版本隔离）。
---

## 十三、16:25 图标 / 副标题 / Fabric 启动不了（三个都是真 bug）

### 1. 图标还是紫黑错误块 —— 1.21.4 的 item model 换了机制

**根因**：1.21.4 起，物品模型不再从 `assets/<ns>/models/item/<id>.json` 自动推断，
必须在 `assets/<ns>/items/<id>.json` 里显式声明（Item Model Definition）。
只给 models/item 的话，客户端根本不知道这个物品该用哪个模型 → 紫黑错误方块。

**修法**：
- 新增 `assets/mcver/items/download_tool.json`:
  `{"model":{"type":"minecraft:model","model":"mcver:item/download_tool"}}`
- 图标贴图：直接引用 `minecraft:block/water_flow` 是**画不出来的**——
  水的贴图是**灰度遮罩**（颜色由 biome water color 调制），拿来做物品图标会是一块灰的，
  而且 32×1024 的动画帧在 item/generated 下也没显示出来。
  最终做法：从客户端 jar 取 `water_flow.png` 第一帧 → 缩到 16×16 → 按水的蓝
  （#2E5CB8 → #9EC8FF）重新映射灰度 → 存成 `assets/mcver/textures/item/download_tool.png`（不透明）。
- `models/item/download_tool.json` 的 layer0 指自己的贴图。

**验证**：自检钩子 `MCVER_ICON=1` 在面板右下角 `ctx.drawItem` 画一次，
取该区域像素：B-R = **97**（明显是蓝的）；对照组：原版纸图标能画出来，改动前 mcver 图标区域 B-R = **0**（纯按钮灰）。

### 2. 去掉「游戏内版本管理器」副标题

`MainScreen.drawHeader` 里那段（算宽度、trim、与用户名避让）整段删掉，只留标题 + 右上角用户名。
**验证**：截图像素检查，标题区有文字（dark 1941），标题右侧区域 min=198（纯面板灰，dark=0）。

### 3. Fabric 版本启动不了（退出码 1）—— classpath 里 ASM 重复

**日志**（这次终于抓到了）：
`
java.lang.IllegalStateException: duplicate ASM classes found on classpath:
  .../org/ow2/asm/asm/9.6/asm-9.6.jar!/org/objectweb/asm/ClassReader.class,
  .../org/ow2/asm/asm/9.10.1/asm-9.10.1.jar!/org/objectweb/asm/ClassReader.class
  at net.fabricmc.loader.impl.util.LoaderUtil.verifyClasspath
`

**根因**：`merged()` 合并父版本（1.21.4 自带 asm 9.6）和 Fabric 版本的 libraries 时，
按 `group|artifact|classifier` 去重只在**相邻两层**生效；多层继承 / json 里本来就有两个 asm 时，
两个版本都会进 classpath。Fabric 的 `verifyClasspath` 发现重复类直接抛异常 → 退出码 1。

**修法**：`plan()` 里先把 libraries 收进 `Map<group:artifact:ext, lib>`，
**同 artifact 留版本最高的那个**（`LoaderInstaller.compareVersions`），再走 natives / classpath。
这也正是官方启动器的行为。

**顺手改的**：启动失败时的日志摘要 `tail()` 原来只取最后 5 行，
堆栈一长就全是 `at ...`，用户看到的就是「日志里没写原因」。
现在先扫第一条含 `Exception / Error / Caused by / Could not` 的行（那才是根因），再补最后几行。

**实测**（真目录 `D:\Program Files\Minecraft Java Edition (pcl2)\.minecraft`）：
- 1.21.4-Fabric 0.19.5（Java 21）→ 进游戏（Setting user），只剩离线 401
- 1.20.1-Fabric 0.19.5（Java 17）→ 进游戏，只剩离线 401
- 1.20.1-Forge_47.2.31（Java 17）→ 模块系统正常加载，进游戏

### 新增/沿用钩子
`MCVER_ICON=1`（右下角画物品图标，用来查图标）、`MCVER_AUTO_LAUNCH`、`MCVER_TAB`、`MCVER_SHOT`。

### 教训
- **1.21.4 的 items/*.json 不能忘**，这是 1.21.4 相对 1.21.1 最容易漏的破坏性变更。
- 原版好多贴图是**灰度 + 着色**的（水、草、树叶），直接拿来当物品图标会是灰的。
- 想验 UI 效果就 `ctx.drawItem` 画出来 + 取像素比颜色，比截图肉眼靠谱（这个环境我看不到图）。

---

## 十四、16:42 交接包

为了换电脑接力开发，写了两样东西：

- **HANDOVER.md**：交接手册。不重复功能说明，只写「怎么在新电脑跑起来 / 代码怎么组织 /
  哪些坑别再踩 / 没有画面的环境怎么写验证」。踩坑部分记了 8 类：图标三件套、
  Fabric 的 ASM 重复、Forge/NeoForge 的 DNS 污染与 1.20.1 前缀、版本隔离、多目录登记、
  PowerShell 的 BOM/CRLF/中文编码、离线账号 401 属正常、build 残留旧资源。
- **mcvermod-handover-2026-10-23.zip**（1.9 MB，54 个文件，顶层 watercraft-launcher/）：
  HANDOVER + README + REPORT + jar + 9 张截图 + mod/src 全部源码 + 资源 + 构建脚本。
  排除 mod/run（554 MB 测试游戏目录）、mod/build、mod/.gradle（69 MB）、
  .tools/gradle-8.12（144 MB）、*.bak 和所有 
un-*.log。

Gradle 没打进包（144 MB 太大），HANDOVER 1.2 给了三条路：用现有 gradle / 拷目录 /
下 8.12 压缩包（附腾讯镜像），并说明为什么不建议直接用 gradlew.bat。

打包脚本用 .NET 的 ZipFile（Compress-Archive 对中文文件名和大目录不稳），
踩了一个新坑：**PowerShell 5.1 按 GBK 读 .ps1**，脚本里写中文注释会解析失败报
"表达式或语句中包含意外的标记"，所以打包脚本一律写成 UTF-8 无 BOM + 英文注释。
---

## 十五、2026-10-23 夜：换机复现 + Fabric 启动"有 pid 没窗口"

接手环境：本机没有 gradle，按 HANDOVER 1.2 从腾讯镜像下 gradle-8.12 解压到 `.tools/`，
`gradle build`（首次联网）7m39s 通过，产物 200 KB 正常。

### 15.1 先搭了个离线复现台

core 层除了 `Config` 里一句 `MinecraftClient.getInstance()` 之外不依赖 Minecraft，
所以可以拿个桩类把 core 单独编出来，直接用真实 metadata 跑 `Launcher.plan()`：

`
javac -d out (core/*.java + 桩 net/minecraft/client/MinecraftClient.java)
java -cp out Probe <gameDir> <versionId>
`

这样能一眼看出 classpath / jvm 参数 / 占位符替换对不对，不用进游戏。

### 15.2 实测：Fabric 启动本身是通的

在 `testgame/` 里装真 1.21.4（官方 403 MB 资源全下完）+ Fabric loader 0.20.1，
再走 UI 那套「改名 + 版本隔离」，然后 `Launcher.launch()` 起真游戏：

- pid 正常，25 秒内窗口出现（任务栏标题 `Minecraft* 1.21.4`）
- 日志到 `Setting user:` / `Backend library: LWJGL version 3.3.3-snapshot`
- 只剩离线账号 401 + Realms 认证失败（HANDOVER 4.7 说过的正常现象）

结论：**"pid 有、窗口没有"不是 plan()/launch() 的结构性 bug**，得看现场环境。

### 15.3 这轮修掉的三处真问题

1. **`Installer.Json` 不认 BOM**（真 bug）
   同一个文件 `core/Json` 能读、`Installer.Json` 抛
   `NumberFormatException: For input string: ""`（`Long.parseLong("")`）。
   后果：带 BOM 的版本 json（记事本存过 / 别的启动器写的）会被 `isComplete()` 判成"没装完"
   从启动页消失；强行启动则直接抛异常。现在 `parse()` 先剥 `\uFEFF`，`number()` 也加了空 token 兜底。

2. **`rulesAllow()` 忽略 `os.arch`**（真 bug）
   1.21.4 里 `-Xss1M` 的规则是 `os.arch=x86`（只有 32 位该加），以前不看 arch，
   64 位也被塞 `-Xss1M`，线程栈只剩 1 MB —— Fabric 的 mixin 深调用正好怕这个。
   现在按 `os.arch` 比对（x86_64 / x86 / arm64 归一化），实测 jvm 参数从 11 条降到 10 条。

3. **JavaFinder 会把 32 位 JVM 也列进来**（潜在）
   `inspect()` 只读 `JAVA_VERSION`，不看 `OS_ARCH`。64 位系统上选中 32 位 java，
   MC 1.13+ 的 natives 全是 64 位，表现就是**进程起来了、窗口永远出不来**。
   现在 64 位系统直接过滤 32 位 JVM。

### 15.4 新增诊断：窗口看门狗

`LaunchPage.watchWindow()`：启动 25 秒后若子进程还活着、日志里却连
`Backend library:` / `Setting user:` 都没有，就把结论 + 日志路径（有 `hs_err_pid<pid>.log`
就带崩溃报告路径）写到启动页状态栏。以后再遇到"有 pid 没窗口"，界面自己会说原因。

### 15.5 产物

`mcver-1.0.0.jar` 已用新代码重建（202,487 字节，旧的在 `mcver-1.0.0.jar.bak`）。
`testgame/` 是这次的实测游戏目录（1.21.4 + 1.21.4-Fabric_0.20.1，开了隔离），可直接复跑。
### 15.6 自动选 Java 的逻辑也重写了（用户指出「如果真是 java 的问题，那说明自动选择有问题」）

原来 `pickJava` 的毛病：

1. **设置页指定的 java 无条件优先**，哪怕它低于版本要求。用户早先给 1.8 版本设过 Java 8，
   后来启动 1.21.4 依旧拿 Java 8 起进程，只在状态栏多一行「Java 8，版本要 21」——
   表现出来就是"有 pid、没窗口"，而且完全看不出跟 Java 有关。
2. `forMajor()` 找不到满足的版本时**返回列表里版本最低的那个**（`list.get(size-1)`），
   等于明知道不够还硬上。
3. `Found` 里没有架构字段，`inspect()` 只看 `JAVA_VERSION` 不看 `OS_ARCH`，
   64 位系统上照样会列/选 32 位 JVM。

现在的行为：

- `Found` 多了 `arch`，界面 Java 页显示成 `Java 21 x86_64  ...\bin\java.exe`，看得见。
- `inspect()` 读 `release` 里的 `JAVA_VERSION` + `OS_ARCH`；没有 release 文件才跑一次
  `java -XshowSettings:properties -version`，一次把版本和架构都抠出来。
- 64 位系统上 32 位 JVM 直接不进列表（32 位系统不过滤）。
- `pickJava()`：指定的 java **必须满足 requiredJava** 才用，否则自动兜底并说明原因。
- `forMajor()`：先找同版本 → 再找"更高的里面最接近的" → 一个都没有返回 null（不糊弄）。
- `Launch.launch()`：选出来的 Java 版本低于 requiredJava 时**直接报错**，附上版本要求和当前路径，
  不再让 JVM 抛 `UnsupportedClassVersionError` 那种天书。

本机实测（3 个 JVM）：

`
21  arch=x86_64  D:\Program Files\Java\jdk-21\bin\java.exe
18  arch=x86_64  D:\Program Files\Java\jdk-18.0.2.1\bin\java.exe
 8  arch=x86_64  D:\Program Files\Java\jre1.8.0_431\bin\java.exe

forMajor(21) = jdk-21      forMajor(17) = jdk-18.0.2.1      forMajor(99) = null
pickJava(javaPath=Java 8, 需要 21) -> jdk-21   notes=[指定的 Java 8 低于版本要求的 21，改用自动选择]
`

改完又跑了一遍 Fabric 启动回归：窗口照常出来（`Minecraft* 1.21.4`），日志到 `Setting user` / `Backend library`。
---

## 十六、2026-10-24 凌晨：六项 UI/流程需求

用户提的六条，全部改完并构建通过（mcver-1.0.0.jar 204 KB 级别，另有 .bak 备份旧版）：

1. **标题栏加物品图标**：MainScreen.drawHeader() 里 ctx.drawItem(new ItemStack(DOWNLOAD_TOOL), x+PAD, y+5)，
   标题和下面的目录行整体右移 20 px 让位。
2. **设置页版本隔离勾了会丢**：根因是 isolation 只是页内字段，init() 每次从 Config.defaultIsolation 重新读，
   切换页面 → host.rebuild() → 又读回旧值。现在点一下就写 Config + Config.save()；「全屏」按钮同样处理。
3. **下载进度日志没营养**：原来 InstallJob 把 Installer 每次进度回调的 detail 原样记进日志，
   而下载阶段的 detail 就是 "149 MB" 这种东西。改成 
oteProgress() 按 **阶段变化 / 每 10%** 记一条，
   文案是 下载资源文件 40%（1600/3985），MB 数不再进日志（进度条上本来就有）。
4. **取消 → 终止下载（二次确认）+ 完成后变「返回」**：
   DownloadPage.initProgress() 现在是三态按钮（终止下载 / 确认终止+不终止了 / 返回），
   返回时回到「下载前的界面」（picked != null ? DETAIL : LIST）。
   另外给 Page 加了 	ick() 钩子，MainScreen 每 tick 调一次，DownloadPage.tick() 在任务状态变化时重建按钮 ——
   不然按钮要等下一次 rebuild 才会从「终止下载」变成「返回」。
5. **删掉设置页那两行说明**（JVM 参数示例、版本隔离解释）。
6. **装完模组版本多出一个原版**：新增 Launcher.HIDDEN（ersions/<id>/.mcver-hidden）。
   InstallJob 在开装前记下 anillaExisted，装完若 loader != NONE 且原版是这次才装的，
   就给原版打隐藏标记：**文件不动**（加载器版本 inheritsFrom 还要它），只是列表里不显示。
   Launcher.installed() 过滤隐藏项，installedAll() 仍然全量（版本页底部显示「另有 N 个原版被加载器版本引用（已隐藏）」）。
   版本页还多了第 7 个按钮「隐藏/显示」，用来手动清掉以前多出来的原版。

### 实测

- gradle build --offline 通过（6s）。
- 无 UI 跑了一整套 InstallJob（1.21.3 + Fabric 0.20.1 + 改名 1.21.3-Fabric_test）：
  `
  installed = [1.21.4-Fabric_0.20.1, 1.21.4, 1.21.3-Fabric_test]
  hidden    = [1.21.3]
  all       = [1.21.4-Fabric_0.20.1, 1.21.4, 1.21.3-Fabric_test, 1.21.3]
  `
  日志也变成每 10% 一条：下载资源文件 10%（400/3985）…100%（3985/3985），不再刷 MB。
- 装完的那个 1.21.3-Fabric_test 真启动了一次：窗口出来，日志 Loading Minecraft 1.21.3 with Fabric Loader 0.20.1。
  → 说明隐藏原版不影响启动。
### 16.1 追补：用户要的是「文件夹里」也别多出来（不是只隐藏列表）

上一版只是把多出来的原版从列表里隐藏，磁盘上 ersions/1.20.1/ 还在。用户明确要求文件夹里也不该有。

新增 Launcher.flatten(gameDir, loaderId, vanillaId)：把继承链展开成**自包含**版本

- merged() 递归合并父版本 → 去掉 inheritsFrom、id 改成加载器版本名，写成自己的 json
- 原版的 1.20.1.jar 用 Files.createLink（同分区硬链接，不占额外空间；失败退回复制）搬到
  ersions/<加载器版本>/<加载器版本>.jar
- 自检 json 能解析、jar 到位，才 deleteTree(versions/1.20.1)
- 保守条件：**别的版本还继承着这个原版时不合并**（返回 false → 退化为隐藏）

InstallJob 装完自动调用；版本页最后一个按钮在新旧数据上都管用：

- 选中的是「被加载器版本继承的原版」→ 按钮显示 **合并**，点一下把原版并进去并删目录
- 否则还是 隐藏/显示

另外：版本页会**故意显示**这类被打过隐藏标记的原版（否则用户没地方点「合并」），启动页仍然不显示。

### 实测

`
== 安装前 versions/ ==  1.21.2 ...
安装日志：
  版本已命名为 1.21.2-Fabric_test
  已合并成单一版本：versions/1.21.2-Fabric_test（versions/1.21.2 已移除）
  安装完成: 1.21.2-Fabric_test
== 安装后 versions/ ==  只剩 1.21.2-Fabric_test（1.21.2 文件夹没了）
installed = [..., 1.21.4-Fabric_0.20.1, 1.21.4, 1.21.3-Fabric_test, 1.21.2-Fabric_test]
hidden    = []      ← 这次不需要隐藏了，原版目录是真的删掉了
`

合并后的版本真启动验证过：窗口出来，日志 `Loading Minecraft 1.21.3 with Fabric Loader 0.20.1`。
（顺带修了进度日志在多线程下乱序的问题：非递增的百分比直接丢弃。）

---

## 十七、2026-10-24 下午：三条现场反馈（含一个真闪退 bug）

### 17.1 设置 → 下载源：状态行压在输入框上

- 根因：`renderSources()` 里状态行的 y 是 `srcBottom() + 6`，而 `srcBottom() = contentBottom() - 42`，
  算出来正好是 `contentBottom() - 36` —— 和底部两个输入框（占 `-36 .. -20`）完全重叠。
- 修法：状态行改到 `contentBottom() - 16`（输入框下方，仍在面板内）。
- 同时按用户要求删掉底部那行提示「点一行即启用；镜像按 BMCLAPI 规则映射（/mc /maven /assets）」。

### 17.2 下载进行时「再装一个」按下去没反应

- 根因：按钮只做了 `view = LIST; rebuild();`，但 `init()` 里有
  `if (Jobs.hasJob() && Jobs.running() && view != PROGRESS) view = PROGRESS;` ——
  页面立刻被拨回进度页，用户看到的就是「按了没反应」。
- 修法：新增 `allowListWhileRunning`。点「再装一个」置 true，`init()` 就不再强制跳进度页；
  提交新任务时清回 false；列表页在后台有任务时多一个「◀ 回到进度」按钮，避免回不去。
- 注意：安装任务仍然只有一个槽位（`Jobs` 单例），后台在跑时点「开始安装」会提示先看进度页。

### 17.3 【真 bug】Forge 1.20.2 启动后窗口闪 1 秒就退

日志（`logs/mcver-launch-<id>-<ts>.log`）：

```
java.lang.module.ResolutionException: Module minecraft contains package net.minecraft.client,
  module _1._20._2 exports package net.minecraft.client to minecraft
  at cpw.mods.modlauncher.ModuleLayerHandler.buildLayer(ModuleLayerHandler.java:72)
```

- **根因**：Forge 1.20.2+ 的客户端代码是加载器自带的 —— 安装器会把原版 jar 展开成
  `libraries/net/minecraft/client/client-<ver>-srg.jar`，由 `BootstrapLauncher` 自己加载成 `minecraft` 模块。
  我们又按 Fabric 时代的习惯把 `versions/<id>/<id>.jar` 塞进了 `-cp`，
  它就被 Java 当成一个**自动模块**（文件名 `1.20.2.jar` → 模块名 `_1._20._2`），
  和 `minecraft` 模块抢同一个包 → 模块解析直接失败，主线程退出，窗口消失。
- **修法**：`Launcher.clientJarProvidedByLoader(libs, mainClass)`，两个条件**同时**满足才把原版 jar 排除：
  1. `mainClass` 以 `BootstrapLauncher` 结尾；
  2. `libraries/net/minecraft/client/` 下真的存在 `client-*-srg.jar`。
  两条都卡住是为了不误伤 Forge 1.20.1：它用的是 `cpw.mods.bootstraplauncher`，且没有 srg jar，
  客户端代码仍然只能来自原版 jar。
- **实测**（离线 Probe：core 单独编译 + 跑 `Launcher.plan()`）：
  `classpath_has_vanilla_jar = false`、`missing = []`、`warnings = []`；
  再用同一份 Plan 真启动 → `Setting user: Probe` / `Backend library: LWJGL 3.3.2` /
  一整套纹理图集创建成功，标题界面正常。
- 判别方法：看 `logs/mcver-args-*.txt` 的 `-cp` 里还有没有 `versions/1.20.2/1.20.2.jar`。

### 17.4 验证手法（这次新加的）

把 `core/*.java` 单独拷到一个临时目录 + 一个 `net.minecraft.client.MinecraftClient` 桩，
就能 `javac` 出一个不依赖 Minecraft 的探针，直接跑 `Launcher.plan()` 打印 classpath / missing / warnings，
需要时再 `--launch` 真起进程。比每次进游戏点面板快得多（core 层只有 `Config.dir()` 用到 MinecraftClient）。

### 17.5 设置页内存改了就回 4096 / 与启动页不同步

- 现象一：设置页把内存改成 8192，切到其他页再回来 → 又变 4096。
- 现象二：启动页那里的内存显示与设置页不一致。
- **根因**：设置页的输入框（内存/下载线程/JVM 参数/游戏参数/窗口宽高）**只有点「保存」才写 Config**，
  而 `init()` 每次都从 `Config` 重新取值填回输入框。所以不点保存就切页 = 刚改的丢掉，
  而 `Config.memMb` 默认就是 4096，看起来就像“自动变回 4096”。
- 启动页那边又正好相反：它的内存框 `setChangedListener` 只改了内存里的 `Config.memMb`，**没调 `Config.save()`**
  → 重启就回去了，两边也容易对不上。
- **修法**（统一成“写透”）：新增 `bindInt()` / `bindText()`，输入框一变就写 `Config` + `Config.save()`。
  - 数字框带范围保护（内存 256〜65536、线程 1〜64、宽 320〜7680、高 240〜4320），
    且**打字中途的中间值不写盘**（想打 8192 时敲出的 "8" 不会把内存改成 256）；值没变就不写盘。
  - 启动页的内存框也补上 `Config.save()`。
  - 「保存」按钮保留：它额外做一遍 clamp + 把规范化后的值回填到框里。
- 规矩（和 4.9 同源）：**页内变量只能当缓存，配置的真源永远是 Config，且改了就落盘**；
  否则一旦 `init()` 重读 Config，页内改动就被默默覆盖。

---

## 十八、2026-10-24 17:00–17:40：NeoForge 1.20.3 装不上

### 18.1 第一层原因：NeoForge 20.2+ 的安装器没有 `--installClient`

```
joptsimple.UnrecognizedOptionException: installClient is not a recognized option
  at net.minecraftforge.installer.SimpleInstaller.main(SimpleInstaller.java:93)
```

`--help` 列得很清楚，只有 `--installServer` / `--extract` / `--mirror` / `--offline` / `--debug`。
（Forge 49.0.2 for 1.20.3 **有** `--installClient`，所以这是 NeoForge 分支的差异。）

**修法**：新增 `LoaderInstaller.installViaInstallProfile()`——不再依赖安装器 CLI，直接按
`install_profile.json`（Forge spec=1）自己跑客户端处理器：

1. 从安装器 jar 里取出 `version.json` → 写到 `versions/<id>/<id>.json`；
2. 下 `version.json` 的 54 个运行库 + `install_profile` 的 182 个处理器依赖（`ensureMaven()`）；
3. 解 `data` 表：`[maven 坐标]` → libraries 下的文件，`/data/client.lzma` → 从安装器里解到 `.mcver-tmp`，
   `'字面量'` → 去引号；
4. 按 `processors` 跑（只跑 `sides` 为空或含 client 的）：MCP_DATA → DOWNLOAD_MOJMAPS → MERGE_MAPPING
   → jarsplitter(client) → ForgeAutoRenamingTool → binarypatcher，自动跳过 server-only 的 4 个；
5. 自检 `PATCHED`（`net.neoforged:neoforge:<ver>:client`）真的产出来了。

`installNeoForge()` 现在是：先试 `--installClient`，抛异常就自动转内置流程。

### 18.2 第二层原因（本机无法绕过）：NeoForge 的 maven 不可达

| 目标 | 结果 |
|---|---|
| `maven.neoforged.net`（真 IP 104.23.125.189 / 污染 IP 103.240.182.55）| 两个 IP 都 **连不上 443**（不是 DNS 问题，是链路被封）|
| `bmclapi2.bangbang93.com/maven/net/neoforged/...` → USTC 镜像 | 安装器 jar 200；`neoform-*.zip` **403**；`fancymodloader/events` **404** |
| `maven.minecraftforge.net` | 直连正常（安装器 5.5MB/5.3s）|

**结论**：NeoForge 的库只发在 `maven.neoforged.net`，镜像不全 → 本机无代理时装不完。
内置流程已经把能做的做完了（实测已走到“写出版本 json”后卡在 `fancymodloader:events` 下载 404）。

### 18.3 顺手修掉的真 bug：Forge 版本 id 命名变了

- 1.20.2：`forge-1.20.2-48.1.0`
- **1.20.3：`1.20.3-forge49.0.2`**（`<mc>-forge<ver>`，前面没有 forge- 也没有第二个连字符）

`installForge()` 以前直接 `return "forge-" + full` → 1.20.3 会返回一个不存在的 id，
后面的改名/隔离/合并全都会失败。现在统一用 `findFreshVersionId()`：装完去 `versions/` 里
找名字含该版本号、且 mtime 最新的那个目录。

### 18.4 实测

- `1.20.3` 原版 + **Forge 49.0.2**：通过 `installForge()` 装成功（id `1.20.3-forge49.0.2`），
  `Probe3 --launch` 真启动 → `Setting user: Probe` / `Backend library: LWJGL 3.3.2`。
- 注意：Forge 1.20.3 的 mainClass 是 `net.minecraftforge.bootstrap.ForgeBootstrap`，
  **带着原版 jar 也能启动**，所以 17.3 那个“排除原版 jar”的规则**只针对 BootstrapLauncher**，
  别把 ForgeBootstrap 一起算进去（加了反而可能坏）。
- NeoForge 20.3.8-beta：CLI 回退正常触发、版本 json 已写、处理器流程已进入，卡在库下载（18.2）。

### 18.5 下载列表不再给“已装过”的版本打勾

按用户要求去掉 `rowLabel()` 里的 `✔` 前缀（连同只为它服务的 `isInstalled()` 一起删了），
列表现在是 `版本号 · 类型 · 日期`。进度页的“✔ 完成 xxx”保留（那是任务状态，不是版本标记）。

---

## 十九、2026-10-24 18:00–18:10：NeoForge 1.20.3 最终装上了

用户提醒“PCL2 没开代理也能装成功”，顺着查出一个错判 + 两个真 bug。

### 19.1 我之前用 shell 测镜像，被 UA 误导了

BMCLAPI 对 **User-Agent** 敏感（同一文件不同 UA 实测）：

| UA | 结果 |
|---|---|
| `WaterCraftLauncher/1.0` / `mcver/1.0` / `PCL2/…` / `curl/8.4.0` | **200，真文件** |
| 浏览器 UA（含 Mozilla）| 200 但是 929 字节的“Verifying your browser”页 |
| 空 UA / Java 默认 UA | 403 |

我们代码本来就在 `Net.open()` 里写死了 `WaterCraftLauncher/1.0`，所以 **UA 不是我们的问题**。
当时我在 shell 里用 `Invoke-WebRequest`/无 UA 的 curl 测，才测出 403/验证页 → 得出了
“镜像没收录”的错误结论。**教训：测 CDN 行为必须带上启动器实际用的 UA。**

### 19.2 真 bug 1：`fancymodloader:events:1.0.16` 上游就是 404

NeoForge 20.3.x 所有 beta（3.3/3.4/3.6/3.7/3.8）的 version.json 都引用
`net.neoforged.fancymodloader:events:1.0.16`，而 **BMCLAPI（含 USTC 后端）、forge maven、central 全部 404**；
相邻的 `1.0.15` / `1.0.17` 都存在（14352 字节，同尺寸不同 sha1）。
→ 上游发布时就漏了/撤了这个版本。

**修法**：`ensureMaven()` 下载全失败时，若是三段式版本号，按 `+1..+3` → `-1..-3` 找相邻补丁版本，
下到**原定路径**（classpath 不变）。日志里会看到它降级的痕迹。

### 19.3 真 bug 2：data 表里的 maven 坐标是“产物路径”，不该下载

第一版把 `install_profile.json` 的 `data` 里所有 `[maven]` 都当成待下载输入，于是去下
`net.minecraft:client:1.20.3-…:slim` —— 这东西是 jarsplitter **要生成**的，永远 404。
现在 `resolveDataValue()` 只算路径不下载；只有在 **processor 参数**里出现的 `[maven]` 才真下载。

### 19.4 结果（实测链路完整）

```
安装器不支持 --installClient → 内置处理器流程
运行库就绪 54 个
MCP_DATA → DOWNLOAD_MOJMAPS → MERGE_MAPPING → jarsplitter(client) → FART → binarypatcher
安装完成: neoforge-20.3.8-beta
```

启动验证（`Probe3 --launch`）：`Setting user: Probe` / `Backend library: LWJGL 3.3.2`，
且 classpath 按规则排除了原版 jar（mainClass = `cpw.mods.bootstraplauncher.BootstrapLauncher`）。

### 19.5 PCL2 / HMCL 到底怎么做的

- 它们同样从 BMCLAPI 下（PCL2 日志能看到成片的 `bmclapi2.bangbang93.com/maven/net/neoforged/...`）；
- 它们也**不依赖安装器 CLI**，自己解析 install_profile 跑处理器（HMCL 的 ForgeNewInstallTask、PCL2 的安装模块）；
- 遇到缺件它们也会退版本/报错 —— PCL2 能装成是因为它装的是 **1.21.3 + NeoForge 21.3.100**（FML 5.0.8），
  那套 artifact 齐全；没碰到 1.20.3 那个没发出来的 `events:1.0.16`。

---

## 二十、2026-10-24 18:30：设置页「目录」输入框与按钮重叠

- 布局：`dirField` 宽 `w - 128` → 实际占 `[x+40, x+w-88]`，而「检测」按钮占 `[x+w-128, x+w-86]`、
  「设为目录」占 `[x+w-84, x+w]` → 输入框右边 40px 被「检测」盖住。
- 修法：右边两个按钮共占 130px，输入框改成 `w - 174`，「检测」左移到 `x + w - 130`。
  现在（contentW=316）：输入框 `x+40 .. x+258`，「检测」`262..304`，「设为目录」`308..392`，间隔都是 4px。

---

## 二十一、2026-10-24 18:35：账号页改成「列表中间 + 按钮底部 + 分页」

原来的布局是按 `contentY + 固定偏移` 算的：列表只画 `LIST_ROWS = 4` 行，名字框 / 登录按钮跟在列表下面。
账号一多，列表要么被截断要么把按钮顶到面板中间。

现在：

| 区域 | 位置 |
|---|---|
| 列表 | `contentY+2` .. `nameRowY-6`（高度 114 → **6 行**）|
| 名字输入框 + 「离线登录」| `contentBottom-72` |
| 微软登录 / 设为当前 / 删除 | `contentBottom-50` |
| 状态提示 | `contentBottom-22` |
| 分页 ◀ 1/2 ▶ | 按钮行最右侧（`x+w-72` / `x+w-18`）|

列表行数改成按可用高度算（`listRows()`），账号超过一页就分页；微软登录的设备码界面按钮也一并到底部。

---

## 二十二、2026-10-24 18:48：物品合成配方

之前 **没有配方**：物品只靠创造模式「工具」栏（`ItemGroupEvents`）和 `/give @s mcver:download_tool` 拿。

新增 `resources/data/mcver/recipe/download_tool.json`：

**最终版（用户指定）**：一桶水 → 1 个工具，桶自动返还：

```json
{
  "type": "minecraft:crafting_shapeless",
  "ingredients": [ { "item": "minecraft:water_bucket" } ],
  "result": { "id": "mcver:download_tool", "count": 1 }
}
```

**桶为什么不用写代码就会退**：原版 `Items.WATER_BUCKET` 注册时就是
`new Item.Settings().recipeRemainder(Items.BUCKET).maxCount(1)`
（反编译 1.21.4 的 `net/minecraft/item/Items.class` 确认，`lava_bucket` 同样），
合成时 `CraftingScreenHandler` 会把配料的 `getRecipeRemainder()` 退回背包。
蛋糕用三个牛奶桶能退回三个空桶就是这个机制。

### 22.1 坑：1.21.4 的 ingredients 必须写字符串，不能写 `{"item": ...}`

第一版写成 `"ingredients": [ { "item": "minecraft:water_bucket" } ]`，游戏日志报：

```
Couldn't parse data file 'mcver:download_tool' from 'mcver:recipe/download_tool.json':
DataResult.Error['List is too short: 0, expected range [1-9]']
```

正确写法（直接照抄原版 `data/minecraft/recipe/acacia_button.json`）：

```json
"ingredients": [ "minecraft:water_bucket" ]
```

shaped 的 `key` 也一样：`"#": "minecraft:acacia_planks"`，不是对象。
**验证配方格式最靠谱的办法：直接从 1.21.4 客户端 jar 里拆一个原版配方对照**
（`data/minecraft/recipe/*.json`，客户端 jar 里有）。

---

## 二十三、2026-10-24 21:05：产物改名 watercraftlauncher-…

用户要求：模组名叫 **Water Craft Launcher**（`fabric.mod.json` 的 `name` 早就对），
但**产物文件名**要改成 `watercraftlauncher-<modloader>-<version>-mc<mc_version>.jar`。

- `gradle.properties` 新增 `mod_loader=fabric`，`archives_base_name` 改成 `watercraftlauncher`；
- `build.gradle` 里用 `tasks.matching{...}.configureEach { archiveFileName = ... }`（jar / remapJar /
sourcesJar / remapSourcesJar 四个任务），**因为 Gradle 默认会把 version 拼到文件名最后**，
  光改 `archivesName` 会得到 `watercraftlauncher-fabric-1.0.0-mc1.21.4-1.0.0.jar`（版本重复）。
- 坑：`sourcesJar` 在配置阶段还不存在（`java { withSourcesJar() }` 后面才创建），
  用 `tasks.named("sourcesJar")` 会直接报 “Task with name not found” → 必须 `tasks.matching{}.configureEach`。
- 产物：`watercraftlauncher-fabric-1.0.0-mc1.21.4.jar`（218,856 字节）；
  部署时把旧的 `mcver-1.0.0.jar` 移走（同一 mod id 两个 jar 会冲突）。
- `settings.gradle` 的 `rootProject.name` 也改成 `watercraftlauncher`。
- 模组内部 id 仍是 **`mcver`**（改 id 会让 `config/mcver.json`、`/give mcver:download_tool` 全部失效）。

---

## 二十六、2026-10-25 凌晨：P0 验证矩阵（已开跑）

按 `PLAN.md` 的 P0 阶段，用离线探针（`ProbeInstall` + `ProbeLaunch`）把「代码写了」变成「实测可用」。
明细见 `VERIFY.md`。

### 26.1 已到 L2（装完 + 启动到 Setting user）

| MC | 加载器 | 版本 |
|---|---|---|
| 1.21.4 | Fabric | 0.16.x |
| 1.21.4 | Quilt | 0.20.0-beta.9 |
| 1.20.3 | Forge | 49.0.2 |
| 1.20.2 | Forge | 48.1.0 |
| 1.20.1 | **Forge** | 47.x |
| 1.20.3 | NeoForge | 20.3.8-beta |
| 1.20.1 | **NeoForge** | 47.1.84 |
| 1.20.2 | OptiFine | HD_U_I7_pre1 |
| 1.20.2 | Iris | 1.6.14 + Sodium 0.5.5 |

**6 个加载器全覆盖（L2）**，LiteLoader 仍缺。

### 26.2 本轮挖出并修掉的 4 个真 bug

1. `installNeoForge()` 自动选版直连 `maven.neoforged.net` → 国内 `SocketTimeoutException`；改走 `LoaderVersions` 的多源列表。
2. NeoForge 1.20.1 的 artifact 在 `net.neoforged:forge`，文件名 `forge-<mc>-<ver>-installer.jar`；现在两套路径都试。
3. 处理器 `ClassNotFoundException: ...ConsoleTool`——某些 profile 的 `classpath` 不列工具本体；现在强制把处理器 jar 加到首位。
4. **库下载慢到不可用**（180 个库×每个等 15s 超时）：加死主机黑名单（`maven.neoforged.net` 首次超时后直接跳过）+ neoforged 放到候选链最后 + **库并行下载（8 线程）**。
   → NeoForge 1.20.1 的 74 个库从「预计 45 分钟」降到「约 2 分钟」。

### 26.3 待续

老版本 Fabric（1.16.5 / 1.14.4）与快照（23w13a_or_b）尚未验证；LiteLoader 待决策。

### 26.4 第二轮（老版本）又修 3 个

| # | 现象 | 根因 |
|---|---|---|
| 5 | 1.12.2 报「找不到主类 @…\mcver-args.txt」| **Java 8 不支持 @argfile**（Java 9+ 才有）→ Java ≤ 8 直接拼命令行 |
| 6 | plan 里报 lwjgl-platform / jinput-platform 缺失 | 纯 natives 库本来就没普通 jar，不该计入 missing |
| 7 | **1.13~1.18 `UnsatisfiedLinkError: lwjgl.dll`** | 合并 json 时同版本「不含 natives」的条目挤掉了原版那份；且 natives 只解了第一个 jar |

新增到 L2：**Forge 1.12.2**（Java 8 + launchwrapper）、**Fabric 1.16.5**（natives 修好）。
累计 11 个组合验证通过。

---

## 二十四、2026-10-24 21:22：加载器 × 版本兼容规则表（LoaderSupport）

用户提出“搞 n.xx+ 就不用一个个版本支持”。实测后的结论：

| 加载器 | 能否用区间规则 | 数据 |
|---|---|---|
| Fabric | ✅ `>= 1.14` | 532 个游戏版本（48 正式版）|
| Quilt | ✅ `>= 1.14.4` | 441 个（44 正式版）|
| NeoForge | ✅ `>= 1.20.1` | 1.20.1/1.20.5/1.21.2/1.21.4/26.x 都有 |
| **Forge** | ❌ 必须精确集合 | 77 个 MC 版本，**缺口：1.16、1.17、1.20.5、1.21.2**，1.13 只有 1.13.2、1.14 只有 1.14.2+ |
| **LiteLoader** | ❌ 固定集合 | 16 个版本，2017-11-29 停更 |

所以落地为 `core/LoaderSupport.java`：**区间规则 + 精确集合 + 快照归为 MAYBE**。
`Verdict.OK / MAYBE / NO`，`reason()` 给界面一句话。快照/愚人节版：
Fabric/Quilt 为 MAYBE（它们真发快照构建），Forge/NeoForge 直接 NO。

接入：`DownloadPage` 选加载器时先查规则表，不支持的组合**不发网络请求**、直接状态栏报原因；
`startInstall()` 再卡一道。自检（ProbeSupport，16 个用例）全对。

剩下的全加载器工作：LiteLoader 还需 `Kind` + 安装实现（生成 version json + launchwrapper + tweakClass），
只有 16 个版本，已经在 `LoaderSupport.liteLoaderVersions()` 里备好白名单。

---

## 二十五、2026-10-24 22:30：多版本架构落地（单源码 + compat 覆盖）

用户要求“全版本全加载器”，并同意用复制多份项目的方式推进。实测定下来的做法：

### 25.1 验证：相邻版本差异极小

把项目复制成 `watercraft-launcher-1.21.1` 后，从 1.21.4 降到 1.21.1 只错两处：

1. `Item.Settings.registryKey(...)` 是 1.21.2+ 才加的 → 1.21.1 去掉；
2. `UseItemCallback` 返回值 1.21.1 是 `TypedActionResult<ItemStack>`，1.21.4 改成 `ActionResult`。

修完 `BUILD SUCCESSFUL`，产物 `watercraftlauncher-fabric-1.0.0-mc1.21.1.jar`。

### 25.2 但手工复制 N 份会失控 → 改成覆盖式

- `gradle.properties` 新增 `fabric_api_version` / `compat`；
- `build.gradle`：`-Pcompat=<name>` 时把 `src/compat/<name>/java` 加成源码目录，
  并**排除主源码里同名文件**（实测日志：`[compat] 覆盖 2 个文件`）；
- `src/compat/1.21.1/java/dev/mcver/{McVerMod,McVerClient}.java` 就是那两份差异文件；
- `scripts/build-all.ps1` 一键构建全部 target，产物拷到 `dist/`。
  实测：两个 target 都出 jar（mc1.21.4 / mc1.21.1）。
- ⚠️ PowerShell 5.1 按 GBK 读 .ps1，带中文注释的脚本**必须存成 UTF-8 带 BOM**，否则报“字符串缺少终止符”。

### 25.3 LiteLoader：决定暂不实现（有实测依据）

`dl.liteloader.com/versions/versions.json` 还能读（最后更新 2017-11-29，16 个版本），但 jar 下载：

- `repo.mumfrey.com` 的固定版位 **404**；
- 只有时间戳 snapshot 重定向能拿到，且**实测 8 KB/s**（1.68 MB 文件 60 秒只下了 470 KB）。

结论：技术可行但依赖一个正在死的仓库，价值极低。白名单（`liteLoaderVersions()`）和规则留在代码里，以后要补直接接。

### 25.4 顺手修掉的真 bug：NeoForge 前缀推断

`mc.split("\\.")` 取第 2、3 段拼前缀的写法，遇到新命名 `26.1` 直接抛“无法推导前缀”。
新增 `LoaderVersions.neoPrefix()`：`1.20.1→47.`、`1.20.3→20.3.`、`26.1→26.1.`，`installNeoForge()` 也改用它。

- 1.21+ 数据包目录是 **`recipe`（单数）**，1.20.x 及以前是 `recipes`；本项目 `depends.minecraft >= 1.21`，所以用单数。
- 配方是数据包内容，**旧存档不用重开**，重启游戏就生效。
- 想换配方就改这一个 JSON（`pattern` + `key` + `result`）。

---

## 二十七、2026-10-25 17:0x：出报告 + 打包移交

用户要求「写一下报告，然后打包」。本轮**没有改代码**，只做文档整理、重新构建、打包。

### 27.1 报告

- `PROGRESS.md` 重写为 **「进展报告：截至 2026-10-25」**（提炼层）：真实支持表（11 个 L2 组合 + 证据）、两天做的事、7 个 bug 的根因表、关键结论、产物路径、诚实缺口、阶段计划、待拍板事项。
- `VERIFY.md` 是原始验证记录，`REPORT.md`（本文件）是流水账，三者分工写明在 PROGRESS 开头。

### 27.2 重新构建

`scripts/build-all.ps1` 跑两个 target，均 `BUILD SUCCESSFUL`：

| target | 产物 | 字节 |
|---|---|---|
| 1.21.4 | `watercraftlauncher-fabric-1.0.0-mc1.21.4.jar` | 226,096 |
| 1.21.1 | `watercraftlauncher-fabric-1.0.0-mc1.21.1.jar` | 225,954 |

部署三处已核对一致（SHA256 `A78A7E70…3A16`）：`mod/build/libs` → 项目根 → `.minecraft/versions/1.21.4-Fabric 0.19.5/mods/`。
⚠️ 1.21.4 的 `UP-TO-DATE`（源码 02:04 之后没动过），**1.21.1 是重新编译的**——之前 dist 里那份（10-24 22:38）已过期，这次补上了。

### 27.3 打包

新增 `scripts/pack-release.ps1`（可复跑），产物：

- **`D:\Development\watercraft-launcher-2026-10-25.zip`**，57 个文件，**0.84 MB**。
- 含：源码（29 + compat 2）、资源、`dist/` 两个 jar、根目录 1.21.4 jar、`build.gradle` / `settings.gradle` / `gradle.properties` / wrapper、4 个脚本、6 份文档（README / HANDOVER / PLAN / PROGRESS / REPORT / VERIFY）、`.gitignore`。
- 排除：`.tools`（274 MB Gradle）、`testgame`（727 MB）、`_keep`、`mod/build`、`mod/.gradle`、`mod/run`、`mod/config`（含 token）、`logs`、`*.bak*`、`config/mcver.json`。
- 接力方需要自备 **JDK 21 + Gradle 8.12**（HANDOVER §1.2 写了三条获取路径，`.tools` 故意不打进包）。

## 二十八、2026-10-26 上午：mod 本体新增 1.20.1 target（单源码 + compat，未复制工程）

上一轮 P0 收尾后 PLAN 的 P2 是「老版本 target，顺序 1.20.1 → 1.19.4 → ...」。这一轮把 **1.20.1** 做完了，
按既定纪律走 compat 覆盖，**没有复制项目**：主源码只有 4 个文件动了，compat 层 3 个 java + 1 个资源。

### 28.1 真实差异（编译器的判决，不是猜的）

先用 `-Pminecraft_version=1.20.1` 硬编一次，拿到的错误清单就是全部差异：

| # | 位置 | 1.21.4 | 1.20.1 | 处理 |
|---|---|---|---|---|
| 1 | `McVerMod` | `Identifier.of(ns, path)` + `Item.Settings.registryKey(...)` | 只有 `new Identifier(ns, path)`，没有 registryKey | compat 覆盖 |
| 2 | `McVerClient` | `UseItemCallback` 返回 `ActionResult` | 返回 `TypedActionResult<ItemStack>` | compat 覆盖 |
| 3 | `Screen.renderBackground` | `(DrawContext,int,int,float)` | `(DrawContext)` | 抽出 `ChromeScreen` 基类，按 target 覆盖 |
| 4 | `Screen.mouseScrolled` | 4 个参数（带横向） | 3 个参数 | 同上 |
| 5 | `LaunchPage` 里的 `Session` | `client.session.Session` | `client.util.Session` | 不 import 具体类，改用 `var`（两个版本方法签名一致） |
| 6 | `Util.getOperatingSystem().open(Path)` | 只有 `Path` 重载 | 只有 `File/URI/URL/String` | 新增 `core/Open.java`，直接走系统命令（explorer/open/xdg-open） |
| 7 | 配方目录 | `data/<ns>/recipe/`（单数，`result.id`） | `data/<ns>/recipes/`（复数，`ingredients[].item`） | compat 资源层 `src/compat/1.20.1/resources/` |
| 8 | 字节码 | release 21 | release 17（1.20.1 跑在 Java 17） | `gradle.properties` 新增 `java_release` |

第 3、4 条是这一轮唯一的结构性改动：把「Screen 里跨版本改过签名的那两个钩子」抽到基类 `ChromeScreen`，
主源码和 compat 各写一份 20 行的基类，**300 行的 MainScreen 不用复制**。

### 28.2 顺手修掉的两个隐患

- `fabric.mod.json` 里的 `minecraft` / `java` 依赖以前写死 `>=1.21` / `>=21`。现在按 target 自动算，
  并且**卡上界**：`>=1.20.1 <1.20.2`、`>=1.21.4 <1.21.5`。理由是相邻补丁版本确实会改 API
  （1.21.2 就改了 `UseItemCallback` 的返回类型），宁可让加载器挡住，也别让玩家进游戏后崩。
- 构建脚本里 `-Pjava_dep=>=17` 这种值在 Windows 下会被 shell 的 `>` 吃掉（实测变成空字符串，
  jar 里 `"java": ""`）。改成从 `minecraft_version` + `java_release` 在 build.gradle 里算，命令行不再传这些值。

### 28.3 验证（L2 口径：装完 + 启动到 Setting user）

`dist/watercraftlauncher-fabric-1.0.0-mc1.20.1.jar`（245,401 字节，class 版本 61）丢进
`testgame/versions/V1.20.1-FABRIC/mods/`（Fabric API 0.92.2+1.20.1 一起放），用生产代码 `Launcher` 起真进程：

- `[probe] requiredJava=17` → 实际用 `jdk-17.0.10` 起（版本要求的 Java 自动选对了）；
- `Loading Minecraft 1.20.1 with Fabric Loader 0.20.1`、`- mcver 1.0.0` 在 mod 列表里；
- `mcver: 物品已注册 mcver:download_tool`、`mcver: 客户端初始化完成`，**没有 Mixin / 注册 / 模型报错**；
- `[probe] OK 到达 Setting user`。

结论：1.20.1 这个 target **装得上、加载得了、物品注册成功**。
GUI 截图没拿到（这台机器上无头起游戏时卡在 atlas 创建之后，和 mod 无关），**界面级验证留给实机**。

### 28.4 产物与脚本

- `scripts/build-all.ps1` 的 `` 加了一行（`java = '17'`）；`-Pjava_release` 统一传进去。
- 三个 target 全绿：`dist/` 下 mc1.20.1 / mc1.21.1 / mc1.21.4 各 245 KB 左右。
- 新增文件：`core/Open.java`、`ui/ChromeScreen.java`、`src/compat/1.20.1/{McVerMod,McVerClient,ui/ChromeScreen}.java`、
  `src/compat/1.20.1/resources/data/mcver/recipes/download_tool.json`。

### 28.5 下一个 target 的预估

1.19.4 / 1.18.2 都在「1.20.1 之前」，主要差异会集中在 : `Screen` 的 `renderBackground`（更老的是
`renderBackground(DrawContext)`，与 1.20.1 一致）、`RenderSystem`/`DrawContext` 的 `drawItem` 行为、
以及 1.19.4 之前 `KeyBinding`/`InputUtil` 的包名没变但 `UseItemCallback` 还是 `TypedActionResult`。
预计 compat 仍是 3 个文件上下；1.17 及更早要额外处理 `Text`/`TextRenderer` 与 `ItemGroup` 的差异，需要先探一次再定。

## 二十九、2026-10-26 10:20–11:05：唯一的失败（26.1 Forge）如何处理 + 挖出一个假通过 bug

用户问「没有失败需要处理吗」，于是把 CSV 按「每个组合最后一行」重算了一遍：46 OK，1 失败（26.1-FORGE）。
本以为是 26.x 要 Java 25 的老问题，实测不是——是另外两件事。

### 29.1 26.1 Forge 的真实根因：镜像回 403，错误页被当成 zip

`install_profile.json` 里 `de.oceanlabs.mcp:mcp_config:26.1-20260326.105027@zip` 这一条，
BMCLAPI 302 → `mirrors.cernet.edu.cn` → `cernet.mirrors.ustc.edu.cn` 最后一跳回 **403**，
而那个 403 的响应体（14,163 字节）被当成 zip 落盘。清单里明确写了 `size=407294 / sha1=7173fa93…`，
但坏文件是**安装器自己**在 `--offline` 里写的，预取阶段来不及校验。

修法（两层）：
1. `installForge` / `installNeoForge` 第一次离线失败后，先 `purgeInvalidLibraries()` 按清单把不符合
   sha1/size 的库文件删掉，再重新预取一遍，然后重试一次离线；还不行才退 `--mirror`。
2. 把「读两份清单收集库」抽成 `collectAllFetches()`，预取和清理共用同一套判定，避免两边不一致。

然后直接重跑：26.1 Forge **installed=OK**。顺手把从来没跑过的 26.1 NeoForge 也跑了 —— 也是 OK。
也就是说 **26.x 现在 3 个加载器全通**（Fabric 之前就过了，Java 25 运行时是启动器自动下的）。

### 29.2 但重跑暴露一个更严重的问题：假通过

26.1 Forge 重跑后结果里 `resultVersionId=26.1`（原版 id），启动日志 `mainClass=net.minecraft.client.main.Main`。
也就是说**装的是原版，启动的也是原版**，日志里照样有 `Setting user`，按原口径算「L2 通过」。

根因在 `InstallJob`：重写「按版本要求选 Java」那段循环（bug #6）时，把原来的
`if (id != null) result = id;` 弄丢了。于是 `result` 停在原版 id，下一步「改名」把
**`versions/<mc>` 原版目录**改成了 `V<mc>-<KIND>`，加载器目录另在一旁。

自查全部 47 个「带加载器」的版本目录主类，中招 7 个（都是 10-25 20:30 之后跑的）：
1.7.10 / 1.10.2 / 1.12.2 Forge、26.1 Forge、26.1 NeoForge、1.20.1-FABRIC-IRIS、1.21.4-FABRIC-IRIS。

修法：
1. `InstallJob` 补回 `result = id;`（带注释说明为什么不能漏）。
2. `verify-matrix.ps1` 加硬断言：非 NONE 组合若 `mainClass=net.minecraft.client.main.Main` → 直接判 FAIL
   （信息：「假通过：主类是原版 Main」）。
3. `findFreshVersionId` 不再拿 MC 版本号当 hint（`versions/26.1` 也含 `26.1`），改以
   `install_profile.json` 的 `version` 字段为准。
4. 7 个中招组合逐个 `-Force` 重装复验，现在 `version` 列都是真实加载器 id
   （`26.1-forge-62.0.9` / `neoforge-26.1.0.19-beta` / `1.7.10-Forge10.13.4.1614-1.7.10` …）。

### 29.3 现在的口径

- `verification/results.csv` 按最后一行算：**48/48 L2，零失败**。
- 另外做了一次独立审计：47 个带加载器的组合，版本 json 主类**全部**是加载器类
  （KnotClient / BootstrapLauncher / ForgeBootstrap / modlauncher / launchwrapper），没有一个是原版 Main。
- 教训写进 PROGRESS §3 与 VERIFY §2.1：**只看 `Setting user` 不足以判定启动成功**。

## 三十、2026-10-26 11:05–11:45：1.19.4 target —— 被 DrawContext 拦住的不是三条街，是一条河

用户说「26.x 可以先不管」，于是继续 P2 的下一个目标：1.19.4。

### 30.1 硬编译：37 个错误，同一个原因

按老规矩先直接编译（不加 compat），让编译器说话。37 个错误全部指向
`net.minecraft.client.gui.DrawContext` 找不到符号 —— 这个类是 **1.20 才引入的**，
1.19.4 及更早的渲染是 `MatrixStack` + `VertexConsumerProvider`。

也就是说 1.17~1.19 整条线都不是「改两个签名」的量级，渲染层要换一套。
之前 PLAN 里估的「1.19.4 compat ≤ 3 个文件」是错的，得改。

### 30.2 做法：把三个绘制原语收口到 `ui/Ctx`

先数了一下 UI 代码到底用了哪些 DrawContext 方法：`fill` 25 处、`drawText` 6 处、`drawItem` 3 处，
就这三个。于是新增 `dev.mcver.ui.Ctx`：

- 主源码（1.20+）：字段就是 `DrawContext`，三个方法直通；
- `src/compat/1.19.4`：字段是 `MatrixStack`，
  `fill` → `DrawableHelper.fill`、`drawText` → `TextRenderer.draw`（收的是 **Matrix4f**，
  要 `matrices.peek().getPositionMatrix()`）、`drawItem` → `ItemRenderer.renderInGui`。

同时把 `render` / `renderBackground` 两个 override 从 `MainScreen` 上提到 `ChromeScreen`：
子类只实现 `drawChrome(Ctx)` 和 `renderContent(Ctx, ...)`，不再出现 MC 的版本类型。
这一步是关键 —— 否则 MatrixStack 进来，300 行的 MainScreen 每个旧 target 都要复制一份。

页面文件（7 个）只是 `DrawContext ctx` → `Ctx ctx` + 删 import，机械替换。

1.19.4 三个钩子的真实签名（用 javap 对着 remap 后的 jar 确认，不靠记忆）：
- `render(MatrixStack, int, int, float)`
- `renderBackground(MatrixStack)` —— **只有 1 个参数**（1.20 是 DrawContext 版，1.21.2 又加 mouseX/mouseY/delta）
- `mouseScrolled(double, double, double)`

另外 compat 里补了 `McVerMod`（`new Identifier` + 无 `registryKey`）、`McVerClient`
（`TypedActionResult<ItemStack>`）、`data/mcver/recipes/download_recipe.json`（复数目录）。

### 30.3 顺手把探针升级成能验界面

`ProbeLaunch` 之前到 `Setting user` 就 kill 进程，界面根本没机会验证。加了可选第 4 个参数 `waitFor`：
到了 Setting user 之后继续等日志里出现指定标记。`MainScreen` 在 `drawChrome` 和首帧
`renderContent` 各记一条一次性日志（`screen=MAIN chrome 已绘制` / `首帧渲染完成`），
`debugDump` 也顺便写进游戏日志（以前只写 mcver-debug.txt）。

### 30.4 结果

- 四个 target 全绿：1.19.4 / 1.20.1 / 1.21.1 / 1.21.4，各约 247 KB，class 版本 61/61/65/65。
- 1.19.4 运行证据：`Loading Minecraft 1.19.4 with Fabric Loader 0.20.1`、`- mcver 1.0.0`、
  `物品已注册`、`客户端初始化完成`、`screen=MAIN` → `chrome 已绘制` → `首帧渲染完成`、
  6 个页轮着切都正常、无异常堆栈。
- **像素级确认没拿到**：同一套钩子跑 1.21.4 能截到面板（`C6C6C6` 命中 6 万+像素，中心点就是面板色），
  但 1.19.4 / 1.20.1 在这台机器上截出来是启动画面（1.19.4 那块红橙 splash），说明是无头环境的问题，
  不是代码 —— 跟上一轮 1.20.1「卡在 atlas 之后」是同一个坑。界面最终确认留给实机。

## 三十一、2026-10-26 11:40：用户报「面板变透明」—— 是我判断错了，真 bug

用户实机起 1.20.1 / 1.19.4，发现 WCL 界面**背景板没了**（只剩按钮和字，底是透明的）。

我上一轮把这些截图判成「无头环境限制」，是错的。用 javap 对着 remap 后的 jar 一看就清楚了：

| 版本 | `Screen.render` 会不会自己调 `renderBackground` |
|---|---|
| 1.21.1 / 1.21.4 | **会**（字节码第 6 条就是 `invokevirtual renderBackground`）|
| 1.20.1 / 1.19.4 | **不会**（只遍历 drawables）|

而 `drawChrome`（面板底、标题栏、导航分隔线）被我写在 `renderBackground` 的 override 里。
于是在 1.20.1 / 1.19.4 上 **根本没人调 renderBackground → 面板一次都没画**；
1.21.x 上因为父类会自动调，所以一直正常。这完全对得上「1.21.4 截得到面板、1.20.1/1.19.4 截不到」。

修法（只动两个 compat 文件）：`render` 里手动按顺序来 —— 原版底 → 面板 → 控件 → 页内容：

.. code:: java

    renderBackground(ctx);            // 父类版：世界/泥土底；我们的 override 接着画面板
    super.render(ctx, mouseX, mouseY, delta);   // 只画控件，不会再调 renderBackground
    renderContent(Ctx.of(ctx), ...);

同时修了截图钩子的两个坑（否则验证永远看不见 UI）：
1. `MCVER_SHOT` 现在等首帧内容渲染完才抓（无头起游戏时 atlas 要 2~3 秒，早抓全是启动画面）；
2. 抓完记一条 `mcver: shot saved <name>`，探针可以拿它当 waitFor，避免「刚抓完就被 kill」。

### 验证（真实窗口 PrintWindow 抓图，30 秒后）

| 版本 | 窗口中心点 | C6C6C6 命中（4px 采样）|
|---|---|---|
| 1.19.4 | C6C6C6 | 15349 |
| 1.20.1 | C6C6C6 | 15061 |
| 1.21.4 | C6C6C6 | 15092 |

三个都一致，面板画出来了。截图：`logs/build/hold-1.19.4.png` / `hold-1.20.1.png` / `hold-1.21.4.png`。

### 教训（比 bug 本身重要）
- **用户看到的才算验证**。我拿「日志里有 screen=MAIN」当界面通过的证据，太弱了；
  `screen=MAIN` 只证明 `init()` 跑过，跟「画出来没有」是两件事。
- 截图/像素判据要用**真实窗口**（PrintWindow）或至少等首帧渲染完；
  `ScreenshotRecorder` 在无头环境早期抓的是启动画面，很容易把人骗过去 —— 这次骗了我两轮。
- 「父类会不会调用某个钩子」是**版本行为**，必须 javap 看字节码，不能按「一般都会」想当然。

## 三十二、2026-10-26 12:10：1.18.2 target

按「先硬编译，让编译器列差异」的流程走。1.18.2 的渲染差异跟 1.19.4 同源（都是 MatrixStack 时代），
真正多出来的是三样**满地都是的小 API**：

- `Text.literal` 没有（1.19 才加）→ UI 里 **92 处**；
- `ButtonWidget.builder(...).dimensions().build()` 没有（1.19 才加）→ **72 处**；
- `ItemGroupEvents` 没有（Fabric API 0.77 只有 `FabricItemGroupBuilder`）→ 创造栏得自建一个。

做法是不让页面代码分叉：新加两个适配层 `ui/Txt` / `ui/Btn`（main 里直接转给真 API，
1.18.2 的 compat 里换成 `Text.of` / `new ButtonWidget(...)`），再用脚本
`scripts/rename-shims.mjs` 把 164 处调用点机械替换掉。加上 `core/Compat` 收口
`Registries` vs `Registry`、`Session#getUuidOrNull` vs `getUuid`，剩下的都是 Ctx/ChromeScreen 一层的事。

### 界面验证做成了脚本
上一轮靠手工拼 PowerShell 抓窗口，容易看天吃饭。这轮固化成 `scripts/verify-gui.ps1`：
起真进程 → **等日志出现 `screen=MAIN chrome`**（不再只看窗口出现）→ 再等 4 秒 → PrintWindow →
数 `#C6C6C6`。顺手会缺什么补什么（往测试实例里拷 jar / 从 Modrinth 下对应 fabric-api）。

跑出来五个目标一致：

| 1.21.4 | 1.21.1 | 1.20.1 | 1.19.4 | 1.18.2 |
|---|---|---|---|---|
| 15092 | 15092 | 15061 | 15349 | 15357 |

（命中数 = 4px 采样里面板色的个数，中心点全是 `C6C6C6`，children 全是 17。）

### 教训
- 「等窗口出现」不等于「界面画完了」：1.18.2 第一次抓就抓到全景背景（`E45962`），
  因为窗口 10 秒就出来，而面板要等 atlas + 全景背景就绪。判据要绑**产品特征**（日志里的绘制标记 / 像素），不要绑时间。
- 老版本移植的成本曲线是**反直觉**的：渲染层（Ctx）一次就够，反而是 `Text.literal` 这种
  一句话的 API 散落在 92 个地方，最费工。所以这类「到处都用」的 API 一定要先做适配层，再做下一个 target。

## 三十三、2026-10-26 12:25：1.17.1 target（六个包）

1.17.1 复用 1.18.2 的全部 shim，只要改三个钩子名：`onClose` / `isPauseScreen` /
`MatrixStack.Entry#getModel()`。顺手把 `close()` / `shouldPause()` 从 MainScreen 上提到 ChromeScreen，
并加一个跟版本无关的 `closeSelf()` 给 UI 调 —— 这样「关界面」这件事以后不会再影响任何页面代码。

### 一个只有 1.17.1 会暴露的 bug
`fabric.mod.json` 依赖写死 `"fabric-api": "*"`。但 Fabric API 在 1.19 之前的 **mod id 是 `fabric`**
（1.17 的 bundle 里就是 `"id": "fabric"`）→ 启动直接 HARD_DEP_NO_CANDIDATE。
现在按 minecraft_version 推 id（<1.19 → `fabric`），1.18.2 的 0.77 同时 provides fabric，两边都通。

**教训**：依赖 id 也会改名。这种「同一个东西在不同年代叫不同名字」的坑，只有真的把老版本跑起来才会遇到；
规则表/文档都救不了，只能硬跑。

### 顺带的意义
1.17.1 的实例不是手工摆的，是用**我们自己的安装器**现装的（`ProbeInstall 1.17.1 FABRIC`，
跑生产代码 `InstallJob`，自动挑了 `jre-16.0.1-full`）。等于把「安装链路」又端到端验了一遍。

六目标像素验证：15092 / 15092 / 15061 / 15349 / 15357 / 15357，中心点全 `C6C6C6`，children 全 17。

## 三十四、2026-10-26 12:40：1.16.5 target（编译 + 界面都过，但卡在 Java 版本门槛）

1.16.5 的渲染层跟 1.17.1 一样（compat 直接抄），真正多出来的是一圈**周边 API**：
slf4j 没了（这年代用 log4j2）、`addDrawableChild` → `addChild`、`clearChildren` → 直接清 buttons、
`setScreen` → `openScreen`、`isOf` → `getItem() ==`、`ScreenshotRecorder` → `ScreenshotUtils`。

收口方式：新增 `core/Log`（两个实现：slf4j / log4j2，都是 `info/warn/error/debug(String, Object...)`），
`ChromeScreen` 加 `addWidget` / `clearWidgets`，`Compat` 加 `saveScreenshot`。
一共只动 9 个调用点，页面代码一行没改。

### 结果
`dist/watercraftlauncher-fabric-1.0.0-mc1.16.5.jar` 编译通过，界面也验过（19754 命中，中心 C6C6C6，children 17）。
**但**：原版 1.16.5 声明 javaVersion=8，玩家默认拿 Java 8 起，而我们的 jar 是 Java 17 字节码
（`Files.writeString` / `isBlank` / `var` / `List.of` 一百多处）→ loader 直接
`HARD_DEP_INCOMPATIBLE_PRESELECTED` 把 mod 挡掉。把实例钉到 Java 17 就一切正常。

### 教训
- 「编译通过 + 界面能画」还不够，**运行时的 Java 门槛**也是一道门。这次的证据链差一点就停在「编译成功」。
  探针第一次起 1.16.5 用的就是 jre-1.8，日志里那句话才把真相说出来。
- 老版本移植真正的成本不在渲染，而在**这些零碎的基础设施**（日志库、控件 API、Java 版本）。
  Java 8 兼容这一刀（~117 处）现在是 1.16.5 和 1.12.2 的共同前置，值得单独当一件事做，而不是顺手。

七目标像素验证：15092 / 15092 / 15061 / 15349 / 15357 / 15357 / 19754，中心全 `C6C6C6`。

## 三十五、2026-10-26 13:20：Java 8 源码兼容（用户选了「做」）

用户对上一轮留的选择题回答「1」= 做 Java 8 源码兼容。这是本工程目前为止**改动面最大**的一次重构：
主源码整体降到 Java 8 语言级，七个 target 共用一份、没有分叉。

### 改了什么
- 新增 `core/Jdk`：`writeString/writeAppend/readString/readAll/readAllText/pid/currentExe/isBlank/urlEncode/emptyList/emptyMap`。
  把 JDK 9+ 的便捷 API 全部收口。
- 9 个 record → final class（字段 + 同名无参方法 + equals/hashCode/toString），调用点零改动。
- 14 个箭头 switch → 经典 switch / if 链；8 个 switch 表达式改成先赋值再 return。
- **`java.net.http.HttpClient` 全部换成 `Net`**（HttpURLConnection），Auth 的 POST 也搬过去
  （`Net.postForm/postJson/getJson`）。
- 修掉 `readAllBytes`、`Path.of`、`Process#pid()`、`ProcessHandle`、`URLEncoder(Charset)`、
  `Matcher.appendReplacement(StringBuilder)`、`Optional.isEmpty`、文本块。

### 验证
| 层次 | 证据 |
|---|---|
| 字节码 | mc1.16.5 = 52（Java 8）；1.17.1 = 60；1.18.2/1.19.4/1.20.1 = 61；1.21.x = 65 |
| 真机 | 1.16.5 + jre-1.8 → 无 HARD_DEP，到 `Setting user`，物品注册 / screen=MAIN 正常 |
| 像素 | 1.16.5 Java 8 跑出来 19754 / 中心 C6C6C6 / children 17，**和 Java 17 那轮数字一致** |
| 七目标 | 15092 / 15092 / 15061 / 15349 / 15357 / 15357 / 19754 全绿 |
| 安装链 | 新 `Net` 装 1.20.1+Fabric 一遍过（manifest→66 库→3598 资源→loader→改名→隔离）|
| HTTP 往返 | 本地回环服务验 postForm/postJson/getJson：方法/Content-Type/Bearer/body 全对 |

### 教训
1. **record 改 class 时，访问器方法不能省**：原代码里 `found.exe` 和 `found.exe()` 两种写法混用，
   生成的类必须**同时**提供 public final 字段和同名方法，否则要改一大堆调用点。
2. **局部 record 不能加 static**：`JavaRuntime.Job` 在方法体里，加 static 直接编不过。
3. **脚本批量改语法要防两个坑**：吃掉行尾 `\n` 会让下一行匹配不到（用 lookahead）；
   CRLF/LF 不统一会让 `\{\n` 一个都匹配不上。
4. **重构 HTTP 层之后必须跑一遍安装链路**：编译通过 ≠ 能下载。这次靠 smoke 安装 + 本地回环往返兜住了。
5. `--release 8` 报 `target value 8 is obsolete` 是正常警告，不是错误。

### 遗留
- 48 组合的安装矩阵是这轮重构之前跑的。安装链路的**主路径**已用 smoke 验过，
  但没重跑全矩阵 —— 如果之后发现某个加载器装的路径有 HTTP 相关怪问题，先怀疑这次重构。

## 三十六、2026-10-26 16:10：Fabric 覆盖到 1.3（Legacy Fabric）+ Net 看门狗

### 起因
Java 8 兼容做完后，下一步本来是 1.12.2 的 UI。查了一下发现更值得先做的一件事：
**Fabric 官方从 1.14 才有**（`meta.fabricmc.net/.../loader/1.12.2` 直接 400），
所以下载页里 1.13 及以前 + Fabric 一直是「不支持」。但 **Legacy Fabric** 支持 1.3～1.13.2。

### 改动
- `core/FabricMeta`（新）：1.13 及以前走 legacy meta/maven，其余走官方；Legacy 没有 BMCLAPI 镜像。
- `LoaderVersions.fabric` / `LoaderInstaller.installFabric`：base 全走 `FabricMeta`；
  Legacy 时跳过官方 fabric-installer（它对 legacy 只会 400）。
- `LoaderSupport`：Fabric 规则 = `>=1.14 || >=1.3`。
- `writeProfile`：**支持 `natives` 映射**（真 bug，见下）。
- `Json.objOrNull`：顶层 Json 补了这个方法（原来只有 `Installer.Json` 内部类有）。

### 两个真 bug（都是自己踩出来的）
1. **`lwjgl-platform` 没有 plain jar**：Legacy profile 里它是 `natives` 库，只有
   `-natives-windows.jar`。`writeProfile` 原来直接拼 `<artifact>-<version>.jar` → 404 →
   `下载库失败` → 回退到官方 installer → 400 → 整个安装失败。
   修法：带 `natives` 的库只下 classifier 包、解压到 `versions/<id>/natives`，不进 classpath。
2. **`Net.download` 会无限卡死**：重跑矩阵时 1.20.5 卡了**一小时**，`.part` 停在 9.5 MB。
   `setConnectTimeout` 管不住 DNS，`setReadTimeout` 管不住半死连接。
   修法：传输放工作线程 + `join(max(90s, connect + 2*read))` 硬超时，到点 `disconnect()` 抛错让上层重试。

### 顺手修的基础设施
- `scripts/build-probe.ps1` 当时是坏的（1.16.5 那轮加的 `core/Compat.java` / `core/Log.java`
  引了 net.minecraft / slf4j，探针环境没有）→ 显式排除，两家都不被 core 引用。
- 新增 `scripts/build-probe-alt.ps1`（输出 `tools\probe\out-alt`），矩阵跑着也能编探针验新功能。

### 验证
- `FABRIC x 1.12.2 => OK`（以前 NO）
- 安装：`ProbeInstall testgame122 1.12.2 FABRIC` → loader 0.20.1 / 11 个库 / natives 1 / `complete=true`
- 启动：`ProbeLaunch` → `KnotClient` / `jre-1.8` / `OK 到达 Setting user`
- 全量矩阵重跑中（`-Force`，之前那轮因为 1.20.5 卡死作废）

### 教训
1. **别假设「profile 里的库都有 plain jar」**：natives 库只有 classifier 包。看 `natives` 字段。
2. **HTTP 超时不是万能的**：DNS 解析不受 connectTimeout 约束，得靠「整体硬超时 + 放弃线程」兜底。
3. **工具脚本也会悄悄过期**：`build-probe.ps1` 半年前就编不过了，因为没人重编。验证流程里该加「探针能不能重编」这一条。
4. **1.12.2 的 mod 本体不是重写**：26 个 MC 类里 16 个同名（legacy yarn 用的是现代命名风格），
   缺的主要是渲染（无 MatrixStack）、注册表、控件基类。spike 已编译 + remapJar 通过。
   真障碍是工具链：loom 1.16 要 Gradle 9，而 Legacy API 聚合包是 loom 1.15.3 打的。

## 三十七、2026-10-26 18:4x：1.21.9 / 1.21.10 / 1.21.11（10 个 jar）

### 决策
用户回「你决定」。选最新三个正式版：玩家最多，而且不用碰 1.20.5 的组件系统断层。

### 结果
- **一份 compat 盖三个版本**（`src/compat/1.21.9`，目录名取这一代最老的版本号，
  里面写了 README 说明为什么能共用）。
- API 差异只有 3 处：`Screen#keyPressed(KeyInput)`、`KeyBinding.Category`、`screenshot` 多一个 int。
- 像素验证：1.21.9 = **15097** / 1.21.10 = **15096** / 1.21.11 = **15096**，
  中心全 `C6C6C6`，`children=17`。
- `dist/` 现在 **10 个 jar**（1.16.5 / 1.17.1 / 1.18.2 / 1.19.4 / 1.20.1 / 1.21.1 / 1.21.4 / 1.21.9 / 1.21.10 / 1.21.11）。

### 架构改动
`MainScreen` 不再 `@Override keyPressed`，改为实现 `ChromeScreen#keyPressedDefault(int,int,int)`；
版本相关的 `keyPressed` 签名只出现在 compat 层。5 个旧 target 的 ChromeScreen 用
`scripts/add-key-hook.mjs` 批量补钩子。这样再加「键盘又改了」的版本时，MainScreen 不用动。

### 工具链
loom `1.9.2` → **`1.13.6`**（Gradle 8.14.2 能用的最高档；1.14.9 起要 Gradle 9.2）。
中间两个坑：1.9.2 报 `Unsupported unpick version`；1.11.8 报 fabric-api 的
`Javadoc ... must have an intermediary source namespace`。10 个 target 全绿。

### 验证脚本自己出问题（最值得记的一条）
本轮 90% 的时间花在「验证脚本说谎」上，而不是产品代码：
1. `( | Where-Object {...}).Count` 只命中一个对象时是 `` → `-gt 0` 为假 →
   **FAIL 却打印「界面验证全部通过。」**。必须 `@()`。
2. `children=` 恒为 `-`：游戏进程活着时日志独占写，`Select-String` 静默读空。
   改成先抓图、杀进程、再读日志，并且把 `children` 纳入判定。
3. `` 取在「窗口出现之后」，而日志在启动后 2~5 秒就创建 → 过滤条件永远为假 →
   找不到本轮日志（表现为「警告：没找到本轮日志」+ children 为空）。基准移到 `Start-Process` 之前。
4. 1.21.9+ 首次启动先弹 `AccessibilityOnboardingScreen`（日志里是 `class_8032`），
   自检钩子只认 `TitleScreen` → 永远等不到界面。验证脚本现在先关掉
   `options.txt#onboardAccessibility`。

### 调试手法
自检钩子不触发时，「没有 screen=MAIN」不提供任何信息。加一行 `mcver: cur=<类名>`（仅
`MCVER_OPEN_GUI` 时打，屏幕变了才打），一次就抓到 `class_8032`——
**以后新 target 界面起不来，先打印 currentScreen 类名。**

## 三十八、2026-10-26 深夜：一个 jar 通吃 1.21 ~ 1.21.11

### 需求来源
用户先说「你为什么一直在处理高版本」，我认了排序错误；接着提了真正的要求：
**1.21 已经不会再有新版本（最新到 26.x），就该做一个 jar 盖住整个 1.21 系列。**

这是对的。而且它顺带证明了我上一轮那个判断「维护版本范围最上端是没尽头的线」在 1.21 上不成立 ——
1.21 是**封闭**的版本段，一次做完永久有效。

### 做法（四条，全部有 javap 依据）
1. **编译基准取系列里最新的 1.21.11**。理由：`Item.Settings#registryKey` 是 1.21.2 才有的，
   1.21/1.21.1 没有；编译必须站在「有这个方法」的一端，旧版本靠 `NoSuchMethodError` 退回。
2. **不 override `Screen#keyPressed`**（1.21.9 起签名换成 `KeyInput`，一个 class 无法两头兼顾）。
   ESC 由 `Screen` 自己处理，旧 target 继续在自己 compat 里 override 各自那版。
3. **右键 / M 键改成每 tick 轮询**：`UseItemCallback` 的 lambda 返回类型（`TypedActionResult` →
   `ActionResult`，1.21.2）和 `KeyBinding` 第 4 参（`String` → `Category`，1.21.9）都会硬断。
   轮询用的 `options.useKey` / `KeyBinding#wasPressed` / `GLFW.glfwGetKey` 全系列不变。
4. **删掉游戏内截图自检**。用户问「那是截给我看的吗？没必要」—— 确实没必要，
   界面验收一直是 `PrintWindow` 外部抓图。删掉后 `saveScreenshot` 那处签名变化直接消失。

### 两个关键技术发现（会反复用到）
**① remap 环境里字符串反射查 MC 符号一律失效。**
`Class.forName("net.minecraft.registry.RegistryKey")` 在运行时找不到 —— 网络包里的类是
intermediary 名（`class_5321`），方法名也是（`method_xxxxx`）。第一版就是靠反射写 `registryKey`，
一跑就 `NullPointerException: Item id not set`。**该用编译期引用（`X.class`），不要用字符串名。**

**② `DrawContext#drawText` 返回类型 int → void（同 id `method_51439`，描述符 `...Z)I` / `...Z)V`）。**
这是本轮唯一真正的 ABI 断点。一个 class 文件无法同时引用两种返回类型，所以 `Ctx` 改成
**运行期按参数类型**遍历 `getDeclaredMethods()` 找方法、拿 `MethodHandle` 调（不写死名字，抗 remap），
找不到就退化到 `drawTextWithShadow`。
`fill(int×5)` / `drawItem(ItemStack,int,int)` 两端 id 和描述符都一致，不用处理。

### 结果
- `dist/` **10 → 6** 个 jar；`src/compat/` 7 → 5 个目录。
- `verify-gui.ps1` 全量 **17/17 通过**：1.21~1.21.11 十二个版本用**同一个** `mc1.21.jar`，
  面板像素 15092~15105、中心全 `C6C6C6`、`children=17`。
- 老 target 数字与改造前完全一致（15061 / 15349 / 15357 / 15357 / 19754）→ 无回归。

### 验证脚本又揪出 4 个「假通过 / 假失败」
1. `-File` 模式下 `-Targets a,b` 是**一个**字符串 → 全跳过，还打印「全部通过（0 个）」。现在按逗号拆 + 0 用例 exit 1。
2. 新实例没有 `options.txt` → 引导屏挡路。现在没有就创建。
3. 新装实例没开版本隔离 → 日志落在 `testgame/logs`、`options.txt` 无效。补 `.mcver-isolated`。
4. mods 里残留旧版本 jar → 同 mod id 两份。拷完即清。

### 教训（操作级）
- `[System.IO.File]` 用的是进程 CWD，不是 PowerShell 的 `cd`；相对路径会跑到 workspace 去。
- **别用正则删 Java 方法**：一次把 `class Compat {` 到文件尾整段吃掉，四个文件全废，靠 `git checkout` 抢救。
  按行扫 + 配对大括号才对。
- 用户指出方向错了的时候，先认，再问他真正想要什么 —— 这次「一个 jar 盖全系」就是问出来的，
  我自己排的优先级（1.21.5–1.21.8 下一块）完全没往这上面想。
