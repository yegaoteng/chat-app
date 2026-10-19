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

        try { showSplash(); } catch (Throwable ignore) {}

        new Handler().postDelayed(new Runnable() {
            public void run() { finishGo(); }
        }, SPLASH_MS);
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
