package dev.mcver.probe;
import dev.mcver.core.JavaRuntime;
import dev.mcver.core.JavaFinder;
import java.nio.file.*;
public final class ProbeJavaRuntime {
    public static void main(String[] a) throws Exception {
        Path gameDir = Paths.get(a[0]).toAbsolutePath();
        int need = Integer.parseInt(a[1]);
        String comp = a.length > 2 ? a[2] : null;
        Path exe = JavaRuntime.ensure(gameDir, comp, need, s -> System.out.println("[rt] " + s));
        System.out.println("result = " + exe);
        if (exe != null) {
            System.out.println("major  = " + JavaFinder.majorOfExe(exe));
            Process p = new ProcessBuilder(exe.toString(), "-version").redirectErrorStream(true).start();
            System.out.println(new String(p.getInputStream().readAllBytes()));
            p.waitFor();
        }
    }
}
