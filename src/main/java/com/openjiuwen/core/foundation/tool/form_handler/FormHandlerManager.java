/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.form_handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton manager for form handler registration and retrieval.
 * <p>
 * Mirrors Python's {@code FormHandlerManager} class from
 * <code>foundation/tool/form_handler/form_handler_manager.py</code>.
 *
 * <p>Manages registration of form handlers for different content types
 * and provides a default handler fallback.
 */
public final class FormHandlerManager {

    private static final Logger LOG = LoggerFactory.getLogger(FormHandlerManager.class);

    private static final FormHandlerManager INSTANCE = new FormHandlerManager();

    private final Map<String, FormHandler<?>> handlerMap = new ConcurrentHashMap<>();
    private Class<? extends FormHandler<?>> defaultHandlerClass;

    private FormHandlerManager() {
        this.defaultHandlerClass = null;
    }

    /**
     * Get the singleton instance.
     *
     * @return the FormHandlerManager instance
     */
    public static FormHandlerManager getInstance() {
        return INSTANCE;
    }

    /**
     * Register a handler for a specific form type.
     *
     * @param handlerTypeValue the form type identifier
     * @param handlerClass     the handler class to register
     */
    public void register(String handlerTypeValue, Class<? extends FormHandler<?>> handlerClass) {
        if (handlerTypeValue == null || handlerTypeValue.isEmpty()) {
            LOG.error("register handler failed, {} is invalid", handlerTypeValue);
            return;
        }
        if (handlerClass == null || !FormHandler.class.isAssignableFrom(handlerClass)) {
            LOG.error("register handler failed, {} is not a subclass of FormHandler", handlerClass);
            return;
        }
        try {
            FormHandler<?> handler = handlerClass.getDeclaredConstructor().newInstance();
            handlerMap.put(handlerTypeValue, handler);
            LOG.info("register handler success, handler_type: {}, handler_class: {}", handlerTypeValue, handlerClass);
        } catch (Exception e) {
            LOG.error("register handler failed, could not instantiate {}: {}", handlerClass, e.getMessage());
        }
    }

    /**
     * Register the default form handler.
     *
     * @param handlerClass the default handler class
     */
    public void registerDefaultHandler(Class<? extends FormHandler<?>> handlerClass) {
        if (handlerClass == null || !FormHandler.class.isAssignableFrom(handlerClass)) {
            LOG.error("register default handler failed, {} is not a subclass of FormHandler", handlerClass);
            return;
        }
        this.defaultHandlerClass = handlerClass;
        LOG.info("register default handler success, handler_class: {}", handlerClass);
    }

    /**
     * Get a handler for the specified form type.
     * Falls back to the default handler if no specific handler is registered.
     *
     * @param handlerType the form type
     * @return the matching handler, or the default handler
     */
    @SuppressWarnings("unchecked")
    public <T> FormHandler<T> getHandler(String handlerType) {
        FormHandler<?> handler = handlerMap.get(handlerType);
        if (handler != null) {
            return (FormHandler<T>) handler;
        }
        if (defaultHandlerClass != null) {
            try {
                return (FormHandler<T>) defaultHandlerClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                LOG.error("Failed to instantiate default handler: {}", e.getMessage());
            }
        }
        return (FormHandler<T>) new DefaultFormHandler();
    }
}
