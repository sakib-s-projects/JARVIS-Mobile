package com.orbit.assistant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;

/** The 30-minute screen-context limit must also remove expired local data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 35})
public final class LastScreenStoreRetentionTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("orbit_last_screen", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test public void expiredScreenContextDeletesItsPrivateThumbnailAndMetadata() {
        Bitmap image = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        try {
            LastScreenStore.begin(context, "com.example.mail", "Mail");
            LastScreenStore.updateText(context, "private message preview",
                    "com.example.mail", "Mail");
            LastScreenStore.updateImage(context, image, "com.example.mail", "Mail");
        } finally {
            image.recycle();
        }

        android.content.SharedPreferences prefs = context.getSharedPreferences(
                "orbit_last_screen", Context.MODE_PRIVATE);
        String imagePath = prefs.getString("imagePath", "");
        assertTrue("a private thumbnail should have been saved", !imagePath.isEmpty());
        File thumbnail = new File(imagePath);
        assertTrue(thumbnail.isFile());

        assertTrue(prefs.edit()
                .putLong("capturedAt", System.currentTimeMillis() - 31L * 60L * 1000L)
                .commit());

        assertNull("expired screen data must not be returned", LastScreenStore.load(context));
        assertFalse("the expired thumbnail must be deleted", thumbnail.exists());
        assertFalse(prefs.contains("text"));
        assertFalse(prefs.contains("imagePath"));
        assertFalse(prefs.contains("packageName"));
        assertFalse(prefs.contains("appLabel"));
        assertFalse(prefs.contains("capturedAt"));
    }

    @Test public void freshScreenContextRemainsAvailable() {
        LastScreenStore.begin(context, "com.example.reader", "Reader");
        LastScreenStore.updateText(context, "fresh selected page",
                "com.example.reader", "Reader");

        LastScreenStore.Snapshot snapshot = LastScreenStore.load(context);

        assertNotNull(snapshot);
        assertTrue(snapshot.text.contains("fresh selected page"));
    }
}
