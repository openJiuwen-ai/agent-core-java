/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User input event containing input data.
 * <p>
 * This is the main input type for the controller, used to receive user requests.
 * <p>
 * Mirrors Python's {@code InputEvent} in
 * {@code openjiuwen/core/controller/schema/event.py}.
 */
public class InputEvent extends Event {

    @JsonProperty("input_data")
    private List<DataFrame> inputData;

    public InputEvent() {
        super(EventType.INPUT);
        this.inputData = new ArrayList<>();
    }

    public InputEvent(List<DataFrame> inputData) {
        super(EventType.INPUT);
        this.inputData = inputData != null ? inputData : new ArrayList<>();
    }

    public List<DataFrame> getInputData() {
        return inputData;
    }

    public void setInputData(List<DataFrame> inputData) {
        this.inputData = inputData != null ? inputData : new ArrayList<>();
    }

    /**
     * Create input event from user input.
     *
     * @param userInput user input (String, Map, InteractiveInput, or InputEvent)
     * @return InputEvent instance
     */
    @SuppressWarnings("unchecked")
    public static InputEvent fromUserInput(Object userInput) {
        if (userInput instanceof InputEvent inputEvent) {
            return inputEvent;
        }
        if (userInput instanceof String text) {
            return new InputEvent(List.of(new DataFrame.TextDataFrame(text)));
        }
        if (userInput instanceof Map<?, ?> map) {
            return new InputEvent(List.of(new DataFrame.JsonDataFrame((Map<String, Object>) map)));
        }
        if (userInput instanceof InteractiveInput interactiveInput) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("query", interactiveInput);
            return new InputEvent(List.of(new DataFrame.JsonDataFrame(data)));
        }
        throw new IllegalArgumentException(
                "Unsupported user input type: "
                        + (userInput == null ? "null" : userInput.getClass().getName())
                        + ". Must be String, Map, InteractiveInput, or InputEvent.");
    }
}
