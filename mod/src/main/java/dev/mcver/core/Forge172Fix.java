package dev.mcver.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Forge 1.7.2 的启动补丁：把 FML 7.2 的 {@code CoreModManager.sortTweakList()} 换成
 * 「排序数组副本再 set 回去」的等价物，绕开 Java 8 上的
 * {@code ConcurrentModificationException}。
 *
 * <h2>为什么必须打这个补丁</h2>
 * launchwrapper 的 {@code Launch.launch} 一边用 {@code Iterator} 遍历 tweaker 列表一边
 * {@code it.remove()}；同一个列表又以 {@code "Tweaks"} 的 key 挂在 {@code Launch.blackboard} 上。
 * FML 7.2 的 {@code sortTweakList()} 直接对这个列表调 {@code Collections.sort}。
 * Java 8 起 {@code Collections.sort} 委派给 {@code List.sort}，而 {@code ArrayList.sort}
 * 结尾<b>无条件</b> {@code modCount++} —— 迭代器立刻失效，下一次 {@code remove()} 抛 CME。
 * FML 7.10（1.7.10）改成 {@code toArray} + {@code Arrays.sort} + {@code set} 就没这问题；
 * 1.7.2 当年是配 Java 7 跑的（Java 7 的 {@code Collections.sort} 也用 toArray+set）。
 *
 * <h2>补丁做了什么</h2>
 * 只动两个地方，别处一个字节都不碰：
 * <ol>
 *   <li>往类文件的常量池<b>追加</b> 6 个条目，指向注入的
 *       {@code cpw/mods/fml/relauncher/McverSortFix.sort(List,Object)}；</li>
 *   <li>把 {@code sortTweakList} 里那一条 {@code invokestatic} 的操作数从
 *       {@code Collections.sort(List,Comparator)} 改成上面那个 Methodref
 *       （栈上是 {@code [List, Comparator]}，形参是 {@code (List,Object)}，校验器认可）。</li>
 * </ol>
 * 不新增分支、不新增栈帧，所以不必重算 StackMapTable —— 这也是刻意选「转发一个静态方法」
 * 而不是「把循环内联进去」的原因。
 *
 * <p>幂等：jar 里留了 {@value #MARKER} 条目，二次调用直接跳过。
 */
public final class Forge172Fix {

    static final String MARKER = "mcver-lwfix172.marker";
    static final String TARGET_ENTRY = "cpw/mods/fml/relauncher/CoreModManager.class";
    static final String HELPER_ENTRY = "cpw/mods/fml/relauncher/McverSortFix.class";
    /** 助手类以资源形式随 mod 打包，安装时原样塞进 Forge 的 universal jar。 */
    static final String HELPER_RESOURCE = "/mcver/forge172/McverSortFix.class";

    private Forge172Fix() {
    }

    /**
     * 给 Forge 的 universal jar 打补丁。找不到目标方法就安静跳过（不是 1.7.2 就别动）。
     *
     * @return true = 这次真的改了，false = 已经打过 / 结构不匹配 / 没有助手类资源
     */
    public static boolean patch(Path forgeJar, Consumer<String> log) {
        if (!Files.isRegularFile(forgeJar)) return false;
        byte[] helper = readResource();
        if (helper == null) {
            log.accept("  没有 McverSortFix.class 资源，跳过 1.7.2 启动补丁");
            return false;
        }

        // Windows 上不能一边开着 ZipFile 一边把自己覆盖掉 —— 先把内容全读进内存，
        // 关掉之后再写临时文件 + 原子替换。
        List<String> order = new ArrayList<>();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        byte[] original = null;
        try (ZipFile zip = new ZipFile(forgeJar.toFile())) {
            if (zip.getEntry(MARKER) != null) return false;
            java.util.Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                String name = e.getName();
                byte[] data = readAll(zip.getInputStream(e));
                if (name.equals(TARGET_ENTRY)) {
                    original = data;
                    continue;
                }
                order.add(name);
                entries.put(name, data);
            }
        } catch (IOException e) {
            log.accept("  1.7.2 启动补丁读 jar 失败：" + e);
            return false;
        }
        if (original == null) return false;

        byte[] patched = patchClass(original);
        if (patched == null) {
            log.accept("  1.7.2 启动补丁：sortTweakList 结构不匹配，跳过");
            return false;
        }

        Path tmp = forgeJar.resolveSibling(forgeJar.getFileName() + ".mcver-tmp");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(tmp))) {
            for (String name : order) put(out, name, entries.get(name));
            put(out, TARGET_ENTRY, patched);
            put(out, HELPER_ENTRY, helper);
            put(out, MARKER, "forge172-sortfix\n".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.accept("  1.7.2 启动补丁失败：" + e);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // 临时文件删不掉不影响主流程
            }
            return false;
        }
        try {
            Files.move(tmp, forgeJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.accept("  1.7.2 启动补丁写回失败：" + e);
            return false;
        }
        log.accept("  1.7.2 启动补丁已打（FML 7.2 的 tweaker 排序会崩迭代器）");
        return true;
    }

    private static void put(ZipOutputStream out, String name, byte[] data) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(data);
        out.closeEntry();
    }

    private static byte[] readResource() {
        try (InputStream in = Forge172Fix.class.getResourceAsStream(HELPER_RESOURCE)) {
            return in == null ? null : readAll(in);
        } catch (IOException e) {
            return null;
        }
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = in.read(tmp)) > 0) bos.write(tmp, 0, n);
        return bos.toByteArray();
    }

    // ------------------------------------------------------------------ 类文件手术

    /** @return 打好补丁的类字节；结构不认识就返回 null。 */
    static byte[] patchClass(byte[] b) {
        if (b.length < 20) return null;
        int count = u2(b, 8);
        int p = 10;
        int[] tag = new int[count];
        int[] start = new int[count];
        int[] end = new int[count];
        try {
            for (int i = 1; i < count; i++) {
                start[i] = p;
                tag[i] = b[p++] & 0xff;
                switch (tag[i]) {
                    case 1: p += 2 + u2(b, p); break;
                    case 7: case 8: case 16: case 19: case 20: p += 2; break;
                    case 15: p += 3; break;
                    case 3: case 4: case 9: case 10: case 11: case 12: case 17: case 18: p += 4; break;
                    case 5: case 6:
                        p += 8;
                        end[i] = p;
                        i++;
                        continue;
                    default: return null;
                }
                end[i] = p;
            }
        } catch (RuntimeException e) {
            return null;
        }
        int poolEnd = p;

        // 找 java/util/Collections.sort(List,Comparator)V
        int sortRef = 0;
        for (int i = 1; i < count; i++) {
            if (tag[i] != 10) continue;
            String owner = className(b, tag, start, end, count, u2(b, start[i] + 1));
            int nat = u2(b, start[i] + 3);                 // Methodref 里存的是 NameAndType 的下标
            if (nat <= 0 || nat >= count || tag[nat] != 12) continue;
            String name = utf8(b, tag, start, end, count, u2(b, start[nat] + 1));
            String desc = utf8(b, tag, start, end, count, u2(b, start[nat] + 3));
            if ("java/util/Collections".equals(owner) && "sort".equals(name)
                    && "(Ljava/util/List;Ljava/util/Comparator;)V".equals(desc)) {
                sortRef = i;
                break;
            }
        }
        if (sortRef == 0) return null;

        int[] code = findSortTweakListCode(b, tag, start, end, count, poolEnd);
        if (code == null) return null;
        int codeStart = code[0];
        int codeLen = code[1];

        int operandAt = -1;
        for (int i = 0; i + 2 < codeLen; i++) {
            if ((b[codeStart + i] & 0xff) == 0xb8 && u2(b, codeStart + i + 1) == sortRef) {
                operandAt = codeStart + i + 1;
                break;
            }
        }
        if (operandAt < 0) return null;

        // 追加 6 个常量池条目
        int base = count;
        int helperRef = base + 5;
        ByteArrayOutputStream add = new ByteArrayOutputStream();
        writeUtf8(add, "cpw/mods/fml/relauncher/McverSortFix");        // base+0
        add.write(7); writeU2(add, base);                             // base+1 Class
        writeUtf8(add, "sort");                                       // base+2
        writeUtf8(add, "(Ljava/util/List;Ljava/lang/Object;)V");      // base+3
        add.write(12); writeU2(add, base + 2); writeU2(add, base + 3); // base+4 NameAndType
        add.write(10); writeU2(add, base + 1); writeU2(add, base + 4); // base+5 Methodref
        byte[] appended = add.toByteArray();

        ByteArrayOutputStream out = new ByteArrayOutputStream(b.length + appended.length);
        out.write(b, 0, 8);                       // magic + minor + major
        writeU2(out, count + 6);                  // 新的常量池计数
        out.write(b, 10, poolEnd - 10);           // 原常量池
        out.write(appended, 0, appended.length);
        int restStart = poolEnd;
        int restLen = b.length - poolEnd;
        int newOperandAt = operandAt - restStart;
        byte[] rest = Arrays.copyOfRange(b, restStart, restStart + restLen);
        rest[newOperandAt] = (byte) (helperRef >> 8);
        rest[newOperandAt + 1] = (byte) helperRef;
        out.write(rest, 0, rest.length);
        return out.toByteArray();
    }

    /** @return {codeStart, codeLen} 或 null */
    private static int[] findSortTweakListCode(byte[] b, int[] tag, int[] start, int[] end,
                                               int count, int poolEnd) {
        int p = poolEnd + 6;                                   // access / this / super
        p += 2 + 2 * u2(b, p);                                 // interfaces
        for (int pass = 0; pass < 2; pass++) {
            int n = u2(b, p);
            p += 2;
            for (int i = 0; i < n; i++) {
                String name = utf8(b, tag, start, end, count, u2(b, p + 2));
                String desc = utf8(b, tag, start, end, count, u2(b, p + 4));
                int attrCount = u2(b, p + 6);
                int attrs = p + 8;                                 // 属性表从成员头之后 8 字节开始
                if (pass == 1 && "sortTweakList".equals(name) && "()V".equals(desc)) {
                    int q = attrs;
                    for (int a = 0; a < attrCount; a++) {
                        String an = utf8(b, tag, start, end, count, u2(b, q));
                        int len = u4(b, q + 2);
                        int body = q + 6;
                        if ("Code".equals(an)) {
                            int codeLen = u4(b, body + 4);
                            return new int[] { body + 8, codeLen };
                        }
                        q = body + len;
                    }
                    return null;
                }
                p = attrs;                                          // 跳过属性表
                for (int a = 0; a < attrCount; a++) {
                    p += 2;
                    p += 4 + u4(b, p);
                }
            }
        }
        return null;
    }

    private static String utf8(byte[] b, int[] tag, int[] start, int[] end, int count, int idx) {
        if (idx <= 0 || idx >= count || tag[idx] != 1) return null;
        return new String(b, start[idx] + 3, end[idx] - start[idx] - 3, StandardCharsets.UTF_8);
    }

    private static String className(byte[] b, int[] tag, int[] start, int[] end, int count, int idx) {
        if (idx <= 0 || idx >= count || tag[idx] != 7) return null;
        return utf8(b, tag, start, end, count, u2(b, start[idx] + 1));
    }

    private static int u2(byte[] b, int p) {
        return ((b[p] & 0xff) << 8) | (b[p + 1] & 0xff);
    }

    private static int u4(byte[] b, int p) {
        return ((b[p] & 0xff) << 24) | ((b[p + 1] & 0xff) << 16) | ((b[p + 2] & 0xff) << 8) | (b[p + 3] & 0xff);
    }

    private static void writeU2(ByteArrayOutputStream out, int v) {
        out.write((v >> 8) & 0xff);
        out.write(v & 0xff);
    }

    private static void writeUtf8(ByteArrayOutputStream out, String s) {
        byte[] raw = s.getBytes(StandardCharsets.UTF_8);
        out.write(1);
        writeU2(out, raw.length);
        out.write(raw, 0, raw.length);
    }
}
