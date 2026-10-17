package xfan.chat.app;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class Api {
    private static final String TAG = "XFAN";
    // 轮询间隔 4 秒，超时必须更短，否则线程堆积、界面永远刷新不动
    private static final int TIMEOUT = 8000;

    /** 最后一次实际请求的完整地址（失败时用于提示） */
    public static String lastUrl = "";
    /** 最后一次失败的详情：HTTP 码 / 异常 / 返回片段 */
    public static String lastError = "";

    /**
     * 全站强制 GET。
     * 该主机上 POST 会被网关拦截（400 Bad Request / openresty），
     * 而 GET 实测可正常返回 JSON，所以所有请求一律走 GET。
     * 副作用：参数（含密码）会出现在 URL 里，服务端 access log 可能记录。
     */
    public static final boolean USE_GET_ONLY = true;

    // true = 走 WebView 发请求（浏览器身份，可绕过 WAF 验证）
    // 需要服务器上同时存在 bridge.html
    public static boolean USE_WEBVIEW = false;

    // true = 用 http:// 而非 https://（部分网关只对 https 做验证）
    public static boolean USE_HTTP = false;

    // 服务器地址写死在源码里，登录页不再让用户输入
    public static String SERVER = "https://ffurry.hyperphp.com/chat/appapi.php";

    /** 应用启动时按 USE_HTTP 切换协议 */
    public static void applyHttpSwitch() {
        if (USE_HTTP && SERVER.startsWith("https://")) {
            SERVER = "http://" + SERVER.substring(8);
        }
    }

    /** 记一次失败原因，便于在界面上集中展示 */
    public static void noteError(String s) { lastError = s; }

    /**
     * 借用 WebView 完成一次浏览器验证，把拿到的 __test 回灌给直连。
     * 这样即使不走 WebView 通道，直连也能带上验证身份。
     */
    public static void syncWebCookies(Context c) {
        if (c == null) return;
        String has = Prefs.testCookie();
        if (has != null && !has.isEmpty()) return;   // 已拿到就不重复
        try {
            WebBridge wb = WebBridge.get(c.getApplicationContext());
            String raw = wb.grabCookie(base() + "/");
            if (raw == null || raw.isEmpty()) return;
            String test = pick(raw, "__test");
            if (!test.isEmpty()) {
                Prefs.testCookie(test);
                Log.d(TAG, "已从 WebView 同步 __test: " + test);
            }
        } catch (Throwable t) {
            Log.w(TAG, "同步 WebView cookie 失败: " + t);
        }
    }

    /** 从 "a=1; __test=xxx; b=2" 中取指定键 */
    private static String pick(String cookieHeader, String key) {
        String[] parts = cookieHeader.split(";");
        for (String p : parts) {
            String t = p.trim();
            int i = t.indexOf('=');
            if (i <= 0) continue;
            if (t.substring(0, i).trim().equals(key)) return t.substring(i + 1).trim();
        }
        return "";
    }

    private static int webFail = 0;

    /** 并发闸门：在途请求上限，防止轮询把线程池堆满 */
    private static final java.util.concurrent.Semaphore GATE =
            new java.util.concurrent.Semaphore(3);

    private static boolean gate() {
        try { return GATE.tryAcquire(6, java.util.concurrent.TimeUnit.SECONDS); }
        catch (Exception e) { return false; }
    }

    private static void gateRelease() { GATE.release(); }

    private static Context ctxRef;

    public static void bindContext(Context c) { ctxRef = c; }

    public static String base() {
        String s = SERVER;
        if (s == null) return "";
        s = s.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        // 自动补协议，避免只填域名导致 UnknownHost
        if (!s.isEmpty() && !s.contains("://")) s = "http://" + s;
        // 容错：用户把具体文件名也填进来了（如 .../chat/appapi.php），
        // 后面拼接会变成 .../appapi.php/appapi.php，这里先剥掉文件名
        int slash = s.lastIndexOf('/');
        if (slash > 8) {
            String tail = s.substring(slash + 1);
            if (tail.endsWith(".php") || tail.endsWith(".html") || tail.endsWith(".htm")) {
                s = s.substring(0, slash);
            }
        }
        return s;
    }

    public static String abs(String rel) {
        String b = base();
        if (b.isEmpty()) return "";
        if (rel.startsWith("http")) return rel;
        if (!rel.startsWith("/")) rel = "/" + rel;
        return b + rel;
    }

    /** 相对路径的图片地址转绝对地址 */
    public static String absUrl(String p) {
        if (p == null || p.isEmpty()) return "";
        if (p.startsWith("http")) return p;
        return abs(p);
    }

    private static HttpURLConnection conn(String urlStr, String method) throws Exception {
        URL u = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(TIMEOUT);
        c.setReadTimeout(TIMEOUT);
        c.setUseCaches(false);
        // Cookie 拼装：验证 cookie(__test) + 会话 cookie(chat_token)
        StringBuilder ck = new StringBuilder();
        String tv = Prefs.testCookie();
        if (tv != null && !tv.isEmpty()) ck.append("__test=").append(tv);
        String t = Prefs.token();
        if (t != null && !t.isEmpty()) {
            if (ck.length() > 0) ck.append("; ");
            ck.append("chat_token=").append(t);
        }
        if (ck.length() > 0) c.setRequestProperty("Cookie", ck.toString());
        c.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        c.setRequestProperty("Accept",
                "application/json, text/plain, */*");
        c.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
        // 带 Referer 与 X-Requested-With，更接近网页表单发起的请求
        String host = u.getProtocol() + "://" + u.getHost();
        c.setRequestProperty("Referer", host + "/");
        c.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        return c;
    }

    /** GET 请求，返回 JSONObject；失败返回 null */
    public static JSONObject get(String path) {
        if (USE_WEBVIEW) {
            lastUrl = abs(path);
            String raw = null;
            try { raw = webGet(lastUrl); }
            catch (Throwable t) { raw = null; }
            if (raw != null) {
                try { return new JSONObject(raw); }
                catch (Exception je) {
                    lastError = "WebView 返回非 JSON：\n"
                            + (raw.length() > 200 ? raw.substring(0, 200) + "…" : raw);
                }
            } else {
                lastError = "WebView 无响应（桥未就绪/bridge.html 缺失/超时）";
            }
            // 无论哪种失败都回退直连，绝不把请求卡死在桥上
            webFail++;
            if (webFail >= 3) { USE_WEBVIEW = false; }
            Log.w(TAG, "WebView 通道失败（第" + webFail + "次），回退直连");
        }
        if (!gate()) { lastError = "请求过多已排队，稍后重试"; return null; }
        try {
            lastUrl = abs(path);
            HttpURLConnection c = conn(lastUrl, "GET");
            int code = c.getResponseCode();
            String body = read(code < 400 ? c.getInputStream() : c.getErrorStream());
            c.disconnect();
            Log.d(TAG, "GET " + path + " -> " + body);
            try { return new JSONObject(body); }
            catch (Exception je) {
                lastError = "HTTP " + code + "\n返回不是 JSON：\n"
                        + (body.length() > 200 ? body.substring(0, 200) + "…" : body);
                return null;
            }
        } catch (Exception e) {
            lastError = errText(e);
            Log.w(TAG, "GET " + path + " failed: " + e);
            return null;
        } finally {
            gateRelease();
        }
    }

    /** POST JSON，返回 JSONObject；失败返回 null */
    public static JSONObject post(String path, JSONObject data) {
        if (USE_GET_ONLY) {
            String q = toForm(data);
            return get(q.isEmpty() ? path : path + (path.contains("?") ? "&" : "?") + q);
        }
        try {
            lastUrl = abs(path);
            HttpURLConnection c = conn(lastUrl, "POST");
            c.setDoOutput(true);
            // 优先用表单编码：部分网关会拦截 Content-Type: application/json 的 POST
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            byte[] payload = toForm(data).getBytes("UTF-8");
            OutputStream os = c.getOutputStream();
            os.write(payload);
            os.flush();
            os.close();
            int code = c.getResponseCode();
            String body = read(code < 400 ? c.getInputStream() : c.getErrorStream());
            c.disconnect();
            Log.d(TAG, "POST " + path + " -> " + body);
            try { return new JSONObject(body); }
            catch (Exception je) {
                lastError = "HTTP " + code + "\n返回不是 JSON：\n"
                        + (body.length() > 200 ? body.substring(0, 200) + "…" : body);
                // 网关拦了 POST，降级用 GET 重发一次（参数放 query）
                Log.w(TAG, "POST 被拦截，降级为 GET 重试");
                return get(path + (path.contains("?") ? "&" : "?") + toForm(data));
            }
        } catch (Exception e) {
            lastError = errText(e);
            Log.w(TAG, "POST " + path + " failed: " + e);
            return get(path + (path.contains("?") ? "&" : "?") + toForm(data));
        }
    }

    /** multipart 上传（表单字段 file + type），额外字段用 extra */
    public static JSONObject upload(String path, File file, String type, Map<String, String> extra) {
        final String BOUND = "----XFAN" + System.currentTimeMillis();
        try {
            HttpURLConnection c = conn(abs(path), "POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUND);

            DataOutputStream dos = new DataOutputStream(c.getOutputStream());
            // type 字段
            if (type != null) {
                dos.writeBytes("--" + BOUND + "\r\n");
                dos.writeBytes("Content-Disposition: form-data; name=\"type\"\r\n\r\n");
                dos.writeBytes(type + "\r\n");
            }
            if (extra != null) {
                for (Map.Entry<String, String> e : extra.entrySet()) {
                    dos.writeBytes("--" + BOUND + "\r\n");
                    dos.writeBytes("Content-Disposition: form-data; name=\"" + e.getKey() + "\"\r\n\r\n");
                    dos.writeBytes(e.getValue() + "\r\n");
                }
            }
            // 文件
            String field = "file";
            dos.writeBytes("--" + BOUND + "\r\n");
            dos.writeBytes("Content-Disposition: form-data; name=\"" + field
                    + "\"; filename=\"" + file.getName() + "\"\r\n");
            dos.writeBytes("Content-Type: application/octet-stream\r\n\r\n");
            FileInputStream fis = new FileInputStream(file);
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) > 0) dos.write(buf, 0, n);
            fis.close();
            dos.writeBytes("\r\n--" + BOUND + "--\r\n");
            dos.flush();
            dos.close();

            int code = c.getResponseCode();
            String body = read(code < 400 ? c.getInputStream() : c.getErrorStream());
            c.disconnect();
            Log.d(TAG, "UPLOAD " + path + " -> " + body);
            return new JSONObject(body);
        } catch (Exception e) {
            Log.w(TAG, "UPLOAD " + path + " failed: " + e);
            return null;
        }
    }

    /** 通过 WebView 取原始文本 */
    private static String webGet(String url) {
        if (ctxRef == null) return null;
        try {
            return WebBridge.get(ctxRef).get(url);
        } catch (Exception e) {
            Log.w(TAG, "WebView 通道异常: " + e);
            return null;
        }
    }

    /** JSONObject -> a=1&b=2 形式，key/value 做 URL 编码 */
    private static String toForm(JSONObject o) {
        if (o == null) return "";
        StringBuilder sb = new StringBuilder();
        java.util.Iterator<String> it = o.keys();
        boolean first = true;
        while (it.hasNext()) {
            String k = it.next();
            Object v = o.opt(k);
            if (v == null) continue;
            if (!first) sb.append('&');
            first = false;
            sb.append(enc(k)).append('=').append(enc(String.valueOf(v)));
        }
        return sb.toString();
    }

    private static String enc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }

    private static String errText(Exception e) {
        String n = e.getClass().getSimpleName();
        String m = e.getMessage() == null ? "" : e.getMessage();
        if (e instanceof java.net.UnknownHostException)  return "域名解析失败（UnknownHost）\n" + m;
        if (e instanceof java.net.ConnectException)      return "连接被拒绝（ConnectException）\n" + m;
        if (e instanceof java.net.SocketTimeoutException) return "连接超时（Timeout）\n" + m;
        if (e instanceof javax.net.ssl.SSLException)     return "SSL/HTTPS 错误\n" + m;
        if (e instanceof java.io.FileNotFoundException)  return "HTTP 404，文件不存在\n" + m;
        return n + "\n" + m;
    }

    private static String read(InputStream in) {
        if (in == null) return "";
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            in.close();
            return bo.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
