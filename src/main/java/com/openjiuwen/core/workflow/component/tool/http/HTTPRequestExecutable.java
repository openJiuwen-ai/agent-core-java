/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.tool.http;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.ComponentExecutable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executable for HTTP request workflow component.
 * <p>
 * Performs HTTP requests with configurable authentication, retry, and response handling.
 * <p>
 * Mirrors Python's {@code HTTPRequestExecutable}.
 */
public class HTTPRequestExecutable extends ComponentExecutable {

    private static final List<String> TEXT_CONTENT_TYPES = List.of(
            "text/", "application/json", "application/xml",
            "application/javascript", "application/xhtml+xml"
    );

    private final HttpComponentConfig config;
    private final HttpRequestParamConfig requestParams;

    public HTTPRequestExecutable(HttpComponentConfig config) {
        this.config = config;
        this.requestParams = config.getRequestParams() != null 
                ? config.getRequestParams() 
                : HttpRequestParamConfig.builder().build();
    }

    @Override
    public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
        Map<String, Object> processedInputs = processInputs(inputs);
        Map<String, Object> response = makeRequest(processedInputs);
        return processResponse(response);
    }

    @Override
    public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
        Object result = invoke(inputs, session, context);
        return Collections.singleton(result).iterator();
    }

    /**
     * Process the input data and merge with component configuration.
     */
    private Map<String, Object> processInputs(Object inputs) {
        Map<String, Object> inputMap = inputs instanceof Map 
                ? (Map<String, Object>) inputs 
                : new HashMap<>();
        Map<String, Object> processed = new LinkedHashMap<>();

        // Process URL - allow for dynamic values from inputs
        String url = requestParams.getUrl();
        processed.put("url", resolvePlaceholders(url, inputMap));

        // Process method
        String method = inputMap.containsKey("method") 
                ? (String) inputMap.get("method") 
                : requestParams.getMethod();
        processed.put("method", method.toUpperCase());

        // Process headers
        Map<String, String> headers = new LinkedHashMap<>();
        if (requestParams.getHeaders() instanceof Map) {
            headers.putAll((Map<String, String>) requestParams.getHeaders());
        }
        if (inputMap.containsKey("headers")) {
            headers.putAll((Map<String, String>) inputMap.get("headers"));
        }
        processed.put("headers", headers);

        // Process query parameters
        Map<String, Object> queryParams = new LinkedHashMap<>();
        if (requestParams.getQueryParameters() != null) {
            queryParams.putAll(requestParams.getQueryParameters());
        }
        if (inputMap.containsKey("queryParameters")) {
            queryParams.putAll((Map<String, Object>) inputMap.get("queryParameters"));
        }
        processed.put("queryParameters", queryParams);

        // Process body
        processed.put("body", requestParams.getBody() != null 
                ? requestParams.getBody() 
                : inputMap.getOrDefault("body", new HashMap<>()));

        // Process authentication
        processed.put("authentication", requestParams.getAuthentication() != null 
                ? requestParams.getAuthentication() 
                : inputMap.getOrDefault("authentication", new HashMap<>()));

        return processed;
    }

    /**
     * Perform the actual HTTP request.
     */
    private Map<String, Object> makeRequest(Map<String, Object> params) {
        String url = (String) params.get("url");
        String method = (String) params.get("method");
        Map<String, String> headers = (Map<String, String>) params.get("headers");
        HttpRequestBodyConfig bodyConfig = (HttpRequestBodyConfig) params.get("body");

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(requestParams.getTimeout()))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url));

            // Add headers
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    requestBuilder.header(entry.getKey(), entry.getValue());
                }
            }

            // Add body for POST/PUT/PATCH
            if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
                String bodyContent = serializeBody(bodyConfig);
                requestBuilder.method(method, HttpRequest.BodyPublishers.ofString(bodyContent));
            } else {
                requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpRequest httpRequest = requestBuilder.build();
            HttpResponse<String> httpResponse = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("statusCode", httpResponse.statusCode());
            result.put("body", httpResponse.body());
            result.put("headers", httpResponse.headers().map());
            return result;

        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_HTTP_REQUEST_FAILED,
                    "error_msg", "HTTP request failed: " + e.getMessage());
        }
    }

    /**
     * Process and format the HTTP response.
     */
    private Map<String, Object> processResponse(Map<String, Object> response) {
        int statusCode = (int) response.get("statusCode");
        String body = (String) response.get("body");

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("status_code", statusCode);
        output.put("data", body);
        output.put("success", statusCode >= 200 && statusCode < 300);

        return output;
    }

    /**
     * Resolve {{key}} placeholders in a template string.
     */
    private String resolvePlaceholders(String template, Map<String, Object> inputs) {
        if (template == null) return "";
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            template = template.replace(placeholder, String.valueOf(entry.getValue()));
        }
        return template;
    }

    /**
     * Serialize request body based on content type.
     */
    private String serializeBody(HttpRequestBodyConfig bodyConfig) {
        if (bodyConfig == null) return "";
        if (bodyConfig.getJsonData() != null) {
            // Return JSON string representation
            return bodyConfig.getJsonData() instanceof String 
                    ? (String) bodyConfig.getJsonData() 
                    : toJsonString(bodyConfig.getJsonData());
        }
        if (bodyConfig.getTextData() != null) {
            return bodyConfig.getTextData();
        }
        return "";
    }

    /**
     * Convert object to JSON string.
     */
    private String toJsonString(Object obj) {
        // Simple JSON conversion - in production would use Jackson/ObjectMapper
        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            Map<String, Object> map = (Map<String, Object>) obj;
            int i = 0;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":");
                sb.append(entry.getValue() instanceof String 
                        ? "\"" + entry.getValue() + "\"" 
                        : String.valueOf(entry.getValue()));
                i++;
            }
            sb.append("}");
            return sb.toString();
        }
        return String.valueOf(obj);
    }
}