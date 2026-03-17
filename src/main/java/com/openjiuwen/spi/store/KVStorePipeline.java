/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.spi.store;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Pipeline for batch operations on a key-value store.
 * <p>
 * Mirrors Python's {@code BasedKVStorePipeline}.
 */
public class KVStorePipeline {

    private final Function<List<Object[]>, List<Object>> executorFunc;
    private final List<Object[]> operations = new ArrayList<>();

    /**
     * Construct a pipeline with a batch executor function.
     *
     * @param executorFunc function that executes a list of operations and returns results
     */
    public KVStorePipeline(Function<List<Object[]>, List<Object>> executorFunc) {
        this.executorFunc = executorFunc;
    }

    /** Add a set operation to the pipeline. */
    public KVStorePipeline set(String key, Object value) {
        operations.add(new Object[]{"set", key, value});
        return this;
    }

    /** Add a get operation to the pipeline. */
    public KVStorePipeline get(String key) {
        operations.add(new Object[]{"get", key});
        return this;
    }

    /** Add an exists operation to the pipeline. */
    public KVStorePipeline exists(String key) {
        operations.add(new Object[]{"exists", key});
        return this;
    }

    /**
     * Execute all queued operations and return their results.
     *
     * @return results in the order operations were added
     */
    public List<Object> execute() {
        List<Object> results = executorFunc.apply(new ArrayList<>(operations));
        operations.clear();
        return results;
    }
}
