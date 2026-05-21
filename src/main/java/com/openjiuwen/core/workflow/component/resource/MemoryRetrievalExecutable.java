/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.common.security.UserConfig;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.memory.MemResult;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.ComponentExecutable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executable for the Memory Retrieval workflow component.
 * <p>
 * Retrieves memories from long-term memory based on query input.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.resource.memory_retrieval_comp.MemoryRetrievalExecutable}.
 */
public class MemoryRetrievalExecutable extends ComponentExecutable {

    private static final LoggerProtocol WORKFLOW_LOGGER = Loggers.WORKFLOW;

    private final MemoryRetrievalCompConfig config;
    private NodeSessionApi session;

    public MemoryRetrievalExecutable(MemoryRetrievalCompConfig config) {
        this.config = config;
    }

    @Override
    public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
        this.session = session;

        MemoryRetrievalInput retrievalInput = validateInputs(inputs);
        String query = retrievalInput.getQuery();

        if (query == null || query.strip().isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_MEMORY_RETRIEVAL_INPUT_PARAM_ERROR,
                    "error_msg", "Query must be a non-empty string");
        }

        // Log retrieval start
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("query_length", query.length());
        metadata.put("top_k", retrievalInput.getTopK());
        metadata.put("threshold", config.getThreshold());
        metadata.put("user_id", config.getUserId());
        metadata.put("scope_id", config.getScopeId());
        metadata.put("sensitive_mode", UserConfig.isSensitive());

        WORKFLOW_LOGGER.info(
                "Memory retrieval started",
                LogEventType.WORKFLOW_COMPONENT_START,
                session.getExecutableId(),
                "MemoryRetrievalComponent",
                session.getSessionId(),
                metadata
        );

        // Execute retrieval
        List<MemResult> memResults;
        List<MemResult> summaryResults;
        try {
            memResults = config.getMemory().searchUserMem(
                    query,
                    retrievalInput.getTopK(),
                    config.getUserId(),
                    config.getScopeId(),
                    config.getThreshold()
            );
            summaryResults = config.getMemory().searchUserHistorySummary(
                    query,
                    retrievalInput.getTopK(),
                    config.getUserId(),
                    config.getScopeId(),
                    config.getThreshold()
            );
        } catch (Exception e) {
            WORKFLOW_LOGGER.error(
                    "Memory retrieval failed",
                    LogEventType.WORKFLOW_COMPONENT_ERROR,
                    session.getExecutableId(),
                    "MemoryRetrievalComponent",
                    session.getSessionId()
            );
            throw ErrorHelper.buildError(StatusCode.COMPONENT_MEMORY_RETRIEVAL_INVOKE_CALL_FAILED,
                    "error_msg", "Memory retrieval call failed: " + e.getMessage());
        }

        // Format output
        MemoryRetrievalOutput output = MemoryRetrievalOutput.builder()
                .fragmentMemoryResults(memResults)
                .summaryResults(summaryResults)
                .build();

        // Log completion
        Map<String, Object> endMetadata = new HashMap<>();
        endMetadata.put("num_results", memResults.size());
        endMetadata.put("num_summary_results", summaryResults.size());
        endMetadata.put("sensitive_mode", UserConfig.isSensitive());

        WORKFLOW_LOGGER.info(
                "Memory retrieval completed",
                LogEventType.WORKFLOW_COMPONENT_END,
                session.getExecutableId(),
                "MemoryRetrievalComponent",
                session.getSessionId(),
                endMetadata
        );

        return output;
    }

    /**
     * Validate and parse inputs into MemoryRetrievalInput.
     *
     * @param inputs raw input object
     * @return validated MemoryRetrievalInput
     */
    private MemoryRetrievalInput validateInputs(Object inputs) {
        if (inputs instanceof MemoryRetrievalInput) {
            return (MemoryRetrievalInput) inputs;
        }
        if (inputs instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) inputs;
            String query = (String) map.get("query");
            Integer topK = map.containsKey("top_k") ? (Integer) map.get("top_k") : 5;
            return MemoryRetrievalInput.builder()
                    .query(query)
                    .topK(topK)
                    .additionalFields(map)
                    .build();
        }
        throw ErrorHelper.buildError(StatusCode.COMPONENT_MEMORY_RETRIEVAL_INPUT_PARAM_ERROR,
                "error_msg", "Invalid input type for MemoryRetrieval");
    }
}