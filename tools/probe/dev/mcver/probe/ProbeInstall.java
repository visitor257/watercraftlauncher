package dev.mcver.probe;

import dev.mcver.core.InstallJob;
import dev.mcver.core.Launcher;
import dev.mcver.core.LoaderInstaller;
import dev.mcver.core.LoaderSupport;
import dev.mcver.core.Sources;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 无界面安装探针：跑的是**生产代码**（InstallJob），只是把结果打到 stdout。
 *
 * <pre>
 * java -cp out dev.mcver.probe.ProbeInstall &lt;gameDir&gt; &lt;mc&gt; &lt;KIND&gt; &lt;loaderVer|-&gt; \
 *      &lt;SHADER|NONE&gt; &lt;shaderVer|-&gt; &lt;target|-&gt; &lt;iso 0|1&gt;
 * </pre>
 *
 * 退出码：0 = 装完；1 = 失败；3 = 规则表判定不支持（不联网，直接跳）。
 */
public final class ProbeInstall {

    public static void main(String[] args) throws Exception {
        if (args.length < 8) {
            System.err.println("用法: ProbeInstall <gameDir> <mc> <KIND> <loaderVer|-> <SHADER|NONE> <shaderVer|-> <target|-> <iso 0|1>");
            System.exit(2);
        }
        Path gameDir = Paths.get(args[0]).toAbsolutePath().normalize();
        String mc = args[1];
        LoaderInstaller.Kind kind = LoaderInstaller.Kind.valueOf(args[2].toUpperCase());
        String loaderVer = dash(args[3]);
        LoaderInstaller.Shader shader = LoaderInstaller.Shader.valueOf(args[4].toUpperCase());
        String shaderVer = dash(args[5]);
        String target = dash(args[6]);
        boolean iso = "1".equals(args[7]);

        // 规则表先行：不支持就别浪费网络请求
        LoaderSupport.Verdict v = LoaderSupport.check(kind, mc);
        System.out.println("[probe] 规则表 " + kind + " x " + mc + " => " + v);
        if (v == LoaderSupport.Verdict.NO) {
            System.out.println("[probe] SKIP: " + LoaderSupport.reason(kind, mc));
            System.exit(3);
        }

        Sources.setActive(Sources.BMCLAPI.label());
        System.out.println("[probe] 下载源: " + Sources.active().label() + "  gameDir=" + gameDir);

        InstallJob job = new InstallJob(gameDir, mc, kind, loaderVer, shader, shaderVer, iso, target);
        final Object lock = new Object();
        final boolean[] finished = {false};

        job.start(j -> {
            synchronized (lock) {
                finished[0] = true;
                lock.notifyAll();
            }
        });

        // 边跑边把日志刷出来（外部脚本靠这些行判进度）
        int printed = 0;
        long deadline = System.currentTimeMillis() + 60L * 60 * 1000;
        while (true) {
            Thread.sleep(2000);
            var lines = job.logSnapshot(400);
            for (; printed < lines.size(); printed++) {
                System.out.println("[job] " + lines.get(printed));
            }
            if (System.currentTimeMillis() > deadline) {
                System.out.println("[probe] TIMEOUT: 安装超过 60 分钟");
                System.exit(1);
            }
            synchronized (lock) {
                if (finished[0]) break;
            }
        }

        var lines = job.logSnapshot(400);
        for (; printed < lines.size(); printed++) System.out.println("[job] " + lines.get(printed));

        System.out.println("[probe] state=" + job.state);
        System.out.println("[probe] resultVersionId=" + job.resultVersionId);
        if (job.state == InstallJob.State.DONE) {
            String id = job.resultVersionId;
            System.out.println("[probe] complete=" + (id != null && Launcher.isComplete(gameDir, id)));
            System.out.println("[probe] OK " + id);
            System.exit(0);
        }
        System.out.println("[probe] ERROR " + job.error);
        System.exit(1);
    }

    private static String dash(String s) {
        return (s == null || s.isBlank() || "-".equals(s)) ? null : s;
    }

    private ProbeInstall() { }
}
