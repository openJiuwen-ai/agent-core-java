/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.Collection;
import java.util.List;

/**
 * Lightweight progress callback aligned with Python's TqdmCallback.
 * This Java variant tracks progress counters without introducing a UI dependency.
 */
public class TqdmCallback extends BaseCallback {

    private final int length;
    private final String desc;

    public TqdmCallback(Collection<?> sequence) {
        this(sequence, "Indexing");
    }

    public TqdmCallback(Collection<?> sequence, String desc) {
        super(sequence);
        this.length = sequence == null ? 0 : sequence.size();
        this.desc = desc == null || desc.isBlank() ? "Indexing" : desc;
    }

    @Override
    public void onBatch(int startIdx, int endIdx, List<String> batch) {
        super.onBatch(startIdx, endIdx, batch);
    }

    public int length() {
        return length;
    }

    public String getDesc() {
        return desc;
    }
}
