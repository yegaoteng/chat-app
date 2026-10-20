package xfan.chat.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.TextView;

/** 全屏图片预览：支持双指缩放、拖动，点一下关闭 */
public class ImageViewActivity extends Activity {

    private ImageView iv;
    private final Matrix mx = new Matrix();
    private final Matrix saved = new Matrix();
    private static final int NONE = 0, DRAG = 1, ZOOM = 2;
    private int mode = NONE;
    private final PointF start = new PointF();
    private final PointF mid = new PointF();
    private float oldDist = 1f;
    private float scale = 1f;
    private boolean loaded = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final String url = getIntent().getStringExtra("url");

        iv = new ImageView(this);
        iv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.MATRIX);
        iv.setBackgroundColor(0xFF000000);

        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(0xFF000000);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(iv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f));

        // 底部「保存到相册」：用下载中心断点续传到系统 Download 目录。
        // 需求要求不弹提示，所以这里静默开始，进度去下载页看。
        TextView save = new TextView(this);
        save.setText("保存到相册");
        save.setTextColor(0xFFFFFFFF);
        save.setTextSize(15);
        save.setGravity(android.view.Gravity.CENTER);
        save.setPadding(0, 26, 0, 26);
        save.setBackgroundResource(R.drawable.bg_login_btn);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        int m = (int) (12 * getResources().getDisplayMetrics().density);
        sp.setMargins(m, m, m, m * 2);
        save.setLayoutParams(sp);
        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { saveToAlbum(url); }
        });
        root.addView(save);

        setContentView(root);

        // 单击关闭：未拖动过才算单击，避免缩放时误关
        iv.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        saved.set(mx);
                        start.set(e.getX(), e.getY());
                        mode = DRAG;
                        break;
                    case MotionEvent.ACTION_POINTER_DOWN:
                        oldDist = spacing(e);
                        if (oldDist > 10f) {
                            saved.set(mx);
                            midPoint(mid, e);
                            mode = ZOOM;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_POINTER_UP:
                        if (mode == DRAG && spacing(e) < 1f
                                && moved(start, e) < 10f && !zoomed()) {
                            finish();
                        }
                        mode = NONE;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (mode == DRAG) {
                            mx.set(saved);
                            mx.postTranslate(e.getX() - start.x, e.getY() - start.y);
                        } else if (mode == ZOOM && e.getPointerCount() >= 2) {
                            float nd = spacing(e);
                            if (nd > 10f) {
                                mx.set(saved);
                                float s = nd / oldDist;
                                scale = Math.min(Math.max(scale * s, 0.5f), 8f);
                                mx.postScale(s, s, mid.x, mid.y);
                            }
                        }
                        break;
                }
                if (loaded) iv.setImageMatrix(mx);
                return true;
            }
        });

        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "地址为空", Toast.LENGTH_SHORT).show();
            return;
        }

        // 原图解码上限：按屏幕短边的 2.5 倍，放大后细节够用，
        // 且不会影响列表里那张缩略图（缓存按尺寸分桶）。
        final int want = (int) (screen() * 2.5f);
        Toast.makeText(this, "加载中…", Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            public void run() {
                try {
                    final Bitmap bm = ImgLoader.fetch(url, want, want);
                    if (bm == null) throw new Exception("null");
                    runOnUiThread(new Runnable() {
                        public void run() {
                            loaded = true;
                            iv.setImageBitmap(bm);
                            fit(bm);
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(ImageViewActivity.this, "加载失败",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    /** 初始居中并按比例缩放到屏幕内 */
    private void fit(Bitmap bm) {
        if (bm == null) return;
        int vw = iv.getWidth(), vh = iv.getHeight();
        float bw = bm.getWidth(), bh = bm.getHeight();
        if (vw <= 0 || vh <= 0) {
            iv.post(new Runnable() {
                public void run() { fit2(); }
            });
            return;
        }
        fit2();
    }

    /** 保存到系统 Download 目录，并通知相册扫描（静默，不弹提示） */
    private void saveToAlbum(String url) {
        if (url == null || url.isEmpty()) return;
        String name = url;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        int q = name.indexOf('?');
        if (q > 0) name = name.substring(0, q);
        if (name.isEmpty()) name = "image.jpg";
        Downloads.start(this, url, name);
        // 下载完成后让相册能扫到。用广播扫描，不需要存储权限。
        Downloads.setListener(new Downloads.Listener() {
            public void onChanged() {
                for (Downloads.Task x : Downloads.all()) {
                    if (x.state == Downloads.ST_DONE && x.fileExists()) {
                        try {
                            android.media.MediaScannerConnection.scanFile(
                                    ImageViewActivity.this,
                                    new String[]{x.path}, null, null);
                        } catch (Throwable ignore) {}
                    }
                }
            }
        });
    }

    private void fit2() {
        android.graphics.drawable.Drawable d = iv.getDrawable();
        if (d == null) return;
        int vw = iv.getWidth(), vh = iv.getHeight();
        float bw = d.getIntrinsicWidth(), bh = d.getIntrinsicHeight();
        if (vw <= 0 || vh <= 0 || bw <= 0 || bh <= 0) return;
        float s = Math.min(vw / bw, vh / bh);
        s = Math.min(s, 3f);
        mx.reset();
        mx.postScale(s, s);
        mx.postTranslate((vw - bw * s) / 2f, (vh - bh * s) / 2f);
        scale = 1f;
        iv.setImageMatrix(mx);
    }

    private boolean zoomed() {
        float[] v = new float[9];
        mx.getValues(v);
        return Math.abs(v[Matrix.MSCALE_X] - 1f) > 0.01f;
    }

    private float spacing(MotionEvent e) {
        if (e.getPointerCount() < 2) return 0f;
        float x = e.getX(0) - e.getX(1);
        float y = e.getY(0) - e.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private void midPoint(PointF p, MotionEvent e) {
        if (e.getPointerCount() < 2) { p.set(e.getX(), e.getY()); return; }
        p.set((e.getX(0) + e.getX(1)) / 2f, (e.getY(0) + e.getY(1)) / 2f);
    }

    private float moved(PointF s, MotionEvent e) {
        float dx = e.getX() - s.x, dy = e.getY() - s.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** 取屏幕短边 */
    private int screen() {
        try {
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            return Math.min(dm.widthPixels, dm.heightPixels);
        } catch (Throwable e) { return 1080; }
    }
}
