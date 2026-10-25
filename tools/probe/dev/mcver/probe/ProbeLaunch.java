package dev.mcver.probe;

import dev.mcver.core.Launcher;
import dev.mcver.core.Sources;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 无界面启动探针：只用 {@code Launcher}（生产代码）拼参数 + 起真进程，然后盯日志。
 *
 * <pre>
 * java -cp out dev.mcver.probe.ProbeLaunch &lt;gameDir&gt; &lt;versionId&gt; &lt;timeoutSec&gt;
 * </pre>
 *
 * 成功判据（与 PLAN 一致）：日志里出现 {@code Setting user}。
 * 离线账号的 401 / Realms 认证失败是正常的，不算失败。
 *
 * 退出码：0 = 到 Setting user；1 = 崩溃/超时；2 = 参数错。
 */
public final class ProbeLaunch {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("用法: ProbeLaunch <gameDir> <versionId> <timeoutSec>");
            System.exit(2);
        }
        Path gameDir = Paths.get(args[0]).toAbsolutePath().normalize();
        String id = args[1];
        int timeout = Integer.parseInt(args[2]);

        Sources.setActive(Sources.BMCLAPI.label());

        Launcher.Plan p = Launcher.plan(gameDir, id);
        System.out.println("[probe] mainClass=" + p.mainClass);
        System.out.println("[probe] requiredJava=" + p.requiredJava);
        System.out.println("[probe] assets=" + p.assetIndexId + " missingAssets=" + p.assetMissing);
        System.out.println("[probe] classpath entries=" + p.classpath.size());
        System.out.println("[probe] missing files=" + p.missing.size());
        for (Path m : p.missing) System.out.println("[probe]   MISSING " + m);
        for (String w : p.warnings) System.out.println("[probe]   WARN " + w);
        if (!p.ready()) {
            System.out.println("[probe] ERROR plan 不完整（mainClass 或 classpath 空）");
            System.exit(1);
        }

        Launcher.Running r = Launcher.launch(p, "Probe", "00000000000000000000000000000000", "0", 2048);
        System.out.println("[probe] java=" + r.java + "  (" + r.javaNote + ")");
        System.out.println("[probe] log=" + r.logFile);

        long deadline = System.currentTimeMillis() + timeout * 1000L;
        boolean ok = false;
        boolean sawTitle = false;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(1500);
            List<String> lines = safeRead(r.logFile);
            for (String s : lines) {
                if (s.contains("Setting user")) ok = true;
                if (s.contains("Backend library:")) sawTitle = true;
            }
            if (ok) break;
            if (!r.process.isAlive()) {
                // 给它一点时间把日志冲刷完
                Thread.sleep(1500);
                List<String> tail = safeRead(r.logFile);
                System.out.println("[probe] 进程已退出，代码 " + r.process.exitValue());
                printTail(tail, 40);
                String root = firstError(tail);
                System.out.println("[probe] ERROR " + (root == null ? "进程过早退出" : root));
                System.exit(1);
            }
        }

        if (ok) {
            System.out.println("[probe] OK 到达 Setting user（Backend library: " + sawTitle + "）");
        } else {
            System.out.println("[probe] TIMEOUT " + timeout + "s 内没看到 Setting user");
            printTail(safeRead(r.logFile), 40);
        }

        kill(r);
        System.exit(ok ? 0 : 1);
    }

    private static void kill(Launcher.Running r) {
        try {
            r.process.descendants().forEach(ProcessHandle::destroyForcibly);
            r.process.destroyForcibly();
            r.process.waitFor();
        } catch (Exception ignored) { }
    }

    private static List<String> safeRead(Path f) {
        try {
            return Files.readAllLines(f, StandardCharsets.UTF_8);
        } catch (Exception e) {
            try {
                return Files.readAllLines(f, StandardCharsets.ISO_8859_1);
            } catch (Exception e2) {
                return List.of();
            }
        }
    }

    private static void printTail(List<String> lines, int n) {
        int from = Math.max(0, lines.size() - n);
        for (int i = from; i < lines.size(); i++) System.out.println("[log] " + lines.get(i));
    }

    /** 找第一条像样的错误行（堆栈顶那句才是根因）。 */
    private static String firstError(List<String> lines) {
        for (String raw : lines) {
            String s = raw.trim();
            if (s.isEmpty() || s.startsWith("at ") || s.startsWith("... ")) continue;
            if (s.contains("Exception") || s.contains("Error") || s.contains("Caused by")
                    || s.contains("Could not") || s.contains("Unrecognized")) {
                return s.length() > 240 ? s.substring(0, 240) + "…" : s;
            }
        }
        return null;
    }

    private ProbeLaunch() { }
}
