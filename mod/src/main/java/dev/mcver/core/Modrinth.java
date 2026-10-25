package dev.mcver.core;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Modrinth 的只读接口：查版本、下载文件。用来装 Iris / Sodium。 */
public final class Modrinth {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public record File(String url, String filename) { }

    public record Ver(String id, String number, String channel, List<File> files) { }

    public static List<Ver> versions(String project, String mc, String loader) throws Exception {
        String url = "https://api.modrinth.com/v2/project/" + project + "/version"
                + "?game_versions=" + enc("[\"" + mc + "\"]")
                + (loader == null ? "" : "&loaders=" + enc("[\"" + loader + "\"]"));
        return parseList(url);
    }

    private static List<Ver> parseList(String url) throws Exception {
        String body = Net.get(url);

        List<Ver> out = new ArrayList<>();
        for (Object o : Json.arr(Json.parse(body))) {
            Map<String, Object> m = Json.obj(o);
            List<File> files = new ArrayList<>();
            for (Object f : Json.arr(m.get("files"))) {
                Map<String, Object> fm = Json.obj(f);
                String u = Json.str(fm, "url", "");
                String name = Json.str(fm, "filename", "");
                if (!u.isEmpty()) files.add(new File(u, name));
            }
            if (files.isEmpty()) continue;
            out.add(new Ver(Json.str(m, "id", ""), Json.str(m, "version_number", ""),
                    Json.str(m, "version_type", "release"), files));
        }
        return out;
    }

    /** 下载到 dst 目录，返回落盘路径。 */
    public static Path download(File f, Path dir) throws Exception {
        Files.createDirectories(dir);
        Path dst = dir.resolve(f.filename());
        if (Files.isRegularFile(dst) && Files.size(dst) > 1024) return dst;
        return Net.download(f.url(), dst, 15_000, 120_000);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private Modrinth() { }
}
