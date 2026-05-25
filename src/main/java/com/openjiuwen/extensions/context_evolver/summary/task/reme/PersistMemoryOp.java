/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reme;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Persist memory operation for ReMe algorithm.
 * <p>
 * Mirrors Python's PersistMemoryOp in ReMe summary task.
 */
public class PersistMemoryOp {

    private Map<String, Object> memoryData;
    private boolean deduplicated;

    public PersistMemoryOp() {
    }

    public PersistMemoryOp(Map<String, Object> memoryData) {
        this.memoryData = memoryData;
    }

    public CompletableFuture<String> execute() {
        return CompletableFuture.completedFuture("reme-" + System.currentTimeMillis());
    }

    public Map<String, Object> getMemoryData() {
        return memoryData;
    }

    public void setMemoryData(Map<String, Object> memoryData) {
        this.memoryData = memoryData;
    }

    public boolean isDeduplicated() {
        return deduplicated;
    }

    public void setDeduplicated(boolean deduplicated) {
        this.deduplicated = deduplicated;
    }
}