/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.extensions.context_evolver.core.context;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.core.context.service_context.ServiceContext}.
 * 
 * Singleton context for managing shared services (LLM, embeddings, vector store).
 */
public class ServiceContext {
    
    private static final Logger log = LoggerFactory.getLogger(ServiceContext.class);
    
    private static volatile ServiceContext instance;
    
    private final Map<String, Object> services;
    
    private ServiceContext() {
        this.services = new ConcurrentHashMap<>();
        log.info("ServiceContext initialized");
    }
    
    /**
     * Get singleton instance.
     */
    public static ServiceContext getInstance() {
        if (instance == null) {
            synchronized (ServiceContext.class) {
                if (instance == null) {
                    instance = new ServiceContext();
                }
            }
        }
        return instance;
    }
    
    /**
     * Register a service.
     *
     * @param name    service name
     * @param service service instance
     */
    public void registerService(String name, Object service) {
        services.put(name, service);
        log.info("Registered service: {}", name);
    }
    
    /**
     * Get a registered service.
     *
     * @param name service name
     * @return service instance or null
     */
    public Object getService(String name) {
        return services.get(name);
    }
    
    /**
     * Get LLM service.
     */
    public Object getLlm() {
        return getService("llm");
    }
    
    /**
     * Get embedding model service.
     */
    public Object getEmbeddingModel() {
        return getService("embedding_model");
    }
    
    /**
     * Get vector store service.
     */
    public Object getVectorStore() {
        return getService("vector_store");
    }
    
    /**
     * Clear all registered services.
     */
    public void clear() {
        services.clear();
        log.info("ServiceContext cleared");
    }
    
    /**
     * Check if service exists.
     */
    public boolean hasService(String name) {
        return services.containsKey(name);
    }
    
    @Override
    public String toString() {
        return "ServiceContext(services=" + services.keySet() + ")";
    }
}
