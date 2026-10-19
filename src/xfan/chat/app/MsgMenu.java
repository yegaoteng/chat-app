package xfan.chat.app;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

/**
 * 长按菜单：显示在长按位置的下方（下方放不下就翻到上方），
 * 不铺灰色遮罩，每项都带圆角边框。
 *
 * 不用 AlertDialog —— 它只能居中且自带遮罩，做不到「贴在手指下方」。
 */
public class MsgMenu {

    public interface Callback {
        void onAction(String act);
    }

    /**
     * @param anchor 用于定位的参照 View（一般是消息列表）
     * @param x      长按点相对 anchor 的横坐标
     * @param y      长按点相对 anchor 的纵坐标
     * @param muted  当前会话是否已免打扰
     */
    public static void show(Context ctx, View anchor, float x, float y,
                            List<String> acts, boolean muted, final Callback cb) {
        show(ctx, anchor, x, y, acts, muted, true, cb);
    }

    /**
     * 按被长按的条目 View 定位。
     *
     * 之前只用全局记录的 lastTouchX/Y，第二次长按时常拿到上一次的旧坐标，
     * 弹窗就出现在上次的位置。用条目自身的位置最可靠 ——
     * 它天然就是「这次长按的那一条」，不存在复用旧值的问题。
     */
    public static void showAt(Context ctx, View anchor, View item,
                              List<String> acts, boolean muted, boolean showMute,
                              final Callback cb) {
        if (ctx == null || anchor == null) return;
        if (item != null) {
            float cx = item.getLeft() + item.getWidth() / 2f;
            float cy = item.getTop() + item.getHeight() / 2f;
            show(ctx, anchor, cx, cy, acts, muted, showMute, cb);
            return;
        }
        show(ctx, anchor, anchor.getWidth() / 2f, anchor.getHeight() / 2f,
                acts, muted, showMute, cb);
    }

    /**
     * @param showMute 是否显示免打扰开关。消息栏长按传 false。
     */
    public static void show(Context ctx, View anchor, float x, float y,
                            List<String> acts, boolean muted, boolean showMute,
                            final Callback cb) {
        if (ctx == null || anchor == null) return;

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (8 * ctx.getResources().getDisplayMetrics().density + 0.5f);
        box.setPadding(pad, pad, pad, pad);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xFFF2F3F5);          // 灰底
        gd.setCornerRadius(Ui.R);
        gd.setStroke(2, 0xFF1B3A8C);      // 深蓝边
        box.setBackgroundDrawable(gd);

        // 免打扰开关：接收消息，但不在通知中心弹（仅好友 / 频道页显示）
        if (showMute) {
            final Switch sw = new Switch(ctx);
            sw.setText("免打扰");
            sw.setTextColor(0xFF1B4DB0);
            sw.setChecked(muted);
            sw.setPadding(pad, pad, pad, pad);
            sw.setBackgroundDrawable(Ui.border(0xFFF2F3F5, 0xFF1B3A8C, Ui.R));
            sw.setOnCheckedChangeListener(
                    new android.widget.CompoundButton.OnCheckedChangeListener() {
                        public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                            if (cb != null) cb.onAction(on ? "__mute_on" : "__mute_off");
                        }
                    });
            box.addView(sw, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        for (int i = 0; i < acts.size(); i++) {
            final String a = acts.get(i);
            TextView t = new TextView(ctx);
            t.setText(a);
            t.setTextColor(0xFF1B4DB0);
            t.setTextSize(15);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(pad * 2, pad, pad * 2, pad);
            t.setBackgroundDrawable(Ui.border(0xFFF2F3F5, 0xFF1B3A8C, Ui.R));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = pad / 2;
            t.setLayoutParams(lp);
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (cb != null) cb.onAction(a);
                }
            });
            box.addView(t);
        }

        final PopupWindow pw = new PopupWindow(box,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setBackgroundDrawable(null);          // 不要默认黑框
        pw.setClippingEnabled(true);
        pw.setAnimationStyle(android.R.style.Animation_Dialog);

        box.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int mw = box.getMeasuredWidth();
        int mh = box.getMeasuredHeight();

        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int screenH = ctx.getResources().getDisplayMetrics().heightPixels;
        int screenW = ctx.getResources().getDisplayMetrics().widthPixels;

        int gx = (int) (loc[0] + x - mw / 2f);
        int gy = (int) (loc[1] + y + 8);          // 默认：手指下方
        if (loc[1] + y + mh + 24 > screenH) {
            gy = (int) (loc[1] + y - mh - 8);     // 下方放不下就翻到上方
        }
        if (gx < 8) gx = 8;
        if (gx + mw > screenW - 8) gx = screenW - 8 - mw;
        if (gy < 8) gy = 8;

        try {
            pw.showAtLocation(anchor, Gravity.NO_GRAVITY, gx, gy);
        } catch (Throwable ignore) {}
    }
}
