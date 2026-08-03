/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-knowledge-base configuration used by the Knowledge Retrieval workflow component.
 *
 * <p>Mirrors Python's {@code ComponentKBConfig} in
 * {@code openjiuwen/core/workflow/components/resource/knowledge_retrieval_comp.py}.</p>
 */
@Data
public class ComponentKBConfig {

    @JsonProperty("kb_config")
    private KnowledgeBaseConfig kbConfig;

    @JsonProperty("vector_store_config")
    private VectorStoreConfig vectorStoreConfig;

    @JsonProperty("embed_config")
    private EmbeddingConfig embedConfig;

    @JsonProperty("embed_additional_config")
    private Map<String, Object> embedAdditionalConfig = new LinkedHashMap<>();

    public void setEmbedAdditionalConfig(Map<String, Object> embedAdditionalConfig) {
        this.embedAdditionalConfig = embedAdditionalConfig == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(embedAdditionalConfig);
    }
}
