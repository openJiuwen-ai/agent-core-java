/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

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
}
