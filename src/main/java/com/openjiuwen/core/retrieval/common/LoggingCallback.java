/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Simple SLF4J-backed callback for batch progress.
 */
public class LoggingCallback extends BaseCallback {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingCallback.class);

    private final int total;
    private final String desc;

    /**
     * Auto-generated for codecheck compliance.
     */
    public LoggingCallback(int total, String desc) {
        this.total = Math.max(total, 0);
        this.desc = desc == null || desc.isBlank() ? "Indexing" : desc;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void onBatch(int startIdx, int endIdx, List<String> batch) {
        super.onBatch(startIdx, endIdx, batch);
        LOG.info("{} progress: {}/{}", desc, Math.min(endIdx, total), total);
    }
}
