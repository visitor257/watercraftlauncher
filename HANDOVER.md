# HANDOVER — Water Craft Launcher（mcver）接力开发手册

> 写于 2026-10-23 16:40。这份文档是给**下一个接手的人（或 AI）**看的：
> 不重复 README 的功能介绍，只讲**怎么在另一台电脑上跑起来**、**代码是怎么组织的**、
> **哪些坑已经踩过别再踩**、**怎么在没有画面的环境里验证改动**。

---

## 0. 一句话

Minecraft **Fabric 客户端 mod**（目标 MC 1.21.4 / Loader 0.16.14 / Java 21）。
手里拿着「Water Craft Launcher」右键（或按 `M`），在游戏里开一个原版风格灰色面板，
能**下载版本 / 装加载器（Fabric·Quilt·Forge·NeoForge·OptiFine·Iris）/ 管账号 / 启动别的版本 / 多游戏目录 / 自定义下载源**。

- mod id 永远保持 `mcver`（改了 `config/mcver.json` 和 `/give @s mcver:download_tool` 全废）
- 显示名是 Water Craft Launcher
- 产物 `watercraftlauncher-fabric-1.0.0-mc1.21.4.jar`（约 214 KB），命名规则见 1.2.2

---

## 1. 新电脑第一次跑起来

### 1.1 需要什么

| 东西 | 版本 | 说明 |
|---|---|---|
| JDK | **21**（必须） | 编译目标 release=21；运行 1.20.6+ 的 MC 也要 21 |
| Gradle | **8.12** | 见 1.2，**别直接用 `gradlew.bat`** |
| 网络 | 首次构建要 | 拉 fabric-loom 插件 + Minecraft + yarn 映射（几百 MB） |
| 磁盘 | ≥2 GB | 依赖缓存 + 测试游戏目录 |

### 1.2 先搞定 Gradle 8.12

本机原来有个 `.tools/gradle-8.12`（解压好的发行版，144 MB），**但这个 zip 里没有带**（太大）。
新机器上按顺位取一个：

1. **已有 gradle**：`gradle -v` 是 8.x 就能直接用 `gradle` 代替下面的 `..\.tools\gradle-8.12\bin\gradle.bat`。
2. **有安装包/U 盘**：直接拷一份 `gradle-8.12` 目录到 `<项目根>/.tools/gradle-8.12`，跟原来一样用。
3. **只能联网下**：`https://services.gradle.org/distributions/gradle-8.12-bin.zip`，
   国内慢的话用腾讯镜像 `https://mirrors.cloud.tencent.com/gradle/gradle-8.12-bin.zip`。
   下完解压到 `<项目根>/.tools/`。

> **为什么劝你别直接用 `gradlew.bat`**：wrapper 会去 `services.gradle.org` 拉 8.12 的 zip，
> 国内经常卡住十几分钟然后超时（本机就是这么卡住的）。如果非要用 wrapper，把
> `mod/gradle/wrapper/gradle-wrapper.properties` 里的 `distributionUrl` 换成腾讯镜像那条：
> ```
> distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.12-bin.zip
> ```

### 1.2.1 构建（唯一正确姿势）

```powershell
cd <解压目录>\mod
..\.tools\gradle-8.12\bin\gradle.bat build --console=plain --offline
```

**第一次构建必须去掉 `--offline`**（新机器没有依赖缓存）：

```powershell
..\.tools\gradle-8.12\bin\gradle.bat build --console=plain
```

如果连 fabric maven 都慢，加镜像见第 7 节。

产物：`mod/build/libs/watercraftlauncher-fabric-1.0.0-mc1.21.4.jar`。

### 1.2.2 产物命名规则

`watercraftlauncher-<modloader>-<version>-mc<minecraft_version>.jar`，由 `gradle.properties` 的
`archives_base_name` / `mod_loader` / `mod_version` / `minecraft_version` 拼成（`build.gradle` 里用
`archiveFileName` 直接指定，因为 Gradle 默认会把 version 拼到最后）。
换目标 MC 或加 NeoForge/Forge 分支时只改这四个属性。

> ⚠️ `build` 之后 gradle 进程常常还挂着不退（Windows 上的 daemon），
> 占着文件锁导致下一次 `Copy-Item` 失败。遇到就 `process kill` 或 `gradle --stop`。

### 1.3 跑开发客户端（带 mod，能截图）

```powershell
cd <解压目录>\mod
$env:MCVER_OPEN_GUI="1"; $env:MCVER_TAB="启动"; $env:MCVER_SHOT="1"
..\.tools\gradle-8.12\bin\gradle.bat runClient --offline --console=plain
```

> 首次跑 `runClient` 同样要去掉 `--offline`（loom 要下 MC 客户端和资源）。

进标题界面会自动打开面板，`MCVER_SHOT=1` 会自动截图到 `mod/run/screenshots/mcver-启动.png`。

### 1.4 装到真实游戏

把 `watercraftlauncher-fabric-1.0.0-mc1.21.4.jar` 丢进 `mods/`（**只需要 Fabric Loader + Fabric API**，不需要别的）。
⚠️ 换文件名后记得删旧的 `mcver-1.0.0.jar`，否则两个 jar 同时提供 mod id `mcver` 会冲突。

---

## 2. 目录结构

```
mcvermod/
├─ watercraftlauncher-fabric-1.0.0-mc1.21.4.jar   # 已构建产物，直接可用
├─ HANDOVER.md              # ← 本文
├─ README.md                # 功能说明（给用户看）
├─ REPORT.md                # 开发过程流水账（含每轮需求与验证记录，排查历史问题时很有用）
├─ _keep/screenshots/       # 各页截图归档
├─ .tools/gradle-8.12/      # 本地 gradle，绕开 wrapper 下载（本 zip 未包含，见 1.2）
└─ mod/
   ├─ build.gradle / settings.gradle / gradle.properties   # loom 1.9.2
   ├─ src/main/java/dev/mcver/
   │  ├─ McVerMod.java        # 注册物品（DOWNLOAD_TOOL）
   │  ├─ McVerClient.java     # 客户端入口：右键/快捷键开面板 + 自检钩子
   │  ├─ core/                # 全部逻辑，无 UI 依赖
   │  └─ ui/                  # 全部界面
   ├─ src/main/resources/
   │  ├─ fabric.mod.json
   │  └─ assets/mcver/{items,models/item,textures/item,lang}
   └─ run/                    # 开发用游戏目录（554 MB，打包时已排除）
```

### core（逻辑层）

| 文件 | 职责 |
|---|---|
| `Config.java` | `config/mcver.json` 读写。字段：memMb / closeAfterLaunch / jvmArgs / gameArgs / winWidth / winHeight / fullscreen / javaPath / javaExtra / downloadThreads / downloadSource / downloadSources / defaultIsolation / lastDir / dirs / accountIndex / accounts |
| `Dirs.java` | 多游戏目录：扫描、添加、删除、恢复；`add(p, alsoKeep)` 保留切换前目录 |
| `Installer.java` | **最大的一块**（33 KB）：版本 json 解析、资源/库下载、规则判断（`rulesAllow`）、zip 解压、`Json` 内部工具类、`MANIFEST_OFFICIAL` |
| `InstallJob.java` | 一次安装任务：状态机（RUNNING/DONE/FAILED/CANCELLED）、进度、可取消 |
| `Jobs.java` | 全局任务登记处，关界面不停任务，重新打开能接回进度 |
| `Launcher.java` | 生成启动计划 `Plan`（classpath / natives / jvm / game args），然后真的 `ProcessBuilder` 起进程 |
| `LoaderInstaller.java` | Fabric/Quilt/Forge/NeoForge/OptiFine/Iris 安装器调度；`downloadFirst(urls…)` 多源回退；**`compareVersions`**（classpath 去重要用） |
| `LoaderVersions.java` | 各加载器可用版本列表（BMCLAPI 优先，官方兜底） |
| `VersionList.java` | 官方版本清单（manifest_v2），镜像失败自动退官方 |
| `Sources.java` | 下载源：`record Src(name, root)`，`rewrite(url)` 做 BMCLAPI 风格映射，`probe()` 探测可用性 |
| `Net.java` | HTTP + 下载（带重试/进度/`isJar` 校验） |
| `Modrinth.java` | Modrinth v2 只读接口：查版本、下文件（目前用于 Iris/Sodium） |
| `JavaFinder.java` | 扫盘找所有 Java、`majorOfExe()` 读主版本 |
| `Auth.java` / `Account.java` | 微软正版登录（device code）与离线账号 |
| `Json.java` / `VersionMeta.java` | 轻量 JSON 与版本元数据封装 |

### ui（界面层）

`MainScreen` 是容器：左侧 6 项导航 + 右侧页内容，灰色面板 375×220（GUI 427×240 时）。
每页继承 `Page`（`nav() / init() / render()`，`add()` 加原版控件，`line()/text()/trim()` 等小工具）。

页面：`LaunchPage`（启动）、`DownloadPage`（下载+装加载器）、`AccountsPage`（账号）、
`VersionsPage`（版本管理：删/隔离/日志/改目录）、`JavaPage`（Java 列表+分页+手动路径）、`SettingsPage`（内存/参数/下载源）。

**UI 约定**：所有文字 `drawText(..., false)`（不画阴影）；面板是原版箱子灰；
`PANEL_W=400, PANEL_H=232, NAV_ITEM=18`。

---

## 3. 启动链路（改了 Launcher 必看）

```
LaunchPage 点启动
  → Launcher.plan(gameDir, versionId, …)          # 生成 Plan
       ├─ merged(): 递归合并 inheritsFrom，libraries 按 group:artifact:classifier 覆盖
       ├─ 若 versions/<id>/.mcver-isolated 存在 → runDir = versions/<id>（版本隔离）
       ├─ byArtifact: 同 group:artifact 只留**版本最高** → classpath   ★关键
       ├─ ≤1.18: natives 走 classifiers 解压到 versions/<id>/natives
       └─ 拼 -cp / jvm args / game args / mainClass
  → Launcher.launch(): 写 @argfile（绕开 32k 命令行上限）→ ProcessBuilder
       └─ 工作目录 = p.runDir，日志 = p.runDir/logs/mcver-launch-<id>-<ts>.log
  → 1.8 秒内退出 → 抛异常并把「根因那行 + 日志尾巴」显示到界面
```

**标记文件**（都放在 `versions/<id>/` 下）：

- `.mcver-incomplete` — 安装中/未装完，启动页不显示它
- `.mcver-isolated` — 版本隔离开，`--gameDir` 指到版本目录

---

## 4. 已经踩过的坑（血泪，别重复）

### 4.1 物品图标紫黑方块

1.21.4+ 的物品图标是**三件套**，缺一个就紫黑：

```
assets/mcver/items/download_tool.json        ← Item Model Definition，1.21.4 起必需！
{"model":{"type":"minecraft:model","model":"mcver:item/download_tool"}}

assets/mcver/models/item/download_tool.json
{"parent":"minecraft:item/generated","textures":{"layer0":"mcver:item/download_tool"}}

assets/mcver/textures/item/download_tool.png ← 16×16、**不透明**
```

- **1.21.1 及以前不需要第一个文件**，1.21.4 开始必须有。
- 原版 `block/water_flow` 是**灰度遮罩**，颜色靠 biome water color 调 → 直接引用是**灰色**的；
  而且 32×1024 动画帧在 `item/generated` 下画不出来。
- 现在这个 png 是从客户端 jar 抠 `water_flow.png` 第一帧 → 缩 16×16 → 灰度重映射成水蓝（#2E5CB8→#9EC8FF）。
- ⚠️ **删资源文件后 `build` 会显示 UP-TO-DATE，jar 里仍有旧文件**。必须删掉
  `build/resources/main` 和 `build/libs/*.jar` 再 build。

### 4.2 Fabric 启动立刻退出、退出码 1

日志根因：

```
java.lang.IllegalStateException: duplicate ASM classes found on classpath:
  .../org/ow2/asm/asm/9.6/asm-9.6.jar!/org/objectweb/asm/ClassReader.class,
  .../org/ow2/asm/asm/9.10.1/asm-9.10.1.jar!/org/objectweb/asm/ClassReader.class
  at net.fabricmc.loader.impl.util.LoaderUtil.verifyClasspath
```

- 父版本（1.21.4）自带 asm 9.6，Fabric 版本带 9.10.1，`merged()` 只在相邻层去重 → 两个都进 classpath。
- **修法**：`plan()` 里先 `Map<group:artifact:ext, lib>`，同 artifact 留 `LoaderInstaller.compareVersions` 最高的。
- ⚠️ 顺手做的：日志摘要 `tail()` 原来只取最后 5 行 → 堆栈全是 `at ...`，
  用户看到的就是"日志里没写原因"。现在先扫第一条含 `Exception/Caused by/Error/Could not` 的行。

### 4.3 Forge / NeoForge 拿不到版本列表

- `maven.neoforged.net` 在部分国内网络 **DNS 被污染**（解析到 `2a03:2880:...:face:b00c` 之类假 IP）→ 超时。
- `api.github.com/repos/neoforged/NeoForge/releases` 返回 2 字节空体。
- 旧代码按 `p[1]+"."+p[2]+"."` 推前缀，MC 1.20.1 算出 `20.1.`，而 **NeoForge 1.20.1 是 47.x** → 永远空列表。
- 现在：`LoaderVersions.forge()` 优先 `https://bmclapi2.bangbang93.com/forge/minecraft/<mc>`；
  `neoforge()` 优先 `https://bmclapi2.bangbang93.com/neoforge/list/<mc>`（含 installerPath），官方 maven 兜底，1.20.1 特判 `47.`。
- 可用端点：`/neoforge/list/<mc>`、`/forge/minecraft/<mc>`、`/maven/...`；**`/neoforge/<mc>` 是 404**。

### 4.4 版本隔离"开关没用"

根因是 `Plan` 有 `runDir` 字段但 `plan()` 从没改它，`--gameDir` 永远指根目录。
现在靠 `versions/<id>/.mcver-isolated` 标记驱动；关隔离只删标记，**不动用户存档和 mods**。

### 4.5 多目录「添加」把原目录替换掉

`restoreDir()` 只 `setDir` 没 `Config.dirs` 登记，旧目录只靠 `lastDir` 一根线挂着，一 add 就断。
修法：恢复时 `Dirs.add`；`switchDir` 先抓旧目录再改 `lastDir`。

### 4.6 Windows / PowerShell 工具链坑

- **PowerShell 5.1 的 `Set-Content -Encoding UTF8` 写 BOM**，javac 会报一堆"需要 class/interface/enum"。
  改 Java 文件用：
  ```powershell
  [System.IO.File]::WriteAllText($p, $s, (New-Object System.Text.UTF8Encoding($false)))
  ```
- 读源文件必须绝对路径 + 显式 UTF-8：`[IO.File]::ReadAllLines($p,[Text.Encoding]::UTF8)`。
- 追加长文档到 `REPORT.md`：`edit` 常因 CRLF 匹配失败，用
  `[System.IO.File]::AppendAllText($path,$txt,(New-Object System.Text.UTF8Encoding($false)))`。
- 游戏目录名带空格和括号（`D:\Program Files\Minecraft Java Edition (pcl2)`），
  命令行会超长/被拆 → 已用 `@argfile` 解决，别改回单条命令。
- `logs/latest.log` 被占用时游戏会报 "Unable to delete file"，无害。

### 4.7 离线账号的 401 是正常的

用离线账号启动，日志一定会有 `InvalidCredentialsException: Status: 401` 和 Realms 认证失败。
**只要走到 `Setting user: xxx` 就说明启动链路是通的**，别去修这个。

### 4.8 Forge 1.20.2+ 启动闪退：`Module minecraft contains package ... module _1._20._2`

- 现象：点了启动，窗口出来 1 秒就没，日志里是
  `java.lang.module.ResolutionException`，`Module minecraft contains package net.minecraft.client, module _1._20._2 exports package ...`。
- 根因：Forge 1.20.2+ 的客户端代码由**加载器自带**（安装器展开的
  `libraries/net/minecraft/client/client-<ver>-srg.jar`，`BootstrapLauncher` 把它加载成 `minecraft` 模块）。
  再把原版 `versions/<id>/<id>.jar` 塞进 `-cp`，它就会变成自动模块 `_1._20._2`，和 `minecraft` 抢包 → 解析失败。
- 修法（已在 `Launcher.plan()` 里）：`clientJarProvidedByLoader()` 两个条件都满足才不把原版 jar 放进 classpath：
  ① `mainClass` 以 `BootstrapLauncher` 结尾；② `libraries/net/minecraft/client/` 下有 `client-*-srg.jar`。
  **不要**改成“凡 Forge 就不加” —— Forge 1.20.1 用的是 `cpw.mods.bootstraplauncher` 且没有 srg jar，
  它的客户端代码只能来自原版 jar。
- 验证：`logs/mcver-args-*.txt` 的 `-cp` 里不应再有 `versions/1.20.2/1.20.2.jar`。

### 4.9 UI 重建循环的坑：`init()` 里写“强制跳页”会吃掉用户操作

下载页曾经在 `init()` 里写“有任务在跑就强制回进度页”，结果「再装一个」按钮
（只做 `view=LIST; rebuild()`）每次都被立刻拨回去，表现就是按了没反应。
**规矩**：`init()` 不要无条件纠正用户刚做的导航，用一次性/显式标志（如 `allowListWhileRunning`）区分
“进页面时的默认页”和“用户主动选的页”。

### 4.10 离线验证：把 core 单独编成探针（比进游戏快得多）

core 层只有 `Config.dir()` 引用 `net.minecraft.client.MinecraftClient`，所以：

1. 把 `core/*.java` 拷到临时目录；
2. 加一个桩 `net/minecraft/client/MinecraftClient.java`（`getInstance()` 返回 null、`runDirectory` 给个 `.`）；
3. `javac -d out` 后跑 `Launcher.plan(gameDir, id)`，直接看 classpath / missing / warnings；
4. 需要时再调 `Launcher.launch(...)`，它自己会写 `@argfile` 并起真进程。

排查“启动闪退”这类问题，先跑探针看 classpath，比反复开游戏快一个数量级。

### 4.12 NeoForge 20.2+ 的安装器**没有** `--installClient`

- 现象：`joptsimple.UnrecognizedOptionException: installClient is not a recognized option`。
- NeoForge 20.x 安装器 (SimpleInstaller) 只支持 `--installServer` / `--extract` / `--mirror` / `--offline`。
  （Forge 49.0.2 反过来是有 `--installClient` 的，两家不一样）
- 现成修法：`LoaderInstaller.installViaInstallProfile()`——按 `install_profile.json`（spec=1）
  自己跑客户端处理器（MCP_DATA → DOWNLOAD_MOJMAPS → MERGE_MAPPING → jarsplitter(client)
  → ForgeAutoRenamingTool → binarypatcher），跳过 `sides` 只有 server 的。
  `installNeoForge()` 先试 CLI，失败自动转这条路。
- 自装流程需要的东西：安装器 jar 里的 `version.json` / `install_profile.json` / `data/*.lzma`，
  以及 `{MINECRAFT_JAR}`（原版 jar 必须先装好）。

### 4.13 ⚠️ NeoForge 的库在本机网络不可达

- `maven.neoforged.net` 两个 IP（104.23.125.189 / 103.240.182.55）都连不上 443，**不是 DNS 问题是链路。**
- BMCLAPI → USTC 镜像只对部分文件有效：安装器 jar 200，但 `neoform-*.zip` 403、
  `net.neoforged.fancymodloader:events` 404。
- 所以在无代理的机器上，NeoForge 20.2+ 装不完；**1.20.3 建议改用 Forge 49.0.2**（实测可装可启动）。
- 如果以后要支持自定义 maven 镜像，入口在 `LoaderInstaller.ensureMaven()`（已经是多源回退）。

### 4.14 加载器版本 id 的命名不统一，别硬拼字符串

| 加载器 | 1.20.2 的 id | 1.20.3 的 id |
|---|---|---|
| Forge | `forge-1.20.2-48.1.0` | **`1.20.3-forge49.0.2`** |
| NeoForge | — | `neoforge-20.3.8-beta` |

装完用 `LoaderInstaller.findFreshVersionId()` 去 `versions/` 里找（名字含版本号 + mtime 最新），
不要再 `return "forge-" + mc + "-" + ver`。

### 4.15 测镜像/下载必须带启动器 UA（否则会得出错误结论）

BMCLAPI 对 `User-Agent` 敏感，同一个 `neoform-*.zip`：

| UA | 结果 |
|---|---|
| `WaterCraftLauncher/1.0`、`mcver/1.0`、`PCL2/2.11.2`、`curl/8.4.0` | 200，真文件 |
| 浏览器 UA（Mozilla…）| 200 但是 929 字节“Verifying your browser”页 |
| 空 UA / Java 默认 UA | 403 |

`Net.open()` 已经写死了启动器 UA，**别删**。排查时也别用 `Invoke-WebRequest` 裸测。

### 4.16 NeoForge 20.3.x 引用了不存在的 `fancymodloader:events:1.0.16`

所有 20.3.x beta 都引用它，而全镜像 404（相邻 1.0.15/1.0.17 都在）。
`LoaderInstaller.ensureMaven()` 已加“邻近补丁版本回退”（下载到原定路径），
新增类似缺件时先看日志里是哪个坐标 404。

### 4.17 install_profile 的 data 表是“产物路径”，不是下载清单

`[net.minecraft:client:…:slim]` / `:srg]` / `neoforge:…:client` 都是处理器要**生成**的，
去下载必 404。只有 processor `args` 里的 `[maven]` 才是真输入。

### 4.11 设置项的“真源”只能是 Config，输了就要落盘

同一类 bug 在这个项目里已经出现三次（版本隔离勾了会丢、全屏勾了会丢、内存改了回 4096）：

- **页内字段只是缓存**，`init()` 会把 `Config` 的值重新填回界面；
- 所以任何输入框都必须 **on change 就写 `Config` + `Config.save()`**（见 `SettingsPage.bindInt()/bindText()`），
  不能只靠底部的“保存”按钮，更不能只改页内变量；
- 数字框要加范围保护和“中间值不写盘”（否则想打 8192，敲到 "8" 就把内存改成 256）；
- 同一个配置项在多个页面出现时（启动页和设置页都有内存），**两边都写透** 才能真同步。

---

## 5. 怎么在没有画面的环境里验证（AI 必读）

这个开发环境（OpenClaw）**看不到图片附件**，`read` 一张 png 也没用。可靠办法是**取像素算数值**：

```powershell
Add-Type -AssemblyName System.Drawing
$b=[System.Drawing.Bitmap]::FromFile("...\screenshots\mcver-启动.png")
# 统计某个矩形的平均色 / 暗像素数 / 蓝色分量差
$b.Dispose()
```

已经用过的判据：

| 要验的东西 | 判据 |
|---|---|
| 图标是不是蓝的 | 图标区域 `B-R`：改前 = 0（纯灰按钮），改后 = 97 |
| 副标题有没有删掉 | 标题右侧区域 `min` 亮度 = 198（纯面板灰）、`dark(亮度<140)` 计数 = 0 |
| 某页有没有渲染 | 页面区域 `dark` 计数 > 0 |

UI 逻辑自检更直接：**在 `render()` 里用环境变量门控画一次 + `LOGGER.info` 打状态**，
跑 `runClient` 后读 `run/mcver-debug.txt` 和 stdout。现成例子：`MainScreen.render()` 里的 `MCVER_ICON` 分支。

---

## 6. 调试钩子（环境变量，全在这）

| 变量 | 作用 |
|---|---|
| `MCVER_OPEN_GUI=1` | 到标题界面自动开面板 |
| `MCVER_TAB=启动\|下载\|账号\|版本\|Java\|设置` | 指定初始页 |
| `MCVER_TAB_CYCLE=1` | 依次切所有页并截图 |
| `MCVER_SHOT=1` | 每页截图到 `run/screenshots/mcver-<tab>.png` |
| `MCVER_SCREEN=wizard\|download` | 直接进下载页 |
| `MCVER_ICON=1` | 右下角画物品图标 + 原版纸图标做对照 |
| `MCVER_PICK` / `MCVER_LOADER` / `MCVER_SHADER` / `MCVER_NAME` | 下载页自动选中版本/加载器/光影/填名字 |
| `MCVER_AUTO_INSTALL=<id>` | 自动跑一次安装 |
| `MCVER_AUTO_LAUNCH=<版本 id>` | 自动启动指定版本（复现启动 bug 用这个） |
| `MCVER_AUTO_CLOSE=1` | 启动后关游戏 |
| `MCVER_VSEL=<id>` / `MCVER_VDEL` / `MCVER_VDEL_OK` | 版本页选中/删除/确认删除 |
| `MCVER_VISO=1` | 关版本隔离 |
| `MCVER_ISOLATION=1` | 开版本隔离 |
| `MCVER_DIRS=1` / `MCVER_DIR_ADD=<path>` | 目录页 / 添加目录 |
| `MCVER_SOURCES=1` / `MCVER_SRC_ADD="名字\|根地址"` | 下载源页 / 添加源 |

运行时诊断：
- `mod/run/mcver-debug.txt` — `screen=MAIN tab=… gui=427x240 panel=375x220@26,10 children=N`
- `mod/run/mcver-install.log` — 安装流水
- `<gameDir>/logs/mcver-launch-<id>-<ts>.log` — 每次启动的完整输出
- `<gameDir>/logs/mcver-args-<ts>.txt` — 实际传给 java 的参数（排查 classpath 神器）

---

## 7. 网络与镜像（国内网络必看）

- 默认下载源**只有官方 Mojang**；镜像是用户自己在「设置 → 下载源」里加的（有 `＋ BMCLAPI` 一键预设）。
- `Sources.rewrite()` 按 BMCLAPI 约定映射：
  - `{root}/mc/game/version_manifest_v2.json`
  - `{root}/maven/...`
  - `{root}/assets/...`
- 任何一步失败都会**自动回退官方**（`VersionList.fetch` / `LoaderInstaller.downloadFirst`）。
- 本机实测可用：`https://bmclapi2.bangbang93.com`。
- 构建期如果 fabric maven 慢，可在 `build.gradle` / `settings.gradle` 的 repositories 里加：
  `maven { url = 'https://maven.aliyun.com/repository/public' }`（放在前面）。

---

## 8. 已知问题 / 下一步候选

**已知问题**

1. `AssetsPage` 级别的多语言只做了物品名（`lang/zh_cn.json`），界面文字硬编码中文。
2. 版本列表 / 下载列表的分页是手写的，条目多时没有虚拟滚动。
3. `mods` 目录判断只剩「有没有 mods 文件夹」这类启发式，隔离状态一律以 `.mcver-isolated` 为准（别改回去）。
4. `REPORT.md` 是流水账，越写越长；重大结论已折叠进本文档。

**下一步候选（按价值排序，用户尚未指定）**

1. **Modrinth 下载中心**：Mod / 资源包 / 光影 / 整合包（`core/Modrinth.java` 已有只读接口雏形）
2. **Mod 管理**：读 `fabric.mod.json` / `mods.toml` / `quilt.mod.json`，`.disabled` 启停
3. `.mrpack` 整合包导入导出
4. **Java 自动下载**（Adoptium API，按版本挑 8/17/21）
5. 存档备份 / 崩溃日志分析（`crash-reports/*.txt` 提取关键帧）
6. 正版登录刷新 token 的边界情况（expires 过期、多账号切换）

---

## 9. 风险提示（发布前必须想清楚）

Fabric mod 里**直接提供 Minecraft 官方版本的下载与启动**，在国内涉及游戏版号/发行监管问题，
在国外也可能触碰 Mojang 的 EULA（"不得提供游戏本身的下载"）。
`REPORT.md` 第八节写过同样的提醒。自己玩没问题；**要公开发布得先想清楚定位**（比如只做"启动已有版本"，不提供下载）。

---

## 10. 交接检查清单

- [ ] `gradle build --offline` 能过
- [ ] `runClient` 能进标题界面并自动开面板
- [ ] 物品图标不是紫黑（创造模式「工具」栏找 Water Craft Launcher）
- [ ] 启动页能列出安装好的版本
- [ ] 装一个 Fabric 版本 → 启动 → 走到 `Setting user` 即算通
- [ ] `mod/run/config/mcver.json` 里的 `lastDir` 指到本机真实游戏目录再测

祝顺利。踩到新坑记得往第 4 节补一条。
