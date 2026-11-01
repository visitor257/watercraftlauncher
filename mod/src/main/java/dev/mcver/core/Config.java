package dev.mcver.core;

import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局配置 + 账号库，存在 <gameDir>/config/mcver.json。
 * 只有主界面会改它，读写都很随意。
 */
public final class Config {

    // ---- 启动
    public static int memMb = 4096;
    public static boolean closeAfterLaunch = true;
    public static String jvmArgs = "";
    public static String gameArgs = "";
    public static int winWidth = 854;
    public static int winHeight = 480;
    public static boolean fullscreen = false;
    public static String javaPath = "";

    /** 手动加进来的 Java（java.exe / javaw.exe / bin 目录 都行）。 */
    public static final List<String> javaExtra = new ArrayList<>();

    // ---- 下载
    public static int downloadThreads = 16;
    /** 当前下载源的名字（空 / "official" = 官方）。 */
    public static String downloadSource = "";
    /** 用户自己加的镜像，每项是 "名字=根地址"。默认空：只有官方。 */
    public static final List<String> downloadSources = new ArrayList<>();

    // ---- 版本
    public static boolean defaultIsolation = false;

    /** 用户登记的游戏目录（.minecraft），和自动扫到的一起在「启动」页切换。 */
    public static final List<String> dirs = new ArrayList<>();

    // ---- 账号（token 明文落盘，和 PCL/HMCL 一样；不想要就别用正版登录）
    public static final List<Account> accounts = new ArrayList<>();
    public static int accountIndex = -1;

    /**
     * 有没有一个「同 UUID 的微软正版账号」——离线账号能不能用的唯一判据。
     *
     * <p>合规要求：离线启动只允许复用**已经验证过正版身份**的账号（和官方启动器的
     * "Play offline" 一样）。所以离线档案必须能对上一条 MSA 记录，否则拒绝启动。
     */
    public static boolean hasMsaFor(String uuid) {
        if (uuid == null || uuid.isEmpty()) return false;
        for (Account a : accounts) {
            if (a.isMsa() && uuid.equalsIgnoreCase(a.uuid)) return true;
        }
        return false;
    }

    /** 上次用过的游戏目录。 */
    public static String lastDir = "";

    private static boolean loaded;

    private Config() { }

    public static Path dir() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Path base = mc == null ? Paths.get(".") : mc.runDirectory.toPath();
        return base.resolve("config");
    }

    public static Path file() {
        return dir().resolve("mcver.json");
    }

    // ================================================================ 读写

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            Path f = file();
            if (!Files.isRegularFile(f)) return;
            Map<String, Object> m = Json.obj(Json.parse(dev.mcver.core.Jdk.readString(f, StandardCharsets.UTF_8)));
            memMb = clamp(Json.num(m, "memMb", memMb), 256, 65536);
            closeAfterLaunch = Json.bool(m, "closeAfterLaunch", closeAfterLaunch);
            jvmArgs = Json.str(m, "jvmArgs", jvmArgs);
            gameArgs = Json.str(m, "gameArgs", gameArgs);
            winWidth = clamp(Json.num(m, "winWidth", winWidth), 320, 7680);
            winHeight = clamp(Json.num(m, "winHeight", winHeight), 240, 4320);
            fullscreen = Json.bool(m, "fullscreen", fullscreen);
            javaPath = Json.str(m, "javaPath", javaPath);
            downloadThreads = clamp(Json.num(m, "downloadThreads", downloadThreads), 1, 64);
            downloadSource = Json.str(m, "downloadSource", downloadSource);
            downloadSources.clear();
            for (Object o : Json.arr(m.get("downloadSources"))) {
                String s = o == null ? "" : String.valueOf(o).trim();
                if (!s.isEmpty()) downloadSources.add(s);
            }
            defaultIsolation = Json.bool(m, "defaultIsolation", defaultIsolation);
            lastDir = Json.str(m, "lastDir", lastDir);
            javaExtra.clear();
            for (Object o : Json.arr(m.get("javaExtra"))) {
                String s = o == null ? "" : String.valueOf(o).trim();
                if (!s.isEmpty()) javaExtra.add(s);
            }
            dirs.clear();
            for (Object o : Json.arr(m.get("dirs"))) {
                String s = o == null ? "" : String.valueOf(o).trim();
                if (!s.isEmpty()) dirs.add(s);
            }
            accounts.clear();
            for (Object o : Json.arr(m.get("accounts"))) {
                if (o instanceof Map) {
                    Account a = Account.fromMap(Json.obj(o));
                    if (!a.name.trim().isEmpty()) accounts.add(a);
                }
            }
            accountIndex = Json.num(m, "accountIndex", accounts.isEmpty() ? -1 : 0);
            if (accountIndex >= accounts.size()) accountIndex = accounts.isEmpty() ? -1 : 0;
        } catch (Exception e) {
            System.err.println("[mcver] 读配置失败: " + e);
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(dir());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("memMb", memMb);
            m.put("closeAfterLaunch", closeAfterLaunch);
            m.put("jvmArgs", jvmArgs);
            m.put("gameArgs", gameArgs);
            m.put("winWidth", winWidth);
            m.put("winHeight", winHeight);
            m.put("fullscreen", fullscreen);
            m.put("javaPath", javaPath);
            m.put("javaExtra", new ArrayList<Object>(javaExtra));
            m.put("downloadThreads", downloadThreads);
            m.put("downloadSource", downloadSource);
            m.put("downloadSources", new ArrayList<Object>(downloadSources));
            m.put("defaultIsolation", defaultIsolation);
            m.put("lastDir", lastDir);
            m.put("dirs", new ArrayList<Object>(dirs));
            m.put("accountIndex", accountIndex);
            List<Object> list = new ArrayList<>();
            for (Account a : accounts) list.add(a.toMap());
            m.put("accounts", list);
            dev.mcver.core.Jdk.writeString(file(), Json.write(m), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[mcver] 写配置失败: " + e);
        }
    }

    // ================================================================ 账号

    public static Account account() {
        if (accountIndex < 0 || accountIndex >= accounts.size()) return null;
        return accounts.get(accountIndex);
    }

    public static void setAccount(Account a) {
        int idx = accounts.indexOf(a);
        if (idx < 0) {
            accounts.add(a);
            idx = accounts.size() - 1;
        }
        accountIndex = idx;
        save();
    }

    // ================================================================ 工具

    public static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** 按空格切参数，支持 "双引号" 包住的整段。 */
    public static List<String> splitArgs(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return out;
        StringBuilder cur = new StringBuilder();
        boolean quote = false;
        for (int k = 0; k < raw.length(); k++) {
            char c = raw.charAt(k);
            if (c == '"') { quote = !quote; continue; }
            if (!quote && Character.isWhitespace(c)) {
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
                continue;
            }
            cur.append(c);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }
}
