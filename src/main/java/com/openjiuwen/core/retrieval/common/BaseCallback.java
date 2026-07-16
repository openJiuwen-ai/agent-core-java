/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base callback for indexing and embedding progress.
 * 
 * @since 0.1.7
 */
public class BaseCallback {
    private final AtomicInteger callCounter = new AtomicInteger();
    private final int total;

    /**
     * BaseCallback.
     * 
     * @since 0.1.7
     */
    public BaseCallback() {
        this.total = 0;
    }

    /**
     * BaseCallback.
     * 
     * @param sequence sequence
     * @since 0.1.7
     */
    public BaseCallback(Collection<?> sequence) {
        this.total = sequence == null ? 0 : sequence.size();
    }

    /**
     * onBatch.
     * 
     * @param startIdx startIdx
     * @param endIdx endIdx
     * @param batch batch
     * @since 0.1.7
     */
    public void onBatch(int startIdx, int endIdx, List<String> batch) {
        callCounter.incrementAndGet();
    }

    /**
     * getCallCounter.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getCallCounter() {
        return callCounter.get();
    }

    /**
     * getTotal.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getTotal() {
        return total;
    }
}
