package xfan.chat.app;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.net.Uri;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * 后台守护：
 *   - 每 60 秒查一次当前会话有没有新消息（仅当 App 不在前台时发通知）
 *   - 每 1 小时比对一次服务端版本，有新版本发通知
 *
 * 用常驻 Service + Handler 定时，比 AlarmManager 省事，
 * 也不依赖 BootReceiver（App 启动过一次就在）。
 */
public class MonitorService extends Service {

    private static final long MSG_EVERY = 20_000L;
    private static final long VER_EVERY = 3_600_000L;

    private final Handler h = new Handler(Looper.getMainLooper());
    private long lastVer = 0L;
    private final Map<String, Long> lastSeen = new HashMap<String, Long>();

    private final Runnable tick = new Runnable() {
        public void run() {
            try {
                checkMessages();
                long now = System.currentTimeMillis();
                if (now - lastVer > VER_EVERY) {
                    lastVer = now;
                    checkVersion();
                }
            } catch (Throwable t) {
                Log.w("XFAN", "monitor tick", t);
            }
            h.postDelayed(this, MSG_EVERY);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Api.bindContext(this);
        Api.applyHttpSwitch();
        // 必须前台化：普通后台 Service 在国产 ROM（vivo/小米等）上
        // 几分钟内就会被回收，表现为「挂后台收不到通知」。
        // 前台服务会常驻一条低优先级通知，系统不会轻易杀。
        try { startForeground(7788, ongoing()); } catch (Throwable ignore) {}
        h.postDelayed(tick, 3000);
    }

    /** 常驻通知：重要性设为 LOW，不响铃不震动，只在通知栏安静显示 */
    private android.app.Notification ongoing() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                android.app.NotificationChannel ch = new android.app.NotificationChannel(
                        "xfan_keep", "后台运行",
                        android.app.NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                android.app.NotificationManager m =
                        (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (m != null) m.createNotificationChannel(ch);
            }
            android.app.Notification.Builder b =
                    new android.app.Notification.Builder(this)
                            .setSmallIcon(android.R.drawable.stat_notify_chat)
                            .setContentTitle("xfan chat 正在后台运行")
                            .setContentText("有新的频道或私聊消息时会通知你")
                            .setOngoing(true);
            if (android.os.Build.VERSION.SDK_INT >= 26) b.setChannelId("xfan_keep");
            try {
                b.setContentIntent(android.app.PendingIntent.getActivity(this, 2,
                        new Intent(this, MainActivity.class)
                                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        android.os.Build.VERSION.SDK_INT >= 23
                                ? android.app.PendingIntent.FLAG_IMMUTABLE
                                        | android.app.PendingIntent.FLAG_UPDATE_CURRENT
                                : android.app.PendingIntent.FLAG_UPDATE_CURRENT));
            } catch (Throwable ignore) {}
            return b.build();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        // 进程被杀后系统会用 null intent 重建，前台标记要重新打一次
        try { startForeground(7788, ongoing()); } catch (Throwable ignore) {}
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // 用户划掉后台卡片后自动重启，避免彻底收不到消息
        try {
            Intent i = new Intent(this, MonitorService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                startForegroundService(i);
            } else {
                startService(i);
            }
        } catch (Throwable ignore) {}
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        try { h.removeCallbacks(tick); } catch (Throwable ignore) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) { return null; }

    /** 前台不打扰：App 正在看的时候就不发通知 */
    private boolean foreground() {
        try {
            return MainActivity.self != null && MainActivity.foreground;
        } catch (Throwable ignore) {
            return false;
        }
    }

    private void checkMessages() {
        if (foreground()) return;
        final String user = Prefs.user();
        if (user == null || user.isEmpty()) return;

        new Thread(new Runnable() {
            public void run() {
                // 频道列表。注意返回的 key 是 channels，不是 list
                JSONObject ch = Api.get("user.php?action=my_channels");
                JSONArray arr = (ch == null) ? null : ch.optJSONArray("channels");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o == null) continue;
                        String code = o.optString("code", "");
                        if (code.isEmpty()) continue;
                        scan("频道" + code, "user.php?action=ch_history&code="
                                + Uri.encode(code));
                    }
                }
                // 私聊列表。接口名是 pm_history（不是 pm），返回 key 是 friends
                JSONObject fr = Api.get("user.php?action=friends");
                JSONArray fa = (fr == null) ? null : fr.optJSONArray("friends");
                if (fa != null) {
                    for (int i = 0; i < fa.length(); i++) {
                        JSONObject o = fa.optJSONObject(i);
                        if (o == null) continue;
                        String nm = o.optString("username", "");
                        if (nm.isEmpty()) continue;
                        scan("与" + nm + "私聊", "user.php?action=pm_history&peer="
                                + Uri.encode(nm));
                    }
                }
            }
        }).start();
    }

    /** 拉一次会话，若最后一条比上次看到的新且不是自己发的，就通知 */
    private void scan(String target, String url) {
        JSONObject r = Api.get(url);
        if (r == null) return;
        long ts = Notify.lastTs(r);
        if (ts <= 0) return;
        Long prev = lastSeen.get(target);
        lastSeen.put(target, ts);
        if (prev == null) return;          // 首次只记录基线，不补历史通知
        if (ts <= prev) return;

        JSONArray arr = r.optJSONArray("messages");
        if (arr == null) arr = r.optJSONArray("list");
        if (arr == null || arr.length() == 0) return;
        JSONObject last = arr.optJSONObject(arr.length() - 1);
        if (last == null) return;
        String who = last.optString("username", "");
        String me = Prefs.user();
        if (who.isEmpty() || who.equals(me)) return;
        Notify.message(this, target, who + "（" + target + "）",
                last.optString("content", ""));
    }

    private void checkVersion() {
        new Thread(new Runnable() {
            public void run() {
                JSONObject r = Api.get("appapi.php?action=app_update");
                if (r == null || !r.optBoolean("success", false)) return;
                int remoteCode = r.optInt("version_code", 0);
                String remoteName = r.optString("version_name", "");
                int localCode = 0;
                String localName = "0";
                try {
                    localCode = getPackageManager()
                            .getPackageInfo(getPackageName(), 0).versionCode;
                    localName = getPackageManager()
                            .getPackageInfo(getPackageName(), 0).versionName;
                } catch (Exception ignore) {}
                String shown = Prefs.get("last_update_notified", "");
                boolean newer = remoteCode > localCode
                        || (remoteCode == localCode && remoteCode > 0
                            && compare(remoteName, localName) > 0);
                if (!newer || remoteName.equals(shown)) return;
                Prefs.put("last_update_notified", remoteName);
                Notify.update(MonitorService.this, remoteName, r.optString("changelog", ""));
            }
        }).start();
    }

    /** 版本名逐段比较 */
    private static int compare(String a, String b) {
        String[] x = (a == null ? "" : a).split("\\.");
        String[] y = (b == null ? "" : b).split("\\.");
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            int p = 0, q = 0;
            try { p = Integer.parseInt(x[i].replaceAll("[^0-9]", "")); } catch (Exception ignore) {}
            try { q = Integer.parseInt(y[i].replaceAll("[^0-9]", "")); } catch (Exception ignore) {}
            if (p != q) return p - q;
        }
        return 0;
    }
}
