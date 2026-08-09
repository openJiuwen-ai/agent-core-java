/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.function;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.utils.SchemaUtils;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.callback.ToolCallEvents;
import com.openjiuwen.core.session.SessionContextHolder;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * Mirrors Python's {@code LocalFunction} in
 * {@code openjiuwen/core/foundation/tool/function/function.py}.
 */
public class LocalFunction extends Tool {

    private final Function<Map<String, Object>, Object> func;
    private final ContextFunction contextFunc;

    /**
     * Creates a local function tool.
     *
     * @param card tool metadata card
     * @param func wrapped function; must not be {@code null}
     */
    public LocalFunction(ToolCard card, Function<Map<String, Object>, Object> func) {
        super(card);
        if (func == null) {
            throw ErrorHelper.buildError(StatusCode.TOOL_LOCAL_FUNCTION_FUNC_NOT_SUPPORTED,
                    "card", String.valueOf(getCard()));
        }
        this.func = func;
        this.contextFunc = null;
    }

    /**
     * Creates a local function tool that can access execution kwargs such as {@code session}.
     *
     * @param card tool metadata card
     * @param contextFunc context-aware function; must not be {@code null}
     */
    public LocalFunction(ToolCard card, ContextFunction contextFunc) {
        super(card);
        if (contextFunc == null) {
            throw ErrorHelper.buildError(StatusCode.TOOL_LOCAL_FUNCTION_FUNC_NOT_SUPPORTED,
                    "card", String.valueOf(getCard()));
        }
        this.func = null;
        this.contextFunc = contextFunc;
    }

    /**
     * Returns the wrapped function.
     *
     * @return wrapped function, or {@code null} when a {@link ContextFunction} is used
     */
    public Function<Map<String, Object>, Object> getFunc() {
        return func;
    }

    /**
     * Returns the context-aware function variant.
     *
     * @return context-aware function, or {@code null} when a plain function is used
     */
    public ContextFunction getContextFunc() {
        return contextFunc;
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        Map<String, Object> formattedInputs = formatInputs(inputs, kwargs);
        Object result = invokeFunction(formattedInputs, kwargs);
        if (result instanceof Iterator<?>) {
            throw ErrorHelper.buildError(StatusCode.TOOL_LOCAL_FUNCTION_EXECUTION_ERROR,
                    "method", "invoke",
                    "reason", "func can not be generator",
                    "card", String.valueOf(getCard()));
        }
        return result;
    }

    @Override
    protected Iterator<Object> streamInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        Map<String, Object> formattedInputs = formatInputs(inputs, kwargs);
        Object result = invokeFunction(formattedInputs, kwargs);
        if (result instanceof Iterator<?> iterator) {
            return castIterator(iterator);
        }
        if (result instanceof Iterable<?> iterable) {
            return castIterator(iterable.iterator());
        }
        throw ErrorHelper.buildError(StatusCode.TOOL_LOCAL_FUNCTION_EXECUTION_ERROR,
                "method", "stream",
                "reason", "func is not generator",
                "card", String.valueOf(getCard()));
    }

    /**
     * Invokes the wrapped function within a saved session context that is
     * restored after execution, so parallel tool calls do not clobber the
     * session bound to the calling thread.
     */
    private Object invokeFunction(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        Object session = kwargs != null ? kwargs.get("session") : null;
        Object previousSession = SessionContextHolder.getCurrentSession();
        try {
            if (session != null) {
                SessionContextHolder.setCurrentSession(session);
            }
            if (contextFunc != null) {
                return awaitIfNeeded(contextFunc.apply(inputs, kwargs != null ? kwargs : Map.of()));
            }
            return awaitIfNeeded(func.apply(inputs));
        } finally {
            SessionContextHolder.restoreCurrentSession(previousSession);
        }
    }

    private Map<String, Object> formatInputs(Map<String, Object> inputs, Map<String, Object> kwargs) {
        Map<String, Object> inputParams = getCard().getInputParams();
        if (inputParams == null) {
            return inputs;
        }
        triggerCallback(ToolCallEvents.TOOL_PARSE_STARTED, parseStartedKwargs(inputs, inputParams));
        boolean skipNoneValue = isTrue(kwargs, "skip_none_value");
        boolean skipValidate = isTrue(kwargs, "skip_inputs_validate");
        Map<String, Object> formattedInputs = SchemaUtils.formatWithSchema(inputs, inputParams, skipNoneValue,
                skipValidate);
        triggerCallback(ToolCallEvents.TOOL_PARSE_FINISHED, parseFinishedKwargs(formattedInputs));
        return formattedInputs;
    }

    private Map<String, Object> parseStartedKwargs(Map<String, Object> inputs, Map<String, Object> inputParams) {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("tool_name", getCard().getName());
        kwargs.put("tool_id", getCard().getId());
        kwargs.put("raw_inputs", inputs);
        kwargs.put("schema", inputParams);
        return kwargs;
    }

    private Map<String, Object> parseFinishedKwargs(Map<String, Object> formattedInputs) {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("tool_name", getCard().getName());
        kwargs.put("tool_id", getCard().getId());
        kwargs.put("formatted_inputs", formattedInputs);
        return kwargs;
    }

    private static boolean isTrue(Map<String, Object> kwargs, String key) {
        return kwargs != null && Boolean.TRUE.equals(kwargs.get(key));
    }

    private static Object awaitIfNeeded(Object value) throws Exception {
        if (!(value instanceof CompletionStage<?> stage)) {
            return value;
        }
        try {
            return stage.toCompletableFuture().get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (ExecutionException | CompletionException executionError) {
            Throwable cause = executionError.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(Objects.requireNonNullElse(cause, executionError));
        }
    }

    @SuppressWarnings("unchecked")
    private static Iterator<Object> castIterator(Iterator<?> iterator) {
        return (Iterator<Object>) iterator;
    }

    /**
     * Context-aware local function signature.
     */
    @FunctionalInterface
    public interface ContextFunction {
        Object apply(Map<String, Object> inputs, Map<String, Object> kwargs);
    }
}
