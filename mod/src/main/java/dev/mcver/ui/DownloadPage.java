package dev.mcver.ui;

import dev.mcver.McVerMod;
import dev.mcver.core.Jobs;
import dev.mcver.core.InstallJob;
import dev.mcver.core.Installer;
import dev.mcver.core.Launcher;
import dev.mcver.core.LoaderInstaller;
import dev.mcver.core.LoaderSupport;
import dev.mcver.core.LoaderVersions;
import dev.mcver.core.Modrinth;
import dev.mcver.core.Sources;
import dev.mcver.core.VersionList;
import dev.mcver.core.VersionMeta;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 「下载」页：两层。
 *
 * 第一层是版本列表（像 PCL / HMCL 那样一行一个版本，带类型和日期）；
 * 点进某个版本后是安装页 —— 起个版本名、选模组加载器和光影加载器、
 * 再选它们各自的版本，最后开始装。
 *
 * 安装任务跑在后台线程（{@link Jobs}），关掉界面不会停。
 */
public class DownloadPage extends Page {

    private enum View { LIST, DETAIL, PROGRESS }

    private View view = View.LIST;

    // ---------------- 列表
    private List<VersionList.V> all = dev.mcver.core.Jdk.emptyList();
    private List<VersionList.V> shown = dev.mcver.core.Jdk.emptyList();
    private String query = "";
    private int page = 0;
    /** 当前页签：正式 / 快照 / 远古 / 愚人节（{@link VersionList#CAT_LABELS}）。 */
    private int cat = VersionList.CAT_RELEASE;
    private volatile boolean loadingVersions = false;
    private volatile String versionError;
    private boolean versionsRequested = false;
    private int loadSeq = 0;

    // ---------------- 详情
    private VersionList.V picked;
    private String name = "";
    private boolean nameEdited;
    private LoaderInstaller.Kind loader = LoaderInstaller.Kind.NONE;
    private LoaderInstaller.Shader shader = LoaderInstaller.Shader.NONE;
    private boolean focusShader;

    private List<LoaderVersions.Lv> modList = dev.mcver.core.Jdk.emptyList();
    private List<LoaderVersions.Lv> shaderList = dev.mcver.core.Jdk.emptyList();
    private int modSel, shaderSel, modPage, shaderPage;
    private volatile boolean loadingComp;
    private volatile String compError;
    private int compSeq = 0;

    private String status = "";
    private int statusColor = MainScreen.C_DIM;

    private TextFieldWidget searchField;
    /** 这次重建是从搜索框的输入触发的 —— 建完要把焦点还回去（见 initList）。 */
    private boolean focusSearchAfterRebuild;
    private TextFieldWidget nameField;

    /** 终止下载的二次确认状态。 */
    private boolean confirmStop;
    /** 上次看到的任务状态：变了就重建按钮（完成 → 终止下载 变 返回）。 */
    private InstallJob.State lastJobState;
    /** 用户主动点「再装一个」留在列表页时置 true，避免 init() 又把页面拉回进度页。 */
    private boolean allowListWhileRunning;

    private final String autoVersion = env("MCVER_PICK");   // 自检：直接跳进某版本
    private final String autoLoader = env("MCVER_LOADER");
    private final String autoShader = env("MCVER_SHADER");
    private final String autoName = env("MCVER_NAME");

    public DownloadPage(MainScreen host) {
        super(host);
    }

    @Override
    public String nav() { return "下载"; }

    // ================================================================ 几何

    private int listTop() { return host.contentY() + 39; }
    private int listRows() { return Math.max(1, (host.contentBottom() - 16 - listTop()) / 15); }
    private int catTop() { return host.contentY() + 20; }
    private int footY() { return host.contentBottom() - 15; }

    // 详情页各行
    private int yBack() { return host.contentY() + 1; }
    private int yName() { return host.contentY() + 19; }
    private int yLoader() { return host.contentY() + 39; }
    private int yShader() { return host.contentY() + 58; }
    private int yListHead() { return host.contentY() + 78; }
    private int yListRow0() { return host.contentY() + 90; }
    private int yInstall() { return host.contentY() + 140; }
    private int yStatus() { return host.contentY() + 162; }

    // ================================================================ init

    @Override
    public void init() {
        // 有任务在跑时默认拉回进度页；但用户主动点了「再装一个」就别再拉回去（allowListWhileRunning）
        if (!allowListWhileRunning && Jobs.hasJob() && Jobs.running() && view != View.PROGRESS) view = View.PROGRESS;
        if (autoVersion != null && !autoDetailDone) {
            // 自检：直接进某个版本的详情
            autoDetailDone = true;
            picked = new VersionList.V(autoVersion, "release", "", "");
            view = View.DETAIL;
            if (autoLoader != null) {
                try { loader = LoaderInstaller.Kind.valueOf(autoLoader.toUpperCase(java.util.Locale.ROOT)); }
                catch (Exception ignored) { }
            }
            if (autoShader != null) {
                try { shader = LoaderInstaller.Shader.valueOf(autoShader.toUpperCase(java.util.Locale.ROOT)); }
                catch (Exception ignored) { }
            }
            focusShader = shader != LoaderInstaller.Shader.NONE;
            if (autoName != null) { name = autoName; nameEdited = true; }
        }
        switch (view) {
            case LIST: initList(); break;
            case DETAIL: initDetail(); break;
            case PROGRESS: initProgress(); break;
            default: break;
        }
    }

    private boolean autoDetailDone;
    private boolean autoCatDone;

    // ---------------------------------------------------------------- 列表

    private void initList() {
        int x = host.contentX();
        int w = host.contentW();

        // 自检：MCVER_CAT=愚人节 直接从某个分类进去
        if (env("MCVER_CAT") != null && !autoCatDone) {
            autoCatDone = true;
            String wantCat = env("MCVER_CAT");
            for (int i = 0; i < VersionList.CAT_LABELS.length; i++) {
                if (VersionList.CAT_LABELS[i].equals(wantCat)) cat = i;
            }
        }

        searchField = Field.of(this.tr(), x, host.contentY() + 1, w, 16, "搜索版本号");
        searchField.setMaxLength(32);
        searchField.setText(query);
        if (focusSearchAfterRebuild) {
            focusSearchAfterRebuild = false;
            Focus.apply(searchField, true);        }
        searchField.setChangedListener(s -> {
            query = s;
            page = 0;
            // rebuild() 会把包括搜索框在内的控件全部重建，焦点是控件实例自己的
            // 属性，新实例默认不聚焦 —— 不还回去的话，第二个字符就没人接收，
            // 表现为「搜索框只能打一个字」（2026-10-29 实测：第二次按键时
            // focused=0，日志里只有 搜索=1）。
            focusSearchAfterRebuild = true;
            rebuild();
        });
        add(searchField);

        // 四个分类页签：正式 / 快照 / 远古 / 愚人节（像下面的加载器选择那样，选一个再列对应版本）
        int cw = (w - 3 * 3) / 4;
        for (int i = 0; i < VersionList.CAT_LABELS.length; i++) {
            final int c = i;
            boolean on = c == cat;
            String label = on ? "▶" + VersionList.CAT_LABELS[c] : VersionList.CAT_LABELS[c];
            if (!all.isEmpty()) label = label + " " + VersionList.count(all, c);
            add(Btn.builder(Txt.of(label), b -> {
                cat = c;
                page = 0;
                rebuild();
            }).dimensions(x + i * (cw + 3), catTop(), cw, 15).build());
        }

        // 给「点页签」类验收脚本用的几何信息（GUI 坐标，不含窗口缩放）：
        // 探针从日志里拿 gui=WxH 和这几个数，换算成屏幕坐标后真点一下 —— 这样验收脚本
        // 不用复制一份布局算式，界面改了也不会悄悄失准。每次重建列表面板只打一行。
        McVerMod.LOGGER.info(String.format(java.util.Locale.ROOT,
                "mcver: layout tabs@%d,%d,%d,%d,%d search@%d,%d,%d,%d",
                x, catTop(), cw, 15, VersionList.CAT_LABELS.length,
                x, host.contentY() + 1, w, 16));

        // 分类/搜索一改就该立刻出结果 —— recompute() 只做本地过滤（清单已经全在 all 里），
        // 不需要重新联网。以前只在加载完成时才调，所以点完页签列表还是上一个分类的内容，
        // 用户必须先按「刷新」才看到变化。
        if (!all.isEmpty()) recompute();

        startVersionLoad();
        int rows = listRows();
        int from = page * rows;
        int to = Math.min(shown.size(), from + rows);
        int ry = listTop();
        for (int i = from; i < to; i++) {
            VersionList.V v = shown.get(i);
            add(Btn.builder(Txt.of(rowLabel(v, w - 6)), b -> enterDetail(v))
                    .dimensions(x, ry, w, 14).build());
            ry += 15;
        }

        int y = footY();
        add(Btn.builder(Txt.of("◀"), b -> {
            if (page > 0) { page--; rebuild(); }
        }).dimensions(x, y, 22, 15).build());
        add(Btn.builder(Txt.of("▶"), b -> {
            if (page < pages() - 1) { page++; rebuild(); }
        }).dimensions(x + 24, y, 22, 15).build());

        add(Btn.builder(Txt.of("刷新"), b -> {
            versionsRequested = false;
            startVersionLoad();
            rebuild();
        }).dimensions(x + w - 44, y, 44, 15).build());

        // 后台还有任务在跑时，留一个回进度页的入口（不然离开进度页就没路回去了）
        if (Jobs.running()) {
            add(Btn.builder(Txt.of("◀ 回到进度"), b -> {
                allowListWhileRunning = false;
                view = View.PROGRESS;
                rebuild();
            }).dimensions(x + 50, y, 66, 15).build());
        }
    }

    private String rowLabel(VersionList.V v, int maxW) {
        // 按用户要求：已经装过的版本不再打勾，列表保持干净（需要判断装没装看启动页/版本页）
        String type = VersionList.CAT_LABELS[VersionList.catOf(v)];
        String date = v.releaseTime() == null || v.releaseTime().length() < 10
                ? "" : v.releaseTime().substring(0, 10);
        return trim(v.id() + "    " + type + "    " + date, maxW);
    }

    private int pages() { return Math.max(1, (shown.size() + listRows() - 1) / listRows()); }

    private void startVersionLoad() {
        if (versionsRequested) return;
        versionsRequested = true;
        loadingVersions = true;
        final int seq = ++loadSeq;
        Thread t = new Thread(() -> {
            try {
                List<VersionList.V> list = VersionList.fetch(Sources.active());
                McVerMod.LOGGER.info("mcver: 版本清单 {} 个", list.size());
                minecraft().execute(() -> {
                    if (seq != loadSeq) return;
                    all = list;
                    loadingVersions = false;
                    recompute();
                    if (view == View.LIST) rebuild();
                });
            } catch (Exception e) {
                McVerMod.LOGGER.error("mcver: 清单加载失败", e);
                minecraft().execute(() -> {
                    if (seq != loadSeq) return;
                    versionError = String.valueOf(e.getMessage());
                    loadingVersions = false;
                    if (view == View.LIST) rebuild();
                });
            }
        }, "mcver-manifest");
        t.setDaemon(true);
        t.start();
    }

    private void recompute() {
        shown = VersionList.filter(all, query, cat);
        if (page > pages() - 1) page = Math.max(0, pages() - 1);
        McVerMod.LOGGER.info("mcver: 分类={} 搜索={} 共={} 个（总 {}）",
                VersionList.CAT_LABELS[cat], query.isEmpty() ? "-" : query, shown.size(), all.size());
    }

    // ---------------------------------------------------------------- 详情

    private void enterDetail(VersionList.V v) {
        picked = v;
        view = View.DETAIL;
        status = "";
        compError = null;
        nameEdited = false;
        name = VersionMeta.suggest(v.id(), loader, modId(), shader, shaderId());

        if (autoLoader != null) {
            try { loader = LoaderInstaller.Kind.valueOf(autoLoader.toUpperCase(java.util.Locale.ROOT)); }
            catch (Exception ignored) { }
        }
        if (autoShader != null) {
            try { shader = LoaderInstaller.Shader.valueOf(autoShader.toUpperCase(java.util.Locale.ROOT)); }
            catch (Exception ignored) { }
        }
        if (autoName != null) { name = autoName; nameEdited = true; }

        rebuild();
        loadFocus();
    }

    private boolean autoInstallTried;

    private void initDetail() {
        int x = host.contentX();
        int w = host.contentW();

        add(Btn.builder(Txt.of("◀ 返回"), b -> {
            view = View.LIST;
            rebuild();
        }).dimensions(x, yBack(), 46, 15).build());

        nameField = Field.of(this.tr(), x + 30, yName() - 1, w - 30, 16, "版本名");
        nameField.setMaxLength(64);
        nameField.setText(name);
        nameField.setChangedListener(s -> {
            name = s;
            nameEdited = true;
        });
        add(nameField);

        // 模组加载器
        LoaderInstaller.Kind[] kinds = {LoaderInstaller.Kind.NONE, LoaderInstaller.Kind.FABRIC,
                LoaderInstaller.Kind.QUILT, LoaderInstaller.Kind.FORGE, LoaderInstaller.Kind.NEOFORGE};
        int bw = (w - (kinds.length - 1) * 3) / kinds.length;
        for (int i = 0; i < kinds.length; i++) {
            LoaderInstaller.Kind k = kinds[i];
            boolean on = k == loader;
            add(Btn.builder(Txt.of(on ? "▶" + k.label : k.label), b -> {
                loader = k;
                focusShader = false;
                modSel = 0;
                modPage = 0;
                modList = dev.mcver.core.Jdk.emptyList();
                if (!nameEdited) name = VersionMeta.suggest(picked.id(), loader, null, shader, shaderId());
                status = "";
                // 先查规则表：这个组合根本不存在就直接说，不必等网络查询
                String why = picked == null ? null : LoaderSupport.reason(k, picked.id());
                if (why != null) {
                    status = why;
                    statusColor = MainScreen.C_ERR;
                    modList = dev.mcver.core.Jdk.emptyList();
                    rebuild();
                    return;
                }
                rebuild();
                loadFocus();
            }).dimensions(x + i * (bw + 3), yLoader(), bw, 15).build());
        }

        // 光影加载器
        LoaderInstaller.Shader[] shaders = {LoaderInstaller.Shader.NONE, LoaderInstaller.Shader.IRIS,
                LoaderInstaller.Shader.OPTIFINE};
        int sw = (w - (shaders.length - 1) * 3) / shaders.length;
        for (int i = 0; i < shaders.length; i++) {
            LoaderInstaller.Shader s = shaders[i];
            boolean on = s == shader;
            add(Btn.builder(Txt.of(on ? "▶" + s.label : s.label), b -> {
                shader = s;
                focusShader = s != LoaderInstaller.Shader.NONE;
                shaderSel = 0;
                shaderPage = 0;
                shaderList = dev.mcver.core.Jdk.emptyList();
                if (!nameEdited) name = VersionMeta.suggest(picked.id(), loader, modId(), shader, null);
                status = "";
                rebuild();
                loadFocus();
            }).dimensions(x + i * (sw + 3), yShader(), sw, 15).build());
        }

        // 版本列表（模组加载器 / 光影，二选一显示）
        List<LoaderVersions.Lv> list = focusList();
        int pagesN = Math.max(1, (list.size() + 2) / 3);
        add(Btn.builder(Txt.of("◀"), b -> {
            if (focusPage() > 0) { setFocusPage(focusPage() - 1); rebuild(); }
        }).dimensions(x + w - 46, yListHead() - 2, 20, 13).build());
        add(Btn.builder(Txt.of("▶"), b -> {
            if (focusPage() < pagesN - 1) { setFocusPage(focusPage() + 1); rebuild(); }
        }).dimensions(x + w - 24, yListHead() - 2, 20, 13).build());

        int from = focusPage() * 3;
        final int rowFrom = from;
        int ry = yListRow0();
        for (int i = from; i < Math.min(list.size(), from + 3); i++) {
            LoaderVersions.Lv lv = list.get(i);
            final int idx = i;
            boolean on = (focusShader ? shaderSel : modSel) == i;
            add(Btn.builder(Txt.of((on ? "▶ " : "   ") + trim(lv.label(), w - 26)),
                    b -> {
                        if (focusShader) shaderSel = idx; else modSel = idx;
                        if (!nameEdited) {
                            name = VersionMeta.suggest(picked.id(), loader, modId(), shader, shaderId());
                            if (nameField != null) nameField.setText(name);
                        }
                        rebuild();
                    }).dimensions(x, ry, w - 4, 14).build());
            ry += 15;
        }

        // 安装
        add(Btn.builder(Txt.of("开始安装"), b -> startInstall())
                .dimensions(x, yInstall(), 74, 18).build());
        add(Btn.builder(Txt.of("后台有任务？"), b -> {
            if (Jobs.hasJob()) { view = View.PROGRESS; rebuild(); }
            else { status = "现在没有后台任务"; statusColor = MainScreen.C_FAINT; rebuild(); }
        }).dimensions(x + 78, yInstall(), 86, 18).build());

        // 自检：直接开装
        if (env("MCVER_AUTO_INSTALL") != null && !autoInstallTried) {
            autoInstallTried = true;
            new Thread(() -> {
                try { Thread.sleep(2500); } catch (InterruptedException ignored) { }
                minecraft().execute(this::startInstall);
            }, "mcver-autostart").start();
        }

        if (modList.isEmpty() && shaderList.isEmpty() && !loadingComp && compError == null) loadFocus();
    }

    private List<LoaderVersions.Lv> focusList() {
        if (focusShader) {
            if (shader == LoaderInstaller.Shader.OPTIFINE) return shaderList;
            if (shader == LoaderInstaller.Shader.IRIS) return shaderList;
            return dev.mcver.core.Jdk.emptyList();
        }
        return loader == LoaderInstaller.Kind.NONE ? dev.mcver.core.Jdk.emptyList() : modList;
    }

    private int focusPage() { return focusShader ? shaderPage : modPage; }

    private void setFocusPage(int p) {
        if (focusShader) shaderPage = p; else modPage = p;
    }

    private String modId() {
        return modList.isEmpty() || modSel >= modList.size() ? null : modList.get(modSel).id();
    }

    private String shaderId() {
        return shaderList.isEmpty() || shaderSel >= shaderList.size() ? null : shaderList.get(shaderSel).id();
    }

    /** 加载当前聚焦的组件版本列表。 */
    private void loadFocus() {
        if (picked == null) return;
        final int seq = ++compSeq;
        final String mc = picked.id();
        final LoaderInstaller.Kind k = loader;
        final LoaderInstaller.Shader s = shader;
        final boolean wantShader = focusShader;

        if (wantShader) {
            if (s == LoaderInstaller.Shader.NONE) return;
        } else if (k == LoaderInstaller.Kind.NONE) {
            return;
        }

        loadingComp = true;
        compError = null;
        Thread t = new Thread(() -> {
            try {
                List<LoaderVersions.Lv> list;
                if (wantShader && s == LoaderInstaller.Shader.OPTIFINE) {
                    list = LoaderVersions.optifineList(mc);
                } else if (wantShader && s == LoaderInstaller.Shader.IRIS) {
                    list = new ArrayList<>();
                    for (Modrinth.Ver v : Modrinth.versions("iris", mc, "fabric")) {
                        list.add(new LoaderVersions.Lv(v.number(), v.channel()));
                    }
                    if (list.isEmpty()) throw new java.io.IOException("Iris 没有 " + mc + " 的版本");
                } else {
                    list = LoaderVersions.fetch(k, mc);
                }
                minecraft().execute(() -> {
                    if (seq != compSeq) return;
                    loadingComp = false;
                    McVerMod.LOGGER.info("mcver: 加载器版本 k={} mc={} 数量={} 首个={}", k, mc, list.size(),
                            list.isEmpty() ? "-" : list.get(0).label());
                    if (wantShader) {
                        shaderList = list;
                        if (shaderSel >= list.size()) shaderSel = 0;
                    } else {
                        modList = list;
                        if (modSel >= list.size()) modSel = 0;
                    }
                    if (!nameEdited) {
                        name = VersionMeta.suggest(mc, loader, modId(), shader, shaderId());
                        if (nameField != null) nameField.setText(name);
                    }
                    if (view == View.DETAIL) rebuild();
                });
            } catch (Exception e) {
                McVerMod.LOGGER.warn("mcver: 版本列表加载失败 {}", e.toString());
                minecraft().execute(() -> {
                    if (seq != compSeq) return;
                    loadingComp = false;
                    compError = String.valueOf(e.getMessage());
                    if (view == View.DETAIL) rebuild();
                });
            }
        }, "mcver-loaders");
        t.setDaemon(true);
        t.start();
    }

    // ---------------------------------------------------------------- 安装

    private void startInstall() {
        // 规则表拦截：Forge 没这个版本 / NeoForge 没 1.20 以前的 / 快照选了 Forge …
        if (picked != null) {
            String why = LoaderSupport.reason(loader, picked.id());
            if (why != null) {
                status = why;
                statusColor = MainScreen.C_ERR;
                rebuild();
                return;
            }
        }
        if (Jobs.running()) {
            status = "还有任务在跑，先去进度页看看";
            statusColor = MainScreen.C_ERR;
            rebuild();
            return;
        }
        if (picked == null) return;
        if (shader == LoaderInstaller.Shader.IRIS && loader != LoaderInstaller.Kind.FABRIC) {
            status = "Iris 需要 Fabric，先把模组加载器换成 Fabric";
            statusColor = MainScreen.C_ERR;
            rebuild();
            return;
        }
        String nm = nameField == null ? name : nameField.getText();
        if (!nm.trim().isEmpty() && !VersionMeta.validName(nm)) {
            status = "版本名里有不允许的字符";
            statusColor = MainScreen.C_ERR;
            rebuild();
            return;
        }

        InstallJob job = new InstallJob(UiState.dir(), picked.id(), loader, modId(),
                shader, shaderId(), Config_isolation(), nm);
        Jobs.submit(job);
        allowListWhileRunning = false;
        McVerMod.LOGGER.info("mcver: 开始安装 {} loader={} shader={} name={}",
                picked.id(), loader, shader, nm);
        view = View.PROGRESS;
        status = "";
        lastJobState = InstallJob.State.RUNNING;
        rebuild();

        // 自检：模拟点右上角 ✕ —— 界面关掉，下载应当照跑
        if (env("MCVER_AUTO_CLOSE") != null) {
            new Thread(() -> {
                try { Thread.sleep(4000); } catch (InterruptedException ignored) { }
                minecraft().execute(() -> host.closeSelf());
            }, "mcver-autoclose").start();
        }
    }

    private boolean Config_isolation() {
        String e = env("MCVER_ISOLATION");
        if (e != null) return Boolean.parseBoolean(e);
        return dev.mcver.core.Config.defaultIsolation;
    }

    // ---------------------------------------------------------------- 进度

    private void initProgress() {
        int x = host.contentX();
        int w = host.contentW();
        InstallJob job = Jobs.install();
        boolean running = job != null && job.state == InstallJob.State.RUNNING;

        if (running && !confirmStop) {
            add(Btn.builder(Txt.of("终止下载"), b -> {
                confirmStop = true;
                rebuild();
            }).dimensions(x, host.contentBottom() - 18, 64, 16).build());
        } else if (running) {
            add(Btn.builder(Txt.of("确认终止"), b -> {
                InstallJob j = Jobs.install();
                if (j != null && j.state == InstallJob.State.RUNNING) j.cancelRequested = true;
                confirmStop = false;
                rebuild();
            }).dimensions(x, host.contentBottom() - 18, 64, 16).build());
            add(Btn.builder(Txt.of("不终止了"), b -> {
                confirmStop = false;
                rebuild();
            }).dimensions(x + 68, host.contentBottom() - 18, 64, 16).build());
        } else {
            // 跑完了 → 返回下载前的界面（从哪个版本点进来的就回哪个）
            add(Btn.builder(Txt.of("返回"), b -> {
                confirmStop = false;
                view = picked != null ? View.DETAIL : View.LIST;
                rebuild();
            }).dimensions(x, host.contentBottom() - 18, 64, 16).build());
        }

        add(Btn.builder(Txt.of("再装一个"), b -> {
            confirmStop = false;
            // 之前这里直接 view=LIST，但 init() 发现任务还在跑又把页面拉回 PROGRESS，
            // 表现出来就是「按钮按下去没反应」。现在显式允许停在列表页。
            allowListWhileRunning = true;
            view = View.LIST;
            rebuild();
        }).dimensions(x + w - 62, host.contentBottom() - 18, 62, 16).build());
    }

    // ================================================================ render

    @Override
    public void tick() {
        if (view != View.PROGRESS) return;
        InstallJob j = Jobs.install();
        InstallJob.State st = j == null ? null : j.state;
        if (st != lastJobState) {
            lastJobState = st;
            rebuild();
        }
    }

    @Override
    public void render(Ctx ctx, int mouseX, int mouseY, float delta) {        switch (view) {
            case LIST: renderList(ctx); break;
            case DETAIL: renderDetail(ctx); break;
            case PROGRESS: renderProgress(ctx); break;
        }
    }

    private void renderList(Ctx ctx) {
        int x = host.contentX();
        int w = host.contentW();

        if (versionError != null) {
            text(ctx, "✖ " + trim(versionError, w), x, listTop(), MainScreen.C_ERR);
        } else if (loadingVersions) {
            text(ctx, "正在获取版本列表…", x, listTop(), MainScreen.C_DIM);
        } else if (shown.isEmpty()) {
            text(ctx, query.trim().isEmpty()
                    ? "这个分类里没有版本"
                    : "「" + VersionList.CAT_LABELS[cat] + "」里没有匹配的版本",
                    x, listTop(), MainScreen.C_DIM);
        }

        text(ctx, (page + 1) + " / " + pages() + "   共 " + shown.size() + " 个", x + 52, footY() + 4,
                MainScreen.C_FAINT);
    }

    private void renderDetail(Ctx ctx) {
        int x = host.contentX();
        int w = host.contentW();

        if (picked != null) {
            String type = VersionList.CAT_LABELS[VersionList.catOf(picked)] + "版";
            String date = picked.releaseTime() == null || picked.releaseTime().length() < 10
                    ? "" : picked.releaseTime().substring(0, 10);
            text(ctx, picked.id() + "   " + type + "   " + date, x + 50, yBack() + 4, MainScreen.C_TEXT);
        }

        text(ctx, "名", x, yName() + 4, MainScreen.C_DIM);

        String listTitle;
        if (focusShader) {
            listTitle = shader == LoaderInstaller.Shader.NONE ? "光影：无" : shader.label + " 版本";
        } else {
            listTitle = loader == LoaderInstaller.Kind.NONE ? "模组加载器：原版" : loader.label + " 版本";
        }
        text(ctx, listTitle, x, yListHead(), MainScreen.C_DIM);

        int listPages = Math.max(1, (focusList().size() + 2) / 3);
        if (focusList().size() > 3) {
            rightAligned(ctx, (focusPage() + 1) + "/" + listPages, x + w - 50, yListHead(), MainScreen.C_FAINT);
        }

        if (loadingComp) {
            text(ctx, "加载版本列表…", x, yListRow0() + 2, MainScreen.C_WARN);
        } else if (compError != null) {
            text(ctx, "✖ " + trim(compError, w), x, yListRow0() + 2, MainScreen.C_ERR);
        } else if (focusList().isEmpty()) {
            String hint = focusShader
                    ? (shader == LoaderInstaller.Shader.NONE ? "没选光影加载器" : "没有可用的光影版本")
                    : (loader == LoaderInstaller.Kind.NONE ? "原版不需要加载器版本" : "没有可用的加载器版本");
            text(ctx, hint, x, yListRow0() + 2, MainScreen.C_FAINT);
        }

        String isolation = Config_isolation() ? "版本隔离：开" : "版本隔离：关";
        text(ctx, isolation, x, yStatus(), MainScreen.C_FAINT);
        if (!status.isEmpty()) {
            text(ctx, trim(status, w), x, yStatus() + 11, statusColor);
        }
    }

    private void renderProgress(Ctx ctx) {
        InstallJob job = Jobs.install();
        int x = host.contentX();
        int w = host.contentW();
        int y = host.contentY() + 2;

        if (job == null) {
            text(ctx, "没有安装任务", x, y, MainScreen.C_DIM);
            return;
        }

        text(ctx, "阶段: " + stageLabel(job.stage), x, y, MainScreen.C_TEXT);
        y += 13;

        double frac = job.fraction();
        ctx.fill(x, y, x + w, y + 8, 0xFF8B8B8B);
        ctx.fill(x, y, x + w, y + 1, 0xFF373737);
        ctx.fill(x, y + 7, x + w, y + 8, 0xFFFFFFFF);
        int fillW = (int) (w * frac);
        if (fillW > 0) {
            ctx.fill(x, y + 1, x + fillW, y + 7, 0xFF3FBF5F);
            ctx.fill(x, y + 1, x + fillW, y + 2, 0xFF7FE89A);
        }
        y += 12;

        String count = job.state == InstallJob.State.RUNNING
                ? (job.total > 0
                    ? job.done + " / " + job.total + "   " + String.format("%.0f%%", frac * 100)
                    : job.detail)
                : job.detail;
        text(ctx, trim(count, w), x, y, MainScreen.C_TEXT);
        y += 12;

        switch (job.state) {
            case FAILED: {
                text(ctx, "✖ " + trim(String.valueOf(job.error), w), x, y, MainScreen.C_ERR);
                y += 11;
                break;
            }
            case DONE: {
                text(ctx, "✔ 完成 " + job.resultVersionId, x, y, MainScreen.C_OK);
                y += 11;
                break;
            }
            case CANCELLED: {
                text(ctx, "已终止（已下载的文件保留）", x, y, 0xFF7A5A00);
                y += 11;
                break;
            }
            default: { } break;
        }

        if (job.state == InstallJob.State.RUNNING) {
            text(ctx, confirmStop ? "再点一下「确认终止」才会停下" : "关掉界面不会中断下载",
                    x, y, confirmStop ? MainScreen.C_ERR : MainScreen.C_FAINT);
            y += 11;
        }

        y += 2;
        int logTop = y;
        int logBottom = host.contentBottom() - 21;
        if (logBottom - logTop > 12) {
            ctx.fill(x, logTop, x + w, logBottom, 0xFF9E9E9E);
            ctx.fill(x, logTop, x + w, logTop + 1, 0xFF6E6E6E);
            ctx.fill(x, logTop, x + 1, logBottom, 0xFF6E6E6E);
            ctx.fill(x, logBottom - 1, x + w, logBottom, 0xFFFFFFFF);

            int ly = logTop + 4;
            int maxLines = Math.max(1, (logBottom - 9 - ly) / 10);
            for (String line : job.logSnapshot(maxLines)) {
                if (ly > logBottom - 9) break;
                text(ctx, trim(line, w - 8), x + 4, ly, 0xFF3A3A3A);
                ly += 10;
            }
        }
    }

    private static String stageLabel(String stage) {
        String k = stage == null ? "" : stage;
        switch (k) {
            case "manifest": return "获取版本清单";
            case "version": return "下载版本元数据";
            case "plan": return "解析依赖";
            case "files": return "下载 jar 与库";
            case "assets": return "下载资源文件";
            case "loader": return "安装加载器";
            case "shader": return "安装光影加载器";
            case "rename": return "重命名版本";
            case "isolation": return "开启版本隔离";
            case "done": return "完成";
            default: return k.trim().isEmpty() ? "准备中" : stage;
        }
    }

    private static String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : "…" + s.substring(s.length() - max + 1);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (view == View.LIST) {
            int d = (int) Math.signum(vertical);
            if (d != 0) {
                page = Math.max(0, Math.min(pages() - 1, page - d));
                rebuild();
                return true;
            }
        } else if (view == View.DETAIL) {
            int d = (int) Math.signum(vertical);
            int n = Math.max(1, (focusList().size() + 2) / 3);
            if (d != 0) {
                setFocusPage(Math.max(0, Math.min(n - 1, focusPage() - d)));
                rebuild();
                return true;
            }
        }
        return false;
    }
}
