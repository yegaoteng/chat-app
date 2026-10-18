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
        // 后台守护：新消息通知 + 每小时版本比对
        try {
            startService(new android.content.Intent(this, MonitorService.class));
        } catch (Throwable ignore) {}
    }
}
