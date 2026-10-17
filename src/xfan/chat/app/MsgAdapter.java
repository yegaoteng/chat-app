package xfan.chat.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MsgAdapter extends BaseAdapter {

    private final Context ctx;
    private final List<Models.Msg> data;
    private final LayoutInflater inf;

    public MsgAdapter(Context c, List<Models.Msg> d) {
        ctx = c;
        data = d;
        inf = LayoutInflater.from(c);
    }

    public void sort() {
        Collections.sort(data, new Comparator<Models.Msg>() {
            public int compare(Models.Msg a, Models.Msg b) {
                return (int) (a.timestamp - b.timestamp);
            }
        });
    }

    public int getCount() { return data.size(); }
    public Object getItem(int p) { return data.get(p); }
    public long getItemId(int p) { return p; }

    public View getView(int pos, View cv, ViewGroup parent) {
        final ViewHolder vh;
        if (cv == null) {
            cv = inf.inflate(R.layout.item_msg, parent, false);
            vh = new ViewHolder();
            vh.root     = cv.findViewById(R.id.rowRoot);
            vh.inner    = cv.findViewById(R.id.rowInner);
            vh.avLeft   = cv.findViewById(R.id.ivAvLeft);
            vh.avRight  = cv.findViewById(R.id.ivAvRight);
            vh.name     = cv.findViewById(R.id.tvName);
            vh.body     = cv.findViewById(R.id.tvBody);
            vh.img      = cv.findViewById(R.id.ivImg);
            vh.time     = cv.findViewById(R.id.tvTime);
            cv.setTag(vh);
        } else {
            vh = (ViewHolder) cv.getTag();
        }

        final Models.Msg m = data.get(pos);
        String me = Prefs.user();
        boolean self = me != null && me.equals(m.username);

        // ---- 左右布局：整行 gravity 控制，图片消息同样生效 ----
        vh.inner.setGravity(self ? Gravity.END : Gravity.START);
        vh.avLeft.setVisibility(self ? View.GONE : View.VISIBLE);
        vh.avRight.setVisibility(self ? View.VISIBLE : View.GONE);

        // ---- 气泡（应用个性化）----
        vh.body.setBackgroundDrawable(ThemeEngine.bubble(self));
        vh.body.setTextColor(ThemeEngine.bubbleText(self));

        // ---- 内容：文本 / 图片 ----
        if ("image".equals(m.type)) {
            vh.body.setVisibility(View.GONE);
            vh.img.setVisibility(View.VISIBLE);
            vh.img.setImageDrawable(null);
            loadImg(vh.img, Api.absUrl(m.content));
            vh.img.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { openImage(m.content); }
            });
        } else {
            vh.img.setVisibility(View.GONE);
            vh.img.setOnClickListener(null);
            vh.body.setVisibility(View.VISIBLE);
            vh.body.setText(m.content);
            vh.body.setOnClickListener(null);
        }

        // ---- 头像：自己也显示（放右侧）----
        ImageView av = self ? vh.avRight : vh.avLeft;
        loadAvatar(av, m.username, m.avatar);

        // ---- 名称 ----
        vh.name.setText(m.username);
        ThemeEngine.applyNameColor(vh.name, null);

        // ---- 时间：与上一条间隔超过 5 分钟才显示 ----
        if (pos > 0 && (m.timestamp - data.get(pos - 1).timestamp) < 300) {
            vh.time.setVisibility(View.GONE);
        } else {
            vh.time.setVisibility(View.VISIBLE);
            vh.time.setText(fmt(m.timestamp));
        }
        return cv;
    }

    private String fmt(long ts) {
        if (ts <= 0) return "";
        java.text.SimpleDateFormat f =
                new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault());
        return f.format(new java.util.Date(ts * 1000));
    }

    private void openImage(String content) {
        if (ctx == null || content == null || content.isEmpty()) return;
        try {
            android.content.Intent i = new android.content.Intent(ctx, ImageViewActivity.class);
            i.putExtra("url", Api.absUrl(content));
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception ignore) {}
    }

    private void loadAvatar(final ImageView iv, final String name, final String url) {
        if (iv == null) return;
        if (url != null && !url.isEmpty()) {
            loadImg(iv, Api.absUrl(url), new Avatars(name));
        } else {
            iv.setImageDrawable(new Avatars(name));
        }
    }

    /** 带占位图的加载，失败时保留占位（头像不至于空白） */
    private void loadImg(final ImageView iv, final String url, final Drawable placeholder) {
        if (iv == null) return;
        if (url == null || url.isEmpty()) { iv.setImageDrawable(placeholder); return; }
        Bitmap cached = ImgLoader.cached(url);
        if (cached != null) { iv.setImageBitmap(cached); return; }
        if (placeholder != null) iv.setImageDrawable(placeholder);
        iv.setTag(url);
        new Thread(new Runnable() {
            public void run() {
                final Bitmap bm = ImgLoader.fetch(url);
                if (bm == null) return;
                ImgLoader.put(url, bm);
                iv.post(new Runnable() {
                    public void run() {
                        if (url.equals(iv.getTag())) iv.setImageBitmap(bm);
                    }
                });
            }
        }).start();
    }

    private void loadImg(final ImageView iv, final String url) {
        loadImg(iv, url, null);
    }

    static class ViewHolder {
        View root;
        LinearLayout inner;
        ImageView avLeft, avRight, img;
        TextView name, body, time;
    }
}
