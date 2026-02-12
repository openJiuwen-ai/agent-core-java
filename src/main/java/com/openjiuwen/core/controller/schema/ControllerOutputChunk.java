// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.schema;

/**
 * Controller Output Chunk.
 *
 * <p>A single data chunk in streaming output, containing index, type, payload,
 * and a flag indicating whether it's the last chunk. Used for streaming output
 * scenarios, supporting incremental return of processing results.
 *
 * <p>Note: In Python this extends OutputSchema (a pydantic BaseModel). In Java,
 * OutputSchema is a record and cannot be extended, so this class uses composition
 * instead, carrying the same fields (index, type, payload) directly.
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class ControllerOutputChunk {

    private final int index;
    private final String type;
    private final ControllerOutputPayload payload;
    private final boolean lastChunk;

    /**
     * Constructor with index only (defaults).
     *
     * @param index the output chunk index
     */
    public ControllerOutputChunk(int index) {
        this(index, "controller_output", null, false);
    }

    /**
     * Full constructor.
     *
     * @param index     the output chunk index
     * @param type      the output type (default "controller_output")
     * @param payload   the output payload (can be null)
     * @param lastChunk whether this is the last chunk
     */
    public ControllerOutputChunk(int index, String type, ControllerOutputPayload payload, boolean lastChunk) {
        this.index = index;
        this.type = type != null ? type : "controller_output";
        this.payload = payload;
        this.lastChunk = lastChunk;
    }

    /**
     * Gets the chunk index.
     *
     * @return the index
     */
    public int getIndex() {
        return index;
    }

    /**
     * Gets the output type.
     *
     * @return the type string
     */
    public String getType() {
        return type;
    }

    /**
     * Gets the payload.
     *
     * @return the payload, or null
     */
    public ControllerOutputPayload getPayload() {
        return payload;
    }

    /**
     * Whether this is the last chunk.
     *
     * @return true if this is the last chunk
     */
    public boolean isLastChunk() {
        return lastChunk;
    }
}

