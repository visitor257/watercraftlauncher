package dev.mcver.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 版本目录改名 / 建议名称。装完想让版本叫别的名字就靠它。 */
public final class VersionMeta {

    /** 给个默认名字：1.21.4 / 1.21.4-Fabric_0.16.14 / 1.21.4-OptiFine_HD_U_I6 */
    public static String suggest(String mc, LoaderInstaller.Kind kind, String kindVersion,
                                 LoaderInstaller.Shader shader, String shaderVersion) {
        String base = mc;
        if (kind != null && kind != LoaderInstaller.Kind.NONE && kindVersion != null) {
            base = mc + "-" + kind.label + "_" + kindVersion.replace('/', '_').replace(' ', '_');
        }
        if (shader != null && shader != LoaderInstaller.Shader.NONE && shaderVersion != null) {
            base = base + "-" + shader.label + "_" + shaderVersion.replace('/', '_');
        }
        return base;
    }

    /** 名字里不能出现的字符。 */
    public static boolean validName(String name) {
        if (name == null || name.isBlank() || name.length() > 64) return false;
        return name.matches("[A-Za-z0-9._\\-+() \\u4e00-\\u9fa5]{1,64}");
    }

    /**
     * 把 versions/&lt;from&gt; 改成 versions/&lt;to&gt;，顺手把目录里的 json 改名并把 "id" 改掉。
     * 成功返回 null，失败返回原因。
     */
    public static String rename(Path gameDir, String from, String to) {
        if (from == null || to == null || from.equals(to)) return null;
        if (!validName(to)) return "名字里有不允许的字符";
        Path dir = gameDir.resolve("versions");
        Path src = dir.resolve(from);
        Path dst = dir.resolve(to);
        if (!Files.isDirectory(src)) return "找不到 versions/" + from;
        if (Files.exists(dst)) return "已经有一个叫 " + to + " 的版本了";
        try {
            Files.move(src, dst);

            Path oldJson = dst.resolve(from + ".json");
            Path newJson = dst.resolve(to + ".json");
            if (Files.isRegularFile(oldJson)) {
                String text = Files.readString(oldJson, StandardCharsets.UTF_8);
                text = text.replaceFirst("(\"id\"\\s*:\\s*\")" + java.util.regex.Pattern.quote(from) + "(\")",
                        "$1" + to.replace("\\", "\\\\").replace("$", "\\$") + "$2");
                Files.writeString(newJson, text, StandardCharsets.UTF_8);
                if (!oldJson.equals(newJson)) Files.deleteIfExists(oldJson);
            }
            // 版本隔离目录里的引用不用管；日志目录留着无害
            return null;
        } catch (Exception e) {
            return "改名失败: " + e.getMessage();
        }
    }

    /** 版本是否开了隔离（versions/&lt;id&gt;/mods 存在）。 */
    public static boolean isolated(Path gameDir, String id) {
        return Files.isDirectory(gameDir.resolve("versions").resolve(id).resolve("mods"));
    }

    private VersionMeta() { }
}
