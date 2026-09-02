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
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.core.op.sequential_op.SequentialOp}.
 * Sequential composition of operations.
 * 
 * @since 0.1.7
 */
public class SequentialOp extends BaseOp {
    private final List<BaseOp> ops;

    /**
     * SequentialOp.
     * 
     * @param ops ops
     * @since 0.1.7
     */
    public SequentialOp(BaseOp... ops) {
        super();
        this.ops = new ArrayList<>(Arrays.asList(ops));
    }

    /**
     * SequentialOp.
     * 
     * @param ops ops
     * @since 0.1.7
     */
    public SequentialOp(List<BaseOp> ops) {
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

        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (BaseOp op : ops) {
            future = future.thenCompose(v -> op.execute(context).thenApply(ctx -> null));
        }
        return future;
    }

    /**
     * Add another operation to the sequence.
     * 
     * @param other other
     * @return the result
     * @since 0.1.7
     */
    public SequentialOp then(BaseOp other) {
        if (other instanceof SequentialOp) {
            List<BaseOp> newOps = new ArrayList<>(this.ops);
            newOps.addAll(((SequentialOp) other).ops);
            return new SequentialOp(newOps);
        }
        List<BaseOp> newOps = new ArrayList<>(this.ops);
        newOps.add(other);
        return new SequentialOp(newOps);
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
                sb.append(" >> ");
            }
            sb.append(ops.get(i).toString());
        }
        sb.append(")");
        return sb.toString();
    }
}
