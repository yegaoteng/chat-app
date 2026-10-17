package xfan.chat.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONObject;

/** 启动自动检查服务器上的最新版本 */
public class UpdateManager {

    public static void check(final Activity act) {
        new Thread(new Runnable() {
            public void run() {
                final JSONObject r = Api.get("appapi.php?action=app_update");
                if (r == null || !r.optBoolean("success", false)) return;

                final int remote = r.optInt("version_code", 0);
                final String vname = r.optString("version_name", "");
                final String log = r.optString("changelog", "");
                final String url = r.optString("download_url", "");

                int local = 0;
                try {
                    local = act.getPackageManager()
                            .getPackageInfo(act.getPackageName(), 0).versionCode;
                } catch (Exception ignore) {}

                if (remote <= local || url.isEmpty()) return;

                act.runOnUiThread(new Runnable() {
                    public void run() {
                        show(act, vname, log, url);
                    }
                });
            }
        }).start();
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
