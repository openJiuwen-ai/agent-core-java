/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.SessionStateAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base interaction helper.
 *
 * <p>Mirrors Python's {@code BaseInteraction} in
 * {@code openjiuwen/core/session/interaction/base.py}.</p>
 */
public abstract class BaseInteraction {

    protected final BaseSession session;
    protected List<Object> interactiveInputs;
    protected Object latestInteractiveInputs;
    protected int index;

    protected BaseInteraction(BaseSession session) {
        this(session, null);
    }

    protected BaseInteraction(BaseSession session, Object defaultInput) {
        this.session = session;
        this.interactiveInputs = defaultInput == null ? null : new ArrayList<>(List.of(defaultInput));
        this.latestInteractiveInputs = null;
        this.index = 0;
        initInteractiveInputs();
    }

    protected final void initInteractiveInputs() {
        SessionStateAccess stateAccess = session == null ? null : session.state();
        Object storedInputs = stateAccess == null ? null : stateAccess.get(Constant.INTERACTIVE_INPUT);
        if (storedInputs instanceof List<?> list) {
            List<Object> mergedInputs = new ArrayList<>(list);
            if (interactiveInputs != null && !interactiveInputs.isEmpty()) {
                mergedInputs.addAll(interactiveInputs);
            }
            interactiveInputs = mergedInputs;
        }
        if (interactiveInputs != null && !interactiveInputs.isEmpty()) {
            if (stateAccess != null) {
                stateAccess.update(Map.of(Constant.INTERACTIVE_INPUT, new ArrayList<>(interactiveInputs)));
            }
            latestInteractiveInputs = interactiveInputs.get(interactiveInputs.size() - 1);
        }
    }

    protected final Object getNextInteractiveInput() {
        if (interactiveInputs != null && index < interactiveInputs.size()) {
            Object result = interactiveInputs.get(index);
            index++;
            return result;
        }
        return null;
    }

    public Object userLatestInput(Object value) {
        return null;
    }

    public abstract Object waitUserInputs(Object value);
}
