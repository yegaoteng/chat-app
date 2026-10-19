package xfan.chat.app;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 后台守护的兜底唤醒（JobScheduler）。
 *
 * 为什么需要它：
 *   MonitorService 跑在应用进程里。进程一旦被系统或用户杀掉，
 *   Service 就没了，而 Application.onCreate 只在进程重建时才执行 ——
 *   表现就是「不打开 App 就收不到通知」。
 *
 *   JobScheduler 的 Job 由系统持有：即使进程被杀，到周期后系统会
 *   重新创建进程并回调 onStartJob，我们在这里把守护服务再拉起来。
 *   这是不引入第三方推送（FCM / 厂商通道）时唯一可行的原生路径。
 *
 * 限制要说清楚：
 *   - 系统最小周期是 15 分钟，做不到秒级实时
 *   - 部分国产 ROM 会限制 JobScheduler，需用户在系统设置里放开自启动
 */
public class MonitorJob extends JobService {

    private static final int JOB_ID = 8810;

    /** 调度周期。已取系统允许的最小值 15 分钟。 */
    private static final long PERIOD = 15 * 60 * 1000L;

    /** 注册周期任务。重复调用是安全的（id 相同会覆盖）。 */
    public static void schedule(Context c) {
        if (c == null) return;
        try {
            if (android.os.Build.VERSION.SDK_INT < 21) return;
            JobScheduler js =
                    (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;
            // 已经有在跑的就直接复用，避免每次启动都重建导致计时重置
            for (JobInfo j : js.getAllPendingJobs()) {
                if (j.getId() == JOB_ID) return;
            }
            JobInfo.Builder b = new JobInfo.Builder(
                    JOB_ID, new ComponentName(c, MonitorJob.class));
            b.setPeriodic(PERIOD);
            // 需要联网，没网就不唤醒，省电
            b.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
            // 重启后仍然保留（需要 RECEIVE_BOOT_COMPLETED）
            try { b.setPersisted(true); } catch (Throwable ignore) {}
            int r = js.schedule(b.build());
            Log.i("XFAN", "MonitorJob scheduled -> " + (r == JobScheduler.RESULT_SUCCESS));
        } catch (Throwable t) {
            Log.w("XFAN", "MonitorJob schedule", t);
        }
    }

    /** 取消周期任务（退出登录时调用） */
    public static void cancel(Context c) {
        try {
            if (android.os.Build.VERSION.SDK_INT < 21) return;
            JobScheduler js =
                    (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js != null) js.cancel(JOB_ID);
        } catch (Throwable ignore) {}
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        // 进程被杀后系统会在这里重新拉起进程：先恢复守护服务，再立刻查一次消息
        try {
            Api.bindContext(this);
            Api.applyHttpSwitch();
        } catch (Throwable ignore) {}
        try {
            Intent si = new Intent(this, MonitorService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                startForegroundService(si);
            } else {
                startService(si);
            }
        } catch (Throwable t) {
            Log.w("XFAN", "MonitorJob restart service", t);
        }
        // 联网查询放到后台线程，不要卡住系统回调
        new Thread(new Runnable() {
            public void run() {
                try {
                    MonitorService.checkOnce(MonitorJob.this);
                } catch (Throwable t) {
                    Log.w("XFAN", "MonitorJob check", t);
                }
                try { jobFinished(params, false); } catch (Throwable ignore) {}
            }
        }).start();
        return true;   // 任务还在跑，稍后自己调 jobFinished
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // 被系统打断就重排，下一次周期继续
        return true;
    }
}
