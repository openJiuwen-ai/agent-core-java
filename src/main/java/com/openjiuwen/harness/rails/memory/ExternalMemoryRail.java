/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.memory;

import com.openjiuwen.harness.rails.DeepAgentRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rail that manages external memory store integration.
 * <p>
 * Mirrors Python's {@code ExternalMemoryRail} in
 * {@code openjiuwen.harness.rails.memory.external_memory_rail}.
 */
public class ExternalMemoryRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalMemoryRail.class);

    public ExternalMemoryRail() {
        super();
    }

    @Override
    public void init(Object agent) {
        LOG.info("[ExternalMemoryRail] Initialized");
    }

    @Override
    public void uninit(Object agent) {
        LOG.info("[ExternalMemoryRail] Uninitialized");
    }
}
