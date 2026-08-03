/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Follow-up event for continuing the task loop with new input.
 *
 * <p>Mirrors Python's {@code FollowUpEvent} in
 * {@code openjiuwen/core/controller/schema/event.py}.</p>
 */
public class FollowUpEvent extends Event {

    @JsonProperty("input_data")
    private List<DataFrame> inputData;

    public FollowUpEvent() {
        super(EventType.FOLLOW_UP);
        this.inputData = new ArrayList<>();
    }

    public FollowUpEvent(List<DataFrame> inputData) {
        super(EventType.FOLLOW_UP);
        this.inputData = inputData != null ? inputData : new ArrayList<>();
    }

    public List<DataFrame> getInputData() {
        return inputData;
    }

    public void setInputData(List<DataFrame> inputData) {
        this.inputData = inputData != null ? inputData : new ArrayList<>();
    }

    /**
     * Create a follow-up event from text.
     *
     * @param text follow-up message text
     * @return follow-up event instance
     */
    public static FollowUpEvent fromText(String text) {
        return new FollowUpEvent(List.of(new DataFrame.TextDataFrame(text)));
    }
}
