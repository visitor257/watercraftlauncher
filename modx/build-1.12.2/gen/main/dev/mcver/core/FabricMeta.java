package dev.mcver.core;

/**
 * Fabric 官方的 meta / maven 地址，按 MC 版本分流。
 *
 * <p>为什么需要这个类：<b>Fabric 官方只从 1.14 开始支持</b>，1.13.2 及更早的「Fabric」是另一个
 * 项目 —— <a href="https://legacyfabric.net">Legacy Fabric</a>，它有自己的 meta
 * （{@code meta.legacyfabric.net}）和 maven（{@code maven.legacyfabric.net}），
 * 而且 1.12.2 及更早还需要一套 legacy 版 LWJGL（{@code org.lwjgl.lwjgl:lwjgl:2.9.4+legacyfabric.N}，
 * 在官方 maven 上根本没有）。
 *
 * <p>好消息是 Legacy 的 profile json 和官方<b>结构完全一致</b>（每个 library 都带 {@code url} 字段），
 * 所以安装链路不用分叉 —— 只要把 meta / maven 的 base 换掉就行。
 */
public final class FabricMeta {

    private FabricMeta() { }

    /** 官方 Fabric 的下限：1.14。低于它走 Legacy Fabric。 */
    private static final int OFFICIAL_MIN_MINOR = 14;

    public static boolean isLegacy(String mc) {
        int[] v = parse(mc);
        if (v == null) return false;
        // 只有 1.x 才有「1.13 及以前」这个概念；26.x / 快照一律当现代 Fabric
        return v[0] == 1 && v[1] < OFFICIAL_MIN_MINOR;
    }

    /** meta base，带结尾斜杠。 */
    public static String metaBase(String mc) {
        return isLegacy(mc) ? "https://meta.legacyfabric.net/" : "https://meta.fabricmc.net/";
    }

    /** maven base，带结尾斜杠。profile 里的 library 没写 url 时用它兜底。 */
    public static String mavenBase(String mc) {
        return isLegacy(mc) ? "https://maven.legacyfabric.net/" : "https://maven.fabricmc.net/";
    }

    /** BMCLAPI 的镜像路径前缀；Legacy 没有对应镜像，返回 null。 */
    public static String mirrorBase(String mc) {
        return isLegacy(mc) ? null : "https://bmclapi2.bangbang93.com/fabric-meta/";
    }

    /** "1.12.2" → {1, 12}；解析不出来返回 null。 */
    private static int[] parse(String mc) {
        if (mc == null) return null;
        String[] parts = mc.split("\\.");
        if (parts.length < 2) return null;
        try {
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
