/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.BaseSession;

import java.util.Iterator;
import java.util.Map;

/**
 * Mirrors Python's {@code ExecutableGraph} in
 * {@code openjiuwen/core/graph/base.py}.
 *
 * @param <I> graph payload type stored under {@code inputs}
 * @param <O> graph output type
 */
public abstract class ExecutableGraph<I, O> extends Executable<Map<String, Object>, O> {

    /**
     * Invoke this graph by extracting the Python envelope keys.
     *
     * @param inputs input envelope containing {@code inputs} and optionally {@code config}
     * @param session execution session
     * @return graph output
     */
    @SuppressWarnings("unchecked")
    public O invoke(Map<String, Object> inputs, BaseSession session) {
        I actualInputs = (I) inputs.get(Constant.INPUTS_KEY);
        Object config = inputs.get(Constant.CONFIG_KEY);
        return invokeInternal(actualInputs, session, config);
    }

    /**
     * Python's {@code stream} pass body returns {@code None}.
     *
     * @param inputs input envelope
     * @param session execution session
     * @return null, matching Python's implicit {@code None}
     */
    public Iterator<O> stream(Map<String, Object> inputs, BaseSession session) {
        return null;
    }

    /**
     * Python's {@code collect} pass body returns {@code None}.
     *
     * @param inputs streaming inputs
     * @param session execution session
     * @return null, matching Python's implicit {@code None}
     */
    public O collect(Iterator<I> inputs, BaseSession session) {
        return null;
    }

    /**
     * Python's {@code transform} pass body returns {@code None}.
     *
     * @param inputs streaming inputs
     * @param session execution session
     * @return null, matching Python's implicit {@code None}
     */
    public Iterator<O> transform(Iterator<I> inputs, BaseSession session) {
        return null;
    }

    /**
     * Python's {@code interrupt} pass body has no visible result.
     *
     * @param message interrupt message
     */
    public void interrupt(Map<String, Object> message) {
        // Matches Python's pass body.
    }

    /**
     * Mirrors Python's abstract {@code _invoke} method.
     *
     * @param inputs extracted graph payload
     * @param session execution session
     * @param config optional graph config
     * @return graph output
     */
    protected abstract O invokeInternal(I inputs, BaseSession session, Object config);
}
