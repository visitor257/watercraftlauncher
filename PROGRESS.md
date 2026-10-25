# 进展报告：截至 2026-10-25

> 计划书：`PLAN.md` · 坑位手册：`HANDOVER.md` · 开发流水账：`REPORT.md`（第十九~二十六节）
> 验证矩阵原始记录：`VERIFY.md`
> 本报告 = 提炼层：**能宣称什么、不能宣称什么、下一步干什么**。过程细节看 REPORT。

---

## 0. 一句话

原来只有 Fabric 一条腿能走，现在 **6 个模组加载器全部实测到 L2（装完 + 启动到 `Setting user`）**，共 **11 个「MC 版本 × 加载器」组合**验证通过；代价是挖出并修掉 **7 个真 bug**，其中 3 个是崩溃级（Forge 1.20.2 模块冲突、Java 8 参数文件、老版本 lwjgl natives）。

**仍然不能对外的说法**：「支持全版本全模组加载器」。现在能说的是：**6 个加载器在 11 个组合上实测可用，其余版本按规则表判定，未实测**。

---

## 1. 真实支持表（有日志证据）

判定标准：装完 + 启动日志出现 `Setting user:`（离线账号 401 属正常）。
游戏目录 `D:\d`，下载源 BMCLAPI，探针 `ProbeInstall` + `ProbeLaunch`。

| # | MC | 加载器 | 版本 | 级别 |
|---|---|---|---|---|
| 1 | 1.21.4 | Fabric | 0.16.x | L2（日常在用）|
| 2 | 1.21.4 | **Quilt** | 0.20.0-beta.9 | L2 |
| 3 | 1.20.2 | Forge | 48.1.0 | L2 |
| 4 | 1.20.3 | Forge | 49.0.2 | L2 |
| 5 | 1.20.1 | Forge | 47.x | L2 |
| 6 | 1.12.2 | **Forge** | 14.23.5.2860 | L2（Java 8 + launchwrapper）|
| 7 | 1.20.3 | NeoForge | 20.3.8-beta | L2 |
| 8 | 1.20.1 | **NeoForge** | 47.1.84 | L2 |
| 9 | 1.20.2 | **OptiFine** | HD_U_I7_pre1 | L2（反射 doInstall）|
| 10 | 1.20.2 | **Iris** | 1.6.14 + Sodium 0.5.5 | L2 |
| 11 | 1.16.5 | **Fabric** | 0.20.1 | L2（natives 修复后）|

覆盖率：**6 加载器 × ≥1 版本 = 全 L2**（Fabric / Quilt / Forge / NeoForge / OptiFine / Iris）。
LiteLoader：**决定不做**（仓库近死，见 §6.3），白名单保留在 `LoaderSupport.liteLoaderVersions()`。

### 1.1 未验证（别当成支持）

| 项 | 状态 |
|---|---|
| Fabric 1.14.4（Fabric 下限） | 未跑 |
| 快照 `23w13a_or_b`（愚人节版） | 未跑（规则表标 MAYBE）|
| 1.20.x / 1.21.x 其余正式版 | 规则表判定为可装，**未逐版本实测** |
| 1.17 / 1.18 / 1.19 全线 | 未跑 |
| mod 本体的 1.20.1 及更早 target | 未做（只有 1.21.4 / 1.21.1）|

---

## 2. 这两天做完的事

### 2.1 2026-10-24（功能与架构）

| 类别 | 内容 |
|---|---|
| UI（8 项） | 状态行压输入框、按钮无响应、内存设置不落盘、设置页/启动页内存不同步、已装版本打勾去掉、目录框与按钮重叠、账号多了放不下（改底部固定 + 分页）、物品合成配方（一桶水 → 1 个，桶返还）|
| 架构 | `core/LoaderSupport.java` 加载器 × 版本兼容规则表（区间 + 精确集合 + 快照 MAYBE）；`DownloadPage` 前置拦截，不存在的组合不发网络请求 |
| 多版本构建 | 「单源码 + `src/compat/<target>` 整文件覆盖」+ `scripts/build-all.ps1`；实测 1.21.4 → 1.21.1 只差 2 个文件 |
| 产物改名 | `watercraftlauncher-fabric-<ver>-mc<mcver>.jar`（Gradle 必须 `archiveFileName`，只改 `archivesName` 会版本重复）|
| 工程 | `.gitignore`（排除 1.15 GB 垃圾）、`PLAN.md`、`REPORT.md`、`HANDOVER.md` |

### 2.2 2026-10-25（P0 验证矩阵）

- 补齐 **6 个从未实测的加载器组合**：Quilt、OptiFine、Iris、NeoForge 1.20.1、Forge 1.20.1、Forge 1.12.2、Fabric 1.16.5。
- 修掉 **7 个真 bug**（见 §3），其中 3 个崩溃级。
- 性能：NeoForge 1.20.1 的 74 个运行库从"预计 45 分钟"降到"约 2 分钟"；180 个库的下载死主机黑名单 + 8 线程并行。
- LiteLoader 定性：**明确不支持**并写进 PLAN（原因 + 实测数据），不做静默失败。

---

## 3. 修掉的 7 个真 bug（根因都写进 HANDOVER 了）

| # | 现象 | 根因 | 修复 |
|---|---|---|---|
| 1 | NeoForge 自动选版直接 `SocketTimeoutException` | `installNeoForge()` 直连 `maven.neoforged.net`（国内不通）| 改走 `LoaderVersions.fetch()` 多源列表 |
| 2 | NeoForge 1.20.1 下载 404 | 该时代 artifact 在 `net.neoforged:forge`，文件名为 `forge-<mc>-<ver>-installer.jar` | 两套 maven 路径都试 |
| 3 | 处理器 `ClassNotFoundException: ...ConsoleTool` | 部分 profile 的 `classpath` 只列依赖、不列工具本体 | 处理器 jar 强制加到 classpath 首位 |
| 4 | 库下载慢到不可用（180 库 × 15s 超时）| `maven.neoforged.net` 排在前且从不记忆失败 | 死主机黑名单 + neoforged 放最后 + 并行下载（8 线程）|
| 5 | Forge 1.20.2 启动 1 秒闪退（`ResolutionException`）| 加载器自带 `client-*-srg.jar`，又把原版 jar 塞进 `-cp` → 自动模块抢包 | `clientJarProvidedByLoader()` 双条件判定后才排除原版 jar（1.20.3 的 `ForgeBootstrap` 故意不排除）|
| 6 | 1.12.2 启动报「找不到主类 @…\mcver-args.txt」| **Java 8 不支持 `@argfile`**（Java 9+ 才有）| Java ≤ 8 直接拼命令行 + 检查 30000 字符上限 |
| 7 | **1.13~1.18 `UnsatisfiedLinkError: lwjgl.dll`** | 合并 json 时「不含 natives」的同版本条目挤掉了原版那份；且 natives 只解了第一个 jar | 去重优先留带 `natives`/`classifiers` 的条目；natives 按 **jar 路径**逐个解压 |

附带修的判定类问题：纯 natives 库（`lwjgl-platform`、`jinput-platform`）不再被误报为「缺失文件」。

---

## 4. 关键结论（选参/判定/工程纪律）

1. **「支持」必须有级别**：L0 判定 / L1 安装 / L2 启动 / L3 可复跑。没到 L2 不许对外说支持。
2. **安装器 CLI 不能假设**：NeoForge 20.2+ 没有 `--installClient`，只能自己读 `install_profile.json` 跑 processors。
3. **版本 id 命名会漂移**：`forge-1.20.2-48.1.0` vs `1.20.3-forge49.0.2` → 一律装完扫目录，别拼字符串。
4. **BMCLAPI 按 User-Agent 区分**：启动器 UA 拿真文件，浏览器 UA 拿 929 字节拦截页，空 UA 403。所有下载固定 `WaterCraftLauncher/1.0`。
5. **改完代码必须重建部署 jar**：只重编探针会让用户测到旧逻辑（踩过一次）。规矩：build → 拷三处 → 让用户重启游戏。
6. **别复制整份项目做多版本**：单源码 + compat 覆盖，否则修一个 bug 改 N 处。

---

## 5. 产物与部署

| 东西 | 路径 |
|---|---|
| 主源码 | `mod/src/main/java/dev/mcver/`（29 个 java，7624 行）|
| 兼容层 | `mod/src/compat/1.21.1/java/dev/mcver/`（2 个文件）|
| 构建 | `scripts/build-all.ps1`（产物进 `dist/`）|
| 产物（1.21.4） | `dist/watercraftlauncher-fabric-1.0.0-mc1.21.4.jar` |
| 产物（1.21.1） | `dist/watercraftlauncher-fabric-1.0.0-mc1.21.1.jar` |
| 游戏内实际加载 | `<.minecraft>/versions/1.21.4-Fabric 0.19.5/mods/watercraftlauncher-fabric-1.0.0-mc1.21.4.jar` |
| 测试游戏目录 | `D:\d`（1.20.2 Forge / 1.20.3 / 1.20.3-NeoForge / 1.12.2 / 1.16.5 / 1.21.4 …）|

部署三处（少一处就有人测到旧版）：`mod/build/libs` → 项目根 → 游戏 `mods/`。

---

## 6. 还没做到的（诚实版）

### 6.1 验证缺口
- Fabric 1.14.4、快照 23w13a_or_b 未跑。
- 正式版判定覆盖 1.1 → 26.3，但**只实测 11 个组合**。
- mod 本体只出 1.21.4 / 1.21.1 两个包。

### 6.2 已知不阻塞问题
- `config/mcver.json` 明文存 refresh token（与 PCL/HMCL 一致，开源需在 README 提醒）。
- 项目**还不是 git 仓库**（开源前先有 `.gitignore` 再 `git init`，这点已就绪）。

### 6.3 LiteLoader 定性（已决定不做）
`versions.json` 可读（16 个版本，2017-11-29 停更），但 `repo.mumfrey.com` 固定版位 404，
只有时间戳 snapshot 能拿到且**实测 8 KB/s**（1.68 MB 文件 60 秒只下 470 KB）。
→ README / 界面明确写「不支持 LiteLoader」及原因，不静默失败。

---

## 7. 接下来（按 PLAN 阶段）

| 阶段 | 内容 | 预计 |
|---|---|---|
| P0 收尾 | Fabric 1.14.4、快照 23w13a_or_b | 0.5 天 |
| P1 | 1.20.x / 1.21.x 全正式版扫一遍安装，修失败点 | 2~3 天 |
| P2 | 老版本 target：1.20.1 → 1.19.4 → 1.18.2 →（可选 1.16.5 / 1.12.2）| 3~5 天 |
| P4 | 开源准备：`git init` / 文档脱敏 / LICENSE(MIT) + NOTICE / Actions matrix | 1 天 |

---

## 8. 等用户拍板

1. P0 剩余两个（1.14.4 / 快照）现在跑，还是直接进 P1？
2. 老版本 target 的优先级：1.20.1 是刚需吗？1.16.5 / 1.12.2 要不要排进来？
3. 开源：MIT + NOTICE 可以吗？作者名沿用 `visitor257 <85747493+visitor257@users.noreply.github.com>`？
4. 对外话术：未实测的版本要不要在界面标「未验证」而不是「支持」？
