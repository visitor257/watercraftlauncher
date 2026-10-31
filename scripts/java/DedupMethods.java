import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 输入 jar 的「重名成员」清洗器。
 *
 * <p>为什么要它：mcp_config 某些版本的 tsrg 会把两个混淆方法写成同一个 srg 名
 * （实测 1.16.2：`aag`=ServerWorld 里 `a` 和 `b` 两个 (Laag;Lfx;)V 都叫 func_241122_a_），
 * Forge 装出来的 `client-*-srg.jar` 于是**真的**带了两个同名同签名的方法 ——
 * 这是非法字节码，tiny-remapper 直接 `duplicate method … in inputs` 退出。
 *
 * <p>处理：同名同签名的成员只保留第一个（ASM 会照常读出来两个，
 * 本工具在 visitMethod 里丢后者）。其余条目字节原样拷贝，不动 MANIFEST。
 *
 * <p>用法：java -cp <asm jars;本类所在目录> DedupMethods &lt;in.jar&gt; &lt;out.jar&gt;
 * 只会创建/覆盖 out.jar。
 */
public final class DedupMethods {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("用法: DedupMethods <in.jar> <out.jar>");
            System.exit(2);
        }
        String in = args[0];
        String out = args[1];
        int classes = 0;
        int dropped = 0;
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(Paths.get(in)));
             ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(Paths.get(out)))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                byte[] data = readAll(zin);
                if (e.getName().endsWith(".class")) {
                    classes++;
                    byte[] fixed = dedup(data);
                    if (fixed != null) {
                        dropped++;
                        data = fixed;
                    }
                }
                ZipEntry ne = new ZipEntry(e.getName());
                ne.setTime(e.getTime());
                zout.putNextEntry(ne);
                zout.write(data);
                zout.closeEntry();
            }
        }
        System.out.println("  dedup: " + classes + " 个类，修了 " + dropped + " 个");
    }

    /** 有重复方法时返回重写后的字节，否则返回 null。 */
    private static byte[] dedup(byte[] data) {
        ClassReader cr;
        try {
            cr = new ClassReader(data);
        } catch (RuntimeException ex) {
            return null; // 读不了就别动
        }
        final boolean[] changed = { false };
        ClassWriter cw = new ClassWriter(0);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            private final Set<String> seen = new HashSet<>();

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (!seen.add(name + descriptor)) {
                    changed[0] = true;
                    return null; // 丢掉重复的那个
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        }, 0);
        return changed[0] ? cw.toByteArray() : null;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    private DedupMethods() { }
}
