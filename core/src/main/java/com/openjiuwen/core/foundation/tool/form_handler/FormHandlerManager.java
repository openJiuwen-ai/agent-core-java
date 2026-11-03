/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.form_handler;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.utils.Singleton;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry for form handler classes.
 *
 * <p>Mirrors Python's {@code FormHandlerManager} in
 * {@code openjiuwen/core/foundation/tool/form_handler/form_handler_manager.py}.</p>
 */
public final class FormHandlerManager {

    private static final LoggerProtocol LOGGER = Loggers.TOOL;

    // Python keeps the raw registry entry even when validation fails, so the Java registry must
    // preserve arbitrary keys and values at this dynamic boundary.
    private static final Map<Object, Object> FORM_HANDLER_MAP = new LinkedHashMap<>();

    private static Object defaultFormHandler = DefaultFormHandler.class;

    public static FormHandlerManager getInstance() {
        return Singleton.getInstance(FormHandlerManager.class, FormHandlerManager::new);
    }

    public static FormHandlerManager getFormHandlerManager() {
        return getInstance();
    }

    public synchronized void register(Object handlerTypeValue, Object handlerClass) {
        if (!(handlerTypeValue instanceof String) || ((String) handlerTypeValue).isEmpty()) {
            LOGGER.error("register handler failed, {} is invalid", handlerTypeValue);
        }
        if (!(handlerClass instanceof Class<?> handlerType)
                || !FormHandler.class.isAssignableFrom(handlerType)) {
            LOGGER.error("register handler failed, {} is not a subclass of FormHandler", handlerClass);
        }
        FORM_HANDLER_MAP.put(handlerTypeValue, handlerClass);
        LOGGER.info(
                "register handler success, handler_type_value: {}, handler_class: {}",
                handlerTypeValue,
                handlerClass
        );
    }

    public synchronized void registerDefaultHandler(Object handlerClass) {
        if (!(handlerClass instanceof Class<?> handlerType)
                || !FormHandler.class.isAssignableFrom(handlerType)) {
            LOGGER.error("register default handler failed, {} is not a subclass of FormHandler", handlerClass);
        }
        defaultFormHandler = handlerClass;
        LOGGER.info("register default handler success, handler_class: {}", handlerClass);
    }

    public synchronized Object getHandler(Object handlerType) {
        return FORM_HANDLER_MAP.getOrDefault(handlerType, defaultFormHandler);
    }

    synchronized void resetForTest() {
        FORM_HANDLER_MAP.clear();
        defaultFormHandler = DefaultFormHandler.class;
    }
}
