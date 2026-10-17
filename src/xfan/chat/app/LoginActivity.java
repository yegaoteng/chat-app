package xfan.chat.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public class LoginActivity extends Activity {

    private EditText edUser, edPass, edInvite;
    private Button btnLogin, btnRegister, btnWeb;
    private TextView tvTip;
    private boolean regMode = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        // WebView 通道要在任何请求之前初始化
        Api.applyHttpSwitch();
        Api.bindContext(this);
        setContentView(R.layout.activity_login);

        edUser   = findViewById(R.id.edUser);
        edPass   = findViewById(R.id.edPass);
        edInvite = findViewById(R.id.edInvite);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        tvTip    = findViewById(R.id.tvTip);

        btnWeb = findViewById(R.id.btnWeb);

        String u = Prefs.user();
        if (u != null && !u.isEmpty()) edUser.setText(u);

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
                    btnLogin.setText("已有账号？点此登录");
                    btnRegister.setText("确认注册");
                    tvTip.setText("注册需要邀请码");
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
                    btnLogin.setText(R.string.btn_login);
                    btnRegister.setText(R.string.btn_register);
                    tvTip.setText("");
                    return true;
                }
                return false;
            }
        });
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
        if (u.isEmpty()) { toast("请填写用户名"); return false; }
        if (p.isEmpty()) { toast("请填写密码"); return false; }
        return true;
    }

    private void doLogin() {
        if (!checkInput()) return;
        final String u = edUser.getText().toString().trim();
        final String p = edPass.getText().toString().trim();
        setBusy(true, "登录中…");
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
                        if (r == null) { showErr("登录失败"); return; }
                        if (!r.optBoolean("success", false)) {
                            toast(r.optString("error", "登录失败"));
                            return;
                        }
                        String token = r.optString("token", "");
                        if (token.isEmpty()) { toast("服务器未返回 token"); return; }
                        Prefs.token(token);
                        Prefs.user(u);
                        Prefs.pass(p);
                        enterMain();
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
        if (p.length() < 4) { toast("密码至少 4 位"); return; }
        setBusy(true, "注册中…");
        new Thread(new Runnable() {
            public void run() {
                JSONObject body = new JSONObject();
                try {
                    body.put("username", u);
                    body.put("password", p);
                    body.put("invite", inv);
                } catch (Exception ignore) {}
                final JSONObject r = Api.post("appapi.php?action=app_register", body);
                runOnUiThread(new Runnable() {
                    public void run() {
                        setBusy(false, "");
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

        new android.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setView(sv)
                .setNegativeButton(R.string.btn_close, null)
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

    private void toast(final String s) {
        runOnUiThread(new Runnable() {
            public void run() { Toast.makeText(LoginActivity.this, s, Toast.LENGTH_SHORT).show(); }
        });
    }
}
