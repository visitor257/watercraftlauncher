package net.minecraft;

/**
 * 1.12.2 垫片：1.14+ 的 {@code net.minecraft.Util}。
 *
 * <p>1.12.2 只有 {@code net.minecraft.util.Util}（管 OS 枚举），<b>没有</b>
 * {@code net.minecraft.Util}，也没有 {@code openUri} —— 所以这个包名是安全的。
 * 页面里用到它的地方只有一处：用浏览器打开微软登录链接。
 */
public final class Util {

    private Util() { }

    public static final Os PLATFORM = new Os();

    public static Os getPlatform() {
        return PLATFORM;
    }

    public static final class Os {

        /** 打开链接。1.12.2 没有官方 API，用 AWT Desktop 兜底。 */
        public void openUri(java.net.URI uri) {
            try {
                if (java.awt.Desktop.isDesktopSupported()
                        && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                    java.awt.Desktop.getDesktop().browse(uri);
                    return;
                }
            } catch (Throwable ignored) { }
            try {
                String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
                if (os.contains("win")) {
                    new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", uri.toString()).start();
                } else if (os.contains("mac")) {
                    new ProcessBuilder("open", uri.toString()).start();
                } else {
                    new ProcessBuilder("xdg-open", uri.toString()).start();
                }
            } catch (Throwable ignored) { }
        }
    }
}
