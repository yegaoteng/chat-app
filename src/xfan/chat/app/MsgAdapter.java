package xfan.chat.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MsgAdapter extends BaseAdapter {

    private final Context ctx;
    private final List<Models.Msg> data;
    private final LayoutInflater inf;
    private final Map<String, Bitmap> cache = new HashMap<String, Bitmap>();
    private final Handler h = new Handler();

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
            vh.root  = cv.findViewById(R.id.rowRoot);
            vh.inner = cv.findViewById(R.id.rowInner);
            vh.av    = cv.findViewById(R.id.ivAv);
            vh.name  = cv.findViewById(R.id.tvName);
            vh.body  = cv.findViewById(R.id.tvBody);
            vh.img   = cv.findViewById(R.id.ivImg);
            vh.time  = cv.findViewById(R.id.tvTime);
            cv.setTag(vh);
        } else {
            vh = (ViewHolder) cv.getTag();
        }

        final Models.Msg m = data.get(pos);
        String me = Prefs.user();
        boolean self = m.username.equals(me);

        vh.img.setVisibility(View.GONE);
        vh.body.setVisibility(View.VISIBLE);

        if ("image".equals(m.type)) {
            vh.body.setVisibility(View.GONE);
            vh.img.setVisibility(View.VISIBLE);
            loadImg(vh.img, Api.absUrl(m.content));
        } else {
            vh.body.setText(m.content);
            vh.body.setBackgroundResource(self ? R.drawable.bg_bubble_self : R.drawable.bg_bubble_other);
            vh.body.setTextColor(self ? 0xFFFFFFFF : 0xFF222222);
            // 自己的消息靠右
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.gravity = self ? Gravity.END : Gravity.START;
            lp.topMargin = 6;
            vh.body.setLayoutParams(lp);
        }

        if (self) {
            vh.av.setVisibility(View.GONE);
            vh.name.setVisibility(View.GONE);
        } else {
            vh.av.setVisibility(View.VISIBLE);
            vh.name.setVisibility(View.VISIBLE);
            vh.name.setText(m.username);
            loadAvatar(vh.av, m.username, m.avatar);
        }

        // 时间：与上一条间隔超过 5 分钟才显示
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

    private void loadAvatar(final ImageView iv, final String name, final String url) {
        iv.setImageDrawable(null);
        if (url != null && !url.isEmpty()) {
            loadImg(iv, Api.absUrl(url));
        } else {
            // 无头像：用首字 + 用户名哈希色
            int hue = Math.abs(name.hashCode()) % 360;
            android.graphics.drawable.ShapeDrawable sd = new android.graphics.drawable.ShapeDrawable(
                    new android.graphics.drawable.shapes.OvalShape());
            sd.getPaint().setColor(android.graphics.Color.HSVToColor(new float[]{hue, 0.55f, 0.85f}));
            iv.setBackgroundDrawable(sd);
            iv.setScaleType(ImageView.ScaleType.CENTER);
            iv.setImageDrawable(null);
        }
    }

    private void loadImg(final ImageView iv, final String url) {
        iv.setImageDrawable(null);
        iv.setTag(url);
        Bitmap b = cache.get(url);
        if (b != null) { iv.setImageBitmap(b); return; }
        new Thread(new Runnable() {
            public void run() {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    c.setDoInput(true);
                    c.connect();
                    InputStream in = c.getInputStream();
                    final Bitmap bm = BitmapFactory.decodeStream(in);
                    in.close();
                    if (bm != null) {
                        cache.put(url, bm);
                        h.post(new Runnable() {
                            public void run() {
                                if (url.equals(iv.getTag())) iv.setImageBitmap(bm);
                            }
                        });
                    }
                } catch (Exception ignore) {}
            }
        }).start();
    }

    static class ViewHolder {
        View root;
        LinearLayout inner;
        ImageView av, img;
        TextView name, body, time;
    }
}
