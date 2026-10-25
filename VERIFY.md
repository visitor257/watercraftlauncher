# 验证矩阵（P0）

> 目标：把「代码写了」变成「实测可用」。
> 判定标准：装完 + 启动日志里出现 `Setting user:`（离线账号的 401 属正常现象）。
> 探针：临时目录里 `ProbeInstall`（跑完整 InstallJob）+ `ProbeLaunch`（plan + 真启动）。
> 游戏目录：`D:\d`，下载源：BMCLAPI。

## 结果总表

| # | MC | 加载器 | 版本 | 安装 | 启动 | 证据 |
|---|---|---|---|---|---|---|
| 1 | 1.21.4 | Fabric | 0.16.x | ✅ | ✅ | 日常使用，`Loading Minecraft 1.21.4 with Fabric Loader` |
| 2 | 1.21.4 | **Quilt** | 0.20.0-beta.9 | ✅ | ✅ | `Loading Minecraft 1.21.4 with Quilt Loader 0.20.0-beta.9` / `Setting user: Probe` |
| 3 | 1.20.2 | Forge | 48.1.0 | ✅ | ✅ | 今天修的模块冲突后通过 |
| 4 | 1.20.3 | Forge | 49.0.2 | ✅ | ✅ | `1.20.3-forge49.0.2`，Setting user |
| 5 | 1.20.3 | NeoForge | 20.3.8-beta | ✅ | ✅ | install_profile 自装流程 |
| 6 | 1.20.2 | **OptiFine** | HD_U_I7_pre1 | ✅ | ✅ | launchwrapper 路径，`Setting user: Probe` / LWJGL 3.3.2 |
| 7 | 1.20.2 | **Iris** | 1.6.14 + Sodium 0.5.5 | ✅ | ✅ | `Loading Minecraft 1.20.2 with Fabric Loader 0.20.1` |
| 8 | 1.20.1 | **NeoForge** | 47.1.84 | ✅ | ✅ | `Setting user: Probe` / LWJGL 3.3.1（修复 3 个 bug 后才通）|
| 9 | 1.20.1 | **Forge** | 47.x | ✅ | ✅ | `Setting user: Probe` / LWJGL 3.3.1 |
| 10 | 1.12.2 | **Forge** | 14.23.5.2860 | ✅ | ✅ | Java 8 + launchwrapper，`Setting user: Probe` |
| 11 | 1.16.5 | **Fabric** | 0.20.1 | ✅ | ✅ | natives 解压后通过 |

## 待验证

| MC | 加载器 | 说明 |
|---|---|---|
| 1.14.4 | Fabric | Fabric 的下限版本 |
| 23w13a_or_b | Fabric | 快照（愚人节版）路径 |

## 覆盖度（截至本轮）

6 个加载器 × 至少 1 个版本 = **全部 L2**（Fabric / Quilt / Forge / NeoForge / OptiFine / Iris）。
LiteLoader 未实现（仓库近死，见 PLAN P3）。

## 首轮结论

1. **Quilt 之前完全没测过**，现在 L2 通过——这条曾是最大盲区。
2. **OptiFine 的反射安装（`doInstall`）可用**，launchwrapper 走 `minecraftArguments` 也正常。
3. **Iris 走 Modrinth 下载可用**（Iris + Sodium 自动配齐）。
4. 资产下载是主要耗时：全新 MC 版本约 4000 个小文件，实测 10~20 分钟。
5. 安装耗时不可预测时，探针要有超时（当前 40 min）。

## 本轮验证里修掉的 4 个真 bug

| # | 现象 | 根因 | 修复 |
|---|---|---|---|
| 1 | NeoForge 1.20.1 自动选版直接 `SocketTimeoutException` | `installNeoForge()` 直接查 `maven.neoforged.net`（国内不通）| 改用 `LoaderVersions.fetch()` 的多源列表 |
| 2 | 同一版本下载 404 | 1.20.1 时代 artifact 在 `net.neoforged:forge`，文件是 `forge-<mc>-<ver>-installer.jar` | 两套路径都试 |
| 3 | 处理器 `ClassNotFoundException: ...ConsoleTool` | 某些 profile 的 `classpath` 只列依赖、不列工具本体 | 把处理器 jar 强制加到 classpath 首位 |
| 4 | 库下载慢到无法接受（180 个库 ~ 每个 15 秒超时）| maven 回退链里 `maven.neoforged.net` 排在前，且从不记忆失败 | 死主机黑名单 + neoforged 放最后 + **库并行下载（8 线程）** |

效果：NeoForge 1.20.1 的 74 个运行库从「预计 45 分钟」降到「约 2 分钟」。

## 第二轮（老版本）又挖出 3 个

| # | 现象 | 根因 | 修复 |
|---|---|---|---|
| 5 | 1.12.2 启动报「找不到主类 @…\mcver-args-xxx.txt」| **Java 8 不支持 `@argfile`**（Java 9+ 才有）| Java ≤ 8 直接拼命令行，并检查 30000 字符上限 |
| 6 | 计划里报两个 missing（lwjgl-platform / jinput-platform）| 纯 natives 库本来就没有普通 jar | 带 natives 块的库不再计入 missing |
| 7 | **1.13~1.18 启动报 `UnsatisfiedLinkError: lwjgl.dll`** | 合并 json 时同版本的「不含 natives」条目把原版那份挤掉了；另外 natives 只解了第一个 jar | 去重时优先留带 natives/classifiers 的条目；natives 按 **jar 路径**逐个解压 |

## 复跑方法

```powershell
# 1) 准备探针（core + 两个探针 + MinecraftClient 桩）
#    见 PLAN.md / PROGRESS.md 里的「离线探针」说明
# 2) 安装
java -cp out dev.mcver.core.ProbeInstall D:\d 1.21.4 QUILT - 1.21.4-Quilt_test - - iso
# 3) 启动验证
java -cp out dev.mcver.core.ProbeLaunch D:\d 1.21.4-Quilt_test 150
```
