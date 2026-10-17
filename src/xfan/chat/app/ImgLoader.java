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

/**
 * 图片加载（内存缓存 + 浏览器请求头）。
 *
 * 之前用裸 HttpURLConnection，默认 UA 是 Dalvik/...，
 * 被服务器前的 WAF 判定为非浏览器而拦截，导致图片和头像都加载不出来。
 * 这里与 Api 保持一致：伪装浏览器 UA + 带上验证 cookie。
 */
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
                final String u = url;
                try {
                    Bitmap bm = fetch(u);
                    if (bm == null) return;
                    cache.put(u, bm);
                    h.post(new Runnable() {
                        public void run() {
                            if (u.equals(iv.getTag())) iv.setImageBitmap(bm);
                        }
                    });
                } catch (Exception ignore) {}
            }
        }).start();
    }

    /** 带浏览器头与 cookie 拉取图片 */
    public static Bitmap fetch(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setDoInput(true);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            c.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
            c.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            c.setRequestProperty("Referer", Api.base() + "/");

            // 验证 cookie + 会话 cookie：没有它 WAF 会把请求挡在 PHP 之外
            StringBuilder ck = new StringBuilder();
            String tv = Prefs.testCookie();
            if (tv != null && !tv.isEmpty()) ck.append("__test=").append(tv);
            String t = Prefs.token();
            if (t != null && !t.isEmpty()) {
                if (ck.length() > 0) ck.append("; ");
                ck.append("chat_token=").append(t);
            }
            if (ck.length() > 0) c.setRequestProperty("Cookie", ck.toString());

            c.connect();
            int code = c.getResponseCode();
            if (code >= 400) { c.disconnect(); return null; }
            String ct = c.getHeaderField("Content-Type");
            if (ct != null && ct.contains("text/html")) { c.disconnect(); return null; }

            InputStream in = c.getInputStream();
            Bitmap bm = BitmapFactory.decodeStream(in);
            in.close();
            c.disconnect();
            return bm;
        } catch (Exception e) {
            return null;
        }
    }

    /** 供消息列表等处直接取缓存 */
    public static Bitmap cached(String url) {
        return url == null ? null : cache.get(url);
    }

    public static void put(String url, Bitmap b) {
        if (url != null && b != null) cache.put(url, b);
    }
}
