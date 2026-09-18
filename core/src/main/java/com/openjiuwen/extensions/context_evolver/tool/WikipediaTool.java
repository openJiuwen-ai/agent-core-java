/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;

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
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Wikipedia search tool and module-level exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.context_evolver.tool.wikipedia_tool} in
 * {@code openjiuwen/extensions/context_evolver/tool/wikipedia_tool.py}.</p>
 */
public final class WikipediaTool {
    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;
    private static final String API_URL = "https://en.wikipedia.org/w/api.php";
    private static final String USER_AGENT = "OpenJiuwenAgent/1.0 (Educational Research)";
    private static final int MAX_RESULT_LENGTH = 2000;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final WikipediaHttpTransport DEFAULT_TRANSPORT = new DefaultWikipediaHttpTransport();

    public static final ToolCard WIKIPEDIA_TOOL_CARD = ToolCard.builder()
            .id("wikipedia_search")
            .name("wikipedia_search")
            .description("Search Wikipedia for information about a topic.")
            .inputParams(WikipediaSearchParams.modelJsonSchema())
            .build();

    public static final LocalFunction WIKIPEDIA_TOOL = new LocalFunction(
            WIKIPEDIA_TOOL_CARD,
            inputs -> searchWikipedia(String.valueOf(inputs.get("query"))));

    private WikipediaTool() {
    }

    public static String searchWikipedia(String query) {
        return searchWikipedia(query, DEFAULT_TRANSPORT);
    }

    public static String search_wikipedia(String query) {
        return searchWikipedia(query);
    }

    static String searchWikipedia(String query, WikipediaHttpTransport transport) {
        LOGGER.info("Searching Wikipedia for: %s", query);
        Map<String, String> headers = Map.of("User-Agent", USER_AGENT);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action", "query");
        params.put("format", "json");
        params.put("list", "search");
        params.put("srsearch", query);
        params.put("srlimit", "1");

        try {
            Map<String, Object> response = transport.get(API_URL, params, headers);
            List<Map<String, Object>> searchResults = searchResults(response);
            if (searchResults.isEmpty()) {
                return "No Wikipedia results found for '" + query + "'.";
            }

            Map<String, Object> firstResult = searchResults.get(0);
            Object pageId = requireValue(firstResult, "pageid");
            Map<String, String> summaryParams = new LinkedHashMap<>();
            summaryParams.put("action", "query");
            summaryParams.put("format", "json");
            summaryParams.put("prop", "extracts");
            summaryParams.put("pageids", String.valueOf(pageId));
            summaryParams.put("explaintext", "true");
            summaryParams.put("exintro", "true");
            summaryParams.put("exlimit", "1");

            Map<String, Object> summaryResponse = transport.get(API_URL, summaryParams, headers);
            Map<String, Object> pages = objectMap(objectMap(summaryResponse.get("query")).get("pages"));
            Map<String, Object> pageData = objectMap(pages.get(String.valueOf(pageId)));
            String extract = stringValue(pageData.get("extract"));
            if (extract.isEmpty()) {
                return "Found page '" + requireValue(firstResult, "title") + "' for '" + query
                        + "', but no summary available.";
            }

            String result = "Title: " + requireValue(firstResult, "title") + "\nSummary: " + extract;
            if (result.length() > MAX_RESULT_LENGTH) {
                return result.substring(0, MAX_RESULT_LENGTH) + "...";
            }
            return result;
        } catch (Exception exception) {
            LOGGER.error("Wikipedia search failed: %s", exception);
            return "Error searching Wikipedia: " + exception.getMessage();
        }
    }

    private static Object requireValue(Map<String, Object> values, String key) {
        if (!values.containsKey(key)) {
            throw new IllegalArgumentException(key);
        }
        return values.get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> searchResults(Map<String, Object> response) {
        Object value = objectMap(response.get("query")).get("search");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static URI uriWithParams(String url, Map<String, String> params) {
        StringJoiner joiner = new StringJoiner("&");
        params.forEach((key, value) -> joiner.add(encode(key) + "=" + encode(value)));
        return URI.create(url + "?" + joiner);
    }

    private static String encode(String value) {
        return URLEncoder.encode(Objects.toString(value, ""), StandardCharsets.UTF_8);
    }

    /**
     * Mirrors Python's {@code WikipediaSearchParams} in
     * {@code openjiuwen/extensions/context_evolver/tool/wikipedia_tool.py}.
     */
    public record WikipediaSearchParams(String query) {
        public static Map<String, Object> modelJsonSchema() {
            Map<String, Object> querySchema = new LinkedHashMap<>();
            querySchema.put("description", "The search query for Wikipedia");
            querySchema.put("title", "Query");
            querySchema.put("type", "string");

            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("query", querySchema);

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("properties", properties);
            schema.put("required", List.of("query"));
            schema.put("title", "WikipediaSearchParams");
            schema.put("type", "object");
            return schema;
        }

        public static Map<String, Object> model_json_schema() {
            return modelJsonSchema();
        }
    }

    /**
     * Mirrors Python's {@code requests.get(...).json()} boundary in
     * {@code openjiuwen/extensions/context_evolver/tool/wikipedia_tool.py}.
     */
    interface WikipediaHttpTransport {
        Map<String, Object> get(String url, Map<String, String> params, Map<String, String> headers)
                throws Exception;
    }

    /**
     * Mirrors Python's two Wikipedia API GET calls in
     * {@code openjiuwen/extensions/context_evolver/tool/wikipedia_tool.py}.
     */
    private static final class DefaultWikipediaHttpTransport implements WikipediaHttpTransport {
        private final HttpClient client = HttpClient.newHttpClient();

        @Override
        public Map<String, Object> get(String url, Map<String, String> params, Map<String, String> headers)
                throws IOException, InterruptedException {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uriWithParams(url, params)).GET();
            headers.forEach(requestBuilder::header);
            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("HTTP " + statusCode);
            }
            return MAPPER.readValue(response.body(), new TypeReference<>() {
            });
        }
    }
}
