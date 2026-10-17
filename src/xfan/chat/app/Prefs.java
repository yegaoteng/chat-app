package xfan.chat.app;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private static final String N = "xfan_chat";
    private static SharedPreferences sp;

    public static void init(Context c) {
        sp = c.getSharedPreferences(N, Context.MODE_PRIVATE);
    }

    public static String get(String k, String def) {
        return sp == null ? def : sp.getString(k, def);
    }

    public static void put(String k, String v) {
        if (sp != null) sp.edit().putString(k, v).apply();
    }

    public static String server() { return get("server", ""); }
    public static void server(String v) { put("server", v); }

    public static String token() { return get("token", ""); }
    public static void token(String v) { put("token", v); }

    // 服务器浏览器验证下发的 __test cookie。
    // 直连会被网关拦截时，带上它可复用浏览器已通过的验证身份。
    public static String testCookie() { return get("__test", ""); }
    public static void testCookie(String v) { put("__test", v); }

    public static String user() { return get("user", ""); }
    public static void user(String v) { put("user", v); }

    public static String pass() { return get("pass", ""); }
    public static void pass(String v) { put("pass", v); }

    /** 自己的 UID（登录时服务器下发，我的页等处展示） */
    public static String uid() { return get("uid", ""); }
    public static void uid(String v) { put("uid", v); }

    public static String theme() { return get("theme", "default"); }
    public static void theme(String v) { put("theme", v); }

    public static String bubble() { return get("bubble", "default"); }
    public static void bubble(String v) { put("bubble", v); }

    public static String namecolor() { return get("namecolor", "default"); }
    public static void namecolor(String v) { put("namecolor", v); }

    public static void clearSession() {
        put("token", "");
        put("pass", "");
        put("__test", "");
    }
}
