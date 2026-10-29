# 当前进度速查（2026-10-29 13:10）

> 这份是**临时状态快照**，给人快速看「现在到哪了 / 下一步该干什么」。
> 详细流水账在 `PROGRESS.md`（§1 – §30），坑位手册在 `HANDOVER.md`。

---

## 一句话

**mod 本体 39 个 jar（Fabric 16 / Forge 18 / NeoForge 5），三个加载器实机真右键全部 PASS；
`dist/` 已全量重编（31/31）。** 已知缺口仍是 Forge 1.13.2 和 Forge/NeoForge 26.1。

---

## 1. 本轮（2026-10-29 下午）干了三件事

### ① 右键复测 → 揪出 `@argfile` 编码 bug（真用户级 bug）

- 复测 `1.21.4-forge` 时发现游戏起得来但**世界不加载**，停在标题界面。
- 根因：`Launcher.writeArgFile` 把参数文件写成 **UTF-8**，而 JLI 读 `@argfile` 用
  **`sun.jnu.encoding`（中文 Windows = GBK）** → `新的世界` 变成 `鏂扮殑涓栫晫` →
  `--quickPlaySingleplayer` 找不到存档，静默失败。
- 影响面：**中文存档名 / 中文用户名 / 中文游戏目录全废**（`C:\Users\张三` 这种）。
- 修法：`argFileCharset()` 按 `sun.jnu.encoding` 写，装不下才退回 UTF-8。Java 8 路线不受影响。
- 提交 `7a8ef3a`，PROGRESS §30。

### ② 全量重建 + 复测

- `node scripts/rebuild-all.mjs` → **31/31 成功**，`publish` 23 个 jar → `dist/`（仍 39 个）。
- `javap` 抽查 1.21.4-forge 与 1.7.10-forge 都带新的 `argFileCharset`。

| 目标 | 路径 | 结果 |
| --- | --- | --- |
| Forge 1.21.4 | mojmap 轮询 + 边沿检测 | **PASS**（新 jar + 中文存档名 + 抓图 870x519） |
| NeoForge 20.6.144 | 同上 | **PASS** |
| Fabric 1.21.4 | Yarn 轮询 + 边沿检测 | **PASS** |
| Fabric ≤1.20 | `UseItemCallback` 事件 | **PASS**（1.20.0 实例，驱动级右键） |
| Forge 1.7.10~1.12.2 | 物品 `onItemRightClick` | 未实测（老版本没有 quickplay，探针进不了世界） |

### ③ 探针加固 + 新工具

- `ProbeRightClick`：**输入改成驱动级** `keybd_event` / `mouse_event`（`PostMessage` 在 1.20.0 上完全不被认，
  连 Esc 都不暂停）；发键前 `ShowWindow` + `SetForegroundWindow` 并核对 `GetForegroundWindow()`。
- `ProbeRightClick`：抓图前先删旧图（`badrect` 但打印旧路径，骗过人）。
- 临时 `.ps1` 必须带 **UTF-8 BOM**（PS 5.1 读无 BOM 按 GBK 解，中文注释会把引号搞崩）。
- `verify-rightclick.mjs`：起跑前杀掉残留的 `Minecraft*` java（否则 `session.lock` 被占）；
  识别「Game crashed」自动重试一次（quickPlay 固有竞态，Forge 自己的 bug）。
- 新 `scripts/nbt.mjs`：往返无损 NBT 编解码（`level.dat` = gzip(NBT)）。
- 新 `scripts/mk-world.mjs`：起对应版本服务端生超平坦世界 → 拷进实例 → 改 `level.dat`
  （创造 + 允许作弊 + 可选 `--dv` 压 DataVersion）。
- 新 `scripts/clone-world.mjs`：克隆现成存档到别的实例，顺手改 `DataVersion` / 版本名 / 游戏模式。

## 1.5 本轮又修了一个 UX 真 bug：下载页选分类要按两次

用户说「选了分类还得按刷新才显示」。根因：`initList()` 渲染前**从不重算 `shown`**，
`shown` 只在清单加载完成 / 「刷新」按钮（强重拉）时才更新 → 点页签只改了 `cat`，列表还是旧的。
**搜索框同一个坑**（改 query → rebuild，一样不生效）。
修法就一行：`initList()` 里渲染前 `if (!all.isEmpty()) recompute();`（纯本地过滤，不联网）。

新验收工具 `scripts/verify-download-tab.mjs <实例> --tab 快照`：真起游戏 → 自动开下载页 →
从日志里的布局行（`mcver: layout tabs@102,60,70,15,4`）算坐标 → 真鼠标点一下 →
只认点击**之后**新出现的 `mcver: 分类=X 共=N 个`。
**做了反例对照**：注释掉修复后点页签 → FAIL（layout 行出现两次但 分类 行不更新）；
恢复后 PASS（快照 748 / 愚人节 9）。

---

## 2. 验证现状（诚实版）

| 目标 | 界面（像素） | 真右键 | 备注 |
| --- | --- | --- | --- |
| Forge 1.21.4 | 绿 | **PASS**（新 jar） | 抓图 870x519 |
| Fabric 1.21.4 | 绿 | **PASS**（新 jar） | — |
| NeoForge 20.6 | 绿 | **PASS**（新 jar） | — |
| Fabric 1.20.0 | 绿 | **PASS** | `UseItemCallback` 路径 |
| 其余 36 个 | 像素验收全绿 | 未逐一实测 | 代码同源，按加载器分三类实现 |
| 远古版（b1.x / a1.x） | — | — | 能装能起，**装不了 mod**（没有 Fabric/Forge） |

---

## 3. 常用命令

```powershell
# 全量重建（31 步 + 自动 publish 到 dist）
node scripts/rebuild-all.mjs
node scripts/rebuild-all.mjs forge      # 只跑名字含 "forge" 的步
node scripts/rebuild-all.mjs publish    # 只 publish
node scripts/rebuild-all.mjs --list     # 列步骤

# 装 jar
node scripts/put-mod.mjs 1.21.4-forge-54.1.22 watercraftlauncher-forge-1.0.0-mc1.21.4.jar

# 造探针用的存档（服务端生成 + 改 level.dat）
node scripts/mk-world.mjs --mc 1.20.1 --name flat120 --save-into fabric-loader-0.20.1-1.20

# 下载页「点页签自动刷新」验收（真鼠标点）
node scripts/verify-download-tab.mjs V1.21.4-FABRIC --tab 快照

# 界面像素验收（走自检钩子，只证明渲染）
node scripts/verify-gui-one.mjs 1.21.4-forge-54.1.22 --timeout 240

# 真右键验收（走用户真实路径）
node scripts/verify-rightclick.mjs 1.21.4-forge-54.1.22 [--world 新的世界] [--timeout 190]

# 下载页四分类验收
node scripts/verify-download-page.mjs 1.21.4-forge-54.1.22 [--cat 愚人节]

# 编译探针（改过 core/ 或 tools/probe/ 之后）
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build-probe.ps1
```

---

## 4. 下一步（按优先级）

1. **老世代的右键**（Forge 1.7.10~1.12.2 / Legacy Fabric）：没有 `--quickPlaySingleplayer`
   （1.20 才有），探针进不了世界。要做得驱动 GUI 点击（标题 → 单人 → 存档列表，窗口尺寸固定、坐标要算）。
   代码上它们走物品自带的 `onItemRightClick` / `onStartUse`，不依赖按键状态 —— 与已实测的
   Fabric ≤1.20 同级，暂按低风险评估。
2. **等用户验剩下 36 个组合**，或按需批量跑。
4. 两个已知缺口（工作量大、非阻塞）：
   - **Forge 1.13.2**：没有 Mojang 映射（`client_mappings` 从 1.14.4 才有），要走 MCP 自有快照名。
   - **Forge/NeoForge 26.1**：class 69 / Java 25、`ResourceLocation` → `Identifier`、loader 拆模块。

---

## 5. 这一轮学到的规矩

- **参数文件按「读者」的编码写**：JVM `@argfile` 是 `sun.jnu.encoding`，不是 `file.encoding`。
- **静默失败造对照组**：换 ASCII 名字能过 → 立刻定位编码问题。
- **验收工具自己也会骗人**：截图断言要删旧图；残留进程会攥着 `session.lock`；
  单机失焦会暂停（`pauseOnLostFocus:false`）；`PostMessage` 在 1.20 上根本不生效，得用驱动级 `keybd_event`；
  GUI 点按要用 `GetClientRect`+`ClientToScreen` 换算（`GetWindowRect` 含标题栏，差十几像素就点空）。
- **改完交互先造反例**：注释掉修复 → 探针应该 FAIL，恢复 → PASS。没反例的 PASS 可能是探针自己在骗人。
