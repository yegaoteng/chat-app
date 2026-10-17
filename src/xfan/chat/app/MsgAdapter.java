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

    /** 点击图片的回调（由 ChatFragment 提供，用于全屏查看） */
    public interface OnImageClick {
        void onImage(String content);
    }

    private OnImageClick imageClick;

    public void setOnImageClick(OnImageClick l) { imageClick = l; }

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
            vh.root       = cv.findViewById(R.id.rowRoot);
            vh.inner      = cv.findViewById(R.id.rowInner);
            vh.avLeft     = cv.findViewById(R.id.ivAvLeft);
            vh.avRight    = cv.findViewById(R.id.ivAvRight);
            vh.name       = cv.findViewById(R.id.tvName);
            vh.body       = cv.findViewById(R.id.tvBody);
            vh.img        = cv.findViewById(R.id.ivImg);
            vh.imgLoading = cv.findViewById(R.id.tvImgLoading);
            vh.time       = cv.findViewById(R.id.tvTime);
            vh.quoteBox   = cv.findViewById(R.id.quoteBox);
            vh.quoteName  = cv.findViewById(R.id.tvQuoteName);
            vh.quoteText  = cv.findViewById(R.id.tvQuoteText);
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

        // ---- 气泡：用服务端回填的发送者样式，别人也能看到个性化 ----
        String bKey = (m.bubble == null || m.bubble.isEmpty()) ? "default" : m.bubble;
        vh.body.setBackgroundDrawable(ThemeEngine.bubbleFor(bKey, self));
        vh.body.setTextColor(ThemeEngine.bubbleTextFor(bKey, self));

        // ---- 引用块 ----
        if (m.replyTo > 0) {
            vh.quoteBox.setVisibility(View.VISIBLE);
            vh.quoteName.setText(m.replyName == null || m.replyName.isEmpty()
                    ? "引用消息" : m.replyName);
            String t = m.replyText == null ? "" : m.replyText;
            if (t.isEmpty()) t = "[图片]";
            vh.quoteText.setText(t);
        } else {
            vh.quoteBox.setVisibility(View.GONE);
        }

        // ---- 内容：文本 / 图片 ----
        if ("image".equals(m.type)) {
            vh.body.setVisibility(View.GONE);
            vh.img.setVisibility(View.GONE);
            vh.imgLoading.setVisibility(View.VISIBLE);
            vh.img.setImageDrawable(null);
            loadImg(vh, m.content);
        } else {
            vh.img.setVisibility(View.GONE);
            vh.imgLoading.setVisibility(View.GONE);
            vh.body.setVisibility(View.VISIBLE);
            vh.body.setText(m.content);
        }

        // ---- 头像：自己也显示（放右侧）----
        ImageView av = self ? vh.avRight : vh.avLeft;
        loadAvatar(av, m.username, m.avatar);

        // ---- 名称：用发送者的个性化颜色 ----
        vh.name.setText(m.username);
        ThemeEngine.applyNameColorFor(vh.name, m.namecolor);

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

    /** 加载消息图片：命中缓存直接显示，否则先显示「图片加载中…」 */
    private void loadImg(final ViewHolder vh, final String content) {
        final String url = Api.absUrl(content);
        if (url.isEmpty()) { vh.imgLoading.setVisibility(View.GONE); return; }

        Bitmap cached = ImgLoader.cached(url);
        if (cached != null) {
            vh.imgLoading.setVisibility(View.GONE);
            vh.img.setVisibility(View.VISIBLE);
            vh.img.setImageBitmap(cached);
            bindImageClick(vh, content);
            return;
        }

        vh.imgLoading.setVisibility(View.VISIBLE);
        vh.img.setVisibility(View.GONE);
        vh.img.setTag(url);

        new Thread(new Runnable() {
            public void run() {
                final Bitmap bm = ImgLoader.fetch(url, 640, 640);
                if (bm == null) return;
                ImgLoader.put(url, bm);
                vh.img.post(new Runnable() {
                    public void run() {
                        if (!url.equals(vh.img.getTag())) return;
                        vh.img.setImageBitmap(bm);
                        vh.imgLoading.setVisibility(View.GONE);
                        vh.img.setVisibility(View.VISIBLE);
                        bindImageClick(vh, content);
                    }
                });
            }
        }).start();
    }

    private void bindImageClick(final ViewHolder vh, final String content) {
        vh.img.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (imageClick != null) imageClick.onImage(content);
            }
        });
    }

    private void loadAvatar(final ImageView iv, final String name, final String url) {
        if (iv == null) return;
        if (url != null && !url.isEmpty()) {
            loadImgSimple(iv, Api.absUrl(url), new Avatars(name), 96, 96);
        } else {
            iv.setImageDrawable(new Avatars(name));
        }
    }

    private void loadImgSimple(final ImageView iv, final String url,
                               final Drawable placeholder, final int w, final int h) {
        if (iv == null) return;
        if (url == null || url.isEmpty()) { iv.setImageDrawable(placeholder); return; }
        Bitmap cached = ImgLoader.cached(url);
        if (cached != null) { iv.setImageBitmap(cached); return; }
        if (placeholder != null) iv.setImageDrawable(placeholder);
        iv.setTag(url);
        new Thread(new Runnable() {
            public void run() {
                try {
                    final Bitmap bm = ImgLoader.fetch(url, w, h);
                    if (bm == null) return;
                    ImgLoader.put(url, bm);
                    iv.post(new Runnable() {
                        public void run() {
                            if (url.equals(iv.getTag())) iv.setImageBitmap(bm);
                        }
                    });
                } catch (Throwable ignore) {
                    // 单张图失败不影响列表
                }
            }
        }).start();
    }

    static class ViewHolder {
        View root;
        LinearLayout inner, quoteBox;
        ImageView avLeft, avRight, img;
        TextView name, body, time, imgLoading, quoteName, quoteText;
    }
}
