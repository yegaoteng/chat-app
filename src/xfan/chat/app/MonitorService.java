package xfan.chat.app;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
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

    private static final long MSG_EVERY = 60_000L;
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
        h.postDelayed(tick, 5000);
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        return START_STICKY;
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
                // 频道列表
                JSONObject ch = Api.get("user.php?action=my_channels");
                JSONArray arr = (ch == null) ? null : ch.optJSONArray("list");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        String code = arr.optJSONObject(i) == null ? ""
                                : arr.optJSONObject(i).optString("code", "");
                        if (code.isEmpty()) continue;
                        scan("频道" + code, "user.php?action=messages&channel="
                                + java.net.URLEncoder.encode(code));
                    }
                }
                // 私聊列表
                JSONObject fr = Api.get("user.php?action=friends");
                JSONArray fa = (fr == null) ? null : fr.optJSONArray("list");
                if (fa != null) {
                    for (int i = 0; i < fa.length(); i++) {
                        JSONObject o = fa.optJSONObject(i);
                        if (o == null) continue;
                        String nm = o.optString("username", "");
                        if (nm.isEmpty()) continue;
                        scan("与" + nm + "私聊", "user.php?action=pm&peer="
                                + java.net.URLEncoder.encode(nm));
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

        JSONArray arr = r.optJSONArray("list");
        if (arr == null) arr = r.optJSONArray("messages");
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
