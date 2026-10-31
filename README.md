# McVer — Water Craft Launcher（游戏内的 MC 启动器）

Fabric 客户端 mod（1.21.4，需 Fabric API）。手里拿着**「Water Craft Launcher」**右键，
在游戏里开一个原版风格的面板，能**下载版本 / 装加载器 / 管账号 / 启动别的版本**。

物品图标是**流动的水**（原版 `water_flow` 纹理染成水蓝，16×16 不透明）。

产物：`watercraftlauncher-fabric-1.0.0-mc1.21-1.21.11.jar`（mod id 还是 `mcver`，显示名是 Water Craft Launcher）。当前共 **85 个 jar**：Fabric 25 / Forge 44 / NeoForge 16，覆盖 MC 1.7 ~ 1.21.11

> 要接手开发（换电脑、搭环境、踩过的坑、调试钩子）看 **`HANDOVER.md`**，别只看这份。

## 主界面

一块居中的灰色面板（原版箱子/背包那种），**右上角有个 ✕**：
点它只是把界面收起来，**后台的下载/安装照跑**（任务线程独立，头部会显示「后台 xxx 42%」）。
左边导航，右边是页：

| 页 | 内容 |
|---|---|
| 启动 | **目录切换行**（◀ 当前目录 ▶ ＋）、已装版本列表（滚轮翻）、内存、启动后关当前、启动、下载版本 |
| 下载 | **两层**：版本列表 → 点进某版 → 版本名 + 模组加载器 + 光影加载器 + 各自的版本 |
| 账号 | 离线账号 / 微软正版登录（设备码，不用浏览器回调）/ 设为当前 / 删除 |
| 版本 | 重命名、**删除（真删，二次确认）**、补全文件、打开目录、**看启动日志**、版本隔离 |
| Java | **列出机器上所有 Java**（版本 + 路径）、点选指定、手动粘路径添加、自动扫描 |
| 设置 | 内存、JVM/游戏参数、窗口大小/全屏、下载线程、**下载源管理**、Java（跳到 Java 页）、**游戏目录**、版本隔离 |

面板尺寸 `min(400, 屏宽×88%) × min(232, 屏高×92%)`，背景压暗+模糊，四周能看到世界。
**文字一律不画阴影** —— 浅灰底上深字加影子，小字号下会糊成重影。

## 下载页（PCL / HMCL 那种两级）

1. **版本列表**：一行一个版本，`版本号 + 正式/快照 + 发布日期`，装过的前面打 `✔`。
   搜索、快照开关、滚轮/翻页、手动刷新。清单 920 个版本。
2. **点进某个版本**，出现：
   - **版本名称**：自动填 `1.21.4-Fabric_0.20.1` 这种，可以随便改（改了就按这个名建目录）
   - **模组加载器**：原版 / Fabric / Quilt / Forge / NeoForge
   - **光影加载器**：无 / Iris / OptiFine
   - **版本列表**：当前聚焦的那个组件的可选版本（点加载器切模组版本，点光影切光影版本），带推荐/最新标注，滚轮翻页
   - **开始安装**

安装顺序：原版 → 模组加载器 → 光影 → 改名 → 隔离目录。
Iris 需要 Fabric（选了别的会拦下来）；OptiFine 配原版最稳。

## 多个游戏目录（像 PCL / HMCL 那样）

启动页最上面那行就是目录切换：

```
[◀] [ mod/run ] [▶] [＋]
```

- `◀ ▶` 在已知目录之间翻；切过去以后版本列表、下载目标、安装目录全都跟着变。
- 点中间那格（或 `＋`）进**目录管理**：列出所有已知目录、`扫描磁盘`、
  底部输入框粘任意路径（`~` 也行）点「添加」—— 目录不存在会自动建。
  添加后会切到新目录，**原来那个不会被替换掉**（仍在列表里，用 `◀` 或管理页能切回去），
  添加完会留在管理页让你看到列表。
- 登记过的目录右侧有 `✕`，可以移出列表（不会删文件）。
- 目录表存在 `<当前实例>/config/mcver.json` 的 `dirs` 里，`lastDir` 记住上次用的；
  下次打开自动回到那个目录。

自动发现的范围：上次用过的、`~/.minecraft`、`%APPDATA%\.minecraft`、
`~/.local/share/minecraft`、macOS 的 `Application Support/minecraft`、当前工作目录，
外加 `扫描磁盘` 时扫到的任何叫 `.minecraft` 或有 `launcher_profiles.json` 的目录。

## Java 页

- 把能找到的 Java **全部列出来**（不再像以前那样每个大版本只留一个）:
  `Java 21  jdk-21.0.7  D:\Program Files\Java\jdk-21.0.7\bin\java.exe`
- 第一个是 **▶ 自动（按版本挑）**：按版本 json 的 `javaVersion.majorVersion` 选。
- 列表和其它页一样**翻页**：顶部 `◀ ▶` + `1/2` 页码，滚轮也能翻（不用只能默默滚）。
- 点任意一行 = 以后启动都用它；`重新扫描` 清缓存重找。
- 底部输入框支持粘：`java.exe` / `javaw.exe` / `...\bin` / JDK 根目录 / macOS 的
  `.../Contents/Home`。加进来的会存到 `javaExtra`，下次还在。
- **Linux 怎么办**：游戏内没有文件选择框（Windows 也没有），所以统一是「粘路径」。
  Linux / macOS 上填 `.../bin/java` 即可（那边没有 javaw.exe），自动扫描也照常工作。

## 启动失败怎么查

- 启动后 1.8 秒内子进程就退出 → 直接把**退出码 + 日志最后几行**显示在启动页，
  不再「点了没反应」。
- 回「版本」页点 **日志**，会打开最近一次 `mcver-launch-*.log`。
- 「启动后关当前」现在会先等 6 秒确认子进程活着才关当前窗口；
  子进程死了就不关，直接把日志路径显示出来。

## 下载源（默认只有官方）

设置页 → `下载源`：

- 列表里第一项永远是**官方 (Mojang)**，点一行就启用。
- 想用国内镜像：点右上 `＋ BMCLAPI` 一键加（会先探测一次再存），或者自己在底部填
  `名字` + `根地址` 点「添加」。加完会直接启用。
- 删除：非官方的行右侧 `✕`。
- 镜像约定（和 BMCLAPI 一致，别的镜像站照这个填就能用）：
  - `{根}/mc/game/version_manifest_v2.json` → 版本清单
  - `{根}/maven/...` → `libraries.minecraft.net` 的镜像
  - `{根}/assets/xx/yy` → `resources.download.minecraft.net` 的镜像
- 用的是镜像时，任何一步失败都会自动回退到官方地址再来一次。

## 版本隔离

开关在「版本」页（选中版本后点 `隔离`）和「设置」页（新装版本的默认值）。

**开的含义**：这个版本用 `versions/<名字>/` 当游戏目录启动（`--gameDir`），
存档、`mods/`、`options.txt` 各自一份；`assets`、`libraries` 仍然共用，不重复占空间。

技术上靠 `versions/<名字>/.mcver-isolated` 这个标记文件，所以：

- 开关可以真正关掉（以前只能开，关不掉）；
- 关掉只删标记，**不删你已经存进去的存档和 Mod**。

## 版本删除

点「删除」先进确认态（底部只剩「确定永久删除 / 取消」），确认后**递归真删**版本目录，
不再进回收站。删之前想留就自己先复制走。

## 怎么用

1. jar 丢进 `mods/`，启动器选 **Fabric 1.21.4**。
2. 进游戏拿到物品（任选）：
   - 创造模式物品栏 → **工具** 页 → 「Water Craft Launcher」（图标是流动的水）
   - `/give @s mcver:download_tool`
3. **手持右键** → 主界面（没有物品也行：默认快捷键 **M**）

## 启动器怎么实现的

不是"热切换"——JVM 的 classpath / natives / 类加载器定死了，换版本只能新开进程。
所以是**拼一条完整命令行，起一个独立 java 进程**，和 PCL/HMCL 的做法一致。

`core/Launcher.java`：

1. 读 `versions/<id>/<id>.json`，跟着 `inheritsFrom` 递归合并父子 json
   （libraries 按 `group:artifact:classifier` 覆盖，`arguments.jvm/game` 父前子后拼接）
2. 从 `libraries/` 里按 `downloads.artifact.path`（或 maven 名推路径）拼 classpath
3. ≤1.18 的版本走 `natives` 映射 + `downloads.classifiers` 解压到 `versions/<id>/natives`；
   ≥1.19 的 natives jar 本身就在 classpath 上，由 LWJGL 自己解压
4. 占位符替换（`${auth_player_name}` `--gameDir` `${classpath}` `--assetsDir`…）
5. 参数写进 `logs/mcver-args-<ts>.txt`，用 `java @argfile` 启动 ——
   Windows 命令行上限 32767 字符，大整合包的 classpath 会撞上
6. 设置在启动前追加：`-Xmx`、JVM 参数、游戏参数、`--width/--height` 或 `--fullscreen`
7. `java` 用 `JavaFinder.forMajor(json.javaVersion.majorVersion)` 挑；
   设置里指定了 `javaPath` 就优先用那个

**版本隔离**：`versions/<id>/mods` 存在 → `--gameDir` 指到 `versions/<id>`，
存档/mods/日志都跟主目录分开（「版本」页能一键开）。

**启动前自检**：libraries 缺文件、客户端 jar 缺、资源对象缺多少个，都先在状态行报出来。

## 账号

- **离线**：随便起名，UUID 按原版算法 `UUID v3("OfflinePlayer:<name>")` 生成
- **微软正版**：设备码流程（`login.live.com/oauth20_connect.srf`），
  游戏里直接显示 8 位代码，去 `microsoft.com/link` 输一下就行 —— 不需要回调端口、不需要内嵌浏览器
  - 链路：MSA token → Xbox Live（`RpsTicket` 先试 `t=`，再试 `d=`）→ XSTS → Minecraft Services → profile
  - 拿到的 `access_token` / `refresh_token` 存在 `config/mcver.json`，
    启动前如果过期会自动用 refresh token 续期

## 代码结构

| 文件 | 作用 |
|---|---|
| `McVerMod.java` | 注册物品 `mcver:download_tool`，入创造栏「工具」页 |
| `McVerClient.java` | 右键事件 + 快捷键 M |
| `ui/MainScreen.java` | 主面板 + 左侧导航 + 分页容器 |
| `ui/Page.java` | 页的基类（加控件、画无阴影文字） |
| `ui/LaunchPage.java` | 启动页 |
| `ui/AccountsPage.java` | 账号页（含设备码 UI） |
| `ui/VersionsPage.java` | 版本管理页 |
| `ui/SettingsPage.java` | 设置页 |
| `ui/WizardScreen.java` | 四步下载向导（目录 → 隔离 → 版本+加载器 → 进度） |
| `ui/UiState.java` | 各界面共享的选定目录 |
| `core/Config.java` | 全局配置 + 账号库（`config/mcver.json`） |
| `core/Account.java` | 账号模型 |
| `core/Auth.java` | 微软设备码登录 / token 续期 |
| `core/Json.java` | 极简 JSON 读写 |
| `core/Launcher.java` | json 合并 → classpath → 命令行 → 子进程 |
| `core/Installer.java` | 安装器：manifest → json → client.jar → libraries → natives → assets |
| `core/VersionList.java` | 版本清单，镜像挂了退官方 |
| `core/LoaderInstaller.java` | Forge / NeoForge / Fabric：下 installer 跑 CLI |
| `core/JavaFinder.java` | 扫本机 Java |
| `core/Dirs.java` | 找 `.minecraft` 目录，后台扫盘 |
| `core/InstallJob.java` | 安装状态机 + 隔离目录 |

## 踩过的坑

### 1. `Item.Settings` 必须带 registryKey（1.21.4）
不加直接 `NullPointerException: Item id not set`，崩在 `<clinit>`，
报错信息还很绕（`Could not execute entrypoint stage 'main'`）。

### 2. `TypedActionResult` 在 1.21.4 没了
换成 `net.minecraft.util.ActionResult`（`SUCCESS` / `PASS` / `FAIL`）。

### 3. natives 机制在 1.19 变了
- **≤1.18**：`downloads.classifiers` 的 jar 解压到 natives 目录，`-Djava.library.path` 指过去
- **≥1.19**：`org.lwjgl:lwjgl:3.3.3:natives-windows` 这类 jar **本身要在 classpath 上**，
  由 LWJGL 运行时自己解压

搞反 → `UnsatisfiedLinkError: no lwjgl in java.library.path`。

### 4. Forge / NeoForge installer 的门槛
- 目标目录必须有 `launcher_profiles.json`，否则 `you need to run the launcher first!`
- 目录里必须**已经有对应原版版本**，所以顺序固定：先原版 → 再 installer
- 跑 installer 要 Java 17/21，不能用 Java 8

### 5. GUI 尺寸比想象的小
1280×720 窗口自动缩放 3，GUI 坐标只有 427×240。布局全部按 `width/height` 算。

### 6. 面板被背景盖掉（最坑）
`Screen.render()` 的实现是：
```java
renderBackground(ctx, mouseX, mouseY, delta);  // 先模糊 + 压暗全屏
for (Drawable d : drawables) d.render(...);     // 再画控件
```
所以在 `render()` 里 `super.render(...)` **之前**用 `ctx.fill` 画的面板，
会被同一帧的 `renderBackground` 完全盖住 —— 现象是：按钮都在、面板却是黑的。

正确做法：override `renderBackground(...)`，先 `super.renderBackground(...)` 再画面板。
（`javap -p -c Screen.class` 反编译确认的，不是猜的。）

### 7. 下载器静默丢文件（最严重）
`downloadAll` 原来把单个文件的异常 `catch` 掉只打一行 stderr，
3598 个资源里丢了 775 个，安装照样报"完成" —— 结果启动就
`NoSuchFileException: assets/objects/9d/9dd3...`。

现在：失败文件收集起来**重试 3 轮**，收尾再按 sha1 全量校验，
还有缺的就抛异常，不再假报成功。

### 8. 进度条满格不显示
`InstallJob` 在 `loader`/`isolation` 阶段把 `done/total` 清零，收尾没改回来 → 完成时条子是空的。
末尾补 `done = 1; total = 1;`。

### 9. 微软登录：`00000000402b5328` 不能用 AAD 端点
用 `login.microsoftonline.com/consumers/oauth2/v2.0/devicecode` 会报
`AADSTS700016: Application ... was not found`。
这个 id 是 legacy Live 应用，得走 `login.live.com/oauth20_connect.srf`，
scope 用 `service::user.auth.xboxlive.com::MBI_SSL`，换来的 MSA token 在 XBL 用 `t=` 前缀。

### 10. 两个实例共用 `logs/latest.log`
子进程抢不到父进程的 latest.log 句柄，会打一行 ERROR（不致命）。
开了版本隔离就没这问题。

### 11. 游戏 JVM 里 `java.net.http.HttpClient` 会卡死在 send()
`meta.fabricmc.net` / `maven.fabricmc.net`（都是 Cloudflare）在游戏 JVM 里
经常 `send()` 不返回，**连 request timeout 都不生效**（`jstack` 看到卡在
`CompletableFuture.get` 里 7 分钟不动）。PowerShell 访问同一 URL 是 1 秒。

改法：小请求全走 `Net`（`HttpURLConnection`，连接 6s / 读 15s 硬超时），
并加镜像回退。默认 **镜像优先**（`Config.downloadSource = "mirror"`），
因为官方 Cloudflare 从国内经常读超时。

### 12. Fabric installer 同样会卡
它在自己进程里访问 meta/maven，也会 `Read timed out`。
所以 Fabric **不再走 installer**：直接拿
`/v2/versions/loader/<mc>/<loader>/profile/json`，自己把 8 个库下下来再写
`versions/<id>/<id>.json`。既快又没有 installer 那一堆超时。installer 只当兜底。

### 13. 下载一半的 jar 会让游戏启动时报 `zip END header not found`
`intermediary-1.21.4.jar` 只有 147KB（正常 701KB），`ZipFile` 打不开。
现在：写库前用 `Net.isJar()`（真的用 `ZipFile` 打开一次）校验，坏的就删了下；
`Launcher.plan()` 拼 classpath 时也校验，坏文件算进 `missing`，启动前就报出来。

### 14. 继承版本的客户端 jar 不是自己的
`mcver-test` 继承了 `1.21.4`，合并 json 后 `inheritsFrom` 字段没了，
之前按 `versions/<id>/<id>.jar` 找就找不到。新增 `resolveJarId()`：
顺着 `inheritsFrom` 往上找，用最底层有 jar 的那个。

### 15. OptiFine 安装器可以 headless
`optifine.Installer.doInstall(File)` 是 public static，用 `URLClassLoader`
反射直接调，全程不弹窗。注意它要 `launcher_profiles.json` **无 BOM**
（它自带的 JSON 解析器受不了 BOM），否则报 `Unexpected character (?) at position 0`。

## 已验证

- 物品注册、四页切换、版清单 920 个
- 面板几何（GUI 427×240）：`panel=375x220@26,10`；启动/下载/账号/版本/设置 children=16/20/9/11/15
- **文字无阴影**：标题带最暗像素 = `0x40`；有阴影时会出现 ~`0x10`
- 像素：面板体 `#C6C6C6`、外框 `#000000`、按钮 `#6F6F6F`、输入框 `#000000`
- 微软设备码**真实拿到过 user code**（`login.live.com` 200，`USER_CODE=RXPXGPFL`）
- **完整安装链路**：`1.21.4 → Fabric 0.20.1 → 改名 mcver-test → 隔离目录` 全跑通
  （库里新下 1 个坏掉的 intermediary，其余复用；耗时 ≈40s）
- **关界面不断下载**：`MCVER_AUTO_CLOSE` 在开装后 4s 把屏幕关掉，任务仍然跑到「安装完成」
- **启动装好的版本**：在 1.21.4 里启动 `mcver-test`（Fabric 1.21.4），
  子进程窗口起来，日志跑到 `Created: ...gui.png-atlas`，进到标题界面
- OptiFine 1.20.1 静默安装实测通过（生成 `1.20.1-OptiFine_HD_U_I6` + libraries/optifine）
- 装 1.7.10 全量（686 assets / 107MB）、装 1.20.1（3598 assets / 622MB）、1.21.4（403MB，0 缺失）

## 还没做（PCL 有，这里没有）

- 社区资源下载：Mod / 资源包 / 光影 / 整合包（Modrinth / CurseForge）
- 整合包导入导出（`.mrpack`）、Mod 管理（启用禁用/更新/从文件安装）
- Java 自动下载安装、存档备份、崩溃日志分析、下载速度显示、断点续传
- 联机（PCL 用 EasyTier/Terracotta，这个塞进游戏里不现实）

## 构建

前提：**JDK 21**（`JAVA_HOME` 指向它）+ **Gradle 8.x**（实测 8.14.2）。构建脚本零 npm 依赖，
但 `scripts/*.mjs` 需要 **Node ≥ 20.11**。

### 只编 Fabric 现代版（clone 下来最快能跑的一条）

```powershell
cd mod
.\gradlew.bat build        # 默认 target 是 1.21.4，产物在 build\libs\
```

wrapper 首次会下载 Gradle（约 130 MB，需要能访问 `services.gradle.org`）。
如果连不上（国内常见），自己装一个 Gradle 8.x 再走脚本：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1              # 9 个 Fabric target
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 -Only 1.21,1.20
```

脚本按这个顺序找 Gradle：`-Gradle` 参数 → `GRADLE_HOME` → `.tools\gradle-8.12` → `PATH`
→ `~\.gradle\wrapper\dists\gradle-8.*`（多个取最高）。

### 要编全部 85 个 jar

Gradle 只覆盖 Fabric 1.14~1.21 那 9 个；其余 76 个走另外三条手写管线
（tiny-remapper / mojmap+reobf / SRG 垫片），它们依赖两个**不在仓库里**的东西：

| 缺的 | 用来干什么 |
| --- | --- |
| `testgame/libraries` | 管线 C/D 的编译期 classpath（forge client jar 等） |
| `modx/srg`、`modx/legacy` | 映射中间产物（srg 名 jar / yarn named jar） |

顺序不能反（映射脚本自己也要读 `testgame/libraries`），完整步骤见 `HANDOVER.md` §1.2：

```powershell
# 1) 用任意启动器（官方 / HMCL / PCL）把需要的 MC 版本装进 testgame/
node scripts\mk-legacy-named.mjs     # 2) -> modx/legacy
node scripts\mk-srg-mojmap.mjs       # 3) -> modx/srg
node scripts\rebuild-all.mjs         # 4) 41 步，约 10 分钟
```

## 调试钩子

| 环境变量 | 作用 |
|---|---|
| `MCVER_OPEN_GUI=1` | 进标题界面自动开面板 |
| `MCVER_SCREEN=download` | 直接进下载页 |
| `MCVER_TAB=启动\|下载\|账号\|版本\|设置` | 指定初始页 |
| `MCVER_TAB_CYCLE=1` | 每 2 秒自动切页 |
| `MCVER_SHOT=1` | 定时存截图到 `run/screenshots` |
| `MCVER_PICK=1.21.4` | 下载页直接进某个版本的详情 |
| `MCVER_LOADER=FABRIC` | 预选模组加载器 |
| `MCVER_SHADER=OPTIFINE` | 预选光影加载器 |
| `MCVER_NAME=mcver-test` | 预设版本名 |
| `MCVER_AUTO_INSTALL=1` | 详情页自动开装 |
| `MCVER_AUTO_CLOSE=1` | 开装 4 秒后关界面（验证后台不断） |
| `MCVER_ISOLATION=true` | 覆盖隔离开关 |
| `MCVER_AUTO_LAUNCH=mcver-test` | 启动页自动启动某版本（`*` = 第一个） |
| `MCVER_KEEP_OPEN=1` | 启动后不退出当前游戏 |

安装过程会同时写 `run/mcver-install.log`（带时间戳），关掉界面也能从外面看进度。
每次面板 init 会往 `run/mcver-debug.txt` 追加一行（tab / GUI 尺寸 / 面板几何 / 控件数）。

## 许可

[MIT](LICENSE) © 2026 visitor257

## 仓库里没有什么

这个仓库只放**源码**（mod 源码、`compat/`/`modx/` 差异层、`scripts/` 构建与验收脚本、文档）。
下面这些都不在库里，需要按 `HANDOVER.md` 自己重建：

| 不在库里 | 为什么 |
| --- | --- |
| `testgame/`、`testgame122/` | Minecraft 游戏目录（assets/libraries，GB 级，且含 Mojang 版权资源） |
| `dist/`、`*.jar` | 构建产物，发版走 Releases |
| `modx/srg/`、`modx/legacy/` | 映射管线生成的中间产物（400+ MB，可重跑） |
| `config/mcver.json` | 本机配置：游戏目录与**账号 token**，永远不入库 |
| `logs/`、`*.log` | 构建/运行日志 |

> 所以 clone 下来直接编译是**编不动**的 —— 先看 `HANDOVER.md` 第 1 节（换机器两条路）
> 和第 5 节（哪些目录不在 git 里、缺了会怎样）。
