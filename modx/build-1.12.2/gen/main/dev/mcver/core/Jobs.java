package dev.mcver.core;

/**
 * 后台任务登记处。
 *
 * 关闭界面不会停掉安装 —— 任务线程自己跑，这里只留一个引用，
 * 界面再打开时能接回去看进度。
 */
public final class Jobs {

    private static volatile InstallJob install;

    public static InstallJob install() {
        return install;
    }

    public static boolean running() {
        InstallJob j = install;
        return j != null && j.state == InstallJob.State.RUNNING;
    }

    /** 有过任务（不管成没成），用来决定要不要显示进度页。 */
    public static boolean hasJob() {
        return install != null;
    }

    public static InstallJob submit(InstallJob job) {
        install = job;
        job.start(j -> { });
        return job;
    }

    /** 标题栏/导航上的一行状态。 */
    public static String brief() {
        InstallJob j = install;
        if (j == null) return "";
        switch (j.state) {
            case RUNNING: {
                String what = j.targetName == null ? j.versionId : j.targetName;
                if (j.total > 0) {
                    return "后台 " + what + " " + (int) (j.fraction() * 100) + "%";
                }
                return "后台 " + what + " " + j.stage;
            }
            case DONE: return "完成 " + j.resultVersionId;
            case FAILED: return "失败 " + j.versionId;
            case CANCELLED: return "已取消 " + j.versionId;
            default: return "";
        }
    }

    private Jobs() { }
}
