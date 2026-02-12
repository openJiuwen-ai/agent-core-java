// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.schema;

import java.util.List;
import java.util.Map;

/**
 * Controller Output.
 *
 * <p>Batch processing output result, containing type, data list, and input event ID.
 * Used for non-streaming output scenarios, returning all results at once.
 *
 * <p>Supports two data modes:
 * <ul>
 *   <li>Chunk list: {@link #getChunks()} returns a list of {@link ControllerOutputChunk}</li>
 *   <li>Dictionary data: {@link #getDictData()} returns a Map</li>
 * </ul>
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class ControllerOutput {

    private final String type;
    private final List<ControllerOutputChunk> chunks;
    private final Map<String, Object> dictData;
    private final String inputEventId;

    /**
     * Constructor with chunk list data.
     *
     * @param type         the output type
     * @param chunks       the output chunks
     * @param inputEventId the associated input event ID (can be null)
     */
    public ControllerOutput(String type, List<ControllerOutputChunk> chunks, String inputEventId) {
        this.type = type;
        this.chunks = chunks;
        this.dictData = null;
        this.inputEventId = inputEventId;
    }

    /**
     * Constructor with dict data.
     *
     * @param type         the output type
     * @param dictData     the dictionary data
     * @param inputEventId the associated input event ID (can be null)
     */
    public ControllerOutput(String type, Map<String, Object> dictData, String inputEventId) {
        this.type = type;
        this.chunks = null;
        this.dictData = dictData;
        this.inputEventId = inputEventId;
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
     * Gets the output chunks (if data is chunk-based).
     *
     * @return the chunks list, or null if dict-based
     */
    public List<ControllerOutputChunk> getChunks() {
        return chunks;
    }

    /**
     * Gets the dictionary data (if data is dict-based).
     *
     * @return the dict data, or null if chunk-based
     */
    public Map<String, Object> getDictData() {
        return dictData;
    }

    /**
     * Gets the associated input event ID.
     *
     * @return the input event ID, or null
     */
    public String getInputEventId() {
        return inputEventId;
    }
}

