/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base callback for indexing and embedding progress.
 */
public class BaseCallback {

    private final AtomicInteger callCounter = new AtomicInteger();
    private final int total;

    public BaseCallback() {
        this.total = 0;
    }

    public BaseCallback(Collection<?> sequence) {
        this.total = sequence == null ? 0 : sequence.size();
    }

    public void onBatch(int startIdx, int endIdx, List<String> batch) {
        callCounter.incrementAndGet();
    }

    public int getCallCounter() {
        return callCounter.get();
    }

    public int getTotal() {
        return total;
    }
}
