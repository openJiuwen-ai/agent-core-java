/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.BaseSession;

import java.util.Iterator;
import java.util.Map;

/**
 * An executable graph that wraps the standard invoke/stream/collect/transform
 * with config extraction from the input map.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.base.ExecutableGraph}.
 *
 * @param <I> input type (typically {@code Map<String, Object>})
 * @param <O> output type
 */
public abstract class ExecutableGraph<I, O> extends Executable<I, O> {

    /**
     * Invoke the graph. Extracts INPUTS_KEY and CONFIG_KEY from the inputs map.
     *
     * @param inputs  input map containing INPUTS_KEY and optionally CONFIG_KEY
     * @param session execution session
     * @return the output
     */
    @SuppressWarnings("unchecked")
    public O invoke(I inputs, BaseSession session) {
        Map<String, Object> inputMap = (Map<String, Object>) inputs;
        Object actualInputs = inputMap.get(Constant.INPUTS_KEY);
        Object config = inputMap.get(Constant.CONFIG_KEY);
        return doInvoke((I) actualInputs, session, config);
    }

    /**
     * Stream the graph output.
     *
     * @param inputs  input data
     * @param session execution session
     * @return an iterator of output chunks
     */
    public Iterator<O> stream(I inputs, BaseSession session) {
        return null;
    }

    /**
     * Collect from streaming inputs and produce a single output.
     *
     * @param inputs  streaming input iterator
     * @param session execution session
     * @return the collected output
     */
    public O collect(Iterator<I> inputs, BaseSession session) {
        return null;
    }

    /**
     * Transform streaming inputs to streaming outputs.
     *
     * @param inputs  streaming input iterator
     * @param session execution session
     * @return an iterator of transformed output chunks
     */
    public Iterator<O> transform(Iterator<I> inputs, BaseSession session) {
        return null;
    }

    /**
     * Handle interrupt messages.
     *
     * @param message interrupt message
     */
    public void interrupt(Map<String, Object> message) {
        // Default no-op
    }

    /**
     * Internal invoke implementation to be provided by subclasses.
     *
     * @param inputs  actual input data
     * @param session execution session
     * @param config  optional configuration
     * @return the output
     */
    protected abstract O doInvoke(I inputs, BaseSession session, Object config);
}
