package xfan.chat.app;

import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

/**
 * 通用圆角边框工具。
 *
 * 需求：所有可点击的东西都要有一圈边框，但四个角必须圆润（不要直角）。
 * 统一在这里生成，避免各处 radius 不一致。
 */
public final class Ui {

    /** 默认圆角：比直角柔和，又不至于变成胶囊 */
    public static final float R = 12f;

    private Ui() {}

    /** 生成圆角边框背景 */
    public static GradientDrawable border(int fill, int stroke, float radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fill);
        gd.setCornerRadius(radiusDp);
        if (stroke != 0) gd.setStroke(2, stroke);
        return gd;
    }

    /** 给单个 View 加圆角边框 */
    public static void outline(View v) {
        outline(v, 0x33FFFFFF, 0x99FFFFFF);
    }

    public static void outline(View v, int fill, int stroke) {
        if (v == null) return;
        try {
            v.setBackgroundDrawable(border(fill, stroke, R));
        } catch (Throwable ignore) {}
    }

    /**
     * 递归给容器内所有文字/按钮加圆角边框。
     * 用于弹窗：一次调用就把里面所有可点项都描上边。
     */
    public static void outlineAll(View root) {
        if (root == null) return;
        if (root instanceof TextView || root instanceof Button) {
            outline(root);
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                outlineAll(g.getChildAt(i));
            }
        }
    }

    /** 弹窗底部按钮统一加边框：先套一层间距，避免边框连在一起 */
    public static void outlineButtons(android.app.AlertDialog d) {
        if (d == null) return;
        try {
            for (int w = android.content.DialogInterface.BUTTON_POSITIVE;
                 w <= android.content.DialogInterface.BUTTON_NEUTRAL; w++) {
                View v = d.getWindow() == null ? null
                        : d.getWindow().findViewById(android.R.id.button1 + (w + 1));
                if (v != null) {
                    View parent = (v.getParent() instanceof View)
                            ? (View) v.getParent() : null;
                    if (parent != null) {
                        parent.setPadding(8, 8, 8, 8);
                        outline(parent, 0x00000000, 0x99FFFFFF);
                    }
                }
            }
        } catch (Throwable ignore) {}
    }
}
