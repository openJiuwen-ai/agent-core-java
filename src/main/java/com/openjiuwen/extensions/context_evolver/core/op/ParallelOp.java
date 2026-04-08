// coding: utf-8
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.op;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.core.op.parallel_op.ParallelOp}.
 * 
 * Parallel composition of operations.
 */
public class ParallelOp extends BaseOp {
    
    private final List<BaseOp> ops;
    
    public ParallelOp(BaseOp... ops) {
        super();
        this.ops = new ArrayList<>(Arrays.asList(ops));
    }
    
    public ParallelOp(List<BaseOp> ops) {
        super();
        this.ops = new ArrayList<>(ops);
    }
    
    @Override
    protected CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        if (ops.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = ops.stream()
            .map(op -> op.execute(context).thenApply(ctx -> null))
            .toArray(CompletableFuture[]::new);
        
        return CompletableFuture.allOf(futures);
    }
    
    /**
     * Add another operation to parallel execution.
     */
    public ParallelOp parallel(BaseOp other) {
        if (other instanceof ParallelOp) {
            List<BaseOp> newOps = new ArrayList<>(this.ops);
            newOps.addAll(((ParallelOp) other).ops);
            return new ParallelOp(newOps);
        }
        List<BaseOp> newOps = new ArrayList<>(this.ops);
        newOps.add(other);
        return new ParallelOp(newOps);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < ops.size(); i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append(ops.get(i).toString());
        }
        sb.append(")");
        return sb.toString();
    }
}