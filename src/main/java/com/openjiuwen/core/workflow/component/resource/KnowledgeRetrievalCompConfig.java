/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.workflow.component.ComponentConfig;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * Configuration for the Knowledge Retrieval workflow component.
 * <p>
 * Mirrors Python's {@code KnowledgeRetrievalCompConfig}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KnowledgeRetrievalCompConfig extends ComponentConfig {

    private List<KnowledgeBaseConfig> kbConfigs;
    private RetrievalConfig retrievalConfig;
    private VectorStoreConfig vectorStoreConfig;
    private Map<String, Object> vectorStoreAdditionalConfig;
    private EmbeddingConfig embedConfig;

    // Optional LLM config for agentic / query-rewrite scenarios
    private String modelId;
    private ModelClientConfig modelClientConfig;
    private ModelRequestConfig modelConfig;

    // Output formatting
    private String resultSeparator = "\n\n";
    private boolean includeMetadata = false;

    public List<KnowledgeBaseConfig> getKbConfigs() { return kbConfigs; }
    public void setKbConfigs(List<KnowledgeBaseConfig> kbConfigs) { this.kbConfigs = kbConfigs; }
    public RetrievalConfig getRetrievalConfig() { return retrievalConfig; }
    public void setRetrievalConfig(RetrievalConfig retrievalConfig) { this.retrievalConfig = retrievalConfig; }
    public VectorStoreConfig getVectorStoreConfig() { return vectorStoreConfig; }
    public void setVectorStoreConfig(VectorStoreConfig vectorStoreConfig) { this.vectorStoreConfig = vectorStoreConfig; }
    public Map<String, Object> getVectorStoreAdditionalConfig() { return vectorStoreAdditionalConfig; }
    public void setVectorStoreAdditionalConfig(Map<String, Object> vectorStoreAdditionalConfig) { this.vectorStoreAdditionalConfig = vectorStoreAdditionalConfig; }
    public EmbeddingConfig getEmbedConfig() { return embedConfig; }
    public void setEmbedConfig(EmbeddingConfig embedConfig) { this.embedConfig = embedConfig; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public ModelClientConfig getModelClientConfig() { return modelClientConfig; }
    public void setModelClientConfig(ModelClientConfig modelClientConfig) { this.modelClientConfig = modelClientConfig; }
    public ModelRequestConfig getModelConfig() { return modelConfig; }
    public void setModelConfig(ModelRequestConfig modelConfig) { this.modelConfig = modelConfig; }
    public String getResultSeparator() { return resultSeparator; }
    public void setResultSeparator(String resultSeparator) { this.resultSeparator = resultSeparator; }
    public boolean isIncludeMetadata() { return includeMetadata; }
    public void setIncludeMetadata(boolean includeMetadata) { this.includeMetadata = includeMetadata; }
}
