package xfan.chat.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public class LoginActivity extends Activity {

    private EditText edUser, edPass, edInvite, edUid;
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

        tvTitle  = findViewById(R.id.tvTitle);
        tvCaret  = findViewById(R.id.tvCaret);
        tvLogging= findViewById(R.id.tvLogging);
        pbLogin  = findViewById(R.id.pbLogin);
        boxLogin = findViewById(R.id.boxLogin);
        startTypewriter();

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
            public void onClick(View v) { doLogin(); }
        });

        btnRegister.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!regMode) {
                    regMode = true;
                    edInvite.setVisibility(View.VISIBLE);
                    edUid.setVisibility(View.VISIBLE);
                    tvUidTip.setVisibility(View.VISIBLE);
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
                    regMode = false;
                    edInvite.setVisibility(View.GONE);
                    edUid.setVisibility(View.GONE);
                    tvUidTip.setVisibility(View.GONE);
                    btnLogin.setText(R.string.btn_login);
                    btnRegister.setText(R.string.btn_register);
                    tvTip.setText("");
                    return true;
                }
                return false;
            }
        });
    }

    /** 标题打字机效果（带闪烁光标） */
    private void startTypewriter() {
        if (tvTitle == null) return;
        final String full = getString(R.string.app_name);
        final int[] idx = {0};

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

        Runnable type = new Runnable() {
            public void run() {
                if (tvTitle == null) return;
                if (idx[0] <= full.length()) {
                    tvTitle.setText(full.substring(0, idx[0]));
                    idx[0]++;
                    hTitle.postDelayed(this, 150);   // 每个字符 150ms
                }
            }
        };
        hTitle.postDelayed(type, 400);
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

    /** 登录中：顶部提示 + 6 秒进度条（从右往左） */
    private void showLogging() {
        hideKeyboard();
        if (tvLogging != null) tvLogging.setVisibility(View.VISIBLE);
        if (pbLogin == null) return;
        pbLogin.setVisibility(View.VISIBLE);
        pbLogin.setProgress(100);

        final int TOTAL = 6000, STEP = 60;
        final int[] left = {TOTAL};
        hTitle.post(new Runnable() {
            public void run() {
                left[0] -= STEP;
                if (left[0] < 0) left[0] = 0;
                // 从右往左：进度条随时间推移逐渐清空
                pbLogin.setProgress((int) (left[0] * 100L / TOTAL));
                if (left[0] > 0) hTitle.postDelayed(this, STEP);
            }
        });
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
                .setMessage("你的 UID 将是「" + uid + "」，注册后不可更改（仅管理员可在后台修改）。\n\n确定使用这个 UID 吗？")
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
