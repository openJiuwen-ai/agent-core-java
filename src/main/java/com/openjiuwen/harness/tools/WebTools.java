package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.Tool;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's web-tool factory helpers in {@code openjiuwen.harness.tools.web_tools}.
 */
public final class WebTools {

    private static final List<String> PAID_SEARCH_API_KEY_ENVS = List.of(
            "BOCHA_API_KEY",
            "PERPLEXITY_API_KEY",
            "SERPER_API_KEY",
            "JINA_API_KEY"
    );

    private WebTools() {
    }

    public static boolean isFreeSearchEnabled() {
        return envFlag("FREE_SEARCH_DDG_ENABLED", false) || envFlag("FREE_SEARCH_BING_ENABLED", false);
    }

    public static boolean isPaidSearchEnabled() {
        for (String key : PAID_SEARCH_API_KEY_ENVS) {
            if (!env(key).isBlank()) {
                return true;
            }
        }
        return false;
    }

    public static List<Tool> createWebTools(String language) {
        return createWebTools(language, true, true, true);
    }

    public static List<Tool> createWebTools(
            String language,
            boolean includeFreeSearch,
            boolean includePaidSearch,
            boolean includeFetchWebpage
    ) {
        List<Tool> tools = new ArrayList<>();
        if (includePaidSearch && isPaidSearchEnabled()) {
            tools.add(new WebPaidSearchTool(language));
        }
        if (includeFreeSearch && isFreeSearchEnabled()) {
            tools.add(new WebFreeSearchTool(language));
        }
        if (includeFetchWebpage) {
            tools.add(new WebFetchWebpageTool());
        }
        return tools;
    }

    private static boolean envFlag(String key, boolean defaultValue) {
        String value = env(key);
        if (value.isBlank()) {
            return defaultValue;
        }
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value);
    }

    private static String env(String key) {
        String property = System.getProperty(key);
        if (property != null) {
            return property.trim();
        }
        String env = System.getenv(key);
        return env == null ? "" : env.trim();
    }
}
