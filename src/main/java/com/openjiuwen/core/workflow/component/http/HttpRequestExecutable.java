/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.ComponentExecutable;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Executable for the HTTP request workflow component.
 * <p>
 * Performs HTTP requests based on {@link HttpComponentConfig}, processes inputs
 * with template placeholder resolution, and returns structured responses.
 * <p>
 * Mirrors Python's {@code HTTPRequestExecutable}.
 * 
 * @since 0.1.7
 */
public class HttpRequestExecutable extends ComponentExecutable {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpComponentConfig config;

    /**
     * HttpRequestExecutable.
     * 
     * @param config config
     * @since 0.1.7
     */
    public HttpRequestExecutable(HttpComponentConfig config) {
        this.config = config;
    }

    /**
     * getConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public HttpComponentConfig getConfig() {
        return config;
    }

    /**
     * invoke.
     * 
     * @param inputs inputs
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
        Map<String, Object> inputMap = (inputs instanceof Map) ? (Map<String, Object>) inputs : Map.of();
        Map<String, Object> processed = processInputs(inputMap);
        Map<String, Object> response = makeRequest(processed);
        return processResponse(response);
    }

    /**
     * stream.
     * 
     * @param inputs inputs
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
        Object result = invoke(inputs, session, context);
        return List.of(result).iterator();
    }

    /**
     * collect.
     * 
     * @param inputs inputs
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object collect(Object inputs, NodeSessionApi session, ModelContext context) {
        return invoke(inputs, session, context);
    }

    /**
     * transform.
     * 
     * @param inputs inputs
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<Object> transform(Object inputs, NodeSessionApi session, ModelContext context) {
        return stream(inputs, session, context);
    }

    // ---- Input processing ----

    @SuppressWarnings("unchecked")
    Map<String, Object> processInputs(Map<String, Object> inputs) {
        Map<String, Object> processed = new LinkedHashMap<>();
        HttpRequestParamConfig params = config.getRequestParams();

        processUrl(inputs, processed, params);
        processMethod(inputs, processed, params);
        processHeaders(inputs, processed, params);
        processQueryParams(inputs, processed, params);
        processBody(inputs, processed, params);
        processAuthentication(inputs, processed, params);

        return processed;
    }

    /**
     * processUrl.
     * 
     * @param inputs inputs
     * @param processed processed
     * @param params params
     * @since 0.1.7
     */
    private void processUrl(Map<String, Object> inputs, Map<String, Object> processed, HttpRequestParamConfig params) {
        String url = resolvePlaceholders(params.getUrl(), inputs);
        processed.put("url", url);
    }

    /**
     * processMethod.
     * 
     * @param inputs inputs
     * @param processed processed
     * @param params params
     * @since 0.1.7
     */
    private void processMethod(Map<String, Object> inputs, Map<String, Object> processed,
            HttpRequestParamConfig params) {
        String method = inputs.containsKey("method")
                ? String.valueOf(inputs.get("method")).toUpperCase(Locale.ROOT)
                : (params.getMethod() != null ? params.getMethod().toUpperCase(Locale.ROOT) : "GET");
        processed.put("method", method);
    }

    @SuppressWarnings("unchecked")
    /**
     * processHeaders.
     * 
     * @param inputs inputs
     * @param processed processed
     * @param params params
     * @since 0.1.7
     */
    private void processHeaders(Map<String, Object> inputs, Map<String, Object> processed,
            HttpRequestParamConfig params) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (params.getHeaders() != null) {
            Object rawHeaders = params.getHeaders();
            if (rawHeaders instanceof String headerStr) {
                Optional<Object> directValue = tryResolveSingleVariable(headerStr, inputs);
                if (directValue.isPresent() && directValue.get() instanceof Map) {
                    ((Map<String, ?>) directValue.get()).forEach((k, v) -> {
                        if (k != null && v != null) {
                            headers.put(String.valueOf(k), String.valueOf(v));
                        }
                    });
                } else {
                    headers.putAll(resolveTemplateToMap(headerStr, inputs));
                }
            } else if (rawHeaders instanceof Map) {
                ((Map<String, String>) rawHeaders).forEach((k, v) -> {
                    if (v != null) {
                        headers.put(k, resolvePlaceholders(v, inputs));
                    }
                });
            } else {
                // no-op
            }
        }
        if (inputs.containsKey("headers") && inputs.get("headers") instanceof Map) {
            ((Map<String, String>) inputs.get("headers")).forEach(headers::put);
        }
        processed.put("headers", headers);
    }

    @SuppressWarnings("unchecked")
    /**
     * processQueryParams.
     * 
     * @param inputs inputs
     * @param processed processed
     * @param params params
     * @since 0.1.7
     */
    private void processQueryParams(Map<String, Object> inputs, Map<String, Object> processed,
            HttpRequestParamConfig params) {
        Map<String, Object> queryParams = new LinkedHashMap<>();
        if (params.getQueryParameters() != null) {
            params.getQueryParameters().forEach((k, v) -> {
                if (v != null) {
                    String resolved = resolvePlaceholders(String.valueOf(v), inputs);
                    queryParams.put(k, resolved);
                }
            });
        }
        if (inputs.containsKey("query_parameters") && inputs.get("query_parameters") instanceof Map) {
            ((Map<String, Object>) inputs.get("query_parameters")).forEach(queryParams::put);
        }
        processed.put("query_parameters", queryParams);
    }

    @SuppressWarnings("unchecked")
    /**
     * processBody.
     * 
     * @param inputs inputs
     * @param processed processed
     * @param params params
     * @since 0.1.7
     */
    private void processBody(Map<String, Object> inputs, Map<String, Object> processed, HttpRequestParamConfig params) {
        HttpRequestBodyConfig bodyConfig = params.getBody();
        if (bodyConfig == null) {
            processed.put("body", inputs.getOrDefault("body", null));
            return;
        }
        HttpRequestBodyConfig resolvedBody = new HttpRequestBodyConfig();
        resolvedBody.setContentType(bodyConfig.getContentType());
        if (bodyConfig.getJsonData() != null) {
            Object jsonData = bodyConfig.getJsonData();
            if (jsonData instanceof String jsonStr) {
                Optional<Object> directValue = tryResolveSingleVariable(jsonStr, inputs);
                if (directValue.isPresent() && !(directValue.get() instanceof String)) {
                    resolvedBody.setJsonData(directValue.get());
                } else {
                    resolvedBody.setJsonData(resolveTemplateToMap(jsonStr, inputs));
                }
            } else {
                resolvedBody.setJsonData(jsonData);
            }
        }
        resolvedBody.setFormData(bodyConfig.getFormData());
        resolvedBody.setMultipartForm(bodyConfig.getMultipartForm());
        resolvedBody.setBinaryData(bodyConfig.getBinaryData());
        resolvedBody.setTextData(bodyConfig.getTextData());
        processed.put("body", resolvedBody);
    }

    /**
     * processAuthentication.
     * 
     * @param inputs inputs
     * @param processed processed
     * @param params params
     * @since 0.1.7
     */
    private void processAuthentication(Map<String, Object> inputs, Map<String, Object> processed,
            HttpRequestParamConfig params) {
        HttpAuthConfig authConfig = params.getAuthentication();
        processed.put("authentication", authConfig != null ? authConfig : inputs.getOrDefault("authentication", null));
    }

    // ---- HTTP request ----

    @SuppressWarnings("unchecked")
    Map<String, Object> makeRequest(Map<String, Object> processed) {
        RequestParams params = extractRequestParams(processed);
        params.headers = applyAuthentication(params.headers, params.authObj);
        params.url = appendQueryParams(params.url, params.queryParams);

        BodyContent bodyContent = prepareBodyContent(params.bodyObj);
        HttpRequest httpRequest = buildHttpRequest(params, bodyContent);

        return executeWithRetry(httpRequest, params);
    }

    @SuppressWarnings("unchecked")
    /**
     * extractRequestParams.
     * 
     * @param processed processed
     * @return the result
     * @since 0.1.7
     */
    private RequestParams extractRequestParams(Map<String, Object> processed) {
        RequestParams params = new RequestParams();
        Object urlObj = processed.get("url");
        params.url = urlObj instanceof String ? (String) urlObj : "";
        Object methodObj = processed.get("method");
        params.method = methodObj instanceof String ? (String) methodObj : "GET";
        params.headers = (Map<String, String>) processed.getOrDefault("headers", new LinkedHashMap<>());
        params.queryParams = (Map<String, Object>) processed.getOrDefault("query_parameters", new LinkedHashMap<>());
        params.bodyObj = processed.get("body");
        params.authObj = processed.get("authentication");
        params.requestParams = config.getRequestParams();
        return params;
    }

    /**
     * prepareBodyContent.
     * 
     * @param bodyObj bodyObj
     * @return the result
     * @since 0.1.7
     */
    private BodyContent prepareBodyContent(Object bodyObj) {
        BodyContent content = new BodyContent();
        if (bodyObj instanceof HttpRequestBodyConfig bodyConfig) {
            if (bodyConfig.getContentType() == HttpContentType.JSON && bodyConfig.getJsonData() != null) {
                try {
                    content.body = MAPPER.writeValueAsString(bodyConfig.getJsonData());
                    content.contentType = "application/json";
                } catch (JsonProcessingException e) {
                    throw ErrorHelper.buildError(StatusCode.COMPONENT_TOOL_EXECUTION_ERROR, "error_msg",
                            "Failed to serialize JSON body: " + e.getMessage());
                }
            } else if (bodyConfig.getContentType() == HttpContentType.TEXT && bodyConfig.getTextData() != null) {
                content.body = bodyConfig.getTextData();
                content.contentType = "text/plain";
            } else if (bodyConfig.getContentType() == HttpContentType.FORM && bodyConfig.getFormData() != null) {
                content.body = encodeFormData(bodyConfig.getFormData());
                content.contentType = "application/x-www-form-urlencoded";
            } else {
                // no-op
            }
        }
        return content;
    }

    /**
     * buildHttpRequest.
     * 
     * @param params params
     * @param bodyContent bodyContent
     * @return the result
     * @since 0.1.7
     */
    private HttpRequest buildHttpRequest(RequestParams params, BodyContent bodyContent) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(params.url));

        long timeoutMillis = secondsToMillis(params.requestParams.getTimeout());
        if (timeoutMillis > 0) {
            requestBuilder.timeout(Duration.ofMillis(timeoutMillis));
        }

        if (bodyContent.contentType != null && !params.headers.containsKey("Content-Type")) {
            requestBuilder.header("Content-Type", bodyContent.contentType);
        }
        params.headers.forEach(requestBuilder::header);

        if (bodyContent.body != null) {
            requestBuilder.method(params.method,
                    HttpRequest.BodyPublishers.ofString(bodyContent.body, StandardCharsets.UTF_8));
        } else {
            requestBuilder.method(params.method, HttpRequest.BodyPublishers.noBody());
        }

        return requestBuilder.build();
    }

    /**
     * executeWithRetry.
     * 
     * @param httpRequest httpRequest
     * @param params params
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> executeWithRetry(HttpRequest httpRequest, RequestParams params) {
        HttpRetryConfig retryConfig = params.requestParams.getRetryConfig();
        int maxRetries = (retryConfig != null && retryConfig.isEnabled()) ? retryConfig.getMaxRetries() : 0;
        int retryCount = 0;

        while (retryCount <= maxRetries) {
            try {
                HttpClient client = createHttpClient(params.requestParams);
                HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();

                if (shouldRetry(retryConfig, statusCode, retryCount, maxRetries)) {
                    Thread.sleep(calculateRetryDelay(retryCount, retryConfig));
                    retryCount++;
                    continue;
                }

                return buildResponseMap(response);
            } catch (BaseError e) {
                throw e;
            } catch (IOException e) {
                if (shouldRetryOnException(retryConfig, retryCount, maxRetries)) {
                    sleepForRetry(calculateRetryDelay(retryCount, retryConfig));
                    retryCount++;
                    continue;
                }
                throw ErrorHelper.buildError(StatusCode.COMPONENT_TOOL_EXECUTION_ERROR, "error_msg",
                        "HTTP request failed: " + e.getMessage());
            } catch (InterruptedException e) {
                throw ErrorHelper.buildError(StatusCode.COMPONENT_TOOL_EXECUTION_ERROR, "error_msg",
                        "HTTP request interrupted: " + e.getMessage());
            }
        }

        throw ErrorHelper.buildError(StatusCode.COMPONENT_TOOL_EXECUTION_ERROR, "error_msg",
                "HTTP request failed after retries");
    }

    /**
     * createHttpClient.
     * 
     * @param params params
     * @return the result
     * @since 0.1.7
     */
    private HttpClient createHttpClient(HttpRequestParamConfig params) {
        HttpClient.Builder clientBuilder =
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NORMAL);
        long timeoutMillis = secondsToMillis(params.getTimeout());
        if (timeoutMillis > 0) {
            clientBuilder.connectTimeout(Duration.ofMillis(timeoutMillis));
        }
        return clientBuilder.build();
    }

    /**
     * shouldRetry.
     * 
     * @param retryConfig retryConfig
     * @param statusCode statusCode
     * @param retryCount retryCount
     * @param maxRetries maxRetries
     * @return the result
     * @since 0.1.7
     */
    private boolean shouldRetry(HttpRetryConfig retryConfig, int statusCode, int retryCount, int maxRetries) {
        return retryConfig != null && retryConfig.isEnabled()
                && retryConfig.getRetryOnStatusCodes().contains(statusCode) && retryCount < maxRetries;
    }

    /**
     * shouldRetryOnException.
     * 
     * @param retryConfig retryConfig
     * @param retryCount retryCount
     * @param maxRetries maxRetries
     * @return the result
     * @since 0.1.7
     */
    private boolean shouldRetryOnException(HttpRetryConfig retryConfig, int retryCount, int maxRetries) {
        return retryConfig != null && retryConfig.isEnabled() && retryCount < maxRetries;
    }

    /**
     * sleepForRetry.
     * 
     * @param delay delay
     * @since 0.1.7
     */
    private void sleepForRetry(long delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_TOOL_EXECUTION_ERROR, "error_msg",
                    "HTTP request retry interrupted: " + ie.getMessage());
        }
    }

    /**
     * buildResponseMap.
     * 
     * @param response response
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> buildResponseMap(HttpResponse<String> response) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status_code", response.statusCode());
        result.put("headers", response.headers().map());
        result.put("content", response.body());
        result.put("url", response.uri().toString());
        return result;
    }

    // Helper classes for request parameters
    private static class RequestParams {
        String url;
        String method;
        Map<String, String> headers;
        Map<String, Object> queryParams;
        Object bodyObj;
        Object authObj;
        HttpRequestParamConfig requestParams;
    }

    private static class BodyContent {
        String body;
        String contentType;
    }

    // ---- Response processing ----

    @SuppressWarnings("unchecked")
    Map<String, Object> processResponse(Map<String, Object> response) {
        Object statusCodeObj = response.get("status_code");
        int statusCode = statusCodeObj instanceof Number ? ((Number) statusCodeObj).intValue() : 0;
        Object content = response.get("content");
        Map<String, List<String>> responseHeaders =
            (Map<String, List<String>>) response.getOrDefault("headers", Map.of());

        HttpRequestParamConfig params = config.getRequestParams();
        HttpResponseHandlingConfig handlingConfig = params.getResponseHandling();

        // Parse body
        String contentTypeHeader =
            responseHeaders.getOrDefault("Content-Type", List.of()).stream().findFirst().orElse("");

        HttpResponseFormat format = handlingConfig.getResponseFormat();
        if (format == HttpResponseFormat.AUTODETECT) {
            if (contentTypeHeader.contains("application/json")) {
                format = HttpResponseFormat.JSON;
            } else if (contentTypeHeader.startsWith("text/")) {
                format = HttpResponseFormat.TEXT;
            } else {
                format = HttpResponseFormat.BINARY;
            }
        }
        Object parsedBody = content;
        if (format == HttpResponseFormat.JSON && content instanceof String bodyStr) {
            try {
                parsedBody = MAPPER.readValue(bodyStr, Object.class);
            } catch (JsonProcessingException e) {
                parsedBody = content;
            }
        }

        // Build result
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("statusCode", statusCode);
        result.put("body", parsedBody);

        // Determine success
        boolean isSuccess = handlingConfig.getResponseCodeSuccessCodes().contains(statusCode)
                || (handlingConfig.getResponseCodeFailureCodes().isEmpty() && statusCode >= 200 && statusCode < 300);
        boolean isFailure = handlingConfig.getResponseCodeFailureCodes().contains(statusCode) || statusCode >= 400;
        result.put("ok", isSuccess && !isFailure);

        return result;
    }

    // ---- Authentication ----

    @SuppressWarnings("unchecked")
    Map<String, String> applyAuthentication(Map<String, String> headers, Object authObj) {
        if (authObj == null) {
            return headers;
        }

        HttpAuthConfig authConfig;
        if (authObj instanceof HttpAuthConfig config) {
            authConfig = config;
        } else if (authObj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) authObj;
            authConfig = new HttpAuthConfig();
            if (map.containsKey("type")) {
                authConfig.setType(HttpAuthType.fromValue(String.valueOf(map.get("type"))));
            }
            if (map.containsKey("username")) {
                authConfig.setUsername(String.valueOf(map.get("username")));
            }
            if (map.containsKey("password")) {
                authConfig.setPassword(String.valueOf(map.get("password")));
            }
            if (map.containsKey("token")) {
                authConfig.setToken(String.valueOf(map.get("token")));
            }
            if (map.containsKey("api_key")) {
                authConfig.setApiKey(String.valueOf(map.get("api_key")));
            }
        } else {
            return headers;
        }

        if (authConfig.getType() == HttpAuthType.NONE) {
            return headers;
        }

        headers = new LinkedHashMap<>(headers);

        if (authConfig.getType() == HttpAuthType.BASIC && authConfig.getUsername() != null
                && authConfig.getPassword() != null) {
            String credentials = authConfig.getUsername() + ":" + authConfig.getPassword();
            String encoded = java.util.Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            headers.put("Authorization", "Basic " + encoded);
        } else if (authConfig.getType() == HttpAuthType.BEARER && authConfig.getToken() != null) {
            headers.put("Authorization", "Bearer " + authConfig.getToken());
        } else if (authConfig.getType() == HttpAuthType.API_KEY && authConfig.getApiKey() != null) {
            if ("header".equals(authConfig.getInLocation())) {
                headers.put(authConfig.getName(), authConfig.getApiKey());
            }
        }

        return headers;
    }

    // ---- Utility methods ----

    static String resolvePlaceholders(String template, Map<String, Object> inputs) {
        if (template == null || inputs == null) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            result = result.replace(placeholder, String.valueOf(entry.getValue()));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> resolveTemplateToMap(String template, Map<String, Object> inputs) {
        String resolved = resolvePlaceholders(template, inputs);
        try {
            return MAPPER.readValue(resolved, Map.class);
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * secondsToMillis.
     * 
     * @param seconds seconds
     * @return the result
     * @since 0.1.7
     */
    private static long secondsToMillis(double seconds) {
        if (seconds <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(seconds).multiply(BigDecimal.valueOf(1000)).setScale(0, RoundingMode.HALF_UP)
                .max(BigDecimal.ONE).longValue();
    }

    /**
     * appendQueryParams.
     * 
     * @param url url
     * @param params params
     * @return the result
     * @since 0.1.7
     */
    private static String appendQueryParams(String url, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        StringBuilder builder = new StringBuilder(url);
        builder.append(url.contains("?") ? "&" : "?");
        boolean isFirst = true;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            if (!isFirst) {
                builder.append("&");
            }
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)).append("=")
                    .append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
            isFirst = false;
        }
        return builder.toString();
    }

    /**
     * encodeFormData.
     * 
     * @param formData formData
     * @return the result
     * @since 0.1.7
     */
    private static String encodeFormData(Map<String, Object> formData) {
        StringBuilder builder = new StringBuilder();
        boolean isFirst = true;
        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            if (!isFirst) {
                builder.append("&");
            }
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)).append("=")
                    .append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
            isFirst = false;
        }
        return builder.toString();
    }

    /**
     * calculateRetryDelay.
     * 
     * @param retryCount retryCount
     * @param retryConfig retryConfig
     * @return the result
     * @since 0.1.7
     */
    private static long calculateRetryDelay(int retryCount, HttpRetryConfig retryConfig) {
        long baseDelay = retryConfig.getRetryDelay();
        String backoffType = retryConfig.getBackoffType();
        if ("fixed".equals(backoffType)) {
            return baseDelay;
        } else if ("linear".equals(backoffType)) {
            return baseDelay * (retryCount + 1);
        } else {
            // exponential
            return baseDelay * (1L << retryCount);
        }
    }

    /**
     * Try to resolve a template that consists of a single variable like "{{key}}".
     * If the template is exactly a single placeholder and the value exists in inputs,
     * return the value directly (preserving its type, e.g. Map, List, Number).
     * Otherwise return Optional.empty().
     *
     * @param template the template string to resolve
     * @param inputs the input map containing variable values
     * @return Optional containing the resolved value, or Optional.empty() if not resolvable
     */
    static Optional<Object> tryResolveSingleVariable(String template, Map<String, Object> inputs) {
        if (template == null || inputs == null) {
            return Optional.empty();
        }
        String trimmed = template.trim();
        if (trimmed.startsWith("{{") && trimmed.endsWith("}}")) {
            String varName = trimmed.substring(2, trimmed.length() - 2).trim();
            if (!varName.isEmpty() && inputs.containsKey(varName)) {
                return Optional.ofNullable(inputs.get(varName));
            }
        }
        return Optional.empty();
    }
}
