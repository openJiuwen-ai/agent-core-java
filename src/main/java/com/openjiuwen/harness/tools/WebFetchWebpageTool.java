package com.openjiuwen.harness.tools;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's minimal webpage-fetch tool intent from {@code openjiuwen.harness.tools.web_tools}.
 */
public class WebFetchWebpageTool extends AbstractHarnessTool {

    public WebFetchWebpageTool() {
        super(toolCard("fetch_webpage", "fetch_webpage", "Fetch webpage text content from a URL."), null);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String url = inputs.get("url") == null ? "" : String.valueOf(inputs.get("url")).trim();
        if (url.isBlank()) {
            return new ToolOutput(false, null, "url cannot be empty");
        }
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(line);
                }
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("url", url);
                data.put("content", builder.toString());
                return new ToolOutput(true, data, null);
            }
        } catch (Exception e) {
            return new ToolOutput(false, null, e.getMessage());
        }
    }
}
