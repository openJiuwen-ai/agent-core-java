/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

import com.openjiuwen.core.session.stream.OutputSchema;

/**
 * Controller output chunk for streaming output.
 * <p>
 * A single data chunk in streaming output, containing index, type, payload,
 * and a flag indicating whether it's the last chunk.
 * <p>
 * Extends {@link OutputSchema} for compatibility with the session stream system.
 * <p>
 * Mirrors Python's {@code ControllerOutputChunk(OutputSchema)}.
 * 
 * @since 0.1.7
 */
public class ControllerOutputChunk extends OutputSchema {
    /**
     * CONTROLLER_OUTPUT_TYPE.
     * 
     * @since 0.1.7
     */
    public static final String CONTROLLER_OUTPUT_TYPE = "controller_output";

    private ControllerOutputPayload controllerPayload;
    private boolean lastChunk;

    /**
     * ControllerOutputChunk.
     * 
     * @since 0.1.7
     */
    public ControllerOutputChunk() {
        setType(CONTROLLER_OUTPUT_TYPE);
    }

    /**
     * ControllerOutputChunk.
     * 
     * @param index index
     * @param payload payload
     * @since 0.1.7
     */
    public ControllerOutputChunk(int index, ControllerOutputPayload payload) {
        setType(CONTROLLER_OUTPUT_TYPE);
        setIndex(index);
        this.controllerPayload = payload;
        setPayload(payload);
    }

    /**
     * ControllerOutputChunk.
     * 
     * @param index index
     * @param payload payload
     * @param lastChunk lastChunk
     * @since 0.1.7
     */
    public ControllerOutputChunk(int index, ControllerOutputPayload payload, boolean lastChunk) {
        this(index, payload);
        this.lastChunk = lastChunk;
    }

    /**
     * getControllerPayload.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ControllerOutputPayload getControllerPayload() {
        return controllerPayload;
    }

    /**
     * setControllerPayload.
     * 
     * @param payload payload
     * @since 0.1.7
     */
    public void setControllerPayload(ControllerOutputPayload payload) {
        this.controllerPayload = payload;
        setPayload(payload);
    }

    /**
     * isLastChunk.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isLastChunk() {
        return lastChunk;
    }

    /**
     * setLastChunk.
     * 
     * @param lastChunk lastChunk
     * @since 0.1.7
     */
    public void setLastChunk(boolean lastChunk) {
        this.lastChunk = lastChunk;
    }
}
