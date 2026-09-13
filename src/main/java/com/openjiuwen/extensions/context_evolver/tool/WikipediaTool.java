/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code wikipedia_tool.py} as a ready-to-register LocalFunction.
 * 
 * @since 0.1.7
 */
public final class WikipediaTool {
    private static final Logger log = LoggerFactory.getLogger(WikipediaTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final String apiUrl = "https://en.wikipedia.org/w/api.php";
    private static final String userAgent = "OpenJiuwenAgent/1.0 (Educational Research)";

    /**
     * WIKIPEDIA_TOOL.
     * 
     * @since 0.1.7
     */
    public static final Tool WIKIPEDIA_TOOL = createWikipediaTool();

    /**
     * WikipediaTool.
     * 
     * @since 0.1.7
     */
    private WikipediaTool() {
    }

    /**
     * SearchExecutor.
     * 
     * @since 0.1.7
     */
    @FunctionalInterface
    public interface SearchExecutor {
        /**
         * search.
         * 
         * @param query query
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        String search(String query) throws Exception;
    }

    /**
     * createWikipediaTool.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Tool createWikipediaTool() {
        return createWikipediaTool(WikipediaTool::searchWikipedia);
    }

    /**
     * createWikipediaTool.
     * 
     * @param executor executor
     * @return the result
     * @since 0.1.7
     */
    public static Tool createWikipediaTool(SearchExecutor executor) {
        ToolCard card = ToolCard.builder().id("wikipedia_search").name("wikipedia_search")
                .description("Search Wikipedia for information about a topic.").inputParams(buildInputSchema()).build();

        return new LocalFunction(card, inputs -> {
            String query = String.valueOf(inputs.getOrDefault("query", ""));
            try {
                return executor.search(query);
            } catch (Exception e) {
                log.error("Wikipedia search failed: {}", e.getMessage());
                return "Error searching Wikipedia: " + e.getMessage();
            }
        });
    }

    /**
     * searchWikipedia.
     * 
     * @param query query
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public static String searchWikipedia(String query) throws Exception {
        String normalizedQuery = query != null ? query.trim() : "";
        log.info("Searching Wikipedia for: {}", normalizedQuery);
        if (normalizedQuery.isEmpty()) {
            return "No Wikipedia results found for ''.";
        }
        Map<String, Object> searchResponse = getJson(Map.of("action", "query", "format", "json", "list", "search",
                "srsearch", normalizedQuery, "srlimit", "1"));
        List<Map<String, Object>> searchResults = extractSearchResults(searchResponse);
        if (searchResults.isEmpty()) {
            return "No Wikipedia results found for '" + normalizedQuery + "'.";
        }
        Map<String, Object> topResult = searchResults.get(0);
        String title = String.valueOf(topResult.getOrDefault("title", normalizedQuery));
        Object pageId = topResult.get("pageid");

        if (pageId == null) {
            return "Found page '" + title + "' for '" + normalizedQuery + "', but no summary available.";
        }
        Map<String, Object> summaryResponse = getJson(Map.of("action", "query", "format", "json", "prop", "extracts",
                "pageids", String.valueOf(pageId), "explaintext", "true", "exintro", "true", "exlimit", "1"));
        String extract = extractSummary(summaryResponse, String.valueOf(pageId));
        if (extract == null || extract.isBlank()) {
            return "Found page '" + title + "' for '" + normalizedQuery + "', but no summary available.";
        }

        String result = "Title: " + title + "\nSummary: " + extract;
        if (result.length() > 2000) {
            return result.substring(0, 2000) + "...";
        }
        return result;
    }

    /**
     * buildInputSchema.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> buildInputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", Map.of("type", "string", "description", "The search query for Wikipedia"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("query"));
        return schema;
    }

    /**
     * getJson.
     * 
     * @param params params
     * @return the result
     * @throws IOException IOException
     * @throws InterruptedException InterruptedException
     * @since 0.1.7
     */
    private static Map<String, Object> getJson(Map<String, String> params) throws IOException, InterruptedException {
        String queryString = params.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .reduce((left, right) -> left + "&" + right).orElse("");
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(apiUrl + "?" + queryString))
                .header("User-Agent", userAgent).GET().build();

        HttpResponse<String> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.readValue(response.body(), Map.class);
        return payload;
    }

    @SuppressWarnings("unchecked")
    /**
     * extractSearchResults.
     * 
     * @param payload payload
     * @return the result
     * @since 0.1.7
     */
    private static List<Map<String, Object>> extractSearchResults(Map<String, Object> payload) {
        Object query = payload.get("query");
        if (!(query instanceof Map<?, ?> queryMap)) {
            return List.of();
        }
        Object search = ((Map<String, Object>) queryMap).get("search");
        if (search instanceof List<?> searchList) {
            return (List<Map<String, Object>>) searchList;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    /**
     * extractSummary.
     * 
     * @param payload payload
     * @param pageId pageId
     * @return the result
     * @since 0.1.7
     */
    private static String extractSummary(Map<String, Object> payload, String pageId) {
        Object query = payload.get("query");
        if (!(query instanceof Map<?, ?> queryMap)) {
            return "";
        }
        Object pages = ((Map<String, Object>) queryMap).get("pages");
        if (!(pages instanceof Map<?, ?> pagesMap)) {
            return "";
        }
        Object page = ((Map<String, Object>) pagesMap).get(pageId);
        if (!(page instanceof Map<?, ?> pageMap)) {
            return "";
        }
        Object extract = ((Map<String, Object>) pageMap).get("extract");
        return extract != null ? String.valueOf(extract) : "";
    }
}
