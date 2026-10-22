package org.kravchuk.redmibuds;

import android.content.Context;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** In-memory ring (last 3000 lines) + continuous append to files/log.txt (flushed per line). */
final class Log2 {
    private static final int MAX = 3000;
    private static final ArrayDeque<String> lines = new ArrayDeque<>();
    private static Writer w;
    static File file;
    private static long version;

    static synchronized void init(Context c) {
        if (w != null) return;
        try {
            file = new File(c.getFilesDir(), "log.txt");
            if (file.length() > 5_000_000) { File old = new File(c.getFilesDir(), "log_prev.txt"); old.delete(); file.renameTo(old); }
            w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8"));
            w.write("\n===== start " + new Date() + " =====\n"); w.flush();
        } catch (Exception ignored) {}
    }

    static synchronized void add(String tag, String s) {
        String line = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date()) + " " + tag + " " + s;
        lines.addLast(line);
        while (lines.size() > MAX) lines.removeFirst();
        version++;
        try { if (w != null) { w.write(line); w.write('\n'); w.flush(); } } catch (Exception ignored) {}
    }
    static synchronized List<String> snapshot() { return new ArrayList<>(lines); }
    static synchronized long version() { return version; }
    static synchronized String all() { return String.join("\n", lines); }
    static synchronized void clear() {
        lines.clear(); version++;
        try { if (w != null) w.close(); } catch (Exception ignored) {}
        try { if (file != null) { w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, false), "UTF-8")); } } catch (Exception ignored) {}
    }
}
