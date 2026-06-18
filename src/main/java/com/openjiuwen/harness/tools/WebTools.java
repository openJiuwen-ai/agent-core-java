/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.Tool;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Web search and webpage fetch tool facades.
 *
 * <p>Mirrors Python's free-search/paid-search/fetch helpers and tool classes in
 * {@code openjiuwen/harness/tools/web_tools.py}.</p>
 */
public final class WebTools {

    private static final List<String> PAID_SEARCH_API_KEY_ENVS = List.of(
            "PERPLEXITY_API_KEY", "BOCHA_API_KEY", "JINA_API_KEY", "SERPER_API_KEY"
    );
    private static final Set<String> TIMELY_QUERY_HINTS = Set.of("today", "latest", "new", "2026", "recent");

    private WebTools() {
    }

    public static boolean isFreeSearchEnabled() {
        return envFlag("FREE_SEARCH_DDG_ENABLED", false) || envFlag("FREE_SEARCH_BING_ENABLED", false);
    }

    public static boolean isPaidSearchEnabled() {
        return PAID_SEARCH_API_KEY_ENVS.stream().anyMatch(name -> !env(name).isBlank());
    }

    public static boolean isTimelyQuery(String query) {
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return TIMELY_QUERY_HINTS.stream().anyMatch(normalized::contains);
    }

    public static List<Tool> createWebTools(SearchProvider searchProvider) {
        List<Tool> tools = new ArrayList<>();
        if (isPaidSearchEnabled()) {
            tools.add(new WebPaidSearchTool(searchProvider));
        }
        if (isFreeSearchEnabled()) {
            tools.add(new WebFreeSearchTool(searchProvider));
        }
        tools.add(new WebFetchWebpageTool());
        return tools;
    }

    public interface SearchProvider {
        Map<String, Object> search(String query, boolean paid, Map<String, Object> inputs) throws Exception;
    }

    /**
     * Mirrors Python's {@code WebFreeSearchTool} in {@code openjiuwen/harness/tools/web_tools.py}.
     */
    public static class WebFreeSearchTool extends AbstractHarnessTool {
        private final SearchProvider searchProvider;

        public WebFreeSearchTool(SearchProvider searchProvider) {
            super(toolCard("web_free_search", "WebFreeSearchTool", "Search the web through a free provider."));
            this.searchProvider = searchProvider;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            String query = requiredString(inputs, "query");
            if (searchProvider == null) {
                return ToolOutput.failure("free search provider is not configured");
            }
            return ToolOutput.success(searchProvider.search(query, false, inputs == null ? Map.of() : inputs));
        }
    }

    /**
     * Mirrors Python's {@code WebPaidSearchTool} in {@code openjiuwen/harness/tools/web_tools.py}.
     */
    public static class WebPaidSearchTool extends AbstractHarnessTool {
        private final SearchProvider searchProvider;

        public WebPaidSearchTool(SearchProvider searchProvider) {
            super(toolCard("web_paid_search", "WebPaidSearchTool", "Search the web through a configured paid provider."));
            this.searchProvider = searchProvider;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            String query = requiredString(inputs, "query");
            if (searchProvider == null) {
                return ToolOutput.failure("paid search provider is not configured");
            }
            return ToolOutput.success(searchProvider.search(query, true, inputs == null ? Map.of() : inputs));
        }
    }

    /**
     * Mirrors Python's {@code WebFetchWebpageTool} in {@code openjiuwen/harness/tools/web_tools.py}.
     */
    public static class WebFetchWebpageTool extends AbstractHarnessTool {
        private final HttpClient client = HttpClient.newHttpClient();

        public WebFetchWebpageTool() {
            super(toolCard("web_fetch_webpage", "WebFetchWebpageTool", "Fetch a webpage as text."));
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs)
                throws IOException, InterruptedException {
            String url = requiredString(inputs, "url");
            int timeout = Math.max(1, intValue(inputs == null ? null : inputs.get("timeout_seconds"), 45));
            int maxChars = Math.max(1, intValue(inputs == null ? null : inputs.get("max_chars"), 20_000));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("User-Agent", "Mozilla/5.0 Java")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body() == null ? "" : response.body();
            if (body.length() > maxChars) {
                body = body.substring(0, maxChars);
            }
            return ToolOutput.success(Map.of("url", url, "status_code", response.statusCode(), "content", body));
        }
    }

    private static boolean envFlag(String key, boolean defaultValue) {
        String value = env(key);
        if (value.isBlank()) {
            return defaultValue;
        }
        return List.of("1", "true", "yes", "on").contains(value.toLowerCase(Locale.ROOT));
    }

    private static String env(String key) {
        String property = System.getProperty(key);
        if (property != null) {
            return property.trim();
        }
        String value = System.getenv(key);
        return value == null ? "" : value.trim();
    }
}
