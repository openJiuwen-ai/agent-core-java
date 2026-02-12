/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.callback;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for stateless callback handlers.
 * 
 * <p>Handlers process events and can have trigger event methods annotated with @TriggerEvent.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public abstract class BaseHandler {
    
    /**
     * The owner of this handler (typically a CallbackManager).
     */
    protected final Object owner;
    
    /**
     * Creates a new BaseHandler with the given owner.
     * 
     * @param owner the owner of this handler
     */
    protected BaseHandler(Object owner) {
        this.owner = owner;
    }
    
    /**
     * Gets the event name for this handler.
     * 
     * @return the event name
     */
    public abstract String eventName();
    
    /**
     * Gets all trigger event method names for this handler.
     * 
     * @return list of trigger event method names
     */
    public List<String> getTriggerEvents() {
        List<String> triggerEvents = new ArrayList<>();
        
        for (Method method : this.getClass().getMethods()) {
            if (method.isAnnotationPresent(TriggerEvent.class)) {
                triggerEvents.add(method.getName());
            }
        }
        
        return triggerEvents;
    }
    
    /**
     * Gets the owner of this handler.
     * 
     * @return the owner
     */
    public Object getOwner() {
        return owner;
    }
}

