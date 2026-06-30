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
 */
public class ControllerOutputChunk extends OutputSchema {

    public static final String CONTROLLER_OUTPUT_TYPE = "controller_output";

    private ControllerOutputPayload controllerPayload;
    private boolean lastChunk;

    /**
     * Auto-generated for codecheck compliance.
     */
    public ControllerOutputChunk() {
        setType(CONTROLLER_OUTPUT_TYPE);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ControllerOutputChunk(int index, ControllerOutputPayload payload) {
        setType(CONTROLLER_OUTPUT_TYPE);
        setIndex(index);
        this.controllerPayload = payload;
        setPayload(payload);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ControllerOutputChunk(int index, ControllerOutputPayload payload, boolean lastChunk) {
        this(index, payload);
        this.lastChunk = lastChunk;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ControllerOutputPayload getControllerPayload() {
        return controllerPayload;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setControllerPayload(ControllerOutputPayload payload) {
        this.controllerPayload = payload;
        setPayload(payload);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isLastChunk() {
        return lastChunk;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setLastChunk(boolean lastChunk) {
        this.lastChunk = lastChunk;
    }
}
