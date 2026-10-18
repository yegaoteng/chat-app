package xfan.chat.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 个性化引擎：主题 / 气泡 / 名称颜色。 */
public class ThemeEngine {

    // ---------- 主题 ----------
    public static boolean isGold() {
        return "gold".equals(Prefs.theme());
    }

    /** 主题主色（顶栏 / 底栏） */
    public static int[] themeBar() {
        if (isGold()) return new int[]{0xFFB8860B, 0xFF8B6508};   // 金色渐变
        return new int[]{0xFF2B2B40, 0xFF1B1B28};                  // 默认深色
    }

    /** 主题边框色（金辉红框） */
    public static int themeEdge() {
        return isGold() ? 0xFFC0392B : 0x00000000;
    }

    /** 主内容背景色；gold 主题返回 -1 表示要用渐变 */
    public static int themeBg() {
        if (isGold()) return -1;
        return 0xFFFFFFFF;
    }

    public static int[] themeBgGradient() {
        if (isGold()) return new int[]{0xFFFFF3C4, 0xFFF5D976, 0xFFE8B923, 0xFFF5D976};
        return new int[]{0xFFFFFFFF, 0xFFFFFFFF};
    }

    /** 面板（输入区 / 分组标题）背景 */
    public static int themePanel() {
        return isGold() ? 0x33C0392B : 0xFFF5F5F7;
    }

    /** 主文字色 */
    public static int themeText() {
        return isGold() ? 0xFF4A2C00 : 0xFF222222;
    }

    /** 次级文字色 */
    public static int themeTextSub() {
        return isGold() ? 0xFF8A6A20 : 0xFF8A8A94;
    }

    /** 把主题应用到整个 Activity（背景 + 顶栏 + 底栏 + 侧栏青字） */
    public static void applyActivity(Activity a) {
        if (a == null) return;
        try {
            // 用 window 背景最可靠：能覆盖所有页面（fragment 不再各自设底色）
            int[] g = themeBgGradient();
            GradientDrawable gd = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM, g);
            a.getWindow().setBackgroundDrawable(gd);
            View root = a.findViewById(android.R.id.content);
            if (root != null) root.setBackgroundDrawable(null);
            int[] bar = themeBar();
            GradientDrawable bd = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT, bar);
            if (isGold()) bd.setStroke(2, themeEdge());

            View top = a.findViewById(R.id.topBar);
            if (top != null) top.setBackgroundDrawable(bd);

            // 底部标签栏
            View bot = a.findViewById(R.id.bottomBar);
            if (bot != null) bot.setBackgroundDrawable(bd);

            // 侧边栏保持青字（用户明确要求），仅换背景
            View panel = a.findViewById(R.id.drawerPanel);
            if (panel != null) {
                panel.setBackgroundColor(isGold() ? 0xFF2A1E00 : 0xFF232336);
            }
        } catch (Exception ignore) {}
    }

    // ---------- 气泡 ----------
    public static String bubbleKey() { return Prefs.bubble(); }

    /**
     * 生成气泡背景。
     * @param self 是否为自己发送的消息
     */
    /** 默认气泡（用自己的偏好） */
    public static GradientDrawable bubble(boolean self) {
        return bubbleFor(bubbleKey(), self);
    }

    /**
     * 按指定气泡 key 生成背景。
     * 服务端会在每条消息里回填发送者的 bubble，所以别人看到的也是对方的个性化气泡。
     */
    public static GradientDrawable bubbleFor(String bubbleKey, boolean self) {
        String k = (bubbleKey == null || bubbleKey.isEmpty()) ? "default" : bubbleKey;
        GradientDrawable gd = new GradientDrawable();

        if ("golden".equals(k)) {
            // 金箔质感：金色渐变 + 金边
            int[] c = self
                    ? new int[]{0xFFF9E076, 0xFFE0A800}
                    : new int[]{0xFFFFF6D0, 0xFFF0D060};
            gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, c);
            gd.setStroke(2, 0xFFB8860B);
            gd.setCornerRadius(14);
            return gd;
        }
        if ("rainbow".equals(k)) {
            // 七色流彩：七彩边框
            int[] c = self
                    ? new int[]{0x33FF0080, 0x33FF8C00}
                    : new int[]{0x22FFFFFF, 0x22EEEEEE};
            gd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, c);
            gd.setCornerRadius(14);
            // 用叠加边框模拟七彩（原生只能单色描边，这里取彩虹主色）
            gd.setStroke(3, rainbowColor(0));
            return gd;
        }
        // default：跟随主题。加一圈描边，保证在主题背景上依然「浮」出来
        int fill = self ? (isGold() ? 0xFFC8901E : 0xFF667EEA)
                        : (isGold() ? 0xFFFFF0C0 : 0xFFECECEF);
        gd.setColor(fill);
        gd.setCornerRadius(14);
        gd.setStroke(1, isGold() ? 0x66FFFFFF : 0x14000000);
        return gd;
    }

    /** 气泡内文字颜色：与底色自动取对比度更高的一方 */
    public static int bubbleText(boolean self) {
        return bubbleTextFor(bubbleKey(), self);
    }

    public static int bubbleTextFor(String bubbleKey, boolean self) {
        String k = (bubbleKey == null || bubbleKey.isEmpty()) ? "default" : bubbleKey;
        if ("golden".equals(k)) return self ? 0xFF3A2A00 : 0xFF4A3A00;
        if ("rainbow".equals(k)) return isGold() ? 0xFF4A2C00 : 0xFF222222;
        return self ? 0xFFFFFFFF : (isGold() ? 0xFF4A2C00 : 0xFF222222);
    }

    // ---------- 名称颜色 ----------
    public static String nameKey() { return Prefs.namecolor(); }

    public static boolean isRainbow() {
        String k = nameKey();
        return "rainbow_static".equals(k) || "rainbow_dynamic".equals(k);
    }

    /** 彩虹色序列 */
    private static final int[] RAINBOW = {
            0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00,
            0xFF34C759, 0xFF00C7BE, 0xFF007AFF, 0xFFAF52DE
    };

    public static int rainbowColor(int i) {
        return RAINBOW[((i % RAINBOW.length) + RAINBOW.length) % RAINBOW.length];
    }

    /** 普通名称颜色（非彩虹时） */
    public static int namePlainColor() {
        return namePlainColorFor(nameKey());
    }

    /** 按指定名称颜色 key 取普通色。 */
    public static int namePlainColorFor(String key) {
        if ("gold".equals(key)) return 0xFFD4A017;
        return themeText();
    }

    /** 给 TextView 应用名称颜色（彩虹用渐变 shader，动态带流动） */
    public static void applyNameColor(TextView tv, String fallbackPlain) {
        applyNameColorFor(tv, nameKey());
    }

    /**
     * 给 TextView 应用指定名称颜色。
     * 传 null/空表示用默认色（对方没设置个性化时）。
     */
    public static void applyNameColorFor(TextView tv, String key) {
        if (tv == null) return;
        String k = (key == null || key.isEmpty()) ? "default" : key;
        if ("rainbow_static".equals(k) || "rainbow_dynamic".equals(k)) {
            applyRainbow(tv);
        } else {
            tv.getPaint().setShader(null);
            tv.setTextColor(namePlainColorFor(k));
            RainbowAnimator.unregister(tv);
        }
    }

    private static void applyRainbow(TextView tv) {
        float w = tv.getWidth();
        if (w <= 0) w = 120f;
        Shader sh = new LinearGradient(0, 0, w, 0, RAINBOW, null, Shader.TileMode.MIRROR);
        tv.getPaint().setShader(sh);
        tv.setTextColor(0xFF222222);   // 底字，被 shader 覆盖
        if ("rainbow_dynamic".equals(nameKey())) RainbowAnimator.register(tv);
        else RainbowAnimator.unregister(tv);
    }

    /**
     * 动态七彩：定时平移 shader 矩阵产生流动效果。
     * 只维护少量已注册的 TextView，避免列表滚动时堆积。
     */
    private static class RainbowAnimator {
        // 同样必须绑主线程 Looper：RainbowAnimator 是内部类，
        // 若在子线程被首次加载，裸 new Handler() 会直接抛异常导致闪退。
        private static final Handler h = new Handler(android.os.Looper.getMainLooper());
        private static final List<TextView> views = new ArrayList<TextView>();
        private static boolean running = false;
        private static float off = 0f;

        private static final Runnable tick = new Runnable() {
            public void run() {
                off += 6f;
                for (int i = views.size() - 1; i >= 0; i--) {
                    TextView tv = views.get(i);
                    try {
                        Shader sh = tv.getPaint().getShader();
                        if (sh == null) continue;
                        android.graphics.Matrix mx = new android.graphics.Matrix();
                        mx.setTranslate(off, 0);
                        sh.setLocalMatrix(mx);
                        tv.invalidate();
                    } catch (Exception ignore) {}
                }
                if (running) h.postDelayed(this, 60);
            }
        };

        static void register(TextView tv) {
            if (!views.contains(tv)) views.add(tv);
            if (!running) { running = true; h.postDelayed(tick, 60); }
        }

        static void unregister(TextView tv) {
            views.remove(tv);
        }
    }

    /** 列表刷新前清理已失效的动画目标，防止内存泄漏 */
    public static void trimRainbow(int max) {
        while (RainbowAnimator.views.size() > max) {
            RainbowAnimator.views.remove(RainbowAnimator.views.size() - 1);
        }
    }

    // ---------- 通用小工具 ----------
    /** 生成带边框的卡片背景（用于按钮 / 面板） */
    public static GradientDrawable card(int fill, int stroke, int radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fill);
        gd.setCornerRadius(radius);
        if (stroke != 0) gd.setStroke(2, stroke);
        return gd;
    }

    public static int parseHex(String s, int def) {
        try { return Color.parseColor(s); } catch (Exception e) { return def; }
    }
}
