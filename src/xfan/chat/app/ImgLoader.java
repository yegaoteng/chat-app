package xfan.chat.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/** 简易图片加载（带内存缓存） */
public class ImgLoader {
    private static final Map<String, Bitmap> cache = new HashMap<String, Bitmap>();
    private static final Handler h = new Handler();

    private final ImageView iv;

    public ImgLoader(ImageView v) { iv = v; }

    public void load(String url) {
        if (iv == null || url == null || url.isEmpty()) return;
        iv.setImageDrawable(null);
        iv.setTag(url);
        Bitmap b = cache.get(url);
        if (b != null) { iv.setImageBitmap(b); return; }
        new Thread(new Runnable() {
            public void run() {
                final String u = (String) iv.getTag();
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    c.connect();
                    InputStream in = c.getInputStream();
                    final Bitmap bm = BitmapFactory.decodeStream(in);
                    in.close();
                    if (bm != null) {
                        cache.put(u, bm);
                        h.post(new Runnable() {
                            public void run() {
                                if (u.equals(iv.getTag())) iv.setImageBitmap(bm);
                            }
                        });
                    }
                } catch (Exception ignore) {}
            }
        }).start();
    }
}
