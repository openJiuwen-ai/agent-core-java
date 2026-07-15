/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.op;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.context.ServiceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.core.op.base_op.BaseOp}.
 * Base class for all operations.
 * 
 * @since 0.1.7
 */
public abstract class BaseOp {
    /**
     * log.
     * 
     * @since 0.1.7
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * params.
     * 
     * @since 0.1.7
     */
    protected final Map<String, Object> params;

    /**
     * serviceContext.
     * 
     * @since 0.1.7
     */
    protected final ServiceContext serviceContext;

    /**
     * BaseOp.
     * 
     * @since 0.1.7
     */
    protected BaseOp() {
        this.params = new HashMap<>();
        this.serviceContext = ServiceContext.getInstance();
    }

    /**
     * BaseOp.
     * 
     * @param params params
     * @since 0.1.7
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
     * @since 0.1.7
     */
    public CompletableFuture<RuntimeContext> execute(RuntimeContext context) {
        String opName = getClass().getSimpleName();
        log.debug("Executing operation: {}", opName);

        return asyncExecute(context).whenComplete((ctx, ex) -> {
            if (ex != null) {
                log.error("Operation {} failed: {}", opName, ex.getMessage());
            } else {
                log.debug("Operation {} completed successfully", opName);
            }
        }).thenApply(ctx -> context);
    }

    /**
     * Execute the operation logic (to be implemented by subclasses).
     * 
     * @param context runtime context
     * @return future completing when done
     * @since 0.1.7
     */
    protected abstract CompletableFuture<Void> asyncExecute(RuntimeContext context);

    /**
     * Get LLM service.
     * 
     * @return the result
     * @since 0.1.7
     */
    protected Object getLlm() {
        return serviceContext.getLlm();
    }

    /**
     * Get embedding model service.
     * 
     * @return the result
     * @since 0.1.7
     */
    protected Object getEmbeddingModel() {
        return serviceContext.getEmbeddingModel();
    }

    /**
     * Get vector store service.
     * 
     * @return the result
     * @since 0.1.7
     */
    protected Object getVectorStore() {
        return serviceContext.getVectorStore();
    }

    /**
     * Sequential composition operator.
     * 
     * @param other other
     * @return the result
     * @since 0.1.7
     */
    public SequentialOp then(BaseOp other) {
        return new SequentialOp(this, other);
    }

    /**
     * Parallel composition operator.
     * 
     * @param other other
     * @return the result
     * @since 0.1.7
     */
    public ParallelOp parallel(BaseOp other) {
        return new ParallelOp(this, other);
    }

    /**
     * getParam.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    protected Object getParam(String key) {
        return params.get(key);
    }

    /**
     * getParam.
     * 
     * @param key key
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    protected Object getParam(String key, Object defaultValue) {
        return params.getOrDefault(key, defaultValue);
    }

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String toString() {
        if (params.isEmpty()) {
            return getClass().getSimpleName() + "()";
        }
        return getClass().getSimpleName() + "(" + params + ")";
    }
}
