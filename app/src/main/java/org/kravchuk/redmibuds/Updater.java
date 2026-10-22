package org.kravchuk.redmibuds;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/** GitHub Releases updater. */
final class Updater {
    /** ===== Fill in: GitHub owner/repo ===== */
    static final String OWNER = "clsasa1";
    static final String REPO = "redmibuds-app";
    static final String API = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest";

    interface Result { void onNewer(String tag, String apkUrl, String notes); void onNone(String msg); }

    static String current(Activity a) {
        try { return a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName; } catch (Exception e) { return "0"; }
    }

    /** true if a > b ("v0.10" > "0.9"). */
    static boolean newer(String a, String b) {
        String[] x = a.replaceAll("^[vV]", "").split("[.\\-]"), y = b.replaceAll("^[vV]", "").split("[.\\-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }
    private static int num(String s) { try { return Integer.parseInt(s.replaceAll("\\D", "")); } catch (Exception e) { return 0; } }

    static void check(Activity a, Result r) {
        final String cur = current(a);
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                int code = c.getResponseCode();
                if (code != 200) { post(a, () -> r.onNone("GitHub ответил " + code + (OWNER.equals("OWNER") ? " (не задан OWNER в Updater.java)" : ""))); return; }
                String body = new String(readAll(c.getInputStream()), "UTF-8");
                JSONObject j = new JSONObject(body);
                String tag = j.optString("tag_name", "");
                String url = null;
                JSONArray as = j.optJSONArray("assets");
                if (as != null) for (int i = 0; i < as.length(); i++) {
                    JSONObject o = as.getJSONObject(i);
                    if (o.optString("name").endsWith(".apk")) { url = o.optString("browser_download_url"); break; }
                }
                Log2.add("UPD", "latest=" + tag + " current=" + cur + " apk=" + url);
                final String u = url;
                if (newer(tag, cur) && u != null) post(a, () -> r.onNewer(tag, u, j.optString("body", "")));
                else post(a, () -> r.onNone(u == null && newer(tag, cur) ? "В релизе " + tag + " нет APK" : "Установлена последняя версия (" + cur + ")"));
            } catch (Throwable e) {
                Log2.add("UPD", "ошибка: " + e);
                post(a, () -> r.onNone("Ошибка проверки: " + e.getMessage()));
            }
        }, "updater").start();
    }

    static void downloadAndInstall(Activity a, String url, String tag) {
        if (!a.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Нужно разрешение")
                    .setMessage("Разрешите этому приложению устанавливать неизвестные приложения, затем снова нажмите «Проверить обновление».")
                    .setPositiveButton("Открыть настройки", (d, w) -> a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName()))))
                    .setNegativeButton("Отмена", null).show();
            return;
        }
        Toast.makeText(a, "Загрузка " + tag + "…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File dir = new File(a.getCacheDir(), "upd"); dir.mkdirs();
                File f = new File(dir, "update.apk");
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setInstanceFollowRedirects(true); c.setConnectTimeout(10000); c.setReadTimeout(30000);
                try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(f)) {
                    byte[] b = new byte[65536]; int n; while ((n = in.read(b)) > 0) out.write(b, 0, n);
                }
                Log2.add("UPD", "скачано " + f.length() + " байт");
                post(a, () -> {
                    Uri u = androidx.core.content.FileProvider.getUriForFile(a, a.getPackageName() + ".files", f);
                    Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(u, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    a.startActivity(i);
                });
            } catch (Throwable e) {
                Log2.add("UPD", "ошибка загрузки: " + e);
                post(a, () -> Toast.makeText(a, "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "updater-dl").start();
    }

    /** Interactive check (button). */
    static void checkInteractive(Activity a) {
        Toast.makeText(a, "Проверка обновлений…", Toast.LENGTH_SHORT).show();
        check(a, new Result() {
            public void onNewer(String tag, String url, String notes) { offer(a, tag, url, notes); }
            public void onNone(String msg) { Toast.makeText(a, msg, Toast.LENGTH_LONG).show(); }
        });
    }
    static void offer(Activity a, String tag, String url, String notes) {
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Доступна версия " + tag)
                .setMessage((notes == null || notes.isEmpty() ? "" : notes + "\n\n") + "Текущая: " + current(a))
                .setPositiveButton("Обновить", (d, w) -> downloadAndInstall(a, url, tag)).setNegativeButton("Позже", null).show();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n); in.close(); return o.toByteArray();
    }
    private static void post(Activity a, Runnable r) { a.runOnUiThread(() -> { try { if (!a.isFinishing()) r.run(); } catch (Throwable e) { Log2.add("UPD", "ui: " + e); } }); }
}
