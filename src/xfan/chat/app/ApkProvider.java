package xfan.chat.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 内置下载器的文件提供者。
 *
 * Android 7.0 起禁止把 file:// 暴露给其他应用，安装 APK 必须用 content://。
 * 项目不引入 androidx，所以不用 FileProvider，自己实现一个极简的：
 * 只服务「下载目录下的单个文件」，按 Uri 最后一段当文件名。
 */
public class ApkProvider extends ContentProvider {

    public static final String AUTHORITY = "xfan.chat.app.apk";

    private static final UriMatcher M = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        M.addURI(AUTHORITY, "*", 1);
    }

    /** 下载目录：应用专属，Android 4.4+ 不需要任何存储权限 */
    public static File dir(android.content.Context c) {
        File d = new File(c.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS),
                "xfan");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** 由文件名构造可授予权限的 content Uri */
    public static Uri uriFor(String fileName) {
        return Uri.parse("content://" + AUTHORITY + "/" + fileName);
    }

    @Override
    public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (getContext() == null) throw new FileNotFoundException("no context");
        if (M.match(uri) != 1) throw new FileNotFoundException("bad uri: " + uri);

        String name = uri.getLastPathSegment();
        if (TextUtils.isEmpty(name)) throw new FileNotFoundException("no name");
        // 防目录穿越：只取文件名
        name = new File(name).getName();

        File f = new File(dir(getContext()), name);
        if (!f.exists()) throw new FileNotFoundException("not found: " + f);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
