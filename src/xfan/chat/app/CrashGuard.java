package xfan.chat.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 崩溃兜底：记录崩溃堆栈，下次启动时可查看/复制。
 *
 * 目的：闪退发生在主线程之外时界面上什么提示都没有，
 * 只能猜。这里把堆栈存下来，下次打开登录页直接展示，便于定位。
 */
public class CrashGuard {

    private static final String PREF = "xfan_crash";
    private static final String KEY  = "last";
    private static final String FILE = "xfan_crash.txt";

    private static boolean installed = false;

    public static synchronized void install(final Context c) {
        if (installed) return;
        installed = true;
        final Context app = c.getApplicationContext();
        final Thread.UncaughtExceptionHandler old =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(
                new Thread.UncaughtExceptionHandler() {
                    public void uncaughtException(Thread t, Throwable e) {
                        try { save(app, t, e); } catch (Throwable ignore) {}
                        try {
                            if (old != null) old.uncaughtException(t, e);
                        } catch (Throwable ignore) {}
                    }
                });
    }

    private static void save(Context c, Thread t, Throwable e) {
        StringBuilder sb = new StringBuilder();
        sb.append("时间：").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()).format(new Date())).append("\n");
        sb.append("线程：").append(t == null ? "?" : t.getName()).append("\n\n");
        appendThrow(sb, e, 0);

        // 存 SharedPreferences（供界面展示）
        try {
            SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            sp.edit().putString(KEY, sb.toString()).apply();
        } catch (Throwable ignore) {}

        // 同时写文件（供导出）
        try {
            File dir = c.getExternalFilesDir(null);
            if (dir == null) dir = c.getFilesDir();
            if (dir != null) {
                PrintWriter pw = new PrintWriter(new FileWriter(new File(dir, FILE), true));
                pw.println("================================");
                pw.print(sb.toString());
                pw.flush();
                pw.close();
            }
        } catch (Throwable ignore) {}
    }

    private static void appendThrow(StringBuilder sb, Throwable e, int depth) {
        if (e == null || depth > 6) return;
        sb.append(e.getClass().getName())
          .append(": ")
          .append(e.getMessage() == null ? "" : e.getMessage())
          .append("\n");
        StackTraceElement[] st = e.getStackTrace();
        int n = Math.min(st == null ? 0 : st.length, 40);
        for (int i = 0; i < n; i++) sb.append("    at ").append(st[i].toString()).append("\n");
        Throwable cause = e.getCause();
        if (cause != null && cause != e) {
            sb.append("Caused by: ");
            appendThrow(sb, cause, depth + 1);
        }
    }

    public static String last(Context c) {
        try {
            SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            return sp.getString(KEY, "");
        } catch (Throwable e) {
            return "";
        }
    }

    public static void clear(Context c) {
        try {
            c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply();
        } catch (Throwable ignore) {}
    }
}
