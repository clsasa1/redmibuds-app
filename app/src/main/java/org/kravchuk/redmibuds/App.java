package org.kravchuk.redmibuds;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.*;
import java.util.Date;

public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Log2.init(this);
        final Thread.UncaughtExceptionHandler def = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                String txt = "Сбой " + new Date() + " в потоке " + t.getName() + "\n" + sw;
                Log2.add("CRASH", txt);
                try (FileOutputStream f = new FileOutputStream(new File(getFilesDir(), "crash.txt"))) { f.write(txt.getBytes("UTF-8")); }
            } catch (Throwable ignored) {}
            if (def != null) def.uncaughtException(t, e); else System.exit(2);
        });
    }

    static String readFile(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), 200_000)]; int n = in.read(b);
            return new String(b, 0, Math.max(n, 0), "UTF-8");
        } catch (Exception e) { return ""; }
    }

    /** Share a file from filesDir via FileProvider. */
    static void share(Context c, File f, String title) {
        try {
            Uri u = androidx.core.content.FileProvider.getUriForFile(c, c.getPackageName() + ".files", f);
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, u)
                    .putExtra(Intent.EXTRA_SUBJECT, f.getName()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            c.startActivity(Intent.createChooser(i, title));
        } catch (Throwable e) {
            android.widget.Toast.makeText(c, "Не удалось поделиться: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
    }
}
