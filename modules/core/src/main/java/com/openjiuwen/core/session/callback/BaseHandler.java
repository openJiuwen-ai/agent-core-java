/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.callback;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Base handler for stateless data processing via callbacks.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.callback.base.BaseHandler}.
 * 
 * @since 0.1.7
 */
public abstract class BaseHandler {
    private final Object owner;

    /**
     * BaseHandler.
     * 
     * @param owner owner
     * @since 0.1.7
     */
    protected BaseHandler(Object owner) {
        this.owner = owner;
    }

    /**
     * getOwner.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getOwner() {
        return owner;
    }

    /**
     * Return the event name this handler is associated with.
     * 
     * @return event name
     * @since 0.1.7
     */
    public abstract String eventName();

    /**
     * Get all methods annotated with {@link TriggerEvent}.
     * 
     * @return list of trigger event method names
     * @since 0.1.7
     */
    public List<String> getTriggerEvents() {
        List<String> triggerEvents = new ArrayList<>();
        Method[] methods = this.getClass().getMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(TriggerEvent.class)) {
                triggerEvents.add(method.getName());
            }
        }
        return triggerEvents;
    }
}
