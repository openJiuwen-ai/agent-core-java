/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.workflow.component.ComponentConfig;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for the Knowledge Retrieval workflow component.
 *
 * <p>Mirrors Python's {@code KnowledgeRetrievalCompConfig} in
 * {@code openjiuwen/core/workflow/components/resource/knowledge_retrieval_comp.py}.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KnowledgeRetrievalCompConfig extends ComponentConfig {

    @JsonProperty("component_kb_configs")
    private List<ComponentKBConfig> componentKbConfigs = new ArrayList<>();

    @JsonProperty("vector_store_connection_config")
    private Map<String, Object> vectorStoreConnectionConfig = new LinkedHashMap<>();

    @JsonProperty("retrieval_config")
    private RetrievalConfig retrievalConfig;

    @JsonProperty("model_id")
    private String modelId;

    @JsonProperty("model_client_config")
    private ModelClientConfig modelClientConfig;

    @JsonProperty("model_config")
    private ModelRequestConfig modelConfig;

    public void setComponentKbConfigs(List<ComponentKBConfig> componentKbConfigs) {
        this.componentKbConfigs = componentKbConfigs == null ? new ArrayList<>() : new ArrayList<>(componentKbConfigs);
    }

    public void setVectorStoreConnectionConfig(Map<String, Object> vectorStoreConnectionConfig) {
        this.vectorStoreConnectionConfig = vectorStoreConnectionConfig == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(vectorStoreConnectionConfig);
    }
}
