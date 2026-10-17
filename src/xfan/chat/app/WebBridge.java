package xfan.chat.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * WebView 网络层。
 *
 * 服务器前面的 WAF 只放行"看起来像浏览器"的请求。
 * WebView 本身就是浏览器：它会自行完成验证并维护 cookie，
 * 页面内的 fetch 是同源请求、cookie 自动带上，因此不会被拦截。
 *
 * 用法：所有数据请求改走这里，Java 通过 evaluateJavascript 触发 fetch，
 * 结果由 JS 通过 XfanBridge 回调回来。
 */
public class WebBridge {
    private static final String TAG = "XFAN-WEB";

    private static WebBridge inst;
    private WebView wv;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean ready = false;
    private volatile boolean failed = false;
    private volatile String cookies = "";

    /** 等待中的请求：id -> 结果容器 */
    private final Map<String, Res> pending = new ConcurrentHashMap<String, Res>();
    private int seq = 0;

    private static final class Res {
        final CountDownLatch latch = new CountDownLatch(1);
        String body;
        int status;
        String err;
    }

    public static synchronized WebBridge get(Context c) {
        if (inst == null) inst = new WebBridge(c.getApplicationContext());
        return inst;
    }

    private WebBridge(Context ctx) {
        main.post(new Runnable() {
            @SuppressLint("SetJavaScriptEnabled")
            public void run() {
                try {
                    wv = new WebView(ctx);
                    WebSettings s = wv.getSettings();
                    s.setJavaScriptEnabled(true);
                    s.setDomStorageEnabled(true);
                    s.setCacheMode(WebSettings.LOAD_NO_CACHE);
                    s.setUserAgentString(
                            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 "
                                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                    wv.addJavascriptInterface(new JsApi(), "XfanBridge");
                    wv.setWebViewClient(new WebViewClient() {
                        public void onPageFinished(WebView v, String url) {
                            // 页面自己回调 onPageReady 才算就绪；
                            // 若 bridge.html 缺失（404 页），不会回调，进入 failed
                            main.postDelayed(new Runnable() {
                                public void run() {
                                    if (!ready) markFailed();
                                }
                            }, 5000);
                        }
                    });
                    CookieManager cm = CookieManager.getInstance();
                    cm.setAcceptCookie(true);
                    wv.loadUrl(Api.base() + "/bridge.html");
                } catch (Exception e) {
                    Log.w(TAG, "WebView 初始化失败: " + e);
                }
            }
        });
    }

    private final class JsApi {
        @JavascriptInterface
        public void onResult(String id, String status, String body) {
            Res r = pending.get(id);
            if (r == null) return;
            try { r.status = Integer.parseInt(status); } catch (Exception e) { r.status = 0; }
            r.body = body;
            r.latch.countDown();
        }

        @JavascriptInterface
        public void onError(String id, String err) {
            Res r = pending.get(id);
            if (r == null) return;
            r.err = err;
            r.latch.countDown();
        }

        @JavascriptInterface
        public void onCookies(String c) { cookies = c; }

        @JavascriptInterface
        public void onPageReady(String c) {
            cookies = c;
            ready = true;
        }
    }

    private void markFailed() { failed = true; }

    /** 阻塞等待 WebView 就绪；已判定不可用则立即返回 false，避免每次请求都空等 */
    public boolean waitReady() {
        if (ready) return true;
        if (failed) return false;
        for (int i = 0; i < 100 && !ready && !failed; i++) {
            try { Thread.sleep(100); } catch (Exception ignore) { break; }
        }
        return ready;
    }

    public boolean isReady() { return ready; }
    public String cookies() { return cookies; }

    /** 从 WebView cookie jar 取指定地址的 cookie（含 __test 验证 cookie） */
    public String grabCookie(final String url) {
        if (!waitReady()) return "";
        final String[] out = new String[1];
        out[0] = "";
        final CountDownLatch l = new CountDownLatch(1);
        main.post(new Runnable() {
            public void run() {
                try {
                    String c = CookieManager.getInstance().getCookie(url);
                    out[0] = c == null ? "" : c;
                } catch (Exception e) { out[0] = ""; }
                l.countDown();
            }
        });
        try { l.await(3, TimeUnit.SECONDS); } catch (Exception ignore) {}
        return out[0];
    }

    /** 刷新 cookies 快照（用于排查） */
    public void fetchCookies() {
        if (!ready) return;
        main.post(new Runnable() {
            public void run() {
                eval("__cookies()");
            }
        });
    }

    private void eval(String js) {
        if (wv == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                wv.evaluateJavascript(js, null);
            } else {
                wv.loadUrl("javascript:" + js);
            }
        } catch (Exception e) {
            Log.w(TAG, "eval 失败: " + e);
        }
    }

    /** 用 base64 传递参数，避免引号/中文/特殊字符破坏 JS 字符串 */
    private static String b64(String s) {
        return Base64.encodeToString(s.getBytes(), Base64.NO_WRAP | Base64.URL_SAFE);
    }

    /** 走 WebView 发起 GET；失败返回 null */
    public String get(final String url) {
        if (!waitReady()) return null;
        final String id = "r" + (++seq);
        final Res r = new Res();
        pending.put(id, r);

        final String js = "__f('" + id + "', decodeURIComponent(escape(atob('"
                + b64(url) + "'))), null)";

        main.post(new Runnable() {
            public void run() { eval(js); }
        });

        try {
            if (!r.latch.await(20, TimeUnit.SECONDS)) {
                pending.remove(id);
                return null;
            }
        } catch (Exception e) {
            pending.remove(id);
            return null;
        }
        pending.remove(id);
        return r.err == null ? r.body : null;
    }
}
