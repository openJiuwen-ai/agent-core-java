/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Mirrors Python's {@code BaseCallback} in
 * {@code openjiuwen/core/retrieval/common/callbacks.py}.
 */
public class BaseCallback {

    protected final AtomicInteger callCounter = new AtomicInteger();
    protected final ReentrantLock threadLock = new ReentrantLock();

    public BaseCallback(Iterable<?> seq) {
    }

    public void call(int startIdx, int endIdx, List<String> batch) {
        threadLock.lock();
        try {
            callCounter.incrementAndGet();
        } finally {
            threadLock.unlock();
        }
    }

    public int getCallCounter() {
        return callCounter.get();
    }
}
