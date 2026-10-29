package dev.mcver.probe;

import dev.mcver.core.Launcher;
import dev.mcver.core.Sources;

import java.io.IOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 界面点按探针：真起游戏 → 自动打开某页 → 按日志里的布局算坐标 → <b>真点一下</b> → 看日志变化。
 *
 * <p><b>为什么需要它</b>：「点下载页的分类页签后列表没变，得再按刷新」这种 bug，
 * 只跑「界面能画」（children=N / 面板像素）是看不出来的 —— 那证明的是渲染，不是交互。
 * 本探针走用户真实路径：真鼠标点到页签上，只认 <b>点击之后</b> 日志里新出现的
 * {@code mcver: 分类=X 共=N 个} 那一行。
 *
 * <p>坐标怎么来的：mod 在列表面板重建时打一行
 * {@code mcver: layout tabs@x,y,cw,ch,n search@x,y,w,h}（GUI 坐标），
 * 加上同一行的 {@code gui=WxH}，用 {@code GetClientRect} + {@code ClientToScreen}
 * 换算成屏幕坐标。验收脚本因此不用复制一份布局算式。
 *
 * <p>前置：游戏进程要拿到 {@code MCVER_OPEN_GUI=1}（进标题界面自动开主界面）
 * 和 {@code MCVER_TAB=<页名>}。一般用 {@code scripts/verify-download-tab.mjs} 调。
 *
 * <p>用法：
 * <pre>
 * ProbeGuiClick &lt;gameDir&gt; &lt;versionId&gt; &lt;秒&gt; &lt;页名&gt; &lt;页签序号&gt; &lt;期望分类名&gt;
 * </pre>
 */
public final class ProbeGuiClick {

    /** mcver: layout tabs@102,60,70,15,4 search@102,31,291,16 */
    private static final Pattern LAYOUT =
            Pattern.compile("mcver: layout tabs@(\\d+),(\\d+),(\\d+),(\\d+),(\\d+) search@(\\d+),(\\d+),(\\d+),(\\d+)");
    /** mcver: 12:34:56 screen=MAIN tab=下载 gui=427x240 panel=... */
    private static final Pattern GUI = Pattern.compile("gui=(\\d+)x(\\d+)");
    private static final Pattern CATLOG = Pattern.compile("mcver: 分类=(\\S+) 搜索=\\S* 共=(\\d+) 个");

    private ProbeGuiClick() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 6) {
            System.err.println("用法: ProbeGuiClick <gameDir> <versionId> <秒> <页名> <页签序号> <期望分类名>");
            System.exit(2);
        }
        Path gameDir = Paths.get(args[0]).toAbsolutePath().normalize();
        String id = args[1];
        int timeout = Integer.parseInt(args[2]);
        String tabName = args[3];
        int tabIndex = Integer.parseInt(args[4]);
        String wantCat = args[5];
        // 调试/通用：MCVER_CLICK_AT=gx,gy 直接给 GUI 坐标，不按页签算
        String atSpec = System.getenv("MCVER_CLICK_AT") == null ? "" : System.getenv("MCVER_CLICK_AT").trim();
        int gxRaw = -1;
        int gyRaw = -1;

        Sources.setActive(Sources.BMCLAPI.label());

        Launcher.Plan p = Launcher.plan(gameDir, id);
        System.out.println("[probe] mainClass=" + p.mainClass + "  cp=" + p.classpath.size()
                + "  missing=" + p.missing.size());
        if (!p.ready()) {
            System.out.println("[probe] ERROR plan 不完整");
            System.exit(1);
        }

        Launcher.Running r = Launcher.launch(p, "Probe", "00000000000000000000000000000000", "0", 2048, null);
        System.out.println("[probe] log=" + r.logFile);

        long deadline = System.currentTimeMillis() + timeout * 1000L;

        if (!waitFor(r, "Setting user", deadline)) {
            System.out.println("[probe] ERROR 没到 Setting user");
            tail(r);
            kill(r);
            System.exit(1);
        }

        // 等界面自己开出来（MCVER_OPEN_GUI）并落到目标页
        String screenMark = "screen=MAIN tab=" + tabName;
        if (!waitFor(r, screenMark, deadline)) {
            System.out.println("[probe] ERROR 没看到 " + screenMark);
            tail(r);
            kill(r);
            System.exit(1);
        }
        System.out.println("[probe] 界面已到「" + tabName + "」页");

        // 布局行是列表面板重建时打的；界面刚开时可能还没轮到，多等一会
        String layoutLine = null;
        int guiW = 0;
        int guiH = 0;
        while (System.currentTimeMillis() < deadline) {
            for (String s : safeRead(r.logFile)) {
                Matcher g = GUI.matcher(s);
                if (g.find()) { guiW = Integer.parseInt(g.group(1)); guiH = Integer.parseInt(g.group(2)); }
                if (LAYOUT.matcher(s).find()) layoutLine = s;
            }
            if (layoutLine != null && guiW > 0) break;
            sleep(800);
        }
        if (layoutLine == null || guiW <= 0) {
            System.out.println("[probe] ERROR 日志里没有布局信息（layout/gui）");
            tail(r);
            kill(r);
            System.exit(1);
        }
        Matcher m = LAYOUT.matcher(layoutLine);
        m.find();
        int tabX = Integer.parseInt(m.group(1));
        int tabY = Integer.parseInt(m.group(2));
        int cw = Integer.parseInt(m.group(3));
        int ch = Integer.parseInt(m.group(4));
        int n = Integer.parseInt(m.group(5));
        System.out.println("[probe] gui=" + guiW + "x" + guiH + " tabs@" + tabX + "," + tabY
                + " cw=" + cw + " ch=" + ch + " n=" + n);

        if (tabIndex < 0 && !atSpec.isEmpty()) {
            // 调试用：直接给 GUI 坐标（MCVER_CLICK_AT=34,50），不走页签算式
            String[] xy = atSpec.split(",");
            gxRaw = Integer.parseInt(xy[0].trim());
            gyRaw = Integer.parseInt(xy[1].trim());
            System.out.println("[probe] 使用显式坐标 " + gxRaw + "," + gyRaw);
        } else if (tabIndex < 0 || tabIndex >= n) {
            System.out.println("[probe] ERROR 页签序号 " + tabIndex + " 超出 0.." + (n - 1));
            kill(r);
            System.exit(1);
        }

        // 页签横向排列：x + i * (cw + 3)，宽 cw，高 ch。取中心点。
        int gx = gxRaw >= 0 ? gxRaw : tabX + tabIndex * (cw + 3) + cw / 2;
        int gy = gyRaw >= 0 ? gyRaw : tabY + ch / 2;

        long hwnd = findMinecraftWindow();
        System.out.println("[probe] hwnd=" + hwnd);
        if (hwnd == 0L) {
            System.out.println("[probe] ERROR 找不到游戏窗口");
            kill(r);
            System.exit(1);
        }

        int before = countCatLog(r.logFile);
        // 清单还没拉完时点没用（列表里连按钮都没几个），而且加载完成那次 recompute
        // 会冒充成「点的结果」—— 先等到至少一条分类日志再点。
        while (before == 0 && System.currentTimeMillis() < deadline && r.process.isAlive()) {
            sleep(1000);
            before = countCatLog(r.logFile);
        }
        if (before == 0) {
            System.out.println("[probe] ERROR 版本清单一直没加载出来");
            tail(r);
            kill(r);
            System.exit(1);
        }
        System.out.println("[probe] 点击前 分类日志条数=" + before + "，准备点 (" + gx + "," + gy + ") gui");

        click(hwnd, gx, gy, guiW);

        // 只认「点击之后新冒出来」的那一行 —— 之前的日志早就有了，不能当证据
        String hit = null;
        long clickAt = System.currentTimeMillis();
        while (System.currentTimeMillis() < deadline) {
            sleep(1000);
            List<String> lines = safeRead(r.logFile);
            int seen = 0;
            for (String s : lines) {
                Matcher c = CATLOG.matcher(s);
                if (!c.find()) continue;
                seen++;
                if (seen > before) {
                    hit = c.group(1) + "|" + c.group(2);
                    break;
                }
            }
            if (hit != null) break;
            if (!r.process.isAlive()) break;
        }

        boolean ok = false;
        if (hit == null) {
            System.out.println("[probe] FAIL 点完页签没有新的分类日志（列表没自动刷新）");
        } else {
            String[] parts = hit.split("\\|");
            int shown = Integer.parseInt(parts[1]);
            System.out.println("[probe] 点击后：分类=" + parts[0] + " 共=" + shown + " 个（耗时 "
                    + (System.currentTimeMillis() - clickAt) + "ms）");
            if (!wantCat.equals(parts[0])) {
                System.out.println("[probe] FAIL 分类没切到「" + wantCat + "」");
            } else if (shown <= 0) {
                System.out.println("[probe] FAIL 切过去了但列表是空的");
            } else {
                ok = true;
            }
        }

        if (ok) {
            System.out.println("[probe] PASS 点页签后列表自动刷新");
        } else {
            System.out.println("[probe] ---- 最近日志 ----");
            tail(r);
        }
        kill(r);
        System.exit(ok ? 0 : 1);
    }

    // ---------------- 输入 ----------------

    /**
     * 真点一下。
     *
     * <p>用 {@code SetCursorPos} + {@code mouse_event} 而不是 {@code PostMessage(WM_LBUTTONDOWN)} ——
     * 1.20.0 上投递的消息 GLFW 根本不认（键盘那边已经踩过，鼠标同理）。
     *
     * <p>GUI 坐标 → 屏幕坐标：{@code scale = 客户区宽 / guiW}，原点用 {@code ClientToScreen(0,0)}。
     * 不用 {@code GetWindowRect}：那个含标题栏和边框，差十来个像素就点空了。
     */
    private static void click(long hwnd, int gx, int gy, int guiW) {
        StringBuilder sh = new StringBuilder();
        sh.append("param([int]$Hwnd, [int]$Gx, [int]$Gy, [int]$GuiW)\n");
        sh.append("Add-Type -TypeDefinition @\"\n");
        sh.append("using System;\nusing System.Runtime.InteropServices;\n");
        sh.append("public class C6 {\n");
        sh.append("    [DllImport(\"user32.dll\")] public static extern void mouse_event(uint f, int dx, int dy, uint d, UIntPtr e);\n");
        sh.append("    [DllImport(\"user32.dll\")] public static extern bool SetCursorPos(int x, int y);\n");
        sh.append("    [DllImport(\"user32.dll\")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);\n");
        sh.append("    [DllImport(\"user32.dll\")] public static extern bool GetClientRect(IntPtr h, out RECT r);\n");
        sh.append("    [DllImport(\"user32.dll\")] public static extern bool SetForegroundWindow(IntPtr h);\n");
        sh.append("    [DllImport(\"user32.dll\")] public static extern bool ShowWindow(IntPtr h, int c);\n");
        sh.append("    [DllImport(\"user32.dll\")] public static extern IntPtr GetForegroundWindow();\n");
        sh.append("    [DllImport(\"user32.dll\")] public static extern bool BringWindowToTop(IntPtr h);\n");
        sh.append("    [DllImport(\"user32.dll\")] public static extern uint GetWindowThreadProcessId(IntPtr h, IntPtr pid);\n");
        sh.append("    [DllImport(\"user32.dll\")] public static extern bool AttachThreadInput(uint a, uint b, bool attach);\n");
        sh.append("    [DllImport(\"kernel32.dll\")] public static extern uint GetCurrentThreadId();\n");
        sh.append("    public struct POINT { public int X, Y; }\n");
        sh.append("    public struct RECT { public int L, T, R, B; }\n");
        sh.append("}\n");
        sh.append("\"@\n");
        sh.append("$h = [IntPtr]$Hwnd\n");
        sh.append("[void][C6]::ShowWindow($h, 9)\n");
        sh.append("# SetForegroundWindow 受前台锁限制会静默失败 — 用 AttachThreadInput 把输入队列接上再抢一次\n");
        sh.append("if ([C6]::GetForegroundWindow() -ne $h) {\n");
        sh.append("  $fg = [C6]::GetForegroundWindow()\n");
        sh.append("  $tidFg = [C6]::GetWindowThreadProcessId($fg, [IntPtr]::Zero)\n");
        sh.append("  $tidMe = [C6]::GetCurrentThreadId()\n");
        sh.append("  [void][C6]::AttachThreadInput($tidMe, $tidFg, $true)\n");
        sh.append("  [void][C6]::BringWindowToTop($h)\n");
        sh.append("  [void][C6]::SetForegroundWindow($h)\n");
        sh.append("  [void][C6]::AttachThreadInput($tidMe, $tidFg, $false)\n");
        sh.append("}\n");
        sh.append("Start-Sleep -Milliseconds 400\n");
        sh.append("Write-Output (\"foreground=\" + [C6]::GetForegroundWindow() + \" target=\" + $h)\n");
        sh.append("$rc = New-Object C6+RECT\n");
        sh.append("[void][C6]::GetClientRect($h, [ref]$rc)\n");
        sh.append("$cw = $rc.R - $rc.L; $chh = $rc.B - $rc.T\n");
        sh.append("$pt = New-Object C6+POINT\n");
        sh.append("$pt.X = 0; $pt.Y = 0\n");
        sh.append("[void][C6]::ClientToScreen($h, [ref]$pt)\n");
        sh.append("$scale = $cw / [double]$GuiW\n");
        sh.append("$sx = [int]($pt.X + $Gx * $scale)\n");
        sh.append("$sy = [int]($pt.Y + $Gy * $scale)\n");
        sh.append("Write-Output (\"client={0}x{1} origin={2},{3} scale={4} click={5},{6}\" -f $cw,$chh,$pt.X,$pt.Y,$scale,$sx,$sy)\n");
        sh.append("[void][C6]::SetCursorPos($sx, $sy)\n");
        sh.append("Start-Sleep -Milliseconds 250\n");
        sh.append("# 点两次：窗口刚激活时第一次点击可能被系统吃掉（激活用）\n");
        sh.append("for ($i = 0; $i -lt 2; $i++) {\n");
        sh.append("  [C6]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)\n");
        sh.append("  Start-Sleep -Milliseconds 90\n");
        sh.append("  [C6]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)\n");
        sh.append("  Start-Sleep -Milliseconds 350\n");
        sh.append("}\n");
        sh.append("Write-Output 'clicked'\n");

        Path script = null;
        try {
            script = Files.createTempFile("mcver-click-", ".ps1");
            Files.write(script, ("\uFEFF" + sh).getBytes(StandardCharsets.UTF_8));
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-File", script.toAbsolutePath().toString(),
                    "-Hwnd", String.valueOf(hwnd), "-Gx", String.valueOf(gx),
                    "-Gy", String.valueOf(gy), "-GuiW", String.valueOf(guiW));
            pb.redirectErrorStream(true);
            Process pr = pb.start();
            String out = new String(pr.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            pr.waitFor();
            if (!out.isEmpty()) System.out.println("[probe] ps: " + out);
        } catch (Throwable t) {
            System.out.println("[probe] (点击失败: " + t.getMessage() + ")");
        } finally {
            try { if (script != null) Files.deleteIfExists(script); } catch (IOException ignored) { }
        }
    }

    private static long findMinecraftWindow() {
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    "@(Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 } "
                            + "| Select-Object -First 1).MainWindowHandle");
            pb.redirectErrorStream(true);
            Process pr = pb.start();
            String out = new String(pr.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            pr.waitFor();
            if (!out.isEmpty()) return Long.parseLong(out.split("\\s+")[0]);
        } catch (Throwable t) {
            System.out.println("[probe] (找窗口失败: " + t.getMessage() + ")");
        }
        return 0L;
    }

    // ---------------- 杂项 ----------------

    private static int countCatLog(Path log) {
        int c = 0;
        for (String s : safeRead(log)) if (CATLOG.matcher(s).find()) c++;
        return c;
    }

    private static boolean waitFor(Launcher.Running r, String marker, long deadline) throws Exception {
        while (System.currentTimeMillis() < deadline) {
            sleep(1200);
            for (String s : safeRead(r.logFile)) if (s.contains(marker)) return true;
            if (!r.process.isAlive()) {
                sleep(1000);
                for (String s : safeRead(r.logFile)) if (s.contains(marker)) return true;
                return false;
            }
        }
        return false;
    }

    /** 日志按平台编码写（中文 Windows = GBK）：先严格 UTF-8，失败退 GBK。 */
    private static List<String> safeRead(Path p) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(p);
        } catch (IOException e) {
            return new ArrayList<>();
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (Exception ignored) {
            try {
                text = java.nio.charset.Charset.forName("GBK").decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            } catch (Exception e2) {
                text = new String(bytes, StandardCharsets.ISO_8859_1);
            }
        }
        List<String> out = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) out.add(line);
        return out;
    }

    private static void tail(Launcher.Running r) {
        List<String> lines = safeRead(r.logFile);
        int from = Math.max(0, lines.size() - 20);
        for (int i = from; i < lines.size(); i++) System.out.println("[log] " + lines.get(i));
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }

    private static void kill(Launcher.Running r) {
        try {
            if (r.process != null && r.process.isAlive()) {
                r.process.descendants().forEach(ProcessHandle::destroyForcibly);
                r.process.destroyForcibly();
            }
        } catch (Throwable ignored) { }
    }
}
