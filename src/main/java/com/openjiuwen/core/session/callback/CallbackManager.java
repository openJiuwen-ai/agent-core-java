/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.callback;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Manages callback handlers and their trigger events.
 * 
 * <p>Allows registration of handlers and triggering of their events.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class CallbackManager {
    
    private static final LoggerProtocol logger = LogManager.getLogger("session");
    
    private final Map<String, BaseHandler> handlers = new HashMap<>();
    private final Map<String, List<String>> triggerEvents = new HashMap<>();
    
    /**
     * Creates a new CallbackManager.
     */
    public CallbackManager() {
    }
    
    /**
     * Registers handlers.
     * 
     * @param configs map of handler names to handler instances
     */
    public void register(Map<String, BaseHandler> configs) {
        for (Map.Entry<String, BaseHandler> entry : configs.entrySet()) {
            String handlerName = entry.getKey();
            BaseHandler handler = entry.getValue();
            handlers.put(handlerName, handler);
            triggerEvents.put(handlerName, handler.getTriggerEvents());
        }
    }
    
    /**
     * Triggers an event on a handler.
     * 
     * @param handlerClassName the handler class name
     * @param eventName the event name
     * @param kwargs the event arguments
     * @return a CompletableFuture that completes when the event is handled
     * @throws TypeError if the event doesn't exist
     */
    public CompletableFuture<Void> trigger(String handlerClassName, String eventName, Map<String, Object> kwargs) {
        if (!triggerEvents.containsKey(handlerClassName) || 
            !triggerEvents.get(handlerClassName).contains(eventName)) {
            logger.error("event name not exists: {}, {}", handlerClassName, eventName);
            throw new TypeError("event name not exists");
        }
        
        BaseHandler handler = handlers.get(handlerClassName);
        
        try {
            Method method = findMethod(handler.getClass(), eventName);
            if (method != null) {
                Object result = method.invoke(handler, kwargs);
                if (result instanceof CompletableFuture<?> future) {
                    return future.thenApply(v -> null);
                }
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            logger.error("Failed to invoke event: {}.{}", handlerClassName, eventName);
            return CompletableFuture.failedFuture(e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Finds a method by name on a class.
     */
    private Method findMethod(Class<?> clazz, String methodName) {
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }
    
    /**
     * Gets the handlers map.
     * 
     * @return the handlers
     */
    public Map<String, BaseHandler> getHandlers() {
        return handlers;
    }
    
    /**
     * Gets the trigger events map.
     * 
     * @return the trigger events
     */
    public Map<String, List<String>> getTriggerEvents() {
        return triggerEvents;
    }
    
    /**
     * TypeError for invalid event names.
     */
    public static class TypeError extends RuntimeException {
        public TypeError(String message) {
            super(message);
        }
    }
}

