/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.context;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code ServiceContext} in
 * {@code openjiuwen/extensions/context_evolver/core/context/service_context.py}.
 */
public class ServiceContext {

    private static final LoggerProtocol LOGGER = Loggers.COMMON;
    private static final Map<String, Object> SERVICES = new LinkedHashMap<>();
    private static boolean initialized;

    public ServiceContext() {
        if (!initialized) {
            initialized = true;
            LOGGER.info("ServiceContext initialized");
        }
    }

    public void registerService(String name, Object service) {
        SERVICES.put(name, service);
        LOGGER.info("Registered service: %s", name);
    }

    public Object getService(String name) {
        return SERVICES.get(name);
    }

    public Object getLlm() {
        return getService("llm");
    }

    public Object getEmbeddingModel() {
        return getService("embedding_model");
    }

    public Object getVectorStore() {
        return getService("vector_store");
    }

    public void clear() {
        SERVICES.clear();
        LOGGER.info("ServiceContext cleared");
    }

    @Override
    public String toString() {
        return "ServiceContext(services=" + List.copyOf(SERVICES.keySet()) + ")";
    }
}
