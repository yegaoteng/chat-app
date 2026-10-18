package xfan.chat.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
    private TextView tvTitle, tvPoints;
    private View tabChat, tabFriends, tabMe;
    private TextView tvChat, tvFriends, tvMe;
    private ImageView icChat, icFriends, icMe;
    private View dockIndicator;
    private boolean drawerOpen = false;

    public ChatFragment fChat;
    public FriendsFragment fFriends;
    public MeFragment fMe;
    private int curTab = 0;   // 0 聊天 1 好友 2 我的

    /**
     * 兜底：确保同一时刻只有一个页面的视图可见。
     * 现象：切到好友/我的后，聊天页的输入栏偶尔还留在下面。
     */
    private void enforceOnly(int t) {
        Fragment[] fs = {fChat, fFriends, fMe};
        for (int i = 0; i < fs.length; i++) {
            Fragment x = fs[i];
            if (x == null || x.getView() == null) continue;
            try {
                x.getView().setVisibility(i == t ? View.VISIBLE : View.GONE);
            } catch (Throwable ignore) {}
        }
    }

    /** 选中项文字与图标一起点亮（图2 风格：白图标 + 亮蓝字） */
    private void syncDockColors() {
        int on = getResources().getColor(R.color.accent);
        int off = 0xFF000000;   // 未选中用黑色，白底 Dock 上才看得清
        TextView[] tvs = {tvChat, tvFriends, tvMe};
        ImageView[] ics = {icChat, icFriends, icMe};
        for (int i = 0; i < 3; i++) {
            boolean sel = (i == curTab);
            if (tvs[i] != null) {
                tvs[i].setTextColor(sel ? on : off);
                tvs[i].setTypeface(null, sel ? android.graphics.Typeface.BOLD
                        : android.graphics.Typeface.NORMAL);
            }
            if (ics[i] != null) {
                try {
                    ics[i].setColorFilter(sel ? on : off,
                            android.graphics.PorterDuff.Mode.SRC_IN);
                } catch (Throwable ignore) {}
            }
        }
    }

    /** Dock 拖拽用的主线程 Handler（长按判定 + 指示层移动） */
    private final Handler dockH = new Handler(Looper.getMainLooper());

    /** 侧边栏文字配色：未选中白、选中蓝 */
    private static final int WHITE_OFF = 0xFFFFFFFF;
    private static final int BLUE_ON   = 0xFF4EA3FF;

    public static MainActivity self;
    /** 是否在前台：后台守护据此决定要不要发通知 */
    public static boolean foreground = false;

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
        tvChat      = findViewById(R.id.tvChat);
        tvFriends   = findViewById(R.id.tvFriends);
        tvMe        = findViewById(R.id.tvMe);
        icChat      = findViewById(R.id.icChat);
        icFriends   = findViewById(R.id.icFriends);
        icMe        = findViewById(R.id.icMe);

        // 先用本地缓存把右上角填上，别等网络回来才出现（否则看着像缺了一块 UI）
        syncTopRight(Prefs.points());

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
        setupDock();
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
    protected void onResume() {
        super.onResume();
        foreground = true;
        // 每次进入都「检查」权限状态，但绝不在这里弹系统授权框。
        // 之前在 onResume 里无条件 requestPermissions：
        //   授权框出现 -> Activity onPause -> 框消失 -> onResume -> 再次请求…
        // 形成死循环，表现为所有弹窗闪屏、按钮点不动（触摸被授权框窗口吞掉）。
        // 冷启动且从未问过时才自动问一次，之后只由用户点开关触发。
        try {
            if (Prefs.get("notify_asked", "0").equals("0")) {
                Prefs.put("notify_asked", "1");
                // 仅首次进入 APP 弹一次系统授权框
                ensureNotifyPermission();
            }
        } catch (Throwable ignore) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        foreground = false;
    }

    /**
     * 通知权限：Android 13+ 必须显式授权。
     * 未授权就什么也不做（保持关闭），不会反复骚扰。
     */
    /** 返回 true 表示已经弹了系统授权框 */
    public boolean ensureNotifyPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33) return false;
        try {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) return false;
            requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 9001);
            return true;
        } catch (Throwable ignore) {
            return false;
        }
    }

    /** 通知权限授权结果回来后的回调（由设置弹窗里的开关注册） */
    private static Runnable notifyCb = null;

    public static void notifyCallback(Runnable r) { notifyCb = r; }

    @Override
    public void onRequestPermissionsResult(int code, String[] ps, int[] res) {
        super.onRequestPermissionsResult(code, ps, res);
        if (code == 9001 && notifyCb != null) {
            try { notifyCb.run(); } catch (Throwable ignore) {}
            notifyCb = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) { closeDrawer(); return; }
        super.onBackPressed();
    }

    // ---------- 页面切换（带过渡动画）----------
    /** 底部 Dock：液态玻璃底板 + 深蓝选中层（可长按拖动切页） */
    private void setupDock() {
        View glass = findViewById(R.id.dockGlass);
        if (glass != null) Glass.dock(glass, 24f);
        dockIndicator = findViewById(R.id.dockIndicator);
        final View wrap = findViewById(R.id.dockWrap);
        if (wrap != null) {
            // 底板在 padding 区域也能接到触摸，padding 处长按同样可拖
            attachDockDrag(wrap);
            wrap.post(new Runnable() {
                public void run() { moveDockIndicator(curTab, false); }
            });
        }
        // 标签本身也挂上：选中层压在标签下面，长按标签即等于长按选中层
        attachDockDrag(tabChat);
        attachDockDrag(tabFriends);
        attachDockDrag(tabMe);
    }

    /**
     * 长按 450ms 进入拖动态，之后手指横向移动，选中层跟着走；
     * 松手吸附到最近的一页并切换。
     */
    private void attachDockDrag(final View v) {
        if (v == null) return;
        v.setOnTouchListener(new View.OnTouchListener() {
            private boolean dragging = false;
            private float downX = 0f, downY = 0f;
            private Runnable armed = null;

            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        dragging = false;
                        final float sx = downX;
                        armed = new Runnable() {
                            public void run() {
                                dragging = true;
                                dragIndicatorTo(sx, true);
                                try {
                                    v.performHapticFeedback(
                                            android.view.HapticFeedbackConstants.LONG_PRESS);
                                } catch (Throwable ignore) {}
                            }
                        };
                        dockH.postDelayed(armed, 450);
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (!dragging && armed != null
                                && (Math.abs(e.getRawX() - downX) > 14
                                 || Math.abs(e.getRawY() - downY) > 14)) {
                            dockH.removeCallbacks(armed);
                            armed = null;
                        }
                        if (dragging) dragIndicatorTo(e.getRawX(), false);
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (armed != null) { dockH.removeCallbacks(armed); armed = null; }
                        if (dragging) {
                            dragging = false;
                            int t = dockTabAt(e.getRawX());
                            moveDockIndicator(t, true);
                            if (t != curTab) switchTab(t);
                            return true;   // 吃掉这一下，避免再触发 click 重复切换
                        }
                        break;
                }
                return false;
            }
        });
    }

    /** 把选中层挪到手指所在的横向位置（限制在 Dock 内） */
    private void dragIndicatorTo(float rawX, boolean animate) {
        final View ind = dockIndicator;
        View wrap = findViewById(R.id.dockWrap);
        if (ind == null || wrap == null) return;
        int[] loc = new int[2];
        wrap.getLocationOnScreen(loc);
        float x = rawX - loc[0] - ind.getWidth() / 2f;
        float max = wrap.getWidth() - ind.getWidth();
        if (x < 0) x = 0;
        if (x > max) x = max;
        ind.animate().cancel();
        if (animate) {
            ind.animate().translationX(x).setDuration(120).start();
        } else {
            ind.setTranslationX(x);
        }
    }

    /** 手指位置最接近哪一页 */
    private int dockTabAt(float rawX) {
        View[] tabs = {tabChat, tabFriends, tabMe};
        int best = 0;
        float bestD = Float.MAX_VALUE;
        int[] loc = new int[2];
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i] == null) continue;
            tabs[i].getLocationOnScreen(loc);
            float cx = loc[0] + tabs[i].getWidth() / 2f;
            float d = Math.abs(cx - rawX);
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    /** 让选中层的宽度与某一页一致 */
    private void sizeDockIndicator(int t) {
        final View ind = dockIndicator;
        View target = (t == 0) ? tabChat : (t == 1 ? tabFriends : tabMe);
        if (ind == null || target == null) return;
        int w = target.getWidth();
        if (w <= 0) return;
        int pad = Glass.dp(this, 5);
        android.view.ViewGroup.LayoutParams lp = ind.getLayoutParams();
        if (lp == null) lp = new android.widget.FrameLayout.LayoutParams(
                w - pad * 2, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        lp.width = w - pad * 2;
        ind.setLayoutParams(lp);
        ind.setVisibility(View.VISIBLE);
    }

    private void moveDockIndicator(int t, boolean animate) {
        final View ind = dockIndicator;
        View target = (t == 0) ? tabChat : (t == 1 ? tabFriends : tabMe);
        if (ind == null || target == null) return;
        sizeDockIndicator(t);
        int pad = Glass.dp(this, 5);
        final float toX = target.getLeft() + pad;
        ind.animate().cancel();
        if (animate) {
            ind.animate().translationX(toX).setDuration(280)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f))
                    .start();
        } else {
            ind.setTranslationX(toX);
        }
    }

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
            // 关键：commit 是异步的。不立刻执行的话，
            // 快速连点或在 fragment 尚未添加时切换，旧页面（含聊天输入栏）会残留可见。
            getFragmentManager().executePendingTransactions();
        } catch (Throwable e) {
            Log.w("XFAN", "fragment 切换失败，降级重来", e);
            try {
                FragmentTransaction ft2 = getFragmentManager().beginTransaction();
                if (!f.isAdded()) ft2.add(R.id.content, f, "tab" + t);
                ft2.show(f);
                ft2.commitAllowingStateLoss();
                getFragmentManager().executePendingTransactions();
            } catch (Throwable ignore) {}
        }
        // 双保险：直接按可见性兜底，hide 万一没生效也不会露出聊天输入栏
        enforceOnly(t);

        syncDockColors();
        popTab(t == 0 ? tabChat : (t == 1 ? tabFriends : tabMe));

        tvTitle.setText(t == 0 ? getString(R.string.tab_chat)
                : t == 1 ? getString(R.string.tab_friends) : getString(R.string.tab_me));
        syncTopRight(Prefs.points());
        moveDockIndicator(t, true);

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

    /**
     * 右上角：聊天页显示「设置聊天背景」（点击可换背景），
     * 好友/我的页显示积分。
     */
    private void syncTopRight(int pts) {
        if (tvPoints == null) return;
        // 单行走到底，避免宽度不够被截断成半个图标
        tvPoints.setSingleLine(true);
        tvPoints.setMinWidth(Glass.dp(this, 56));
        if (curTab == 0) {
            tvPoints.setText("设置聊天背景");
            tvPoints.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (fChat != null) fChat.pickChatBg();
                }
            });
        } else {
            // 好友/我的页：只显示数字 + 「积分」，不要图标
            tvPoints.setText(Math.max(0, pts) + " 积分");
            tvPoints.setOnClickListener(null);
        }
        tvPoints.setVisibility(View.VISIBLE);
        tvPoints.invalidate();
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
                        Prefs.points(pts);
                        Prefs.user(r.optString("username", Prefs.user()));
                        Prefs.theme(r.optString("theme", "default"));
                        Prefs.bubble(r.optString("bubble", "default"));
                        Prefs.namecolor(r.optString("namecolor", "default"));
                        syncTopRight(pts);
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

        // 个性化目录走 24 小时缓存：每次开侧边栏都拉会很慢，
        // 而且拿到之前会短暂显示「未兑换」，看着像状态错了。
        String cached = Prefs.stylesJson();
        boolean fresh = Prefs.stylesTs() > 0
                && (System.currentTimeMillis() - Prefs.stylesTs() < 24L * 3600 * 1000);
        if (!cached.isEmpty() && fresh) {
            try { applyStyleJson(new JSONObject(cached)); } catch (Throwable ignore) {}
            return;   // 缓存有效：本次只加载公告，不再请求目录
        }

        new Thread(new Runnable() {
            public void run() {
                final JSONObject r = Api.get("user.php?action=style_catalog");
                ui(new Runnable() {
                    public void run() {
                        if (r == null) {
                            // 请求失败时退回旧缓存，至少不会显示空目录
                            String old = Prefs.stylesJson();
                            if (!old.isEmpty()) {
                                try { applyStyleJson(new JSONObject(old)); } catch (Throwable ignore) {}
                            }
                            return;
                        }
                        Prefs.stylesJson(r.toString());
                        Prefs.stylesTs(System.currentTimeMillis());
                        applyStyleJson(r);
                    }
                });
            }
        }).start();
    }

    /** 把目录 JSON 渲染进个性化区块 */
    private void applyStyleJson(JSONObject r) {
        JSONArray th = r.optJSONArray("themes");
        JSONArray bb = r.optJSONArray("bubbles");
        JSONArray nc = r.optJSONArray("namecolors");
        JSONObject mine = r.optJSONObject("mine");
        if (th == null && bb == null && nc == null) return;
        rebuildStyles(th, bb, nc, mine);
    }

    /**
     * 抽屉里的可点击项统一套「圆角矩形按钮」边框，
     * 让用户一眼看出这里能点（之前是纯文字，看不出可交互）。
     */
    /** 侧边栏按钮：液态玻璃底 + 文字居中 + 按压动效 */
    private void asButton(View v) {
        Glass.apply(v, false, 8f);
    }

    /** 当前选中项：更亮的玻璃底 + 蓝色描边 */
    private void asButtonOn(View v) {
        Glass.apply(v, true, 8f);
    }

    private TextView sectionTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(getResources().getColor(R.color.text_drawer));
        t.setTextSize(14);
        t.setPadding(4, 18, 4, 8);
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
        // 已签到就显示「今日已签到」，不再让人误以为还能再点一次
        btn.setText(Prefs.get("signed_today", "0").equals("1") ? "今日已签到" : "每日签到 (+50)");
        btn.setTextColor(WHITE_OFF);
        btn.setTextSize(15);
        btn.setPadding(18, 16, 18, 16);
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
                        if (r == null) { btn.setText("每日签到 (+50)"); toast("网络错误"); return; }
                        btn.setText(r.optBoolean("success", false) ? "今日已签到"
                                : "每日签到 (+50)");
                        Prefs.put("signed_today", r.optBoolean("success", false) ? "1" : "0");
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
        btn.setTextColor(WHITE_OFF);
        btn.setTextSize(15);
        btn.setPadding(18, 16, 18, 16);
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
            final boolean on = key.equals(cur);
            // 未选中白色，选中蓝色（侧边栏底板是深色，白色才看得清）
            row.setTextColor(on ? BLUE_ON : WHITE_OFF);
            row.setTextSize(15);
            row.setPadding(18, 16, 18, 16);
            if (on) asButtonOn(row); else asButton(row);
            markStyle(row, kind);
            // 项间距再放大一点
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 12;
            row.setLayoutParams(lp);
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
                        // 立即生效，不等下次拉取资料。
                        // 聊天数据也要重画：气泡/名称色是服务端按发送者回填的，
                        // 不重载的话列表里仍是旧样式，得手动刷新才看得到变化。
                        applyTheme();
                        toast("已切换");
                        if (fChat != null) {
                            try { fChat.reloadForStyleChange(); } catch (Throwable ignore) {}
                        }
                        if (fFriends != null) {
                            try { fFriends.reloadForStyleChange(); } catch (Throwable ignore) {}
                        }
                        // 手动兑换/切换过，缓存作废，下次开侧边栏重新拉
                        Prefs.invalidateStyles();
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
