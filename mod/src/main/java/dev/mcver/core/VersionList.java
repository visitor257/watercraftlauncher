package dev.mcver.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** 官方版本清单（只取 id / type / 发布时间 / json 地址）。 */
public final class VersionList {

    public static final class V {

        // —— 原来是 record，为了 Java 8 兼容改写成普通类；字段名/方法名跟 record 一致，
        //    所以 found.exe 和 found.exe() 两种写法都还能用。
        public final String id;
        public final String type;
        public final String releaseTime;
        public final String url;

        public V(String id, String type, String releaseTime, String url) {
            this.id = id;
            this.type = type;
            this.releaseTime = releaseTime;
            this.url = url;
        }

        public String id() { return id; }
        public String type() { return type; }
        public String releaseTime() { return releaseTime; }
        public String url() { return url; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof V)) return false;
            V that = (V) o;
            return true
                && java.util.Objects.equals(id, that.id)
                && java.util.Objects.equals(type, that.type)
                && java.util.Objects.equals(releaseTime, that.releaseTime)
                && java.util.Objects.equals(url, that.url);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(
                java.util.Objects.hashCode(id),
                java.util.Objects.hashCode(type),
                java.util.Objects.hashCode(releaseTime),
                java.util.Objects.hashCode(url));
        }

        @Override
        public String toString() {
            return "V[" + "id=" + id + ", " + "type=" + type + ", " + "releaseTime=" + releaseTime + ", " + "url=" + url + "]";
        }

        // —— 以下为原 record 里的自定义方法

        public boolean snapshot() {
            return "snapshot".equals(type) || "old_beta".equals(type) || "old_alpha".equals(type);
        }
    }

    // ================================================================ 分类（下载页的四个页签）

    public static final int CAT_RELEASE = 0;
    public static final int CAT_SNAPSHOT = 1;
    public static final int CAT_OLD = 2;
    public static final int CAT_FOOLS = 3;

    public static final String[] CAT_LABELS = { "正式", "快照", "远古", "愚人节" };

    /**
     * 愚人节版本。清单里它们也标 {@code snapshot}（Mojang 没有单独的 type），只能按 id 认。
     *
     * <p>清单里 921 个版本中，只有这 9 个是「4/1 前后、且不在任何 pre/rc 链上」的独立版本。
     * 注意 {@code 22w13a} / {@code 21w13a} / {@code 17w13b} 也是 3/31 发的，但它们是当年的正常快照
     * （前后有 22w12a → 22w14a 这种邻居），所以不在表里。
     *
     * <p>{@code 26w14a} 是 2026 那次：26.x 起快照改叫 {@code 26.N-snapshot-M}，它是唯一一个旧式 id，
     * 发在 4/1，且 26.2 的第一张快照是 4/7 才出的 —— 中间这个位置就是愚人节槽位。
     */
    private static final java.util.Set<String> FOOLS =
            new java.util.HashSet<String>(java.util.Arrays.asList(
                    "15w14a",
                    "1.RV-Pre1",
                    "3D Shareware v1.34",
                    "20w14infinite",
                    "22w13oneblockatatime",
                    "23w13a_or_b",
                    "24w14potato",
                    "25w14craftmine",
                    "26w14a"));

    /** 版本属于哪个分类。愚人节先判 —— 它们在清单里也是 snapshot。 */
    public static int catOf(V v) {
        if (v == null) return CAT_RELEASE;
        String id = v.id();
        if (id != null && FOOLS.contains(id)) return CAT_FOOLS;
        String t = v.type();
        if ("old_beta".equals(t) || "old_alpha".equals(t)) return CAT_OLD;
        if ("snapshot".equals(t)) return CAT_SNAPSHOT;
        return CAT_RELEASE;
    }

    /** 某个分类下的版本数（给页签显示用）。 */
    public static int count(List<V> all, int cat) {
        int n = 0;
        for (V v : all) if (catOf(v) == cat) n++;
        return n;
    }

    private static final long MIN_INTERVAL = 500;

    public static List<V> fetch(Sources.Src src) throws Exception {
        String url = src == null ? Installer.MANIFEST_OFFICIAL : src.manifestUrl();

        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String body = Net.get(url, 8_000, 20_000);
                Map<String, Object> root = Installer.Json.obj(Installer.Json.parse(body));
                List<V> out = new ArrayList<>();
                for (Object o : Installer.Json.arrOrEmpty(root.get("versions"))) {
                    Map<String, Object> m = Installer.Json.obj(o);
                    out.add(new V(
                            Installer.Json.str(m.get("id")),
                            Installer.Json.str(m.get("type")),
                            Installer.Json.str(m.get("releaseTime")),
                            Installer.Json.str(m.get("url"))));
                }
                if (out.isEmpty()) throw new java.io.IOException("清单里没有版本");
                return out;
            } catch (Exception e) {
                last = e;
                // 镜像挂了就退回官方
                if (url != null && !url.equals(Installer.MANIFEST_OFFICIAL) && attempt == 0) {
                    url = Installer.MANIFEST_OFFICIAL;
                    continue;
                }
                try { Thread.sleep(700); } catch (InterruptedException ignored) { }
            }
        }
        throw last;
    }

    public static String latestRelease(List<V> list) {
        for (V v : list) if ("release".equals(v.type())) return v.id();
        return list.isEmpty() ? "?" : list.get(0).id();
    }

    public static List<V> filter(List<V> all, String query, boolean showSnapshots) {
        Predicate<V> p = v -> showSnapshots || !v.snapshot();
        if (query != null && !query.trim().isEmpty()) {
            String q = query.trim().toLowerCase(java.util.Locale.ROOT);
            p = p.and(v -> v.id().toLowerCase(java.util.Locale.ROOT).contains(q));
        }
        List<V> out = new ArrayList<>();
        for (V v : all) if (p.test(v)) out.add(v);
        return out;
    }

    /** 按分类 + 搜索词筛。搜索只在当前分类里找（页签是显式选择，跨类搜反而意外）。 */
    public static List<V> filter(List<V> all, String query, int cat) {
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        List<V> out = new ArrayList<>();
        for (V v : all) {
            if (catOf(v) != cat) continue;
            if (!q.isEmpty() && !v.id().toLowerCase(java.util.Locale.ROOT).contains(q)) continue;
            out.add(v);
        }
        return out;
    }

    private VersionList() { }
}
