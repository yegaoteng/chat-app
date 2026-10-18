package xfan.chat.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/** 用户资料卡：点头像 / 点昵称时弹出。 */
public class UserInfoDialog {

    /** 弹出资料卡。host 用于提供 Activity 与后续操作入口。 */
    public static void show(final ChatFragment host, final String username) {
        if (host == null || username == null || username.isEmpty()) return;
        if (host.getActivity() == null) return;

        host.loadUserInfo(username, new ChatFragment.UserInfoCb() {
            public void onDone(final JSONObject d) {
                if (d == null) {
                    host.toast("读取资料失败");
                    return;
                }
                if (host.getActivity() == null) return;
                render(host, d);
            }
        });
    }

    private static void render(final ChatFragment host, final JSONObject d) {
        final String name   = d.optString("username", "");
        final String uid    = d.optString("uid", "");
        final String avatar = d.optString("avatar", "");
        final String bio    = d.optString("bio", "");
        final String nc     = d.optString("namecolor", "default");
        final int    pts    = d.optInt("points", 0);
        final boolean online = d.optBoolean("online", false);
        final boolean isFriend = d.optBoolean("is_friend", false);
        final boolean isSelf = d.optBoolean("is_self", false);

        LinearLayout box = new LinearLayout(host.getActivity());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 26, 40, 10);
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        // ---- 头像（点击放大）----
        ImageView iv = new ImageView(host.getActivity());
        int size = (int) (110 * host.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.bottomMargin = 14;
        iv.setLayoutParams(lp);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (!avatar.isEmpty()) {
            new ImgLoader(iv).load(Api.absUrl(avatar), 400, 400);
        } else {
            iv.setImageDrawable(new Avatars(name));
        }
        iv.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!avatar.isEmpty()) host.openImage(avatar);
            }
        });

        // ---- 昵称（用对方自己的名称颜色）----
        TextView tvName = new TextView(host.getActivity());
        tvName.setText(name);
        tvName.setTextSize(17);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setGravity(Gravity.CENTER);
        ThemeEngine.applyNameColorFor(tvName, nc);

        // ---- UID ----
        TextView tvUid = new TextView(host.getActivity());
        tvUid.setText(uid.isEmpty() ? "UID：-" : ("UID：" + uid));
        tvUid.setTextSize(12);
        tvUid.setTextColor(ThemeEngine.themeTextSub());
        tvUid.setGravity(Gravity.CENTER);
        tvUid.setPadding(0, 4, 0, 0);

        // ---- 在线 / 积分 ----
        TextView tvMeta = new TextView(host.getActivity());
        tvMeta.setText((online ? "在线" : "离线") + " · " + pts + " 积分");
        tvMeta.setTextSize(12);
        tvMeta.setGravity(Gravity.CENTER);
        tvMeta.setPadding(0, 6, 0, 0);
        tvMeta.setTextColor(online ? 0xFF2E7D32 : ThemeEngine.themeTextSub());

        // ---- 简介 ----
        TextView tvBio = new TextView(host.getActivity());
        String b = bio;
        if (b == null || b.isEmpty() || b.equals("这个人很懒，没有留下简介")) b = "这个人很懒，没有留下简介";
        tvBio.setText(b);
        tvBio.setTextSize(13);
        tvBio.setTextColor(ThemeEngine.themeTextSub());
        tvBio.setGravity(Gravity.CENTER);
        tvBio.setPadding(0, 10, 0, 0);
        tvBio.setMaxLines(3);
        tvBio.setEllipsize(TextUtils.TruncateAt.END);

        box.addView(iv);
        box.addView(tvName);
        box.addView(tvUid);
        box.addView(tvMeta);
        box.addView(tvBio);

        AlertDialog.Builder bld = new AlertDialog.Builder(host.getActivity())
                .setTitle("资料")
                .setView(box)
                .setNegativeButton(R.string.btn_close, null);

        if (!isSelf) {
            bld.setPositiveButton(isFriend ? "发消息" : "申请好友",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dg, int w) {
                            if (isFriend) {
                                host.openPm(name);
                            } else {
                                host.sendFriendRequest(name);
                            }
                        }
                    });
        }

        bld.show();
    }
}
