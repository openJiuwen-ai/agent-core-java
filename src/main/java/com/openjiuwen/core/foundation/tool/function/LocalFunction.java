/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.function;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.utils.SchemaUtils;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.session.SessionContextHolder;

import java.util.Iterator;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Local function tool that wraps a Java {@link Function} as a tool.
 * <p>
 * Mirrors Python's {@code LocalFunction} class. The wrapped function
 * receives input as a {@code Map<String, Object>} and returns the result.
 *
 * <p>Usage:
 * <pre>
 *   ToolCard card = ToolCard.builder().name("add").description("Add two numbers").build();
 *   LocalFunction tool = new LocalFunction(card, inputs -> {
 *       int a = (int) inputs.get("a");
 *       int b = (int) inputs.get("b");
 *       return a + b;
 *   });
 *   Object result = tool.invoke(Map.of("a", 1, "b", 2));
 * </pre>
 *
 * @since 0.1.7
 */
public class LocalFunction extends Tool {

    private final Function<Map<String, Object>, Object> func;
    private final ContextFunction contextFunc;

    /**
     * Create a local function tool.
     *
     * @param card the tool card configuration
     * @param func the function to wrap; must not be null
     */
    public LocalFunction(ToolCard card, Function<Map<String, Object>, Object> func) {
        super(card);
        if (func == null) {
            throw ErrorHelper.buildError(StatusCode.TOOL_LOCAL_FUNCTION_FUNC_NOT_SUPPORTED,
                    "card", card.toString());
        }
        this.func = func;
        this.contextFunc = null;
    }

    /**
     * Create a local function tool that can access execution kwargs such as {@code session}.
     *
     * @param card        the tool card configuration
     * @param contextFunc the context-aware function to wrap
     */
    public LocalFunction(ToolCard card, ContextFunction contextFunc) {
        super(card);
        if (contextFunc == null) {
            throw ErrorHelper.buildError(StatusCode.TOOL_LOCAL_FUNCTION_FUNC_NOT_SUPPORTED,
                    "card", card.toString());
        }
        this.func = null;
        this.contextFunc = contextFunc;
    }

    /**
     * Invoke the wrapped function once with validated inputs.
     *
     * @param inputs tool inputs
     * @param kwargs runtime keyword arguments
     * @return tool result
     * @throws Exception when tool execution fails
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        Map<String, Object> validatedInputs = validateInputs(inputs, kwargs);
        return invokeFunction(validatedInputs, kwargs);
    }

    /**
     * Invoke the wrapped streaming function.
     *
     * @param inputs tool inputs
     * @param kwargs runtime keyword arguments
     * @return streaming iterator
     * @throws Exception when the wrapped function is not stream-capable
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        Map<String, Object> validatedInputs = validateInputs(inputs, kwargs);
        Object result = invokeFunction(validatedInputs, kwargs);

        // If the function returns an Iterator, yield it directly
        if (result instanceof Iterator<?> iterator) {
            @SuppressWarnings("unchecked")
            Iterator<Object> typedIterator = (Iterator<Object>) iterator;
            return typedIterator;
        }

        // If the function returns an Iterable, convert to iterator
        if (result instanceof Iterable<?> iterable) {
            @SuppressWarnings("unchecked")
            Iterator<Object> typedIterator = ((Iterable<Object>) iterable).iterator();
            return typedIterator;
        }

        // Non-streaming function: throw error instead of silently wrapping
        throw ErrorHelper.buildError(StatusCode.TOOL_LOCAL_FUNCTION_EXECUTION_ERROR,
                "method", "stream", "reason", "func is not a streaming function (must return Iterator or Iterable)",
                "card", getCard().toString());
    }

    /**
     * Get the underlying function.
     *
     * @return plain function implementation
     */
    public Function<Map<String, Object>, Object> getFunc() {
        return func;
    }

    /**
     * Get the context-aware function variant.
     *
     * @return context-aware function implementation
     */
    public ContextFunction getContextFunc() {
        return contextFunc;
    }

    /**
     * Validate and format inputs against the tool card's input schema.
     */
    private Map<String, Object> validateInputs(Map<String, Object> inputs, Map<String, Object> kwargs) {
        Map<String, Object> inputParams = getCard().getInputParams();
        if (inputParams != null && !inputParams.isEmpty()) {
            boolean skipNoneValue = kwargs != null && Boolean.TRUE.equals(kwargs.get("skip_none_value"));
            boolean skipValidate = kwargs != null && Boolean.TRUE.equals(kwargs.get("skip_inputs_validate"));
            return SchemaUtils.formatWithSchema(inputs, inputParams, skipNoneValue, skipValidate);
        }
        return inputs;
    }

    private Object invokeFunction(Map<String, Object> inputs, Map<String, Object> kwargs) {
        Object session = kwargs != null ? kwargs.get("session") : null;
        try {
            if (session instanceof com.openjiuwen.core.session.Session) {
                SessionContextHolder.setCurrentSession((com.openjiuwen.core.session.Session) session);
            }
            if (contextFunc != null) {
                return contextFunc.apply(inputs, kwargs != null ? kwargs : Map.<String, Object>of());
            }
            return func.apply(inputs);
        } finally {
            SessionContextHolder.clearCurrentSession();
        }
    }

    /**
     * Context-aware local function signature.
     *
     * @since 0.1.7
     */
    @FunctionalInterface
    public interface ContextFunction extends BiFunction<Map<String, Object>, Map<String, Object>, Object> {
    }
}
