  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.foundation.tool.function;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.utils.SchemaUtils;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;

import java.util.Iterator;
import java.util.Map;
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
 */
public class LocalFunction extends Tool {

    private final Function<Map<String, Object>, Object> func;

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
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        Map<String, Object> validatedInputs = validateInputs(inputs, kwargs);
        return func.apply(validatedInputs);
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        Map<String, Object> validatedInputs = validateInputs(inputs, kwargs);
        Object result = func.apply(validatedInputs);

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
     */
    public Function<Map<String, Object>, Object> getFunc() {
        return func;
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
}
