/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api;

import com.openjiuwen.core.foundation.tool.ToolCard;

import java.util.Map;
import java.util.Set;

/**
 * RESTful API tool card with HTTP method and URL configuration.
 * <p>
 * Mirrors Python's {@code RestfulApiCard}.
 * 
 * @since 0.1.7
 */
public class RestfulApiCard extends ToolCard {
    /**
     * SUPPORTED_METHODS.
     * 
     * @since 0.1.7
     */
    public static final Set<String> SUPPORTED_METHODS =
        Set.of("POST", "GET", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");

    /** Restful API URL, e.g. /api/v1/users. */
    private String url;

    /** HTTP method (POST or GET). */
    private String method = "POST";

    /**
     * Default request headers.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> headers = Map.of();

    /**
     * Default query parameters.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> queries = Map.of();

    /**
     * Path parameters for URL placeholders.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> paths = Map.of();

    /** Request timeout in seconds. */
    private double timeout = 60.0;

    /** Maximum response size in bytes (default 10 MB). */
    private int maxResponseByteSize = 10 * 1024 * 1024;

    /**
     * RestfulApiCard.
     * 
     * @param url url
     * @since 0.1.7
     */
    public RestfulApiCard(String url) {
        this.url = url;
    }

    /**
     * RestfulApiCard.
     * 
     * @param url url
     * @param method method
     * @param headers headers
     * @param queries queries
     * @param paths paths
     * @param timeout timeout
     * @param maxResponseByteSize maxResponseByteSize
     * @since 0.1.7
     */
    private RestfulApiCard(String url, String method, Map<String, Object> headers, Map<String, Object> queries,
            Map<String, Object> paths, double timeout, int maxResponseByteSize) {
        this.url = url;
        if (method != null && !method.isBlank()) {
            this.method = method;
        }
        if (headers != null) {
            this.headers = headers;
        }
        if (queries != null) {
            this.queries = queries;
        }
        if (paths != null) {
            this.paths = paths;
        }
        this.timeout = timeout;
        this.maxResponseByteSize = maxResponseByteSize;
    }

    /**
     * getUrl.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getUrl() {
        return url;
    }

    /**
     * getMethod.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getMethod() {
        return method;
    }

    /**
     * getHeaders.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getHeaders() {
        return headers;
    }

    /**
     * getQueries.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getQueries() {
        return queries;
    }

    /**
     * getPaths.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getPaths() {
        return paths;
    }

    /**
     * getTimeout.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getTimeout() {
        return timeout;
    }

    /**
     * getMaxResponseByteSize.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getMaxResponseByteSize() {
        return maxResponseByteSize;
    }

    /**
     * builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
     */
    public static class Builder extends ToolCard.Builder {
        private String url;
        private String method = "POST";

        /**
         * Map.of.
         * 
         * @since 0.1.7
         */
        private Map<String, Object> headers = Map.of();

        /**
         * Map.of.
         * 
         * @since 0.1.7
         */
        private Map<String, Object> queries = Map.of();

        /**
         * Map.of.
         * 
         * @since 0.1.7
         */
        private Map<String, Object> paths = Map.of();
        private double timeout = 60.0;
        private int maxResponseByteSize = 10 * 1024 * 1024;

        /**
         * id.
         * 
         * @param id id
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder id(String id) {
            super.id(id);
            return this;
        }

        /**
         * name.
         * 
         * @param name name
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder name(String name) {
            super.name(name);
            return this;
        }

        /**
         * description.
         * 
         * @param description description
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder description(String description) {
            super.description(description);
            return this;
        }

        /**
         * inputParams.
         * 
         * @param inputParams inputParams
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder inputParams(Map<String, Object> inputParams) {
            super.inputParams(inputParams);
            return this;
        }

        /**
         * properties.
         * 
         * @param properties properties
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder properties(Map<String, Object> properties) {
            super.properties(properties);
            return this;
        }

        /**
         * url.
         * 
         * @param url url
         * @return the result
         * @since 0.1.7
         */
        public Builder url(String url) {
            this.url = url;
            return this;
        }

        /**
         * method.
         * 
         * @param method method
         * @return the result
         * @since 0.1.7
         */
        public Builder method(String method) {
            this.method = method;
            return this;
        }

        /**
         * headers.
         * 
         * @param headers headers
         * @return the result
         * @since 0.1.7
         */
        public Builder headers(Map<String, Object> headers) {
            this.headers = headers;
            return this;
        }

        /**
         * queries.
         * 
         * @param queries queries
         * @return the result
         * @since 0.1.7
         */
        public Builder queries(Map<String, Object> queries) {
            this.queries = queries;
            return this;
        }

        /**
         * paths.
         * 
         * @param paths paths
         * @return the result
         * @since 0.1.7
         */
        public Builder paths(Map<String, Object> paths) {
            this.paths = paths;
            return this;
        }

        /**
         * timeout.
         * 
         * @param timeout timeout
         * @return the result
         * @since 0.1.7
         */
        public Builder timeout(double timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * maxResponseByteSize.
         * 
         * @param maxResponseByteSize maxResponseByteSize
         * @return the result
         * @since 0.1.7
         */
        public Builder maxResponseByteSize(int maxResponseByteSize) {
            this.maxResponseByteSize = maxResponseByteSize;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public RestfulApiCard build() {
            RestfulApiCard card =
                new RestfulApiCard(url, method, headers, queries, paths, timeout, maxResponseByteSize);
            if (id != null) {
                card.setId(id);
            }
            card.setName(name);
            card.setDescription(description);
            card.setInputParams(inputParams);
            card.setProperties(properties);
            return card;
        }
    }
}
