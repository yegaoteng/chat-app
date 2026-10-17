package xfan.chat.app;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** 无头像时的首字圆形头像（按用户名哈希取色） */
public class Avatars extends Drawable {

    private final String text;
    private final int bg;
    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTx = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Avatars(String name) {
        String n = (name == null || name.isEmpty()) ? "?" : name;
        // 按码位取首字符，避免切断 emoji 代理对导致显示成方块
        int cp = n.codePointAt(0);
        this.text = new String(Character.toChars(cp));
        int hue = Math.abs(n.hashCode()) % 360;
        this.bg = android.graphics.Color.HSVToColor(new float[]{hue, 0.45f, 0.78f});
        pBg.setColor(this.bg);
        pTx.setColor(0xFFFFFFFF);
        pTx.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        float r = Math.min(b.width(), b.height()) / 2f;
        RectF rf = new RectF(b.left, b.top, b.right, b.bottom);
        c.drawRoundRect(rf, r, r, pBg);
        pTx.setTextSize(Math.min(b.width(), b.height()) * 0.5f);
        Paint.FontMetrics fm = pTx.getFontMetrics();
        float y = b.centerY() - (fm.ascent + fm.descent) / 2f;
        c.drawText(text, b.centerX(), y, pTx);
    }

    @Override public void setAlpha(int a) { pBg.setAlpha(a); pTx.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { pBg.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
