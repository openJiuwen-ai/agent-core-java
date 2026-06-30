/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.BaseSession;

import java.util.List;

/**
 * Base class for interaction handling, managing interactive input queue.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.interaction.base.BaseInteraction}.
 */
public abstract class BaseInteraction {

    /**
     * Auto-generated for codecheck compliance.
     */
    protected List<Object> interactiveInputs;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected Object latestInteractiveInputs;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected int idx;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected final BaseSession session;

    /**
     * Auto-generated for codecheck compliance.
     */
    protected BaseInteraction(BaseSession session, Object defaultInput) {
        this.session = session;
        this.idx = 0;
        this.latestInteractiveInputs = null;

        if (defaultInput != null) {
            this.interactiveInputs = new java.util.ArrayList<>();
            this.interactiveInputs.add(defaultInput);
        } else {
            this.interactiveInputs = null;
        }

        initInteractiveInputs();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected BaseInteraction(BaseSession session) {
        this(session, null);
    }

    @SuppressWarnings("unchecked")
    private void initInteractiveInputs() {
        Object inputs = session.state().get(Constant.INTERACTIVE_INPUT);
        if (inputs instanceof List) {
            List<Object> inputList = (List<Object>) inputs;
            if (interactiveInputs != null) {
                List<Object> merged = new java.util.ArrayList<>(inputList);
                merged.addAll(interactiveInputs);
                interactiveInputs = merged;
            } else {
                interactiveInputs = new java.util.ArrayList<>(inputList);
            }
        }
        if (interactiveInputs != null && !interactiveInputs.isEmpty()) {
            java.util.Map<String, Object> updateMap = new java.util.HashMap<>();
            updateMap.put(Constant.INTERACTIVE_INPUT, new java.util.ArrayList<>(interactiveInputs));
            session.state().update(updateMap);
            latestInteractiveInputs = interactiveInputs.get(interactiveInputs.size() - 1);
        }
    }

    /**
     * Get the next interactive input from the queue.
     *
     * @return next input or null if exhausted
     */
    protected Object getNextInteractiveInput() {
        if (interactiveInputs != null && idx < interactiveInputs.size()) {
            Object result = interactiveInputs.get(idx);
            idx++;
            return result;
        }
        return null;
    }

    /**
     * Wait for user inputs, blocking until input is available.
     *
     * @param value the prompt/schema to present
     * @return the user input
     */
    public abstract Object waitUserInputs(Object value);

    /**
     * Get the latest user input.
     *
     * @param value the prompt
     * @return the latest input
     */
    public Object userLatestInput(Object value) {
        return null;
    }
}
