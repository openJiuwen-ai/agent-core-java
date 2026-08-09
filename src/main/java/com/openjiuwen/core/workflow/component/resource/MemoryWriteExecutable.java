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
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.workflow.ComponentExecutable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Executable memory write workflow component.
 *
 * <p>Mirrors Python's {@code MemoryWriteExecutable} in
 * {@code openjiuwen/core/workflow/components/resource/memory_write_comp.py}.</p>
 */
public class MemoryWriteExecutable extends ComponentExecutable<Object, Map<String, Object>> {

    private static final LoggerProtocol WORKFLOW_LOGGER = Loggers.WORKFLOW;
    private static final String COMPONENT_TYPE = "LongTermMemoryWriteComponent";

    private final MemoryWriteCompConfig config;
    private BaseSession session;

    public MemoryWriteExecutable(MemoryWriteCompConfig config) {
        this.config = config;
    }

    @Override
    public Map<String, Object> invoke(Object inputs, BaseSession session, ModelContext context) {
        this.session = session;
        MemoryWriteInput writeInput = validateInputs(inputs);
        List<BaseMessage> messages = writeInput.getMessages();

        if (messages.isEmpty()) {
            throw inputError("Messages list cannot be empty", null);
        }

        logStart(messages);

        try {
            join(requireMemory().addMessages(
                    messages,
                    config.getAgentConfig(),
                    config.getUserId(),
                    config.getScopeId(),
                    config.getSessionId(),
                    writeInput.getTimestamp(),
                    config.isGenMem(),
                    config.getGenMemWithHistoryMsgNum()
            ));
        } catch (RuntimeException exception) {
            logError();
            throw invokeError("Memory write call failed: " + exception.getMessage(), exception);
        }

        Map<String, Object> output = new MemoryWriteOutput(true).toMap();
        logEnd(messages);
        return output;
    }

    /**
     * Validate and normalize workflow input using Python's Pydantic model shape.
     *
     * @param inputs raw graph input
     * @return normalized input
     */
    public static MemoryWriteInput validateInputs(Object inputs) {
        if (inputs instanceof MemoryWriteInput writeInput) {
            if (writeInput.getMessages() == null) {
                throw inputError("Field 'messages' is required", null);
            }
            return writeInput;
        }
        if (!(inputs instanceof Map<?, ?> rawMap)) {
            throw inputError("inputs must be a map containing 'messages'", null);
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> normalized.put(String.valueOf(key), value));
        if (!normalized.containsKey("messages") || normalized.get("messages") == null) {
            throw inputError("Field 'messages' is required", null);
        }
        MemoryWriteInput writeInput = new MemoryWriteInput();
        writeInput.setMessages(parseMessages(normalized.get("messages")));
        writeInput.setTimestamp(parseTimestamp(normalized.get("timestamp")));
        Map<String, Object> extraFields = new LinkedHashMap<>(normalized);
        extraFields.remove("messages");
        extraFields.remove("timestamp");
        writeInput.setExtraFields(extraFields);
        return writeInput;
    }

    private LongTermMemory requireMemory() {
        if (config == null || config.getMemory() == null) {
            throw new IllegalStateException("memory is required");
        }
        return config.getMemory();
    }

    private void logStart(List<BaseMessage> messages) {
        WORKFLOW_LOGGER.info(
                "Long-term memory write started. event_type={}, component_id={}, component_type={}, session_id={}, metadata={}",
                LogEventType.WORKFLOW_COMPONENT_START.getValue(),
                executableId(),
                COMPONENT_TYPE,
                sessionId(),
                Map.of(
                        "message_count", messages.size(),
                        "scope_id", config == null ? LongTermMemory.DEFAULT_VALUE : config.getScopeId(),
                        "user_id", config == null ? LongTermMemory.DEFAULT_VALUE : config.getUserId(),
                        "gen_mem", config == null || config.isGenMem(),
                        "sensitive_mode", UserConfig.isSensitive()
                )
        );
    }

    private void logError() {
        WORKFLOW_LOGGER.error(
                "Long-term memory write failed. event_type={}, component_id={}, component_type={}, session_id={}",
                LogEventType.WORKFLOW_COMPONENT_ERROR.getValue(),
                executableId(),
                COMPONENT_TYPE,
                sessionId()
        );
    }

    private void logEnd(List<BaseMessage> messages) {
        WORKFLOW_LOGGER.info(
                "Long-term memory write completed. event_type={}, component_id={}, component_type={}, session_id={}, metadata={}",
                LogEventType.WORKFLOW_COMPONENT_END.getValue(),
                executableId(),
                COMPONENT_TYPE,
                sessionId(),
                Map.of(
                        "message_count", messages.size(),
                        "sensitive_mode", UserConfig.isSensitive()
                )
        );
    }

    private String executableId() {
        String reflected = stringMethod("getExecutableId");
        if (reflected != null) {
            return reflected;
        }
        reflected = stringMethod("executableId");
        if (reflected != null) {
            return reflected;
        }
        String fallback = stringMethod("getCurrentOperatorId");
        return fallback == null ? "" : fallback;
    }

    private String sessionId() {
        return session == null ? "" : session.getSessionId();
    }

    private String stringMethod(String methodName) {
        if (session == null) {
            return null;
        }
        try {
            Method method = session.getClass().getMethod(methodName);
            Object value = method.invoke(session);
            return value == null ? null : String.valueOf(value);
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
            return null;
        } catch (InvocationTargetException exception) {
            return null;
        }
    }

    private static List<BaseMessage> parseMessages(Object value) {
        if (!(value instanceof List<?> rawList)) {
            throw inputError("Field 'messages' must be a list", null);
        }
        List<BaseMessage> messages = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof BaseMessage message)) {
                throw inputError("Field 'messages' must contain BaseMessage values", null);
            }
            messages.add(message);
        }
        return messages;
    }

    private static ZonedDateTime parseTimestamp(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toZonedDateTime();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return ZonedDateTime.parse(text);
            } catch (RuntimeException exception) {
                throw inputError("Field 'timestamp' must be an ISO-8601 datetime", exception);
            }
        }
        throw inputError("Field 'timestamp' must be a datetime", null);
    }

    private static void join(CompletionStage<?> stage) {
        try {
            stage.toCompletableFuture().join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    private static RuntimeException inputError(String message, Throwable cause) {
        return ErrorHelper.buildError(
                StatusCode.COMPONENT_MEMORY_WRITE_INPUT_PARAM_ERROR,
                null,
                null,
                cause,
                Map.of("error_msg", message)
        );
    }

    private static RuntimeException invokeError(String message, Throwable cause) {
        return ErrorHelper.buildError(
                StatusCode.COMPONENT_MEMORY_WRITE_INVOKE_CALL_FAILED,
                null,
                null,
                cause,
                Map.of("error_msg", message)
        );
    }
}
