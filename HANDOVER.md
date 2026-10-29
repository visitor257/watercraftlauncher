# HANDOVER — Water Craft Launcher（mcver）接力手册

> 更新：**2026-10-29 19:50**（这次是「老 Forge 全家右键/配方/图标」收尾之后）。
> 给**另一台电脑上的下一个人（或 AI）**看：怎么跑起来、代码怎么组织、哪些坑已经踩过、怎么验证。
> 不重复 README 的功能介绍，也不重复 PROGRESS.md 的逐轮流水账。
>
> 上一版（2026-10-23）只覆盖「Fabric 1.21.4 单 target + Gradle」那个时代，**已经严重过期**，
> 需要时用 `git log --follow HANDOVER.md` 翻。

---

## 0. 一句话

Minecraft **客户端 mod**（mod id 永远叫 `mcver`，显示名 Water Craft Launcher）：
在游戏里右键物品开一个原版风格的灰色面板，能下载/安装 MC 版本、装六种加载器、管账号、启动别的版本。

但它已经不是「一个 mod」了，是**一套多版本构建系统**：

| 维度 | 现在的规模 |
| --- | --- |
| 产物 jar | **49 个**（Fabric/Legacy Fabric 25 · Forge 19 · NeoForge 5） |
| 覆盖 MC | **1.7 ~ 26.1**（含快照/远古版的安装） |
| 源码形态 | 一份主源码 + 20 个 `compat/` 覆盖 + 30 个 `modx/` 垫片目录 |
| 构建管线 | **4 条**（Gradle/loom、tiny-remapper、mojmap+reobf、SRG 垫片），没有一条是「IDE 点一下」 |
| 验证 | 49 个「版本×加载器」组合到 L2；17 个 target 过像素级界面验收 |

**这个项目的真实难度不在写界面，在于「同一份代码要喂给十几个世代的 MC 类名」。**
接手前先把第 3 节看懂。

---

## 1. 换机器：两条路

### 1.1 路线 A：整目录拷（**强烈推荐**）

原因见第 5 节：**`testgame/`（10.5 GB）和 `modx/srg/`（426 MB）都不在 git 里，但没有它们就编不动**。
从零重建这两个目录要几小时机器时间（要下载所有 MC 版本 + 所有加载器 + 跑映射管线）。

```powershell
robocopy "D:\Development\watercraftlauncher" "E:\dev\watercraftlauncher" /E /MT:16 ^
  /XD .git .gradle build logs .mcver-tmp /XF *.log
```

- 总量约 **13 GB**，建议走移动硬盘或千兆局域网。
- **别排除 `testgame/`**：`testgame/libraries` 是四条管线里三条的编译期 classpath，
  `testgame/versions` 里 158 个实例是探针/验收的靶子。
- 可以放心排除：`.git`（123 MB，要历史就单独拷）、`logs/`（123 MB，历史日志没用）、
  `testgame/.cache`、`testgame/.mcver-tmp`（225 MB 临时下载）、`mod/build`、`mod/run`。
- 拷完在新机器上跑第 1.4 节的 `portable-jdk.mjs`，再跑第 1.5 节的自检。

### 1.2 路线 B：git clone（干净但费时间）

```powershell
git clone <repo> watercraftlauncher   # 1908 个文件 / 138 MB
```

clone 完**只有源码**，还要自己补：

1. 按第 1.3 节装工具链；
2. 用启动器（或 `ProbeInstall`）把 `testgame/` 里那 158 个实例**重新装一遍** —— 这一半是验证靶子，
   另一半（`libraries/`）是编译依赖；
3. 跑 `scripts/mk-legacy-named.mjs` 生成 yarn named jar、`scripts/mk-srg-*.mjs` 生成 SRG 中间产物；
4. `node scripts/rebuild-all.mjs`。

**顺序不能反**：`mk-srg-mojmap.mjs` 自己也要读 `testgame/libraries` 里的 forge client jar。

### 1.3 环境要求

| 东西 | 版本 | 为什么 |
| --- | --- | --- |
| Windows | 10/11 | 验证脚本用 `PrintWindow` / `keybd_event`，别指望在 Linux 上跑验收 |
| **PowerShell 5.1** | 系统自带 | `verify-gui.ps1` / `build-all.ps1` / `build-probe.ps1` 都是 PS 5.1 写的。**用 `pwsh` 7 可能行为不同**（本项目没在 7 上验过） |
| **Node.js** | **≥ 20.11** | 脚本用 `import.meta.dirname`（20.11 才有）。本机是 v24.19.0。**零 npm 依赖，不需要 `npm install`** |
| **JDK 21** | 21.x | `javac` / `javap` / `jar`。所有编译都以它为基准（`--release 8/17/21`） |
| JRE 8 | 1.8.0_3xx | 只在「1.7~1.12 老版本启动验收」时用到；没有不影响构建 |
| Gradle | 8.14.2（wrapper 缓存） | 只有 `fabric-modern` 那一步用。`build-all.ps1` 会自己找：`-Gradle` 参数 > `GRADLE_HOME` > `.tools\gradle-8.12` > `PATH` > `~\.gradle\wrapper\dists\gradle-8.*` |
| 网络 | Mojang + BMCLAPI | `bmclapi2.bangbang93.com` 是本机实测唯一稳定的镜像。**NeoForge 官方 maven 在国内链路不通**（不是 DNS 问题） |
| 磁盘 | ≥ 30 GB 空闲 | 13 GB 工程 + 构建中间产物 |

> 项目里**没有任何 `.npmrc` / `package.json` / `node_modules`**。所有 `.mjs` 只用 Node 标准库。
> 这是刻意的：换机器不需要装依赖。

### 1.4 第一条命令：把写死的 Java 路径改成这台机器的

`scripts/` 里二十来处写死了 `D:\Program Files\Java\jdk-21.0.7`（还有几处写成 `C:/`，同一台机器两个盘符混用）。
新机器上这些路径不存在，报错会是「找不到 javac」或 `spawn ENOENT`，**看不出是路径问题**。

```powershell
cd <工程根>
node scripts/portable-jdk.mjs            # 先看报告（不动文件）
node scripts/portable-jdk.mjs --write    # 确认后自动改
```

它会：扫所有盘符的 `Program Files` 找 JDK 17+ / JRE 1.8（真跑 `-version` 验证，不是只看目录存在）→
把脚本里的绝对路径换成这台机器的。也认环境变量 `MCVER_JDK21` / `MCVER_JRE8` / `JAVA_HOME`。

### 1.5 换机器后的自检（三条命令，按顺序）

```powershell
# 1) 探针能编出来吗（core 层 + 探针，30 秒）
powershell -ExecutionPolicy Bypass -File scripts\build-probe.ps1

# 2) 一个实例能起、界面能画吗（像素级，最容易暴露环境问题）
node scripts\verify-gui-one.mjs V1.21.4-FORGE
#   期望最后一行：ok: true  center C6C6C6  hits >10000  children 17  870x519

# 3) 全量重建（41 步，约 8~12 分钟）
node scripts\rebuild-all.mjs
```

三条都过 = 这台机器可以干活了。任何一条挂，看第 6 节对应的坑。

---

## 2. 现在到底有什么

### 2.1 产物矩阵（`dist/`，49 个 jar）

命名规则：`watercraftlauncher-<loader>-1.0.0-mc<mc>.jar`。

| 加载器 | jar 数 | 覆盖的 mc 后缀 |
| --- | --- | --- |
| **Fabric**（含 Legacy Fabric） | 25 | `1.7` `1.7.1`~`1.7.8` `1.7.10` `1.8.9` `1.9.4` `1.10.2` `1.11.2` `1.12.2` `1.13.2` `1.14` `1.15` `1.16` `1.17` `1.18` `1.19` `1.19.3` `1.20` `1.21` |
| **Forge** | 19 | `1.7.2` `1.7.10` `1.8.9` `1.9.4` `1.10.2` `1.11.2` `1.12.2` `1.14.4` `1.15.2` `1.16.5` `1.17.1` `1.18.2` `1.19.4` `1.20.1` `1.20.2` `1.20.4` `1.20.6` `1.21.1` `1.21.4` |
| **NeoForge** | 5 | `1.20.2` `1.20.4` `1.20.6` `1.21.1` `1.21.4`（**20.1 直接用 Forge 1.20.1 那个 jar**：它是 Forge 47 换皮，包名仍是 `net.minecraftforge.*`） |

「一个 jar 盖一个大版本」：`mc1.20.jar` 靠 `fabric.mod.json` 里 `"minecraft": ">=1.20 <1.21"` 盖住 1.20~1.20.6。
Forge/NeoForge 的 `mods.toml` 同理。

**明确做不到的**（别再花时间）：

| 目标 | 为什么 |
| --- | --- |
| Fabric **1.7.9** | `meta.legacyfabric.net` 没有它的 yarn，且 loader 自己报 `tinyremapper: Unfixable conflicts` |
| Forge **1.13.2** | 那代没有 mojmap 也没有能用的 MCP 映射 |
| Forge/NeoForge **26.1** | class 69 / Java 25，工具链不支持 |
| MC **≤1.6.4** | 只剩 Ornithe，不是 Fabric/Forge，另有 loader |
| Legacy Fabric 的**物品图标/配方** | loader 不挂载 mod 的 `assets/`（资源重载日志里没有 `ModResourcePack`）。要修得加 `fabric-resource-loader-v0` 硬依赖 |

### 2.2 启动器本体（面板里能干什么）

- **启动页**：多游戏目录切换（`[◀][目录][▶][＋]`）、选版本、内存/JVM 参数、启动、退出码+日志尾巴
- **下载页**：4 个分类页签（正式 103 / 快照 748 / 远古 61 / 愚人节 9）、六种加载器、光影（Iris/OptiFine）、版本命名
- **账号页**：微软正版 device-code 登录 + 离线账号
- **版本页**：删除（二次确认）、版本隔离、看日志、改显示名
- **Java 页**：扫盘列出全部 Java + 手动粘路径
- **设置页**：内存、参数、下载源（可自加镜像，有 `＋ BMCLAPI` 一键预设）

### 2.3 证据在哪

| 文件 | 内容 |
| --- | --- |
| `verification/results.csv` | **49 行**，每行一个「版本×加载器」组合的 install/launch 结果（L2 原始记录，可断点续跑） |
| `PROGRESS.md` §1 | 支持表（按加载器汇总）+ 每条的证据说明 |
| `PROGRESS.md` §9~§36 | 每一轮的根因、修法、验证方式（**排查历史 bug 先翻这里**） |
| `logs/build/rebuild-*.log` | 全量重建每步的原始输出 |

---

## 3. 代码地图

### 3.1 源码分层

```
mod/src/main/java/dev/mcver/        # 主源码。用「官方名」(mojmap / yarn) 写，Java 8 语言级
├─ McVerMod.java / McVerClient.java # 物品注册 / 客户端入口（右键、快捷键、自检钩子）
├─ core/                            # 全部逻辑：安装、启动、下载、账号、配置
└─ ui/                              # 全部界面：MainScreen + 8 个 Page + 4 个适配层
mod/src/main/resources/             # fabric.mod.json、assets/、jar 里的资源

mod/src/compat/<版本>/java/...      # **整文件覆盖**（不是编译期开关）：1.7.2 1.7.10 1.8.9 ...
mod/src/compat/<版本>/resources/    # 该世代的资源（如 1.13+ 的 data/ 配方）

modx/<名字>/                        # 各世代的「垫片 / overlay」，只在对应管线里挂上
├─ forge14 forge16 forge17 forge18 forge19 forge21 forge214   # Forge 各代
├─ forge102 forge111 forge1122 forge1710 forge172 forge189 forge194  # 老 Forge
├─ neoforge20 neoforge21 neoforge214 shims201 shims21        # NeoForge 及其共享垫片
├─ shim1102 shim1122 shim1710 shim172 shim189                # SRG 时代的 net.minecraft.* 垫片
├─ lwfix/                        # Forge 1.7.2 的 FML 字节码补丁源
├─ out/<target>/                 # 构建产物（publish 从这里抄进 dist）
└─ srg/ legacy/ build*/          # 中间产物，**不进 git**（见第 5 节）

scripts/                          # 全部构建 / 验证脚本（零 npm 依赖）
tools/probe/                      # 离线探针（Java）：跑生产代码但不开界面
```

**关键约定：主源码不许 import 任何 `net.minecraft.*`（除了 `ui/Ctx`、`ui/Txt`、`ui/Btn`、
`ui/ChromeScreen`、`core/Compat`、`core/Log`、`dev.mcver.bridge.Minecraft` 这几个适配层）。**
跨世代差异一律往 `compat/` 和 `modx/` 里塞，主源码保持「一套」。

### 3.2 四条构建管线（**接手必须分清**）

| # | 管线 | 脚本 | 覆盖 | 编译期 classpath 从哪来 |
| --- | --- | --- | --- | --- |
| A | **Gradle + fabric-loom** | `scripts/build-all.ps1` → `mod/build.gradle` | Fabric 1.14~1.21（9 jar） | loom 自己下（`~/.gradle`） |
| B | **javac + tiny-remapper** | `build-1132.mjs` / `build-1122.mjs` | Legacy Fabric 1.7~1.13.2（16 jar） | `modx/legacy/<mc>/`（yarn named jar） |
| C | **javac(mojmap) + reobf** | `build-neoforge.mjs` | Forge 1.14.4~1.21.4 + NeoForge 20.2~21.4（17 jar） | `modx/srg/<mc>/` + `testgame/libraries` |
| D | **javac(SRG) + 垫片** | `build-srg-legacy.mjs` / `build-1122forge.mjs` / `build-1710forge.mjs` / `build-172forge.mjs` | Forge 1.7.2~1.12.2（7 jar） | `modx/srg/<mc>/`（SRG 名 jar） |

四条管线的产物命名、`fabric.mod.json` / `mods.toml`、资源目录结构都不一样，别想统一。

管线 A 的 `build-all.ps1` 用法：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1                 # 全部 target
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 -Only 1.21,1.20  # 只跑指定
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 -NoDaemon        # 跑完杀 daemon（CI 必须）
```

> `-NoDaemon` 一定要开：Gradle daemon 会继承 stdout，`execFileSync` 等 EOF 会挂到超时
> （2026-10-28 实测：产物早好了，步骤就是不返回）。

### 3.3 脚本分类（87 个，别一个个点开）

| 类别 | 脚本 |
| --- | --- |
| **全量/单步构建** | `rebuild-all.mjs`（41 步总调度 + publish）、`build-all.ps1`、`build-neoforge.mjs`、`build-srg-legacy.mjs`、`build-11{22,32}.mjs`、`build-17{10,2}forge.mjs`、`build-1122forge.mjs` |
| **映射/SRG 管线** | `mk-srg-mojmap.mjs`、`mk-srg-srgv0.mjs`、`mk-srg-1710.mjs`、`mk-mcp-srg.mjs`、`mk-legacy-named.mjs`、`mk-1122-named.mjs`、`support-17x.mjs` |
| **Forge 1.7.2 字节码补丁** | `lwfix172.mjs`（`<class> <dir>` / `--emit-helper <dir>`）+ `modx/lwfix/` 源 |
| **探针** | `build-probe.ps1`（编）、`put-mod.mjs`（装 jar 进实例）、`mk-world.mjs` / `clone-world.mjs` / `nbt.mjs`（造存档） |
| **验收** | `verify-gui-one.mjs`（单实例像素）、`verify-gui.ps1`（多 target）、`verify-legacy-forge.mjs`、`verify-rightclick.mjs`、`verify-download-tab.mjs`、`verify-download-page.mjs`、`verify-fabric-all.mjs`、`verify-matrix.ps1` |
| **环境/发布** | `portable-jdk.mjs`（换机器改路径）、`pack-release.ps1`、`rename-shims.mjs` |
| **一次性迁移脚本** | `_*.mjs`、`fix-*.mjs`、`java8-*.mjs`、`widen-*.mjs`、`split-*.mjs`、`add-*.mjs` —— 干完就完了，**别当工具用，但别删**（它们记录了当时的改法） |

### 3.4 探针（`tools/probe/`，离线跑生产代码）

为什么存在：`core/` 只有一个文件引用 `net.minecraft.client.MinecraftClient`（`Config.dir()`），
一个桩（`tools/probe/net/minecraft/client/MinecraftClient.java`）就能把整个 core 编出来在**没有游戏**的情况下跑。

| 探针 | 干什么 |
| --- | --- |
| `ProbeInstall` | 跑生产代码 `InstallJob` 装一个「版本×加载器」，输出日志 |
| `ProbeLaunch` | 跑生产代码 `Launcher` 起游戏，`<gameDir> <id> <秒> [waitForMarker]` |
| `ProbeRightClick` | 起游戏 → 自动进存档 → `/give` → 用驱动级 `keybd_event`/`mouse_event` 真右击 → 查日志 |
| `ProbeGuiClick` | 真鼠标点界面上的具体坐标（下载页页签验收用） |
| `ProbeJava` / `ProbeJavaRuntime` / `ProbeMaven` | Java 探测 / 运行时下载 / maven 连通性 |

---

## 4. 日常命令（复制粘贴区）

```powershell
# 改了 mod/src/main 里任何东西 → 全量重建（41 步 + publish）
node scripts\rebuild-all.mjs
node scripts\rebuild-all.mjs forge          # 只跑名字含 forge 的步骤
node scripts\rebuild-all.mjs --list         # 看有哪些步骤

# 装到测试实例再起
node scripts\put-mod.mjs V1.12.2-FORGE watercraftlauncher-forge-1.0.0-mc1.12.2.jar
node scripts\verify-gui-one.mjs V1.12.2-FORGE

# 改了核心页面代码后的最小回归（17 个 target，约 20 分钟）
powershell -ExecutionPolicy Bypass -File scripts\verify-gui.ps1
$env:MCVER_TARGETS='1.21.4,1.20.1'; powershell -ExecutionPolicy Bypass -File scripts\verify-gui.ps1

# 老 Forge（1.7.2~1.12.2）物品三件套
node scripts\verify-legacy-forge.mjs
node scripts\verify-legacy-forge.mjs 1.12.2      # 只跑一个 MC 版本

# 真右键（现代版本；需要存档已存在，用 mk-world.mjs 造）
node scripts\verify-rightclick.mjs V1.21.4-FORGE --world 新的世界

# 下载页分类页签（真鼠标点）
node scripts\verify-download-tab.mjs V1.21.4-FABRIC --tab 快照

# 全矩阵 L2（装+启，几小时）
powershell -ExecutionPolicy Bypass -File scripts\verify-matrix.ps1 -Only 1.21.4
```

**纪律**：每次改动都要有「真进程 + 真像素/真日志」的证据，不接受「日志里有 `screen=MAIN`」这种代码路径证据。

---

## 5. 不在 git 里、但缺了就编不动的东西

`.gitignore` 排掉了体积大 / 可再生的目录。**换机器时最容易在这里翻车**。

| 目录 | 大小 | 干什么用 | 没有会怎样 | 怎么补 |
| --- | --- | --- | --- | --- |
| **`testgame/`** | 10.5 GB | ① `libraries/` 是管线 C/D 的编译期 classpath ② `versions/` 158 个实例是验收靶子 ③ 存档/options.txt | 管线 C 直接编不过（找不到 `net.minecraft.*`）；所有验收入口失效 | 用启动器或 `ProbeInstall` 重装；`libraries` 也可以从真游戏目录拷 |
| **`modx/srg/`** | 426 MB | 管线 C/D 的 mojmap/SRG jar + 双向映射 | 17 + 7 个 target 全编不动 | `mk-srg-mojmap.mjs`（要 mcp_config 下载 + `testgame/libraries`）/ `mk-srg-srgv0.mjs` / `mk-srg-1710.mjs` |
| **`modx/legacy/<mc>/`** | 278 MB | 管线 B 的 yarn named jar | Legend Fabric 16 个 target 编不动 | `mk-legacy-named.mjs` |
| `modx/build*/` | 32 MB | 每 target 的 gen 源码 + classes | 会重新生成（慢） | 自动 |
| `dist/` | 11 MB | 发布产物 | 无 | `node scripts/rebuild-all.mjs publish` |
| `tools/probe/out/` | 1 MB | 探针 class | 探针跑不了 | `scripts/build-probe.ps1` |
| `config/mcver.json` | — | **本机私有**：游戏目录、账号 token | 启动器要重新配置 | **永远不要拷到别人的机器，也不要提交** |
| `.tools/gradle-8.12/` | 144 MB | 已不存在；`build-all.ps1` 改去 `~/.gradle/wrapper/dists` 找 | 管线 A 失败 | 装 Gradle 8.14.2 或让它下次自己下 |

---

## 6. 已踩过的坑（按域，浓缩版）

### 6.1 构建管线

- **「编不过」先分清是 API 变了还是映射缺了**。后者便宜一个数量级（1.7.2~1.7.8 的 yarn 根本没映射
  `Screen`/`ButtonWidget` 的成员，靠 `javap -c` 反推中间名解决了 41 个文件的编译）。
- **换 target 的正确套路**：先直接编译，让编译器列出真实差异，再写 compat。**别猜**。
- **`modx/srg/` 的 tsrg 有两种格式**：1.17+ 是 tsrg2（srg 类名是 `C_xxxxx_`，类映射恒等）；
  ≤1.16.5 是 tsrg v1（srg 类名就是真包名，**必须真改类名**，如 `gui/FontRenderer` → `Font`）。
  只认 v2 会把整个 v1 文件当脏数据丢掉，然后误判「官方映射残缺」。
- **reobf 必须同时倒类和描述符的命名空间**，只倒类名 → 编得过、跑起来 `NoClassDefFoundError`。
- **老 Forge 的 maven 目录名不规则**：1.7.2 是 `-mc172` 不是 `-1.7.2`；Forge 1.7.x 只有 `1.7.2` 和 `1.7.10` 两个版本。
- **`legacy install_profile` 的 versionInfo 可能不带 `inheritsFrom`**（1.7.2 就没有）→ 原版已存在时 flatten
  不跑，会得到一个「没有客户端 jar」的版本。要按 `install.minecraft` 补。
- **老的 `asm-all:4.1` 读不了 Java 8 字节码**（major 52）→ legacy profile 抬到 `5.0.3`。
- **FML 7.2 用 `Opcodes.ASM4` 建 visitor，接口静态方法一律抛异常** → 主源码不许写
  `Comparator.comparingXxx` / `List.of`。检查工具 `modx/lwfix/IfaceCallScan.java`，要求整包 0 处。
- **字节码补丁优先「转发到静态方法」，不要内联循环**：不加分支就不用重算 `StackMapTable`。
  另外：常量池 `Methodref` 存的是 **NameAndType 下标**；成员头里 `attributes_count` 在 `+6`，
  属性表从 `+8` 才开始。这两条都踩过。

### 6.2 运行期

- **`Setting user` 不等于启动成功**。曾经 `InstallJob` 丢了 `result = id;`，装的是原版却全部报 OK
  （7 个组合中招）。现在 `verify-matrix` 有「主类不能是 `net.minecraft.client.main.Main`」的断言。
- **JVM `@argfile` 的编码是 `sun.jnu.encoding`（中文 Windows = GBK），不是 `file.encoding`**。
  写 UTF-8 → 中文存档名静默失效（游戏照常起，只是找不到存档）。见 `Launcher.argFileCharset()`。
- **classpath 去重必须「同 group:artifact 只留版本最高」**，否则 Fabric 报 `duplicate ASM classes`。
- **`lwjgl:2.9.1-nightly` 有个「只在老 OSX 允许」的 rule**：`plan()` 必须**先按 rules 过滤库、再按版本去重**。
  反了会让三个 lwjgl 全消失 → `NoClassDefFoundError: org/lwjgl/LWJGLException`，而 `missing files=0` 还是绿的。
- **1.20.2+ Forge 不能把原版 jar 放进 classpath**（`ResolutionException`：`Module minecraft contains package ...`）。
  判据是 `BootstrapLauncher` + 存在 `client-*-srg.jar` 两个条件**都**满足，别写成「凡 Forge 就不加」。
- **`renderBackground` 是版本行为**：1.21.1+ 的 `Screen.render` 会自己调，1.20.1/1.19.4/1.18.2 **不会**。
- **`DrawContext#drawText` 返回类型 int→void** 是 1.21 系列唯一真 ABI 断点（同 id `method_51439`），
  `ui/Ctx` 用 `MethodHandle` 按参数类型找，不写名字。
- **右键在 1.20 上必须做边沿检测**：原版 `while (keyUse.wasPressed())` 会把按键计数吃干净；
  但老版本（≤1.12.2）的 `onItemRightClick` 是**原版主动调**的，不需要 workaround。
- **1.21.9+ 首次启动会先弹 `AccessibilityOnboardingScreen`**，自检钩子只认 `TitleScreen` 会永远等不到。

### 6.3 验证脚本（这个项目在验证上栽过 7 次，每次都是「假通过」）

- `($r | Where-Object {...}).Count` 只命中一个对象时是 `$null`（PS 5.1），`-gt 0` 为假 → **FAIL 却打印「全部通过」**。必须 `@(...)`。
- 游戏活着时日志被独占写，`Select-String` 静默读空 → **先杀进程再读**。
- 时间基准要在 `Start-Process` **之前**取。
- `powershell -File` 传 `-Targets a,b` 是**一个**字符串（要自己按逗号拆）。
- 0 个用例也算「全部通过」→ 现在 case 为 0 时 `exit 1`。
- 新实例没有 `options.txt` → 引导屏挡路。
- 新实例没开隔离（缺 `.mcver-isolated`）→ 日志落到 `testgame/logs`，残留旧 jar 还会造成同 mod id 两份。
- **`screen=MAIN` 只证明 `init()` 跑过，不证明画出来了**。像素判据：真窗口 `PrintWindow` +
  数 `#C6C6C6` 命中数 > 10000 + 中心点颜色 + `children=17` + 窗口 `870x519`。

### 6.4 Windows / 工具链

- **PowerShell 5.1 读无 BOM 的 `.ps1` 按 ANSI(GBK) 解**：脚本里有中文就会把引号解析崩 →
  生成的临时 `.ps1` 要带 UTF-8 BOM。
- **`Set-Content -Encoding UTF8` 会写 BOM**，javac 报「非法字符 `\ufeff`」。改 Java 文件用
  `[System.IO.File]::WriteAllText($p,$s,(New-Object System.Text.UTF8Encoding($false)))`。
- **`PostMessage(WM_KEYDOWN)` 不是真按键**：MC 1.21.x 认，1.20.0 完全不认（Esc 都不暂停）。
  要模拟输入用驱动级 `keybd_event`/`mouse_event` + `SetForegroundWindow`，并核对 `GetForegroundWindow()`。
- **中文环境变量不能经 PowerShell 传**：`$env:MCVER_CAT='愚人节'` 会被转 GBK，游戏里对不上、
  静默退回默认页签。要传中文就在 node 里设 `process.env` 再 spawn。
- **老版本日志是 GBK**：外部探针按 UTF-8 读永远匹配不到中文 → **验收用的日志行必须 ASCII 打头**。
- Windows 控制台打印中文乱码是**正常现象**（GBK 控制台 vs UTF-8 文件），别据此判断文件坏了；
  用 `node -e "...s.includes('中文')"` 断言字节内容。

### 6.5 批量改源码

- **`s.replace` 的锚点没命中不会报错**：方法生成了但没人调用，编译通过、日志却没有那两行。
  **改完要断言「调用点」存在，不能只断言方法体存在**。
- **插成员不能拿「行尾」当锚点**（会把代码插进构造器里，javac 报 `';' expected`）。
  要按大括号层级找位置。
- 每次批量改完断言文件里没有 `\uFFFD`（乱码哨兵）。

---

## 7. 未完成 / 下一步

**建议优先做（按价值）**

1. **Mod 管理页**：读 `fabric.mod.json` / `mods.toml` / `quilt.mod.json`，`.disabled` 启停。
   `core/Modrinth.java` 已有只读接口雏形，接上就是一个完整的 Modrinth 下载中心。
2. **老版本真右键的自动化**：目前 1.7.2~1.12.2 的右键只能人工验（这批版本没有 `--quickPlaySingleplayer`）。
   可行方案：**本地服务端 + `--server`**（1.12.2 客户端支持），绕开 quickPlay 限制。
3. **`.mrpack` 整合包导入导出**。
4. **Java 自动下载**扩展：现在只按版本要求下 Mojang 运行时，没有 Adoptium 8/17 的备选。
5. **把 `portable-jdk.mjs` 思路推广**：`portable-jdk.mjs` 只处理 Java 路径；脚本里还有
   `testgame`/`modx` 之类的相对路径（这些是相对 `ROOT` 的，没问题），以及 `verify-matrix.ps1 -GameDir`。

**明确不做的**（第 2.1 节列过，别重复讨论）
Fabric 1.7.9、Forge 1.13.2、Forge/NeoForge 26.1、MC ≤1.6.4、Legacy Fabric 的资源挂载。

**需要用户拍板的**
Fabric ≤1.13.2 的物品图标/配方要不要加 `fabric-resource-loader-v0` 硬依赖（影响是否能「只装 loader 就能用」）。

**技术债**
- `PROGRESS.md` 已 144 KB、36 节，`REPORT.md` 93 KB。新结论应该进 `PROGRESS.md`，
  `REPORT.md` 只当历史流水账，别再往里堆。
- `scripts/` 里 30+ 个一次性迁移脚本（`_*.mjs`、`fix-*.mjs`）没清理。它们记录了当时的改法，
  但现在跑会乱改文件。**只在看懂用途后才用**。
- `testgame122/` 本该进 `.gitignore`，现在有一部分 assets 进了库（138 MB 的仓库里大部分是它）。

---

## 8. 交接检查清单

- [ ] `node scripts/portable-jdk.mjs` 显示 JDK 21 找到了
- [ ] `scripts\build-probe.ps1` 编得过
- [ ] `node scripts\verify-gui-one.mjs V1.21.4-FORGE` → `ok: true` / `hits > 10000` / `children 17`
- [ ] `node scripts\rebuild-all.mjs` → **41/41 成功** + `publish: N 个 jar -> dist`
- [ ] `dist/` 里 **49 个 jar**
- [ ] `node scripts\verify-legacy-forge.mjs` → 7 PASS（需要 JRE 8）
- [ ] 真游戏里右键物品能开面板（`config/mcver.json` 的 `lastDir` 指向本机真实游戏目录）
- [ ] `git status` 干净；没有残留的 `java.exe` / `javaw.exe`

---

## 9. 文档索引（先看哪份）

| 想知道 | 看 |
| --- | --- |
| 怎么在另一台机器上跑起来、代码怎么组织、有哪些坑 | **本文** |
| 现在支持什么、证据是什么 | `PROGRESS.md` §1 / `verification/results.csv` |
| 某个 bug 当初怎么查出来的 | `PROGRESS.md` §2~§36（按轮次） |
| 「支持」到底是什么标准（L0~L3） | `PLAN.md` |
| 验证矩阵怎么跑、哪些组合验过 | `VERIFY.md` |
| 功能说明（给用户看） | `README.md` |
| 原始流水账 | `REPORT.md`（历史，别再往后堆） |
| 当前这一轮在干什么 | `NOW.md` |

---

祝顺利。踩到新坑记得往第 6 节补一条 —— 这一节的价值比代码高。
