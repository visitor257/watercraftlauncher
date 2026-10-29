import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Lists every invokestatic / invokespecial that targets an *interface* method.
 * Those only exist since Java 8 and make FML 7.2 (built against ASM 4) fail with
 * "INVOKESPECIAL/STATIC on interfaces require ASM 5".
 */
public class IfaceCallScan {
    public static void main(String[] a) throws Exception {
        int total = 0;
        Set<String> kinds = new LinkedHashSet<String>();
        try (JarFile jf = new JarFile(a[0])) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                if (!e.getName().endsWith(".class")) continue;
                byte[] b;
                try (InputStream in = jf.getInputStream(e)) {
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    byte[] tmp = new byte[8192];
                    int n;
                    while ((n = in.read(tmp)) > 0) bos.write(tmp, 0, n);
                    b = bos.toByteArray();
                }
                final String owner = e.getName();
                final List<String> hits = new ArrayList<String>();
                new ClassReader(b).accept(new ClassVisitor(Opcodes.ASM5) {
                    public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exc) {
                        final String where = owner + " " + name + desc;
                        return new MethodVisitor(Opcodes.ASM5) {
                            public void visitMethodInsn(int op, String o, String n, String d, boolean itf) {
                                if (itf && (op == Opcodes.INVOKESTATIC || op == Opcodes.INVOKESPECIAL)) {
                                    hits.add(where + " -> " + o + "." + n + d
                                            + (op == Opcodes.INVOKESTATIC ? " [invokestatic]" : " [invokespecial]"));
                                }
                            }
                        };
                    }
                }, 0);
                for (String h : hits) System.out.println(h);
                total += hits.size();
            }
        }
        System.out.println("total=" + total);
    }
}
