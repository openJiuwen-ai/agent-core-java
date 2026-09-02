/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.retrieval.GraphKnowledgeBase;
import com.openjiuwen.core.retrieval.KnowledgeBase;
import com.openjiuwen.core.retrieval.SimpleKnowledgeBase;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.MultiKBRetrievalResult;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.ComponentExecutable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executable for the Knowledge Retrieval workflow component.
 * <p>
 * Lazily initialises knowledge bases and retrieves relevant documents.
 * <p>
 * Mirrors Python's {@code KnowledgeRetrievalExecutable}.
 * 
 * @since 0.1.7
 */
public class KnowledgeRetrievalExecutable extends ComponentExecutable {
    private final KnowledgeRetrievalCompConfig config;

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<KnowledgeBase> knowledgeBases = new ArrayList<>();
    private Model llm;
    private boolean initialized = false;
    private NodeSessionApi session;

    /**
     * KnowledgeRetrievalExecutable.
     * 
     * @param config config
     * @since 0.1.7
     */
    public KnowledgeRetrievalExecutable(KnowledgeRetrievalCompConfig config) {
        this.config = config;
    }

    /**
     * invoke.
     * 
     * @param inputs inputs
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
        this.session = session;
        initializeIfNeeded();

        KnowledgeRetrievalInput krInput = validateInputs(inputs);
        String query = krInput.getQuery();
        if (query == null || query.strip().isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_INPUT_PARAM_ERROR, "error_msg",
                    "Query must be a non-empty string");
        }

        RetrievalConfig retrievalConfig = config.getRetrievalConfig();

        List<MultiKBRetrievalResult> retrievalResults;
        try {
            retrievalResults =
                SimpleKnowledgeBase.retrieveMultiKbWithSource(knowledgeBases, query, retrievalConfig.getTopK());
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_INVOKE_CALL_FAILED, "error_msg",
                    "Retrieve call failed: " + e.getMessage());
        }

        return formatOutput(retrievalResults);
    }

    @SuppressWarnings("unchecked")
    /**
     * validateInputs.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    private KnowledgeRetrievalInput validateInputs(Object inputs) {
        if (inputs instanceof Map) {
            return KnowledgeRetrievalInput.fromMap((Map<String, Object>) inputs);
        }
        throw ErrorHelper.buildError(StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_INPUT_PARAM_ERROR, "error_msg",
                "inputs must be a map containing 'query'");
    }

    /**
     * initializeIfNeeded.
     * 
     * @since 0.1.7
     */
    private synchronized void initializeIfNeeded() {
        if (initialized) {
            return;
        }
        try {
            if (config.getRetrievalConfig().isAgentic()) {
                llm = createLlmInstance();
            }
            knowledgeBases = createKnowledgeBases();
            initialized = true;
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_INVOKE_CALL_FAILED, "error_msg",
                    "Failed to initialise knowledge base: " + e.getMessage());
        }
    }

    /**
     * createKnowledgeBases.
     * 
     * @return the result
     * @since 0.1.7
     */
    private List<KnowledgeBase> createKnowledgeBases() {
        // Knowledge base instances
        Boolean useGraph = config.getRetrievalConfig().getUseGraph();
        List<KnowledgeBase> kbInstances = new ArrayList<>();
        for (KnowledgeBaseConfig kbConfig : config.getKbConfigs()) {
            if (Boolean.TRUE.equals(useGraph)) {
                kbInstances.add(new GraphKnowledgeBase(kbConfig));
            } else {
                kbInstances.add(new SimpleKnowledgeBase(kbConfig));
            }
        }
        return kbInstances;
    }

    /**
     * createLlmInstance.
     * 
     * @return the result
     * @since 0.1.7
     */
    private Model createLlmInstance() {
        if (config.getModelId() != null) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_LLM_MODEL_INIT_ERROR, "error_msg",
                    "model_id based model lookup not yet supported; "
                            + "provide modelClientConfig and modelConfig instead");
        }
        if (config.getModelClientConfig() == null || config.getModelConfig() == null) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_LLM_MODEL_INIT_ERROR, "error_msg",
                    "LLM model config is required for agentic retrieval");
        }
        return new Model(config.getModelClientConfig(), config.getModelConfig());
    }

    /**
     * formatOutput.
     * 
     * @param results results
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> formatOutput(List<MultiKBRetrievalResult> results) {
        List<String> texts = new ArrayList<>();
        for (MultiKBRetrievalResult r : results) {
            texts.add(r.getText());
        }
        String contextStr = String.join(config.getResultSeparator(), texts);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("results", texts);
        output.put("context", contextStr);

        if (config.isIncludeMetadata()) {
            List<Map<String, Object>> metadataList = new ArrayList<>();
            for (MultiKBRetrievalResult r : results) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("text", r.getText());
                entry.put("score", r.getScore());
                if (r.getMetadata() != null) {
                    entry.put("metadata", r.getMetadata());
                }
                metadataList.add(entry);
            }
            output.put("results_with_metadata", metadataList);
        }

        return output;
    }
}
