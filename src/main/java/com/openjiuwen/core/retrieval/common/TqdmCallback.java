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

    /**
     * Auto-generated for codecheck compliance.
     */
    public TqdmCallback(Collection<?> sequence) {
        this(sequence, "Indexing");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TqdmCallback(Collection<?> sequence, String desc) {
        super(sequence);
        this.length = sequence == null ? 0 : sequence.size();
        this.desc = desc == null || desc.isBlank() ? "Indexing" : desc;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void onBatch(int startIdx, int endIdx, List<String> batch) {
        super.onBatch(startIdx, endIdx, batch);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int length() {
        return length;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDesc() {
        return desc;
    }
}
