package xfan.chat.app;

import android.app.Application;

/**
 * 应用入口（在 AndroidManifest 的 application android:name 里注册）。
 *
 * 必须存在的原因：
 *   开屏页是最先启动的界面，但 Prefs / CrashGuard 以前只在登录页初始化。
 *   结果开屏阶段一崩，既拿不到 SharedPreferences，也没有崩溃处理器接管
 *   —— 表现就是「闪退且完全没有日志」。
 *
 * 这里统一提前初始化，保证任何阶段崩溃都能留下堆栈。
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        try { CrashGuard.install(this); } catch (Throwable ignore) {}
        try { Prefs.init(this); }        catch (Throwable ignore) {}
        // 必须在主线程初始化：ImgLoader 里有 Handler，
        // 在子线程触发类加载会抛 "Can't create handler inside thread..."
        try { ImgLoader.init(this); }    catch (Throwable ignore) {}
    }
}
