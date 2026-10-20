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

    public static int points() {
        try { return Integer.parseInt(get("points", "0")); } catch (Exception e) { return 0; }
    }
    public static void points(int v) { put("points", String.valueOf(v)); }

    /** 目标帧率，0 表示跟随系统 */
    public static int fps() {
        try { return Integer.parseInt(get("fps", "0")); } catch (Exception e) { return 0; }
    }
    public static void fps(int v) { put("fps", String.valueOf(v)); }

    public static String theme() { return get("theme", "default"); }
    public static void theme(String v) { put("theme", v); }

    public static String bubble() { return get("bubble", "default"); }
    public static void bubble(String v) { put("bubble", v); }

    public static String namecolor() { return get("namecolor", "default"); }
    public static void namecolor(String v) { put("namecolor", v); }

    /** 个性化数据缓存：避免每次打开侧边栏都重新拉取 */
    public static String stylesJson() { return get("styles_json", ""); }
    public static void stylesJson(String v) { put("styles_json", v); }

    /** 个性化数据缓存时间（毫秒） */
    public static long stylesTs() {
        try { return Long.parseLong(get("styles_ts", "0")); } catch (Exception e) { return 0L; }
    }
    public static void stylesTs(long v) { put("styles_ts", String.valueOf(v)); }

    /** 手动兑换/购买个性化后调用：下一侧边栏强制刷新 */
    public static void invalidateStyles() { put("styles_ts", "0"); }

    // ---- 好友 / 频道 24 小时缓存 ----
    // 每次进页面都全量重拉会很慢，而且列表会闪一下。
    // 缓存 24 小时，只有好友在线状态发生变化时才真正刷新列表。

    public static String friendsJson() { return get("friends_json", ""); }

    public static void friendsJson(String v) { put("friends_json", v); }

    public static long friendsTs() {
        try { return Long.parseLong(get("friends_ts", "0")); } catch (Exception e) { return 0; }
    }

    public static void friendsTs(long v) { put("friends_ts", String.valueOf(v)); }

    public static String chansJson() { return get("chans_json", ""); }

    public static void chansJson(String v) { put("chans_json", v); }

    public static long chansTs() {
        try { return Long.parseLong(get("chans_ts", "0")); } catch (Exception e) { return 0; }
    }

    public static void chansTs(long v) { put("chans_ts", String.valueOf(v)); }

    /** 手动操作（加好友、退频道、换风格等）后调用，强制下次重新拉取 */
    public static void invalidateLists() {
        put("friends_ts", "0");
        put("chans_ts", "0");
    }

    /** 会话免打扰：接收消息但不发通知。key = mute_<会话标识> */
    public static boolean muted(String target) {
        return "1".equals(get("mute_" + target, "0"));
    }
    public static void muted(String target, boolean on) {
        put("mute_" + target, on ? "1" : "0");
    }

    public static void clearSession() {
        put("token", "");
        put("pass", "");
        put("__test", "");
    }
}
