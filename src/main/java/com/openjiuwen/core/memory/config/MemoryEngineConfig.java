/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.security.CryptUtils;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.Arrays;

/**
 * Memory engine configuration.
 *
 * <p>Mirrors Python's {@code MemoryEngineConfig} in
 * {@code openjiuwen/core/memory/config/config.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemoryEngineConfig {

    @JsonProperty("default_model_cfg")
    private ModelRequestConfig defaultModelCfg;

    @JsonProperty("default_model_client_cfg")
    private ModelClientConfig defaultModelClientCfg;

    @JsonProperty("forbidden_variables")
    private String forbiddenVariables = "";

    @JsonProperty("input_msg_max_len")
    private int inputMsgMaxLen = 8192;

    @JsonProperty("crypto_key")
    private byte[] cryptoKey = new byte[0];

    @JsonProperty("single_turn_history_summary_max_token")
    private int singleTurnHistorySummaryMaxToken = 128;

    public MemoryEngineConfig() {
    }

    public MemoryEngineConfig(
            ModelRequestConfig defaultModelCfg,
            ModelClientConfig defaultModelClientCfg,
            String forbiddenVariables,
            int inputMsgMaxLen,
            byte[] cryptoKey,
            int singleTurnHistorySummaryMaxToken) {
        this.defaultModelCfg = defaultModelCfg;
        this.defaultModelClientCfg = defaultModelClientCfg;
        setForbiddenVariables(forbiddenVariables);
        this.inputMsgMaxLen = inputMsgMaxLen;
        setCryptoKey(cryptoKey);
        setSingleTurnHistorySummaryMaxToken(singleTurnHistorySummaryMaxToken);
    }

    public static Builder builder() {
        return new Builder();
    }

    public ModelRequestConfig getDefaultModelCfg() {
        return defaultModelCfg;
    }

    public void setDefaultModelCfg(ModelRequestConfig defaultModelCfg) {
        this.defaultModelCfg = defaultModelCfg;
    }

    public ModelClientConfig getDefaultModelClientCfg() {
        return defaultModelClientCfg;
    }

    public void setDefaultModelClientCfg(ModelClientConfig defaultModelClientCfg) {
        this.defaultModelClientCfg = defaultModelClientCfg;
    }

    public String getForbiddenVariables() {
        return forbiddenVariables;
    }

    public void setForbiddenVariables(String forbiddenVariables) {
        this.forbiddenVariables = forbiddenVariables == null ? "" : forbiddenVariables;
    }

    public int getInputMsgMaxLen() {
        return inputMsgMaxLen;
    }

    public void setInputMsgMaxLen(int inputMsgMaxLen) {
        this.inputMsgMaxLen = inputMsgMaxLen;
    }

    public byte[] getCryptoKey() {
        return cryptoKey == null ? new byte[0] : Arrays.copyOf(cryptoKey, cryptoKey.length);
    }

    public void setCryptoKey(byte[] cryptoKey) {
        byte[] nextKey = cryptoKey == null ? new byte[0] : Arrays.copyOf(cryptoKey, cryptoKey.length);
        if (nextKey.length != 0 && nextKey.length != CryptUtils.AES_KEY_LENGTH) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_SET_CONFIG_EXECUTION_ERROR,
                    "config_type", "crypto_key",
                    "error_msg", "crypto_key must be empty or " + CryptUtils.AES_KEY_LENGTH + " bytes length"
            );
        }
        this.cryptoKey = nextKey;
    }

    public int getSingleTurnHistorySummaryMaxToken() {
        return singleTurnHistorySummaryMaxToken;
    }

    public void setSingleTurnHistorySummaryMaxToken(int singleTurnHistorySummaryMaxToken) {
        if (singleTurnHistorySummaryMaxToken <= 0) {
            throw new IllegalArgumentException("single_turn_history_summary_max_token must be greater than 0");
        }
        this.singleTurnHistorySummaryMaxToken = singleTurnHistorySummaryMaxToken;
    }

    /**
     * Explicit validator retained for compatibility with earlier translated call sites.
     */
    public void validateCryptoKey() {
        setCryptoKey(cryptoKey);
    }

    public static final class Builder {
        private ModelRequestConfig defaultModelCfg;
        private ModelClientConfig defaultModelClientCfg;
        private String forbiddenVariables = "";
        private int inputMsgMaxLen = 8192;
        private byte[] cryptoKey = new byte[0];
        private int singleTurnHistorySummaryMaxToken = 128;

        private Builder() {
        }

        public Builder defaultModelCfg(ModelRequestConfig defaultModelCfg) {
            this.defaultModelCfg = defaultModelCfg;
            return this;
        }

        public Builder defaultModelClientCfg(ModelClientConfig defaultModelClientCfg) {
            this.defaultModelClientCfg = defaultModelClientCfg;
            return this;
        }

        public Builder forbiddenVariables(String forbiddenVariables) {
            this.forbiddenVariables = forbiddenVariables;
            return this;
        }

        public Builder inputMsgMaxLen(int inputMsgMaxLen) {
            this.inputMsgMaxLen = inputMsgMaxLen;
            return this;
        }

        public Builder cryptoKey(byte[] cryptoKey) {
            this.cryptoKey = cryptoKey == null ? new byte[0] : Arrays.copyOf(cryptoKey, cryptoKey.length);
            return this;
        }

        public Builder singleTurnHistorySummaryMaxToken(int singleTurnHistorySummaryMaxToken) {
            this.singleTurnHistorySummaryMaxToken = singleTurnHistorySummaryMaxToken;
            return this;
        }

        public MemoryEngineConfig build() {
            return new MemoryEngineConfig(
                    defaultModelCfg,
                    defaultModelClientCfg,
                    forbiddenVariables,
                    inputMsgMaxLen,
                    cryptoKey,
                    singleTurnHistorySummaryMaxToken
            );
        }
    }
}
