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
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.MemResult;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.workflow.ComponentExecutable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Executable memory retrieval workflow component.
 *
 * <p>Mirrors Python's {@code MemoryRetrievalExecutable} in
 * {@code openjiuwen/core/workflow/components/resource/memory_retrieval_comp.py}.</p>
 */
public class MemoryRetrievalExecutable extends ComponentExecutable<Object, Map<String, Object>> {

    private static final LoggerProtocol WORKFLOW_LOGGER = Loggers.WORKFLOW;
    private static final String COMPONENT_TYPE = "MemoryRetrievalComponent";

    private final MemoryRetrievalCompConfig config;
    private BaseSession session;

    public MemoryRetrievalExecutable(MemoryRetrievalCompConfig config) {
        this.config = config;
    }

    @Override
    public Map<String, Object> invoke(Object inputs, BaseSession session, ModelContext context) {
        this.session = session;
        MemoryRetrievalInput retrievalInput = validateInputs(inputs);
        String query = retrievalInput.getQuery();
        if (query == null || query.strip().isEmpty()) {
            throw inputError("Query must be a non-empty string", null);
        }

        logStart(query, retrievalInput);

        List<MemResult> memResults;
        List<MemResult> summaryResults;
        try {
            LongTermMemory memory = requireMemory();
            memResults = join(memory.searchUserMem(
                    query,
                    retrievalInput.getTopK(),
                    config.getUserId(),
                    config.getScopeId(),
                    config.getThreshold()
            ));
            summaryResults = join(memory.searchUserHistorySummary(
                    query,
                    retrievalInput.getTopK(),
                    config.getUserId(),
                    config.getScopeId(),
                    config.getThreshold()
            ));
        } catch (RuntimeException exception) {
            logError();
            throw invokeError("Memory retrieval call failed: " + exception.getMessage(), exception);
        }

        Map<String, Object> output = formatOutput(memResults, summaryResults);
        logEnd(memResults, summaryResults);
        return output;
    }

    /**
     * Validate and normalize workflow input using Python's Pydantic model shape.
     *
     * @param inputs raw graph input
     * @return normalized input
     */
    public static MemoryRetrievalInput validateInputs(Object inputs) {
        if (inputs instanceof MemoryRetrievalInput retrievalInput) {
            if (retrievalInput.getQuery() == null) {
                throw inputError("Field 'query' is required", null);
            }
            return retrievalInput;
        }
        if (!(inputs instanceof Map<?, ?> rawMap)) {
            throw inputError("inputs must be a map containing 'query'", null);
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> normalized.put(String.valueOf(key), value));
        if (!normalized.containsKey("query") || normalized.get("query") == null) {
            throw inputError("Field 'query' is required", null);
        }
        Object queryValue = normalized.get("query");
        MemoryRetrievalInput retrievalInput = new MemoryRetrievalInput();
        retrievalInput.setQuery(String.valueOf(queryValue));
        retrievalInput.setTopK(parseTopK(normalized));
        Map<String, Object> extraFields = new LinkedHashMap<>(normalized);
        extraFields.remove("query");
        extraFields.remove("top_k");
        extraFields.remove("topK");
        retrievalInput.setExtraFields(extraFields);
        return retrievalInput;
    }

    public static Map<String, Object> formatOutput(List<MemResult> results, List<MemResult> summaryResults) {
        return new MemoryRetrievalOutput(results, summaryResults).toMap();
    }

    private LongTermMemory requireMemory() {
        if (config == null || config.getMemory() == null) {
            throw new IllegalStateException("memory is required");
        }
        return config.getMemory();
    }

    private void logStart(String query, MemoryRetrievalInput retrievalInput) {
        WORKFLOW_LOGGER.info(
                "Memory retrieval started. event_type={}, component_id={}, component_type={}, session_id={}, metadata={}",
                LogEventType.WORKFLOW_COMPONENT_START.getValue(),
                executableId(),
                COMPONENT_TYPE,
                sessionId(),
                Map.of(
                        "query_length", query.length(),
                        "top_k", retrievalInput.getTopK(),
                        "threshold", config == null ? 0.3d : config.getThreshold(),
                        "user_id", config == null ? LongTermMemory.DEFAULT_VALUE : config.getUserId(),
                        "scope_id", config == null ? LongTermMemory.DEFAULT_VALUE : config.getScopeId(),
                        "sensitive_mode", UserConfig.isSensitive()
                )
        );
    }

    private void logError() {
        WORKFLOW_LOGGER.error(
                "Memory retrieval failed. event_type={}, component_id={}, component_type={}, session_id={}",
                LogEventType.WORKFLOW_COMPONENT_ERROR.getValue(),
                executableId(),
                COMPONENT_TYPE,
                sessionId()
        );
    }

    private void logEnd(List<MemResult> memResults, List<MemResult> summaryResults) {
        WORKFLOW_LOGGER.info(
                "Memory retrieval completed. event_type={}, component_id={}, component_type={}, session_id={}, metadata={}",
                LogEventType.WORKFLOW_COMPONENT_END.getValue(),
                executableId(),
                COMPONENT_TYPE,
                sessionId(),
                Map.of(
                        "num_results", size(memResults),
                        "num_summary_results", size(summaryResults),
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
        return session == null || session.getCurrentOperatorId() == null ? "" : session.getCurrentOperatorId();
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

    private static int parseTopK(Map<String, Object> normalized) {
        Object value = normalized.containsKey("top_k") ? normalized.get("top_k") : normalized.get("topK");
        if (value == null) {
            return 5;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException exception) {
                throw inputError("Field 'top_k' must be an integer", exception);
            }
        }
        throw inputError("Field 'top_k' must be an integer", null);
    }

    private static int size(List<MemResult> results) {
        return results == null ? 0 : results.size();
    }

    private static <T> List<T> join(CompletionStage<List<T>> stage) {
        try {
            List<T> result = stage.toCompletableFuture().join();
            return result == null ? new ArrayList<>() : result;
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    private static RuntimeException inputError(String message, Throwable cause) {
        return ErrorHelper.buildError(
                StatusCode.COMPONENT_MEMORY_RETRIEVAL_INPUT_PARAM_ERROR,
                null,
                null,
                cause,
                Map.of("error_msg", message)
        );
    }

    private static RuntimeException invokeError(String message, Throwable cause) {
        return ErrorHelper.buildError(
                StatusCode.COMPONENT_MEMORY_RETRIEVAL_INVOKE_CALL_FAILED,
                null,
                null,
                cause,
                Map.of("error_msg", message)
        );
    }
}
