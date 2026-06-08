/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Interactive input data carrying user inputs for interactions.
 * <p>
 * Mirrors Python's {@code InteractiveInput} in
 * {@code openjiuwen/core/session/interaction/interactive_input.py}.
 * </p>
 */
public class InteractiveInput {

    private Map<String, Object> userInputs;
    private Object rawInputs;

    public InteractiveInput() {
        this.userInputs = new LinkedHashMap<>();
        this.rawInputs = null;
    }

    public InteractiveInput(Object rawInputs) {
        if (rawInputs == null) {
            throw ErrorHelper.buildError(
                    StatusCode.INTERACTION_INPUT_INVALID,
                    "reason",
                    "value of raw_inputs is none"
            );
        }
        this.userInputs = new LinkedHashMap<>();
        this.rawInputs = rawInputs;
    }

    public Map<String, Object> getUserInputs() {
        return userInputs;
    }

    public void setUserInputs(Map<String, Object> userInputs) {
        this.userInputs = userInputs;
    }

    public Object getRawInputs() {
        return rawInputs;
    }

    public void setRawInputs(Object rawInputs) {
        this.rawInputs = rawInputs;
    }

    public void update(String nodeId, Object value) {
        if (rawInputs != null) {
            throw ErrorHelper.buildError(
                    StatusCode.INTERACTION_INPUT_INVALID,
                    "reason",
                    "raw_inputs existed, update is invalid"
            );
        }
        if (nodeId == null || value == null) {
            throw ErrorHelper.buildError(
                    StatusCode.INTERACTION_INPUT_INVALID,
                    "reason",
                    "value is none or node_id is none"
            );
        }
        userInputs.put(nodeId, value);
    }
}
