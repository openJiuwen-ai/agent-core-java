/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Compatibility facade for retrieval embedding configuration.
 * <p>
 * Mirrors Python's {@code EmbeddingConfig} in
 * {@code openjiuwen/core/retrieval/common/config.py}.
 * </p>
 */
public class EmbeddingConfig extends com.openjiuwen.core.foundation.store.EmbeddingConfig {

    @JsonProperty("verify_ssl")
    @JsonAlias("verifySsl")
    private boolean verifySsl = true;

    @JsonProperty("ssl_cert")
    @JsonAlias("sslCert")
    private String sslCert;

    public EmbeddingConfig() {
        super();
    }

    public EmbeddingConfig(String modelName, String baseUrl) {
        this(modelName, baseUrl, null);
    }

    public EmbeddingConfig(String modelName, String baseUrl, String apiKey) {
        super(modelName, baseUrl, apiKey);
    }

    public boolean isVerifySsl() {
        return verifySsl;
    }

    public void setVerifySsl(boolean verifySsl) {
        this.verifySsl = verifySsl;
    }

    public String getSslCert() {
        return sslCert;
    }

    public void setSslCert(String sslCert) {
        this.sslCert = sslCert;
    }
}
