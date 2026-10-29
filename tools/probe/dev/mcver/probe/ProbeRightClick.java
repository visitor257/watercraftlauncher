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

/**
 * 右键实测探针：真起游戏 → 自动进存档 → 聊天栏给物品 → 真右击 → 看日志。
 *
 * <p><b>为什么需要它</b>：之前的「像素验收」走的是 {@code MCVER_OPEN_GUI=1} 自检钩子
 * （进标题界面自动开界面），<b>压根没经过右键那条路</b>。结果 39 个 jar 全绿，
 * 用户一右键却发现没反应 —— 判据覆盖错了路径。
 *
 * <p>本探针走的是用户真实路径：{@code --quickPlaySingleplayer} 进世界 → 用聊天命令拿物品 →
 * 发一个真右键（驱动级 {@code mouse_event}）→ 只有 {@code McVerClient} 的右键分支真的命中，
 * 日志里才会出现「mcver: 右键打开启动器」。
 *
 * <p>用法：
 * <pre>
 * ProbeRightClick &lt;gameDir&gt; &lt;versionId&gt; &lt;秒&gt; &lt;存档名&gt;
 * </pre>
 */
public final class ProbeRightClick {

    private ProbeRightClick() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("用法: ProbeRightClick <gameDir> <versionId> <秒> <存档名>");
            System.exit(2);
        }
        Path gameDir = Paths.get(args[0]).toAbsolutePath().normalize();
        String id = args[1];
        int timeout = Integer.parseInt(args[2]);
        String world = args[3];

        Sources.setActive(Sources.BMCLAPI.label());

        Launcher.Plan p = Launcher.plan(gameDir, id);
        System.out.println("[probe] mainClass=" + p.mainClass + "  cp=" + p.classpath.size()
                + "  missing=" + p.missing.size());
        if (!p.ready()) {
            System.out.println("[probe] ERROR plan 不完整");
            System.exit(1);
        }

        List<String> extra = new ArrayList<>();
        extra.add("--quickPlaySingleplayer");
        extra.add(world);

        Launcher.Running r = Launcher.launch(p, "Probe", "00000000000000000000000000000000", "0", 2048, extra);
        System.out.println("[probe] log=" + r.logFile);

        long deadline = System.currentTimeMillis() + timeout * 1000L;

        // ---- 1. 等进世界（Setting user 只是启动；进世界后才有 player）
        if (!waitFor(r, "Setting user", deadline)) {
            System.out.println("[probe] ERROR 没到 Setting user");
            tail(r);
            kill(r);
            System.exit(1);
        }
        System.out.println("[probe] 已到 Setting user，等进世界 ...");

        // 进世界没有稳定日志标记（各版本不同），给固定时间让区块加载完
        sleep(25_000);
        if (!r.process.isAlive()) {
            System.out.println("[probe] ERROR 进世界前进程退出");
            tail(r);
            System.exit(1);
        }

        long hwnd = findMinecraftWindow();
        System.out.println("[probe] hwnd=" + hwnd);
        if (hwnd == 0L) {
            System.out.println("[probe] ERROR 找不到游戏窗口");
            tail(r);
            kill(r);
            System.exit(1);
        }

        // ---- 2+3. 一次性在同一条 PowerShell 里发完整输入序列
        // T 开聊天 → 输入 /give → 回车 → 真右键。
        // 不用「每个按键起一个 powershell」那套：每次启动 shells 要 300ms+，
        // 时序完全不可控，而且右键必须发生在物品已经进背包之后。
        sendInput(hwnd, "/give @s mcver:download_tool");
        System.out.println("[probe] 已发送 /give + 右键");

        // ---- 4. 等日志证据
        boolean fired = waitFor(r, "右键打开启动器", deadline);
        if (!fired) {
            System.out.println("[probe] FAIL 右键没触发 McVerClient 分支");
            System.out.println("[probe] ---- 最近日志 ----");
            tail(r);
            kill(r);
            System.exit(1);
        }
        System.out.println("[probe] PASS 右键真的打开了启动器");
        sleep(2500);

        // ---- 5. 抓图（有 PrintWindow 就抓，没有也不影响结论）
        try {
            Path png = Paths.get("logs", "build", "rightclick-" + id + ".png");
            Files.createDirectories(png.getParent());
            shoot(hwnd, png);
            System.out.println("[probe] png=" + png.toAbsolutePath());
        } catch (Throwable t) {
            System.out.println("[probe] WARN 抓图失败: " + t.getMessage());
        }

        kill(r);
        System.exit(0);
    }

    // ---------------- Windows 调用 ----------------

    /**
     * 一条 PowerShell 里发完整序列：T(WORD) → 逐字符 → ENTER → 等物品进背包 → 右键。
     *
     * <p><b>为什么用驱动级 {@code keybd_event}/{@code mouse_event} 而不是 {@code PostMessage}：</b>
     * 1.21.x 的 GLFW 会处理投递到窗口的 {@code WM_KEYDOWN}，但 1.20.0 上实测<b>完全不生效</b>
     * （连 Esc 都不会暂停）—— 这就是旧写法在旧世代会假 FAIL 的源头。
     * 驱动级事件才是「用户真按了」的等价物；代价是窗口必须真的在前台，
     * 所以先 {@code ShowWindow(SW_RESTORE)} + {@code SetForegroundWindow}，并核对
     * {@code GetForegroundWindow()} 到底是不是它。
     *
     * <p>字符靠 {@code VkKeyScanW} 反查虚拟键 + 是否需要 Shift（`/` `@` `_` 这些都会被正确处理）。
     * 落一个临时 {@code .ps1} 用 {@code -File} 跑：引号层次少，不会撞 {@code -Command} 的转义地狱。
     */
    private static void sendInput(long hwnd, String text) {
        StringBuilder sh = new StringBuilder();
        sh.append("param([int]$Hwnd, [string]$Text)\n");
        sh.append("Add-Type -TypeDefinition @\"\n");
        sh.append("using System;\nusing System.Runtime.InteropServices;\n");
        sh.append("public class K5 {\n");
        sh.append("    [DllImport(\"user32.dll\")] public static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra);\n");
        sh.append("    [DllImport(\"user32.dll\")] public static extern void mouse_event(uint flags, int dx, int dy, uint data, UIntPtr extra);\n");
        sh.append("    [DllImport(\"user32.dll\")] public static extern bool SetForegroundWindow(IntPtr h);\n");
        sh.append("    [DllImport(\"user32.dll\")] public static extern bool ShowWindow(IntPtr h, int c);\n");
        sh.append("    [DllImport(\"user32.dll\")] public static extern IntPtr GetForegroundWindow();\n");
        sh.append("    [DllImport(\"user32.dll\")] public static extern short VkKeyScanW(char c);\n");
        sh.append("}\n");
        sh.append("\"@\n");
        sh.append("$h = [IntPtr]$Hwnd\n");
        sh.append("[void][K5]::ShowWindow($h, 9)\n");
        sh.append("[void][K5]::SetForegroundWindow($h)\n");
        sh.append("Start-Sleep -Milliseconds 700\n");
        sh.append("$fg = [K5]::GetForegroundWindow()\n");
        sh.append("Write-Output (\"foreground=$fg target=$h\")\n");
        sh.append("if ($fg -ne $h) { Write-Output 'WARN 窗口没拿到前台，输入可能被系统丢掉' }\n");
        sh.append("function Tap([int]$vk) {\n");
        sh.append("  [K5]::keybd_event([byte]$vk, 0, 0, [UIntPtr]::Zero)\n");
        sh.append("  Start-Sleep -Milliseconds 45\n");
        sh.append("  [K5]::keybd_event([byte]$vk, 0, 2, [UIntPtr]::Zero)\n");
        sh.append("  Start-Sleep -Milliseconds 45\n");
        sh.append("}\n");
        sh.append("function TypeChar([char]$c) {\n");
        sh.append("  $v = [K5]::VkKeyScanW($c)\n");
        sh.append("  $vk = $v -band 0xFF\n");
        sh.append("  if ($vk -eq 0xFF -or $vk -eq -1) { Write-Output (\"skip char: $c\"); return }\n");
        sh.append("  $shift = (($v -shr 8) -band 1) -eq 1\n");
        sh.append("  if ($shift) { [K5]::keybd_event(0x10, 0, 0, [UIntPtr]::Zero) }\n");
        sh.append("  Tap $vk\n");
        sh.append("  if ($shift) { [K5]::keybd_event(0x10, 0, 2, [UIntPtr]::Zero) }\n");
        sh.append("}\n");
        sh.append("Tap 0x54                       # T 开聊天\n");
        sh.append("Start-Sleep -Milliseconds 1200\n");
        sh.append("foreach ($c in $Text.ToCharArray()) { TypeChar $c }\n");
        sh.append("Start-Sleep -Milliseconds 400\n");
        sh.append("Tap 0x0D                       # Enter 执行 /give\n");
        sh.append("Start-Sleep -Milliseconds 2000\n");
        sh.append("# 右键：MOUSEEVENTF_RIGHTDOWN(0x0008) / RIGHTUP(0x0010)\n");
        sh.append("[K5]::mouse_event(0x0008, 0, 0, 0, [UIntPtr]::Zero)\n");
        sh.append("Start-Sleep -Milliseconds 120\n");
        sh.append("[K5]::mouse_event(0x0010, 0, 0, 0, [UIntPtr]::Zero)\n");
        sh.append("Write-Output 'input sent'\n");

        Path script = null;
        try {
            script = Files.createTempFile("mcver-keys-", ".ps1");
            // 必须带 UTF-8 BOM：Windows PowerShell 5.1 读无 BOM 的 .ps1 会按 ANSI(GBK) 解，
            // 里面的中文提示就会把字符串引号搞崩（ParserError: TerminatorExpectedAtEndOfString）。
            Files.write(script, ("\uFEFF" + sh).getBytes(StandardCharsets.UTF_8));
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-File", script.toAbsolutePath().toString(), "-Hwnd", String.valueOf(hwnd), "-Text", text);
            pb.redirectErrorStream(true);
            Process pr = pb.start();
            String out = new String(pr.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            pr.waitFor();
            if (!out.isEmpty()) System.out.println("[probe] ps: " + out);
        } catch (Throwable t) {
            System.out.println("[probe] (发送输入失败: " + t.getMessage() + ")");
        } finally {
            try { if (script != null) Files.deleteIfExists(script); } catch (IOException ignored) { }
        }
    }

    private static long findMinecraftWindow() {
        // 不用 JNA：直接用 PowerShell 侧传进来的 hwnd 更稳，但探针是独立进程，
        // 这里用 ProcessHandle + user32 的 FindWindowW 反射太重。
        // 折中：遍历当前会话里标题为 Minecraft 的 java 进程主窗口。
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

    /**
     * 抓真窗口。
     *
     * <p>不用 {@code Add-Type -MemberDefinition}：带 struct 的参数写在 -MemberDefinition 里
     * 编译不过（{@code 找不到类型或命名空间 user32}），verify-gui.ps1 用的是
     * {@code Add-Type -TypeDefinition} 整类定义才成。这里照它来。
     *
     * <p>也不把这一大坨塞进 {@code -Command} 单行：引号层次太多、还卡路径转义。
     * 落一个临时 .ps1 用 {@code -File} 跑，最稳。
     */
    private static void shoot(long hwnd, Path png) throws Exception {
        // 先删旧图：否则「文件存在且 >1KB」的断言会被上一轮留下的图骗过（已经踩过）
        try { Files.deleteIfExists(png); } catch (IOException ignored) { }
        Path script = Files.createTempFile("mcver-shot-", ".ps1");
        String body = String.join("\n",
                "Add-Type -AssemblyName System.Drawing",
                "if (-not ('Shot' -as [type])) {",
                "    Add-Type -TypeDefinition @'",
                "using System;",
                "using System.Runtime.InteropServices;",
                "public class Shot {",
                "    [DllImport(\"user32.dll\")] public static extern bool PrintWindow(IntPtr h, IntPtr dc, uint f);",
                "    [DllImport(\"user32.dll\")] public static extern bool GetWindowRect(IntPtr h, out R r);",
                "    public struct R { public int L, T, Ri, B; }",
                "}",
                "'@",
                "}",
                "$h = [IntPtr]" + hwnd,
                "$r = New-Object Shot+R",
                "[void][Shot]::GetWindowRect($h, [ref]$r)",
                "$w = $r.Ri - $r.L; $hh = $r.B - $r.T",
                "if ($w -le 0 -or $hh -le 0) { Write-Output 'badrect'; exit 1 }",
                "$bmp = New-Object System.Drawing.Bitmap $w, $hh",
                "$g = [System.Drawing.Graphics]::FromImage($bmp)",
                "$dc = $g.GetHdc()",
                "$ok = [Shot]::PrintWindow($h, $dc, 2)",
                "$g.ReleaseHdc($dc); $g.Dispose()",
                "$bmp.Save('" + png.toAbsolutePath().toString().replace('\\', '/') + "', [System.Drawing.Imaging.ImageFormat]::Png)",
                "$bmp.Dispose()",
                "Write-Output \"pw=$ok w=$w h=$hh\"");
        Files.write(script, body.getBytes(StandardCharsets.UTF_8));

        ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-File", script.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process pr = pb.start();
        String out = new String(pr.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        pr.waitFor();
        try { Files.deleteIfExists(script); } catch (IOException ignored) { }
        if (!out.isEmpty()) System.out.println("[probe] 抓图: " + out);
        if (!Files.isRegularFile(png) || Files.size(png) < 1000) {
            throw new IOException("抓图没落盘");
        }
    }

    // ---------------- 杂项 ----------------

    private static boolean waitFor(Launcher.Running r, String marker, long deadline) throws Exception {
        while (System.currentTimeMillis() < deadline) {
            sleep(1500);
            for (String s : safeRead(r.logFile)) {
                if (s.contains(marker)) return true;
            }
            if (!r.process.isAlive()) {
                sleep(1200);
                for (String s : safeRead(r.logFile)) {
                    if (s.contains(marker)) return true;
                }
                return false;
            }
        }
        return false;
    }

    /**
     * 读日志：游戏（尤其是 Forge / 旧版）用平台默认编码写日志，中文 Windows 上是 GBK。
     * 单纯 {@code readAllLines(UTF_8)} 不会抛异常，只会把中文换成 U+FFFD，
     * 于是按中文标记 grep 永远匹配不上 —— 踩过：GUI 明明开了，探针报 FAIL。
     * 所以先按 UTF-8 严格解码，解不了再退 GBK(936)。
     */
    private static List<String> safeRead(Path p) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(p);
        } catch (IOException e) {
            return new ArrayList<>();
        }
        String text = null;
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
        int from = Math.max(0, lines.size() - 25);
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
