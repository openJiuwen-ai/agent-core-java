/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.op;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.context.ServiceContext;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.core.op.base_op.BaseOp}.
 * 
 * Base class for all operations.
 */
public abstract class BaseOp {
    
    protected final Logger log = LoggerFactory.getLogger(getClass());
    /**
     * Auto-generated for codecheck compliance.
     */
    protected final Map<String, Object> params;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected final ServiceContext serviceContext;
    
    /**
     * Auto-generated for codecheck compliance.
     */
    protected BaseOp() {
        this.params = new HashMap<>();
        this.serviceContext = ServiceContext.getInstance();
    }
    
    /**
     * Auto-generated for codecheck compliance.
     */
    protected BaseOp(Map<String, Object> params) {
        this.params = params != null ? new HashMap<>(params) : new HashMap<>();
        this.serviceContext = ServiceContext.getInstance();
    }
    
    /**
     * Execute the operation.
     *
     * @param context runtime context with input data
     * @return future with updated runtime context
     */
    public CompletableFuture<RuntimeContext> execute(RuntimeContext context) {
        String opName = getClass().getSimpleName();
        log.debug("Executing operation: {}", opName);
        
        return asyncExecute(context)
            .whenComplete((ctx, ex) -> {
                if (ex != null) {
                    log.error("Operation {} failed: {}", opName, ex.getMessage());
                } else {
                    log.debug("Operation {} completed successfully", opName);
                }
            })
            .thenApply(ctx -> context);
    }
    
    /**
     * Execute the operation logic (to be implemented by subclasses).
     *
     * @param context runtime context
     * @return future completing when done
     */
    protected abstract CompletableFuture<Void> asyncExecute(RuntimeContext context);
    
    /**
     * Get LLM service.
     */
    protected Object getLlm() {
        return serviceContext.getLlm();
    }
    
    /**
     * Get embedding model service.
     */
    protected Object getEmbeddingModel() {
        return serviceContext.getEmbeddingModel();
    }
    
    /**
     * Get vector store service.
     */
    protected Object getVectorStore() {
        return serviceContext.getVectorStore();
    }
    
    /**
     * Sequential composition operator.
     */
    public SequentialOp then(BaseOp other) {
        return new SequentialOp(this, other);
    }
    
    /**
     * Parallel composition operator.
     */
    public ParallelOp parallel(BaseOp other) {
        return new ParallelOp(this, other);
    }
    
    /**
     * Auto-generated for codecheck compliance.
     */
    protected Object getParam(String key) {
        return params.get(key);
    }
    
    /**
     * Auto-generated for codecheck compliance.
     */
    protected Object getParam(String key, Object defaultValue) {
        return params.getOrDefault(key, defaultValue);
    }
    
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String toString() {
        if (params.isEmpty()) {
            return getClass().getSimpleName() + "()";
        }
        return getClass().getSimpleName() + "(" + params + ")";
    }
}
