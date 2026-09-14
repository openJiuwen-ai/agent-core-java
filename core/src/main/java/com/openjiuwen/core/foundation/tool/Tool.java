/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.callback.CallbackDecorators;
import com.openjiuwen.core.runner.callback.DecoratorFramework;
import com.openjiuwen.core.runner.callback.ToolCallEvents;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Base class for tool implementations.
 *
 * <p>Mirrors Python's {@code Tool} in
 * {@code openjiuwen/core/foundation/tool/base.py}.</p>
 */
public abstract class Tool {

    private static DecoratorFramework callbackFramework;

    private final ToolCard card;

    public Tool(ToolCard card) {
        if (card == null) {
            throw toolCardInvalid(null, "card is None");
        }
        if (card.getId() == null || card.getId().isEmpty()) {
            throw toolCardInvalid(card, "card id is None or empty");
        }
        this.card = card;
    }

    public static void setCallbackFramework(DecoratorFramework framework) {
        callbackFramework = framework;
    }

    public static void clearCallbackFramework() {
        callbackFramework = null;
    }

    public ToolCard getCard() {
        return card;
    }

    public ToolCard card() {
        return card;
    }

    /**
     * Execute the tool with provided inputs and return final result.
     *
     * @param inputs structured input data conforming to the tool's input schema
     * @param kwargs additional execution parameters
     * @return the complete result of tool execution
     * @throws Exception when the underlying tool execution fails
     */
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        DecoratorFramework framework = callbackFramework;
        InvocationInput request = prepareInput(framework, ToolCallEvents.TOOL_INVOKE_INPUT, inputs, kwargs);
        trigger(framework, ToolCallEvents.TOOL_INVOKE_INPUT, request.args(), merge(request.kwargs(), toolExtra()));
        trigger(framework, ToolCallEvents.TOOL_CALL_STARTED, request.args(), lifecycleKwargs(request));
        Object result;
        try {
            result = invokeInternal(request.inputs(), request.userKwargs());
            trigger(framework, ToolCallEvents.TOOL_CALL_FINISHED, request.args(), lifecycleKwargs(request, result));
        } catch (Exception exception) {
            trigger(framework, ToolCallEvents.TOOL_CALL_ERROR, request.args(), lifecycleErrorKwargs(exception));
            throw exception;
        }
        Object transformed = transformOutput(framework, ToolCallEvents.TOOL_INVOKE_OUTPUT, result);
        Map<String, Object> outputKwargs = resultKwargs(transformed);
        outputKwargs.putAll(toolExtra());
        trigger(framework, ToolCallEvents.TOOL_INVOKE_OUTPUT, new Object[0], outputKwargs);
        return transformed;
    }

    public Object invoke(Map<String, Object> inputs) throws Exception {
        return invoke(inputs, Map.of());
    }

    /**
     * Execute the tool and stream incremental results.
     *
     * @param inputs structured input data conforming to the tool's input schema
     * @param kwargs additional execution parameters
     * @return an iterator of incremental results
     * @throws Exception when stream creation fails
     */
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        DecoratorFramework framework = callbackFramework;
        InvocationInput request = prepareInput(framework, ToolCallEvents.TOOL_STREAM_INPUT, inputs, kwargs);
        trigger(framework, ToolCallEvents.TOOL_STREAM_INPUT, request.args(), merge(request.kwargs(), toolExtra()));
        trigger(framework, ToolCallEvents.TOOL_CALL_STARTED, request.args(), lifecycleKwargs(request));
        Iterator<Object> delegate;
        try {
            delegate = streamInternal(request.inputs(), request.userKwargs());
        } catch (Exception exception) {
            trigger(framework, ToolCallEvents.TOOL_CALL_ERROR, request.args(), lifecycleErrorKwargs(exception));
            throw exception;
        }
        Iterator<Object> safeDelegate = delegate != null ? delegate : Collections.emptyIterator();
        return new CallbackIterator(safeDelegate, framework, request);
    }

    public Iterator<Object> stream(Map<String, Object> inputs) throws Exception {
        return stream(inputs, Map.of());
    }

    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        throw ErrorHelper.buildError(
                StatusCode.TOOL_INVOKE_NOT_SUPPORTED,
                "card",
                String.valueOf(card)
        );
    }

    protected Iterator<Object> streamInternal(Map<String, Object> inputs, Map<String, Object> kwargs)
            throws Exception {
        throw ErrorHelper.buildError(
                StatusCode.TOOL_STREAM_NOT_SUPPORTED,
                "card",
                String.valueOf(card)
        );
    }

    protected final void triggerCallback(String event, Map<String, Object> kwargs) {
        trigger(callbackFramework, event, new Object[0], kwargs);
    }

    private static RuntimeException toolCardInvalid(ToolCard card, String reason) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("card", card);
        params.put("reason", reason);
        return ErrorHelper.buildError(StatusCode.TOOL_CARD_INVALID, null, null, null, params);
    }

    private InvocationInput prepareInput(DecoratorFramework framework, String event,
                                         Map<String, Object> inputs, Map<String, Object> kwargs) {
        Map<String, Object> safeInputs = copyMap(inputs);
        Map<String, Object> userKwargs = copyMap(kwargs);
        Map<String, Object> eventKwargs = callKwargs(safeInputs, userKwargs);
        Object[] args = new Object[]{safeInputs};
        if (framework == null) {
            return new InvocationInput(safeInputs, userKwargs, eventKwargs, args);
        }

        Object transformed = framework.triggerTransform(event, args, merge(eventKwargs, toolExtra()));
        if (transformed instanceof CallbackDecorators.BoundArgs boundArgs) {
            return fromBoundArgs(boundArgs, safeInputs, userKwargs);
        }
        if (transformed instanceof Map<?, ?> map) {
            return fromTransformedMap(map, safeInputs, userKwargs);
        }
        return new InvocationInput(safeInputs, userKwargs, eventKwargs, args);
    }

    @SuppressWarnings("unchecked")
    private InvocationInput fromBoundArgs(CallbackDecorators.BoundArgs boundArgs,
                                          Map<String, Object> fallbackInputs,
                                          Map<String, Object> fallbackKwargs) {
        Object[] args = boundArgs.getArgs();
        Map<String, Object> transformedKwargs = boundArgs.getKwargs();
        Map<String, Object> resolvedInputs = fallbackInputs;
        if (args.length > 0 && args[0] instanceof Map<?, ?> inputMap) {
            resolvedInputs = stringObjectMap(inputMap);
        } else if (transformedKwargs.get("inputs") instanceof Map<?, ?> inputMap) {
            resolvedInputs = stringObjectMap(inputMap);
        }
        Map<String, Object> resolvedUserKwargs = transformedKwargs.get("kwargs") instanceof Map<?, ?> kwargsMap
                ? stringObjectMap(kwargsMap)
                : fallbackKwargs;
        return new InvocationInput(resolvedInputs, resolvedUserKwargs,
                callKwargs(resolvedInputs, resolvedUserKwargs), new Object[]{resolvedInputs});
    }

    private InvocationInput fromTransformedMap(Map<?, ?> map,
                                               Map<String, Object> fallbackInputs,
                                               Map<String, Object> fallbackKwargs) {
        Map<String, Object> transformed = stringObjectMap(map);
        Map<String, Object> resolvedInputs = transformed.get("inputs") instanceof Map<?, ?> inputMap
                ? stringObjectMap(inputMap)
                : fallbackInputs;
        Map<String, Object> resolvedUserKwargs = transformed.get("kwargs") instanceof Map<?, ?> kwargsMap
                ? stringObjectMap(kwargsMap)
                : fallbackKwargs;
        return new InvocationInput(resolvedInputs, resolvedUserKwargs,
                callKwargs(resolvedInputs, resolvedUserKwargs), new Object[]{resolvedInputs});
    }

    private Object transformOutput(DecoratorFramework framework, String event, Object result) {
        if (framework == null) {
            return result;
        }
        Map<String, Object> outputKwargs = new LinkedHashMap<>();
        outputKwargs.put("result", result);
        outputKwargs.putAll(toolExtra());
        Object transformed = framework.triggerTransform(event, new Object[0], outputKwargs);
        return transformed == null || transformed == CallbackDecorators.TRANSFORM_NOOP ? result : transformed;
    }

    private Map<String, Object> lifecycleKwargs(InvocationInput request) {
        Map<String, Object> values = lifecycleBase();
        values.put("inputs", List.of(request.args(), request.userKwargs()));
        return values;
    }

    private Map<String, Object> lifecycleKwargs(InvocationInput request, Object result) {
        Map<String, Object> values = lifecycleKwargs(request);
        values.put("result", result);
        return values;
    }

    private Map<String, Object> lifecycleResultKwargs(InvocationInput request, Object result) {
        Map<String, Object> values = lifecycleKwargs(request);
        values.put("result", result);
        return values;
    }

    private Map<String, Object> lifecycleErrorKwargs(Throwable exception) {
        Map<String, Object> values = lifecycleBase();
        values.put("error", exception);
        return values;
    }

    private Map<String, Object> lifecycleBase() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tool_name", card.getName());
        values.put("tool_id", card.getId());
        return values;
    }

    private Map<String, Object> toolExtra() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tool_name", card.getName());
        values.put("tool_info", card.toolInfo());
        return values;
    }

    private static void trigger(DecoratorFramework framework, String event, Object[] args, Map<String, Object> kwargs) {
        if (framework != null) {
            framework.trigger(event, args != null ? args : new Object[0], kwargs != null ? kwargs : Map.of());
        }
    }

    private static Map<String, Object> callKwargs(Map<String, Object> inputs, Map<String, Object> kwargs) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("inputs", inputs);
        values.put("kwargs", kwargs);
        values.put("_args", new Object[]{inputs});
        return values;
    }

    private static Map<String, Object> merge(Map<String, Object> first, Map<String, Object> second) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (first != null) {
            values.putAll(first);
        }
        if (second != null) {
            values.putAll(second);
        }
        return values;
    }

    private static Map<String, Object> resultKwargs(Object result) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("result", result);
        return values;
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        return source != null ? new LinkedHashMap<>(source) : new LinkedHashMap<>();
    }

    private static Map<String, Object> stringObjectMap(Map<?, ?> source) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (source != null) {
            source.forEach((key, value) -> values.put(String.valueOf(key), value));
        }
        return values;
    }

    /**
     * Mirrors Python's {@code _ToolMeta} stream lifecycle wrapper in
     * {@code openjiuwen/core/foundation/tool/base.py}.
     */
    private final class CallbackIterator implements Iterator<Object> {
        private final Iterator<Object> delegate;
        private final DecoratorFramework framework;
        private final InvocationInput request;
        private boolean finished;

        private CallbackIterator(Iterator<Object> delegate, DecoratorFramework framework, InvocationInput request) {
            this.delegate = delegate;
            this.framework = framework;
            this.request = request;
        }

        @Override
        public boolean hasNext() {
            if (finished) {
                return false;
            }
            boolean hasNext;
            try {
                hasNext = delegate.hasNext();
            } catch (RuntimeException exception) {
                emitError(exception);
                throw exception;
            }
            if (!hasNext) {
                emitFinished();
            }
            return hasNext;
        }

        @Override
        public Object next() {
            if (finished) {
                throw new NoSuchElementException();
            }
            Object chunk;
            try {
                chunk = delegate.next();
            } catch (RuntimeException exception) {
                emitError(exception);
                throw exception;
            }
            trigger(framework, ToolCallEvents.TOOL_RESULT_RECEIVED, request.args(),
                    lifecycleResultKwargs(request, chunk));
            Object transformed = transformOutput(framework, ToolCallEvents.TOOL_STREAM_OUTPUT, chunk);
            trigger(framework, ToolCallEvents.TOOL_STREAM_OUTPUT, new Object[0],
                    merge(resultKwargs(transformed), toolExtra()));
            return transformed;
        }

        private void emitFinished() {
            if (!finished) {
                trigger(framework, ToolCallEvents.TOOL_CALL_FINISHED, request.args(), lifecycleBase());
                finished = true;
            }
        }

        private void emitError(RuntimeException exception) {
            if (!finished) {
                trigger(framework, ToolCallEvents.TOOL_CALL_ERROR, request.args(), lifecycleErrorKwargs(exception));
                finished = true;
            }
        }
    }

    /**
     * Mirrors Python's {@code _ToolMeta} positional and keyword call payload in
     * {@code openjiuwen/core/foundation/tool/base.py}.
     */
    private record InvocationInput(Map<String, Object> inputs, Map<String, Object> userKwargs,
                                   Map<String, Object> kwargs, Object[] args) {
    }
}
