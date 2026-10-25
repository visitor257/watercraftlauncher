package dev.mcver.runner;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * 在独立 JVM 里跑 OptiFine 安装器（optifine.Installer.doInstall）。
 *
 * <p>为什么要单开一个类、单独编成 Java 8 字节码：OptiFine 1.12.2 那代安装器只能跑在
 * Java 8 上，而启动器本体的类是按 Java 21 编的（class file 65），拿 Java 8 去跑会
 * UnsupportedClassVersionError。所以这个类**必须**用 --release 8 编译，
 * 打包进 mod jar 当资源（optifine-runner.jar），运行时解出来用对应 Java 执行。
 *
 * <p>注意：这个文件不能 import 项目里的任何类，也不能用 var / record / switch 表达式。
 */
public final class OptifineRunner {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: OptifineRunner <installer.jar> <gameDir>");
            System.exit(2);
        }
        File jar = new File(args[0]);
        File gameDir = new File(args[1]);
        System.setProperty("user.dir", gameDir.getAbsolutePath());

        URLClassLoader cl = new URLClassLoader(new URL[]{jar.toURI().toURL()},
                OptifineRunner.class.getClassLoader());
        try {
            Class<?> c = cl.loadClass("optifine.Installer");
            Method m = c.getMethod("doInstall", File.class);
            m.invoke(null, gameDir);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            System.err.println("OptiFine installer failed: " + cause);
            cause.printStackTrace();
            System.exit(1);
        } finally {
            try { cl.close(); } catch (Exception ignored) { }
        }
        System.exit(0);
    }

    private OptifineRunner() { }
}