package dev.mcver.core;
import java.nio.file.*;
public final class ProbeMaven {
    public static void main(String[] a) throws Exception {
        Path dir = Paths.get(a[0]).toAbsolutePath();
        Sources.setActive(Sources.BMCLAPI.label());
        int ok = 0, bad = 0;
        for (String line : Files.readAllLines(Paths.get(a[1]))) {
            String c = line.trim();
            if (c.isEmpty()) continue;
            try {
                Path p = LoaderInstaller.ensureMaven(dir, c);
                if (Files.size(p) > 0) ok++; else { bad++; System.out.println("EMPTY " + c); }
            } catch (Exception e) {
                bad++;
                System.out.println("FAIL " + c + "  <- " + e.getMessage());
            }
        }
        System.out.println("ok=" + ok + " fail=" + bad);
    }
}
