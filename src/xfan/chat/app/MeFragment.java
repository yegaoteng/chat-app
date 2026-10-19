package xfan.chat.app;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;

public class MeFragment extends Fragment {

    public MainActivity host;
    private ImageView ivAvatar;
    private TextView tvName, tvBio, tvPts, tvVer, tvUid;
    private Button btnSign, btnInvite, btnBio, btnRename, btnAvatar, btnLogout, btnUpdate;
    private Button btnDisplay;
    private Button btnProfileToggle, btnPassword;
    private LinearLayout boxProfile;
    private boolean profileOpen = false;
    private boolean animating = false;
    private ProgressBar pbUpdate;
    private android.widget.ProgressBar pbSign;
    private String inviteCode = "";
    private String myUid = "";
    private String avatarUrl = "";
    private String bio = "";
    private boolean signed = false;

    private static final int REQ_AVATAR = 201;

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup c, Bundle b) {
        View v = inf.inflate(R.layout.frag_me, c, false);
        ivAvatar = v.findViewById(R.id.ivAvatar);
        tvName = v.findViewById(R.id.tvName);
        tvBio = v.findViewById(R.id.tvBio);
        tvUid = v.findViewById(R.id.tvUid);
        tvPts = v.findViewById(R.id.tvPts);
        tvVer = v.findViewById(R.id.tvVer);
        btnSign = v.findViewById(R.id.btnSign);
        pbSign = v.findViewById(R.id.pbSign);
        btnInvite = v.findViewById(R.id.btnInvite);
        btnBio = v.findViewById(R.id.btnBio);
        btnRename = v.findViewById(R.id.btnRename);
        btnAvatar = v.findViewById(R.id.btnAvatar);
        btnLogout = v.findViewById(R.id.btnLogout);
        btnUpdate = v.findViewById(R.id.btnUpdate);
        btnDisplay = v.findViewById(R.id.btnDisplay);
        pbUpdate  = v.findViewById(R.id.pbUpdate);
        btnProfileToggle = v.findViewById(R.id.btnProfileToggle);
        btnPassword = v.findViewById(R.id.btnPassword);
        boxProfile = v.findViewById(R.id.boxProfile);
        if (btnProfileToggle != null && boxProfile != null) {
            btnProfileToggle.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v2) { toggleProfile(); }
            });
        }
        if (btnPassword != null) {
            btnPassword.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v2) { showChangePassword(); }
            });
        }

        try {
            tvVer.setText("v" + getActivity().getPackageManager()
                    .getPackageInfo(getActivity().getPackageName(), 0).versionName);
        } catch (Exception ignore) {}
        // 点版本号 -> 打开官网
        if (tvVer != null) {
            tvVer.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v3) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://xiaof.hyperphp.com/chatapp/")));
                    } catch (Throwable ignore) { toast("无法打开网页"); }
                }
            });
        }

        // 点头像 -> 全屏查看自己的头像
        ivAvatar.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v2) { viewAvatar(); }
        });

        btnSign.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v2) { doSign(); }
        });
        btnInvite.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v2) {
                if (inviteCode.isEmpty()) { toast("暂无邀请码"); return; }
                android.content.ClipboardManager cm =
                        (android.content.ClipboardManager) getActivity()
                                .getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                cm.setText(inviteCode);
                toast("已复制：" + inviteCode);
            }
        });
        btnBio.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v2) { editBio(); }
        });
        btnRename.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v2) { editName(); }
        });
        btnAvatar.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v2) { pickAvatar(); }
        });
        btnUpdate.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v2) { checkUpdate(); }
        });
        // 显示设置：选帧率（60/90/120/144）
        // 「显示设置」改为「设置」：里层再放显示设置与通知权限
        if (btnDisplay != null) btnDisplay.setText("设置");
        btnDisplay.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v2) { showSettings(); }
        });
        refreshFpsLabel();
        btnLogout.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v2) {
                new AlertDialog.Builder(getActivity(), R.style.XfanDialog)
                        .setTitle("退出登录")
                        .setMessage("确定退出吗？")
                        .setNegativeButton(R.string.btn_cancel, null)
                        .setPositiveButton("退出", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                Prefs.clearSession();
                                startActivity(new Intent(getActivity(), LoginActivity.class));
                                getActivity().finish();
                            }
                        }).show();
            }
        });
        applyTheme();
        // 恢复上次选的帧率
        if (getActivity() != null) Fps.apply(getActivity(), Prefs.fps());
        // 视图刚建好：如果资料早就回来了，这里补一次
        // 先用本地缓存填一次，避免刚进来右上角/积分位是空的、等网络回来才补
        if (tvPts != null) tvPts.setText(Prefs.points() + " 积分");
        if (tvName != null && !Prefs.user().isEmpty()) tvName.setText(Prefs.user());
        if (tvUid != null && !Prefs.uid().isEmpty()) tvUid.setText("UID：" + Prefs.uid());

        if (pending != null) applyProfile(pending);
        else if (host != null) host.loadProfile();
        return v;
    }

    public void onShow() { if (host != null) host.loadProfile(); }

    /** 视图是否已创建：数据可能在 onCreateView 之前就回来了，直接写控件会 NPE */
    private boolean ready() {
        return getActivity() != null && getView() != null && tvName != null;
    }

    /** 缓存最近一次资料，视图就绪后再补上，避免数据丢失 */
    private JSONObject pending = null;

    public void applyProfile(JSONObject r) {
        if (r == null) return;
        if (!ready()) { pending = r; return; }   // 视图没好，先存着
        pending = null;
        String name = r.optString("username", "");
        tvName.setText(name);
        // 用户名下方显示注册 UID（稳定身份，改昵称不变）
        myUid = r.optString("uid", "");
        if (tvUid != null) {
            tvUid.setText(myUid.isEmpty() ? "" : ("UID：" + myUid));
        }
        tvPts.setText(r.optInt("points", 0) + " 积分");
        inviteCode = r.optString("invite_code", "");
        // 按钮上直接显示真实邀请码，不用点进去才知道
        if (btnInvite != null) {
            btnInvite.setText(inviteCode.isEmpty()
                    ? getString(R.string.me_invite)
                    : (getString(R.string.me_invite) + "（" + inviteCode + "）"));
        }
        // 改名按钮上提示当前是否可改
        int left = r.optInt("rename_left", 0);
        if (btnRename != null) {
            btnRename.setText(left > 0
                    ? (getString(R.string.me_rename) + "（还需 " + fmtLeft(left) + "）")
                    : (getString(R.string.me_rename) + "（可修改）"));
            btnRename.setEnabled(left <= 0);
        }
        bio = r.optString("bio", "");
        tvBio.setText(bio.isEmpty() ? "这个人很懒，没有留下简介" : bio);
        signed = r.optBoolean("signed", false);
        btnSign.setText(signed ? "今日已签到" : "每日签到 (+50)");
        btnSign.setEnabled(!signed);

        String av = r.optString("avatar", "");
        avatarUrl = av;
        // 我的页头像要看得清，按屏幕短边解码（之前固定 200px 显得糊）
        if (!av.isEmpty()) new ImgLoader(ivAvatar).load(Api.absUrl(av), 720, 720);
        else if (ivAvatar != null) ivAvatar.setImageDrawable(new Avatars(name));
        applyTheme();
    }

    private void doSign() {
        if (btnSign == null) return;
        final String oldText = btnSign.getText().toString();
        btnSign.setEnabled(false);
        btnSign.setText("签到中…");
        if (pbSign != null) pbSign.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            public void run() {
                final JSONObject r = Api.post("user.php?action=sign_in", new JSONObject());
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (btnSign == null) return;
                        if (pbSign != null) pbSign.setVisibility(View.GONE);
                        if (r != null && r.optBoolean("success", false)) {
                            toast("签到成功 +" + r.optInt("reward", 50));
                            signed = true;
                            btnSign.setText("今日已签到");
                        } else {
                            toast(r == null ? ("网络错误\n" + Api.lastError)
                                    : r.optString("error", "签到失败"));
                            btnSign.setEnabled(true);
                            btnSign.setText(signed ? "今日已签到" : oldText);
                        }
                        if (host != null) host.refreshPoints();
                    }
                });
            }
        }).start();
    }

    /** 应用个性化主题 */
    public void applyTheme() {
        View v = getView();
        if (v == null) return;
        try {
            v.setBackgroundColor(ThemeEngine.themeBg() == -1 ? 0x00000000 : ThemeEngine.themeBg());
            if (tvName != null) {
                tvName.setTextColor(ThemeEngine.themeText());
                ThemeEngine.applyNameColor(tvName, null);
            }
            if (tvBio != null) tvBio.setTextColor(ThemeEngine.themeTextSub());
            if (tvPts != null) tvPts.setTextColor(ThemeEngine.themeTextSub());
        } catch (Exception ignore) {}
    }

    /** 展开 / 收起「修改个人资料」，带高度过渡动画 */
    private void toggleProfile() {
        if (boxProfile == null || btnProfileToggle == null) return;
        profileOpen = !profileOpen;
        btnProfileToggle.setText(profileOpen ? "修改个人资料 ▾" : "修改个人资料 ▸");

        // 动画期间禁止重复触发，否则高度测量会乱
        if (animating) return;
        animating = true;

        // 先量出目标高度（此时高度为 0，需要先设置成 WRAP_CONTENT 量一次）
        boxProfile.setVisibility(View.VISIBLE);
        boxProfile.measure(
                View.MeasureSpec.makeMeasureSpec(
                        ((View) boxProfile.getParent()).getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        final int target = boxProfile.getMeasuredHeight();
        final int start = profileOpen ? 0 : target;
        final int end = profileOpen ? target : 0;

        android.animation.ValueAnimator va =
                android.animation.ValueAnimator.ofInt(start, end);
        va.setDuration(260);
        va.setInterpolator(new android.view.animation.DecelerateInterpolator());
        va.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(android.animation.ValueAnimator a) {
                int v = (Integer) a.getAnimatedValue();
                android.view.ViewGroup.LayoutParams lp = boxProfile.getLayoutParams();
                if (lp == null) return;
                lp.height = v;
                boxProfile.setLayoutParams(lp);
            }
        });
        va.addListener(new android.animation.Animator.AnimatorListener() {
            public void onAnimationStart(android.animation.Animator a) {}
            public void onAnimationEnd(android.animation.Animator a) {
                animating = false;
                if (!profileOpen) boxProfile.setVisibility(View.GONE);
                android.view.ViewGroup.LayoutParams lp = boxProfile.getLayoutParams();
                if (lp != null) {
                    lp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                    boxProfile.setLayoutParams(lp);
                }
            }
            public void onAnimationCancel(android.animation.Animator a) {
                animating = false;
            }
            public void onAnimationRepeat(android.animation.Animator a) {}
        });
        try { va.start(); } catch (Throwable t) { animating = false; }
    }

    /** 修改密码：原密码 + 新密码 */
    private void showChangePassword() {
        if (getActivity() == null) return;
        LinearLayout box = new LinearLayout(getActivity());
        box.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (16 * getResources().getDisplayMetrics().density + 0.5f);
        box.setPadding(p, p / 2, p, 0);

        int gap = (int) (6 * getResources().getDisplayMetrics().density + 0.5f);
        final EditText oldPw = field("原密码", true);
        final EditText newPw = field("新密码（至少 4 位）", true);
        final EditText newPw2 = field("确认新密码", true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = gap;
        box.addView(oldPw, lp);
        box.addView(newPw, lp);
        box.addView(newPw2, lp);

        new AlertDialog.Builder(getActivity(), R.style.XfanDialog)
                .setTitle("修改密码")
                .setView(box)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton("确认修改", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String o = oldPw.getText().toString();
                        String n = newPw.getText().toString();
                        String n2 = newPw2.getText().toString();
                        if (o.isEmpty() || n.isEmpty()) { toast("请填写完整"); return; }
                        if (n.length() < 4) { toast("新密码至少 4 位"); return; }
                        if (!n.equals(n2)) { toast("两次输入的新密码不一致"); return; }
                        doChangePassword(o, n);
                    }
                })
                .show();
    }

    /** 弹窗里的输入框：带下划线（未选中黑线，聚焦时天蓝线从中心展开） */
    private EditText field(String hint, boolean password) {
        EditText et = new EditText(getActivity());
        et.setHint(hint);
        et.setHintTextColor(0xFF888888);
        et.setTextColor(0xFF222222);
        et.setTextSize(14);
        et.setSingleLine(true);
        et.setBackgroundColor(0x00000000);
        if (password) {
            et.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        Underline.attach(et);
        return et;
    }

    private void doChangePassword(final String oldPw, final String newPw) {
        toast("提交中…");
        new Thread(new Runnable() {
            public void run() {
                JSONObject b = new JSONObject();
                try {
                    b.put("old_password", oldPw);
                    b.put("new_password", newPw);
                } catch (Exception ignore) {}
                final JSONObject r = Api.post("user.php?action=change_password", b);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null) { toast("网络错误"); return; }
                        if (!r.optBoolean("success", false)) {
                            toast(r.optString("error", "修改失败"));
                            return;
                        }
                        toast("密码已修改");
                    }
                });
            }
        }).start();
    }

    private void editBio() {
        final EditText ed = new EditText(getActivity());
        ed.setText(bio.equals("这个人很懒，没有留下简介") ? "" : bio);
        ed.setSingleLine(false);
        new AlertDialog.Builder(getActivity(), R.style.XfanDialog)
                .setTitle(R.string.me_bio)
                .setView(ed)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        final String v = ed.getText().toString().trim();
                        new Thread(new Runnable() {
                            public void run() {
                                JSONObject b = new JSONObject();
                                try { b.put("bio", v); } catch (Exception ignore) {}
                                final JSONObject r = Api.post("user.php?action=set_bio", b);
                                if (getActivity() == null) return;
                                getActivity().runOnUiThread(new Runnable() {
                                    public void run() {
                                        toast(r != null && r.optBoolean("success", false)
                                                ? "已保存" : "保存失败");
                                        if (host != null) host.loadProfile();
                                    }
                                });
                            }
                        }).start();
                    }
                }).show();
    }

    /** 把剩余冷却秒数转成「x天x小时」 */
    private String fmtLeft(int sec) {
        if (sec <= 0) return "可修改";
        int d = sec / 86400;
        int h = (sec % 86400) / 3600;
        if (d > 0) return d + " 天";
        if (h > 0) return h + " 小时";
        return Math.max(1, sec / 60) + " 分钟";
    }

    private void editName() {
        final EditText ed = new EditText(getActivity());
        ed.setHint("2-20 个字符");
        new AlertDialog.Builder(getActivity(), R.style.XfanDialog)
                .setTitle(R.string.me_rename)
                .setView(ed)
                .setMessage("每 7 天只能修改一次")
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        final String v = ed.getText().toString().trim();
                        if (v.length() < 2) { toast("至少 2 个字符"); return; }
                        new Thread(new Runnable() {
                            public void run() {
                                JSONObject b = new JSONObject();
                                try { b.put("username", v); } catch (Exception ignore) {}
                                final JSONObject r = Api.post("user.php?action=rename", b);
                                if (getActivity() == null) return;
                                getActivity().runOnUiThread(new Runnable() {
                                    public void run() {
                                        if (r != null && r.optBoolean("success", false)) {
                                            Prefs.user(v);
                                            toast("已修改，正在刷新…");
                                            if (host != null) host.loadProfile();
                                        } else {
                                            toast(r == null ? "网络错误" : r.optString("error", "修改失败"));
                                        }
                                    }
                                });
                            }
                        }).start();
                    }
                }).show();
    }

    /** 按钮上直接显示当前帧率档位 */
    /**
     * 设置弹窗：
     *   - 显示设置：按钮，点进去是二级弹窗（帧率档位）
     *   - 通知权限：开关，打开后向系统申请；没授权就保持关闭
     */
    private void showSettings() {
        if (getActivity() == null) return;
        android.widget.LinearLayout box = new android.widget.LinearLayout(getActivity());
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (14 * getResources().getDisplayMetrics().density + 0.5f);
        box.setPadding(pad, pad, pad, pad);

        // 显示设置（按钮）
        final android.widget.TextView rowFps = new android.widget.TextView(getActivity());
        rowFps.setText("显示设置（当前 " + fpsLabel() + "）");
        rowFps.setTextColor(0xFF222222);
        rowFps.setTextSize(15);
        rowFps.setPadding(pad, pad, pad, pad);
        Ui.outline(rowFps, 0xFFF5F5F7, 0xFF2979FF);
        rowFps.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v2) {
                Fps.show(getActivity(), new Runnable() {
                    public void run() {
                        refreshFpsLabel();
                        rowFps.setText("显示设置（当前 " + fpsLabel() + "）");
                    }
                });
            }
        });
        box.addView(rowFps);

        // 通知权限（开关）
        android.widget.LinearLayout rowN = new android.widget.LinearLayout(getActivity());
        rowN.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        rowN.setGravity(android.view.Gravity.CENTER_VERTICAL);
        rowN.setPadding(pad, pad, pad, pad);
        Ui.outline(rowN, 0xFFF5F5F7, 0xFF2979FF);

        android.widget.TextView lab = new android.widget.TextView(getActivity());
        lab.setText("通知权限");
        lab.setTextColor(0xFF222222);
        lab.setTextSize(15);
        lab.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        rowN.addView(lab);

        final android.widget.Switch sw = new android.widget.Switch(getActivity());
        sw.setChecked(Notify.granted(getActivity()));
        sw.setOnCheckedChangeListener(
                new android.widget.CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                        if (!on) { toast("已关闭通知"); return; }
                        MainActivity a = (getActivity() instanceof MainActivity)
                                ? (MainActivity) getActivity() : null;
                        if (a == null) return;
                        if (Notify.granted(a)) return;
                        if (android.os.Build.VERSION.SDK_INT < 33) {
                            toast("当前系统无需额外授权");
                            return;
                        }
                        // 用户之前拒过：系统不会再弹框，直接送去设置页
                        if (a.shouldShowRequestPermissionRationale(
                                android.Manifest.permission.POST_NOTIFICATIONS)) {
                            toast("通知曾被拒绝，请在系统设置里开启");
                            try {
                                a.startActivity(new android.content.Intent(
                                        android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,
                                                a.getPackageName()));
                            } catch (Throwable ignore) {}
                            sw.setChecked(false);
                            return;
                        }
                        // 授权结果回来后同步开关（见 MainActivity.onRequestPermissionsResult）
                        MainActivity.notifyCallback(new Runnable() {
                            public void run() { sw.setChecked(Notify.granted(getActivity())); }
                        });
                        a.ensureNotifyPermission();
                        toast("请在弹出的系统框里允许通知");
                    }
                });
        rowN.addView(sw);

        android.widget.LinearLayout.LayoutParams lpN =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lpN.topMargin = pad / 2;
        box.addView(rowN, lpN);

        android.app.AlertDialog d = new android.app.AlertDialog.Builder(getActivity())
                .setTitle("设置")
                .setView(box)
                .setPositiveButton("关闭", null)
                .create();
        d.show();
        Ui.outlineButtons(d);
    }

    /** 帧率的简短文案 */
    private String fpsLabel() {
        int v = Prefs.fps();
        return v <= 0 ? "跟随系统" : (v + " Hz");
    }

    /** 按钮就叫「设置」，帧率档位放到二级弹窗里显示 */
    private void refreshFpsLabel() {
        if (btnDisplay == null) return;
        btnDisplay.setText("设置");
    }

    /** 全屏查看自己的头像；没有头像时提示先上传 */
    private void viewAvatar() {
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            toast("还没有设置头像");
            return;
        }
        try {
            startActivity(new Intent(getActivity(), ImageViewActivity.class)
                    .putExtra("url", Api.absUrl(avatarUrl)));
        } catch (Exception ignore) {}
    }

    /** 手动检查更新：无论结果如何都要给反馈 */
    private void checkUpdate() {
        if (getActivity() == null) return;
        if (btnUpdate != null) btnUpdate.setVisibility(View.GONE);
        if (pbUpdate != null) pbUpdate.setVisibility(View.VISIBLE);
        // 转圈至少可见一小会儿，避免闪一下看不出在检查
        new android.os.Handler().postDelayed(new Runnable() {
            public void run() {
                if (getActivity() == null) return;
                if (pbUpdate != null) pbUpdate.setVisibility(View.GONE);
                if (btnUpdate != null) btnUpdate.setVisibility(View.VISIBLE);
                UpdateManager.checkManual(getActivity());
            }
        }, 350);
    }

    private void pickAvatar() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, REQ_AVATAR);
    }

    @Override
    public void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_AVATAR || res != getActivity().RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        String p = Resolver.getPath(getActivity(), uri);
        if (p == null || !(new File(p).exists())) { toast("无法读取该文件"); return; }
        final File f = new File(p);
        new Thread(new Runnable() {
            public void run() {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    public void run() { toast("上传中…"); }
                });
                final JSONObject r = Api.upload("user.php?action=upload_avatar", f, "image",
                        new HashMap<String, String>());
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null) { toast("上传失败"); return; }
                        if (!r.optBoolean("success", false)) {
                            toast(r.optString("error", "上传失败"));
                            return;
                        }
                        toast("上传完成");
                        if (host != null) host.loadProfile();
                    }
                });
            }
        }).start();
    }

    private void showUploads() {
        new Thread(new Runnable() {
            public void run() {
                final JSONObject r = Api.get("user.php?action=my_uploads");
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null) { toast("网络错误"); return; }
                        org.json.JSONArray g = r.optJSONArray("groups");
                        if (g == null || g.length() == 0) { toast("还没有上传记录"); return; }
                        final java.util.List<String> urls = new java.util.ArrayList<String>();
                        final java.util.List<String> keys = new java.util.ArrayList<String>();
                        final java.util.List<String> labels = new java.util.ArrayList<String>();
                        for (int i = 0; i < g.length(); i++) {
                            JSONObject day = g.optJSONObject(i);
                            if (day == null) continue;
                            org.json.JSONArray items = day.optJSONArray("items");
                            if (items == null) continue;
                            for (int j = 0; j < items.length(); j++) {
                                JSONObject it = items.optJSONObject(j);
                                if (it == null) continue;
                                String u = it.optString("url", "");
                                if (u.isEmpty()) continue;
                                urls.add(u);
                                keys.add(it.optString("name", ""));
                                labels.add(it.optString("name", "图片")
                                        + "（删除返还 " + it.optInt("refund", 0) + "）");
                            }
                        }
                        if (urls.isEmpty()) { toast("还没有上传记录"); return; }
                        new AlertDialog.Builder(getActivity(), R.style.XfanDialog)
                                .setTitle("我的上传（点击进入操作）")
                                .setItems(labels.toArray(new String[0]),
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface d, int w) {
                                                uploadMenu(urls.get(w), labels.get(w), keys.get(w));
                                            }
                                        }).show();
                    }
                });
            }
        }).start();
    }

    private void uploadMenu(final String url, final String label, final String fileKey) {
        new AlertDialog.Builder(getActivity(), R.style.XfanDialog)
                .setTitle(label)
                .setItems(new String[]{"查看大图", "删除并返还积分"},
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                if (w == 0) {
                                    startActivity(new Intent(getActivity(), ImageViewActivity.class)
                                            .putExtra("url", Api.absUrl(url)));
                                } else {
                                    delUpload(fileKey);
                                }
                            }
                        }).show();
    }

    private void delUpload(final String name) {
        new Thread(new Runnable() {
            public void run() {
                JSONObject b = new JSONObject();
                try { b.put("name", name); } catch (Exception ignore) {}
                final JSONObject r = Api.post("user.php?action=delete_upload", b);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        toast(r != null && r.optBoolean("success", false) ? "已删除" : "删除失败");
                        if (host != null) host.refreshPoints();
                    }
                });
            }
        }).start();
    }

    private void toast(final String s) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(new Runnable() {
            public void run() { Toast.makeText(getActivity(), s, Toast.LENGTH_SHORT).show(); }
        });
    }
}
