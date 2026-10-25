# 开发计划书：全版本 × 全模组加载器支持

> 版本：2026-10-24 第一版
> 目标读者：接力开发者 / 未来的我
> 配套文档：`PROGRESS.md`（进展报告）、`HANDOVER.md`（坑位手册）、`REPORT.md`（开发流水账）

---

## 1. 目标与「支持」的定义

**产品目标**：Water Craft Launcher 在游戏内为**任意 Minecraft 版本**安装**任意模组加载器**，并能正常启动。

「支持」必须同时满足三条，缺一条只能叫「代码写了」：

| 级别 | 含义 | 对外可宣称 |
|---|---|---|
| L0 判定 | 能正确判断「该 MC 版本 + 该加载器」存不存在 | ❌ |
| L1 安装 | 能下载并装完，版本目录结构正确 | ❌ |
| L2 启动 | 装完能启动到 `Setting user:` / 标题界面 | ✅ 支持 |
| L3 回归 | 有可重复执行的验证脚本，改代码后能一键复跑 | ✅ 稳定支持 |

**验收口径**：任何一条「支持」声明，必须附 `logs/mcver-launch-*.log` 里到 `Setting user` 的证据，或 `scripts/` 下的可复跑脚本。

---

## 2. 现状盘点（2026-10-24）

### 2.1 加载器

| 加载器 | 安装实现 | 验证级别 | 备注 |
|---|---|---|---|
| Fabric | meta API + 生成 profile | **L2**（1.21.4 日常在用） | 覆盖 MC ≥ 1.14 |
| Quilt | meta API + 生成 profile | **L0** | 一次没测过 |
| Forge | installer jar `--installClient` | **L2**（1.20.2 / 1.20.3） | 77 个正式版，有跳跃缺口 |
| NeoForge | installer 优先，回退 install_profile 自装 | **L2**（1.20.3） | ≥ 1.20.1 |
| OptiFine | 反射调 `optifine.Installer.doInstall` | **L0** | 未验证 |
| Iris | Modrinth 下载 mod 文件 | **L0** | 依赖 Fabric |
| LiteLoader | 无 | — | 2017 停更，仓库近死 |

### 2.2 MC 版本

| 范围 | 判定（L0） | 实测（L2） |
|---|---|---|
| 正式版 1.1 → 26.3 | ✅ `LoaderSupport` 规则表 | 1.20.2 / 1.20.3 / 1.21.4 |
| 快照 / 愚人节 | ⚠️ Fabric/Quilt 标 MAYBE | ❌ |
| alpha / beta | ✅ 判定为无加载器支持 | — |

### 2.3 mod 本体多版本

| MC | 产物 | 状态 |
|---|---|---|
| 1.21.4 | `watercraftlauncher-fabric-1.0.0-mc1.21.4.jar` | ✅ 主 target |
| 1.21.1 | `watercraftlauncher-fabric-1.0.0-mc1.21.1.jar` | ✅ 覆盖 2 个文件即通 |
| 1.20.1 及更早 | — | ❌ 未做 |

---

## 3. 技术路线

### 3.1 加载器安装：五种形态，别硬套一套流程

| 类型 | 代表 | 实现方式 |
|---|---|---|
| A. meta API 拼 profile | Fabric / Quilt | 拉 `/versions/loader/<mc>`，自己写版本 json |
| B. installer CLI | Forge 49.x、OptiFine(无 CLI) | `java -jar installer.jar --installClient <dir>` |
| C. install_profile 自装 | NeoForge 20.2+ | 读 `install_profile.json`，跑 processors |
| D. 反射调内部 API | OptiFine | `URLClassLoader` + `doInstall(File)` |
| E. 手写 profile + tweakClass | LiteLoader（未做） | 生成 json + launchwrapper + `--tweakClass` |

**关键纪律**：
- 安装器 CLI 有没有 `--installClient` **不能假设**，要实测（NeoForge 20.2+ 就没有）。
- 版本 id 命名不统一（`forge-1.20.2-48.1.0` vs `1.20.3-forge49.0.2`），**装完用 `findFreshVersionId()` 扫目录找**，不要拼字符串。
- `install_profile` 的 `data` 表里 `[maven]` 多数是**产物路径**，不是下载清单。

### 3.2 mod 本体多版本：单源码 + compat 覆盖

```
mod/src/main/java/                      ← 唯一主源码
mod/src/compat/<target>/java/           ← 只放有差异的「整文件」
mod/gradle.properties                   ← 默认 target（当前 1.21.4）
scripts/build-all.ps1                   ← 一键构建全部 target，产物进 dist/
```

构建：`gradle build -Pcompat=1.21.1 -Pminecraft_version=1.21.1 -Pyarn_mappings=... -Pfabric_api_version=...`

已验证：1.21.4 → 1.21.1 只差 2 处（`Item.Settings.registryKey` 1.21.2+ 才有；`UseItemCallback` 返回值类型变了）。

**不做的事**：不复制整份项目。N 份副本 = 修一个 bug 改 N 处，开源后无人能维护。

### 3.3 兼容判定层

`core/LoaderSupport.java`：**区间规则 + 精确集合**混合。

| 加载器 | 规则 |
|---|---|
| Fabric | `>= 1.14` |
| Quilt | `>= 1.14.4` |
| NeoForge | `>= 1.20.1` |
| Forge | 精确 77 个版本（缺口：1.16、1.17、1.20.5、1.21.2） |
| LiteLoader | 精确 16 个版本 |

快照/愚人节：Fabric/Quilt 返回 `MAYBE`（交真实查询），Forge/NeoForge 直接 `NO`。

### 3.4 数据源与回退顺序

| 用途 | 顺序 |
|---|---|
| 版本清单 | 当前下载源 → BMCLAPI → 官方 |
| maven 库 | 当前源 `/maven/` → BMCLAPI → maven.neoforged.net → maven.minecraftforge.net → Maven Central |
| Fabric/Quilt | 官方 meta（`meta.fabricmc.net` / `meta.quiltmc.org`） |

**铁律：所有下载必须带启动器 UA**（`WaterCraftLauncher/1.0`）。BMCLAPI 对 UA 敏感：浏览器 UA 会拿到 929 字节拦截页、空 UA 403。

---

## 4. 阶段计划

### P0 — 验证矩阵（预计 1~2 人日）

**目标**：把现有「代码写了」的项变成 L2，产出一张真实支持表。

矩阵（每个至少装一次 + 启动到 `Setting user`）：

```
Quilt    1.20.4 / 1.21.4
Forge    1.20.1 / 1.20.3（1.20.3 已过）
NeoForge 1.20.1 / 1.20.3（已过）/ 1.21.4
Fabric   1.14.4 / 1.16.5 / 1.20.1
OptiFine 1.20.1
Iris     1.21.4（+ Fabric）
Fabric   23w13a_or_b（快照验证）
```

**交付**：`scripts/verify-matrix.ps1` + 一份真实支持表（成功/失败/失败原因）。

### P1 — 现代版本全覆盖（2~3 人日）

- 修 P0 暴露的问题；
- 覆盖 1.20.x / 1.21.x 所有正式版的安装路径（自动化跑一遍，记录失败点）；
- 下载页对「不存在组合」给准确提示（已做，需回归）。

### P2 — 老版本 target（3~5 人日，逐步）

顺序按用户价值：1.20.1 → 1.19.4 → 1.18.2 → 1.16.5 → 1.12.2。

每加一个 target 的流程：
1. `gradle.properties` 加一组参数；
2. 编译，按报错写 `src/compat/<target>/`；
3. `build-all.ps1` 加一行；
4. 启动验证（`runClient` 或实机）。

**注意**：1.20.5 换组件系统（NBT → DataComponent）是最大断层；1.12.2 基本等于重写（GUI 体系、渲染、资源路径全变），需要单独评估。

### P3 — LiteLoader：**已决定不做**（2026-10-25 用户确认）

理由：`versions.json` 可读（16 个版本，2017-11-29 停更），但 jar 所在 `repo.mumfrey.com`
只剩时间戳 snapshot 重定向，实测 **8 KB/s**（1.68 MB 文件 60 秒只下 470 KB）。

**行动项**：README / 启动器界面明确写「不支持 LiteLoader」及原因，不做静默失败。
白名单 `LoaderSupport.liteLoaderVersions()` 保留，日后要接可直接用。

### P4 — 开源准备（1 人日）

- `git init`（**必须先有 `.gitignore`**，仓库里 1.15 GB 垃圾不能进历史）；
- 文档脱敏（`D:\Program Files\...` 换成占位符）；
- `LICENSE`（MIT）+ `NOTICE`（Fabric Loader/API 为 Apache-2.0；yarn mappings 条款）；
- GitHub Actions matrix 构建（对应 `build-all.ps1`）；
- README 加安全说明（`config/mcver.json` 明文存 refresh token）。

---

## 5. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| `maven.neoforged.net` 在国内不可达 | NeoForge 装不完 | BMCLAPI 五档回退；缺件时按邻近补丁版本回退（已实现） |
| 镜像按 UA 区分 | 误判「镜像没文件」 | 固定启动器 UA；测试也带 UA |
| 安装器 CLI 差异 | 装不上 | 先试 CLI，失败走 install_profile 自装（已实现） |
| API 断裂（跨 MC 大版本） | mod 编译不过 | compat 覆盖，一次只碰 1~2 个文件 |
| Mojang EULA / 版号 | 公开分发风险 | 不随包分发任何 Mojang 资产；README 说明定位 |
| 版本 id 命名漂移 | 改名/隔离/合并全失败 | 一律装后扫目录取 id |

---

## 6. 待用户决策

1. **LiteLoader**：明确不支持，还是花 0.5 天做（下载不稳）？
2. **老版本优先级**：1.20.1 是刚需吗？1.16.5 / 1.12.2 要不要进计划？
3. **开源许可**：MIT + NOTICE 可以吗？作者名用 `visitor257`？
4. **验证范围**：P0 矩阵全跑（1~2 天），还是只跑「1.20.1 全线」？
