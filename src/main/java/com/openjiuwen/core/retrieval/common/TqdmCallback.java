/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.List;

/**
 * Mirrors Python's {@code TqdmCallback} in
 * {@code openjiuwen/core/retrieval/common/callbacks.py}.
 */
public class TqdmCallback extends BaseCallback {

    private final int length;
    private boolean closed;

    public TqdmCallback(List<?> seq, boolean useRich, String desc) {
        super(seq);
        this.length = seq == null ? 0 : seq.size();
        this.closed = length == 0;
    }

    @Override
    public void call(int startIdx, int endIdx, List<String> batch) {
        threadLock.lock();
        try {
            callCounter.incrementAndGet();
            if (callCounter.get() >= length) {
                closed = true;
            }
        } finally {
            threadLock.unlock();
        }
    }

    public int length() {
        return length;
    }

    public boolean isClosed() {
        return closed;
    }
}
