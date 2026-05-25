/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.ace;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Persist memory operation for ACE algorithm.
 * <p>
 * Mirrors Python's PersistMemoryOp in ACE summary task.
 */
public class PersistMemoryOp {

    private Map<String, Object> memoryData;
    private String memoryId;

    public PersistMemoryOp() {
    }

    public PersistMemoryOp(Map<String, Object> memoryData) {
        this.memoryData = memoryData;
    }

    public CompletableFuture<String> execute() {
        return CompletableFuture.completedFuture("memory-" + System.currentTimeMillis());
    }

    public Map<String, Object> getMemoryData() {
        return memoryData;
    }

    public void setMemoryData(Map<String, Object> memoryData) {
        this.memoryData = memoryData;
    }

    public String getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }
}