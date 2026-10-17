package xfan.chat.app;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

/** 把 content:// 的 Uri 转成真实文件路径 */
public class Resolver {

    public static String getPath(Context c, Uri uri) {
        if (uri == null) return null;
        try {
            if (Build.VERSION.SDK_INT >= 19 && DocumentsContract.isDocumentUri(c, uri)) {
                String docId = DocumentsContract.getDocumentId(uri);
                if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
                    String[] sp = docId.split(":");
                    String type = sp[0];
                    Uri contentUri = ("image".equals(type))
                            ? MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            : MediaStore.Files.getContentUri("external");
                    return query(c, contentUri, "_id=?", new String[]{sp[1]});
                }
                if ("com.android.providers.downloads".equals(uri.getAuthority())) {
                    if (docId != null && docId.startsWith("raw:")) return docId.substring(4);
                    Uri cu = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"),
                            Long.parseLong(docId));
                    return query(c, cu, null, null);
                }
            }
            if ("content".equalsIgnoreCase(uri.getScheme())) {
                if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
                    return uri.getLastPathSegment();
                }
                return query(c, uri, null, null);
            }
            if ("file".equalsIgnoreCase(uri.getScheme())) return uri.getPath();
        } catch (Exception ignore) {}
        return null;
    }

    private static String query(Context c, Uri uri, String sel, String[] args) {
        try {
            String[] proj = {MediaStore.MediaColumns.DATA};
            Cursor cur = c.getContentResolver().query(uri, proj, sel, args, null);
            if (cur != null) {
                if (cur.moveToFirst()) {
                    int idx = cur.getColumnIndex(MediaStore.MediaColumns.DATA);
                    if (idx >= 0) {
                        String p = cur.getString(idx);
                        cur.close();
                        return p;
                    }
                }
                cur.close();
            }
        } catch (Exception ignore) {}
        return null;
    }
}
