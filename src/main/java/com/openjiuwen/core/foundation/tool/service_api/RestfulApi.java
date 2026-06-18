/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.security.SslUtils;
import com.openjiuwen.core.common.security.UrlUtils;
import com.openjiuwen.core.common.utils.SchemaUtils;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.form_handler.FormHandler;
import com.openjiuwen.core.foundation.tool.form_handler.FormHandlerManager;
import com.openjiuwen.core.foundation.tool.form_handler.ToolFormData;
import com.openjiuwen.core.runner.callback.ToolCallEvents;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * RESTful API tool that executes HTTP requests.
 *
 * <p>Mirrors Python's {@code RestfulApi} in
 * {@code openjiuwen/core/foundation/tool/service_api/restful_api.py}.</p>
 */
public class RestfulApi extends Tool {

    private static final String RESTFUL_SSL_VERIFY = "RESTFUL_SSL_VERIFY";
    private static final String RESTFUL_SSL_CERT = "RESTFUL_SSL_CERT";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final LoggerProtocol LOGGER = Loggers.TOOL;
    private static final Set<String> PARAM_METHODS = Set.of("GET", "HEAD", "OPTIONS", "DELETE");

    private final RestfulApiCard restfulApiCard;
    private final String url;
    private final String method;
    private final double timeout;
    private final int maxResponseByteSize;
    private final ApiParamMapper apiParamMapper;

    public RestfulApi(RestfulApiCard card) {
        super(card);
        this.restfulApiCard = card;
        this.url = card.getUrl();
        this.method = card.getMethod();
        this.timeout = card.getTimeout();
        this.maxResponseByteSize = card.getMaxResponseByteSize();
        this.apiParamMapper = new ApiParamMapper(
                card.getInputParams(),
                card.getQueries(),
                card.getHeaders(),
                card.getPaths()
        );
    }

    /**
     * Helper method for GUI: extract parameters organized by location.
     *
     * @param card RESTful API card
     * @return location names to parameter metadata
     */
    @SuppressWarnings("unchecked")
    public static Map<String, List<Map<String, Object>>> getParametersByLocation(RestfulApiCard card) {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String location : List.of("path", "query", "header", "body", "form")) {
            result.put(location, new ArrayList<>());
        }
        if (card == null || card.getInputParams() == null || card.getInputParams().isEmpty()) {
            return result;
        }

        Object rawProperties = card.getInputParams().get("properties");
        if (!(rawProperties instanceof Map<?, ?> properties)) {
            return result;
        }
        Set<String> requiredFields = requiredFields(card.getInputParams().get("required"));

        for (Map.Entry<?, ?> entry : properties.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> rawParam)) {
                continue;
            }
            String paramName = String.valueOf(entry.getKey());
            Map<String, Object> paramInfo = new LinkedHashMap<>();
            paramInfo.put("name", paramName);
            paramInfo.put("type", rawParam.containsKey("type") ? rawParam.get("type") : "string");
            paramInfo.put("description", rawParam.containsKey("description") ? rawParam.get("description") : "");
            paramInfo.put("required", requiredFields.contains(paramName));
            if (rawParam.containsKey("default")) {
                paramInfo.put("default", rawParam.get("default"));
            }

            String location = String.valueOf(rawParam.containsKey("location") ? rawParam.get("location") : "body")
                    .toLowerCase();
            result.computeIfAbsent(location, ignored -> new ArrayList<>()).add(paramInfo);
        }
        return result;
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        double finalTimeout = timeout;
        try {
            Map<String, Object> safeKwargs = kwargs != null ? kwargs : Map.of();
            Map<String, Object> formattedInputs = inputs != null ? new LinkedHashMap<>(inputs) : new LinkedHashMap<>();
            Map<String, Object> inputParams = restfulApiCard.getInputParams();
            if (inputParams != null && !inputParams.isEmpty()) {
                triggerCallback(ToolCallEvents.TOOL_PARSE_STARTED, parseStartedKwargs(formattedInputs, inputParams));
                formattedInputs = SchemaUtils.formatWithSchema(
                        formattedInputs,
                        inputParams,
                        Boolean.TRUE.equals(safeKwargs.get("skip_none_value")),
                        Boolean.TRUE.equals(safeKwargs.get("skip_inputs_validate"))
                );
                triggerCallback(ToolCallEvents.TOOL_PARSE_FINISHED, parseFinishedKwargs(formattedInputs));
            }

            Map<ApiParamLocation, Map<String, Object>> mapResults =
                    apiParamMapper.map(formattedInputs, ApiParamLocation.BODY);
            finalTimeout = numberOrDefault(safeKwargs.get("timeout"), timeout).doubleValue();
            int maxSize = numberOrDefault(
                    safeKwargs.get("max_response_byte_size"),
                    maxResponseByteSize
            ).intValue();
            boolean raiseForStatus = !Boolean.FALSE.equals(safeKwargs.getOrDefault("raise_for_status", true));
            Map<String, Object> requestArgs = asStringObjectMap(safeKwargs.get("request_args"));
            return executeRequest(mapResults, finalTimeout, maxSize, raiseForStatus, requestArgs);
        } catch (java.net.http.HttpTimeoutException | java.net.ConnectException error) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("method", "invoke");
            params.put("timeout", finalTimeout);
            params.put("card", restfulApiCard);
            throw ErrorHelper.buildError(StatusCode.TOOL_RESTFUL_API_EXECUTION_TIMEOUT, null, null, error, params);
        } catch (BaseError error) {
            throw error;
        } catch (Exception error) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("method", "invoke");
            params.put("reason", error.getMessage());
            params.put("card", restfulApiCard);
            throw ErrorHelper.buildError(StatusCode.TOOL_RESTFUL_API_EXECUTION_ERROR, null, null, error, params);
        }
    }

    @Override
    protected Iterator<Object> streamInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        throw ErrorHelper.buildError(StatusCode.TOOL_STREAM_NOT_SUPPORTED, "card", String.valueOf(getCard()));
    }

    private Map<String, Object> executeRequest(Map<ApiParamLocation, Map<String, Object>> mapResults,
                                               double timeoutSec,
                                               int responseByteLimit,
                                               boolean raiseForStatus,
                                               Map<String, Object> requestArgs) throws Exception {
        RequestPayload payload = buildPayload(mapResults, requestArgs);
        HttpRequest request = payload.requestBuilder()
                .timeout(Duration.ofMillis((long) (timeoutSec * 1000)))
                .build();
        HttpClient client = buildHttpClient(payload.resolvedUrl(), timeoutSec);
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        byte[] content = response.body() != null ? response.body() : new byte[0];
        if (content.length > responseByteLimit) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOL_RESTFUL_API_RESPONSE_SIZE_EXCEED_LIMIT,
                    "method",
                    "invoke",
                    "max_length",
                    String.valueOf(responseByteLimit),
                    "actual_length",
                    String.valueOf(content.length),
                    "card",
                    String.valueOf(getCard())
            );
        }
        if (raiseForStatus && response.statusCode() >= 400) {
            String reason = reasonPhrase(response.statusCode());
            throw ErrorHelper.buildError(
                    StatusCode.TOOL_RESTFUL_API_RESPONSE_ERROR,
                    "method",
                    "invoke",
                    "code",
                    String.valueOf(response.statusCode()),
                    "reason",
                    reason,
                    "card",
                    String.valueOf(getCard())
            );
        }
        return formatResponse(response, content);
    }

    private RequestPayload buildPayload(Map<ApiParamLocation, Map<String, Object>> mapResults,
                                        Map<String, Object> requestArgs) throws Exception {
        String resolvedUrl = applyPathParams(url, mapResults.get(ApiParamLocation.PATH));
        resolvedUrl = appendQueryParams(resolvedUrl, mapResults.get(ApiParamLocation.QUERY));

        Map<String, Object> bodyParams = mapResults.getOrDefault(ApiParamLocation.BODY, Map.of());
        Map<String, Object> formParams = mapResults.getOrDefault(ApiParamLocation.FORM, Map.of());
        Map<String, Object> headers = mergeHeaders(
                mapResults.getOrDefault(ApiParamLocation.HEADER, Map.of()),
                asStringObjectMap(requestArgs.get("headers"))
        );

        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(resolvedUrl));
        if (!formParams.isEmpty()) {
            headers = prepareHeadersForFormData(headers);
            MultipartPayload multipart = toMultipart(processFormData(formParams, bodyParams));
            headers.put("Content-Type", "multipart/form-data; boundary=" + multipart.boundary());
            applyHeaders(builder, headers);
            builder.method(method, HttpRequest.BodyPublishers.ofByteArray(multipart.body()));
            return new RequestPayload(resolvedUrl, builder);
        }

        if (PARAM_METHODS.contains(method)) {
            resolvedUrl = appendQueryParams(resolvedUrl, bodyParams);
            builder.uri(URI.create(resolvedUrl));
            applyHeaders(builder, headers);
            if ("GET".equals(method)) {
                builder.GET();
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            return new RequestPayload(resolvedUrl, builder);
        }

        applyHeaders(builder, headers);
        byte[] jsonBody = OBJECT_MAPPER.writeValueAsBytes(bodyParams);
        if (!containsHeader(headers, "Content-Type")) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, bodyParams.isEmpty()
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(jsonBody));
        return new RequestPayload(resolvedUrl, builder);
    }

    @SuppressWarnings("unchecked")
    ToolFormData processFormData(Map<String, Object> formParams,
                                 Map<String, Object> bodyParams) throws Exception {
        FormHandlerManager formHandlerManager = FormHandlerManager.getInstance();
        ToolFormData finalFormData = new ToolFormData();

        for (Map.Entry<String, Object> entry : formParams.entrySet()) {
            Map<String, Object> paramInfo = entry.getValue() instanceof Map<?, ?> info
                    ? (Map<String, Object>) info
                    : Map.of();
            Object handlerType = paramInfo.get("form_handler_type");
            Object value = paramInfo.get("value");
            FormHandler handler = instantiateHandler(formHandlerManager.getHandler(handlerType));
            finalFormData = awaitForm(handler.handle(finalFormData, Map.of(entry.getKey(), value)));
        }

        for (Map.Entry<String, Object> entry : bodyParams.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            finalFormData.addField(
                    entry.getKey(),
                    OBJECT_MAPPER.writeValueAsString(entry.getValue()),
                    "application/json"
            );
        }
        return finalFormData;
    }

    Map<String, Object> prepareHeadersForFormData(Map<String, Object> headers) {
        Map<String, Object> processedHeaders = new LinkedHashMap<>();
        if (headers == null || headers.isEmpty()) {
            return processedHeaders;
        }
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            if (!"content-type".equalsIgnoreCase(entry.getKey())) {
                processedHeaders.put(entry.getKey(), entry.getValue());
                continue;
            }
            LOGGER.debug("Content-Type header '{}' removed for multipart/form-data request. "
                    + "aiohttp will set the correct Content-Type automatically.", entry.getValue());
        }
        return processedHeaders;
    }

    private Map<String, Object> formatResponse(HttpResponse<byte[]> response, byte[] content) {
        Map<String, String> responseHeaders = new LinkedHashMap<>();
        response.headers().map().forEach((key, values) -> {
            if (!values.isEmpty()) {
                responseHeaders.put(key, values.getFirst());
            }
        });
        int statusCode = response.statusCode();
        try {
            Object parsedResponse = ParserRegistry.getInstance().parse(responseHeaders, content, statusCode);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", statusCode);
            result.put("data", parsedResponse);
            result.put("url", response.uri().toString());
            result.put("headers", responseHeaders);
            result.put("reason", reasonPhrase(statusCode));
            result.put("message", statusCode >= 200 && statusCode < 300 ? "success" : reasonPhrase(statusCode));
            return result;
        } catch (Exception error) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("card", restfulApiCard);
            params.put("reason", error);
            throw ErrorHelper.buildError(StatusCode.TOOL_RESTFUL_API_RESPONSE_PROCESS_ERROR, null, null, error, params);
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
        String proxy = UrlUtils.getGlobalProxyUrl(resolvedUrl);
        LOGGER.info("Proxy enabled for {}: {}", resolvedUrl, proxy != null);
        if (proxy == null || proxy.isBlank()) {
            return;
        }
        try {
            URI proxyUri = URI.create(proxy);
            if (proxyUri.getHost() != null && proxyUri.getPort() > 0) {
                builder.proxy(ProxySelector.of(new InetSocketAddress(proxyUri.getHost(), proxyUri.getPort())));
            }
        } catch (Exception ignored) {
            // Python lets aiohttp handle proxy parsing errors at request time; keep direct client here.
        }
    }

    private void configureSsl(HttpClient.Builder builder, String resolvedUrl) {
        URI resolvedUri = URI.create(resolvedUrl);
        if (!"https".equalsIgnoreCase(resolvedUri.getScheme())) {
            return;
        }
        Object[] sslConfig = SslUtils.getSslConfig(
                RESTFUL_SSL_VERIFY,
                RESTFUL_SSL_CERT,
                List.of("false", "0", "off"),
                true
        );
        boolean sslVerify = Boolean.TRUE.equals(sslConfig[0]);
        String sslCertPath = sslConfig[1] instanceof String text ? text : null;
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

    private static String applyPathParams(String rawUrl, Map<String, Object> pathParams) {
        String resolved = rawUrl;
        if (pathParams == null || pathParams.isEmpty()) {
            return resolved;
        }
        for (Map.Entry<String, Object> entry : pathParams.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return resolved;
    }

    private static String appendQueryParams(String rawUrl, Map<String, Object> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return rawUrl;
        }
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    joiner.add(encodedPair(entry.getKey(), item));
                }
            } else if (value != null && value.getClass().isArray()) {
                int length = java.lang.reflect.Array.getLength(value);
                for (int i = 0; i < length; i++) {
                    joiner.add(encodedPair(entry.getKey(), java.lang.reflect.Array.get(value, i)));
                }
            } else {
                joiner.add(encodedPair(entry.getKey(), value));
            }
        }
        String query = joiner.toString();
        if (query.isEmpty()) {
            return rawUrl;
        }
        return rawUrl + (rawUrl.contains("?") ? "&" : "?") + query;
    }

    private static String encodedPair(String key, Object value) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "="
                + URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8);
    }

    private static FormHandler instantiateHandler(Object handlerObject) {
        if (handlerObject instanceof FormHandler handler) {
            return handler;
        }
        if (handlerObject instanceof Class<?> handlerClass
                && FormHandler.class.isAssignableFrom(handlerClass)) {
            try {
                return (FormHandler) handlerClass.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException error) {
                throw new IllegalArgumentException("failed to instantiate form handler: " + handlerClass, error);
            }
        }
        throw new IllegalArgumentException("invalid form handler: " + handlerObject);
    }

    private static ToolFormData awaitForm(java.util.concurrent.CompletionStage<ToolFormData> stage) throws Exception {
        try {
            return stage.toCompletableFuture().get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (ExecutionException | CompletionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(cause != null ? cause : error);
        }
    }

    private static MultipartPayload toMultipart(ToolFormData formData) {
        String boundary = "----openjiuwen-" + UUID.randomUUID();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Set<String> emitted = new HashSet<>();
        for (String name : formData.names()) {
            if (!emitted.add(name)) {
                continue;
            }
            List<String> values = formData.values(name);
            List<String> contentTypes = formData.contentTypes(name);
            for (int i = 0; i < values.size(); i++) {
                writeAscii(out, "--" + boundary + "\r\n");
                writeAscii(out, "Content-Disposition: form-data; name=\"" + name + "\"\r\n");
                if (i < contentTypes.size()) {
                    writeAscii(out, "Content-Type: " + contentTypes.get(i) + "\r\n");
                }
                writeAscii(out, "\r\n");
                writeUtf8(out, values.get(i));
                writeAscii(out, "\r\n");
            }
        }
        writeAscii(out, "--" + boundary + "--\r\n");
        return new MultipartPayload(boundary, out.toByteArray());
    }

    private static void writeAscii(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static void writeUtf8(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void applyHeaders(HttpRequest.Builder builder, Map<String, Object> headers) {
        headers.forEach((key, value) -> builder.header(key, String.valueOf(value)));
    }

    private static boolean containsHeader(Map<String, Object> headers, String headerName) {
        return headers.keySet().stream().anyMatch(key -> key.equalsIgnoreCase(headerName));
    }

    private static Map<String, Object> prepareStartedKwargsBase(RestfulApiCard card) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tool_name", card.getName());
        values.put("tool_id", card.getId());
        return values;
    }

    private Map<String, Object> parseStartedKwargs(Map<String, Object> inputs, Map<String, Object> inputParams) {
        Map<String, Object> values = prepareStartedKwargsBase(restfulApiCard);
        values.put("raw_inputs", inputs);
        values.put("schema", inputParams);
        return values;
    }

    private Map<String, Object> parseFinishedKwargs(Map<String, Object> inputs) {
        Map<String, Object> values = prepareStartedKwargsBase(restfulApiCard);
        values.put("formatted_inputs", inputs);
        return values;
    }

    private static Set<String> requiredFields(Object rawRequired) {
        Set<String> required = new HashSet<>();
        if (rawRequired instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                required.add(String.valueOf(item));
            }
        } else if (rawRequired instanceof String[] array) {
            required.addAll(List.of(array));
        }
        return required;
    }

    private static Number numberOrDefault(Object value, Number defaultValue) {
        return value instanceof Number number ? number : defaultValue;
    }

    private static Map<String, Object> mergeHeaders(Map<String, Object> primary, Map<String, Object> secondary) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (primary != null) {
            merged.putAll(primary);
        }
        if (secondary != null) {
            merged.putAll(secondary);
        }
        return merged;
    }

    private static Map<String, Object> asStringObjectMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
        }
        return result;
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

    /**
     * Mirrors Python's request argument assembly inside {@code RestfulApi._async_request} in
     * {@code openjiuwen/core/foundation/tool/service_api/restful_api.py}.
     */
    private record RequestPayload(String resolvedUrl, HttpRequest.Builder requestBuilder) {
    }

    /**
     * Mirrors Python's {@code aiohttp.FormData} handoff created by {@code RestfulApi._process_form_data} in
     * {@code openjiuwen/core/foundation/tool/service_api/restful_api.py}.
     */
    private record MultipartPayload(String boundary, byte[] body) {
    }
}
