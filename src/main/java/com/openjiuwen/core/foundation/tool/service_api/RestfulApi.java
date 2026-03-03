/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.tool.service_api;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.service_api.parser.ParserRegistry;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * RESTful API tool that executes HTTP requests.
 * <p>
 * Mirrors Python's {@code RestfulApi} class. Uses JDK {@link HttpClient} instead of aiohttp.
 */
public class RestfulApi extends Tool {

    private final String url;
    private final String method;
    private final double timeout;
    private final int maxResponseByteSize;
    private final ApiParamMapper apiParamMapper;

    /**
     * Construct a new RestfulApi tool.
     *
     * @param card the RestfulApiCard configuration
     */
    public RestfulApi(RestfulApiCard card) {
        super(card);
        this.url = card.getUrl();
        this.method = card.getMethod();
        this.timeout = card.getTimeout();
        this.maxResponseByteSize = card.getMaxResponseByteSize();
        this.apiParamMapper = new ApiParamMapper(
                card.getInputParams(),
                card.getQueries(),
                card.getHeaders(),
                card.getPaths());
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        double finalTimeout = this.timeout;
        try {
            Map<ApiParamLocation, Map<String, Object>> mapResults =
                    apiParamMapper.map(inputs, ApiParamLocation.BODY);
            if (kwargs != null && kwargs.containsKey("timeout")) {
                finalTimeout = ((Number) kwargs.get("timeout")).doubleValue();
            }
            int maxSize = this.maxResponseByteSize;
            if (kwargs != null && kwargs.containsKey("max_response_byte_size")) {
                maxSize = ((Number) kwargs.get("max_response_byte_size")).intValue();
            }
            boolean raiseForStatus = kwargs == null || kwargs.getOrDefault("raise_for_status", true) != Boolean.FALSE;
            return executeRequest(mapResults, finalTimeout, maxSize, raiseForStatus);
        } catch (java.net.http.HttpTimeoutException e) {
            throw ErrorHelper.buildError(StatusCode.TOOL_RESTFUL_API_EXECUTION_TIMEOUT,
                    "method", "invoke", "timeout", String.valueOf(finalTimeout), "card", card.toString());
        } catch (BaseError e) {
            throw e;
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.TOOL_RESTFUL_API_EXECUTION_ERROR,
                    "method", "invoke", "reason", e.getMessage(), "card", card.toString());
        }
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        throw ErrorHelper.buildError(StatusCode.TOOL_STREAM_NOT_SUPPORTED, "card", card.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeRequest(Map<ApiParamLocation, Map<String, Object>> mapResults,
                                               double timeoutSec,
                                               int maxResponseByteSize,
                                               boolean raiseForStatus) throws Exception {
        // Build URL with path params
        String resolvedUrl = this.url;
        Map<String, Object> pathParams = mapResults.getOrDefault(ApiParamLocation.PATH, Map.of());
        for (var entry : pathParams.entrySet()) {
            resolvedUrl = resolvedUrl.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }

        // Append query params
        Map<String, Object> queryParams = mapResults.getOrDefault(ApiParamLocation.QUERY, Map.of());
        if (!queryParams.isEmpty()) {
            StringJoiner joiner = new StringJoiner("&");
            for (var entry : queryParams.entrySet()) {
                joiner.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
            }
            resolvedUrl = resolvedUrl + "?" + joiner;
        }

        // Build request
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(resolvedUrl))
                .timeout(Duration.ofMillis((long) (timeoutSec * 1000)));

        // Set headers
        Map<String, Object> headers = mapResults.getOrDefault(ApiParamLocation.HEADER, Map.of());
        for (var entry : headers.entrySet()) {
            requestBuilder.header(entry.getKey(), String.valueOf(entry.getValue()));
        }

        // Set body / method
        Map<String, Object> bodyParams = mapResults.getOrDefault(ApiParamLocation.BODY, Map.of());
        if ("GET".equalsIgnoreCase(method)) {
            // For GET, body params go as additional query params
            if (!bodyParams.isEmpty()) {
                StringJoiner joiner = new StringJoiner("&");
                for (var entry : bodyParams.entrySet()) {
                    joiner.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                            + "=" + URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
                }
                String sep = resolvedUrl.contains("?") ? "&" : "?";
                requestBuilder.uri(URI.create(resolvedUrl + sep + joiner));
            }
            requestBuilder.GET();
        } else {
            // POST: serialize body as JSON
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String jsonBody = mapper.writeValueAsString(bodyParams);
            requestBuilder.header("Content-Type", "application/json");
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        }

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofMillis((long) (timeoutSec * 1000)))
                .build();

        HttpResponse<byte[]> response = client.send(requestBuilder.build(),
                HttpResponse.BodyHandlers.ofByteArray());

        byte[] content = response.body();
        if (content != null && content.length > maxResponseByteSize) {
            throw ErrorHelper.buildError(StatusCode.TOOL_RESTFUL_API_RESPONSE_SIZE_EXCEED_LIMIT,
                    "method", "invoke",
                    "max_length", String.valueOf(maxResponseByteSize),
                    "actual_length", String.valueOf(content.length),
                    "card", card.toString());
        }

        if (raiseForStatus && (response.statusCode() < 200 || response.statusCode() >= 400)) {
            throw ErrorHelper.buildError(StatusCode.TOOL_RESTFUL_API_RESPONSE_ERROR,
                    "method", "invoke",
                    "code", String.valueOf(response.statusCode()),
                    "reason", "HTTP " + response.statusCode(),
                    "card", card.toString());
        }

        return formatResponse(response, content);
    }

    private Map<String, Object> formatResponse(HttpResponse<byte[]> response, byte[] content) {
        Map<String, String> responseHeaders = new LinkedHashMap<>();
        response.headers().map().forEach((k, v) -> {
            if (!v.isEmpty()) {
                responseHeaders.put(k, v.getFirst());
            }
        });

        int statusCode = response.statusCode();
        try {
            Object parsed = ParserRegistry.getInstance().parse(responseHeaders, content, statusCode);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", statusCode);
            result.put("data", parsed);
            result.put("url", response.uri().toString());
            result.put("headers", responseHeaders);
            if (statusCode >= 200 && statusCode < 300) {
                result.put("message", "success");
            } else {
                result.put("message", "HTTP " + statusCode);
            }
            return result;
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.TOOL_RESTFUL_API_RESPONSE_PROCESS_ERROR,
                    "reason", e.getMessage(), "card", card.toString());
        }
    }
}
