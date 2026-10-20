package xfan.chat.app;

import android.app.AlertDialog;
import android.content.DialogInterface;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.VideoView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public class LoginActivity extends Activity {

    private EditText edUser, edPass, edInvite, edUid;
    private TextView btnForgot;
    /** 视频背景播放位置，用于切后台回来续播 */
    private int videoPos = 0;
    private TextView tvUidTip;
    private Button btnLogin, btnRegister, btnWeb;
    private TextView tvTip, tvTitle, tvCaret, tvLogging;
    private ProgressBar pbLogin;
    private View boxLogin;
    private Handler hTitle = new Handler(android.os.Looper.getMainLooper());
    private boolean regMode = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // 必须在最前面：后面任何一处抛异常都能被记录
        CrashGuard.install(this);
        Prefs.init(this);
        ImgLoader.init(this);
        // WebView 通道要在任何请求之前初始化
        Api.applyHttpSwitch();
        Api.bindContext(this);
        // 后台借用 WebView 完成浏览器验证，把 __test 回灌给直连
        new Thread(new Runnable() {
            public void run() { Api.syncWebCookies(LoginActivity.this); }
        }).start();
        setContentView(R.layout.activity_login);

        edUser   = findViewById(R.id.edUser);
        edPass   = findViewById(R.id.edPass);
        edInvite = findViewById(R.id.edInvite);
        edUid    = findViewById(R.id.edUid);
        tvUidTip = findViewById(R.id.tvUidTip);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        tvTip    = findViewById(R.id.tvTip);

        btnWeb = findViewById(R.id.btnWeb);
        btnForgot = findViewById(R.id.btnForgot);

        tvTitle  = findViewById(R.id.tvTitle);
        tvCaret  = findViewById(R.id.tvCaret);
        tvLogging= findViewById(R.id.tvLogging);
        pbLogin  = findViewById(R.id.pbLogin);
        boxLogin = findViewById(R.id.boxLogin);
        startTypewriter();
        setupVideoBg();
        if (btnForgot != null) {
            btnForgot.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { showForgotDialog(); }
            });
        }

        String u = Prefs.user();
        if (u != null && !u.isEmpty()) edUser.setText(u);

        // 上次崩溃过：把堆栈显示出来，方便定位
        showLastCrash();

        // 浏览器登录后跳回来的 xfan://auth?... 优先处理
        if (handleDeepLink(getIntent())) {
            enterMain();
            return;
        }

        // 已保存 token 直接尝试进入
        if (!Prefs.token().isEmpty()) {
            enterMain();
        }

        btnWeb.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openWebLogin(); }
        });
        btnWeb.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { pasteLoginInfo(); return true; }
        });

        btnLogin.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // 注册模式下该按钮是「已有账号？点此登录」，应切回登录模式。
                // 之前它仍走 doLogin()，于是先校验输入并弹「请填写密码」。
                if (regMode) { switchToLogin(); return; }
                doLogin();
            }
        });

        btnRegister.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!regMode) {
                    regMode = true;
                    // 从下往上滑入 + 淡入，逐项错开一点，看起来是「展开」而不是瞬间跳变
                    reveal(edUid, 0);
                    reveal(tvUidTip, 60);
                    reveal(edInvite, 120);
                    btnLogin.setText("已有账号？点此登录");
                    btnRegister.setText("确认注册");
                    tvTip.setText("注册需要邀请码，UID 一经注册不可更改");
                } else {
                    doRegister();
                }
            }
        });

        btnLogin.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                if (regMode) {
                    switchToLogin();
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * 标题打字机：打完 -> 逐字删除 -> 再打印，循环。
     * 用 Choreographer 按帧回调推进，自动适配 60/90/120Hz 高刷屏。
     */
    private void startTypewriter() {
        if (tvTitle == null) return;
        final String full = getString(R.string.app_name);

        // 光标闪烁
        final Runnable blink = new Runnable() {
            public void run() {
                if (tvCaret == null) return;
                tvCaret.setVisibility(tvCaret.getVisibility() == View.VISIBLE
                        ? View.INVISIBLE : View.VISIBLE);
                hTitle.postDelayed(this, 500);
            }
        };
        hTitle.post(blink);

        final int TYPE_MS = 150, ERASE_MS = 90, HOLD_MS = 1200;
        final int[] n = {0};              // 当前显示字数
        final int[] phase = {0};          // 0=打字 1=停顿 2=删除
        final long[] mark = {0};

        final android.view.Choreographer.FrameCallback fc =
                new android.view.Choreographer.FrameCallback() {
            public void doFrame(long frameTimeNanos) {
                if (tvTitle == null) return;
                long now = frameTimeNanos / 1000000L;
                if (mark[0] == 0) mark[0] = now;
                long el = now - mark[0];

                if (phase[0] == 0) {
                    if (el >= TYPE_MS) {
                        mark[0] = now;
                        if (n[0] < full.length()) { n[0]++; tvTitle.setText(full.substring(0, n[0])); }
                        else { phase[0] = 1; }
                    }
                } else if (phase[0] == 1) {
                    if (el >= HOLD_MS) { mark[0] = now; phase[0] = 2; }
                } else {
                    if (el >= ERASE_MS) {
                        mark[0] = now;
                        if (n[0] > 0) { n[0]--; tvTitle.setText(full.substring(0, n[0])); }
                        else { phase[0] = 0; }
                    }
                }
                android.view.Choreographer.getInstance().postFrameCallback(this);
            }
        };
        android.view.Choreographer.getInstance().postFrameCallback(fc);
    }

    /** 视频背景：assets/login_bg.mp4（没有就保持纯色背景） */
    private void setupVideoBg() {
        VideoView vv = findViewById(R.id.videoBg);
        View mask = findViewById(R.id.videoMask);
        if (vv == null) return;
        try {
            java.io.InputStream in = getAssets().open("login_bg.mp4");
            java.io.File f = new java.io.File(getFilesDir(), "login_bg.mp4");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) fos.write(buf, 0, len);
            fos.close(); in.close();

            vv.setVideoPath(f.getAbsolutePath());
            if (mask != null) mask.setVisibility(View.VISIBLE);
            vv.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener() {
                public void onPrepared(android.media.MediaPlayer mp) {
                    mp.setLooping(true);
                    // 居中裁切铺满
                    mp.setVideoScalingMode(
                            android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                    // 按需求开启声音（0f 是静音）
                    try { mp.setVolume(1f, 1f); } catch (Throwable ignore) {}
                    // 从后台回来时接着上次的位置播，不要重头开始
                    int pos = videoPos;
                    if (pos > 0) {
                        try { vv.seekTo(pos); } catch (Throwable ignore) {}
                    }
                    vv.start();
                }
            });
            vv.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener() {
                public boolean onError(android.media.MediaPlayer mp, int what, int extra) {
                    vv.setVisibility(View.GONE);
                    if (mask != null) mask.setVisibility(View.GONE);
                    return true;
                }
            });
        } catch (Throwable ignore) {
            // 没有视频文件就退回纯色背景
            vv.setVisibility(View.GONE);
            if (mask != null) mask.setVisibility(View.GONE);
        }
    }

    /**
     * 切后台时记下播放位置，回来时 seek 回去。
     * 之前没有做这件事，VideoView 在 onResume 会重新 prepare 并从 0 开始，
     * 于是「滑后台再点进来视频就重播」。
     */
    @Override
    protected void onPause() {
        try {
            VideoView vv = findViewById(R.id.videoBg);
            if (vv != null && vv.isPlaying()) videoPos = vv.getCurrentPosition();
        } catch (Throwable ignore) {}
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 已经创建过视频就只 seek，不重新 prepare
        if (videoPos > 0) {
            try {
                VideoView vv = findViewById(R.id.videoBg);
                if (vv != null) {
                    vv.seekTo(videoPos);
                    if (!vv.isPlaying()) vv.start();
                }
            } catch (Throwable ignore) {}
        }
    }

    @Override
    protected void onDestroy() {
        try { videoPos = 0; } catch (Throwable ignore) {}
        super.onDestroy();
    }

    /** 收起输入法 */
    private void hideKeyboard() {
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            getSystemService(INPUT_METHOD_SERVICE);
            if (imm == null) return;
            View v = getCurrentFocus();
            if (v != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (Throwable ignore) {}
    }

    /** 登录中：顶部提示 + 6 秒绿色进度条（从右往左清空） */
    private void showLogging() {
        hideKeyboard();
        if (tvLogging != null) tvLogging.setVisibility(View.VISIBLE);
        if (pbLogin == null) return;
        pbLogin.setVisibility(View.VISIBLE);
        pbLogin.setProgress(pbLogin.getMax());

        // 用 Choreographer 按帧推进：高刷屏上更顺滑
        final long start = android.os.SystemClock.elapsedRealtime();
        final android.view.Choreographer.FrameCallback fc =
                new android.view.Choreographer.FrameCallback() {
            public void doFrame(long t) {
                long el = android.os.SystemClock.elapsedRealtime() - start;
                float k = 1f - el / 6000f;
                if (k < 0f) k = 0f;
                pbLogin.setProgress((int) (k * pbLogin.getMax()));
                if (k > 0f && pbLogin.getVisibility() == View.VISIBLE) {
                    android.view.Choreographer.getInstance().postFrameCallback(this);
                }
            }
        };
        android.view.Choreographer.getInstance().postFrameCallback(fc);
    }

    private void hideLogging() {
        hTitle.removeCallbacksAndMessages(null);
        if (tvLogging != null) tvLogging.setVisibility(View.GONE);
        if (pbLogin != null) pbLogin.setVisibility(View.GONE);
    }

    /** 登录成功后：布局从四面平移出来 */
    private void animateContent() {
        if (boxLogin == null) return;
        // 用 ScaleAnimation 的轴心模拟从四面扩散：先整体缩小再放大归位
        android.view.animation.AnimationSet set =
                new android.view.animation.AnimationSet(true);

        android.view.animation.ScaleAnimation sc =
                new android.view.animation.ScaleAnimation(
                        0.88f, 1f, 0.88f, 1f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f);
        android.view.animation.AlphaAnimation al =
                new android.view.animation.AlphaAnimation(0f, 1f);
        set.addAnimation(sc);
        set.addAnimation(al);
        set.setDuration(420);
        boxLogin.startAnimation(set);
    }

    /** 浏览器登录成功后跳回：xfan://auth?token=xxx&user=yyy&test=zzz */
    private boolean handleDeepLink(Intent it) {
        if (it == null || it.getData() == null) return false;
        android.net.Uri d = it.getData();
        if (!"xfan".equals(d.getScheme())) return false;

        String token = d.getQueryParameter("token");
        String user  = d.getQueryParameter("user");
        String test  = d.getQueryParameter("test");
        if (token == null || token.isEmpty()) return false;

        Prefs.token(token);
        if (user != null && !user.isEmpty()) Prefs.user(user);
        if (test != null && !test.isEmpty()) Prefs.testCookie(test);
        toast("登录成功");
        return true;
    }

    /** 跳浏览器登录页（浏览器能通过 WAF 验证） */
    private void openWebLogin() {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse(Api.base() + "/login_bridge.php"));
            startActivity(i);
        } catch (Exception e) {
            toast("无法打开浏览器");
        }
    }

    /** 兜底：粘贴网页端复制的 xfan://auth?... 整串 */
    private void pasteLoginInfo() {
        android.content.ClipboardManager cm =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        CharSequence cs = cm != null ? cm.getText() : null;
        if (cs == null || cs.length() == 0) { toast("剪贴板为空"); return; }
        String txt = cs.toString().trim();
        if (!txt.startsWith("xfan://")) { toast("剪贴板内容不是登录信息"); return; }
        if (handleDeepLink(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(txt)))) {
            enterMain();
        }
    }

    /** App 已在栈顶时被浏览器回调 */
    @Override
    protected void onNewIntent(Intent it) {
        super.onNewIntent(it);
        if (handleDeepLink(it)) enterMain();
    }

    private boolean checkInput() {
        String u = edUser.getText().toString().trim();
        String p = edPass.getText().toString().trim();
        if (u.isEmpty()) { toast("请填写用户名或 UID"); return false; }
        if (p.isEmpty()) { toast("请填写密码"); return false; }
        return true;
    }

    /** 从注册模式切回登录模式 */
    private void switchToLogin() {
        regMode = false;
        hide(edUid, 0);
        hide(tvUidTip, 40);
        hide(edInvite, 80);
        btnLogin.setText(R.string.btn_login);
        btnRegister.setText(R.string.btn_register);
        // 之前这里清空了提示，导致「跳到注册页再回来」后底部说明消失
        tvTip.setText(R.string.web_login_tip);
    }

    /**
     * 字段滑入：从下方 24dp 处上移 + 淡入。
     * 之前是直接 setVisibility(VISIBLE)，切换登录/注册时会突然跳一块出来。
     */
    private void reveal(final View v, long delay) {
        if (v == null || v.getVisibility() == View.VISIBLE) return;
        v.setVisibility(View.VISIBLE);
        v.setAlpha(0f);
        v.setTranslationY(24f * getResources().getDisplayMetrics().density);
        v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(260)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .setListener(null)
                .start();
    }

    /** 字段滑出：上移 + 淡出，动画结束后再 GONE，避免收得太生硬 */
    private void hide(final View v, long delay) {
        if (v == null || v.getVisibility() != View.VISIBLE) return;
        v.animate()
                .alpha(0f)
                .translationY(-16f * getResources().getDisplayMetrics().density)
                .setStartDelay(delay)
                .setDuration(200)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .setListener(new android.animation.Animator.AnimatorListener() {
                    public void onAnimationStart(android.animation.Animator a) {}
                    public void onAnimationEnd(android.animation.Animator a) {
                        v.setVisibility(View.GONE);
                        v.setAlpha(1f);
                        v.setTranslationY(0f);
                    }
                    public void onAnimationCancel(android.animation.Animator a) {}
                    public void onAnimationRepeat(android.animation.Animator a) {}
                })
                .start();
    }

    private void doLogin() {
        if (!checkInput()) return;
        final String u = edUser.getText().toString().trim();
        final String p = edPass.getText().toString().trim();
        setBusy(true, "");
        showLogging();
        new Thread(new Runnable() {
            public void run() {
                JSONObject body = new JSONObject();
                try {
                    body.put("username", u);
                    body.put("password", p);
                } catch (Exception ignore) {}
                final JSONObject r = Api.post("appapi.php?action=app_login", body);
                runOnUiThread(new Runnable() {
                    public void run() {
                        setBusy(false, "");
                        hideLogging();
                        if (r == null) { showErr("登录失败"); return; }
                        if (!r.optBoolean("success", false)) {
                            toast(r.optString("error", "登录失败"));
                            return;
                        }
                        String token = r.optString("token", "");
                        if (token.isEmpty()) { toast("服务器未返回 token"); return; }
                        // 必须存服务器返回的真实用户名：用 UID 登录时
                        // 存 UID 会让自己的消息被判成别人发的（显示在左边）
                        String real = r.optString("username", "");
                        if (real.isEmpty()) real = u;
                        Prefs.token(token);
                        Prefs.user(real);
                        Prefs.pass(p);
                        String gotUid = r.optString("uid", "");
                        if (!gotUid.isEmpty()) Prefs.uid(gotUid);
                        animateContent();
                        hTitle.postDelayed(new Runnable() {
                            public void run() { enterMain(); }
                        }, 430);
                    }
                });
            }
        }).start();
    }

    private void doRegister() {
        if (!checkInput()) return;
        final String u = edUser.getText().toString().trim();
        final String p = edPass.getText().toString().trim();
        final String inv = edInvite.getText().toString().trim();
        final String uid = edUid == null ? "" : edUid.getText().toString().trim();
        if (p.length() < 4) { toast("密码至少 4 位"); return; }

        // UID 只能英文和数字
        if (!uid.matches("[A-Za-z0-9]{3,20}")) { toast(getString(R.string.uid_invalid)); return; }

        // UID 不可更改：点「确认注册」后再弹一次警告
        confirmUid(u, uid, p, inv);
    }

    /** 二次确认：UID 一经注册不可更改 */
    private void confirmUid(final String u, final String uid,
                            final String p, final String inv) {
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.uid_confirm_title)
                .setMessage("你的 UID 将是「" + uid + "」，注册后不可更改。\n\n确定使用这个 UID 吗？")
                .setNegativeButton(R.string.uid_think_again, null)
                .setPositiveButton(R.string.uid_confirm_use,
                        new android.content.DialogInterface.OnClickListener() {
                            public void onClick(android.content.DialogInterface d, int w) {
                                doRegisterFinal(u, uid, p, inv);
                            }
                        })
                .show();
    }

    private void doRegisterFinal(final String u, final String uid,
                                 final String p, final String inv) {
        setBusy(true, "");
        showLogging();
        new Thread(new Runnable() {
            public void run() {
                JSONObject body = new JSONObject();
                try {
                    body.put("username", u);
                    body.put("password", p);
                    body.put("invite", inv);
                    body.put("uid", uid);
                } catch (Exception ignore) {}
                final JSONObject r = Api.post("appapi.php?action=app_register", body);
                runOnUiThread(new Runnable() {
                    public void run() {
                        setBusy(false, "");
                        hideLogging();
                        if (r == null) { showErr("登录失败"); return; }
                        if (!r.optBoolean("success", false)) {
                            toast(r.optString("error", "注册失败"));
                            return;
                        }
                        String token = r.optString("token", "");
                        if (!token.isEmpty()) {
                            Prefs.token(token);
                            Prefs.user(u);
                            Prefs.pass(p);
                            enterMain();
                        } else {
                            toast("注册成功，请登录");
                            regMode = false;
                            edInvite.setVisibility(View.GONE);
                            edUid.setVisibility(View.GONE);
                            tvUidTip.setVisibility(View.GONE);
                            btnLogin.setText(R.string.btn_login);
                            btnRegister.setText(R.string.btn_register);
                        }
                    }
                });
            }
        }).start();
    }

    /** 错误详情可能很长，toast 会截断 —— 用可滚动对话框完整显示 */
    private void showErr(String title) {
        String chan = "直连";
        String ck = "";
        try {
            WebBridge wb = WebBridge.get(this);
            chan = "WebView:" + (wb.isReady() ? "就绪" : "未就绪");
            ck = wb.cookies();
        } catch (Exception ignore) {}
        final String detail = "请求地址：\n" + Api.lastUrl
                + "\n\n通道：" + chan + "（开关 USE_WEBVIEW=" + Api.USE_WEBVIEW
                + "，USE_HTTP=" + Api.USE_HTTP + "）"
                + (ck.isEmpty() ? "" : "\nWebView cookie：\n" + ck)
                + "\n\n原因：\n" + (Api.lastError.isEmpty() ? "未知" : Api.lastError);
        final android.widget.TextView tv = new android.widget.TextView(this);
        tv.setText(detail);
        tv.setTextIsSelectable(true);
        tv.setTextSize(12);
        tv.setTextColor(0xFFDDDDDD);
        tv.setPadding(24, 16, 24, 16);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(tv);

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setView(sv)
                .setNegativeButton(R.string.btn_close, null);

        // 返回的是 HTML（多半被网关拦截），且还没有验证 cookie → 引导浏览器登录
        boolean blocked = Api.lastError != null && Api.lastError.contains("不是 JSON");
        if (blocked && Prefs.testCookie().isEmpty()) {
            b.setNeutralButton("用浏览器登录", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) { openWebLogin(); }
            });
        }
        b
                .setPositiveButton("复制", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        android.content.ClipboardManager cm =
                                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        cm.setText(detail);
                        toast("已复制错误详情");
                    }
                })
                .show();
    }

    private void setBusy(boolean busy, String tip) {
        btnLogin.setEnabled(!busy);
        btnRegister.setEnabled(!busy);
        tvTip.setText(tip);
    }

    private void enterMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
        // 页面切换过渡：登录页淡出，主页淡入。用系统内置动画资源，
        // 不新增 XML（Fragment 事务里曾用 XML 触发过 AnimatorInflater 报错）。
        try {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } catch (Throwable ignore) {}
    }

    /** 忘记密码：没有邮箱/手机验证，只能找站长 */
    private void showForgotDialog() {
        String msg = "请联系站长更改密码：\n\n"
                + "1. 在 mc.xfan.l.cd 新建一个账号\n"
                + "2. 在反馈页提交反馈，带上你的用户名\n\n"
                + "站长会在 48 小时内回应你。";
        new AlertDialog.Builder(this, R.style.XfanDialog)
                .setTitle("忘记密码？")
                .setMessage(msg)
                .setNegativeButton("我知道了", null)
                .setPositiveButton("打开官网", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://mc.xfan.l.cd")));
                        } catch (Throwable ignore) {
                            Toast.makeText(LoginActivity.this, "无法打开网页", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    private void showLastCrash() {
        final String log = CrashGuard.last(this);
        if (log.isEmpty()) return;
        CrashGuard.clear(this);
        final android.widget.TextView tv = new android.widget.TextView(this);
        tv.setText(log);
        tv.setTextIsSelectable(true);
        tv.setTextSize(10);
        tv.setTextColor(0xFFFF6666);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.setPadding(14, 10, 14, 10);
        sv.addView(tv);
        try {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("上次崩溃信息（长按可复制）")
                    .setView(sv)
                    .setNegativeButton(R.string.btn_close, null)
                    .setNeutralButton("复制到剪贴板", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) {
                            android.content.ClipboardManager cm =
                                    (android.content.ClipboardManager) getSystemService(
                                            android.content.Context.CLIPBOARD_SERVICE);
                            cm.setText(log);
                            Toast.makeText(LoginActivity.this, "已复制", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .show();
        } catch (Exception ignore) {}
    }

    private void toast(final String s) {
        ui(new Runnable() {
            public void run() { Toast.makeText(LoginActivity.this, s, Toast.LENGTH_SHORT).show(); }
        });
    }

    /** UI 线程执行，自动跳过已销毁状态（避免跳主界面时闪退） */
    private void ui(final Runnable r) {
        if (isFinishing()) return;
        runOnUiThread(new Runnable() {
            public void run() { if (!isFinishing()) r.run(); }
        });
    }
}
