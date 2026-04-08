/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Graph store utility functions.
 * <p>
 * Mirrors Python's {@code store.graph.utils}.
 */
public final class GraphUtils {

    private GraphUtils() {
    }

    /**
     * Batch an iterable into fixed-size chunks.
     * <p>
     * Mirrors Python 3.12's {@code itertools.batched()}.
     *
     * @param iterable the source iterable
     * @param n        batch size (must be >= 1)
     * @param strict   if true, raise if the last batch is shorter than n
     * @param <T>      element type
     * @return iterator of batches (each batch is a List)
     */
    public static <T> Iterator<List<T>> batched(Iterable<T> iterable, int n, boolean strict) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be at least one, got " + n);
        }
        Iterator<T> source = iterable.iterator();

        return new Iterator<>() {
            private List<T> nextBatch = null;
            private boolean done = false;

            @Override
            public boolean hasNext() {
                if (done) {
                    return false;
                }
                if (nextBatch != null) {
                    return true;
                }
                nextBatch = fetchBatch();
                if (nextBatch == null) {
                    done = true;
                    return false;
                }
                return true;
            }

            @Override
            public List<T> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                List<T> result = nextBatch;
                nextBatch = null;
                return result;
            }

            private List<T> fetchBatch() {
                if (!source.hasNext()) {
                    return null;
                }
                List<T> batch = new ArrayList<>(n);
                for (int i = 0; i < n && source.hasNext(); i++) {
                    batch.add(source.next());
                }
                if (strict && batch.size() != n) {
                    throw new IllegalArgumentException(
                            "batched(): incomplete batch (strict mode), got " + batch.size() + " items, expected " + n);
                }
                return batch;
            }
        };
    }

    /**
     * Batch an iterable into fixed-size chunks (non-strict).
     */
    public static <T> Iterator<List<T>> batched(Iterable<T> iterable, int n) {
        return batched(iterable, n, false);
    }
}
