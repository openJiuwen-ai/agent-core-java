/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.op;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;

/**
 * Sequential composition of context evolver operations.
 * <p>
 * Mirrors Python's {@code SequentialOp} in
 * {@code openjiuwen/extensions/context_evolver/core/op/sequential_op.py}.
 * </p>
 */
public class SequentialOp extends BaseOp {

    private final List<BaseOp> ops;

    public SequentialOp(BaseOp... ops) {
        super();
        this.ops = ops == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(ops));
    }

    public List<BaseOp> getOps() {
        return new ArrayList<>(ops);
    }

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        CompletableFuture<RuntimeContext> chain = CompletableFuture.completedFuture(context);
        for (BaseOp op : ops) {
            chain = chain.thenCompose(op::call);
        }
        return chain.thenApply(ignored -> null);
    }

    @Override
    public SequentialOp then(BaseOp other) {
        List<BaseOp> combined = new ArrayList<>(ops);
        if (other instanceof SequentialOp sequentialOp) {
            combined.addAll(sequentialOp.ops);
        } else {
            combined.add(other);
        }
        return new SequentialOp(combined.toArray(BaseOp[]::new));
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(" >> ");
        for (BaseOp op : ops) {
            joiner.add(String.valueOf(op));
        }
        return "(" + joiner + ")";
    }
}
