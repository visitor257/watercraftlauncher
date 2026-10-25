# 进展报告：截至 2026-10-26

> 计划书：`PLAN.md` · 坑位手册：`HANDOVER.md` · 开发流水账：`REPORT.md`
> 验证矩阵（原始记录）：`VERIFY.md` + `verification/results.csv`
> 本报告 = 提炼层：**能宣称什么、不能宣称什么、下一步干什么**。

---

## 0. 一句话

**6 个加载器（Fabric / Quilt / Forge / NeoForge / OptiFine / Iris）在 46 个「MC 版本 × 加载器」
组合上实测到 L2（装完 + 启动到 `Setting user`）**，覆盖 1.7.10 → 1.26 的每个版本纪元断层。
这轮挖出并修掉 **8 个真 bug**（3 个是崩溃/阻塞级：缺 Java 运行时自动下载、老安装器无 `--installClient`、
OptiFine 用错 Java），另外修掉 **3 个会造出假验证结果的工具链坑**。

**仍然不能对外的说法**：「所有版本全支持」。现在能说的是：
**「按版本断层实测 46 个组合，全部通过；其余版本按规则表判定，未逐版本实测」**。

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
| **26.1** | Fabric | L2（需要 Java 25，启动器会自动下）|

按加载器：Fabric 16 · Forge 14 · NeoForge 6 · Quilt 4 · OptiFine 4 · Iris 2 = **46 个 L2**。

### 1.1 已知例外 / 未验证（别当成支持）

| 项 | 状态 |
|---|---|
| **26.1 Forge / 26.1 NeoForge** | 未通过（26.x 要 Java 25，用户决定暂缓）|
| 1.14.1/1.14.2/1.14.3、1.15/1.15.1、1.16.1~1.16.4、1.17 其余、1.20 其余… | 规则表判定可装，**未逐版本实测** |
| 快照 / 愚人节版 | 未跑（Fabric/Quilt 规则表标 MAYBE）|
| mod 本体 | 只出 1.21.4 / 1.21.1 两个包 |

> 若要把「全版本」这三个字用起来，得按加载器把 192 个组合各装一遍（估 10+ 小时机器时间）。
> 现在的口径是**断层覆盖**，不是全量验证。

---

## 2. 这轮挖出并修掉的 8 个真 bug

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

## 3. 验证工具链的 3 个坑（会造假结果）

1. PowerShell 5.1 把原生命令 stderr 当异常 → 一行警告跑挂整批 → 改 .NET `Process` 收流。
2. 矩阵运行期间重编探针 → `.class` 被删 → 后续全 `NoClassDefFoundError`（**造出 11 个假失败**）→ `.run.lock` 互斥。
3. `javapath\java.exe` 是壳，kill 父留僵尸 java 写同一目录 → 用 `JAVA_HOME` 真实路径 + 锁。

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
| 产物 | `dist/watercraftlauncher-fabric-1.0.0-mc{1.21.4,1.21.1}.jar`（约 243 KB）|
| 游戏内实际加载 | `<.minecraft>/versions/1.21.4-Fabric 0.19.5/mods/` |
| 版本控制 | **已是 git 仓库**（本地，未配 remote；两个提交：baseline / 46-L2 修复） |

部署三处（少一处就有人测到旧版）：`mod/build/libs` → `dist/` 与项目根 → 游戏 `mods/`。

---

## 6. 还没做到的

### 6.1 验证缺口
- 26.1 的 Forge / NeoForge（要 Java 25，用户暂缓；启动器能力其实已具备）。
- 103 个正式版里只实测了 22 个 MC 版本 × 部分加载器。
- 快照 / 愚人节版一个没跑。

### 6.2 已知不阻塞问题
- `config/mcver.json` 明文存 refresh token（与 PCL/HMCL 一致，开源需在 README 提醒）。
- mod 本体只有 1.21.4 / 1.21.1 两个 target。
- 还没配 git remote（按用户要求：现在不上 GitHub）。

---

## 7. 接下来

| 阶段 | 内容 | 状态 |
|---|---|---|
| P0 验证矩阵 | 46/47 L2 | ✅ 完成（26.x 暂缓）|
| P1 现代版本全覆盖 | 1.20.x / 1.21.x 逐版本扫安装 | 未做（断层已覆盖）|
| P2 老版本 target | 1.20.1 → 1.19.4 → 1.18.2 → 1.16.5 / 1.12.2 | 未做 |
| P4 开源准备 | LICENSE / NOTICE / 文档脱敏 / Actions | 未做（用户：暂不上 GitHub）|

---

## 8. 等用户拍板

1. 26.x 什么时候做（需要装 Java 25，启动器已能自动下）？
2. P1 全量扫（10+ 小时机器时间）要不要做，还是维持「断层覆盖 + 规则外推」？
3. 老版本 mod target（1.20.1 起）优先级？
