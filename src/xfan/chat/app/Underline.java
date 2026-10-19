package xfan.chat.app;

import android.animation.ValueAnimator;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ScaleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;

/**
 * 输入框下划线：
 *   未选中 -> 黑色横线
 *   选中   -> 天蓝色横线从中心向两侧展开，覆盖黑线
 *
 * 实现方式：LayerDrawable 叠两层 —— 底层黑线常驻，上层是 ScaleDrawable
 * （gravity=center，所以缩放是从中心往两边长）。ScaleDrawable 的 level
 * 范围 0~10000，用属性动画推 level 就能得到平滑的展开/收起效果，
 * 不需要自定义 View，也不用改布局层级。
 */
public final class Underline {

    private static final int MAX = 10000;
    private static final int BLACK = 0xFF000000;
    private static final int SKY = 0xFF38B6FF;

    private Underline() {}

    /** 给输入框装上下划线，并接上焦点动画 */
    public static void attach(final EditText et) {
        if (et == null) return;
        try {
            et.setBackgroundDrawable(build(et));
        } catch (Throwable ignore) {
            return;
        }
        et.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                animate(et, has);
            }
        });
        // 初始状态：若本来就有焦点（如自动聚焦）就直接展开
        if (et.hasFocus()) animate(et, true);
    }

    private static Drawable build(EditText et) {
        int h = Math.max(2, dp(et, 2));

        android.graphics.drawable.shapes.Shape line =
                new android.graphics.drawable.shapes.RectShape();
        line.resize(1, h);

        // 底层：黑线
        android.graphics.drawable.ShapeDrawable black =
                new android.graphics.drawable.ShapeDrawable(line);
        black.getPaint().setColor(BLACK);

        // 上层：蓝线，包进 ScaleDrawable 从中心展开
        android.graphics.drawable.ShapeDrawable sky =
                new android.graphics.drawable.ShapeDrawable(
                        new android.graphics.drawable.shapes.RectShape());
        sky.getPaint().setColor(SKY);
        sky.getIntrinsicHeight();
        ScaleDrawable sc = new ScaleDrawable(sky, Gravity.CENTER, 1f, 0f);
        sc.setLevel(0);

        LayerDrawable ld = new LayerDrawable(new Drawable[]{black, sc});
        // 两条线都贴底
        ld.setLayerInset(0, 0, 0, 0, 0);
        ld.setLayerInset(1, 0, 0, 0, 0);
        ld.setLayerGravity(0, Gravity.BOTTOM);
        ld.setLayerGravity(1, Gravity.BOTTOM);
        return ld;
    }

    /** 展开 / 收起蓝线 */
    private static void animate(final EditText et, final boolean show) {
        Drawable d = et.getBackground();
        if (!(d instanceof LayerDrawable)) return;
        final LayerDrawable ld = (LayerDrawable) d;
        if (ld.getNumberOfLayers() < 2) return;
        final Drawable top = ld.getDrawable(1);
        if (top == null) return;

        ValueAnimator old = (ValueAnimator) et.getTag(R.id.tag_ul_anim);
        if (old != null) {
            try { old.cancel(); } catch (Throwable ignore) {}
        }
        int from = top.getLevel();
        int to = show ? MAX : 0;
        ValueAnimator va = ValueAnimator.ofInt(from, to);
        va.setDuration(show ? 260 : 180);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                try {
                    top.setLevel((Integer) a.getAnimatedValue());
                    et.invalidate();
                } catch (Throwable ignore) {}
            }
        });
        et.setTag(R.id.tag_ul_anim, va);
        try { va.start(); } catch (Throwable ignore) {}
    }

    private static int dp(View v, float v0) {
        if (v == null || v.getContext() == null) return (int) (v0 * 2);
        return (int) (v0 * v.getContext().getResources().getDisplayMetrics().density + 0.5f);
    }
}
