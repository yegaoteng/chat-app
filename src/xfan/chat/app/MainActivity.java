package xfan.chat.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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

    /** 内置样式目录：网络不通时个性化依然可切换 */
    private static JSONArray builtin(String kind) throws Exception {
        if ("theme".equals(kind)) return new JSONArray()
                .put(new JSONObject().put("key", "default").put("name", "默认").put("price", 0))
                .put(new JSONObject().put("key", "gold").put("name", "金辉红框").put("price", 1000));
        if ("bubble".equals(kind)) return new JSONArray()
                .put(new JSONObject().put("key", "default").put("name", "默认").put("price", 0))
                .put(new JSONObject().put("key", "golden").put("name", "金色华章").put("price", 2500))
                .put(new JSONObject().put("key", "rainbow").put("name", "七色流彩").put("price", 6500));
        return new JSONArray()
                .put(new JSONObject().put("key", "default").put("name", "默认").put("price", 0))
                .put(new JSONObject().put("key", "gold").put("name", "金色").put("price", 300))
                .put(new JSONObject().put("key", "rainbow_static").put("name", "七彩渐变·静态").put("price", 800))
                .put(new JSONObject().put("key", "rainbow_dynamic").put("name", "七彩渐变·动态").put("price", 1299));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        CrashGuard.install(this);
        Prefs.init(this);
        ImgLoader.init(this);
        Api.applyHttpSwitch();
        Api.bindContext(this);
        new Thread(new Runnable() {
            public void run() { Api.syncWebCookies(MainActivity.this); }
        }).start();
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

        // 每一步单独容错：任何一处异常都不会导致整个页面闪退
        try { applyTheme(); } catch (Throwable t) { Log.e("XFAN", "applyTheme", t); }
        try { switchTab(0); } catch (Throwable t) { Log.e("XFAN", "switchTab", t); }
        try { loadProfile(); } catch (Throwable t) { Log.e("XFAN", "loadProfile", t); }
        try { UpdateManager.check(this); } catch (Throwable t) { Log.e("XFAN", "update", t); }
    }

    /** 应用个性化主题到整个界面 */
    public void applyTheme() {
        ThemeEngine.applyActivity(this);
        if (fChat != null) fChat.applyTheme();
        if (fFriends != null) fFriends.applyTheme();
        if (fMe != null) fMe.applyTheme();
    }

    /** UI 线程执行，自动跳过已销毁状态 */
    private void ui(Runnable r) {
        if (isFinishing()) return;
        runOnUiThread(new Runnable() {
            public void run() {
                if (isFinishing()) return;
                r.run();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) { closeDrawer(); return; }
        super.onBackPressed();
    }

    // ---------- 页面切换（带过渡动画）----------
    private void switchTab(int t) {
        if (t == curTab && fChat != null && fChat.isAdded() && !fChat.isHidden()
                && t == 0) {
            // 重复点当前页：不重播动画
            return;
        }
        boolean toRight = t >= curTab;
        final int prev = curTab;
        curTab = t;

        Fragment f = (t == 0) ? fChat : (t == 1 ? fFriends : fMe);
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        // 不用 setCustomAnimations：FragmentManager 会用 AnimatorInflater 解析，
        // 而补间动画(<translate>)不是合法 animator，会抛 Unknown animator name。
        // 过渡效果改用下面的 Java 动画作用于内容容器，效果一样且不依赖 XML。
        if (!f.isAdded()) ft.add(R.id.content, f, "tab" + t);
        try {
            if (fChat.isAdded()) ft.hide(fChat);
            if (fFriends.isAdded()) ft.hide(fFriends);
            if (fMe.isAdded()) ft.hide(fMe);
            ft.show(f);
            ft.commit();
        } catch (Throwable e) {
            Log.w("XFAN", "fragment 切换失败，降级重来", e);
            try {
                FragmentTransaction ft2 = getFragmentManager().beginTransaction();
                if (!f.isAdded()) ft2.add(R.id.content, f, "tab" + t);
                ft2.show(f);
                ft2.commitAllowingStateLoss();
            } catch (Throwable ignore) {}
        }

        int on = getResources().getColor(R.color.accent);
        int off = getResources().getColor(R.color.text_sub);
        tabChat.setTextColor(t == 0 ? on : off);
        tabFriends.setTextColor(t == 1 ? on : off);
        tabMe.setTextColor(t == 2 ? on : off);
        popTab(t == 0 ? tabChat : (t == 1 ? tabFriends : tabMe));

        tvTitle.setText(t == 0 ? getString(R.string.tab_chat)
                : t == 1 ? getString(R.string.tab_friends) : getString(R.string.tab_me));

        animateContent(toRight);

        if (t == 0) safeShow(fChat);
        if (t == 1) safeShow(fFriends);
        if (t == 2) safeShow(fMe);
    }

    /** fragment 可能尚未完成 add，延后一帧再回调，避免空指针 */
    private void safeShow(final Fragment f) {
        if (f == null) return;
        findViewById(android.R.id.content).post(new Runnable() {
            public void run() {
                try {
                    if (f instanceof ChatFragment) ((ChatFragment) f).onShow();
                    else if (f instanceof FriendsFragment) ((FriendsFragment) f).onShow();
                    else if (f instanceof MeFragment) ((MeFragment) f).onShow();
                } catch (Exception ignore) {}
            }
        });
    }

    /**
     * 内容区过渡动画：纯 Java 创建补间动画，不经过 AnimatorInflater。
     * @param toRight true 表示切向右侧的页面
     */
    private void animateContent(final boolean toRight) {
        final View content = findViewById(R.id.content);
        if (content == null) return;
        try {
            content.clearAnimation();
            android.view.animation.AnimationSet set =
                    new android.view.animation.AnimationSet(true);
            android.view.animation.TranslateAnimation ta =
                    new android.view.animation.TranslateAnimation(
                            android.view.animation.Animation.RELATIVE_TO_SELF,
                            toRight ? 0.12f : -0.12f,
                            android.view.animation.Animation.RELATIVE_TO_SELF, 0f,
                            android.view.animation.Animation.RELATIVE_TO_SELF, 0f,
                            android.view.animation.Animation.RELATIVE_TO_SELF, 0f);
            android.view.animation.AlphaAnimation aa =
                    new android.view.animation.AlphaAnimation(0.35f, 1f);
            set.addAnimation(ta);
            set.addAnimation(aa);
            set.setDuration(230);
            set.setInterpolator(new android.view.animation.DecelerateInterpolator());
            content.startAnimation(set);
        } catch (Throwable ignore) {}
    }

    /** 选中标签的弹跳反馈 */
    private void popTab(View v) {
        if (v == null) return;
        try {
        ScaleAnimation sa = new ScaleAnimation(0.86f, 1.06f, 0.86f, 1.06f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        sa.setDuration(150);
        sa.setRepeatMode(Animation.REVERSE);
        sa.setRepeatCount(1);
        v.startAnimation(sa);
        } catch (Throwable ignore) {}
    }

    public void gotoChat() { switchTab(0); }

    public void tvTitleSet(final String s) {
        ui(new Runnable() {
            public void run() { if (tvTitle != null) tvTitle.setText(s); }
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
        an.setInterpolator(new android.view.animation.DecelerateInterpolator());
        drawerPanel.startAnimation(an);
        android.view.animation.AlphaAnimation fa =
                new android.view.animation.AlphaAnimation(0f, 1f);
        fa.setDuration(260);
        View mask = findViewById(R.id.drawerMask);
        if (mask != null) mask.startAnimation(fa);
        drawerOpen = true;
        loadDrawer();
    }

    private void closeDrawer() {
        if (!drawerOpen) { drawerRoot.setVisibility(View.GONE); return; }
        drawerOpen = false;
        TranslateAnimation an = new TranslateAnimation(
                TranslateAnimation.RELATIVE_TO_SELF, 0f,
                TranslateAnimation.RELATIVE_TO_SELF, -1f,
                TranslateAnimation.RELATIVE_TO_SELF, 0f,
                TranslateAnimation.RELATIVE_TO_SELF, 0f);
        an.setDuration(240);
        an.setInterpolator(new android.view.animation.AccelerateInterpolator());
        // 关键：动画播完再隐藏容器，否则面板会瞬间消失、看不到收回过程
        an.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
            public void onAnimationStart(android.view.animation.Animation a) {}
            public void onAnimationEnd(android.view.animation.Animation a) {
                if (!drawerOpen) drawerRoot.setVisibility(View.GONE);
            }
            public void onAnimationRepeat(android.view.animation.Animation a) {}
        });
        drawerPanel.startAnimation(an);

        // 遮罩同步淡出
        android.view.animation.AlphaAnimation fa =
                new android.view.animation.AlphaAnimation(1f, 0f);
        fa.setDuration(240);
        View mask = findViewById(R.id.drawerMask);
        if (mask != null) mask.startAnimation(fa);
    }

    // ---------- 积分 / 资料 ----------
    public void loadProfile() {
        new Thread(new Runnable() {
            public void run() {
                final JSONObject r = Api.get("user.php?action=profile");
                ui(new Runnable() {
                    public void run() {
                        if (r == null || !r.optBoolean("success", false)) return;
                        final int pts = r.optInt("points", 0);
                        Prefs.user(r.optString("username", Prefs.user()));
                        Prefs.theme(r.optString("theme", "default"));
                        Prefs.bubble(r.optString("bubble", "default"));
                        Prefs.namecolor(r.optString("namecolor", "default"));
                        if (tvPoints != null) tvPoints.setText(pts + " 分");
                        applyTheme();
                        if (fMe != null) fMe.applyProfile(r);
                        if (fChat != null) fChat.applyProfile(r);
                        if (fFriends != null) fFriends.applyProfile(r);
                    }
                });
            }
        }).start();
    }

    public void refreshPoints() { loadProfile(); }

    // ---------- 抽屉内容 ----------
    // 顺序：个性化（立即可用）→ 签到 → 兑换 → 公告（可慢慢加载）
    private void loadDrawer() {
        drawerBody.removeAllViews();
        addStyleSection(getString(R.string.sec_theme), null, null, "theme", "unlocked_themes");
        addStyleSection(getString(R.string.sec_bubble), null, null, "bubble", "unlocked_bubbles");
        addStyleSection(getString(R.string.sec_namecolor), null, null, "namecolor", "unlocked_namecolors");
        addSignSection();
        addRedeemSection();
        addAnnSection();

        // 异步拉取真实目录（含解锁状态），拿到后只刷新个性化这三块
        new Thread(new Runnable() {
            public void run() {
                final JSONObject r = Api.get("user.php?action=style_catalog");
                ui(new Runnable() {
                    public void run() {
                        if (r == null) return;
                        JSONArray th = r.optJSONArray("themes");
                        JSONArray bb = r.optJSONArray("bubbles");
                        JSONArray nc = r.optJSONArray("namecolors");
                        JSONObject mine = r.optJSONObject("mine");
                        if (th == null && bb == null && nc == null) return;
                        rebuildStyles(th, bb, nc, mine);
                    }
                });
            }
        }).start();
    }

    /**
     * 抽屉里的可点击项统一套「圆角矩形按钮」边框，
     * 让用户一眼看出这里能点（之前是纯文字，看不出可交互）。
     */
    private void asButton(View v) {
        if (v == null) return;
        v.setBackgroundResource(R.drawable.bg_sidebar_btn);
        v.setClickable(true);
    }

    /** 当前选中项：边框加粗、底色更亮 */
    private void asButtonOn(View v) {
        if (v == null) return;
        v.setBackgroundResource(R.drawable.bg_sidebar_btn_on);
        v.setClickable(true);
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

    /** 用真实目录重建个性化区块，插到最前面 */
    private void rebuildStyles(JSONArray th, JSONArray bb, JSONArray nc, JSONObject mine) {
        List<View> keep = new ArrayList<View>();
        // 保留个性化之后的部分（签到 / 兑换 / 公告）
        int n = drawerBody.getChildCount();
        boolean afterStyles = false;
        for (int i = 0; i < n; i++) {
            View v = drawerBody.getChildAt(i);
            Object tag = v.getTag();
            if (tag instanceof String && ((String) tag).startsWith("style:")) continue;
            if (tag instanceof String && ((String) tag).equals("styleTail")) afterStyles = true;
            if (afterStyles) keep.add(v);
        }
        drawerBody.removeAllViews();
        addStyleSection(getString(R.string.sec_theme), th, mine, "theme", "unlocked_themes");
        addStyleSection(getString(R.string.sec_bubble), bb, mine, "bubble", "unlocked_bubbles");
        addStyleSection(getString(R.string.sec_namecolor), nc, mine, "namecolor", "unlocked_namecolors");
        for (View v : keep) drawerBody.addView(v);
    }

    private void markStyle(View v, String kind) { v.setTag("style:" + kind); }

    private void addSignSection() {
        drawerBody.addView(sectionTitle(getString(R.string.sec_sign)));
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        final TextView btn = new TextView(this);
        btn.setText("每日签到 (+50)");
        btn.setTextColor(getResources().getColor(R.color.text_drawer));
        btn.setTextSize(14);
        btn.setPadding(10, 12, 10, 12);
        btn.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        asButton(btn);

        final ProgressBar pb = new ProgressBar(this);
        pb.setVisibility(View.GONE);
        pb.setLayoutParams(new LinearLayout.LayoutParams(36, 36));

        row.addView(btn);
        row.addView(pb);
        row.setTag("styleTail");
        drawerBody.addView(row);

        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { doSign(btn, pb); }
        });
    }

    private void doSign(final TextView btn, final ProgressBar pb) {
        btn.setEnabled(false);
        btn.setText("签到中…");
        pb.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            public void run() {
                final JSONObject r = Api.post("user.php?action=sign_in", new JSONObject());
                ui(new Runnable() {
                    public void run() {
                        pb.setVisibility(View.GONE);
                        btn.setEnabled(true);
                        btn.setText("每日签到 (+50)");
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
        asButton(btn);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final String code = ed.getText().toString().trim();
                if (code.isEmpty()) { toast("请输入兑换码"); return; }
                new Thread(new Runnable() {
                    public void run() {
                        JSONObject b = new JSONObject();
                        try { b.put("code", code); } catch (Exception ignore) {}
                        final JSONObject r = Api.post("user.php?action=redeem", b);
                        ui(new Runnable() {
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
                ui(new Runnable() {
                    public void run() {
                        if (r == null) { body.setText("暂无公告"); return; }
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

    private void addStyleSection(String title, JSONArray arr, JSONObject mine,
                                 final String kind, String unlockedKey) {
        JSONArray list = arr;
        if (list == null || list.length() == 0) {
            try { list = builtin(kind); } catch (Exception e) { return; }
        }
        List<String> owned = new ArrayList<String>();
        owned.add("default");
        if (mine != null) {
            JSONArray ua = mine.optJSONArray(unlockedKey);
            if (ua != null) for (int i = 0; i < ua.length(); i++) owned.add(ua.optString(i, ""));
        }
        final String cur = kind.equals("theme") ? Prefs.theme()
                : kind.equals("bubble") ? Prefs.bubble() : Prefs.namecolor();

        TextView titleView = sectionTitle(title);
        markStyle(titleView, kind);
        drawerBody.addView(titleView);

        for (int i = 0; i < list.length(); i++) {
            final JSONObject it = list.optJSONObject(i);
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
            if (key.equals(cur)) asButtonOn(row); else asButton(row);
            markStyle(row, kind);
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
                ui(new Runnable() {
                    public void run() {
                        if (r == null) { toast("网络错误"); return; }
                        if (!r.optBoolean("success", false)) {
                            toast(r.optString("error", "设置失败"));
                            return;
                        }
                        if (kind.equals("theme")) Prefs.theme(key);
                        else if (kind.equals("bubble")) Prefs.bubble(key);
                        else Prefs.namecolor(key);
                        // 立即生效，不等下次拉取资料
                        applyTheme();
                        toast("已切换");
                        loadDrawer();
                        loadProfile();
                    }
                });
            }
        }).start();
    }

    public void toast(final String s) {
        ui(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, s, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (self == this) self = null;
    }
}
