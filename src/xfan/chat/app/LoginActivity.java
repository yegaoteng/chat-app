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

    private EditText edServer, edUser, edPass, edInvite;
    private Button btnLogin, btnRegister;
    private TextView tvTip;
    private boolean regMode = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        setContentView(R.layout.activity_login);

        edServer = findViewById(R.id.edServer);
        edUser   = findViewById(R.id.edUser);
        edPass   = findViewById(R.id.edPass);
        edInvite = findViewById(R.id.edInvite);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        tvTip    = findViewById(R.id.tvTip);

        String s = Prefs.server();
        if (s != null && !s.isEmpty()) edServer.setText(s);
        String u = Prefs.user();
        if (u != null && !u.isEmpty()) edUser.setText(u);

        // 已保存 token 直接尝试进入
        if (!Prefs.token().isEmpty()) {
            enterMain();
        }

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

    private boolean checkInput() {
        String s = edServer.getText().toString().trim();
        String u = edUser.getText().toString().trim();
        String p = edPass.getText().toString().trim();
        if (s.isEmpty()) { toast("请填写服务器地址"); return false; }
        if (u.isEmpty()) { toast("请填写用户名"); return false; }
        if (p.isEmpty()) { toast("请填写密码"); return false; }
        Prefs.server(s);
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
                        if (r == null) { toast("网络错误\n" + Api.lastUrl); return; }
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
                        if (r == null) { toast("网络错误\n" + Api.lastUrl); return; }
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
