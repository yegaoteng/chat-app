package xfan.chat.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 通知中心。
 * 两个用途：
 *   1) 应用没打开 / 在后台时，频道或私聊来了新消息 -> 发通知
 *   2) 后台每小时比对一次版本，服务器有新版本 -> 发通知
 */
public class Notify {

    public static final String CH_MSG = "xfan_msg";
    public static final String CH_UPD = "xfan_update";

    /** Android 13+ 需要显式授权才能发通知 */
    public static boolean granted(Context c) {
        if (c == null) return true;
        if (Build.VERSION.SDK_INT < 33) return true;
        try {
            return c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignore) {
            return false;
        }
    }

    private static NotificationManager nm(Context c) {
        return (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static void ensureChannel(Context c, String id, String name) {
        if (c == null || Build.VERSION.SDK_INT < 26) return;
        try {
            NotificationManager m = nm(c);
            if (m == null || m.getNotificationChannel(id) != null) return;
            NotificationChannel ch = new NotificationChannel(id, name,
                    NotificationManager.IMPORTANCE_DEFAULT);
            ch.enableVibration(true);
            m.createNotificationChannel(ch);
        } catch (Throwable ignore) {}
    }

    private static PendingIntent openIntent(Context c) {
        try {
            Intent i = new Intent(c, MainActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            return PendingIntent.getActivity(c, 1, i, flags);
        } catch (Throwable ignore) {
            return null;
        }
    }

    /** 新消息通知。会话免打扰时仍然接收，只是不弹 */
    public static void message(Context c, String target, String who, String text) {
        if (c == null || !granted(c)) return;
        if (Prefs.muted(target)) return;
        ensureChannel(c, CH_MSG, "新消息");
        try {
            Notification.Builder b = new Notification.Builder(c)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle(who)
                    .setContentText((text == null || text.isEmpty()) ? "[图片]" : text)
                    .setAutoCancel(true)
                    .setContentIntent(openIntent(c));
            if (Build.VERSION.SDK_INT >= 26) b.setChannelId(CH_MSG);
            nm(c).notify(("msg_" + target).hashCode(), b.build());
        } catch (Throwable ignore) {}
    }

    /** 版本更新通知 */
    public static void update(Context c, String vname, String log) {
        if (c == null || !granted(c)) return;
        ensureChannel(c, CH_UPD, "版本更新");
        try {
            String body = (log == null || log.isEmpty()) ? ("新版本 " + vname) : log;
            Notification.Builder b = new Notification.Builder(c)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("发现新版本 " + vname)
                    .setContentText(body)
                    .setAutoCancel(true)
                    .setContentIntent(openIntent(c));
            if (Build.VERSION.SDK_INT >= 26) b.setChannelId(CH_UPD);
            nm(c).notify(9001, b.build());
        } catch (Throwable ignore) {}
    }

    /** 取会话里最后一条消息的时间戳，用于判断有没有新消息 */
    public static long lastTs(JSONObject r) {
        if (r == null) return 0;
        long max = 0;
        JSONArray arr = r.optJSONArray("list");
        if (arr == null) arr = r.optJSONArray("messages");
        if (arr == null) return 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            long ts = o.optLong("timestamp", 0);
            if (ts > max) max = ts;
        }
        return max;
    }
}
