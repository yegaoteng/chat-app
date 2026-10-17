package xfan.chat.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private FrameLayout drawerRoot;
    private LinearLayout drawerPanel, drawerBody;
    private TextView tvTitle, tvPoints, tabChat, tabFriends, tabMe;
    private boolean drawerOpen = false;

    public ChatFragment fChat;
    public FriendsFragment fFriends;
    public MeFragment fMe;
    private int curTab = 0;   // 0 聊天 1 好友 2 我的

    public static MainActivity self;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        self = this;
        setContentView(R.layout.activity_main);

        drawerRoot  = findViewById(R.id.drawerRoot);
        drawerPanel = findViewById(R.id.drawerPanel);
        drawerBody  = findViewById(R.id.drawerBody);
        tvTitle     = findViewById(R.id.tvTitle);
        tvPoints    = findViewById(R.id.tvPoints);
        tabChat     = findViewById(R.id.tabChat);
        tabFriends  = findViewById(R.id.tabFriends);
        tabMe       = findViewById(R.id.tabMe);

        fChat = new ChatFragment();
        fFriends = new FriendsFragment();
        fMe = new MeFragment();
        fChat.host = this;
        fFriends.host = this;
        fMe.host = this;

        findViewById(R.id.btnDrawer).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { toggleDrawer(); }
        });
        findViewById(R.id.drawerMask).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { closeDrawer(); }
        });

        View.OnClickListener tabClick = new View.OnClickListener() {
            public void onClick(View v) {
                int t = (v == tabChat) ? 0 : (v == tabFriends ? 1 : 2);
                switchTab(t);
            }
        };
        tabChat.setOnClickListener(tabClick);
        tabFriends.setOnClickListener(tabClick);
        tabMe.setOnClickListener(tabClick);

        switchTab(0);
        loadProfile();
        UpdateManager.check(this);
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) { closeDrawer(); return; }
        super.onBackPressed();
    }

    // ---------- 页面切换 ----------
    private void switchTab(int t) {
        curTab = t;
        Fragment f = (t == 0) ? fChat : (t == 1 ? fFriends : fMe);
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        if (!f.isAdded()) ft.add(R.id.content, f, "tab" + t);
        if (fChat.isAdded()) ft.hide(fChat);
        if (fFriends.isAdded()) ft.hide(fFriends);
        if (fMe.isAdded()) ft.hide(fMe);
        ft.show(f);
        ft.commit();

        int on = getResources().getColor(R.color.accent);
        int off = getResources().getColor(R.color.text_sub);
        tabChat.setTextColor(t == 0 ? on : off);
        tabFriends.setTextColor(t == 1 ? on : off);
        tabMe.setTextColor(t == 2 ? on : off);

        tvTitle.setText(t == 0 ? getString(R.string.tab_chat)
                : t == 1 ? getString(R.string.tab_friends) : getString(R.string.tab_me));

        if (t == 0) fChat.onShow();
        if (t == 1) fFriends.onShow();
        if (t == 2) fMe.onShow();
    }

    public void gotoChat() { switchTab(0); }

    public void tvTitleSet(final String s) {
        runOnUiThread(new Runnable() {
            public void run() { tvTitle.setText(s); }
        });
    }

    // ---------- 抽屉 ----------
    private void toggleDrawer() {
        if (drawerOpen) closeDrawer(); else openDrawer();
    }

    private void openDrawer() {
        drawerRoot.setVisibility(View.VISIBLE);
        TranslateAnimation an = new TranslateAnimation(
                TranslateAnimation.RELATIVE_TO_SELF, -1f,
                TranslateAnimation.RELATIVE_TO_SELF, 0f,
                TranslateAnimation.RELATIVE_TO_SELF, 0f,
                TranslateAnimation.RELATIVE_TO_SELF, 0f);
        an.setDuration(260);
        drawerPanel.startAnimation(an);
        drawerOpen = true;
        loadDrawer();
    }

    private void closeDrawer() {
        TranslateAnimation an = new TranslateAnimation(
                TranslateAnimation.RELATIVE_TO_SELF, 0f,
                TranslateAnimation.RELATIVE_TO_SELF, -1f,
                TranslateAnimation.RELATIVE_TO_SELF, 0f,
                TranslateAnimation.RELATIVE_TO_SELF, 0f);
        an.setDuration(220);
        drawerPanel.startAnimation(an);
        drawerRoot.setVisibility(View.GONE);
        drawerOpen = false;
    }

    // ---------- 积分 / 资料 ----------
    public void loadProfile() {
        new Thread(new Runnable() {
            public void run() {
                final JSONObject r = Api.get("user.php?action=profile");
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null || !r.optBoolean("success", false)) return;
                        final int pts = r.optInt("points", 0);
                        Prefs.user(r.optString("username", Prefs.user()));
                        Prefs.theme(r.optString("theme", "default"));
                        Prefs.bubble(r.optString("bubble", "default"));
                        Prefs.namecolor(r.optString("namecolor", "default"));
                        tvPoints.setText(pts + " 分");
                        if (fMe != null) fMe.applyProfile(r);
                        if (fChat != null) fChat.applyProfile(r);
                    }
                });
            }
        }).start();
    }

    public void refreshPoints() { loadProfile(); }

    // ---------- 抽屉内容：主题 / 气泡 / 名称颜色 / 签到 / 兑换 / 公告 ----------
    private void loadDrawer() {
        drawerBody.removeAllViews();
        addSignSection();
        addRedeemSection();
        addAnnSection();
        new Thread(new Runnable() {
            public void run() {
                final JSONObject r = Api.get("user.php?action=style_catalog");
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null) return;
                        JSONArray th = r.optJSONArray("themes");
                        JSONArray bb = r.optJSONArray("bubbles");
                        JSONArray nc = r.optJSONArray("namecolors");
                        JSONObject mine = r.optJSONObject("mine");
                        addStyleSection(getString(R.string.sec_theme), th, mine, "theme", "unlocked_themes");
                        addStyleSection(getString(R.string.sec_bubble), bb, mine, "bubble", "unlocked_bubbles");
                        addStyleSection(getString(R.string.sec_namecolor), nc, mine, "namecolor", "unlocked_namecolors");
                    }
                });
            }
        }).start();
    }

    private TextView sectionTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(getResources().getColor(R.color.text_drawer));
        t.setTextSize(13);
        t.setPadding(4, 14, 4, 6);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private void addSignSection() {
        drawerBody.addView(sectionTitle(getString(R.string.sec_sign)));
        final TextView btn = new TextView(this);
        btn.setText("每日签到 (+50)");
        btn.setTextColor(getResources().getColor(R.color.text_drawer));
        btn.setTextSize(14);
        btn.setPadding(10, 12, 10, 12);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { doSign(btn); }
        });
        drawerBody.addView(btn);
    }

    private void doSign(final TextView btn) {
        btn.setEnabled(false);
        new Thread(new Runnable() {
            public void run() {
                final JSONObject r = Api.post("user.php?action=sign_in", new JSONObject());
                runOnUiThread(new Runnable() {
                    public void run() {
                        btn.setEnabled(true);
                        if (r == null) { toast("网络错误"); return; }
                        toast(r.optBoolean("success", false)
                                ? ("签到成功 +" + r.optInt("reward", 50))
                                : r.optString("error", "签到失败"));
                        loadProfile();
                    }
                });
            }
        }).start();
    }

    private void addRedeemSection() {
        drawerBody.addView(sectionTitle(getString(R.string.sec_redeem)));
        final android.widget.EditText ed = new android.widget.EditText(this);
        ed.setHint("输入兑换码");
        ed.setSingleLine(true);
        ed.setTextColor(getResources().getColor(R.color.text_drawer));
        ed.setHintTextColor(0x8866AAAA);
        ed.setTextSize(13);
        drawerBody.addView(ed);

        TextView btn = new TextView(this);
        btn.setText("兑换");
        btn.setTextColor(getResources().getColor(R.color.text_drawer));
        btn.setTextSize(14);
        btn.setPadding(10, 12, 10, 12);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final String code = ed.getText().toString().trim();
                if (code.isEmpty()) { toast("请输入兑换码"); return; }
                new Thread(new Runnable() {
                    public void run() {
                        JSONObject b = new JSONObject();
                        try { b.put("code", code); } catch (Exception ignore) {}
                        final JSONObject r = Api.post("user.php?action=redeem", b);
                        runOnUiThread(new Runnable() {
                            public void run() {
                                if (r == null) { toast("网络错误"); return; }
                                toast(r.optBoolean("success", false)
                                        ? ("兑换成功 +" + r.optInt("points", 0))
                                        : r.optString("error", "兑换失败"));
                                loadProfile();
                            }
                        });
                    }
                }).start();
            }
        });
        drawerBody.addView(btn);
    }

    private void addAnnSection() {
        drawerBody.addView(sectionTitle(getString(R.string.sec_ann)));
        final TextView body = new TextView(this);
        body.setText("加载中…");
        body.setTextColor(getResources().getColor(R.color.text_drawer));
        body.setTextSize(12);
        body.setPadding(4, 6, 4, 6);
        drawerBody.addView(body);
        new Thread(new Runnable() {
            public void run() {
                final JSONObject r = Api.get("user.php?action=announcements");
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null) { body.setText("加载失败"); return; }
                        JSONArray arr = r.optJSONArray("list");
                        if (arr == null || arr.length() == 0) { body.setText("暂无公告"); return; }
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject a = arr.optJSONObject(i);
                            if (a == null) continue;
                            sb.append("· ").append(a.optString("title", ""))
                              .append("\n").append(a.optString("content", "")).append("\n\n");
                        }
                        body.setText(sb.toString().trim());
                    }
                });
            }
        }).start();
    }

    private void addStyleSection(String title, final JSONArray arr, JSONObject mine,
                                 final String kind, String unlockedKey) {
        if (arr == null || arr.length() == 0) return;
        List<String> owned = new ArrayList<String>();
        if (mine != null) {
            JSONArray ua = mine.optJSONArray(unlockedKey);
            if (ua != null) for (int i = 0; i < ua.length(); i++) owned.add(ua.optString(i, ""));
        }
        final String cur = kind.equals("theme") ? Prefs.theme()
                : kind.equals("bubble") ? Prefs.bubble() : Prefs.namecolor();

        drawerBody.addView(sectionTitle(title));
        for (int i = 0; i < arr.length(); i++) {
            final JSONObject it = arr.optJSONObject(i);
            if (it == null) continue;
            final String key = it.optString("key", "");
            final int price = it.optInt("price", 0);
            final boolean has = owned.contains(key);

            TextView row = new TextView(this);
            String label = it.optString("name", key);
            if (!has && price > 0) label += "（" + price + " 分）";
            if (key.equals(cur)) label += "  ✓";
            row.setText(label);
            row.setTextColor(getResources().getColor(R.color.text_drawer));
            row.setTextSize(13);
            row.setPadding(12, 10, 12, 10);
            if (key.equals(cur)) {
                row.setBackgroundColor(0x225FE3F0);
            }
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { setStyle(kind, key, has, price); }
            });
            drawerBody.addView(row);
        }
    }

    private void setStyle(final String kind, final String key, final boolean has, final int price) {
        if (!has && price > 0) {
            new AlertDialog.Builder(this)
                    .setTitle("解锁")
                    .setMessage("解锁需要 " + price + " 积分，确定吗？")
                    .setPositiveButton("解锁", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { commitStyle(kind, key); }
                    })
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show();
        } else {
            commitStyle(kind, key);
        }
    }

    private void commitStyle(final String kind, final String key) {
        new Thread(new Runnable() {
            public void run() {
                JSONObject b = new JSONObject();
                try { b.put("kind", kind); b.put("key", key); } catch (Exception ignore) {}
                final JSONObject r = Api.post("user.php?action=style_set", b);
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null) { toast("网络错误"); return; }
                        if (!r.optBoolean("success", false)) {
                            toast(r.optString("error", "设置失败"));
                            return;
                        }
                        if (kind.equals("theme")) Prefs.theme(key);
                        else if (kind.equals("bubble")) Prefs.bubble(key);
                        else Prefs.namecolor(key);
                        toast("已设置");
                        loadDrawer();
                        loadProfile();
                    }
                });
            }
        }).start();
    }

    public void toast(final String s) {
        runOnUiThread(new Runnable() {
            public void run() { Toast.makeText(MainActivity.this, s, Toast.LENGTH_SHORT).show(); }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (self == this) self = null;
    }
}
