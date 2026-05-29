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
import java.util.List;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.net.ProxySelector;
import java.util.StringJoiner;

/**
 * RESTful API tool that executes HTTP requests.
 * <p>
 * Mirrors Python's {@code RestfulApi} class. Uses JDK {@link HttpClient} instead of aiohttp.
 */
public class RestfulApi extends Tool {

    private static final String RESTFUL_SSL_VERIFY = "RESTFUL_SSL_VERIFY";
    private static final String RESTFUL_SSL_CERT = "RESTFUL_SSL_CERT";

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
        this.method = card.getMethod().toUpperCase(Locale.ROOT);
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
        if (method == null || !RestfulApiCard.SUPPORTED_METHODS.contains(method.toUpperCase(Locale.ROOT))) {
            throw ErrorHelper.buildError(StatusCode.TOOL_RESTFUL_API_CARD_CONFIG_INVALID,
                    "reason", "unsupported method: " + method + ", only accepts: " + RestfulApiCard.SUPPORTED_METHODS);
        }
        // Validate URL
        String url = card.getUrl();
        if (url == null || url.isBlank()) {
            // Python parity: empty URL is accepted at construction time and fails during invoke.
            return;
        }

        try {
            UrlUtils.checkUrlIsValid(url);
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.TOOL_RESTFUL_API_CARD_CONFIG_INVALID,
                    "reason", "invalid url: " + url);
        }
    }



    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        double finalTimeout = this.timeout;
        try {
            // Check for empty URL before proceeding (Python parity)
            if (this.url == null || this.url.isEmpty()) {
                throw ErrorHelper.buildError(StatusCode.TOOL_RESTFUL_API_EXECUTION_ERROR,
                        "method", "invoke", "reason", "", "card", card.toString());
            }
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
    /**
     * Auto-generated for codecheck compliance.
     */
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        throw ErrorHelper.buildError(StatusCode.TOOL_STREAM_NOT_SUPPORTED, "card", card.toString());
    }

    private Map<String, Object> executeRequest(Map<ApiParamLocation, Map<String, Object>> mapResults,
                                               double timeoutSec,
                                               int maxResponseByteSize,
                                               boolean raiseForStatus) throws Exception {
        String resolvedUrl = resolveUrl(mapResults);
        HttpRequest request = buildHttpRequest(resolvedUrl, mapResults, timeoutSec);
        HttpClient client = buildHttpClient(resolvedUrl, timeoutSec);
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        byte[] content = response.body();
        validateResponse(response, content, maxResponseByteSize, raiseForStatus);
        return formatResponse(response, content);
    }

    private String resolveUrl(Map<ApiParamLocation, Map<String, Object>> mapResults) {
        String resolvedUrl = this.url;
        Map<String, Object> pathParams = mapResults.getOrDefault(ApiParamLocation.PATH, Map.of());
        for (var entry : pathParams.entrySet()) {
            resolvedUrl = resolvedUrl.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        Map<String, Object> queryParams = mapResults.getOrDefault(ApiParamLocation.QUERY, Map.of());
        return appendQueryParams(resolvedUrl, queryParams);
    }

    @SuppressWarnings("unchecked")
    private HttpRequest buildHttpRequest(String resolvedUrl,
                                         Map<ApiParamLocation, Map<String, Object>> mapResults,
                                         double timeoutSec) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(resolvedUrl))
                .timeout(Duration.ofMillis((long) (timeoutSec * 1000)));
        Map<String, Object> headers = mapResults.getOrDefault(ApiParamLocation.HEADER, Map.of());
        for (var entry : headers.entrySet()) {
            requestBuilder.header(entry.getKey(), String.valueOf(entry.getValue()));
        }
        Map<String, Object> bodyParams = mapResults.getOrDefault(ApiParamLocation.BODY, Map.of());
        configureMethodAndBody(requestBuilder, resolvedUrl, bodyParams);
        return requestBuilder.build();
    }

    private void configureMethodAndBody(HttpRequest.Builder requestBuilder, String resolvedUrl,
                                        Map<String, Object> bodyParams) throws Exception {
        switch (method.toUpperCase(Locale.ROOT)) {
            case "GET":
            case "HEAD":
            case "OPTIONS":
                resolvedUrl = appendQueryParams(resolvedUrl, bodyParams);
                requestBuilder.uri(URI.create(resolvedUrl));
                if ("GET".equalsIgnoreCase(method)) {
                    requestBuilder.GET();
                } else {
                    requestBuilder.method(method.toUpperCase(Locale.ROOT), HttpRequest.BodyPublishers.noBody());
                }
                break;
            case "DELETE":
                if (!bodyParams.isEmpty()) {
                    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    String jsonBody = mapper.writeValueAsString(bodyParams);
                    requestBuilder.header("Content-Type", "application/json");
                    requestBuilder.method("DELETE", HttpRequest.BodyPublishers.ofString(jsonBody));
                } else {
                    resolvedUrl = appendQueryParams(resolvedUrl, bodyParams);
                    requestBuilder.uri(URI.create(resolvedUrl));
                    requestBuilder.method("DELETE", HttpRequest.BodyPublishers.noBody());
                }
                break;
            default:
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                String jsonBody = mapper.writeValueAsString(bodyParams);
                requestBuilder.header("Content-Type", "application/json");
                if ("PUT".equalsIgnoreCase(method)) {
                    requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(jsonBody));
                } else if ("PATCH".equalsIgnoreCase(method)) {
                    requestBuilder.method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody));
                } else {
                    requestBuilder.POST(HttpRequest.BodyPublishers.ofString(jsonBody));
                }
                break;
        }
    }

    private void validateResponse(HttpResponse<byte[]> response, byte[] content,
                                  int maxResponseByteSize, boolean raiseForStatus) {
        if (content != null && content.length > maxResponseByteSize) {
            throw ErrorHelper.buildError(StatusCode.TOOL_RESTFUL_API_RESPONSE_SIZE_EXCEED_LIMIT,
                    "method", "invoke",
                    "max_length", String.valueOf(maxResponseByteSize),
                    "actual_length", String.valueOf(content.length),
                    "card", card.toString());
        }
        if (raiseForStatus && (response.statusCode() < 200 || response.statusCode() >= 400)) {
            String reason = reasonPhrase(response.statusCode());
            String responseBody = content != null ? new String(content, java.nio.charset.StandardCharsets.UTF_8) : "";
            throw ErrorHelper.buildError(StatusCode.TOOL_RESTFUL_API_RESPONSE_ERROR,
                    "method", "invoke",
                    "code", String.valueOf(response.statusCode()),
                    "reason", reason + " | body: " + responseBody,
                    "card", card.toString());
        }
    }

    private Map<String, Object> formatResponse(HttpResponse<byte[]> response, byte[] content) {
        Map<String, String> responseHeaders = new LinkedHashMap<>();
        response.headers().map().forEach((k, v) -> {
            if (!v.isEmpty()) {
                responseHeaders.put(k, v.get(0));
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
                .version(HttpClient.Version.HTTP_1_1)
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
        boolean sslVerify = sslConfig[0] instanceof Boolean b ? b : false;
        String sslCertPath = sslConfig[1] instanceof String s ? s : null;
        boolean isExplicitlyEnabled = sslConfig[2] instanceof Boolean b ? b : false;

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
        } else if (isExplicitlyEnabled) {
            // RESTFUL_SSL_VERIFY was explicitly set to a truthy value but no cert provided
            throw ErrorHelper.buildError(StatusCode.COMMON_SSL_CERT_INVALID,
                    "error_msg", "when RESTFUL_SSL_VERIFY=true, must provide ssl cert");
        } else {
            // When sslVerify=true but no explicit switch (default behavior),
            // use the default SSL context which trusts the system's standard CAs.
        }
    }

    @SuppressWarnings("unchecked")
    private static String appendQueryParams(String url, Map<String, Object> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return url;
        }
        StringJoiner joiner = new StringJoiner("&");
        for (var entry : queryParams.entrySet()) {
            String encodedKey = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8);
            Object value = entry.getValue();
            if (value instanceof List<?> listValue) {
                // Array values: repeat the key for each element (e.g. status=available&status=pending)
                for (Object item : listValue) {
                    joiner.add(encodedKey + "=" + URLEncoder.encode(String.valueOf(item), StandardCharsets.UTF_8));
                }
            } else {
                joiner.add(encodedKey + "=" + URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8));
            }
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
