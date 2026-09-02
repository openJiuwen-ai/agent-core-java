/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.op;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.core.op.parallel_op.ParallelOp}.
 * Parallel composition of operations.
 * 
 * @since 0.1.7
 */
public class ParallelOp extends BaseOp {
    private final List<BaseOp> ops;

    /**
     * ParallelOp.
     * 
     * @param ops ops
     * @since 0.1.7
     */
    public ParallelOp(BaseOp... ops) {
        super();
        this.ops = new ArrayList<>(Arrays.asList(ops));
    }

    /**
     * ParallelOp.
     * 
     * @param ops ops
     * @since 0.1.7
     */
    public ParallelOp(List<BaseOp> ops) {
        super();
        this.ops = new ArrayList<>(ops);
    }

    /**
     * asyncExecute.
     * 
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    @Override
    protected CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        if (ops.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures =
            ops.stream().map(op -> op.execute(context).thenApply(ctx -> null)).toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures);
    }

    /**
     * Add another operation to parallel execution.
     * 
     * @param other other
     * @return the result
     * @since 0.1.7
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

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
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
