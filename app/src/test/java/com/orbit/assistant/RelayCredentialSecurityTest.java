package com.orbit.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Map;

/** Relay bearer credentials must never fall back to ordinary SharedPreferences. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 35})
public final class RelayCredentialSecurityTest {
    private static final String SECRET = "relay-test-super-secret-token";
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        Prefs.get(context).edit().clear().commit();
        assertTrue(SecureStore.saveRelayToken(context, ""));
    }

    @Test public void saveNeverStoresRelayTokenInPlaintext() {
        boolean saved = SecureStore.saveRelayToken(context, SECRET);
        assertFalse(Prefs.get(context).contains(Prefs.BACKEND_TOKEN));
        assertNoPlaintextSecret();
        assertEquals(saved ? SECRET : "", SecureStore.loadRelayToken(context));
        assertNoPlaintextSecret();
    }

    @Test public void legacyPlaintextIsMigratedOrDiscardedButNeverReturnedFromPreferences() {
        Prefs.get(context).edit().putString(Prefs.BACKEND_TOKEN, SECRET).commit();

        String loaded = SecureStore.loadRelayToken(context);

        assertFalse("legacy plaintext preference must be removed",
                Prefs.get(context).contains(Prefs.BACKEND_TOKEN));
        assertNoPlaintextSecret();
        assertTrue("a working Android Keystore migrates the legacy value; an unavailable one fails closed",
                loaded.isEmpty() || SECRET.equals(loaded));
        if (!loaded.isEmpty()) assertEquals(SECRET, loaded);
    }

    @Test public void relaySettingsAreNotReportedSavedWhenSecretEncryptionFails() {
        String result = Prefs.saveRelaySettings(context,
                "https://relay.example.test", SECRET);
        if (result.isEmpty()) {
            assertEquals("https://relay.example.test", Prefs.backendUrl(context));
            assertEquals(SECRET, SecureStore.loadRelayToken(context));
        } else {
            assertTrue(Prefs.backendUrl(context).isEmpty());
            assertTrue(result.contains("securely"));
            assertEquals("", SecureStore.loadRelayToken(context));
        }
        assertNoPlaintextSecret();
    }

    @Test public void relayTokenNeverAppearsInBackupSnapshot() throws Exception {
        SecureStore.saveRelayToken(context, SECRET);
        String snapshot = Prefs.backupSnapshot(context).toString();
        assertFalse(snapshot.contains(SECRET));
        assertFalse(snapshot.contains(Prefs.BACKEND_TOKEN));
        assertNoPlaintextSecret();
    }

    private void assertNoPlaintextSecret() {
        for (Map.Entry<String, ?> entry : Prefs.get(context).getAll().entrySet()) {
            assertFalse("preference '" + entry.getKey() + "' contains the raw relay token",
                    String.valueOf(entry.getValue()).contains(SECRET));
        }
    }
}
