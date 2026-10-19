package xfan.chat.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** 开机 / 网络恢复后拉起后台守护，尽量让「未启动」也能收到新消息通知 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intent) {
        if (c == null || intent == null) return;
        String a = intent.getAction();
        if (a == null) return;
        boolean hit = Intent.ACTION_BOOT_COMPLETED.equals(a)
                || "android.intent.action.QUICKBOOT_POWERON".equals(a)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
                || "android.net.conn.CONNECTIVITY_CHANGE".equals(a);
        if (!hit) return;
        // 没登录就不用拉起，省电
        try {
            if (Prefs.user() == null || Prefs.user().isEmpty()) return;
        } catch (Throwable ignore) {}

        try {
            try { MonitorJob.schedule(c); } catch (Throwable ignore) {}
            Intent si = new Intent(c, MonitorService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(si);
            else c.startService(si);
        } catch (Throwable ignore) {
            try { c.startService(new Intent(c, MonitorService.class)); }
            catch (Throwable ignore2) {}
        }
    }
}
