# 当前进度速查（2026-10-29 16:40）

> 这份是**临时状态快照**，给人快速看「现在到哪了 / 下一步该干什么」。
> 详细流水账在 `PROGRESS.md`（§1 – §35），坑位手册在 `HANDOVER.md`。

---

## 一句话

**mod 本体 49 个 jar（Fabric/Legacy Fabric 25 / Forge 19 / NeoForge 5）** ——
本轮把 **1.7 全系**铺完了（1.7 / 1.7.1 ~ 1.7.8 / 1.7.10，9 个新 jar，逐个像素验收 PASS），
**并硬干掉了 Forge 1.7.2**（上游 FML 7.2 在 Java 8 上必崩，靠安装时打的一处字节码补丁绕过）；
三个加载器实机真右键全 PASS；`dist/` 已全量重编（41 步）。
做不了的：Fabric **1.7.9**（上游没 yarn + loader 报 `Unfixable conflicts`）、
Forge 1.13.2（无 mojmap）、Forge/NeoForge 26.1（class 69 / Java 25）、≤1.6.4（没加载器）。

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

## 1.6 本轮第三件：1.7 全系补齐（1.7 / 1.7.1 ~ 1.7.8 / 1.7.10）

用户问「1.7.2 怎么没有？其他版本呢？」。之前只做了 1.7.10，理由是「1.7.2 没有
`MinecraftClient#execute`，跨 1.7.x 一个 jar 盖不住」。这次逐个补：

- **真正的原因不是 API 不同，是映射不全**：1.7.2~1.7.8 的 Legacy Fabric yarn
  **没映射 `Screen`/`ButtonWidget` 的任何成员**（jar 里就是 `method_21925`/`field_22535`）。
  除这两个类，其余映射跟 1.7.10 完全一样。
- **中间名是 javap -c 逐个认出来的**（签名唯一的直接定；7 个空 `()V` 靠方法体调的
  `Mouse.next`/`Keyboard.getEventKey`/`bindTexture` 区分；tick vs removed 反查 `MinecraftClient` 调用点）。
  完整对照表在 `compat/1.7.2/.../ChromeScreen.java` 顶部。
- **差异关在基类里**：`ChromeScreen` 自己声明 `width/height/textRenderer` 影子字段（每次
  init/render/tick 从 `field_225xx` 同步），并把 `method_21947/21936` 转调自己的 `init()/tick()` ——
  于是 41 个共享源文件、`MainScreen` 一个字都不用改。
- **没有 `execute` 就自己排队**：`core/Ui.exec` 改成「主线程立刻跑、否则入队」，
  `ChromeScreen#tick` 每 tick 抽一次（≤50ms）。**但自检钩子必须直接调 `openMain()`**——
  界面还没开时没人 drain，排队就是死锁（踩过）。
- 1.7 首次启动刷 600+ 资源要 >60s，自检轮询窗口从 60s 放宽到 5 分钟。
- 顺手修：`build-1132` 的 classpath 不跟 `inheritsFrom`（1.7 没被 flatten，原版 25 个库在父 json 里
  → 编 1.7 报「找不到 log4j」）；`pack.mcmeta` 补进所有 legacy target。

| 版本 | 结果 |
| --- | --- |
| 1.7 / 1.7.1 / 1.7.2 / 1.7.3 / 1.7.4 / 1.7.5 / 1.7.6 / 1.7.7 / 1.7.8 / 1.7.10 | 全部 PASS（中心 `C6C6C6`、children 17） |
| 1.7.9 | **做不了**：meta 上没有 yarn；loader 自己 `Unfixable conflicts` |

---

## 1.7 本轮第四件：Forge 1.7.x —— 只有 1.7.2 和 1.7.10，两个都做了

查了 maven：**Forge 1.7 这一代只有两个正经版本** —— `1.7.2`（159 build）和 `1.7.10`（163 build），
1.7 / 1.7.1 / 1.7.3~1.7.9 **Forge 根本就没出过**。1.7.10 早就有，所以只剩 1.7.2 要看。

**装 + 编 都通了**（顺手修了两个真 bug）：
- Forge 1.7.2 的 maven 目录名是 `-mc172` 不是 `-1.7.2`（BMCLAPI 的 `branch` 字段）→ `installForge` 加第三套候选。
- legacy install_profile 的 versionInfo **可能不带 `inheritsFrom`**（1.7.2 就没有）→ 原版已存在时
  会得到一个没有客户端 jar 的版本；现在按 `install.minecraft` 补上。
- `mk-srg-1710.mjs` 支持 `--mc`；`build-172forge.mjs` + `modx/{shim172,forge172}` 编译一次就过，
  只差 `Minecraft#func_152344_a`（1.7.6 之后才有）→ 改成自己排队的 tick 队列。

**但游戏起不来，而且跟 mod 无关**（拿走 mod jar 一样崩）：

```
java.util.ConcurrentModificationException at ArrayList$Itr.remove
  at net.minecraft.launchwrapper.Launch.launch(Launch.java:117)
```

根因（已用 20 行最小用例在本机 JRE 1.8.0_381 复现）：Java 8 的 `Collections.sort` → `ArrayList.sort`
**结尾无条件 `modCount++`**；FML 7.2 的 `sortTweakList()` 就在 launchwrapper 正用 `Iterator` 遍历的
那个 ArrayList 上原地排序 → 下一次 `it.remove()` 必崩。**FML 7.10（1.7.10）改成了
`toArray` + `Arrays.sort` + `List.set`**（javap 比对确认），所以 1.7.10 没事。

测过的组合都不行：lw 1.9 / lw 1.12、最早 build 10.12.0.969 到最新 10.12.2.1161。
**Forge 1.7.2 当年是配 Java 7 跑的**；而 Java 7 这条路也堵 —— 我们 mod 是 Java 8 语言级、
字节码 52，JRE 7 直接 `UnsupportedClassVersionError`。

**结论：Forge 1.7.x = 1.7.2 + 1.7.10，两个都 PASS（1.7.2 是 14952 hits / children 17）。**

### 1.7.2 是怎么救回来的（用户问了一句「能不能解决」）

四个卡点，全过：

| # | 卡点 | 做法 |
| --- | --- | --- |
| ① | FML 7.2 的 `sortTweakList` 在 Java 8 上必崩 | **安装时给 Forge universal jar 打字节码补丁**：常量池追加 6 条、把 `Collections.sort` 的操作数改成注入的 `McverSortFix.sort(List,Object)`（就是 1.7.10 的写法）。`core/Forge172Fix.java`，幂等（marker 条目） |
| ② | `asm-all:4.1` 读不了 Java 8 字节码（major 52） | `LoaderInstaller.bumpLegacyAsm()` 抬到 **5.0.3**（1.7.10 用的是同一个） |
| ③ | FML 7.2 按 **ASM 4 的 API** 构造 visitor → 碰「接口上的静态方法」就抛 | 主源码里 4 处 `Comparator.comparingLong/reverseOrder` 改成匿名 `Comparator` / `Collections.reverseOrder()`；扫描器复查整包 **0 处** |
| ④ | 1.7.x 的 FML 查客户端 jar 签名（`0 certificates` → 拒启动） | `Launcher.usesLegacyFml()` 命中 `cpw.mods.fml` 就加 `-Dfml.ignoreInvalidMinecraftCertificates` / `-Dfml.ignorePatchDiscrepancies`（官方启动器/Prism 也这么干） |

另外 `modx/forge172/McVerMod.java` 的 `acceptedMinecraftVersions` 从 `[1.7.10]` 改成 `[1.7.2]`。

**验收方式是「安装器自己搞定」**：干净实例 → `ProbeInstall`（探针跑的是同一份 core 源码）
→ 日志出现 `1.7.2 启动补丁已打` + `asm ... -> 5.0.3` → `verify-gui-one` PASS。
没有手工打补丁、没有临时环境变量。细节见 PROGRESS §35。

---

## 2. 验证现状（诚实版）

| 目标 | 界面（像素） | 真右键 | 备注 |
| --- | --- | --- | --- |
| Forge 1.21.4 | 绿 | **PASS**（新 jar） | 抓图 870x519 |
| Fabric 1.21.4 | 绿 | **PASS**（新 jar） | — |
| NeoForge 20.6 | 绿 | **PASS**（新 jar） | — |
| Fabric 1.20.0 | 绿 | **PASS** | `UseItemCallback` 路径 |
| 其余 36 个 | 像素验收全绿 | 未逐一实测 | 代码同源，按加载器分三类实现 |
| **1.7 全系（10 个版本）** | 逐版本 PASS | 1.7.x 走 `onStartUse`（不依赖按键状态） | 本轮新增 9 个 jar |
| Forge 1.7.2 | — | — | **PASS**（像素验收 children 17）；上游 FML 7.2 在 Java 8 上必崩，靠安装时打字节码补丁绕过（§PROGRESS 35） |
| 远古版（b1.x / a1.x） | — | — | 能装能起，**装不了 mod**（没有 Fabric/Forge） |

**已知瑕疵**：legacy（≤1.13.2）物品图标是紫黑格 —— Legacy Fabric loader 不挂 mod 的 `assets/`，
历史遗留（补 `pack.mcmeta` 也没好），不影响界面与右键。

---

## 3. 常用命令

```powershell
# 全量重建（40 步 + 自动 publish 到 dist）
node scripts/rebuild-all.mjs
node scripts/rebuild-all.mjs forge      # 只跑名字含 "forge" 的步
node scripts/rebuild-all.mjs publish    # 只 publish
node scripts/rebuild-all.mjs --list     # 列步骤

# 1.7 专用：体检映射 / 逐版本构建+像素验收
node scripts/support-17x.mjs 1.7 1.7.1 1.7.9   # 装实例 + 出 named jar + 报映射风格
node scripts/build-17x.mjs                      # 默认 1.7 ~ 1.7.8 全跑

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
