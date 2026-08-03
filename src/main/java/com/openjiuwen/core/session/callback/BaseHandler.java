/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.callback;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Base handler for stateless data processing via callbacks.
 *
 * <p>Mirrors Python's {@code BaseHandler} in
 * {@code openjiuwen/core/session/callback/base.py}.</p>
 */
public abstract class BaseHandler {

    private final Object owner;

    protected BaseHandler(Object owner) {
        this.owner = owner;
    }

    public Object getOwner() {
        return owner;
    }

    public abstract String eventName();

    public List<String> getTriggerEvents() {
        List<String> triggerEvents = new ArrayList<>();
        for (Method method : getClass().getMethods()) {
            if (method.isAnnotationPresent(TriggerEvent.class)) {
                triggerEvents.add(method.getName());
            }
        }
        return triggerEvents;
    }
}
