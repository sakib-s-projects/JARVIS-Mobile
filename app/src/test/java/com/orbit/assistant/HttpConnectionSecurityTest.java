package com.orbit.assistant;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public final class HttpConnectionSecurityTest {
    @Test
    public void disablesAutomaticRedirectsAndCachingForSensitiveRequests() throws Exception {
        HttpURLConnection connection = new StubConnection(new URL("https://relay.example/assistant"));

        HttpConnectionSecurity.applyNoRedirects(connection);

        assertFalse(connection.getInstanceFollowRedirects());
        assertFalse(connection.getUseCaches());
    }

    private static final class StubConnection extends HttpURLConnection {
        StubConnection(URL url) { super(url); }

        @Override public void connect() throws IOException {}
        @Override public void disconnect() {}
        @Override public boolean usingProxy() { return false; }
    }
}
