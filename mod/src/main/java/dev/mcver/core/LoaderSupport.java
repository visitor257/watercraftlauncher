package dev.mcver.core;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 加载器 × MC 版本的兼容性规则表（**纯离线规则，不联网**）。
 *
 * <p>为什么要这张表：下载页允许选任意 MC 版本 + 任意加载器，但很多组合根本不存在
 * （Forge 没有 1.21.2、NeoForge 没有 1.20 之前的、LiteLoader 只做到 1.12.2、快照基本只有
 * Fabric/Quilt 有）。没有这张表时用户要等一次网络查询、拿到空列表才知道不行。
 *
 * <p>能不能用「n.xx+」这种区间规则一刀切？<b>只有 Fabric / Quilt / NeoForge 可以</b>：
 * <pre>
 *   Fabric    &gt;= 1.14      （1.13 及以前没有）—— 但另有 Legacy Fabric 接管 1.3～1.13.2，
 *                              所以实际等于全覆盖，见 {@link FabricMeta}
 *   Quilt     &gt;= 1.14.4
 *   NeoForge  &gt;= 1.20.1
 * </pre>
 * Forge 不行 —— 它有跳跃缺口（1.16、1.17、1.20.5、1.21.2 直接跳过，1.13 只有 1.13.2、
 * 1.14 只有 1.14.2+），只能用精确集合。LiteLoader 同理（固定 16 个版本，2017 年停更）。
 *
 * <p>所以这张表 = <b>区间规则 + 精确集合</b>混着用，而不是单一的 n.xx+。
 */
public final class LoaderSupport {

    /** 判定结果。MAYBE = 规则覆盖不到（快照 / 愚人节版），交给真实查询决定。 */
    public enum Verdict {
        OK("支持"), MAYBE("需联网确认"), NO("不支持");

        public final String label;

        Verdict(String label) { this.label = label; }
    }

    /** Forge 有构建的 MC 正式版（实测 maven-metadata.xml，77 个，2026-10）。 */
    private static final Set<String> FORGE = new LinkedHashSet<>(Arrays.asList(
            "1.1", "1.2.3", "1.2.4", "1.2.5", "1.3.2",
            "1.4.0", "1.4.1", "1.4.2", "1.4.3", "1.4.4", "1.4.5", "1.4.6", "1.4.7",
            "1.5", "1.5.1", "1.5.2", "1.6.1", "1.6.2", "1.6.3", "1.6.4", "1.7.2", "1.7.10",
            "1.8", "1.8.8", "1.8.9", "1.9", "1.9.4", "1.10", "1.10.2", "1.11", "1.11.2",
            "1.12", "1.12.1", "1.12.2", "1.13.2",
            "1.14.2", "1.14.3", "1.14.4", "1.15", "1.15.1", "1.15.2",
            "1.16.1", "1.16.2", "1.16.3", "1.16.4", "1.16.5", "1.17.1",
            "1.18", "1.18.1", "1.18.2", "1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4",
            "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.6",
            "1.21", "1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8",
            "1.21.9", "1.21.10", "1.21.11",
            "26.1", "26.1.1", "26.1.2", "26.2", "26.3"));

    /** LiteLoader 支持的全部版本（官方 versions.json，最后更新 2017-11-29）。 */
    private static final Set<String> LITELOADER = new LinkedHashSet<>(Arrays.asList(
            "1.5.2", "1.6.2", "1.6.4", "1.7.2", "1.7.10", "1.8", "1.8.9",
            "1.9", "1.9.4", "1.10", "1.10.2", "1.11", "1.11.2", "1.12", "1.12.1", "1.12.2"));

    /** 各加载器开始支持的正式版（区间规则用）。 */
    private static final String FABRIC_MIN = "1.14";
    /** Legacy Fabric（另一个项目，见 {@link FabricMeta}）把 Fabric 往前提到了 1.3。 */
    private static final String FABRIC_LEGACY_MIN = "1.3";
    private static final String QUILT_MIN = "1.14.4";
    private static final String NEOFORGE_MIN = "1.20.1";

    public static Verdict check(LoaderInstaller.Kind kind, String mc) {
        if (kind == null || kind == LoaderInstaller.Kind.NONE) return Verdict.OK;
        if (mc == null || mc.trim().isEmpty()) return Verdict.MAYBE;

        boolean release = isRelease(mc);
        switch (kind) {
            case FABRIC:
                // 官方 Fabric >= 1.14；再往前是 Legacy Fabric（1.3+），两段合起来等于全覆盖。
                // 快照/愚人节版规则表没法穷举 → MAYBE
                return release
                        ? (ge(mc, FABRIC_MIN) || ge(mc, FABRIC_LEGACY_MIN) ? Verdict.OK : Verdict.NO)
                        : Verdict.MAYBE;
            case QUILT:
                return release ? (ge(mc, QUILT_MIN) ? Verdict.OK : Verdict.NO) : Verdict.MAYBE;
            case NEOFORGE:
                if (!release) return Verdict.NO;               // NeoForge 从不为快照发版
                return ge(mc, NEOFORGE_MIN) ? Verdict.OK : Verdict.NO;
            case FORGE:
                if (!release) return Verdict.NO;               // Forge 基本不为快照发版
                return FORGE.contains(mc) ? Verdict.OK : Verdict.NO;
            default:
                return Verdict.MAYBE;
        }
    }

    /** 给界面用的一句话说明；支持时返回 null。 */
    public static String reason(LoaderInstaller.Kind kind, String mc) {
        Verdict v = check(kind, mc);
        if (v != Verdict.NO) return null;
        switch (kind) {
            case FABRIC:
                return "Fabric 与 Legacy Fabric 都没有 " + mc + " 的构建";
            case QUILT:
                return "Quilt 从 1.14.4 才开始支持，" + mc + " 没有构建";
            case NEOFORGE:
                return isRelease(mc)
                        ? "NeoForge 从 1.20.1 才开始，" + mc + " 没有构建"
                        : mc + " 是快照/愚人节版本，NeoForge 不为它发版";
            case FORGE:
                return isRelease(mc)
                        ? "Forge 没有 " + mc + " 的构建（该版本被 Forge 跳过了）"
                        : mc + " 是快照/愚人节版本，Forge 不为它发版";
            default:
                return kind.label + " 不支持 " + mc;
        }
    }

    /** LiteLoader 只认固定 16 个版本（加载器本身 2017 年就停更了）。 */
    public static boolean liteLoaderSupported(String mc) {
        return mc != null && LITELOADER.contains(mc);
    }

    public static Set<String> liteLoaderVersions() {
        return LITELOADER;
    }

    public static Set<String> forgeVersions() {
        return FORGE;
    }

    /** 正式版判定：只认 1.2.3 / 26.1 这种纯数字点分。快照/愚人节/实验版都会落到 false。 */
    static boolean isRelease(String mc) {
        String s = mc.trim().toLowerCase(Locale.ROOT);
        return s.matches("\\d{1,2}(\\.\\d{1,2}){0,3}");
    }

    private static boolean ge(String mc, String min) {
        return LoaderInstaller.compareVersions(mc, min) >= 0;
    }

    private LoaderSupport() { }
}
