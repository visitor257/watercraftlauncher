package dev.mcver.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** 官方版本清单（只取 id / type / 发布时间 / json 地址）。 */
public final class VersionList {

    public record V(String id, String type, String releaseTime, String url) {
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
        if (query != null && !query.isBlank()) {
            String q = query.trim().toLowerCase(java.util.Locale.ROOT);
            p = p.and(v -> v.id().toLowerCase(java.util.Locale.ROOT).contains(q));
        }
        List<V> out = new ArrayList<>();
        for (V v : all) if (p.test(v)) out.add(v);
        return out;
    }

    private VersionList() { }
}
