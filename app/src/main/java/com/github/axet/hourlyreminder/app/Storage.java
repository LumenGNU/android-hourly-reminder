package com.github.axet.hourlyreminder.app;

import android.content.ContentResolver;
import android.content.Context;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.v4.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Storage extends com.github.axet.androidlibrary.app.Storage {
    public static final String RINGTONES = "ringtones";

    public Storage(Context context) {
        super(context);
    }

    public File storeRingtone(Uri uri) {
        File dir = new File(context.getApplicationInfo().dataDir, RINGTONES);
        if (!dir.exists()) {
            if (!dir.mkdirs())
                throw new RuntimeException("unable to create: " + dir);
        }

        for (File child : dir.listFiles())
            child.delete();

        Ringtone r = RingtoneManager.getRingtone(context, uri);
        File title = new File(r.getTitle(context));

        File dst = new File(dir, title.getName());

        try {
            ContentResolver cr = context.getContentResolver();
            InputStream in = cr.openInputStream(uri);
            OutputStream out = new FileOutputStream(dst);

            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return dst;
    }
}
