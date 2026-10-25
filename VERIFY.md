# 验证矩阵（L2 = 装完 + 启动到 `Setting user`）

> 目标：把「代码写了」变成「实测可用」。
> 判定标准：装完 + 启动日志里出现 `Setting user:`（离线账号的 401 属正常现象）。
> 探针：`tools/probe/`（`ProbeInstall` 跑生产代码 `InstallJob`，`ProbeLaunch` 跑 `Launcher`）。
> 一键复跑：`scripts/build-probe.ps1` → `scripts/verify-matrix.ps1`
> 结果原始记录：`verification/results.csv`（可断点续跑）、`verification/RESULTS.md`
> 测试目录：`<项目根>/testgame`，下载源 BMCLAPI。

---

## 1. 结果总表（46 个组合全部 L2，2 个已知例外）

| MC | 加载器 | 光影 | 版本 id |
|---|---|---|---|
| 1.7.10 | Forge | — | `V1.7.10-FORGE` |
| 1.10.2 | Forge | — | `V1.10.2-FORGE` |
| 1.12.2 | Forge | — | `V1.12.2-FORGE` |
| 1.12.2 | — | OptiFine | `V1.12.2-NONE-OPTIFINE` |
| 1.14 | Fabric | — | `V1.14-FABRIC` |
| 1.14.4 | Fabric / Quilt / Forge | — | `V1.14.4-*` |
| 1.15.2 | Fabric | — | `V1.15.2-FABRIC` |
| 1.16.5 | Fabric / Forge | — | `V1.16.5-*` |
| 1.17 | Fabric | — | `V1.17-FABRIC` |
| 1.17.1 | Forge | — | `V1.17.1-FORGE` |
| 1.18.2 | Fabric / Quilt / Forge | — | `V1.18.2-*` |
| 1.19.4 | Fabric / Forge | OptiFine | `V1.19.4-*` |
| 1.20.1 | Fabric / Quilt / Forge / NeoForge | OptiFine · Iris | `V1.20.1-*` |
| 1.20.2 | Forge / NeoForge | — | `V1.20.2-*` |
| 1.20.4 | Fabric / Forge / NeoForge | — | `V1.20.4-*` |
| 1.20.5 | Fabric | — | `V1.20.5-FABRIC` |
| 1.20.6 | Fabric / Forge / NeoForge | — | `V1.20.6-*` |
| 1.21.1 | Fabric / Forge / NeoForge | — | `V1.21.1-*` |
| 1.21.4 | Fabric / Quilt / Forge / NeoForge | OptiFine · Iris | `V1.21.4-*` |
| 1.21.5 | Fabric | — | `V1.21.5-FABRIC` |
| 1.21.8 | Fabric | — | `V1.21.8-FABRIC` |
| 26.1 | Fabric | — | `V26.1-FABRIC` |

按加载器统计：Fabric 16 · Forge 14 · NeoForge 6 · Quilt 4 · OptiFine 4 · Iris 2。

**覆盖结论**：6 个加载器全部有 L2 证据；每个「版本纪元断层」（1.7.10 / 1.10.2 / 1.12.2 / 1.14 /
1.16.5 / 1.17 / 1.18.2 / 1.19.4 / 1.20.1 / 1.20.5 组件系统 / 1.21.1 / 1.21.4 / 1.21.8）都实测过。

## 2. 未覆盖 / 已知例外

| 项 | 状态 | 说明 |
|---|---|---|
| **26.1 Forge** | FAIL | `installer 退出码 1`。26.x 需要 Java 25，本机只到 Java 21，暂缓（用户决定） |
| 26.1 NeoForge | 未跑 | 同上 |
| Fabric 1.14.1 / 1.14.2 / 1.14.3、1.15 / 1.15.1、1.16.x 其余、1.17 全线… | 未逐版本实测 | 规则表判定可装，靠断层外推 |
| 快照 / 愚人节版（23w13a_or_b 等） | 未跑 | 规则表对 Fabric/Quilt 标 MAYBE |
| mod 本体（Water Craft Launcher 自己） | 只有 1.21.4 / 1.21.1 两个包 | 启动器本体能跑的 MC 版本 |

> 「支持」= 这一行有日志证据。**没跑的不要对外说支持**，说「按规则表可装、未实测」。

## 3. 这一轮挖出并修掉的 8 个真 bug

| # | 现象 | 根因 | 修复 |
|---|---|---|---|
| 8 | 26.1 Fabric 报「找不到 Java 25」 | 只会扫本机 Java，没有「下载官方运行时」的能力（PCL2/HMCL/官方启动器都有）| 新增 `core/JavaRuntime`：Mojang `all.json` → manifest → 411 文件并行下载到 `<gameDir>/runtime/`；安装链路和启动链路都接上 |
| 9 | 1.20.6 Forge 装到一半 `installer 退出码 1` | Forge 安装器内部 `DownloadUtils` 直连 maven.minecraftforge.net，无镜像/重试/黑名单 | 先用多源链把 `version.json` + `install_profile.json` 声明的库全部预取，再让安装器 `--offline` 跑；失败退 `--mirror BMCLAPI` |
| 10 | NeoForge 1.20.4 / 1.20.6 / 1.21.4 装不上 | 不是「域名不通」，是 BMCLAPI 302 到 `ipv6.mirrors.ustc.edu.cn`，该域**只有 AAAA**，本机 IPv6 不通 → 整链死在 DNS；另 `maven.neoforged.net` 解析到 `157.240.20.8`（Facebook，DNS 污染）| maven 候选链加 BMCLAPI 的 IPv4 镜像 `mirrors.ustc.edu.cn/bmclapi/`、`cernet.mirrors.ustc.edu.cn/bmclapi/` |
| 11 | 1.14.4 Forge 两个库一直下不到 | ① `name` 里的 `1.0.5-shadowed` 是 **version+classifier 混写**，清单 `path` 字段还是错的，只有 `url` 的路径是对的；② 下载到半截（393216/444910 字节）也被当成成功 | 用 `url` 的路径生成镜像候选、按 `path` 落盘；预取加 sha1/size 校验，不合格删掉换源 |
| 12 | 1.7.10 / 1.10.2 Forge 报 `UnrecognizedOptionException: installClient` | 那一代安装器**没有** `--installClient`（只有 `--extract`/`--installServer`），客户端安装当年是 GUI 专属 | 新增 `installViaLegacyProfile()`：读出 `install_profile.json` 的 `install.filePath`（universal jar）+ `versionInfo`，直接铺 libraries 并写版本 json |
| 13 | 老版本安装器用 Java 17 跑（1.7.10 是 2014 年的）| `int need = Math.max(17, r.requiredJava)` 写死 | 先按版本要求的 Java 试（1.7.10→8），失败再退到 17+ |
| 14 | OptiFine 全挂：`NullPointerException ... replace(char,char)` | 矩阵没指定 OptiFine 版本时 `ofId` 是 null | null → 走 `LoaderVersions.optifineList()` 取推荐版 |
| 15 | OptiFine 1.12.2 / 1.19.4 报 `UnsupportedClassVersionError` | 安装器只能在「那个年代的 Java」上跑，而 mod 本体是 Java 21 字节码，同进程反射调用必然用错 Java | 驱动类单独编成 **release 8** 打成 `optifine-runner.jar` 塞进 jar 资源，运行时解出来用目标 Java 起子进程 |

## 4. 验证工具链本身的 3 个坑（会造出假结果）

1. **PowerShell 5.1 把原生命令的 stderr 当异常**：`$ErrorActionPreference='Stop'` 下 java 打一行警告
   就能把整批 25 个组合跑挂 → 改用 .NET `Process` 收流。
2. **矩阵在跑时重编探针**：`build-probe.ps1` 会删 `tools/probe/out/*`，导致后续探针 JVM 全部
   `NoClassDefFoundError`（一次造出 11 个假失败）→ 加 `.run.lock` 互斥。
3. **`javapath\java.exe` 是壳**，kill 父进程会留僵尸 java 继续写同一个游戏目录 → 优先用
   `JAVA_HOME` 的真实路径 + 锁文件。

## 5. 复跑方法

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-probe.ps1     # 编探针（含 Java 8 的 OptiFine 驱动）
powershell -ExecutionPolicy Bypass -File scripts\verify-matrix.ps1   # 全跑
powershell -ExecutionPolicy Bypass -File scripts\verify-matrix.ps1 -Phase 3      # 只跑远古段
powershell -ExecutionPolicy Bypass -File scripts\verify-matrix.ps1 -Only FORGE   # 只跑某加载器
powershell -ExecutionPolicy Bypass -File scripts\verify-matrix.ps1 -Combo 1.20.1-FORGE-NONE -Force
```
