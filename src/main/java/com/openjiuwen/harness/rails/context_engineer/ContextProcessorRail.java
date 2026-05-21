/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.context_engineer;

import com.openjiuwen.harness.rails.DeepAgentRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rail for context processing — transforms and filters context.
 * <p>
 * Mirrors Python's {@code ContextProcessorRail} in
 * {@code openjiuwen.harness.rails.context_engineer.context_processor_rail}.
 */
public class ContextProcessorRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(ContextProcessorRail.class);

    public ContextProcessorRail() {
        super();
    }

    @Override
    public void init(Object agent) {
        LOG.info("[ContextProcessorRail] Initialized");
    }

    @Override
    public void uninit(Object agent) {
        LOG.info("[ContextProcessorRail] Uninitialized");
    }
}
