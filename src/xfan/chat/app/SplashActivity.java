package xfan.chat.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageView;

/**
 * 开屏画面。
 *
 * 自定义图片：覆盖 app/res/drawable/splash.png（竖屏）
 * 与 app/res/drawable-land/splash.png（横屏）即可，无需改代码。
 *
 * 停留 SPLASH_MS 毫秒后跳转：已登录 -> MainActivity，否则 -> LoginActivity。
 * 期间同时预热图片缓存目录，让进入聊天页后图片出得更快。
 */
public class SplashActivity extends Activity {

    /** 开屏停留时长 */
    private static final int SPLASH_MS = 1500;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { setContentView(R.layout.activity_splash); }
        catch (Exception e) { finishGo(); return; }

        // 横竖屏都用同一张图，靠 centerCrop 裁切填满，不会变形
        ImageView iv = findViewById(R.id.ivSplash);
        if (iv != null) iv.setScaleType(ImageView.ScaleType.CENTER_CROP);

        // 预热：初始化图片缓存（磁盘 LruCache），避免首屏图片卡顿
        new Thread(new Runnable() {
            public void run() {
                try { ImgLoader.init(SplashActivity.this); } catch (Exception ignore) {}
            }
        }).start();

        new Handler().postDelayed(new Runnable() {
            public void run() { finishGo(); }
        }, SPLASH_MS);
    }

    /** 触摸可跳过开屏 */
    @Override
    public boolean onTouchEvent(android.view.MotionEvent e) {
        if (e.getAction() == android.view.MotionEvent.ACTION_UP) finishGo();
        return true;
    }

    private boolean gone = false;

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
        } catch (Exception ignore) {}
        finish();
        // 淡入下一个页面，避免生硬切换
        try { overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out); }
        catch (Exception ignore) {}
    }

    @Override
    public void onBackPressed() {
        // 开屏期间按返回直接退出，不卡住
        gone = true;
        finish();
    }
}
