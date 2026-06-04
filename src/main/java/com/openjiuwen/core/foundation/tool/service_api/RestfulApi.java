/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.security.SslUtils;
import com.openjiuwen.core.common.security.UrlUtils;
import com.openjiuwen.core.common.utils.SchemaUtils;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.service_api.parser.ParserRegistry;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.net.InetSocketAddress;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.net.ProxySelector;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RESTful API tool that executes HTTP requests.
 * <p>
 * Mirrors Python's {@code RestfulApi} class. Uses JDK {@link HttpClient} instead of aiohttp.
 */
public class RestfulApi extends Tool {

    private static final String RESTFUL_SSL_VERIFY = "RESTFUL_SSL_VERIFY";
    private static final String RESTFUL_SSL_CERT = "RESTFUL_SSL_CERT";
    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{([^/{}]+)}");

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
        validateCard(card);
        this.url = card.getUrl();
        this.method = card.getMethod().toUpperCase();
        this.timeout = card.getTimeout();
        this.maxResponseByteSize = card.getMaxResponseByteSize();
        this.apiParamMapper = new ApiParamMapper(
                card.getInputParams(),
                card.getQueries(),
                card.getHeaders(),
                card.getPaths());
    }

    /**
     * Validate RestfulApiCard URL and method.
     * Mirrors Python's field_validator('method') and field_validator('url').
     */
    private static void validateCard(RestfulApiCard card) {
        // Validate method
        String method = card.getMethod();
        if (method == null || !RestfulApiCard.SUPPORTED_METHODS.contains(method.toUpperCase())) {
            throw ErrorHelper.buildError(StatusCode.TOOL_RESTFUL_API_CARD_CONFIG_INVALID,
                    "reason", "unsupported method: " + method + ", only accepts: " + RestfulApiCard.SUPPORTED_METHODS);
        }
        // Validate URL
        String url = card.getUrl();
        if (url == null || url.isBlank()) {
            // Python parity: empty URL is accepted at construction time and fails during invoke.
            return;
        }
        validatePathParameters(card);

        try {
            UrlUtils.checkUrlIsValid(url);
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.TOOL_RESTFUL_API_CARD_CONFIG_INVALID,
                    "reason", "invalid url: " + url);
        }
    }

    @SuppressWarnings("unchecked")
    private static void validatePathParameters(RestfulApiCard card) {
        List<String> pathParams = extractPathParamNames(card.getUrl());
        if (pathParams.isEmpty()) {
            return;
        }
        Map<String, Object> inputParams = card.getInputParams();
        Object rawProperties = inputParams == null ? null : inputParams.get("properties");
        if (!(rawProperties instanceof Map<?, ?> rawMap)) {
            throw ErrorHelper.buildError(StatusCode.TOOL_RESTFUL_API_CARD_CONFIG_INVALID,
                    "reason", "url path parameters require input_params properties marked with location=path");
        }
        Map<String, Object> properties = (Map<String, Object>) rawMap;
        for (String name : pathParams) {
            Object rawProp = properties.get(name);
            if (!(rawProp instanceof Map<?, ?> prop)) {
                throw ErrorHelper.buildError(StatusCode.TOOL_RESTFUL_API_CARD_CONFIG_INVALID,
                        "reason", "missing input_params definition for path parameter: " + name);
            }
            Object location = prop.get("location");
            if (!"path".equalsIgnoreCase(String.valueOf(location))) {
                throw ErrorHelper.buildError(StatusCode.TOOL_RESTFUL_API_CARD_CONFIG_INVALID,
                        "reason", "path parameter must be marked with location=path: " + name);
            }
        }
    }

    private static List<String> extractPathParamNames(String url) {
        List<String> result = new ArrayList<>();
        if (url == null) {
            return result;
        }
        Matcher matcher = PATH_PARAM_PATTERN.matcher(url);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    /**
     * Group input parameters by API location for UI and validation helpers.
     *
     * @param card RESTful API card
     * @return location name to parameter metadata list
     */
    @SuppressWarnings("unchecked")
    public static Map<String, List<Map<String, Object>>> getParametersByLocation(RestfulApiCard card) {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (ApiParamLocation location : ApiParamLocation.values()) {
            grouped.put(location.getValue(), new ArrayList<>());
        }

        Map<String, Object> inputParams = card != null ? card.getInputParams() : null;
        Object rawProperties = inputParams != null ? inputParams.get("properties") : null;
        if (!(rawProperties instanceof Map<?, ?> rawMap)) {
            return grouped;
        }

        Set<String> required = requiredNames(inputParams.get("required"));
        Map<String, Object> properties = (Map<String, Object>) rawMap;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> rawProp)) {
                continue;
            }
            Map<String, Object> prop = new LinkedHashMap<>();
            rawProp.forEach((key, value) -> prop.put(String.valueOf(key), value));
            String name = entry.getKey();
            prop.put("name", name);
            prop.put("required", required.contains(name));
            String location = String.valueOf(prop.getOrDefault("location", "body")).toLowerCase();
            grouped.computeIfAbsent(location, ignored -> new ArrayList<>()).add(prop);
        }
        return grouped;
    }

    private static Set<String> requiredNames(Object rawRequired) {
        Set<String> required = new HashSet<>();
        if (rawRequired instanceof String[] array) {
            required.addAll(List.of(array));
        } else if (rawRequired instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                required.add(String.valueOf(item));
            }
        }
        return required;
    }



    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        double finalTimeout = this.timeout;
        try {
            // Schema validation: format inputs against inputParams if defined
            Map<String, Object> validatedInputs = inputs;
            Map<String, Object> inputParams = card.getInputParams();
            if (inputParams != null && !inputParams.isEmpty()) {
                boolean skipNoneValue = kwargs != null && Boolean.TRUE.equals(kwargs.get("skip_none_value"));
                boolean skipValidate = kwargs != null && Boolean.TRUE.equals(kwargs.get("skip_inputs_validate"));
                validatedInputs = SchemaUtils.formatWithSchema(inputs, inputParams, skipNoneValue, skipValidate);
            }
            Map<ApiParamLocation, Map<String, Object>> mapResults =
                    apiParamMapper.map(validatedInputs, ApiParamLocation.BODY);
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
        resolvedUrl = appendQueryParams(resolvedUrl, queryParams);

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
            resolvedUrl = appendQueryParams(resolvedUrl, bodyParams);
            requestBuilder.uri(URI.create(resolvedUrl));
            requestBuilder.GET();
        } else if ("HEAD".equalsIgnoreCase(method)) {
            requestBuilder.method("HEAD", HttpRequest.BodyPublishers.noBody());
        } else {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String jsonBody = mapper.writeValueAsString(bodyParams);
            if (!headers.containsKey("Content-Type")) {
                requestBuilder.header("Content-Type", "application/json");
            }
            HttpRequest.BodyPublisher publisher = bodyParams.isEmpty()
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(jsonBody);
            requestBuilder.method(method, publisher);
        }

        HttpClient client = buildHttpClient(resolvedUrl, timeoutSec);

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
            String reason = reasonPhrase(response.statusCode());
            throw ErrorHelper.buildError(StatusCode.TOOL_RESTFUL_API_RESPONSE_ERROR,
                    "method", "invoke",
                    "code", String.valueOf(response.statusCode()),
                    "reason", reason,
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
            String reason = reasonPhrase(statusCode);
            result.put("reason", reason);
            if (statusCode >= 200 && statusCode < 300) {
                result.put("message", "success");
            } else {
                result.put("message", reason);
            }
            return result;
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.TOOL_RESTFUL_API_RESPONSE_PROCESS_ERROR,
                    "reason", e.getMessage(), "card", card.toString());
        }
    }

    private HttpClient buildHttpClient(String resolvedUrl, double timeoutSec) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofMillis((long) (timeoutSec * 1000)));

        configureProxy(builder, resolvedUrl);
        configureSsl(builder, resolvedUrl);
        return builder.build();
    }

    private void configureProxy(HttpClient.Builder builder, String resolvedUrl) {
        String proxyUrl = UrlUtils.getGlobalProxyUrl(resolvedUrl);
        if (proxyUrl == null || proxyUrl.isBlank()) {
            return;
        }
        try {
            URI proxyUri = URI.create(proxyUrl);
            int port = proxyUri.getPort();
            if (proxyUri.getHost() == null || port <= 0) {
                return;
            }
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxyUri.getHost(), port)));
        } catch (Exception ignored) {
            // Keep default direct connection when proxy parsing fails.
        }
    }

    private void configureSsl(HttpClient.Builder builder, String resolvedUrl) {
        URI resolvedUri = URI.create(resolvedUrl);
        boolean isHttps = "https".equalsIgnoreCase(resolvedUri.getScheme());
        if (!isHttps) {
            return;
        }

        Object[] sslConfig = SslUtils.getSslConfig(
                RESTFUL_SSL_VERIFY,
                RESTFUL_SSL_CERT,
                List.of("false", "0", "off"),
                true);
        boolean sslVerify = (Boolean) sslConfig[0];
        String sslCertPath = (String) sslConfig[1];

        if (!sslVerify) {
            builder.sslContext(SslUtils.createInsecureSslContext());
            SSLParameters sslParameters = new SSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("");
            builder.sslParameters(sslParameters);
            return;
        }

        if (sslCertPath != null && !sslCertPath.isBlank()) {
            SSLContext sslContext = SslUtils.createStrictSslContext(sslCertPath);
            builder.sslContext(sslContext);
        }
    }

    private static String appendQueryParams(String url, Map<String, Object> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return url;
        }
        StringJoiner joiner = new StringJoiner("&");
        for (var entry : queryParams.entrySet()) {
            joiner.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                    + "=" + URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
        }
        return url + (url.contains("?") ? "&" : "?") + joiner;
    }

    private static String reasonPhrase(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 202 -> "Accepted";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 402 -> "Payment Required";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 408 -> "Request Timeout";
            case 409 -> "Conflict";
            case 413 -> "Payload Too Large";
            case 415 -> "Unsupported Media Type";
            case 422 -> "Unprocessable Entity";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "HTTP " + statusCode;
        };
    }
}
