import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 按「名字表」重写 jar 的成员名（类名不动）。
 *
 * <p>两种用法：
 * <pre>
 *   java -cp asm...:DedupMethods  SrgRename in.jar out.jar --csv methods.csv fields.csv
 *   java -cp asm...:DedupMethods  SrgRename in.jar out.jar --map map.tsv
 * </pre>
 *
 * <p>{@code --csv}：MCP 的 {@code fields.csv} / {@code methods.csv}（列：searge,name,side,desc），
 * 也就是 **srg 名 → 官方名**。1.20.5+ 的 mcp_config 不再发 tsrg，只发这个 CSV，
 * 而这些世代的 srg jar 恰好「类名已经是官方名、只有成员名是 m_/f_」—— 所以一张名字表就够，
 * 不需要 tsrg 中转、也不需要 tiny-remapper。
 *
 * <p>{@code --map}：显式逐类表（每行一条，TAB 分隔），用来处理**官方名不唯一**的反向
 * （official → srg）和 1.14.2/1.14.3 那种「借 1.14.4 的表」的桥接：
 * <pre>
 *   M &lt;owner&gt; &lt;name&gt; &lt;desc&gt; &lt;toName&gt;
 *   F &lt;owner&gt; &lt;name&gt; &lt;desc&gt; &lt;toName&gt;
 * </pre>
 * owner 是内部名（net/minecraft/...，斜杠），desc 是 JVM 描述符。
 *
 * <p>只改成员名，类名/描述符一律恒等（这两条在这些世代都成立）。
 */
public final class SrgRename {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("用法: SrgRename <in.jar> <out.jar> --csv <methods.csv> [fields.csv] | --map <map.tsv>");
            System.exit(2);
        }
        String in = args[0];
        String out = args[1];
        String mode = args[2];
        Map<String, String> methodMap = new HashMap<>();
        Map<String, String> fieldMap = new HashMap<>();
        Map<String, String> perClass = new HashMap<>(); // owner.name.desc -> to

        if ("--csv".equals(mode)) {
            loadCsv(args[3], methodMap);
            if (args.length > 4) loadCsv(args[4], fieldMap);
        } else if ("--map".equals(mode)) {
            for (String line : Files.readAllLines(Paths.get(args[3]))) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] f = line.split("\t");
                if (f.length < 5) continue;
                perClass.put(f[1] + "." + f[2] + f[3], f[4]);
            }
        } else {
            System.err.println("未知模式: " + mode);
            System.exit(2);
        }

        final Map<String, String> mm = methodMap;
        final Map<String, String> fm = fieldMap;
        final Map<String, String> pc = perClass;
        Remapper remapper = new Remapper() {
            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                String hit = pc.get(owner + "." + name + descriptor);
                if (hit != null) return hit;
                return mm.getOrDefault(name, name);
            }

            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                String hit = pc.get(owner + "." + name + descriptor);
                if (hit != null) return hit;
                return fm.getOrDefault(name, name);
            }
        };

        int classes = 0;
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(Paths.get(in)));
             ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(Paths.get(out)))) {
            ZipEntry e;
            byte[] buf = new byte[65536];
            while ((e = zin.getNextEntry()) != null) {
                byte[] data;
                if (e.getName().endsWith(".class")) {
                    classes++;
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    int n;
                    while ((n = zin.read(buf)) > 0) bos.write(buf, 0, n);
                    ClassReader cr = new ClassReader(bos.toByteArray());
                    ClassWriter cw = new ClassWriter(0);
                    cr.accept(new ClassRemapper(cw, remapper), 0);
                    data = cw.toByteArray();
                } else {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    int n;
                    while ((n = zin.read(buf)) > 0) bos.write(buf, 0, n);
                    data = bos.toByteArray();
                }
                ZipEntry ne = new ZipEntry(e.getName());
                ne.setTime(e.getTime());
                zout.putNextEntry(ne);
                zout.write(data);
                zout.closeEntry();
            }
        }
        System.out.println("  srgrename: " + classes + " 个类 -> " + out);
    }

    /** MCP CSV：searge,name,side,desc（第一行是表头）。 */
    private static void loadCsv(String path, Map<String, String> into) throws Exception {
        for (String line : Files.readAllLines(Paths.get(path))) {
            if (line.isEmpty()) continue;
            String[] f = line.split(",");
            if (f.length < 2) continue;
            if ("searge".equals(f[0])) continue; // 表头
            into.put(f[0], f[1]);
        }
    }

    private SrgRename() { }
}
