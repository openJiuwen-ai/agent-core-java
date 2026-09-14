/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.tools.web.WebHttpFetcher;
import com.openjiuwen.harness.tools.web.WebHttpResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Public class WebFreeSearchTool used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class WebFreeSearchTool {
    private static final Pattern DDG_TITLE =
        Pattern.compile("result__a\" href=\"([^\"]+)\">([^<]+)</a>", Pattern.CASE_INSENSITIVE);

    /**
     * Pattern.compile.
     * 
     * @since 0.1.7
     */
    private static final Pattern DDG_SNIPPET =
        Pattern.compile("result__snippet[^>]*>([^<]+)</a>", Pattern.CASE_INSENSITIVE);

    /**
     * Pattern.compile.
     * 
     * @param href=\"([^\"]+ href=\"([^\"]+
     * @since 0.1.7
     */
    private static final Pattern BING_RESULT = Pattern.compile(
            "<h2><a href=\"([^\"]+)\">([^<]+)</a></h2>.*?<p>([^<]*)</p>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final WebHttpFetcher fetcher;
    private final Map<String, String> env;
    private final String language;

    /**
     * WebFreeSearchTool.
     * 
     * @since 0.1.7
     */
    public WebFreeSearchTool() {
        this("cn");
    }

    /**
     * WebFreeSearchTool.
     * 
     * @param language language
     * @since 0.1.7
     */
    public WebFreeSearchTool(String language) {
        this(WebFetchWebpageTool::defaultFetch, System.getenv(), language);
    }

    /**
     * WebFreeSearchTool.
     * 
     * @param fetcher fetcher
     * @param env env
     * @since 0.1.7
     */
    public WebFreeSearchTool(WebHttpFetcher fetcher, Map<String, String> env) {
        this(fetcher, env, "cn");
    }

    /**
     * WebFreeSearchTool.
     * 
     * @param fetcher fetcher
     * @param env env
     * @param language language
     * @since 0.1.7
     */
    public WebFreeSearchTool(WebHttpFetcher fetcher, Map<String, String> env, String language) {
        this.fetcher = fetcher;
        this.env = env;
        this.language = language == null || language.isBlank() ? "cn" : language;
    }

    /**
     * getLanguage.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getLanguage() {
        return language;
    }

    /**
     * invoke.
     * 
     * @param query query
     * @param maxResults maxResults
     * @return the result
     * @since 0.1.7
     */
    public String invoke(String query, Integer maxResults) {
        if (query == null || query.isBlank()) {
            return "[ERROR]: query cannot be empty.";
        }
        int limit = maxResults != null ? Math.max(1, maxResults) : 5;
        boolean isDdgEnabled = !"false".equalsIgnoreCase(env.getOrDefault("FREE_SEARCH_DDG_ENABLED", "true"));
        boolean isBingEnabled = "true".equalsIgnoreCase(env.getOrDefault("FREE_SEARCH_BING_ENABLED", "false"));
        if (!isDdgEnabled && !isBingEnabled) {
            return "[ERROR]: free search failed: all free search engines are disabled";
        }
        if (isDdgEnabled) {
            String result = searchDuckDuckGo(query, limit);
            if (result != null) {
                return result;
            }
        }
        if (isBingEnabled) {
            String result = searchBing(query, limit);
            if (result != null) {
                return result;
            }
        }
        return "[ERROR]: free search failed: no search engine returned usable results";
    }

    /**
     * searchDuckDuckGo.
     * 
     * @param query query
     * @param limit limit
     * @return the result
     * @since 0.1.7
     */
    private String searchDuckDuckGo(String query, int limit) {
        try {
            WebHttpResponse response = fetcher.fetch("GET", "https://duckduckgo.com/?q=" + query);
            if (response.statusCode() != 200) {
                return nullValue();
            }
            List<String> rows = new ArrayList<>();
            Matcher titleMatcher = DDG_TITLE.matcher(response.text());
            Matcher snippetMatcher = DDG_SNIPPET.matcher(response.text());
            while (titleMatcher.find() && rows.size() < limit) {
                String title = titleMatcher.group(2);
                String url = titleMatcher.group(1);
                String snippet = snippetMatcher.find() ? snippetMatcher.group(1) : "";
                rows.add("- " + title + " | " + url + " | " + snippet);
            }
            return rows.isEmpty() ? null : "Free search results (DuckDuckGo)\n" + String.join("\n", rows);
        } catch (Exception ex) {
            return nullValue();
        }
    }

    /**
     * searchBing.
     * 
     * @param query query
     * @param limit limit
     * @return the result
     * @since 0.1.7
     */
    private String searchBing(String query, int limit) {
        try {
            WebHttpResponse response = fetcher.fetch("GET", "https://www.bing.com/search?q=" + query);
            if (response.statusCode() != 200) {
                return nullValue();
            }
            List<String> rows = new ArrayList<>();
            Matcher matcher = BING_RESULT.matcher(response.text());
            while (matcher.find() && rows.size() < limit) {
                rows.add("- " + matcher.group(2) + " | " + matcher.group(1) + " | " + matcher.group(3));
            }
            return rows.isEmpty() ? null : "Free search results (Bing)\n" + String.join("\n", rows);
        } catch (Exception ex) {
            return nullValue();
        }
    }

    /**
     * isFreeSearchEnabled.
     * 
     * @param env env
     * @return the result
     * @since 0.1.7
     */
    public static boolean isFreeSearchEnabled(Map<String, String> env) {
        boolean isDdgEnabled = !"false".equalsIgnoreCase(env.getOrDefault("FREE_SEARCH_DDG_ENABLED", "true"));
        boolean isBingEnabled = "true".equalsIgnoreCase(env.getOrDefault("FREE_SEARCH_BING_ENABLED", "false"));
        return isDdgEnabled || isBingEnabled;
    }

    /**
     * nullValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static <T> T nullValue() {
        return null;
    }
}
