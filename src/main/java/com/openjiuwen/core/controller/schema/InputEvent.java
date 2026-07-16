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
 * 
 * @since 0.1.7
 */
public class InputEvent extends Event {
    private List<DataFrame> inputData;

    /**
     * InputEvent.
     * 
     * @since 0.1.7
     */
    public InputEvent() {
        super(EventType.INPUT);
        this.inputData = new ArrayList<>();
    }

    /**
     * InputEvent.
     * 
     * @param inputData inputData
     * @since 0.1.7
     */
    public InputEvent(List<DataFrame> inputData) {
        super(EventType.INPUT);
        this.inputData = inputData != null ? inputData : new ArrayList<>();
    }

    /**
     * getInputData.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<DataFrame> getInputData() {
        return inputData;
    }

    /**
     * setInputData.
     * 
     * @param inputData inputData
     * @since 0.1.7
     */
    public void setInputData(List<DataFrame> inputData) {
        this.inputData = inputData != null ? inputData : new ArrayList<>();
    }

    /**
     * fromUserInput.
     * 
     * @param userInput userInput
     * @return the result
     * @since 0.1.7
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
            return new InputEvent(List.of(new DataFrame.JsonDataFrame((java.util.Map<String, Object>) map)));
        }
        throw new IllegalArgumentException("Unsupported user input type: " + userInput.getClass().getName()
                + ". Must be String, Map, or InputEvent.");
    }
}
