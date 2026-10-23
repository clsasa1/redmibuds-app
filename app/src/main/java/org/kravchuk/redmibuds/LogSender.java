package org.kravchuk.redmibuds;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Primary: user-configured webhook (SharedPreferences). Fallback: ntfy topic.
 * Auth: Authorization: Bearer &lt;key&gt; (no make-bot-ui skill found; Bearer is the fallback convention).
 */
final class LogSender {
    /** ntfy fallback topic (public). */
    static final String NTFY_TOPIC = "redmibuds-kravchuk-ea8aa7c21c143207712f";
    static final String NTFY_URL = "https://ntfy.sh/" + NTFY_TOPIC;
    private static final int MAX_LINES = 400, MAX_BYTES = 48_000;

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("p", 0); }

    static String tail() {
        List<String> snap = Log2.snapshot();
        int from = Math.max(0, snap.size() - MAX_LINES);
        StringBuilder b = new StringBuilder();
        for (int i = from; i < snap.size(); i++) {
            if (b.length() > MAX_BYTES) break;
            b.append(snap.get(i)).append('\n');
        }
        if (b.length() == 0 && Log2.file != null && Log2.file.exists()) {
            try (RandomAccessFile raf = new RandomAccessFile(Log2.file, "r")) {
                long start = Math.max(0, raf.length() - MAX_BYTES);
                raf.seek(start);
                if (start > 0) raf.readLine();
                String line; while ((line = raf.readLine()) != null) b.append(line).append('\n');
            } catch (Exception ignored) {}
        }
        return b.toString();
    }

    interface Callback { void done(boolean ok, String message); }

    static void send(Context c, String reason, boolean toast) { send(c, reason, toast, null); }

    /** Sends on a background thread. Webhook (if set) → on failure falls back to ntfy. Callback on main thread. */
    static void send(Context c, String reason, boolean toast, Callback cb) {
        final Context app = c.getApplicationContext();
        String verTmp;
        try { verTmp = app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName; } catch (Exception e) { verTmp = "?"; }
        final String ver = verTmp;
        final String body = tail();
        SharedPreferences p = prefs(app);
        final String url = p.getString("webhook_url", "").trim();
        final String key = p.getString("webhook_key", "").trim();
        Thread t = new Thread(() -> {
            boolean ok = false; String msg;
            String err = null;
            if (!url.isEmpty()) {
                try {
                    int code = postWebhook(url, key, ver, body, reason);
                    ok = code >= 200 && code < 300;
                    if (!ok) err = "webhook HTTP " + code;
                } catch (Throwable e) { err = "webhook: " + e.getClass().getSimpleName() + " " + e.getMessage(); }
                if (err != null) Log2.add("SEND", err + " → ntfy");
            }
            if (!ok) {
                try {
                    int code = postNtfy(ver, body, reason);
                    ok = code >= 200 && code < 300;
                    if (!ok) err = (err == null ? "" : err + "; ") + "ntfy HTTP " + code;
                } catch (Throwable e) {
                    err = (err == null ? "" : err + "; ") + "ntfy: " + e.getClass().getSimpleName() + " " + e.getMessage();
                }
            }
            msg = ok ? "Журнал отправлен" : "Не отправился: " + err;
            Log2.add("SEND", msg + " (" + reason + ")");
            final boolean fok = ok; final String fmsg = msg;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (toast) Toast.makeText(app, fmsg, Toast.LENGTH_SHORT).show();
                if (cb != null) try { cb.done(fok, fmsg); } catch (Throwable ignored) {}
            });
        }, "log-send");
        t.start();
    }

    private static int postWebhook(String url, String key, String ver, String log, String reason) throws Exception {
        JSONObject j = new JSONObject();
        j.put("version", ver); j.put("reason", reason); j.put("log", log);
        byte[] data = j.toString().getBytes(StandardCharsets.UTF_8);
        java.util.Map<String, String> h = new java.util.LinkedHashMap<>();
        h.put("Content-Type", "application/json; charset=utf-8");
        if (!key.isEmpty()) h.put("Authorization", "Bearer " + key);
        int code = HttpPost.post(url, h, data, 15000);
        Log2.add("SEND", "webhook HTTP " + code + " (" + data.length + " байт)");
        return code;
    }

    static int postNtfy(String ver, String log, String reason) throws Exception {
        byte[] data = (log.isEmpty() ? "(empty log)" : log).getBytes(StandardCharsets.UTF_8);
        java.util.Map<String, String> h = new java.util.LinkedHashMap<>();
        h.put("Title", HttpPost.asciiHeader("redmibuds log v" + ver + " (" + reason + ")"));
        h.put("Content-Type", "text/plain; charset=utf-8");
        int code = HttpPost.post(NTFY_URL, h, data, 15000);
        Log2.add("SEND", "ntfy HTTP " + code + " (" + data.length + " байт)");
        return code;
    }
}
