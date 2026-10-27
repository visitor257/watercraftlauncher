package dev.mcver.core;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 查某个 MC 版本能装哪些加载器版本。
 *
 * Fabric / Quilt 走各自的 meta API，Forge / NeoForge 走 maven 元数据，
 * OptiFine 走 BMCLAPI 的列表接口。
 */
public final class LoaderVersions {

    /** 一个可选版本：id 是给 installer 用的，note 是给人看的补充。 */
    public static final class Lv {

        // —— 原来是 record，为了 Java 8 兼容改写成普通类；字段名/方法名跟 record 一致，
        //    所以 found.exe 和 found.exe() 两种写法都还能用。
        public final String id;
        public final String note;

        public Lv(String id, String note) {
            this.id = id;
            this.note = note;
        }

        public String id() { return id; }
        public String note() { return note; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Lv)) return false;
            Lv that = (Lv) o;
            return true
                && java.util.Objects.equals(id, that.id)
                && java.util.Objects.equals(note, that.note);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(
                java.util.Objects.hashCode(id),
                java.util.Objects.hashCode(note));
        }

        @Override
        public String toString() {
            return "Lv[" + "id=" + id + ", " + "note=" + note + "]";
        }

        // —— 以下为原 record 里的自定义方法

        public String label() {
            return note == null || note.trim().isEmpty() ? id : id + "  " + note;
        }
    }

    public static List<Lv> fetch(LoaderInstaller.Kind kind, String mc) throws Exception {
        switch (kind) {
            case FABRIC: return fabric(mc);
            case QUILT: return quilt(mc);
            case FORGE: return forge(mc);
            case NEOFORGE: return neoforge(mc);
            case NONE: return dev.mcver.core.Jdk.emptyList();
            default: return dev.mcver.core.Jdk.emptyList();
        }
    }

    // ---------------------------------------------------------------- Fabric

    private static List<Lv> fabric(String mc) throws Exception {
        String base = FabricMeta.metaBase(mc);
        String mirror = FabricMeta.mirrorBase(mc);
        String body = mirror == null
                ? Net.get(base + "v2/versions/loader/" + mc, 10_000, 30_000)
                : Net.getMirrored(base + "v2/versions/loader/" + mc,
                        mirror + "v2/versions/loader/" + mc, "Fabric 版本表");
        List<Lv> out = new ArrayList<>();
        for (Object o : Json.arr(Json.parse(body))) {
            Map<String, Object> e = Json.obj(o);
            Map<String, Object> loader = Json.obj(e.get("loader"));
            String v = Json.str(loader, "version", "");
            if (v.isEmpty()) continue;
            boolean stable = Json.bool(loader, "stable", false);
            String inter = Json.str(Json.obj(e.get("intermediary")), "version", "");
            out.add(new Lv(v, (stable ? "稳定" : "测试") + (inter.isEmpty() ? "" : "  " + inter)));
        }
        if (out.isEmpty()) throw new java.io.IOException("Fabric 不支持 " + mc);
        return out;
    }

    // ---------------------------------------------------------------- Quilt

    private static List<Lv> quilt(String mc) throws Exception {
        String body = Net.getMirrored("https://meta.quiltmc.org/v3/versions/loader/" + mc, "https://bmclapi2.bangbang93.com/quilt-meta/v3/versions/loader/" + mc, "Quilt 版本表");
        List<Lv> out = new ArrayList<>();
        for (Object o : Json.arr(Json.parse(body))) {
            Map<String, Object> e = Json.obj(o);
            String v = Json.str(Json.obj(e.get("loader")), "version", "");
            if (v.isEmpty()) continue;
            out.add(new Lv(v, ""));
        }
        if (out.isEmpty()) throw new java.io.IOException("Quilt 不支持 " + mc);
        return out;
    }

    // ---------------------------------------------------------------- Forge

    private static final Pattern XML_VERSION = Pattern.compile("<version>([^<]+)</version>");

    private static List<Lv> forge(String mc) throws Exception {
        // 1) BMCLAPI 按 MC 版本直接给列表（国内能通，而且不会把别的 MC 版本摆进来）
        List<Lv> out = new ArrayList<>();
        Exception first = null;
        try {
            String body = Net.get("https://bmclapi2.bangbang93.com/forge/minecraft/" + mc, 6_000, 15_000);
            for (Object o : Json.arr(Json.parse(body))) {
                String v = Json.str(Json.obj(o), "version", "");
                if (!v.isEmpty()) out.add(new Lv(v, ""));
            }
        } catch (Exception e) {
            first = e;
        }

        // 2) 官方 maven 兑底
        if (out.isEmpty()) {
            String meta = Net.get(
                    "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml",
                    8_000, 25_000);
            Matcher m = XML_VERSION.matcher(meta);
            while (m.find()) {
                String full = m.group(1);
                if (!full.startsWith(mc + "-")) continue;
                out.add(new Lv(full.substring(mc.length() + 1), ""));
            }
        }
        if (out.isEmpty()) {
            throw new java.io.IOException("Forge 没有 " + mc + " 的版本"
                    + (first == null ? "" : "（" + first.getMessage() + "）"));
        }

        // 推荐 / 最新标注（这个表 BMCLAPI 没有，失败就跳过）
        String recommended = null, latest = null;
        try {
            String promos = Net.get(
                    "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json",
                    6_000, 12_000);
            Map<String, Object> p = Json.obj(Json.obj(Json.parse(promos)).get("promos"));
            recommended = Json.str(p, mc + "-recommended", null);
            latest = Json.str(p, mc + "-latest", null);
        } catch (Exception ignored) { }

        out.sort((a, b) -> LoaderInstaller.compareVersions(b.id(), a.id()));
        String newest = out.get(0).id();
        List<Lv> noted = new ArrayList<>(out.size());
        for (Lv lv : out) {
            String note = "";
            if (lv.id().equals(recommended)) note = "推荐";
            else if (lv.id().equals(latest)) note = "最新";
            else if (note.isEmpty() && lv.id().equals(newest)) note = "最新";
            noted.add(new Lv(lv.id(), note));
        }
        return noted;
    }

    // ---------------------------------------------------------------- NeoForge

    /**
     * NeoForge 的版本前缀。它跟 MC 版本不是简单拼接，而且新命名（26.1）把旧的
     * 「取第 2、3 段」写法直接弄崩：
     * <pre>
     *   1.20.1 → 47.       （NeoForge 只在 1.20.1 用 47.x）
     *   1.20.3 → 20.3.
     *   1.21.4 → 21.4.
     *   26.1   → 26.1.     （MC 2026 起改年份命名，NeoForge 跟着用）
     * </pre>
     */
    static String neoPrefix(String mc) {
        if (mc == null || mc.trim().isEmpty()) return null;
        if ("1.20.1".equals(mc)) return "47.";
        String s = mc.startsWith("1.") ? mc.substring(2) : mc;
        if (!s.matches("\\d+(\\.\\d+)*")) return null;
        return s + ".";
    }

    private static List<Lv> neoforge(String mc) throws Exception {
        List<Lv> out = new ArrayList<>();
        Exception first = null;

        // 1) BMCLAPI 按 MC 版本给列表（官方 maven 在国内经常 DNS 被污染，直接超时）
        try {
            String body = Net.get("https://bmclapi2.bangbang93.com/neoforge/list/" + mc, 6_000, 15_000);
            for (Object o : Json.arr(Json.parse(body))) {
                Map<String, Object> e = Json.obj(o);
                String v = Json.str(e, "version", "");
                if (!v.isEmpty()) out.add(new Lv(v, v.contains("beta") ? "测试" : ""));
            }
        } catch (Exception e) {
            first = e;
        }

        // 2) 官方 maven API
        if (out.isEmpty()) {
            String prefix = neoPrefix(mc);
            if (prefix == null) throw new java.io.IOException("无法从 " + mc + " 推导 NeoForge 前缀");
            try {
                String meta = Net.get(
                        "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge",
                        8_000, 25_000);
                for (Object o : Json.arr(Json.obj(Json.parse(meta)).get("versions"))) {
                    addNeo(out, Json.str(o), prefix);
                }
            } catch (Exception e) {
                if (first == null) first = e;
            }
        }

        if (out.isEmpty()) {
            throw new java.io.IOException("NeoForge 版本表获取失败"
                    + (first == null ? "" : "（" + first.getMessage() + "）")
                    + "；官方 maven 国内常被墙，可以在「设置 → 下载源」加 BMCLAPI");
        }
        out.sort((a, b) -> LoaderInstaller.compareVersions(b.id(), a.id()));
        return out;
    }

    private static void addNeo(List<Lv> out, String v, String prefix) {
        if (v == null) return;
        String s = v.startsWith("v") ? v.substring(1) : v;
        if (!s.startsWith(prefix)) return;
        for (Lv e : out) if (e.id().equals(s)) return;
        out.add(new Lv(s, s.contains("beta") ? "测试" : ""));
    }

    // ---------------------------------------------------------------- OptiFine

    /** OptiFine 的 id 是 "HD_U/I6" 这种，配合 mcversion 拼下载地址。 */
    private static List<Lv> optifine(String mc) throws Exception {
        String body = get("https://bmclapi2.bangbang93.com/optifine/" + mc);
        List<Lv> out = new ArrayList<>();
        for (Object o : Json.arr(Json.parse(body))) {
            Map<String, Object> e = Json.obj(o);
            String type = Json.str(e, "type", "");
            String patch = Json.str(e, "patch", "");
            String file = Json.str(e, "filename", "");
            if (type.isEmpty() || patch.isEmpty()) continue;
            boolean preview = file.startsWith("preview") || patch.startsWith("pre");
            out.add(new Lv(type + "/" + patch, preview ? "预览版" : type + " " + patch));
        }
        if (out.isEmpty()) throw new java.io.IOException("OptiFine 没有 " + mc + " 的版本");
        return out;
    }

    public static List<Lv> optifineList(String mc) throws Exception {
        return optifine(mc);
    }

    // ---------------------------------------------------------------- HTTP

    static String get(String url) throws Exception {
        return Net.get(url);
    }

    private LoaderVersions() { }
}
