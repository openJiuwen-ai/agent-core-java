/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.op;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.context.ServiceContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;

/**
 * Base operation class for context evolver operations.
 * <p>
 * Mirrors Python's {@code BaseOp} in
 * {@code openjiuwen/extensions/context_evolver/core/op/base_op.py}.
 * </p>
 */
public abstract class BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    private final Map<String, Object> params;
    private final ServiceContext serviceContext;

    protected BaseOp() {
        this(Map.of());
    }

    protected BaseOp(Map<String, Object> params) {
        this.params = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
        this.serviceContext = new ServiceContext();
    }

    public CompletableFuture<RuntimeContext> call(RuntimeContext context) {
        String opName = getClass().getSimpleName();
        LOGGER.debug("Executing operation: %s", opName);
        try {
            return asyncExecute(context)
                    .thenApply(ignored -> {
                        LOGGER.debug("Operation %s completed successfully", opName);
                        return context;
                    })
                    .exceptionally(error -> {
                        LOGGER.error("Operation %s failed: %s", opName, error);
                        throw error instanceof RuntimeException runtimeException
                                ? runtimeException
                                : new RuntimeException(error);
                    });
        } catch (RuntimeException exception) {
            LOGGER.error("Operation %s failed: %s", opName, exception);
            throw exception;
        }
    }

    public CompletableFuture<RuntimeContext> execute(RuntimeContext context) {
        return call(context);
    }

    public abstract CompletableFuture<Void> asyncExecute(RuntimeContext context);

    public Object getLlm() {
        return serviceContext.getLlm();
    }

    public Object getEmbeddingModel() {
        return serviceContext.getEmbeddingModel();
    }

    public Object getVectorStore() {
        return serviceContext.getVectorStore();
    }

    public SequentialOp then(BaseOp other) {
        return new SequentialOp(this, other);
    }

    public ParallelOp parallelWith(BaseOp other) {
        return new ParallelOp(this, other);
    }

    protected Map<String, Object> getParams() {
        return new LinkedHashMap<>(params);
    }

    protected ServiceContext getServiceContext() {
        return serviceContext;
    }

    @Override
    public String toString() {
        if (params.isEmpty()) {
            return getClass().getSimpleName() + "()";
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            joiner.add(entry.getKey() + "=" + entry.getValue());
        }
        return getClass().getSimpleName() + "(" + joiner + ")";
    }
}
