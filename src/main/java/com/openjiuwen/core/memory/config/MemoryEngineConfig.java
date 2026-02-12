/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.config;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.memory.common.CryptoUtils;

import java.util.Arrays;

/**
 * Memory engine configuration.
 * Corresponds to Python: config/config.py - MemoryEngineConfig
 */
public class MemoryEngineConfig {

    private final ModelRequestConfig defaultModelCfg;
    private final ModelClientConfig defaultModelClientCfg;
    private final int inputMsgMaxLen;
    private final byte[] cryptoKey;

    private MemoryEngineConfig(Builder builder) {
        this.defaultModelCfg = builder.defaultModelCfg;
        this.defaultModelClientCfg = builder.defaultModelClientCfg;
        this.inputMsgMaxLen = builder.inputMsgMaxLen;
        this.cryptoKey = builder.cryptoKey != null ? builder.cryptoKey.clone() : new byte[0];
    }

    public ModelRequestConfig getDefaultModelCfg() {
        return defaultModelCfg;
    }

    public ModelClientConfig getDefaultModelClientCfg() {
        return defaultModelClientCfg;
    }

    public int getInputMsgMaxLen() {
        return inputMsgMaxLen;
    }

    public byte[] getCryptoKey() {
        return cryptoKey.clone();
    }

    /**
     * Check if encryption is enabled.
     *
     * @return true if crypto_key is set
     */
    public boolean isEncryptionEnabled() {
        return cryptoKey.length > 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ModelRequestConfig defaultModelCfg;
        private ModelClientConfig defaultModelClientCfg;
        private int inputMsgMaxLen = 8192;
        private byte[] cryptoKey = new byte[0];

        public Builder defaultModelCfg(ModelRequestConfig defaultModelCfg) {
            this.defaultModelCfg = defaultModelCfg;
            return this;
        }

        public Builder defaultModelClientCfg(ModelClientConfig defaultModelClientCfg) {
            this.defaultModelClientCfg = defaultModelClientCfg;
            return this;
        }

        public Builder inputMsgMaxLen(int inputMsgMaxLen) {
            this.inputMsgMaxLen = inputMsgMaxLen;
            return this;
        }

        public Builder cryptoKey(byte[] cryptoKey) {
            if (cryptoKey == null) {
                this.cryptoKey = new byte[0];
            } else if (cryptoKey.length == 0 || cryptoKey.length == CryptoUtils.AES_KEY_LENGTH) {
                this.cryptoKey = cryptoKey.clone();
            } else {
                throw new IllegalArgumentException(
                    String.format("Invalid crypto_key, must be empty or %d bytes length", CryptoUtils.AES_KEY_LENGTH));
            }
            return this;
        }

        public MemoryEngineConfig build() {
            return new MemoryEngineConfig(this);
        }
    }

    @Override
    public String toString() {
        return "MemoryEngineConfig{" +
               "inputMsgMaxLen=" + inputMsgMaxLen +
               ", cryptoKeySet=" + (cryptoKey.length > 0) +
               '}';
    }
}

