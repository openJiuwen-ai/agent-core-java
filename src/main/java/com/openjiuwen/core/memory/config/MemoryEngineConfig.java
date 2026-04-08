/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.memory.common.MemoryCrypto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Memory engine configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryEngineConfig {

    @JsonProperty("default_model_cfg")
    private ModelRequestConfig defaultModelCfg;

    @JsonProperty("default_model_client_cfg")
    private ModelClientConfig defaultModelClientCfg;

    @Builder.Default
    @JsonProperty("input_msg_max_len")
    private int inputMsgMaxLen = 8192;

    @Builder.Default
    @JsonProperty("crypto_key")
    private byte[] cryptoKey = new byte[0];

    @Builder.Default
    @JsonProperty("single_turn_history_summary_max_token")
    private int singleTurnHistorySummaryMaxToken = 128;

    /**
     * Validate crypto key: must be empty or exactly 32 bytes.
     */
    public void validateCryptoKey() {
        if (cryptoKey == null || cryptoKey.length == 0) {
            return;
        }
        if (cryptoKey.length != MemoryCrypto.AES_KEY_LENGTH) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_SET_CONFIG_EXECUTION_ERROR,
                    "config_type", "crypto_key",
                    "error_msg", "crypto_key must be empty or " + MemoryCrypto.AES_KEY_LENGTH + " bytes length"
            );
        }
    }
}
