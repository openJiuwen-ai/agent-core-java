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
 * Parallel composition of context evolver operations.
 * <p>
 * Mirrors Python's {@code ParallelOp} in
 * {@code openjiuwen/extensions/context_evolver/core/op/parallel_op.py}.
 * </p>
 */
public class ParallelOp extends BaseOp {

    private final List<BaseOp> ops;

    public ParallelOp(BaseOp... ops) {
        super();
        this.ops = ops == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(ops));
    }

    public List<BaseOp> getOps() {
        return new ArrayList<>(ops);
    }

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        List<CompletableFuture<RuntimeContext>> futures = new ArrayList<>();
        for (BaseOp op : ops) {
            futures.add(op.call(context));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    @Override
    public ParallelOp parallelWith(BaseOp other) {
        List<BaseOp> combined = new ArrayList<>(ops);
        if (other instanceof ParallelOp parallelOp) {
            combined.addAll(parallelOp.ops);
        } else {
            combined.add(other);
        }
        return new ParallelOp(combined.toArray(BaseOp[]::new));
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(" | ");
        for (BaseOp op : ops) {
            joiner.add(String.valueOf(op));
        }
        return "(" + joiner + ")";
    }
}
