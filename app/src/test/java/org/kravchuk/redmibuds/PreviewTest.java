package org.kravchuk.redmibuds;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;

/** Renders screens at Pixel 8 Pro size (1344 px wide, 480 dpi; height minus status/nav bars) into PNGs. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "w448dp-h925dp-xxhdpi")
public class PreviewTest {
    static void shot(Activity a, String name) throws Exception {
        ShadowLooper.idleMainLooper();
        View v = a.getWindow().getDecorView();
        int w = 1344, h = 2775;
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.eraseColor(0xFF000000);
        v.draw(new Canvas(b));
        File dir = new File(System.getProperty("preview.out", "/tmp/preview")); dir.mkdirs();
        try (FileOutputStream o = new FileOutputStream(new File(dir, name + ".png"))) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
    }
    @Test public void screens() throws Exception {
        Intent i = new Intent(androidx.test.core.app.ApplicationProvider.getApplicationContext(), MainActivity.class).putExtra("demo", true);
        shot(Robolectric.buildActivity(MainActivity.class, i).setup().get(), "main");
        for (String p : new String[]{"anc", "gestures", "sound", "find", "extra"}) {
            Intent f = new Intent(androidx.test.core.app.ApplicationProvider.getApplicationContext(), FeatureActivity.class).putExtra("page", p);
            shot(Robolectric.buildActivity(FeatureActivity.class, f).setup().get(), p);
        }
        shot(Robolectric.buildActivity(SettingsActivity.class).setup().get(), "settings");
    }
}
