/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.memory.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Scope-specific memory configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryScopeConfig {

    @JsonProperty("model_cfg")
    private ModelRequestConfig modelCfg;

    @JsonProperty("model_client_cfg")
    private ModelClientConfig modelClientCfg;

    @JsonProperty("embedding_cfg")
    private EmbeddingConfig embeddingCfg;
}
