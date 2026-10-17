package xfan.chat.app;

import org.json.JSONObject;

public class Models {

    public static class Msg {
        public String username = "";
        public String content = "";
        public String type = "text";
        public long timestamp = 0;
        public String avatar = "";
        public int dup = 0;
        public int id = 0;          // 私聊消息 id
        public String badge = "";
        public boolean recalled;
        public String uid = "";       // 发送者 UID（稳定身份）
        public String bubble = "";    // 发送者个性化气泡（服务端按发送者回填）
        public String namecolor = ""; // 发送者个性化名称颜色
        public long replyTo = 0;      // 引用的消息 id
        public String replyName = ""; // 被引用者昵称（本地展示用）
        public String replyText = ""; // 被引用内容摘要

        public String key() {
            if (id > 0) return "p_" + id;
            return "w_" + timestamp + "_" + username + "_" + content.hashCode() + "_" + dup;
        }
    }

    public static class Friend {
        public String username = "";
        public String avatar = "";
        public String namecolor = "default";
        public boolean online;
        public String last = "";
        public long lastTs;
        public int unread;
        public boolean pinned;
    }

    public static class Channel {
        public String code = "";
        public String name = "";
        public String desc = "";
        public long lastTs;
        public int unread;
        public boolean pinned;
    }

    public static class Req {
        public int id;
        public String username = "";
        public String avatar = "";
        public String reason = "";
    }

    public static class Upload {
        public String name = "";
        public String url = "";
        public long time;
        public int cost;
        public int refund;
    }

    public static class StyleItem {
        public String key = "";
        public String name = "";
        public String desc = "";
        public int price;
        public boolean owned;

        public static StyleItem from(JSONObject o) {
            StyleItem s = new StyleItem();
            s.key = o.optString("key", "");
            s.name = o.optString("name", "");
            s.desc = o.optString("desc", "");
            s.price = o.optInt("price", 0);
            s.owned = o.optBoolean("owned", false);
            return s;
        }
    }
}
