package xfan.chat.app;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

/**
 * 液态玻璃 UI。
 *
 * 零第三方依赖，纯 Canvas 绘制：
 *   1. 半透明渐变底（上亮下暗，模拟玻璃厚度）
 *   2. 顶部一条高光（玻璃边缘反光）
 *   3. 内描边（选中态更强）
 *   4. 按下时一道斜向扫光掠过
 *
 * 配合 attach() 的按压缩放，做出「按下去会陷一下、松开弹回来」的手感。
 */
public class Glass {

    // ---------- 配色 ----------
    private static final int TOP_ON     = 0x59FFFFFF;   // 选中：顶部玻璃
    private static final int BOTTOM_ON  = 0x26FFFFFF;
    private static final int TOP_OFF    = 0x33FFFFFF;   // 未选中
    private static final int BOTTOM_OFF = 0x14FFFFFF;
    private static final int EDGE_ON    = 0x99FFFFFF;
    private static final int EDGE_OFF   = 0x40FFFFFF;
    private static final int HI_ON      = 0xB3FFFFFF;   // 顶部高光
    private static final int HI_OFF     = 0x66FFFFFF;
    private static final int GLOW       = 0x552979FF;   // 选中态的蓝色辉光

    /** 玻璃背景 */
    public static class GlassDrawable extends Drawable {
        private final Paint fill   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hi     = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowP  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sh     = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rc     = new RectF();

        private float radius;
        private boolean on;
        private float shine = -1f;      // 扫光进度 0..1，<0 表示不画

        public GlassDrawable(float radiusPx, boolean on) {
            this.radius = radiusPx;
            this.on = on;
            stroke.setStyle(Paint.Style.STROKE);
            hi.setStyle(Paint.Style.STROKE);
            glowP.setStyle(Paint.Style.STROKE);
        }

        public void setOn(boolean v) { on = v; invalidateSelf(); }

        /** 扫光进度：0=刚出现（左上），1=已掠过（右下） */
        public void setShine(float p) { shine = p; invalidateSelf(); }

        public float getShine() { return shine; }

        public void setRadiusPx(float r) { radius = r; invalidateSelf(); }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            rc.set(b.left + 0.5f, b.top + 0.5f, b.right - 0.5f, b.bottom - 0.5f);

            // 1) 底：竖向渐变
            fill.setShader(new LinearGradient(0, rc.top, 0, rc.bottom,
                    on ? TOP_ON : TOP_OFF, on ? BOTTOM_ON : BOTTOM_OFF,
                    Shader.TileMode.CLAMP));
            c.drawRoundRect(rc, radius, radius, fill);

            // 2) 底部一点环境反光，让玻璃有厚度
            Paint env = new Paint(Paint.ANTI_ALIAS_FLAG);
            env.setShader(new LinearGradient(0, rc.bottom - Math.max(6f, rc.height() * 0.35f),
                    0, rc.bottom, 0x00000000, on ? 0x22FFFFFF : 0x14FFFFFF,
                    Shader.TileMode.CLAMP));
            c.drawRoundRect(rc, radius, radius, env);

            // 3) 顶部高光
            hi.setStrokeWidth(Math.max(1f, radius * 0.10f));
            hi.setColor(on ? HI_ON : HI_OFF);
            c.drawLine(rc.left + radius * 0.7f, rc.top + hi.getStrokeWidth(),
                    rc.right - radius * 0.7f, rc.top + hi.getStrokeWidth(), hi);

            // 4) 描边（选中加蓝色辉光）
            if (on) {
                glowP.setStrokeWidth(Math.max(2f, radius * 0.22f));
                glowP.setColor(GLOW);
                c.drawRoundRect(rc, radius, radius, glowP);
            }
            stroke.setStrokeWidth(Math.max(1f, radius * 0.13f));
            stroke.setColor(on ? EDGE_ON : EDGE_OFF);
            c.drawRoundRect(rc, radius, radius, stroke);

            // 5) 按下时的斜向扫光
            if (shine >= 0f && shine <= 1f) {
                float w = rc.width();
                float x = rc.left - w * 0.6f + (w * 1.6f) * shine;
                sh.setShader(new LinearGradient(x, rc.top, x + w * 0.45f, rc.bottom,
                        0x00FFFFFF, 0x5CFFFFFF, Shader.TileMode.CLAMP));
                c.save();
                c.clipRect(rc.left, rc.top, rc.right, rc.bottom);
                android.graphics.Path p = new android.graphics.Path();
                p.moveTo(x, rc.bottom);
                p.lineTo(x + w * 0.30f, rc.top);
                p.lineTo(x + w * 0.52f, rc.top);
                p.lineTo(x + w * 0.22f, rc.bottom);
                p.close();
                c.drawPath(p, sh);
                c.restore();
                sh.setShader(null);
            }
        }

        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ---------- 对外接口 ----------

    public static int dp(View v, float v0) {
        float d = (v != null && v.getResources() != null)
                ? v.getResources().getDisplayMetrics().density : 2f;
        return (int) (v0 * d + 0.5f);
    }

    /**
     * 给 View 套上玻璃背景。
     * 文字类自动居中（侧边栏按钮要求文字在按钮正中）。
     */
    public static GlassDrawable apply(View v, boolean selected) {
        return apply(v, selected, 10f);
    }

    public static GlassDrawable apply(View v, boolean selected, float radiusDp) {
        if (v == null) return null;
        GlassDrawable d = new GlassDrawable(dp(v, radiusDp), selected);
        v.setBackground(d);
        v.setClickable(true);
        if (v instanceof TextView) {
            ((TextView) v).setGravity(Gravity.CENTER);
        }
        attach(v, d);
        return d;
    }

    /** 只换背景，不动重力/点击（容器用） */
    public static GlassDrawable background(View v, boolean selected, float radiusDp) {
        if (v == null) return null;
        GlassDrawable d = new GlassDrawable(dp(v, radiusDp), selected);
        v.setBackground(d);
        return d;
    }

    /** 按压动效：按下缩小并扫光，松开回弹 */
    public static void attach(final View v, final GlassDrawable d) {
        if (v == null) return;
        v.setOnTouchListener(new View.OnTouchListener() {
            private ValueAnimator sh;

            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        press(view, true);
                        startShine(d);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        press(view, false);
                        if (sh != null) { sh.cancel(); sh = null; }
                        if (d != null) d.setShine(-1f);
                        break;
                }
                // 返回 false：点击事件照常触发
                return false;
            }
        });
    }

    private static void press(View v, boolean down) {
        if (v == null) return;
        v.animate().cancel();
        v.animate()
                .scaleX(down ? 0.955f : 1f)
                .scaleY(down ? 0.955f : 1f)
                .setDuration(down ? 90 : 260)
                .setInterpolator(down ? null : new OvershootInterpolator(2.2f))
                .start();
    }

    private static void startShine(final GlassDrawable d) {
        if (d == null) return;
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(420);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator an) {
                d.setShine((Float) an.getAnimatedValue());
            }
        });
        a.start();
    }
}
