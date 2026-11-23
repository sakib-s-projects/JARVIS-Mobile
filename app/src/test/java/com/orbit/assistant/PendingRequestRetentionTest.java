package com.orbit.assistant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.FileOutputStream;

/** Retention of a failed request must include its private image files, not just its metadata. */
@RunWith(RobolectricTestRunner.class)
public final class PendingRequestRetentionTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("orbit_pending_requests", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test public void expiredFailedRequestDeletesItsUnreferencedPendingScreenshot() throws Exception {
        File directory = new File(context.getFilesDir(), "orbit_attachments/pending");
        assertTrue(directory.mkdirs() || directory.isDirectory());
        File screenshot = new File(directory, "retention-test.jpg");
        try (FileOutputStream output = new FileOutputStream(screenshot)) {
            output.write(new byte[]{1, 2, 3, 4});
        }
        assertTrue(screenshot.isFile());

        PendingRequestStore.Item item = PendingRequestStore.create(context,
                "retention-conversation", "Retry this request", "",
                screenshot.getAbsolutePath(), false, false,
                AiSelections.FALLBACK, false, "");
        assertTrue(item != null);
        PendingRequestStore.markFailed(context, item.id, "Temporary provider failure");

        // Move only the terminal record's retention timestamp beyond the seven-day cutoff.
        android.content.SharedPreferences prefs = context.getSharedPreferences(
                "orbit_pending_requests", Context.MODE_PRIVATE);
        JSONArray records = new JSONArray(prefs.getString("items_v1", "[]"));
        assertTrue(records.length() > 0);
        JSONObject record = records.getJSONObject(0);
        record.put("updatedAt", System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1000);
        assertTrue(prefs.edit().putString("items_v1", records.toString()).commit());

        // Creating a new request invokes the existing metadata-pruning path.
        PendingRequestStore.create(context, "new-conversation", "New request", "", "",
                false, false, AiSelections.FALLBACK, false, "");

        assertFalse("an expired request must not leave its screenshot behind", screenshot.exists());
        assertTrue(PendingRequestStore.load(context, item.id) == null);
    }
}
