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
        pbUpdate  = v.findViewById(R.id.pbUpdate);

        try {
            tvVer.setText("v" + getActivity().getPackageManager()
                    .getPackageInfo(getActivity().getPackageName(), 0).versionName);
        } catch (Exception ignore) {}

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
        btnLogout.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v2) {
                new AlertDialog.Builder(getActivity())
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
        // 视图刚建好：如果资料早就回来了，这里补一次
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
        tvPts.setText(r.optInt("points", 0) + " 分");
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
        if (!av.isEmpty()) new ImgLoader(ivAvatar).load(Api.absUrl(av), 200, 200);
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

    private void editBio() {
        final EditText ed = new EditText(getActivity());
        ed.setText(bio.equals("这个人很懒，没有留下简介") ? "" : bio);
        ed.setSingleLine(false);
        new AlertDialog.Builder(getActivity())
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
        new AlertDialog.Builder(getActivity())
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
                        new AlertDialog.Builder(getActivity())
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
        new AlertDialog.Builder(getActivity())
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
