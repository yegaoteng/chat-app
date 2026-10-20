package xfan.chat.app;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 下载中心：断点续传 + 暂停/继续 + 速度统计 + 历史记录。
 *
 * 断点续传靠 HTTP Range 头：请求时带上已下载的字节数，
 * 服务端返回 206 就接着写；返回 200 说明不支持，那就从头写。
 *
 * 历史记录存在 Prefs 里（JSON），只记录路径和文件名，
 * 打开页面时再检查文件还在不在 —— 用户在系统里自己删掉的话显示「文件不存在」。
 */
public final class Downloads {

    /** 任务状态 */
    public static final int ST_RUNNING = 0;
    public static final int ST_PAUSED = 1;
    public static final int ST_DONE = 2;
    public static final int ST_ERROR = 3;
    public static final int ST_CANCEL = 4;

    /** 一条下载任务 */
    public static class Task {
        public String id = "";
        public String name = "";
        public String url = "";
        public String path = "";
        public long total = 0;
        public long done = 0;
        public int state = ST_RUNNING;
        public String error = "";
        /** 瞬时速度（字节/秒） */
        public long speed = 0;
        /** 是否支持断点续传 */
        public boolean resume = false;
        /** 内部用：标记下载线程是否已经在跑，避免重复开线程 */
        boolean threadRunning = false;

        public boolean finished() {
            return state == ST_DONE || state == ST_CANCEL || state == ST_ERROR;
        }

        /** 百分比 0~100 */
        public int percent() {
            if (total <= 0) return done > 0 ? (int) Math.min(99, done / 1024) : 0;
            return (int) (done * 100L / total);
        }

        /** 文件还在不在（用户可能在系统文件管理器里删了） */
        public boolean fileExists() {
            try {
                return !path.isEmpty() && new File(path).exists();
            } catch (Throwable ignore) {
                return false;
            }
        }
    }

    private static final List<Task> tasks = new ArrayList<Task>();
    private static final Handler h = new Handler(Looper.getMainLooper());
    private static Runnable notifyTick;
    private static Listener listener;

    public interface Listener {
        void onChanged();
    }

    public static void setListener(Listener l) { listener = l; }

    public static List<Task> all() { return tasks; }

    public static Task find(String id) {
        for (Task t : tasks) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }

    /** 默认下载目录：系统 Download 文件夹。不可用则退回应用私有目录。 */
    public static File dir(Context c) {
        File d = null;
        try {
            d = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
        } catch (Throwable ignore) {}
        if (d == null) {
            try { d = c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS); }
            catch (Throwable ignore) {}
        }
        if (d == null) {
            try { d = c.getFilesDir(); } catch (Throwable ignore) {}
        }
        if (d != null && !d.exists()) {
            try { d.mkdirs(); } catch (Throwable ignore) {}
        }
        return d;
    }

    /**
     * 新建下载任务。
     * 文件名已存在时自动加后缀，避免覆盖旧文件。
     */
    public static Task start(final Context c, String url, String name) {
        final File dir = dir(c);
        if (dir == null || url == null || url.isEmpty()) return null;

        String safe = safeName(name);
        File f = new File(dir, safe);
        int n = 1;
        while (f.exists()) {
            int dot = safe.lastIndexOf('.');
            if (dot > 0) {
                f = new File(dir, safe.substring(0, dot) + "(" + n + ")"
                        + safe.substring(dot));
            } else {
                f = new File(dir, safe + "(" + n + ")");
            }
            n++;
        }

        final Task t = new Task();
        t.id = String.valueOf(System.currentTimeMillis()) + "_" + tasks.size();
        t.name = safe;
        t.url = url;
        t.path = f.getAbsolutePath();
        t.state = ST_RUNNING;
        tasks.add(t);
        save(c);
        kick(c);
        return t;
    }

    /** 去掉文件名里不合法的字符 */
    private static String safeName(String n) {
        String s = (n == null || n.isEmpty()) ? "download" : n;
        s = s.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (s.length() > 80) s = s.substring(0, 80);
        return s;
    }

    public static void pause(Task t) {
        if (t == null || t.finished()) return;
        t.state = ST_PAUSED;
        t.speed = 0;
        changed();
    }

    public static void resume(Context c, Task t) {
        if (t == null || t.finished()) return;
        t.state = ST_RUNNING;
        t.error = "";
        kick(c);
    }

    public static void pauseAll() {
        for (Task t : tasks) {
            if (t.state == ST_RUNNING) { t.state = ST_PAUSED; t.speed = 0; }
        }
        changed();
    }

    public static void resumeAll(Context c) {
        for (Task t : tasks) {
            if (t.state == ST_PAUSED) { t.state = ST_RUNNING; t.error = ""; }
        }
        kick(c);
    }

    /** 取消：删掉半截文件，任务从列表移除 */
    public static void cancel(Context c, Task t) {
        if (t == null) return;
        t.state = ST_CANCEL;
        try {
            File f = new File(t.path);
            if (f.exists()) f.delete();
        } catch (Throwable ignore) {}
        tasks.remove(t);
        save(c);
        changed();
    }

    /**
     * 删除历史记录。
     * delFile 为 true 时连文件一起删（弹窗里的开关控制）。
     */
    public static void remove(Context c, Task t, boolean delFile) {
        if (t == null) return;
        if (delFile) {
            try {
                File f = new File(t.path);
                if (f.exists()) f.delete();
            } catch (Throwable ignore) {}
        }
        tasks.remove(t);
        save(c);
        changed();
    }

    // ---------- 下载线程 ----------

    private static void kick(final Context c) {
        for (Task t : tasks) {
            if (t.state != ST_RUNNING) continue;
            if (t.threadRunning) continue;
            t.threadRunning = true;
            final Task task = t;
            new Thread(new Runnable() {
                public void run() { run_(c, task); }
            }).start();
        }
        changed();
    }

    private static void run_(Context c, Task t) {
        try {
            while (t.state == ST_RUNNING) {
                if (!download(c, t)) break;
                if (t.done >= t.total && t.total > 0) {
                    t.state = ST_DONE;
                    t.speed = 0;
                    break;
                }
            }
        } catch (Throwable e) {
            t.state = ST_ERROR;
            t.error = e.getMessage() == null ? "下载失败" : e.getMessage();
        } finally {
            t.threadRunning = false;
            try { save(c); } catch (Throwable ignore) {}
            h.post(new Runnable() {
                public void run() { changed(); }
            });
        }
    }

    /** 单次连接下载。返回 false 表示不能再继续（出错或暂停）。 */
    private static boolean download(Context c, Task t) {
        HttpURLConnection conn = null;
        try {
            long start = t.done;
            conn = (HttpURLConnection) new URL(t.url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", Api.UA_BROWSER);
            if (start > 0) {
                // 断点续传：告诉服务端从哪个字节开始给
                conn.setRequestProperty("Range", "bytes=" + start + "-");
            }

            int code = conn.getResponseCode();
            if (code == 416) {
                // 请求的范围超出文件长度，说明服务端那边已经下完了
                t.done = t.total;
                return true;
            }
            if (code != 200 && code != 206) {
                throw new java.io.IOException("HTTP " + code);
            }
            t.resume = (code == 206);

            if (code == 200) {
                // 服务端不支持续传，只能从头写
                start = 0;
                t.done = 0;
            }
            long len = conn.getContentLength();
            if (len > 0) {
                t.total = (code == 206) ? (start + len) : len;
            }

            File f = new File(t.path);
            if (f.getParentFile() != null && !f.getParentFile().exists()) {
                f.getParentFile().mkdirs();
            }

            RandomAccessFile raf = new RandomAccessFile(f, "rw");
            try {
                raf.seek(start);
                InputStream in = conn.getInputStream();
                byte[] buf = new byte[16384];
                int n;
                long lastMark = System.currentTimeMillis();
                long lastDone = t.done;

                while (t.state == ST_RUNNING && (n = in.read(buf)) > 0) {
                    raf.write(buf, 0, n);
                    t.done += n;

                    long now = System.currentTimeMillis();
                    long gap = now - lastMark;
                    if (gap >= 400) {
                        t.speed = (t.done - lastDone) * 1000L / gap;
                        lastMark = now;
                        lastDone = t.done;
                        h.post(new Runnable() {
                            public void run() { changed(); }
                        });
                    }
                    // 每写一段就存一次进度，下次打开能接着下
                    if (t.done % (512 * 1024) < 16384) {
                        try { save(c); } catch (Throwable ignore) {}
                    }
                }
                try { in.close(); } catch (Throwable ignore) {}
            } finally {
                try { raf.close(); } catch (Throwable ignore) {}
            }

            // 暂停时也要保留已写的部分
            if (t.state == ST_PAUSED) return false;
            if (t.total > 0 && t.done < t.total) return true;   // 继续下一轮
            if (t.total <= 0) t.state = ST_DONE;                // 拿不到总长度，读完就算完成
            return true;
        } catch (Throwable e) {
            if (t.state == ST_RUNNING) {
                t.state = ST_ERROR;
                t.error = e.getMessage() == null ? "下载失败" : e.getMessage();
            }
            return false;
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Throwable ignore) {}
            }
        }
    }

    // ---------- 历史记录持久化 ----------

    public static void save(Context c) {
        try {
            JSONArray a = new JSONArray();
            for (Task t : tasks) {
                JSONObject o = new JSONObject();
                o.put("id", t.id);
                o.put("name", t.name);
                o.put("url", t.url);
                o.put("path", t.path);
                o.put("total", t.total);
                o.put("done", t.done);
                // 退出时还在跑的任务，下次进来默认暂停，避免偷偷占流量
                o.put("state", t.state == ST_RUNNING ? ST_PAUSED : t.state);
                o.put("error", t.error);
                a.put(o);
            }
            Prefs.put("dl_list", a.toString());
        } catch (Throwable ignore) {}
    }

    /** 启动时恢复历史记录 */
    public static void restore() {
        try {
            String s = Prefs.get("dl_list", "");
            if (s.isEmpty()) return;
            JSONArray a = new JSONArray(s);
            tasks.clear();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                Task t = new Task();
                t.id = o.optString("id", String.valueOf(i));
                t.name = o.optString("name", "");
                t.url = o.optString("url", "");
                t.path = o.optString("path", "");
                t.total = o.optLong("total", 0);
                t.done = o.optLong("done", 0);
                t.state = o.optInt("state", ST_PAUSED);
                t.error = o.optString("error", "");
                // 已完成的任务：以文件实际大小为准
                if (t.state == ST_DONE && t.fileExists()) t.done = new File(t.path).length();
                tasks.add(t);
            }
        } catch (Throwable ignore) {}
    }

    private static void changed() {
        if (listener != null) {
            try { listener.onChanged(); } catch (Throwable ignore) {}
        }
    }

    /** 人性化速度显示 */
    public static String speedText(long bps) {
        if (bps <= 0) return "";
        if (bps < 1024) return bps + " B/s";
        if (bps < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB/s",
                bps / 1024.0);
        return String.format(Locale.getDefault(), "%.2f MB/s", bps / 1048576.0);
    }

    /** 人性化大小显示 */
    public static String sizeText(long b) {
        if (b <= 0) return "0 B";
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", b / 1024.0);
        return String.format(Locale.getDefault(), "%.2f MB", b / 1048576.0);
    }
}
