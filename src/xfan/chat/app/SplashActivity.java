package xfan.chat.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import android.widget.VideoView;

/** 开屏画面。 */
public class SplashActivity extends Activity {

    private static final int SPLASH_MS = 1500;

    private boolean gone = false;

    @Override
    protected void onCreate(Bundle b) {
        // 崩溃兜底已在 App.onCreate 安装，这里再兜一次以防万一
        try { CrashGuard.install(this); } catch (Throwable ignore) {}
        try { Prefs.init(this); }        catch (Throwable ignore) {}

        super.onCreate(b);

        try {
            setContentView(R.layout.activity_splash);
        } catch (Throwable t) {
            // 布局都加载不了就别卡在开屏了，直接进主流程
            finishGo();
            return;
        }

        // 优先播视频：有 splash.mp4 就等它播完再进，否则走图片 + 定时
        if (!showSplashVideo()) {
            try { showSplash(); } catch (Throwable ignore) {}
            new Handler().postDelayed(new Runnable() {
                public void run() { finishGo(); }
            }, SPLASH_MS);
        }
    }

    /**
     * 开屏视频：assets/splash.mp4。
     * 播放完（或出错）才进入下一步，保证视频完整播完。
     * 返回 true 表示已接管，调用方不要再走定时逻辑。
     */
    private boolean showSplashVideo() {
        VideoView vv = null;
        try { vv = findViewById(R.id.vvSplash); } catch (Throwable ignore) {}
        if (vv == null) return false;

        String hit = null;
        try {
            android.content.res.AssetManager am = getAssets();
            String[] all = am.list("");
            if (all != null) {
                for (String f : all) {
                    if (f.equalsIgnoreCase("splash.mp4")
                            || f.equalsIgnoreCase("splash.webm")
                            || f.equalsIgnoreCase("splash.3gp")) { hit = f; break; }
                }
            }
        } catch (Throwable ignore) {}
        if (hit == null) return false;

        try {
            // VideoView 只能播文件路径，先把 assets 里的视频拷到本地
            java.io.File f = new java.io.File(getFilesDir(), "splash_video.mp4");
            if (!f.exists() || f.length() == 0) {
                java.io.InputStream in = getAssets().open(hit);
                java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                fos.close();
                in.close();
            }

            final VideoView fv = vv;
            fv.setVisibility(View.VISIBLE);
            fv.setVideoPath(f.getAbsolutePath());
            fv.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener() {
                public void onPrepared(android.media.MediaPlayer mp) {
                    try {
                        mp.setLooping(false);
                        mp.setVideoScalingMode(
                                android.media.MediaPlayer
                                        .VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                        // 开屏视频按需求开启声音
                        mp.setVolume(1f, 1f);
                    } catch (Throwable ignore) {}
                    fv.start();
                }
            });
            // 播完才进登录页
            fv.setOnCompletionListener(new android.media.MediaPlayer.OnCompletionListener() {
                public void onCompletion(android.media.MediaPlayer mp) { finishGo(); }
            });
            // 出错也要进，不能卡在开屏
            fv.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener() {
                public boolean onError(android.media.MediaPlayer mp, int what, int extra) {
                    finishGo();
                    return true;
                }
            });
            return true;
        } catch (Throwable ignore) {
            return false;
        }
    }

    /** 载入开屏图：只从 assets 读（支持 jpg / png / webp）。 */
    private void showSplash() {
        ImageView iv = findViewById(R.id.ivSplash);
        if (iv == null) return;
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);

        Bitmap bm = loadFromAssets();
        if (bm != null) {
            iv.setImageBitmap(bm);
            return;
        }
        // assets 里没有图：显示纯色背景 + 应用名，不再依赖 res 资源。
        // 这样 res/drawable 下放任何格式都不会引发 Duplicate resources。
        iv.setImageDrawable(null);
        TextView tv = findViewById(R.id.tvSplashTip);
        if (tv != null) {
            tv.setVisibility(View.VISIBLE);
            tv.setText(R.string.app_name);
        }
    }

    /**
     * 从 assets 里找开屏图，按 png -> jpg -> jpeg -> webp 顺序尝试。
     * 用 inSampleSize 降采样：原图再大也不会 OOM。
     */
    private Bitmap loadFromAssets() {
        String[] names = {"splash.png", "splash.jpg", "splash.jpeg", "splash.webp"};
        try {
            android.content.res.AssetManager am = getAssets();
            String hit = null;
            for (String n : names) {
                // list() 比直接 open() 更安全：文件不存在时不会抛异常
                String[] all = am.list("");
                if (all == null) break;
                for (String f : all) {
                    if (f.equalsIgnoreCase(n)) { hit = f; break; }
                }
                if (hit != null) break;
            }
            if (hit == null) return null;

            // 第一遍：只读尺寸
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream is = am.open(hit);
            try { BitmapFactory.decodeStream(is, null, o); }
            finally { try { is.close(); } catch (Throwable ignore) {} }

            int w = o.outWidth, h = o.outHeight;
            if (w <= 0 || h <= 0) return null;

            // 按屏幕尺寸算采样率，最多解出 2 倍屏幕大小就够清晰了
            int sw = getResources().getDisplayMetrics().widthPixels;
            int sh = getResources().getDisplayMetrics().heightPixels;
            int max = Math.max(sw, sh) * 2;
            int sample = 1;
            while (Math.max(w, h) / sample > max) sample <<= 1;
            if (sample < 1) sample = 1;

            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            o2.inPreferredConfig = Bitmap.Config.RGB_565;   // 内存再省一半
            InputStream is2 = am.open(hit);
            try { return BitmapFactory.decodeStream(is2, null, o2); }
            finally { try { is2.close(); } catch (Throwable ignore) {} }
        } catch (Throwable ignore) {
            return null;
        }
    }

    /** 触摸可跳过开屏 */
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) finishGo();
        return true;
    }

    private void finishGo() {
        if (gone) return;
        gone = true;
        try {
            Intent i;
            if (!Prefs.token().isEmpty() && !Prefs.user().isEmpty()) {
                i = new Intent(this, MainActivity.class);
            } else {
                i = new Intent(this, LoginActivity.class);
            }
            startActivity(i);
        } catch (Throwable ignore) {}
        try { finish(); } catch (Throwable ignore) {}
        try {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } catch (Throwable ignore2) {}
        try { overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out); }
        catch (Throwable ignore) {}
    }

    @Override
    public void onBackPressed() {
        gone = true;
        try { finish(); } catch (Throwable ignore) {}
    }
}
