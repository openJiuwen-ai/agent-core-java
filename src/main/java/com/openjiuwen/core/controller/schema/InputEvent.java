  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.controller.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * User input event containing input data.
 * <p>
 * This is the main input type for the controller, used to receive user requests.
 * <p>
 * Mirrors Python's {@code InputEvent}.
 */
public class InputEvent extends Event {

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
     * @param userInput user input (String, Map, or InputEvent)
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
        if (userInput instanceof java.util.Map<?, ?> map) {
            return new InputEvent(List.of(
                    new DataFrame.JsonDataFrame((java.util.Map<String, Object>) map)));
        }
        throw new IllegalArgumentException(
                "Unsupported user input type: " + userInput.getClass().getName()
                        + ". Must be String, Map, or InputEvent.");
    }
}
