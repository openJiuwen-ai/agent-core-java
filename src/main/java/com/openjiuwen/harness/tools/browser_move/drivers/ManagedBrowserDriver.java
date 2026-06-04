package com.openjiuwen.harness.tools.browser_move.drivers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Minimal managed browser driver.
 *
 * <p>Mirrors Python's {@code ManagedBrowserDriver} in
 * {@code openjiuwen.harness.tools.browser_move.drivers.managed_browser}.</p>
 */
public class ManagedBrowserDriver {

    private static final Pattern READY_MARKER = Pattern.compile("\\b(webSocketDebuggerUrl|Browser)\\b");

    private final BrowserProfile profile;
    private Process process;
    private boolean ownsProcess = false;

    public ManagedBrowserDriver(BrowserProfile profile) {
        this.profile = profile;
    }

    public String start() {
        if (process != null && process.isAlive() && isEndpointReady()) {
            return profile.getCdpUrl();
        }
        if (isEndpointReady()) {
            process = null;
            ownsProcess = false;
            return profile.getCdpUrl();
        }
        ownsProcess = true;
        return profile.getCdpUrl();
    }

    public void stop() {
        Process current = process;
        boolean owned = ownsProcess;
        process = null;
        ownsProcess = false;
        if (current != null && owned && current.isAlive()) {
            current.destroy();
        }
    }

    public boolean isEndpointReady() {
        String endpoint = profile.getCdpUrl();
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        try {
            URL url = new URL(endpoint.endsWith("/json/version") ? endpoint : endpoint + "/json/version");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(1500);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(line);
                }
                return READY_MARKER.matcher(builder.toString()).find();
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean isOwnsProcess() {
        return ownsProcess;
    }

    public void setProcess(Process process) {
        this.process = process;
    }

    public void setOwnsProcess(boolean ownsProcess) {
        this.ownsProcess = ownsProcess;
    }
}
