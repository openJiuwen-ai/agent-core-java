/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.tool.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.workflow.ComponentExecutable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Executable for HTTP request workflow component.
 * <p>
 * Performs HTTP requests with configurable authentication, retry, and response handling.
 * <p>
 * Mirrors Python's {@code HTTPRequestExecutable} in
 * {@code openjiuwen/core/workflow/components/tool/http/http_request_component.py}.
 */
public class HTTPRequestExecutable extends ComponentExecutable {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> TEXT_CONTENT_TYPES = List.of(
            "text/", "application/json", "application/xml",
            "application/javascript", "application/xhtml+xml"
    );
    private static final List<Integer> DEFAULT_SUCCESS_CODES = List.of(200, 201, 202, 204);
    private static final List<Integer> DEFAULT_RETRY_STATUS_CODES = List.of(429, 500, 502, 503, 504);

    private final HttpComponentConfig config;
    private final HttpRequestParamConfig requestParams;

    public HTTPRequestExecutable(HttpComponentConfig config) {
        this.config = config == null ? new HttpComponentConfig() : config;
        this.requestParams = this.config.getRequestParams() == null
                ? HttpRequestParamConfig.builder().build()
                : this.config.getRequestParams();
    }

    @Override
    public Object invoke(Object inputs, BaseSession session, ModelContext context) {
        Map<String, Object> processedInputs = processInputs(inputs);
        Map<String, Object> response = makeRequest(processedInputs);
        return processResponse(response);
    }

    @Override
    public Iterator<Object> stream(Object inputs, BaseSession session, ModelContext context) {
        return Collections.singleton(invoke(inputs, session, context)).iterator();
    }

    @Override
    public Object collect(Object inputs, BaseSession session, ModelContext context) {
        return invoke(inputs, session, context);
    }

    @Override
    public Iterator<Object> transform(Object inputs, BaseSession session, ModelContext context) {
        return stream(inputs, session, context);
    }

    /**
     * Process the input data and merge with component configuration.
     *
     * @param inputs workflow input map
     * @return normalized request parameter map
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> processInputs(Object inputs) {
        Map<String, Object> inputMap = inputs instanceof Map<?, ?> rawMap
                ? normalizeMap(rawMap)
                : new LinkedHashMap<>();
        Map<String, Object> processed = new LinkedHashMap<>();

        processed.put("url", resolvePlaceholders(requestParams.getUrl(), inputMap));

        Object inputMethod = inputMap.getOrDefault("method", requestParams.getMethod());
        String method = inputMethod == null ? "GET" : String.valueOf(inputMethod);
        processed.put("method", method.toUpperCase(Locale.ROOT));

        Map<String, String> headers = headersFromConfig(requestParams.getHeaders(), inputMap);
        Object inputHeaders = firstPresent(inputMap, "headers", "header");
        if (inputHeaders instanceof Map<?, ?> rawHeaders) {
            headers.putAll(normalizeStringMap(rawHeaders));
        }
        processed.put("headers", headers);

        Map<String, Object> queryParams = new LinkedHashMap<>();
        if (requestParams.getQueryParameters() != null) {
            for (Map.Entry<String, Object> entry : requestParams.getQueryParameters().entrySet()) {
                Object value = entry.getValue();
                queryParams.put(entry.getKey(), value instanceof String text ? resolvePlaceholders(text, inputMap) : value);
            }
        }
        Object inputQuery = firstPresent(inputMap, "query_parameters", "queryParameters");
        if (inputQuery instanceof Map<?, ?> rawQuery) {
            queryParams.putAll(normalizeMap(rawQuery));
        }
        processed.put("query_parameters", queryParams);

        Object rawBody = requestParams.getBody() == null
                ? firstPresent(inputMap, "body")
                : requestParams.getBody();
        processed.put("body", normalizeBodyConfig(rawBody, inputMap));

        Object rawAuth = requestParams.getAuthentication() == null
                ? firstPresent(inputMap, "authentication")
                : requestParams.getAuthentication();
        processed.put("authentication", normalizeAuthConfig(rawAuth));

        return processed;
    }

    /**
     * Perform the actual HTTP request with retry behavior.
     *
     * @param params normalized request parameters
     * @return raw response map
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> makeRequest(Map<String, Object> params) {
        String url = String.valueOf(params.getOrDefault("url", ""));
        String method = String.valueOf(params.getOrDefault("method", "GET"));
        Map<String, String> headers = params.get("headers") instanceof Map<?, ?> rawHeaders
                ? normalizeStringMap(rawHeaders)
                : new LinkedHashMap<>();
        Map<String, Object> queryParams = params.get("query_parameters") instanceof Map<?, ?> rawQuery
                ? normalizeMap(rawQuery)
                : new LinkedHashMap<>();
        HttpRequestBodyConfig bodyConfig = normalizeBodyConfig(params.get("body"), Map.of());
        HttpAuthConfig authConfig = normalizeAuthConfig(params.get("authentication"));

        applyAuthentication(headers, queryParams, bodyConfig, authConfig);

        HttpRetryConfig retryConfig = retryConfig();
        int maxRetries = retryConfig.isEnabled() ? retryConfig.getMaxRetries() : 0;
        int retryCount = 0;
        RuntimeException lastError = null;
        while (retryCount <= maxRetries) {
            try {
                Map<String, Object> response = sendOnce(url, method, headers, queryParams, bodyConfig);
                int statusCode = (int) response.get("status_code");
                if (shouldRetry(statusCode, retryConfig) && retryCount < maxRetries) {
                    sleepBeforeRetry(retryCount, retryConfig);
                    retryCount += 1;
                    continue;
                }
                return response;
            } catch (RuntimeException exception) {
                lastError = exception;
                if (!retryConfig.isEnabled() || retryCount >= maxRetries) {
                    break;
                }
                sleepBeforeRetry(retryCount, retryConfig);
                retryCount += 1;
            }
        }
        throw ErrorHelper.buildError(
                StatusCode.COMPONENT_TOOL_EXECUTION_ERROR,
                "error_msg",
                "HTTP request failed: " + (lastError == null ? "unknown error" : lastError.getMessage())
        );
    }

    /**
     * Process and format the HTTP response according to response handling config.
     *
     * @param response raw response map
     * @return component output map
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> processResponse(Map<String, Object> response) {
        int statusCode = ((Number) response.get("status_code")).intValue();
        Object content = response.get("content");
        Map<String, Object> headers = response.get("headers") instanceof Map<?, ?> rawHeaders
                ? normalizeMap(rawHeaders)
                : new LinkedHashMap<>();

        HttpResponseHandlingConfig handling = responseHandlingConfig();
        List<Integer> successCodes = handling.getResponseCodeSuccessCodes() == null
                ? DEFAULT_SUCCESS_CODES
                : handling.getResponseCodeSuccessCodes();
        List<Integer> failureCodes = handling.getResponseCodeFailureCodes() == null
                ? List.of()
                : handling.getResponseCodeFailureCodes();

        boolean isSuccess = successCodes.contains(statusCode)
                || (failureCodes.isEmpty() && statusCode >= 200 && statusCode < 300);
        boolean isFailure = failureCodes.contains(statusCode) || statusCode >= 400;

        Object parsedContent = parseContent(content, headers, handling.getResponseFormat());
        if (handling.getResponseDataProperty() != null
                && parsedContent instanceof Map<?, ?> parsedMap
                && parsedMap.containsKey(handling.getResponseDataProperty())) {
            parsedContent = parsedMap.get(handling.getResponseDataProperty());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("statusCode", statusCode);
        result.put("headers", headers);
        result.put("body", parsedContent);
        result.put("url", response.getOrDefault("url", ""));
        result.put("ok", isSuccess && !isFailure);

        String responseMode = handling.getResponseMode() == null ? "full" : handling.getResponseMode();
        if ("on-success".equals(responseMode) && !isSuccess) {
            return new LinkedHashMap<>();
        }
        if ("on-error".equals(responseMode) && isSuccess) {
            return new LinkedHashMap<>();
        }
        return result;
    }

    private Map<String, Object> sendOnce(String url,
                                         String method,
                                         Map<String, String> headers,
                                         Map<String, Object> queryParams,
                                         HttpRequestBodyConfig bodyConfig) {
        try {
            Duration requestTimeout = resolveRequestTimeout();
            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(requestTimeout)
                    .followRedirects(advancedOptions().isFollowRedirect()
                            ? HttpClient.Redirect.NORMAL
                            : HttpClient.Redirect.NEVER)
                    .build();

            URI uri = URI.create(appendQuery(url, queryParams));
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(requestTimeout);

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getValue() != null) {
                    requestBuilder.header(entry.getKey(), entry.getValue());
                }
            }

            BodyPayload bodyPayload = prepareRequestBody(bodyConfig);
            if (bodyPayload.contentType() != null && !headersContains(headers, "Content-Type")) {
                requestBuilder.header("Content-Type", bodyPayload.contentType());
            }
            if (List.of("POST", "PUT", "PATCH").contains(method)) {
                requestBuilder.method(method, HttpRequest.BodyPublishers.ofByteArray(bodyPayload.body()));
            } else {
                requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<byte[]> httpResponse = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
            byte[] content = httpResponse.body() == null ? new byte[0] : httpResponse.body();
            int maxResponseBytes = requestParams.getMaxResponseByteSize() > 0
                    ? requestParams.getMaxResponseByteSize()
                    : 10 * 1024 * 1024;
            if (content.length > maxResponseBytes) {
                throw ErrorHelper.buildError(
                        StatusCode.COMPONENT_TOOL_EXECUTION_ERROR,
                        "error_msg",
                        "Response size (" + content.length + " bytes) exceeds maximum allowed size ("
                                + maxResponseBytes + " bytes)"
                );
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status_code", httpResponse.statusCode());
            response.put("headers", new LinkedHashMap<>(httpResponse.headers().map()));
            response.put("content", decodeResponseContent(content, httpResponse.headers().firstValue("content-type").orElse("")));
            response.put("url", httpResponse.uri().toString());
            response.put("reason", "");
            return response;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException(exception);
        }
    }

    private void applyAuthentication(Map<String, String> headers,
                                     Map<String, Object> queryParams,
                                     HttpRequestBodyConfig bodyConfig,
                                     HttpAuthConfig authConfig) {
        if (authConfig == null || authConfig.getType() == null || authConfig.getType() == HttpAuthType.NONE) {
            return;
        }
        if (authConfig.getType() == HttpAuthType.BASIC
                && authConfig.getUsername() != null
                && authConfig.getPassword() != null) {
            String credentials = authConfig.getUsername() + ":" + authConfig.getPassword();
            headers.put("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
            return;
        }
        if (authConfig.getType() == HttpAuthType.BEARER && authConfig.getToken() != null) {
            headers.put("Authorization", "Bearer " + authConfig.getToken());
            return;
        }
        if (authConfig.getType() == HttpAuthType.API_KEY && authConfig.getApiKey() != null) {
            String location = authConfig.getInLocation() == null ? "header" : authConfig.getInLocation();
            String name = authConfig.getName() == null ? "Authorization" : authConfig.getName();
            if ("query".equals(location)) {
                queryParams.put(name, authConfig.getApiKey());
            } else if ("body".equals(location)) {
                addApiKeyToBody(bodyConfig, name, authConfig.getApiKey());
            } else {
                headers.put(name, authConfig.getApiKey());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void addApiKeyToBody(HttpRequestBodyConfig bodyConfig, String name, String apiKey) {
        if (bodyConfig == null) {
            return;
        }
        if (bodyConfig.getJsonData() instanceof Map<?, ?> rawMap) {
            Map<String, Object> jsonData = new LinkedHashMap<>((Map<String, Object>) rawMap);
            jsonData.put(name, apiKey);
            bodyConfig.setJsonData(jsonData);
            return;
        }
        if (bodyConfig.getFormData() != null) {
            Map<String, Object> formData = new LinkedHashMap<>(bodyConfig.getFormData());
            formData.put(name, apiKey);
            bodyConfig.setFormData(formData);
        }
    }

    private BodyPayload prepareRequestBody(HttpRequestBodyConfig bodyConfig) {
        if (bodyConfig == null) {
            return new BodyPayload(new byte[0], null);
        }
        HttpContentType contentType = bodyConfig.getContentType() == null
                ? HttpContentType.JSON
                : bodyConfig.getContentType();
        if (contentType == HttpContentType.JSON && bodyConfig.getJsonData() != null) {
            return new BodyPayload(toJsonBytes(bodyConfig.getJsonData()), "application/json");
        }
        if (contentType == HttpContentType.FORM && bodyConfig.getFormData() != null) {
            return new BodyPayload(urlEncode(bodyConfig.getFormData()).getBytes(StandardCharsets.UTF_8),
                    "application/x-www-form-urlencoded");
        }
        if (contentType == HttpContentType.MULTIPART_FORM && bodyConfig.getMultipartForm() != null) {
            return multipartBody(bodyConfig.getMultipartForm());
        }
        if (contentType == HttpContentType.TEXT && bodyConfig.getTextData() != null) {
            return new BodyPayload(bodyConfig.getTextData().getBytes(StandardCharsets.UTF_8), "text/plain");
        }
        if (contentType == HttpContentType.BINARY && bodyConfig.getBinaryData() != null) {
            return new BodyPayload(Base64.getDecoder().decode(bodyConfig.getBinaryData()), "application/octet-stream");
        }
        return new BodyPayload(new byte[0], null);
    }

    private Object parseContent(Object content, Map<String, Object> headers, HttpResponseFormat configuredFormat) {
        HttpResponseFormat responseFormat = configuredFormat == null ? HttpResponseFormat.AUTODETECT : configuredFormat;
        if (responseFormat == HttpResponseFormat.AUTODETECT) {
            String contentType = headerValue(headers, "content-type").toLowerCase(Locale.ROOT);
            if (contentType.contains("application/json")) {
                responseFormat = HttpResponseFormat.JSON;
            } else if (contentType.contains("text/")) {
                responseFormat = HttpResponseFormat.TEXT;
            } else {
                responseFormat = HttpResponseFormat.BINARY;
            }
        }
        if (responseFormat == HttpResponseFormat.JSON) {
            try {
                String contentText = content instanceof byte[] bytes
                        ? new String(bytes, StandardCharsets.UTF_8)
                        : String.valueOf(content);
                return JSON.readValue(contentText, new TypeReference<Map<String, Object>>() {
                });
            } catch (Exception ignored) {
                return content;
            }
        }
        if (responseFormat == HttpResponseFormat.TEXT) {
            return content instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : content;
        }
        return content;
    }

    private Object decodeResponseContent(byte[] content, String contentType) {
        String lowerContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        boolean asText = TEXT_CONTENT_TYPES.stream().anyMatch(lowerContentType::contains);
        return asText ? new String(content, StandardCharsets.UTF_8) : content;
    }

    private HttpRequestBodyConfig normalizeBodyConfig(Object rawBody, Map<String, Object> inputs) {
        if (rawBody == null) {
            return null;
        }
        if (rawBody instanceof HttpRequestBodyConfig bodyConfig) {
            return copyBodyConfig(bodyConfig, inputs);
        }
        if (!(rawBody instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return null;
        }
        Map<String, Object> bodyMap = normalizeMap(rawMap);
        HttpRequestBodyConfig.HttpRequestBodyConfigBuilder builder = HttpRequestBodyConfig.builder();
        Object contentType = firstPresent(bodyMap, "content_type", "contentType");
        if (contentType instanceof HttpContentType httpContentType) {
            builder.contentType(httpContentType);
        } else if (contentType != null) {
            builder.contentType(HttpContentType.valueOf(String.valueOf(contentType).toUpperCase(Locale.ROOT)));
        }
        Object jsonData = firstPresent(bodyMap, "json_data", "jsonData");
        Object textData = firstPresent(bodyMap, "text_data", "textData");
        Object binaryData = firstPresent(bodyMap, "binary_data", "binaryData");
        Object formData = firstPresent(bodyMap, "form_data", "formData");
        Object multipartForm = firstPresent(bodyMap, "multipart_form", "multipartForm");
        builder.jsonData(resolveJsonData(jsonData, inputs));
        if (textData != null) {
            builder.textData(resolvePlaceholders(String.valueOf(textData), inputs));
        }
        if (binaryData != null) {
            builder.binaryData(String.valueOf(binaryData));
        }
        if (formData instanceof Map<?, ?> map) {
            builder.formData(normalizeMap(map));
        }
        if (multipartForm instanceof Map<?, ?> map) {
            builder.multipartForm(normalizeMap(map));
        }
        return builder.build();
    }

    private HttpRequestBodyConfig copyBodyConfig(HttpRequestBodyConfig source, Map<String, Object> inputs) {
        return HttpRequestBodyConfig.builder()
                .contentType(source.getContentType())
                .jsonData(resolveJsonData(source.getJsonData(), inputs))
                .formData(source.getFormData() == null ? null : new LinkedHashMap<>(source.getFormData()))
                .multipartForm(source.getMultipartForm() == null ? null : new LinkedHashMap<>(source.getMultipartForm()))
                .binaryData(source.getBinaryData())
                .textData(source.getTextData() == null ? null : resolvePlaceholders(source.getTextData(), inputs))
                .build();
    }

    private Object resolveJsonData(Object jsonData, Map<String, Object> inputs) {
        if (!(jsonData instanceof String text)) {
            return jsonData;
        }
        String resolved = resolvePlaceholders(text, inputs);
        try {
            return JSON.readValue(resolved, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ignored) {
            return resolved;
        }
    }

    private HttpAuthConfig normalizeAuthConfig(Object rawAuth) {
        if (rawAuth instanceof HttpAuthConfig authConfig) {
            return authConfig;
        }
        if (!(rawAuth instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return null;
        }
        Map<String, Object> authMap = normalizeMap(rawMap);
        HttpAuthConfig.HttpAuthConfigBuilder builder = HttpAuthConfig.builder();
        Object type = authMap.get("type");
        if (type instanceof HttpAuthType authType) {
            builder.type(authType);
        } else if (type != null) {
            builder.type(HttpAuthType.valueOf(String.valueOf(type).toUpperCase(Locale.ROOT)));
        }
        builder.username(asString(authMap.get("username")))
                .password(asString(authMap.get("password")))
                .token(asString(authMap.get("token")))
                .apiKey(asString(firstPresent(authMap, "api_key", "apiKey")))
                .inLocation(asString(firstPresent(authMap, "in_location", "inLocation")))
                .name(asString(authMap.get("name")))
                .accessKey(asString(firstPresent(authMap, "access_key", "accessKey")))
                .secretKey(asString(firstPresent(authMap, "secret_key", "secretKey")))
                .region(asString(authMap.get("region")));
        return builder.build();
    }

    private Map<String, String> headersFromConfig(Object rawHeaders, Map<String, Object> inputs) {
        if (rawHeaders instanceof Map<?, ?> rawMap) {
            return normalizeStringMap(rawMap);
        }
        if (rawHeaders instanceof String headerTemplate) {
            String resolved = resolvePlaceholders(headerTemplate, inputs);
            try {
                Map<String, Object> parsed = JSON.readValue(resolved, new TypeReference<Map<String, Object>>() {
                });
                return normalizeStringMap(parsed);
            } catch (Exception ignored) {
                return new LinkedHashMap<>();
            }
        }
        return new LinkedHashMap<>();
    }

    private Duration resolveRequestTimeout() {
        double timeoutSeconds = requestParams.getTimeout() > 0 ? requestParams.getTimeout() : 60.0;
        return Duration.ofMillis(Math.round(timeoutSeconds * 1000));
    }

    private boolean shouldRetry(int statusCode, HttpRetryConfig retryConfig) {
        if (!retryConfig.isEnabled()) {
            return false;
        }
        List<Integer> retryStatusCodes = retryConfig.getRetryOnStatusCodes() == null
                ? DEFAULT_RETRY_STATUS_CODES
                : retryConfig.getRetryOnStatusCodes();
        return retryStatusCodes.contains(statusCode);
    }

    private void sleepBeforeRetry(int retryCount, HttpRetryConfig retryConfig) {
        double delaySeconds = calculateRetryDelay(retryCount, retryConfig);
        try {
            Thread.sleep(Math.max(0L, Math.round(delaySeconds * 1000)));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(interrupted);
        }
    }

    private double calculateRetryDelay(int retryCount, HttpRetryConfig retryConfig) {
        double baseDelay = retryConfig.getRetryDelay() / 1000.0d;
        String backoffType = retryConfig.getBackoffType() == null ? "exponential" : retryConfig.getBackoffType();
        if ("fixed".equals(backoffType)) {
            return baseDelay;
        }
        if ("linear".equals(backoffType)) {
            return baseDelay * (retryCount + 1);
        }
        if ("exponential".equals(backoffType)) {
            return baseDelay * Math.pow(2, retryCount);
        }
        return baseDelay;
    }

    private String appendQuery(String url, Map<String, Object> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return url;
        }
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + urlEncode(queryParams);
    }

    private String urlEncode(Map<String, ?> values) {
        List<String> pairs = new ArrayList<>();
        values.forEach((key, value) -> pairs.add(URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(value == null ? "" : String.valueOf(value), StandardCharsets.UTF_8)));
        return String.join("&", pairs);
    }

    private BodyPayload multipartBody(Map<String, Object> values) {
        String boundary = "----jiuwen-http-" + System.nanoTime();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        values.forEach((key, value) -> {
            try {
                buffer.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                buffer.write(("Content-Disposition: form-data; name=\"" + key + "\"\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
                buffer.write((value == null ? "" : String.valueOf(value)).getBytes(StandardCharsets.UTF_8));
                buffer.write("\r\n".getBytes(StandardCharsets.UTF_8));
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        });
        try {
            buffer.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
        return new BodyPayload(buffer.toByteArray(), "multipart/form-data; boundary=" + boundary);
    }

    private byte[] toJsonBytes(Object value) {
        try {
            return JSON.writeValueAsBytes(value);
        } catch (Exception exception) {
            return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
        }
    }

    private HttpResponseHandlingConfig responseHandlingConfig() {
        return requestParams.getResponseHandling() == null
                ? HttpResponseHandlingConfig.builder().build()
                : requestParams.getResponseHandling();
    }

    private HttpAdvancedOptionsConfig advancedOptions() {
        return requestParams.getAdvancedOptions() == null
                ? HttpAdvancedOptionsConfig.builder().build()
                : requestParams.getAdvancedOptions();
    }

    private HttpRetryConfig retryConfig() {
        return requestParams.getRetryConfig() == null
                ? HttpRetryConfig.builder().build()
                : requestParams.getRetryConfig();
    }

    private String resolvePlaceholders(String template, Map<String, Object> inputs) {
        if (template == null) {
            return "";
        }
        String resolved = template;
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            resolved = resolved.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return resolved;
    }

    private static boolean headersContains(Map<String, String> headers, String name) {
        return headers.keySet().stream().anyMatch(key -> key.equalsIgnoreCase(name));
    }

    private static String headerValue(Map<String, Object> headers, String name) {
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                Object value = entry.getValue();
                if (value instanceof List<?> list && !list.isEmpty()) {
                    return String.valueOf(list.get(0));
                }
                return value == null ? "" : String.valueOf(value);
            }
        }
        return "";
    }

    private static Object firstPresent(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> rawMap) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> normalized.put(String.valueOf(key), value));
        return normalized;
    }

    private static Map<String, String> normalizeStringMap(Map<?, ?> rawMap) {
        Map<String, String> normalized = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> normalized.put(String.valueOf(key), value == null ? null : String.valueOf(value)));
        return normalized;
    }

    private record BodyPayload(byte[] body, String contentType) {
    }
}
