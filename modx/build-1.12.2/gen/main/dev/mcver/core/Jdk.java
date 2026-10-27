package dev.mcver.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Java 8 垫片：把主源码里用到的 JDK 9+ 便捷 API 收口到这里。
 *
 * <p>为什么要这么干：1.16.5 的原版 json 声明 {@code javaVersion=8}，玩家默认拿 Java 8 起游戏，
 * 而 mod 的字节码版本必须 ≤ 运行 JVM 的版本（否则 {@code UnsupportedClassVersionError}，
 * 或者被 loader 的 {@code depends java} 直接挡掉）。所以 1.16.5 这个 target 用
 * {@code -Pjava_release=8} 编，主源码就不许再碰 Java 9 以后的东西。
 *
 * <p>这里替换掉的东西：
 * <ul>
 *   <li>{@code Files.writeString(p, s, cs)} → {@link #writeString}（Java 11）；</li>
 *   <li>{@code Files.readString(p[, cs])} → {@link #readString}（Java 11）；</li>
 *   <li>{@code InputStream#readAllBytes()} → {@link #readAll}（Java 9）；</li>
 *   <li>{@code Process#pid()} → {@link #pid}（Java 9，反射读字段）；</li>
 *   <li>{@code java.util.Optional.ofNullable(dev.mcver.core.Jdk.currentExe()).map(java.nio.file.Path::toString)} → {@link #currentExe}（Java 9）；</li>
 *   <li>{@code String#isBlank()} → {@link #isBlank}（Java 11，等价语义）；</li>
 *   <li>{@code List.of()} / {@code Map.of()} → {@link #emptyList} / {@link #emptyMap}。</li>
 * </ul>
 *
 * <p>另外三个是**语法**问题，脚本改不了，只能人工：{@code var}（Java 10）、
 * 箭头 switch（Java 14）、模式匹配 instanceof（Java 16）。那些已经就地改成老写法了。
 */
public final class Jdk {

    private Jdk() { }

    // ---------------------------------------------------------------- 文件读写

    /** {@code Files.writeString(p, s, cs)} 的 Java 8 等价物。 */
    public static void writeString(Path p, CharSequence s, Charset cs) throws IOException {
        Files.write(p, s.toString().getBytes(cs));
    }

    /** {@code Files.writeString(p, s, UTF_8)}。 */
    public static void writeUtf8(Path p, CharSequence s) throws IOException {
        writeString(p, s, StandardCharsets.UTF_8);
    }

    /** 追加写一行（原来用 writeString 的 CREATE+APPEND 重载，Java 8 没有）。 */
    public static void writeAppend(Path p, CharSequence s) throws IOException {
        Files.write(p, s.toString().getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    /** {@code Files.readString(p, UTF_8)}。 */
    public static String readString(Path p) throws IOException {
        return readString(p, StandardCharsets.UTF_8);
    }

    /** {@code Files.readString(p, cs)} 的 Java 8 等价物。 */
    public static String readString(Path p, Charset cs) throws IOException {
        return new String(Files.readAllBytes(p), cs);
    }

    /** {@code InputStream#readAllBytes()} 的 Java 8 等价物。 */
    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(64, in.available()));
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    /** 读整个流当 UTF-8 文本。 */
    public static String readAllText(InputStream in) throws IOException {
        return new String(readAll(in), StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- 进程

    /**
     * {@code Process#pid()} 的 Java 8 等价物。
     *
     * <p>Java 8 的 {@code Process} 没有公开 pid，只能反射读实现类的 {@code pid} 字段
     * （Windows 是 {@code java.lang.ProcessImpl}，Unix 是 {@code java.lang.UNIXProcess}，
     * 两个都在 {@code java.base} 里，不需要 {@code --add-opens}）。
     * 拿不到就返回 -1 —— 调用方都只是拿去拼日志/提示，不是关键路径。
     */
    public static long pid(Process p) {
        if (p == null) return -1;
        try {
            Field f = p.getClass().getDeclaredField("pid");
            f.setAccessible(true);
            return f.getLong(p);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /**
     * 当前 JVM 的可执行文件路径（{@code java.util.Optional.ofNullable(dev.mcver.core.Jdk.currentExe()).map(java.nio.file.Path::toString)} 的替代）。
     *
     * <p>不用 ProcessHandle：那是 Java 9 才有的。用 {@code java.home} 拼出来，
     * 对 JDK/JRE 都成立（JRE 的 java.home 就是 jre 目录）。
     */
    public static Path currentExe() {
        String home = System.getProperty("java.home", "");
        if (home.isEmpty()) return null;
        boolean win = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        Path p = Paths.get(home, "bin", win ? "java.exe" : "java");
        return Files.isRegularFile(p) ? p : null;
    }

    // ---------------------------------------------------------------- 字符串

    /** {@code String#isBlank()} 的 Java 8 等价物（空白定义跟 JDK 11 一致：Character.isWhitespace）。 */
    public static boolean isBlank(CharSequence s) {
        if (s == null) return true;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) return false;
        }
        return true;
    }

    /** {@code URLEncoder.encode(s, UTF_8)}：Charset 重载是 Java 10，这里包一层不掉受检异常。 */
    public static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 不支持？", e);
        }
    }

    /** 把 {@code List.of()} / {@code Map.of()} 换掉用；泛型靠调用点推。 */
    public static <T> java.util.List<T> emptyList() {
        return java.util.Collections.emptyList();
    }

    public static <K, V> java.util.Map<K, V> emptyMap() {
        return java.util.Collections.emptyMap();
    }

    /** 顺手把未检查异常包一下，省得每处都写 try/catch。 */
    public static UncheckedIOException unchecked(IOException e) {
        return new UncheckedIOException(e);
    }
}
