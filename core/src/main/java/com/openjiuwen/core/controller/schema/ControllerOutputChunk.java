/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.session.stream.OutputSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Controller output chunk for streaming output.
 * <p>
 * A single data chunk in streaming output, containing index, type, payload,
 * and a flag indicating whether it's the last chunk.
 * <p>
 * Extends {@link OutputSchema} for compatibility with the session stream system.
 * <p>
 * Mirrors Python's {@code ControllerOutputChunk} in
 * {@code openjiuwen/core/controller/schema/controller_output.py}.
 */
public class ControllerOutputChunk extends OutputSchema {

    public static final String CONTROLLER_OUTPUT_TYPE = "controller_output";

    private boolean lastChunk;

    public ControllerOutputChunk() {
        setType(CONTROLLER_OUTPUT_TYPE);
    }

    public ControllerOutputChunk(int index, ControllerOutputPayload payload) {
        setType(CONTROLLER_OUTPUT_TYPE);
        setIndex(index);
        setPayload(payload);
    }

    public ControllerOutputChunk(int index, ControllerOutputPayload payload, boolean lastChunk) {
        this(index, payload);
        this.lastChunk = lastChunk;
    }

    @Override
    @JsonProperty("payload")
    public ControllerOutputPayload getPayload() {
        Object payload = super.getPayload();
        if (payload == null) {
            return null;
        }
        if (payload instanceof ControllerOutputPayload controllerOutputPayload) {
            return controllerOutputPayload;
        }
        throw new IllegalStateException("payload is not a ControllerOutputPayload: " + payload.getClass().getName());
    }

    @Override
    @JsonProperty("payload")
    public void setPayload(Object payload) {
        super.setPayload(coercePayload(payload));
    }

    @JsonIgnore
    public ControllerOutputPayload getControllerPayload() {
        return getPayload();
    }

    @JsonIgnore
    public void setControllerPayload(ControllerOutputPayload payload) {
        setPayload(payload);
    }

    @JsonProperty("last_chunk")
    public boolean isLastChunk() {
        return lastChunk;
    }

    @JsonProperty("last_chunk")
    public void setLastChunk(boolean lastChunk) {
        this.lastChunk = lastChunk;
    }

    @SuppressWarnings("unchecked")
    private static ControllerOutputPayload coercePayload(Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof ControllerOutputPayload controllerOutputPayload) {
            return controllerOutputPayload;
        }
        if (!(payload instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("payload must be a ControllerOutputPayload or map");
        }
        Object rawType = map.get("type");
        String type = rawType != null ? String.valueOf(rawType) : null;
        List<DataFrame> data = coerceDataFrames(map.get("data"));
        Object rawMetadata = map.get("metadata");
        Map<String, Object> metadata = null;
        if (rawMetadata instanceof Map<?, ?> metadataMap) {
            metadata = (Map<String, Object>) metadataMap;
        } else if (rawMetadata != null) {
            throw new IllegalArgumentException("payload metadata must be a map when provided");
        }
        return new ControllerOutputPayload(type, data, metadata);
    }

    private static List<DataFrame> coerceDataFrames(Object rawData) {
        if (rawData == null) {
            return new ArrayList<>();
        }
        if (!(rawData instanceof List<?> rawList)) {
            throw new IllegalArgumentException("payload data must be a list");
        }
        List<DataFrame> frames = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            frames.add(coerceDataFrame(item));
        }
        return frames;
    }

    @SuppressWarnings("unchecked")
    private static DataFrame coerceDataFrame(Object item) {
        if (item instanceof DataFrame dataFrame) {
            return dataFrame;
        }
        if (!(item instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("payload data items must be DataFrame values or maps");
        }
        String type = String.valueOf(map.get("type"));
        return switch (type) {
            case "text" -> new DataFrame.TextDataFrame(String.valueOf(map.get("text")));
            case "json" -> new DataFrame.JsonDataFrame((Map<String, Object>) map.get("data"));
            case "file" -> new DataFrame.FileDataFrame(
                    String.valueOf(map.get("name")),
                    String.valueOf(map.get("mimeType")),
                    coerceBytes(map.get("bytes")),
                    map.get("uri") != null ? String.valueOf(map.get("uri")) : null
            );
            default -> throw new IllegalArgumentException("Unsupported dataframe type: " + type);
        };
    }

    private static byte[] coerceBytes(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        throw new IllegalArgumentException("file dataframe bytes must be byte[] when provided");
    }
}
