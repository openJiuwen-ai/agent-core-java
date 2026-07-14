/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.tool.ToolCard;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RESTful API tool card with HTTP method validation.
 *
 * <p>Mirrors Python's {@code RestfulApiCard} in
 * {@code openjiuwen/core/foundation/tool/service_api/restful_api.py}.</p>
 */
public class RestfulApiCard extends ToolCard {

    public static final Set<String> SUPPORTED_METHODS = Set.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

    private static final LoggerProtocol LOGGER = Loggers.TOOL;
    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{(\\w+)}");
    private static final int DEFAULT_MAX_RESPONSE_BYTE_SIZE = 10 * 1024 * 1024;

    private final String url;
    private final String method;
    private final Map<String, Object> headers;
    private final Map<String, Object> queries;
    private final Map<String, Object> paths;
    private final double timeout;
    private final int maxResponseByteSize;

    public RestfulApiCard(String id,
                          String name,
                          String description,
                          Map<String, Object> inputParams,
                          Map<String, Object> properties,
                          String url,
                          String method,
                          Map<String, Object> headers,
                          Map<String, Object> queries,
                          Map<String, Object> paths,
                          double timeout,
                          int maxResponseByteSize) {
        super(id != null ? id : UUID.randomUUID().toString().replace("-", ""), name, description, inputParams,
                properties);
        this.url = validateUrl(url);
        this.method = validateMethod(method);
        this.headers = copyMap(headers);
        this.queries = copyMap(queries);
        this.paths = copyMap(paths);
        this.timeout = validateTimeout(timeout);
        this.maxResponseByteSize = maxResponseByteSize;
        validatePathParameters(this.url, getInputParams());
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getUrl() {
        return url;
    }

    public String getMethod() {
        return method;
    }

    public Map<String, Object> getHeaders() {
        return new LinkedHashMap<>(headers);
    }

    public Map<String, Object> getQueries() {
        return new LinkedHashMap<>(queries);
    }

    public Map<String, Object> getPaths() {
        return new LinkedHashMap<>(paths);
    }

    public double getTimeout() {
        return timeout;
    }

    public int getMaxResponseByteSize() {
        return maxResponseByteSize;
    }

    public int getMax_response_byte_size() {
        return getMaxResponseByteSize();
    }

    private static String validateMethod(String rawMethod) {
        String normalized = rawMethod == null ? "POST" : rawMethod.toUpperCase();
        if (!SUPPORTED_METHODS.contains(normalized)) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOL_RESTFUL_API_CARD_CONFIG_INVALID,
                    "reason",
                    "support invalid method, method=" + rawMethod + ", only accepts: " + SUPPORTED_METHODS + "."
            );
        }
        return normalized;
    }

    private static String validateUrl(String rawUrl) {
        try {
            String substitutedUrl = rawUrl != null ? PATH_PARAM_PATTERN.matcher(rawUrl).replaceAll("placeholder") : null;
            if (hasUnsupportedSchemeWithHost(substitutedUrl)) {
                return rawUrl;
            }
            validateHttpUrlSyntax(substitutedUrl);
            return rawUrl;
        } catch (Exception error) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOL_RESTFUL_API_CARD_CONFIG_INVALID,
                    null,
                    null,
                    error,
                    Map.of("reason", "support invalid url, url=" + rawUrl + ".")
            );
        }
    }

    private static void validateHttpUrlSyntax(String url) throws URISyntaxException {
        if (url == null || url.isBlank()) {
            throw new URISyntaxException(String.valueOf(url), "url is empty");
        }
        URI parsedUrl = new URI(url);
        String scheme = parsedUrl.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new URISyntaxException(url, "illegal url protocol");
        }
        if (parsedUrl.getHost() == null || parsedUrl.getHost().isBlank()) {
            throw new URISyntaxException(url, "host is required");
        }
    }

    private static boolean hasUnsupportedSchemeWithHost(String url) throws URISyntaxException {
        if (url == null || url.isBlank()) {
            return false;
        }
        URI parsedUrl = new URI(url);
        String scheme = parsedUrl.getScheme();
        if (scheme == null || scheme.isBlank()) {
            return false;
        }
        return !"http".equalsIgnoreCase(scheme)
                && !"https".equalsIgnoreCase(scheme)
                && parsedUrl.getHost() != null;
    }

    @SuppressWarnings("unchecked")
    private static void validatePathParameters(String url, Map<String, Object> inputParams) {
        Set<String> urlPathParams = Set.copyOf(extractPathParamNames(url));
        if (urlPathParams.isEmpty()) {
            return;
        }
        if (inputParams == null || inputParams.isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOL_RESTFUL_API_CARD_CONFIG_INVALID,
                    "reason",
                    "URL contains path parameters " + urlPathParams
                            + " but input_params schema is not defined. You must define input_params with "
                            + "'location': 'path' for each path parameter."
            );
        }

        Object rawProperties = inputParams.get("properties");
        Map<String, Object> properties = rawProperties instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        Set<String> schemaPathParams = properties.entrySet().stream()
                .filter(entry -> entry.getValue() instanceof Map<?, ?>)
                .filter(entry -> "path".equals(((Map<?, ?>) entry.getValue()).get("location")))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());

        List<String> missing = urlPathParams.stream()
                .filter(param -> !schemaPathParams.contains(param))
                .toList();
        if (!missing.isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOL_RESTFUL_API_CARD_CONFIG_INVALID,
                    "reason",
                    "URL contains path parameters " + missing
                            + " that are not defined in input_params schema with 'location': 'path'."
            );
        }

        List<String> extra = schemaPathParams.stream()
                .filter(param -> !urlPathParams.contains(param))
                .toList();
        if (!extra.isEmpty()) {
            LOGGER.warning("Schema defines path parameters {} that are not used in URL {}", extra, url);
        }
    }

    private static List<String> extractPathParamNames(String url) {
        List<String> result = new ArrayList<>();
        Matcher matcher = PATH_PARAM_PATTERN.matcher(url != null ? url : "");
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private static double validateTimeout(double timeout) {
        if (timeout <= 0.0d || timeout > 300.0d) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOL_RESTFUL_API_CARD_CONFIG_INVALID,
                    "reason",
                    "timeout must be greater than 0.0 and at most 300.0, timeout=" + timeout + "."
            );
        }
        return timeout;
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    /**
     * Builder for RESTful API tool cards.
     *
     * <p>Mirrors Python's pydantic construction for {@code RestfulApiCard} in
     * {@code openjiuwen/core/foundation/tool/service_api/restful_api.py}.</p>
     */
    public static class Builder extends ToolCard.Builder {
        private String id;
        private String name = "";
        private String description = "";
        private Map<String, Object> inputParams;
        private Map<String, Object> properties;
        private String url;
        private String method = "POST";
        private Map<String, Object> headers;
        private Map<String, Object> queries;
        private Map<String, Object> paths;
        private double timeout = 60.0d;
        private int maxResponseByteSize = DEFAULT_MAX_RESPONSE_BYTE_SIZE;

        protected Builder() {
            super();
        }

        @Override
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        @Override
        public Builder name(String name) {
            this.name = name != null ? name : "";
            return this;
        }

        @Override
        public Builder description(String description) {
            this.description = description != null ? description : "";
            return this;
        }

        @Override
        public Builder inputParams(Map<String, Object> inputParams) {
            this.inputParams = inputParams;
            return this;
        }

        @Override
        public Builder properties(Map<String, Object> properties) {
            this.properties = properties;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder headers(Map<String, Object> headers) {
            this.headers = headers;
            return this;
        }

        public Builder queries(Map<String, Object> queries) {
            this.queries = queries;
            return this;
        }

        public Builder paths(Map<String, Object> paths) {
            this.paths = paths;
            return this;
        }

        public Builder timeout(double timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxResponseByteSize(int maxResponseByteSize) {
            this.maxResponseByteSize = maxResponseByteSize;
            return this;
        }

        public Builder max_response_byte_size(int maxResponseByteSize) {
            return maxResponseByteSize(maxResponseByteSize);
        }

        @Override
        public RestfulApiCard build() {
            return new RestfulApiCard(
                    id,
                    name,
                    description,
                    inputParams,
                    properties,
                    url,
                    method,
                    headers,
                    queries,
                    paths,
                    timeout,
                    maxResponseByteSize
            );
        }
    }
}
