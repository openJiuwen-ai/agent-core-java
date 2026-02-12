// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Remote sandbox gateway connection configuration.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.config.SandboxGatewayConfig
 * 
 * <p>Configuration for sandbox mode system operations, including:
 * <ul>
 *   <li>{@code gatewayUrl} - Remote sandbox gateway service endpoint</li>
 *   <li>{@code params} - Global request parameters</li>
 *   <li>{@code authHeaders} - Authentication HTTP headers</li>
 *   <li>{@code authQueryParams} - Authentication query parameters</li>
 * </ul>
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class SandboxGatewayConfig {

    /**
     * Remote sandbox gateway service endpoint.
     */
    private String gatewayUrl;

    /**
     * Global request parameters.
     */
    private Map<String, Object> params;

    /**
     * Authentication HTTP headers.
     */
    private Map<String, String> authHeaders;

    /**
     * Authentication query parameters.
     */
    private Map<String, String> authQueryParams;

    /**
     * Default constructor with default values.
     */
    public SandboxGatewayConfig() {
        this.gatewayUrl = "";
        this.params = new HashMap<>();
        this.authHeaders = new HashMap<>();
        this.authQueryParams = new HashMap<>();
    }

    /**
     * Constructor with all parameters.
     * 
     * @param gatewayUrl remote sandbox gateway service endpoint
     * @param params global request parameters
     * @param authHeaders authentication HTTP headers
     * @param authQueryParams authentication query parameters
     */
    public SandboxGatewayConfig(String gatewayUrl, Map<String, Object> params,
                                Map<String, String> authHeaders, Map<String, String> authQueryParams) {
        this.gatewayUrl = gatewayUrl != null ? gatewayUrl : "";
        this.params = params != null ? new HashMap<>(params) : new HashMap<>();
        this.authHeaders = authHeaders != null ? new HashMap<>(authHeaders) : new HashMap<>();
        this.authQueryParams = authQueryParams != null ? new HashMap<>(authQueryParams) : new HashMap<>();
    }

    /**
     * Gets the gateway URL.
     * 
     * @return the gateway URL
     */
    public String getGatewayUrl() {
        return gatewayUrl;
    }

    /**
     * Sets the gateway URL.
     * 
     * @param gatewayUrl the gateway URL
     */
    public void setGatewayUrl(String gatewayUrl) {
        this.gatewayUrl = gatewayUrl != null ? gatewayUrl : "";
    }

    /**
     * Gets the global request parameters.
     * 
     * @return the parameters map
     */
    public Map<String, Object> getParams() {
        return params;
    }

    /**
     * Sets the global request parameters.
     * 
     * @param params the parameters map
     */
    public void setParams(Map<String, Object> params) {
        this.params = params != null ? new HashMap<>(params) : new HashMap<>();
    }

    /**
     * Gets the authentication headers.
     * 
     * @return the auth headers map
     */
    public Map<String, String> getAuthHeaders() {
        return authHeaders;
    }

    /**
     * Sets the authentication headers.
     * 
     * @param authHeaders the auth headers map
     */
    public void setAuthHeaders(Map<String, String> authHeaders) {
        this.authHeaders = authHeaders != null ? new HashMap<>(authHeaders) : new HashMap<>();
    }

    /**
     * Gets the authentication query parameters.
     * 
     * @return the auth query params map
     */
    public Map<String, String> getAuthQueryParams() {
        return authQueryParams;
    }

    /**
     * Sets the authentication query parameters.
     * 
     * @param authQueryParams the auth query params map
     */
    public void setAuthQueryParams(Map<String, String> authQueryParams) {
        this.authQueryParams = authQueryParams != null ? new HashMap<>(authQueryParams) : new HashMap<>();
    }

    /**
     * Creates a new Builder instance.
     * 
     * @return a new Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for SandboxGatewayConfig.
     */
    public static class Builder {
        private String gatewayUrl = "";
        private Map<String, Object> params = new HashMap<>();
        private Map<String, String> authHeaders = new HashMap<>();
        private Map<String, String> authQueryParams = new HashMap<>();

        /**
         * Sets the gateway URL.
         * 
         * @param gatewayUrl the gateway URL
         * @return this builder
         */
        public Builder gatewayUrl(String gatewayUrl) {
            this.gatewayUrl = gatewayUrl != null ? gatewayUrl : "";
            return this;
        }

        /**
         * Sets the global request parameters.
         * 
         * @param params the parameters map
         * @return this builder
         */
        public Builder params(Map<String, Object> params) {
            this.params = params != null ? new HashMap<>(params) : new HashMap<>();
            return this;
        }

        /**
         * Sets the authentication headers.
         * 
         * @param authHeaders the auth headers map
         * @return this builder
         */
        public Builder authHeaders(Map<String, String> authHeaders) {
            this.authHeaders = authHeaders != null ? new HashMap<>(authHeaders) : new HashMap<>();
            return this;
        }

        /**
         * Sets the authentication query parameters.
         * 
         * @param authQueryParams the auth query params map
         * @return this builder
         */
        public Builder authQueryParams(Map<String, String> authQueryParams) {
            this.authQueryParams = authQueryParams != null ? new HashMap<>(authQueryParams) : new HashMap<>();
            return this;
        }

        /**
         * Builds the SandboxGatewayConfig instance.
         * 
         * @return the built SandboxGatewayConfig
         */
        public SandboxGatewayConfig build() {
            return new SandboxGatewayConfig(gatewayUrl, params, authHeaders, authQueryParams);
        }
    }

    @Override
    public String toString() {
        return "SandboxGatewayConfig{" +
            "gatewayUrl='" + gatewayUrl + '\'' +
            ", params=" + params +
            ", authHeaders=" + authHeaders +
            ", authQueryParams=" + authQueryParams +
            '}';
    }
}

