// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.schema;

import java.util.Objects;
import java.util.UUID;

/**
 * 模型客户端配置类。
 * 对应 Python: agent-core/openjiuwen/core/foundation/llm/schema/config.py - ModelClientConfig
 */
public class ModelClientConfig {
    private final String clientId;
    private final String clientProvider;
    private final String apiKey;
    private final String apiBase;
    private final double timeout;
    private final int maxRetries;
    private final boolean verifySsl;
    private final String sslCert;

    private ModelClientConfig(Builder builder) {
        this.clientId = builder.clientId != null ? builder.clientId : UUID.randomUUID().toString();
        this.clientProvider = builder.clientProvider;
        this.apiKey = builder.apiKey;
        this.apiBase = builder.apiBase;
        this.timeout = builder.timeout;
        this.maxRetries = builder.maxRetries;
        this.verifySsl = builder.verifySsl;
        this.sslCert = builder.sslCert;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientProvider() {
        return clientProvider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiBase() {
        return apiBase;
    }

    public double getTimeout() {
        return timeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public boolean isVerifySsl() {
        return verifySsl;
    }

    public String getSslCert() {
        return sslCert;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModelClientConfig that = (ModelClientConfig) o;
        return Objects.equals(clientId, that.clientId) &&
                Objects.equals(clientProvider, that.clientProvider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId, clientProvider);
    }

    /**
     * Builder类
     */
    public static class Builder {
        private String clientId;
        private String clientProvider;
        private String apiKey;
        private String apiBase;
        private double timeout = 60.0;
        private int maxRetries = 3;
        private boolean verifySsl = true;
        private String sslCert;

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder clientProvider(String clientProvider) {
            this.clientProvider = clientProvider;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder apiBase(String apiBase) {
            this.apiBase = apiBase;
            return this;
        }

        public Builder timeout(double timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder verifySsl(boolean verifySsl) {
            this.verifySsl = verifySsl;
            return this;
        }

        public Builder sslCert(String sslCert) {
            this.sslCert = sslCert;
            return this;
        }

        public ModelClientConfig build() {
            // 注意: 验证逻辑移至BaseModelClient.validateConfig()
            // 以便与Python实现保持一致（在运行时验证，抛出JiuWenBaseException）
            return new ModelClientConfig(this);
        }
    }
}

