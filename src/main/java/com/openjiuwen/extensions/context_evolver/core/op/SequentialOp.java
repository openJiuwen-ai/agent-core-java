/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.op;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.core.op.sequential_op.SequentialOp}.
 * 
 * Sequential composition of operations.
 */
public class SequentialOp extends BaseOp {
    
    private final List<BaseOp> ops;
    
    public SequentialOp(BaseOp... ops) {
        super();
        this.ops = new ArrayList<>(Arrays.asList(ops));
    }
    
    public SequentialOp(List<BaseOp> ops) {
        super();
        this.ops = new ArrayList<>(ops);
    }
    
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