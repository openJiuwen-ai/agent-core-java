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
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.ComponentExecutable;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executable for the Memory Write workflow component.
 * <p>
 * Writes messages to long-term memory.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.resource.memory_write_comp.MemoryWriteExecutable}.
 */
public class MemoryWriteExecutable extends ComponentExecutable {

    private static final LoggerProtocol WORKFLOW_LOGGER = Loggers.WORKFLOW;

    private final MemoryWriteCompConfig config;
    private NodeSessionApi session;

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

        // Log write start
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("message_count", messages.size());
        metadata.put("scope_id", config.getScopeId());
        metadata.put("user_id", config.getUserId());
        metadata.put("gen_mem", config.getGenMem());
        metadata.put("sensitive_mode", UserConfig.isSensitive());

        WORKFLOW_LOGGER.info(
                "Long-term memory write started",
                LogEventType.WORKFLOW_COMPONENT_START,
                session.getExecutableId(),
                "LongTermMemoryWriteComponent",
                session.getSessionId(),
                metadata
        );

        // Execute write
        try {
            config.getMemory().addMessages(
                    messages,
                    config.getAgentConfig(),
                    config.getUserId(),
                    config.getScopeId(),
                    config.getSessionId(),
                    writeInput.getTimestamp(),
                    config.getGenMem(),
                    config.getGenMemWithHistoryMsgNum()
            );
        } catch (Exception e) {
            WORKFLOW_LOGGER.error(
                    "Long-term memory write failed",
                    LogEventType.WORKFLOW_COMPONENT_ERROR,
                    session.getExecutableId(),
                    "LongTermMemoryWriteComponent",
                    session.getSessionId()
            );
            throw ErrorHelper.buildError(StatusCode.COMPONENT_MEMORY_WRITE_INVOKE_CALL_FAILED,
                    "error_msg", "Memory write call failed: " + e.getMessage());
        }

        // Format output
        MemoryWriteOutput output = MemoryWriteOutput.builder()
                .success(true)
                .build();

        // Log completion
        WORKFLOW_LOGGER.info(
                "Long-term memory write completed",
                LogEventType.WORKFLOW_COMPONENT_END,
                session.getExecutableId(),
                "LongTermMemoryWriteComponent",
                session.getSessionId()
        );

        return output;
    }

    /**
     * Validate and parse inputs into MemoryWriteInput.
     *
     * @param inputs raw input object
     * @return validated MemoryWriteInput
     */
    private MemoryWriteInput validateInputs(Object inputs) {
        if (inputs instanceof MemoryWriteInput) {
            return (MemoryWriteInput) inputs;
        }
        if (inputs instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) inputs;
            List<BaseMessage> messages = (List<BaseMessage>) map.get("messages");
            OffsetDateTime timestamp = map.containsKey("timestamp") 
                    ? (OffsetDateTime) map.get("timestamp") 
                    : null;
            return MemoryWriteInput.builder()
                    .messages(messages)
                    .timestamp(timestamp)
                    .additionalFields(map)
                    .build();
        }
        throw ErrorHelper.buildError(StatusCode.COMPONENT_MEMORY_WRITE_INPUT_PARAM_ERROR,
                "error_msg", "Invalid input type for MemoryWrite");
    }
}
