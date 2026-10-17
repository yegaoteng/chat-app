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

    public static String user() { return get("user", ""); }
    public static void user(String v) { put("user", v); }

    public static String pass() { return get("pass", ""); }
    public static void pass(String v) { put("pass", v); }

    public static String theme() { return get("theme", "default"); }
    public static void theme(String v) { put("theme", v); }

    public static String bubble() { return get("bubble", "default"); }
    public static void bubble(String v) { put("bubble", v); }

    public static String namecolor() { return get("namecolor", "default"); }
    public static void namecolor(String v) { put("namecolor", v); }

    public static void clearSession() {
        put("token", "");
        put("pass", "");
    }
}
