package xfan.chat.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;

import org.json.JSONObject;

/** 版本更新：启动自动检查 + 我的页手动检查。 */
public class UpdateManager {

    /** 本地版本名，如 "1.0.1" */
    private static String localName(Activity act) {
        try {
            PackageInfo pi = act.getPackageManager()
                    .getPackageInfo(act.getPackageName(), 0);
            return pi.versionName == null ? "0" : pi.versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    private static int localCode(Activity act) {
        try {
            return act.getPackageManager()
                    .getPackageInfo(act.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 把 "1.0.1" 这类版本名拆成整数数组，便于逐段比较。
     * 非数字段按 0 处理，长度不足补 0。
     */
    private static int[] parseVer(String v) {
        String[] p = (v == null ? "" : v.trim()).split("\\.");
        int[] out = new int[Math.max(p.length, 3)];
        for (int i = 0; i < out.length; i++) {
            int n = 0;
            if (i < p.length) {
                try {
                    n = Integer.parseInt(p[i].replaceAll("[^0-9]", ""));
                } catch (Exception e) {
                    n = 0;
                }
            }
            out[i] = n;
        }
        return out;
    }

    /** a > b 返回正数，相等返回 0，a < b 返回负数 */
    private static int cmpVer(String a, String b) {
        int[] x = parseVer(a);
        int[] y = parseVer(b);
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            int p = i < x.length ? x[i] : 0;
            int q = i < y.length ? y[i] : 0;
            if (p != q) return p - q;
        }
        return 0;
    }

    /** 启动自动检查：静默，无更新不打扰 */
    public static void check(final Activity act) {
        check(act, false);
    }

    /** 手动检查：无论有没有更新都要给反馈 */
    public static void checkManual(final Activity act) {
        check(act, true);
    }

    private static void check(final Activity act, final boolean manual) {
        new Thread(new Runnable() {
            public void run() {
                final JSONObject r = Api.get("appapi.php?action=app_update");

                if (r == null || !r.optBoolean("success", false)) {
                    if (manual) toast(act, "检查失败：" + (Api.lastError == null
                            ? "网络错误" : Api.lastError));
                    return;
                }

                final int remoteCode = r.optInt("version_code", 0);
                final String remoteName = r.optString("version_name", "");
                final String log = r.optString("changelog", "");
                final String url = r.optString("download_url", "");

                final int localCode = localCode(act);
                final String localName = localName(act);

                // 有更新 = 版本码更高，或版本码相同但服务器版本名更高
                boolean hasUpdate;
                if (remoteCode > localCode) {
                    hasUpdate = true;
                } else if (remoteCode == localCode && remoteCode > 0) {
                    hasUpdate = cmpVer(remoteName, localName) > 0;
                } else {
                    hasUpdate = false;
                }

                if (!hasUpdate) {
                    if (manual) toast(act, "已是最新版本（" + localName + "）");
                    return;
                }

                act.runOnUiThread(new Runnable() {
                    public void run() {
                        if (url.isEmpty()) {
                            // 有新版本但后台没填下载地址：提示但不给按钮
                            if (manual) {
                                new AlertDialog.Builder(act)
                                        .setTitle("发现新版本 " + remoteName)
                                        .setMessage("服务器已发布新版本，但后台未填写下载地址。")
                                        .setPositiveButton("知道了", null)
                                        .show();
                            }
                            return;
                        }
                        show(act, remoteName, log, url);
                    }
                });
            }
        }).start();
    }

    private static void toast(final Activity act, final String msg) {
        act.runOnUiThread(new Runnable() {
            public void run() {
                android.widget.Toast.makeText(act, msg,
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static void show(final Activity act, String vname, String log, final String url) {
        String msg = (log == null || log.isEmpty()) ? "服务器上有新版本" : log;
        AlertDialog d = new AlertDialog.Builder(act)
                .setTitle("发现新版本 " + (vname == null ? "" : vname))
                .setMessage(msg)
                .setCancelable(true)
                .setNegativeButton("稍后再说", null)
                .setPositiveButton("下载更新", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d2, int w) {
                        try {
                            act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        } catch (Exception ignore) {}
                    }
                })
                .create();
        if (!act.isFinishing()) d.show();
    }
}
