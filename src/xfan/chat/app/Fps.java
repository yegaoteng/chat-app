package xfan.chat.app;

import android.app.Activity;
import android.content.DialogInterface;
import android.view.WindowManager;

/**
 * 帧率设置：60 / 90 / 120 / 144 Hz。
 *
 * 「真实流畅」不是只改个数字：
 *   1. 用 Window.setFrameRate() 告诉系统本窗口期望的刷新率（Android 11+）
 *   2. 同时设置 preferredRefreshRate，让系统把屏幕切到对应档位
 * 低于 Android 11 的机型没有这组 API，只能跟随系统。
 */
public class Fps {

    public static final int[] CHOICES = {60, 90, 120, 144};

    /** 屏幕当前实际刷新率；取不到返回 60 */
    public static float screenHz(Activity a) {
        if (a == null) return 60f;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                float hz = a.getWindowManager().getDefaultDisplay().getRefreshRate();
                if (hz > 0) return hz;
            }
        } catch (Throwable ignore) {}
        return 60f;
    }

    public static boolean supported() {
        return android.os.Build.VERSION.SDK_INT >= 30;
    }

    /**
     * 应用帧率。fps<=0 表示跟随系统。
     * 返回是否真正生效。
     */
    public static boolean apply(Activity a, int fps) {
        if (a == null) return false;
        if (!supported()) return false;
        try {
            WindowManager window = a.getWindow();
            WindowManager.LayoutParams p = window.getAttributes();

            if (fps > 0) {
                p.preferredRefreshRate = (float) fps;
                window.setAttributes(p);
                // 声明本窗口按这个帧率渲染，系统会据此调度 vsync
                window.setFrameRate((float) fps,
                        WindowManager.LayoutParams.FRAME_RATE_COMPATIBILITY_DEFAULT);
            } else {
                p.preferredRefreshRate = 0f;
                window.setAttributes(p);
                window.setFrameRate(0f,
                        WindowManager.LayoutParams.FRAME_RATE_COMPATIBILITY_DEFAULT);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 弹出选择框 */
    public static void show(final Activity a, final Runnable onChanged) {
        if (a == null) return;
        final int cur = Prefs.fps();
        final float hz = screenHz(a);

        String[] labels = new String[CHOICES.length + 1];
        labels[0] = "跟随系统（" + fmt(hz) + " Hz）";
        for (int i = 0; i < CHOICES.length; i++) {
            int c = CHOICES[i];
            labels[i + 1] = c + " Hz" + (Math.abs(hz - c) < 1f ? "（当前屏幕档位）" : "");
        }

        int sel = 0;
        for (int i = 0; i < CHOICES.length; i++) {
            if (CHOICES[i] == cur) { sel = i + 1; break; }
        }

        new android.app.AlertDialog.Builder(a)
                .setTitle("显示设置 · 帧率")
                .setSingleChoiceItems(labels, sel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        int v = (which <= 0) ? 0 : CHOICES[which - 1];
                        Prefs.fps(v);
                        boolean ok = apply(a, v);
                        d.dismiss();
                        android.widget.Toast.makeText(a,
                                ok ? ("已切换到 " + (v > 0 ? v + " Hz" : "跟随系统"))
                                   : "当前系统不支持自定义帧率（需 Android 11+）",
                                android.widget.Toast.LENGTH_SHORT).show();
                        if (onChanged != null) onChanged.run();
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private static String fmt(float f) {
        return (Math.abs(f - Math.round(f)) < 0.05f)
                ? String.valueOf(Math.round(f))
                : String.format("%.1f", f);
    }
}
