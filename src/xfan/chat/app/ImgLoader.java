package xfan.chat.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 图片加载（内存缓存 + 降采样 + 浏览器请求头）。 */
public class ImgLoader {
    /** 必须绑定主线程 Looper。 */
    private static final Handler h = new Handler(android.os.Looper.getMainLooper());

    /** 磁盘缓存目录 + 24 小时有效期（切频道/私聊后已加载过的图直接本地取） */
    private static java.io.File diskDir = null;
    private static final long DISK_TTL = 24L * 60 * 60 * 1000;

    public static void init(Context c) {
        if (c == null) return;
        try {
            java.io.File d = new java.io.File(c.getCacheDir(), "imgcache");
            if (!d.exists()) d.mkdirs();
            diskDir = d;
        } catch (Throwable ignore) {}
    }

    /** 缓存键必须带上目标尺寸。 */
    private static java.io.File diskFile(String url, int maxW, int maxH) {
        if (diskDir == null || url == null) return null;
        String k;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] b = md.digest((url + "|" + maxW + "x" + maxH).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            k = sb.toString();
        } catch (Exception e) {
            k = String.valueOf((url + "|" + maxW + "x" + maxH).hashCode());
        }
        return new java.io.File(diskDir, k);
    }

    /** 兼容旧调用：无尺寸信息时用 0x0 作为桶 */
    private static java.io.File diskFile(String url) {
        return diskFile(url, 0, 0);
    }

    /** 清掉超过 24 小时的缓存文件 */
    private static void trimDisk() {
        if (diskDir == null) return;
        try {
            long now = System.currentTimeMillis();
            java.io.File[] fs = diskDir.listFiles();
            if (fs == null) return;
            for (java.io.File f : fs) {
                if (now - f.lastModified() > DISK_TTL) { try { f.delete(); } catch (Throwable ignore) {} }
            }
        } catch (Throwable ignore) {}
    }

    /** 缓存上限：取可用内存的 1/8，最小 8MB */
    private static final int MAX_CACHE;
    private static final LruCache<String, Bitmap> cache;

    static {
        long m = Runtime.getRuntime().maxMemory();
        MAX_CACHE = (int) Math.max(8L * 1024 * 1024, m / 8);
        cache = new LruCache<String, Bitmap>(MAX_CACHE) {
            @Override
            protected int sizeOf(String k, Bitmap v) {
                return v == null ? 0 : v.getByteCount();
            }
        };
    }

    private final ImageView iv;

    public ImgLoader(ImageView v) { iv = v; }

    public void load(String url) { load(url, 720, 720); }

    public void load(String url, int maxW, int maxH) {
        if (iv == null || url == null || url.isEmpty()) return;
        iv.setImageDrawable(null);
        iv.setTag(url);
        Bitmap b = cached(url);
        if (b != null) { iv.setImageBitmap(b); return; }

        new Thread(new Runnable() {
            public void run() {
                final String u = url;
                try {
                    Bitmap bm = fetch(u, maxW, maxH);
                    if (bm == null) return;
                    put(u, bm);
                    h.post(new Runnable() {
                        public void run() {
                            if (u.equals(iv.getTag())) iv.setImageBitmap(bm);
                        }
                    });
                } catch (Throwable ignore) {
                    // OOM 也要吞掉：不能让一张图把整个 App 拖崩
                }
            }
        }).start();
    }

    // ---------- 核心：带降采样的下载 ----------

    /** 按默认上限（720px）取图 */
    public static Bitmap fetch(String url) { return fetch(url, 720, 720); }

    /**
     * 下载并按 maxW/maxH 降采样解码。
     * 失败返回 null（含 OOM、网络错误、被 WAF 拦截等情况）。
     */
    public static Bitmap fetch(String url, int maxW, int maxH) {
        if (url == null || url.isEmpty()) return null;

        // 先查磁盘缓存（24 小时内有效）
        java.io.File df = diskFile(url, maxW, maxH);
        if (df != null && df.exists()
                && System.currentTimeMillis() - df.lastModified() < DISK_TTL) {
            Bitmap d = null;
            try {
                d = BitmapFactory.decodeFile(df.getAbsolutePath());
            } catch (Throwable ignore) {
                d = null;
            }
            if (d != null) { put(url, d); return d; }
        }

        HttpURLConnection c = null;
        try {
            c = open(url);

            // 第一步：只读尺寸
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inJustDecodeBounds = true;
            InputStream in0 = c.getInputStream();
            BitmapFactory.decodeStream(in0, null, opt);
            try { in0.close(); } catch (Throwable ignore) {}
            c.disconnect();

            int w = opt.outWidth, hh = opt.outHeight;
            if (w <= 0 || hh <= 0) return null;

            // 第二步：按目标尺寸算采样率后真正解码
            opt = new BitmapFactory.Options();
            opt.inSampleSize = sampleSize(w, hh, maxW, maxH);
            // 大图（全屏预览）用 ARGB_8888 保真；小图用 RGB_565 省内存
            opt.inPreferredConfig = (maxW >= 720 && maxH >= 720)
                    ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
            opt.inJustDecodeBounds = false;

            c = open(url);
            InputStream in = c.getInputStream();
            Bitmap bm = BitmapFactory.decodeStream(in, null, opt);
            try { in.close(); } catch (Throwable ignore) {}
            if (bm != null && df != null) {
                try {
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(df);
                    bm.compress(Bitmap.CompressFormat.PNG, 90, fos);
                    fos.flush();
                    fos.close();
                } catch (Throwable ignore) {}
                trimDisk();
            }
            return bm;
        } catch (OutOfMemoryError oom) {
            cache.evictAll();          // 内存吃紧时先清空缓存，后续图片还能救回来
            return null;
        } catch (Throwable e) {
            return null;
        } finally {
            if (c != null) { try { c.disconnect(); } catch (Throwable ignore) {} }
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
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
        if (code >= 400) throw new java.io.IOException("HTTP " + code);
        String ct = c.getHeaderField("Content-Type");
        // 返回 HTML 说明被网关挡了，不是图片
        if (ct != null && ct.contains("text/html")) throw new java.io.IOException("blocked");
        return c;
    }

    /** 计算 inSampleSize（取 2 的幂，保证解码效率） */
    private static int sampleSize(int w, int h, int maxW, int maxH) {
        int s = 1;
        while ((w / s) > maxW || (h / s) > maxH) {
            s <<= 1;
            if (s > 64) break;      // 兜底，避免极端尺寸死循环
        }
        return s;
    }

    // ---------- 缓存 ----------
    public static Bitmap cached(String url) {
        if (url == null) return null;
        try { return cache.get(url); } catch (Throwable e) { return null; }
    }

    public static void put(String url, Bitmap b) {
        if (url == null || b == null) return;
        try { cache.put(url, b); } catch (Throwable ignore) {}
    }
}
