package xfan.chat.app;

import android.app.Application;

/** 应用入口（在 AndroidManifest 的 application android:name 里注册）。 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        try { CrashGuard.install(this); } catch (Throwable ignore) {}
        try { Prefs.init(this); }        catch (Throwable ignore) {}
        // 必须在主线程初始化：ImgLoader 里有 Handler，
        // 在子线程触发类加载会抛 "Can't create handler inside thread..."
        try { ImgLoader.init(this); }    catch (Throwable ignore) {}
        try { Api.bindContext(this); }   catch (Throwable ignore) {}
        try { Api.applyHttpSwitch(); }   catch (Throwable ignore) {}
        // 后台守护：新消息通知 + 每小时版本比对。
        // Android 8+ 从后台 startService 会抛异常，必须走 startForegroundService。
        try {
            android.content.Intent si =
                    new android.content.Intent(this, MonitorService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(si);
            else startService(si);
        } catch (Throwable ignore) {
            // 前台启动被拒（如从后台被拉起）时退回普通启动
            try { startService(new android.content.Intent(this, MonitorService.class)); }
            catch (Throwable ignore2) {}
        }
    }
}
