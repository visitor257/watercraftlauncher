# 接力说明（2026-11-01 交接包）

这个 zip 是 **2026-10-31 ~ 11-01 两轮工作的成果快照**，用来在另一台电脑上继续开发。
先读 `HANDOVER.md`（工程全貌、代码地图、踩过的坑），再读 `REPORT-2026-11-01.md`（这一轮的逐条过程与证据）。

---

## 0. 包里有什么 / 没什么

| 有 | 说明 |
| --- | --- |
| 源码 | `mod/src/`（主源码 + `compat/`）、`tools/probe/`、`spike/` 外的全部代码 |
| 脚本 | `scripts/`（构建、映射、验收、发布，零 npm 依赖） |
| 垫片 | `modx/` 里各世代的 overlay（`forge142` `forge215` `forge219` `neoforge215/216/219` 等） |
| **产物** | `dist/` 全部 **85 个 jar**（fabric 25 / forge 44 / neoforge 16）+ `SHA256SUMS`，已与 GitHub release 上那份**逐字节一致**（86/86 校验通过） |
| 验证记录 | `logs/coverage-verify.json`、`logs/build/gui-*.png`（25 个 target 的真窗口截图） |
| 历史 | `.git`（含全部提交，HEAD = `55c3ed3`） |

| 没有 | 为什么 | 怎么补 |
| --- | --- | --- |
| `testgame/`（6.0 GB） | 实例 + assets + libraries，可重生 | 见 §2；或直接从旧机器拷 |
| `modx/srg/`（680 MB） | mojmap jar + 映射中间产物 | 见 §3，`mk-srg-mojmap.mjs` / 两个 coverage 驱动会自动生成 |
| `.tools/`（492 MB） | JDK 11、JRE 8u302、Gradle 发行版 | 自己装 JDK 21 + Gradle 8.14.2；旧 JDK 只有验 1.16.x 时才需要 |
| `config/mcver.json` | **含账号信息**，绝不能带走 | 首次运行会重建；用 `portable-jdk.mjs` 之后手动指游戏目录 |
| `mod/build/`、`modx/build/`、`modx/out/` | Gradle / javac 中间产物 | 重编时自动生成 |

---

## 1. 新机器准备

| 东西 | 版本 | 备注 |
| --- | --- | --- |
| Windows 10/11 | — | 验收脚本用 `PrintWindow` / `keybd_event`，别指望 Linux |
| PowerShell **5.1** | 系统自带 | `build-all.ps1` / `verify-gui.ps1` / `release-replace.ps1` 都是 5.1 写的 |
| **Node.js** | **≥ 20.11**（本机 24.21） | 脚本用 `import.meta.dirname`；零 npm 依赖，不用 `npm install` |
| **JDK 21** | 任意 21.x | `javac` / `javap` / `jar` 全走它 |
| JRE 8 | 1.8.0_3xx | 只在启动 1.7~1.16 老版本时用 |
| Gradle | 8.14.2 | 只有 Fabric 那一步用；`build-all.ps1` 会自己找 |
| 网络 | Mojang + BMCLAPI | `bmclapi2.bangbang93.com`；NeoForge 官方 maven 在国内基本不通 |

```powershell
# 解压后先确认代码状态
cd <解压目录>
git log --oneline -1          # 应该是 55c3ed3
git status --short            # 应该只有 scripts/ 下若干「本机路径适配」改动

# 1) 把写死的 JDK 路径改成这台机器的
node scripts\portable-jdk.mjs            # 先看报告
node scripts\portable-jdk.mjs --write    # 确认后改

# 2) 编探针（30 秒左右）
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build-probe.ps1
```

---

## 2. 重建 `testgame/`（编译期 classpath + 验收靶子）

最省事的办法是让两个 coverage 驱动自己装（它们的第一步就是 `ProbeInstall`，幂等）：

```powershell
# Forge（modern 段：1.20.3 / 1.21 / 1.21.3 / 1.21.5~1.21.11）
node scripts\build-forge-coverage.mjs modern

# Forge（SRG 段：1.15 / 1.15.1 / 1.16.1~1.16.4 / 1.18 / 1.18.1 / 1.19~1.19.3 / 1.20）
node scripts\build-forge-coverage.mjs srg

# Forge 1.14.2 / 1.14.3（先生成 1.14.4 的映射当来源）
node scripts\mk-srg-mojmap.mjs 1.14.4 --forge 28.2.30 --patched
node scripts\build-forge-coverage.mjs "1.14.2,1.14.3"
```

```powershell
# NeoForge（11 个：1.20.3 / 1.20.5 / 1.21 / 1.21.2 / 1.21.5~1.21.11）
node scripts\build-neo-coverage.mjs --list
node scripts\build-neo-coverage.mjs
```

每个版本「装 + 编」大约 1~6 分钟（取决于下载）。失败会写在 `logs/{forge,neo}-coverage/<mc>.log`。

> 只想跑验收、不想重编的话：也可以只用 `ProbeInstall` 装实例：
> `java -cp tools\probe\out dev.mcver.probe.ProbeInstall testgame <mc> FORGE <forgeVer> NONE - - 1`

---

## 3. 映射与重建全量产物

`dist/` 里已经是最终产物，**只有改了源码才需要重编**。

```powershell
node scripts\rebuild-all.mjs            # 41 步 + publish（Fabric 走 Gradle，其余走 javac 管线）
node scripts\rebuild-all.mjs --list     # 看有哪些步骤
```

映射相关的脚本（一般由上面的驱动自动调用，不用手动跑）：

| 脚本 | 干什么 |
| --- | --- |
| `scripts\mk-srg-mojmap.mjs <mc> --forge <ver> [--patched] [--tinys-from 1.14.4]` | 生成 `modx/srg/<mc>/` 的 mojmap jar 与双向映射；`--tinys-from` 是「借映射」（1.14.2/1.14.3 用） |
| `scripts\mk-srg-1710.mjs` / `mk-srg-srgv0.mjs` / `mk-mcp-srg.mjs` | 更老的 SRG 世代 |
| `scripts\mk-legacy-named.mjs` / `mk-1122-named.mjs` | Legacy Fabric 的 yarn named jar |

---

## 4. 验收

```powershell
# 批量像素验收（实例必须已存在；脚本会先清共享 testgame/mods 防串味）
node scripts\verify-coverage-batch.mjs
node scripts\verify-coverage-batch.mjs 1.16           # 只跑名字含这个子串的

# 单个实例
node scripts\verify-gui-one.mjs 1.14.2-forge-26.0.63 --timeout 240
```

判据（和项目既有标准一致）：日志出现 `screen=MAIN chrome` → `PrintWindow` 抓真窗口 →
`#C6C6C6` 命中 > 10000、中心点 `C6C6C6`、`children=17`、窗口 `870x519`。

**已验证的 25 个 target** 记录在 `logs/coverage-verify.json`；两个没验成的（Forge 1.16.3/1.16.4）原因见
`REPORT-2026-11-01.md` §3.1 与 `HANDOVER.md` §6.1 —— 是上游 ModLauncher 6.1 与本机 JDK 的兼容问题，
**不是 mod 的问题，别去改 mod**。

---

## 5. 发布（替换 release 资产）

```powershell
$env:GH_TOKEN = 'ghp_...'      # 需要 repo 权限；不要写进脚本（会被脱敏成 *** → 401）
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\release-replace.ps1                # 演练
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\release-replace.ps1 -Apply -Verify  # 真替换并校验
git push origin main
```

脚本约定：**就地替换 v1.0.0 的资产、不新建 tag**；同名资产先删后传；`SHA256SUMS` 最后传。
本次结果：50 → **86** 个资产（85 jar + SHA256SUMS），从远端下载回来校验 **86/86 通过**。

---

## 6. 这轮的关键结论（省得重新踩）

1. **1.14 之后（不含 26.x）产物全覆盖**：43/43 个正式版；Forge 是「上游有构建的 37 个」全覆盖，
   `1.14` `1.14.1` `1.16` `1.17` `1.20.5` `1.21.2` 上游根本没发过 Forge。
2. **`mcp_config` 有三种布局**：`*-mappings.tsrg` / `config/joined.tsrg` / `*-srg2off.jar`（CSV 名字表，1.21+）。
3. **没有官方映射的世代可以借映射**：srg 名跨补丁稳定，1.14.2/1.14.3 直接借 1.14.4 的 tiny 表。
4. **Forge 26/27 没有 `DeferredRegister`**：1.14.2/1.14.3 的 `modx/forge142` 用 `RegistryEvent` 注册。
5. **NeoForge 和 Forge 的事件总线不是同步换的**：21.5 仍是 `IEventBus`；21.6 去掉 `EventBusSubscriber.bus`；
   21.9 起 `Window#handle()` / `keyPressed(KeyInput)`；21.10 起只发 universal jar。
6. **NeoForge 20.1 的 mod id 是 `forge`**：用 Forge 1.20.1 的 jar，别单独出货。
7. 细节全在 `HANDOVER.md` §6.1（新加了 10 条）。
