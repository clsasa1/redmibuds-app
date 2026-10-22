package org.kravchuk.redmibuds;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;

import static org.kravchuk.redmibuds.Ui.*;

public class SettingsActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        ScrollView sv = new ScrollView(this); sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(this, 16); root.setPadding(p, dp(this, 24), p, p); sv.addView(root); setContentView(sv);
        text(this, root, "Настройки", 24, TEXT, true).setPadding(0, 0, 0, dp(this, 14));

        LinearLayout c = card(this, root);
        header(this, c, "Отправка логов");
        text(this, c, "Основной канал — webhook. Ключ отправителя хранится только на устройстве. Если URL пуст, используется запасной ntfy.", 13, SUB, false);
        text(this, c, "Webhook URL", 13, SUB, false).setPadding(0, dp(this, 12), 0, 0);
        EditText url = new EditText(this); url.setTextColor(TEXT); url.setHintTextColor(SUB);
        url.setHint("https://…"); url.setText(LogSender.prefs(this).getString("webhook_url", "")); c.addView(url);
        text(this, c, "Ключ отправителя", 13, SUB, false).setPadding(0, dp(this, 12), 0, 0);
        EditText key = new EditText(this); key.setTextColor(TEXT); key.setHintTextColor(SUB);
        key.setHint("Bearer token"); key.setText(LogSender.prefs(this).getString("webhook_key", ""));
        key.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        c.addView(key);
        LinearLayout r = row(this, c, 12);
        addWeighted(r, button(this, "Сохранить", ACCENT, v -> {
            LogSender.prefs(this).edit()
                    .putString("webhook_url", url.getText().toString().trim())
                    .putString("webhook_key", key.getText().toString().trim()).apply();
            Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show();
        }), 3);
        addWeighted(r, button(this, "Тест отправки", ORANGE, v -> LogSender.send(this, "settings-test", true)), 3);
        text(this, c, "Запасной ntfy: " + LogSender.NTFY_TOPIC, 11, SUB, false).setPadding(0, dp(this, 10), 0, 0);
    }
}
