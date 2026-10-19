package xfan.chat.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 内置 APK 下载器：在应用内直接下载新版本并拉起安装，不再跳浏览器。
 *
 * 下载走 HttpURLConnection（自带浏览器请求头，沿用 Api 的策略），
 * 落到应用专属下载目录，装完通过 ApkProvider 的 content:// 交给系统安装器。
 */
public class ApkDownloader {

    private static final Handler h = new Handler(Looper.getMainLooper());

    /**
     * 开始下载。显示进度对话框，完成后弹安装界面。
     */
    public static void start(final Activity act, final String url, final String vname) {
        if (act == null || url == null || url.isEmpty()) return;

        final String fileName = "xfan-chat-" + (vname == null ? "update" : vname) + ".apk";
        final File out = new File(ApkProvider.dir(act), fileName);

        final ProgressDialog pd = new ProgressDialog(act);
        pd.setTitle("下载新版本 " + (vname == null ? "" : vname));
        pd.setMessage("准备中…");
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(100);
        pd.setCancelable(false);
        pd.setButton(DialogInterface.BUTTON_NEGATIVE, "取消",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { cancel = true; }
                });
        pd.show();
        // 水平进度条改天蓝色（必须在 show 之后设置才生效）
        try {
            pd.setProgressDrawable(act.getResources().getDrawable(R.drawable.pb_sky));
        } catch (Throwable ignore) {}

        cancel = false;

        new Thread(new Runnable() {
            public void run() {
                HttpURLConnection c = null;
                try {
                    // 步骤 1：连接
                    post(pd, "连接中…", 0);
                    URL u = new URL(url);
                    c = (HttpURLConnection) u.openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(30000);
                    c.setInstanceFollowRedirects(true);
                    c.setUseCaches(false);
                    // 沿用 App 的浏览器请求头，避免被网关拦
                    c.setRequestProperty("User-Agent", Api.UA_BROWSER);
                    c.setRequestProperty("Accept", "*/*");
                    String ck = Prefs.testCookie();
                    if (ck != null && !ck.isEmpty()) {
                        c.setRequestProperty("Cookie", "__test=" + ck);
                    }
                    c.connect();

                    int code = c.getResponseCode();
                    if (code >= 400) {
                        fail(act, pd, "下载失败：HTTP " + code);
                        return;
                    }

                    final int total = c.getContentLength();
                    InputStream in = c.getInputStream();

                    // 先写到临时文件，成功后再改名，避免留下半截 APK
                    File tmp = new File(out.getParentFile(), fileName + ".part");
                    FileOutputStream fos = new FileOutputStream(tmp);
                    byte[] buf = new byte[16384];
                    int n;
                    int done = 0;
                    int lastPct = -1;
                    while ((n = in.read(buf)) > 0) {
                        if (cancel) {
                            fos.close(); in.close();
                            try { tmp.delete(); } catch (Throwable ignore) {}
                            h.post(new Runnable() {
                                public void run() {
                                    try { pd.dismiss(); } catch (Throwable ignore) {}
                                    Toast.makeText(act, "已取消下载", Toast.LENGTH_SHORT).show();
                                }
                            });
                            return;
                        }
                        fos.write(buf, 0, n);
                        done += n;
                        int pct = (total > 0) ? (int) (done * 100L / total) : 0;
                        if (pct != lastPct) {
                            lastPct = pct;
                            post(pd, fmt(done, total), pct);
                        }
                    }
                    fos.close();
                    in.close();

                    if (total > 0 && done < total) {
                        fail(act, pd, "下载不完整，请重试");
                        return;
                    }
                    if (out.exists()) { try { out.delete(); } catch (Throwable ignore) {} }
                    if (!tmp.renameTo(out)) {
                        fail(act, pd, "保存失败");
                        return;
                    }

                    // 步骤 2：拉起安装
                    h.post(new Runnable() {
                        public void run() {
                            try { pd.dismiss(); } catch (Throwable ignore) {}
                            install(act, out, fileName);
                        }
                    });
                } catch (Throwable t) {
                    fail(act, pd, "下载失败：" + t.getMessage());
                } finally {
                    if (c != null) { try { c.disconnect(); } catch (Throwable ignore) {} }
                }
            }
        }).start();
    }

    private static volatile boolean cancel = false;

    private static void post(final ProgressDialog pd, final String msg, final int pct) {
        h.post(new Runnable() {
            public void run() {
                try {
                    pd.setMessage(msg);
                    pd.setProgress(pct);
                } catch (Throwable ignore) {}
            }
        });
    }

    private static void fail(final Activity act, final ProgressDialog pd, final String msg) {
        h.post(new Runnable() {
            public void run() {
                try { pd.dismiss(); } catch (Throwable ignore) {}
                new AlertDialog.Builder(act, R.style.XfanDialog)
                        .setTitle("下载失败")
                        .setMessage(msg + "\n\n也可以稍后在「我的 → 检查更新」里重试。")
                        .setPositiveButton("知道了", null)
                        .show();
            }
        });
    }

    private static String fmt(int done, int total) {
        if (total <= 0) return "已下载 " + (done / 1024) + " KB";
        return (done / 1024) + " KB / " + (total / 1024) + " KB";
    }

    /** 通过 ApkProvider 的 content:// 唤起系统安装器 */
    private static void install(Activity act, File f, String fileName) {
        try {
            Uri uri = ApkProvider.uriFor(fileName);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(i);
        } catch (Throwable t) {
            new AlertDialog.Builder(act, R.style.XfanDialog)
                    .setTitle("下载完成")
                    .setMessage("已下载到：\n" + f.getAbsolutePath()
                            + "\n\n自动安装失败，请手动打开该文件安装。")
                    .setPositiveButton("知道了", null)
                    .show();
        }
    }
}
