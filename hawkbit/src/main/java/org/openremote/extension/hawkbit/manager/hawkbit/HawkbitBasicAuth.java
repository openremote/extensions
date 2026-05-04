package org.openremote.extension.hawkbit.manager.hawkbit;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class HawkbitBasicAuth {

    private HawkbitBasicAuth() {
    }

    public static String buildAuthorizationHeader(String username, String password) {
        String credentials = (username == null ? "" : username) + ":" + (password == null ? "" : password);
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
