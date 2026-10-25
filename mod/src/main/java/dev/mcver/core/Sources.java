package dev.mcver.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 下载源：官方 + 用户自己加的镜像。
 *
 * 默认只有官方。镜像按「根地址」配置，约定与 BMCLAPI 一致：
 *   {root}/mc/game/version_manifest_v2.json   版本清单
 *   {root}/maven/...                          libraries.minecraft.net 的镜像
 *   {root}/assets/xx/yy                       resources.download.minecraft.net 的镜像
 * 所以 BMCLAPI 只是 root=https://bmclapi2.bangbang93.com 的一个普通条目。
 */
public final class Sources {

    public record Src(String name, String root) {

        public boolean official() {
            return root == null || root.isBlank();
        }

        public String rootClean() {
            String r = root == null ? "" : root.trim();
            while (r.endsWith("/")) r = r.substring(0, r.length() - 1);
            return r;
        }

        public String manifestUrl() {
            return official()
                    ? Installer.MANIFEST_OFFICIAL
                    : rootClean() + "/mc/game/version_manifest_v2.json";
        }

        /** 把 Mojang 的地址改写到这个源；官方源原样返回。 */
        public String rewrite(String url) {
            if (url == null || official()) return url;
            String r = rootClean();
            return url
                    .replace("https://launchermeta.mojang.com", r)
                    .replace("https://piston-meta.mojang.com", r)
                    .replace("https://piston-data.mojang.com", r)
                    .replace("https://libraries.minecraft.net", r + "/maven")
                    .replace("https://resources.download.minecraft.net", r + "/assets")
                    .replace("https://launcher.mojang.com", r);
        }

        public String label() {
            return official() ? name : name + "  " + rootClean();
        }
    }

    public static final Src OFFICIAL = new Src("官方 (Mojang)", "");
    /** 常用国内镜像预设：用户点一下加进去，不是默认。 */
    public static final Src BMCLAPI = new Src("BMCLAPI (国内)", "https://bmclapi2.bangbang93.com");

    private Sources() { }

    /** 可选的全部源：官方永远第一。 */
    public static List<Src> all() {
        List<Src> out = new ArrayList<>();
        out.add(OFFICIAL);
        for (String s : Config.downloadSources) {
            Src src = decode(s);
            if (src == null) continue;
            if (src.official()) continue;
            boolean dup = out.stream().anyMatch(x -> x.name().equalsIgnoreCase(src.name()));
            if (!dup) out.add(src);
        }
        return out;
    }

    /** 当前生效的源；配置里那个名字找不到就退回官方。 */
    public static Src active() {
        String want = Config.downloadSource == null ? "" : Config.downloadSource.trim();
        if (want.isEmpty() || "official".equalsIgnoreCase(want) || "mirror".equalsIgnoreCase(want)) {
            // 兼容老配置：mirror 曾经是内置 BMCLAPI
            if ("mirror".equalsIgnoreCase(want)) {
                for (Src s : all()) if (s.rootClean().contains("bangbang93")) return s;
            }
            return OFFICIAL;
        }
        for (Src s : all()) if (s.name().equalsIgnoreCase(want)) return s;
        return OFFICIAL;
    }

    public static void setActive(String name) {
        Config.downloadSource = name == null ? "" : name.trim();
        Config.save();
    }

    /** 加一个镜像（同名覆盖）。 */
    public static void add(String name, String root) {
        String n = name == null ? "" : name.trim();
        String r = root == null ? "" : root.trim();
        if (n.isEmpty()) n = r;
        if (n.isEmpty() || r.isEmpty()) return;
        remove(n);
        Config.downloadSources.add(encode(new Src(n, r)));
        Config.save();
    }

    public static void remove(String name) {
        Config.downloadSources.removeIf(s -> {
            Src src = decode(s);
            return src != null && src.name().equalsIgnoreCase(name);
        });
        Config.save();
    }

    public static boolean isBuiltin(Src s) {
        return s.official();
    }

    // ---------------------------------------------------------------- 编码

    /** 配置里存 "名字=根地址"，简单好读。 */
    static String encode(Src s) {
        return s.name() + "=" + s.rootClean();
    }

    static Src decode(String raw) {
        if (raw == null || raw.isBlank()) return null;
        int i = raw.indexOf('=');
        if (i <= 0 || i == raw.length() - 1) return null;
        return new Src(raw.substring(0, i).trim(), raw.substring(i + 1).trim());
    }

    // ---------------------------------------------------------------- 测试

    /**
     * 试一下这个源通不通：拉一次版本清单，返回耗时毫秒。
     * 失败抛异常，界面把 message 显示出来。
     */
    public static long probe(Src s) throws IOException {
        long t0 = System.currentTimeMillis();
        String body = Net.get(s.manifestUrl(), 6_000, 15_000);
        if (!body.contains("\"versions\"")) throw new IOException("返回内容不像版本清单");
        return System.currentTimeMillis() - t0;
    }
}
