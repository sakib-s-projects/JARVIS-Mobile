package com.orbit.assistant;

import java.net.HttpURLConnection;

/**
 * Security policy for requests that carry account credentials or private user context.
 *
 * <p>Do not let HttpURLConnection replay an authenticated POST or its body to a redirect
 * destination. A 3xx response is surfaced to the caller instead; the app must not silently
 * forward bearer tokens, authorization codes, secrets, screenshots, or chat context to a
 * different origin.
 */
final class HttpConnectionSecurity {
    private HttpConnectionSecurity() {}

    static void applyNoRedirects(HttpURLConnection connection) {
        if (connection == null) throw new IllegalArgumentException("connection");
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
    }
}
