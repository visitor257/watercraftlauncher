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

    private VersionList() { }
}
