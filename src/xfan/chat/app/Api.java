package xfan.chat.app;

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
    private static final int TIMEOUT = 15000;

    /** 最后一次实际请求的完整地址（失败时用于提示） */
    public static String lastUrl = "";
    /** 最后一次失败的详情：HTTP 码 / 异常 / 返回片段 */
    public static String lastError = "";

    // 服务器地址写死在源码里，登录页不再让用户输入
    public static final String SERVER = "https://ffurry.hyperphp.com/chat/appapi.php";

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
        String t = Prefs.token();
        if (t != null && !t.isEmpty()) {
            c.setRequestProperty("Cookie", "chat_token=" + t);
        }
        c.setRequestProperty("User-Agent", "xfan-chat-app");
        return c;
    }

    /** GET 请求，返回 JSONObject；失败返回 null */
    public static JSONObject get(String path) {
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
        }
    }

    /** POST JSON，返回 JSONObject；失败返回 null */
    public static JSONObject post(String path, JSONObject data) {
        try {
            lastUrl = abs(path);
            HttpURLConnection c = conn(lastUrl, "POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] payload = (data == null ? new JSONObject() : data).toString().getBytes("UTF-8");
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
                return null;
            }
        } catch (Exception e) {
            lastError = errText(e);
            Log.w(TAG, "POST " + path + " failed: " + e);
            return null;
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
