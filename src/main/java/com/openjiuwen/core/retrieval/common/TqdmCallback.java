/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.Collection;
import java.util.List;

/**
 * Lightweight progress callback aligned with Python's TqdmCallback.
 * This Java variant tracks progress counters without introducing a UI dependency.
 * 
 * @since 0.1.7
 */
public class TqdmCallback extends BaseCallback {
    private final int length;
    private final String desc;

    /**
     * TqdmCallback.
     * 
     * @param sequence sequence
     * @since 0.1.7
     */
    public TqdmCallback(Collection<?> sequence) {
        this(sequence, "Indexing");
    }

    /**
     * TqdmCallback.
     * 
     * @param sequence sequence
     * @param desc desc
     * @since 0.1.7
     */
    public TqdmCallback(Collection<?> sequence, String desc) {
        super(sequence);
        this.length = sequence == null ? 0 : sequence.size();
        this.desc = desc == null || desc.isBlank() ? "Indexing" : desc;
    }

    /**
     * onBatch.
     * 
     * @param startIdx startIdx
     * @param endIdx endIdx
     * @param batch batch
     * @since 0.1.7
     */
    @Override
    public void onBatch(int startIdx, int endIdx, List<String> batch) {
        super.onBatch(startIdx, endIdx, batch);
    }

    /**
     * length.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int length() {
        return length;
    }

    /**
     * getDesc.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getDesc() {
        return desc;
    }
}
