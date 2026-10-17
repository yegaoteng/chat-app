package xfan.chat.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 全屏图片预览 */
public class ImageViewActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final String url = getIntent().getStringExtra("url");

        ImageView iv = new ImageView(this);
        iv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setBackgroundColor(0xFF000000);

        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(0xFF000000);
        root.addView(iv);
        setContentView(root);

        iv.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });

        if (url == null || url.isEmpty()) { Toast.makeText(this, "地址为空", Toast.LENGTH_SHORT).show(); return; }

        new Thread(new Runnable() {
            public void run() {
                try {
                    // 走 ImgLoader：带浏览器头与验证 cookie，否则会被 WAF 拦下
                    final Bitmap bm = ImgLoader.fetch(url, screen(), screen());
                    if (bm == null) throw new Exception("null");
                    runOnUiThread(new Runnable() {
                        public void run() { iv.setImageBitmap(bm); }
                    });
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(ImageViewActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    /** 取屏幕短边，全屏看图按这个尺寸解码即可 */
    private int screen() {
        try {
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            return Math.min(dm.widthPixels, dm.heightPixels);
        } catch (Throwable e) { return 1080; }
    }
}
