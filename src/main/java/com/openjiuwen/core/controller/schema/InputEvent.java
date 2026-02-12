// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Input Event.
 *
 * <p>User input event containing input data.
 * This is the main input type for the controller, used to receive user requests.
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class InputEvent extends Event {

    private final List<BaseDataFrame> inputData;

    /**
     * Default constructor with empty input data.
     */
    public InputEvent() {
        this(new ArrayList<>());
    }

    /**
     * Constructor with input data.
     *
     * @param inputData the input data list
     */
    public InputEvent(List<BaseDataFrame> inputData) {
        super(EventType.INPUT);
        this.inputData = inputData != null ? new ArrayList<>(inputData) : new ArrayList<>();
    }

    /**
     * Full constructor with all event fields.
     *
     * @param eventId   the event ID
     * @param metadata  the metadata
     * @param inputData the input data list
     */
    public InputEvent(String eventId, Map<String, Object> metadata, List<BaseDataFrame> inputData) {
        super(EventType.INPUT, eventId, metadata);
        this.inputData = inputData != null ? new ArrayList<>(inputData) : new ArrayList<>();
    }

    /**
     * Gets the input data.
     *
     * @return the input data list
     */
    public List<BaseDataFrame> getInputData() {
        return inputData;
    }

    /**
     * Create input event from user input.
     *
     * <p>Convenience method to convert user input to InputEvent.
     *
     * @param userInput the user input (String, Map, or InputEvent)
     * @return the InputEvent
     * @throws IllegalArgumentException if the input type is unsupported
     */
    @SuppressWarnings("unchecked")
    public static InputEvent fromUserInput(Object userInput) {
        if (userInput instanceof InputEvent ie) {
            return ie;
        }

        if (userInput instanceof String s) {
            return new InputEvent(List.of(new TextDataFrame(s)));
        }

        if (userInput instanceof Map<?, ?> m) {
            return new InputEvent(List.of(new JsonDataFrame((Map<String, Object>) m)));
        }

        throw new IllegalArgumentException(
            "Unsupported user input type: " + userInput.getClass().getName()
                + ". Must be String, Map, or InputEvent."
        );
    }
}

