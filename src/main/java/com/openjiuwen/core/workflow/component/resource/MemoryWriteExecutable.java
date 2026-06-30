/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.ComponentExecutable;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Executable for the Memory Write workflow component.
 * <p>
 * Writes messages to long-term memory.
 * <p>
 * Mirrors Python's {@code MemoryWriteExecutable}.
 *
 * @since 1.0.0
 */
public class MemoryWriteExecutable extends ComponentExecutable {
    private final MemoryWriteCompConfig config;
    private NodeSessionApi session;

    /**
     * Create a MemoryWriteExecutable with the given configuration.
     *
     * @param config the component configuration
     */
    public MemoryWriteExecutable(MemoryWriteCompConfig config) {
        this.config = config;
    }

    @Override
    public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
        this.session = session;

        MemoryWriteInput writeInput = validateInputs(inputs);
        List<BaseMessage> messages = writeInput.getMessages();

        if (messages == null || messages.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_MEMORY_WRITE_INPUT_PARAM_ERROR,
                    "error_msg", "Messages list cannot be empty");
        }

        LongTermMemory memory = config.getMemory();
        try {
            OffsetDateTime timestamp = writeInput.getTimestamp();
            if (timestamp == null) {
                timestamp = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
            }
            memory.addMessages(
                    messages,
                    config.getAgentConfig(),
                    config.getUserId(),
                    config.getScopeId(),
                    config.getSessionId(),
                    timestamp,
                    config.isGenMem(),
                    config.getGenMemWithHistoryMsgNum());
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_MEMORY_WRITE_INVOKE_CALL_FAILED,
                    "error_msg", "Memory write call failed: " + e.getMessage());
        }

        return Map.of("success", true);
    }

    @SuppressWarnings("unchecked")
    private MemoryWriteInput validateInputs(Object inputs) {
        if (inputs instanceof Map) {
            return MemoryWriteInput.fromMap((Map<String, Object>) inputs);
        }
        throw ErrorHelper.buildError(StatusCode.COMPONENT_MEMORY_WRITE_INPUT_PARAM_ERROR,
                "error_msg", "inputs must be a map containing 'messages'");
    }
}
