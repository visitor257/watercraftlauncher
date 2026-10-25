package dev.mcver.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 小 HTTP 工具：HttpURLConnection 实现，带明确的连接/读取超时。
 *
 * 为什么不用 java.net.http.HttpClient：在游戏 JVM 里对某些主机
 * （实测 meta.fabricmc.net）会卡在 send() 里不返回，请求超时也不生效，
 * 卡了 7 分钟还不动。HttpURLConnection 的 setReadTimeout 是硬超时，靠谱。
 */
public final class Net {

    private static final String UA = "WaterCraftLauncher/1.0";

    public static String get(String url) throws IOException {
        return get(url, 10_000, 25_000);
    }

    public static String get(String url, int connectMs, int readMs) throws IOException {
        HttpURLConnection c = open(url, connectMs, readMs);
        try {
            int code = c.getResponseCode();
            if (code / 100 != 2) throw new IOException("HTTP " + code + "  " + url);
            try (InputStream in = c.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    /** 先走当前下载源，失败了换官方再试一次。 */
    public static String getMirrored(String official, String builtinMirror, String what) throws IOException {
        Sources.Src src = Sources.active();
        String first = src.official() ? official : src.rewrite(official);
        if (first.equals(official)) {
            // 官方源：先官方（或调用方给的备用镜像）
            try {
                return get(official, 6_000, 15_000);
            } catch (IOException e) {
                if (builtinMirror == null) throw e;
                try {
                    return get(builtinMirror, 8_000, 20_000);
                } catch (IOException e2) {
                    throw new IOException(what + " 官方和镜像都失败: " + e.getMessage() + " / " + e2.getMessage());
                }
            }
        }
        try {
            return get(first, 6_000, 15_000);
        } catch (IOException e) {
            try {
                return get(official, 8_000, 20_000);
            } catch (IOException e2) {
                throw new IOException(what + " 镜像和官方都失败: " + e.getMessage() + " / " + e2.getMessage());
            }
        }
    }

    public static Path download(String url, Path dst) throws IOException {
        return download(url, dst, 15_000, 60_000);
    }

    public static Path download(String url, Path dst, int connectMs, int readMs) throws IOException {
        if (Files.isRegularFile(dst) && Files.size(dst) > 4096
                && (!dst.getFileName().toString().endsWith(".jar") || isJar(dst))) {
            return dst;
        }
        Files.createDirectories(dst.getParent() == null ? dst.toAbsolutePath().getParent() : dst.getParent());
        Path tmp = dst.resolveSibling(dst.getFileName() + ".part");

        HttpURLConnection c = open(url, connectMs, readMs);
        try {
            int code = c.getResponseCode();
            if (code / 100 != 2) throw new IOException("HTTP " + code + "  " + url);
            try (InputStream in = c.getInputStream(); OutputStream out = Files.newOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING);
            return dst;
        } catch (IOException e) {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
            throw e;
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String url, int connectMs, int readMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(connectMs);
        c.setReadTimeout(readMs);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "*/*");
        // 明确不用 gzip：省得自己解压
        c.setRequestProperty("Accept-Encoding", "identity");
        return c;
    }

    /** jar 是不是完整的（能不能当 zip 打开）。下载中途断掉的文件会在这里被拓出来。 */
    public static boolean isJar(Path p) {
        if (!Files.isRegularFile(p)) return false;
        try {
            if (Files.size(p) < 256) return false;
        } catch (IOException e) {
            return false;
        }
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(p.toFile())) {
            return z.size() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Net() { }
}
