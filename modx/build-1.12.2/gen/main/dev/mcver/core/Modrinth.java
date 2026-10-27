package dev.mcver.core;

import java.net.URI;
import java.net.URLEncoder;
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

    public static final class File {

        // —— 原来是 record，为了 Java 8 兼容改写成普通类；字段名/方法名跟 record 一致，
        //    所以 found.exe 和 found.exe() 两种写法都还能用。
        public final String url;
        public final String filename;

        public File(String url, String filename) {
            this.url = url;
            this.filename = filename;
        }

        public String url() { return url; }
        public String filename() { return filename; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof File)) return false;
            File that = (File) o;
            return true
                && java.util.Objects.equals(url, that.url)
                && java.util.Objects.equals(filename, that.filename);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(
                java.util.Objects.hashCode(url),
                java.util.Objects.hashCode(filename));
        }

        @Override
        public String toString() {
            return "File[" + "url=" + url + ", " + "filename=" + filename + "]";
        }
    }

    public static final class Ver {

        // —— 原来是 record，为了 Java 8 兼容改写成普通类；字段名/方法名跟 record 一致，
        //    所以 found.exe 和 found.exe() 两种写法都还能用。
        public final String id;
        public final String number;
        public final String channel;
        public final List<File> files;

        public Ver(String id, String number, String channel, List<File> files) {
            this.id = id;
            this.number = number;
            this.channel = channel;
            this.files = files;
        }

        public String id() { return id; }
        public String number() { return number; }
        public String channel() { return channel; }
        public List<File> files() { return files; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Ver)) return false;
            Ver that = (Ver) o;
            return true
                && java.util.Objects.equals(id, that.id)
                && java.util.Objects.equals(number, that.number)
                && java.util.Objects.equals(channel, that.channel)
                && java.util.Objects.equals(files, that.files);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(
                java.util.Objects.hashCode(id),
                java.util.Objects.hashCode(number),
                java.util.Objects.hashCode(channel),
                java.util.Objects.hashCode(files));
        }

        @Override
        public String toString() {
            return "Ver[" + "id=" + id + ", " + "number=" + number + ", " + "channel=" + channel + ", " + "files=" + files + "]";
        }
    }

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
        return dev.mcver.core.Jdk.urlEncode(s);
    }

    private Modrinth() { }
}
