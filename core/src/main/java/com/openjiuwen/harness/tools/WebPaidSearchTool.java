/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.tools.web.WebHttpFetcher;
import com.openjiuwen.harness.tools.web.WebHttpResponse;

import java.util.Locale;
import java.util.Map;

/**
 * Public class WebPaidSearchTool used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class WebPaidSearchTool {
    private final WebHttpFetcher fetcher;
    private final Map<String, String> env;

    /**
     * WebPaidSearchTool.
     * 
     * @since 0.1.7
     */
    public WebPaidSearchTool() {
        this(WebFetchWebpageTool::defaultFetch, System.getenv());
    }

    /**
     * WebPaidSearchTool.
     * 
     * @param fetcher fetcher
     * @param env env
     * @since 0.1.7
     */
    public WebPaidSearchTool(WebHttpFetcher fetcher, Map<String, String> env) {
        this.fetcher = fetcher;
        this.env = env;
    }

    /**
     * invoke.
     * 
     * @param query query
     * @param provider provider
     * @return the result
     * @since 0.1.7
     */
    public String invoke(String query, String provider) {
        if (query == null || query.isBlank()) {
            return "[ERROR]: query cannot be empty.";
        }
        String normalized = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("bocha") && !normalized.equals("perplexity") && !normalized.equals("serper")) {
            return "[ERROR]: provider must be one of bocha, perplexity, serper";
        }
        if (!isPaidSearchEnabled(env)) {
            return "[ERROR]: paid search is not enabled";
        }
        try {
            WebHttpResponse response = fetcher.fetch("GET", "https://paid-search.local/" + normalized + "?q=" + query);
            if (response.statusCode() != 200) {
                return "[ERROR]: paid search failed: http status " + response.statusCode();
            }
            return "Paid search results (" + normalized + ")\n" + response.text();
        } catch (Exception ex) {
            return "[ERROR]: paid search failed: " + ex.getMessage();
        }
    }

    /**
     * isPaidSearchEnabled.
     * 
     * @param env env
     * @return the result
     * @since 0.1.7
     */
    public static boolean isPaidSearchEnabled(Map<String, String> env) {
        return hasKey(env, "BOCHA_API_KEY") || hasKey(env, "PERPLEXITY_API_KEY") || hasKey(env, "SERPER_API_KEY");
    }

    /**
     * hasKey.
     * 
     * @param env env
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    private static boolean hasKey(Map<String, String> env, String key) {
        String value = env.get(key);
        return value != null && !value.isBlank();
    }
}
