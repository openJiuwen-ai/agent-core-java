/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.MemResult;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.ComponentExecutable;

import java.util.List;
import java.util.Map;

/**
 * Executable for the Memory Retrieval workflow component.
 * <p>
 * Retrieves memories from long-term memory based on query.
 * <p>
 * Mirrors Python's {@code MemoryRetrievalExecutable}.
 * 
 * @since 0.1.7
 */
public class MemoryRetrievalExecutable extends ComponentExecutable {
    private final MemoryRetrievalCompConfig config;
    private NodeSessionApi session;

    /**
     * Create a MemoryRetrievalExecutable with the given configuration.
     * 
     * @param config the component configuration
     * @since 0.1.7
     */
    public MemoryRetrievalExecutable(MemoryRetrievalCompConfig config) {
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

        MemoryRetrievalInput retrievalInput = validateInputs(inputs);
        String query = retrievalInput.getQuery();

        if (query == null || query.strip().isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_MEMORY_RETRIEVAL_INPUT_PARAM_ERROR, "error_msg",
                    "Query must be a non-empty string");
        }

        LongTermMemory memory = config.getMemory();
        List<MemResult> memResults;
        List<MemResult> summaryResults;
        try {
            memResults = memory.searchUserMem(query, retrievalInput.getTopK(), config.getUserId(), config.getScopeId(),
                    config.getThreshold());
            summaryResults = memory.searchUserHistorySummary(query, retrievalInput.getTopK(), config.getUserId(),
                    config.getScopeId(), config.getThreshold());
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_MEMORY_RETRIEVAL_INVOKE_CALL_FAILED, "error_msg",
                    "Memory retrieval call failed: " + e.getMessage());
        }

        return formatOutput(memResults, summaryResults);
    }

    @SuppressWarnings("unchecked")
    /**
     * validateInputs.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    private MemoryRetrievalInput validateInputs(Object inputs) {
        if (inputs instanceof Map) {
            return MemoryRetrievalInput.fromMap((Map<String, Object>) inputs);
        }
        throw ErrorHelper.buildError(StatusCode.COMPONENT_MEMORY_RETRIEVAL_INPUT_PARAM_ERROR, "error_msg",
                "inputs must be a map containing 'query'");
    }

    /**
     * formatOutput.
     * 
     * @param memResults memResults
     * @param summaryResults summaryResults
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> formatOutput(List<MemResult> memResults, List<MemResult> summaryResults) {
        MemoryRetrievalOutput output = new MemoryRetrievalOutput(memResults, summaryResults);
        return output.toMap();
    }
}
