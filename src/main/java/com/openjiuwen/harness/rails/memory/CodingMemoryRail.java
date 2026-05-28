/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.memory;

import com.openjiuwen.harness.rails.DeepAgentRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rail that provides coding memory context for programming agents.
 * <p>
 * Mirrors Python's {@code CodingMemoryRail} in
 * {@code openjiuwen.harness.rails.memory.coding_memory_rail}.
 */
public class CodingMemoryRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(CodingMemoryRail.class);

    public CodingMemoryRail() {
        super();
    }

    @Override
    public void init(Object agent) {
        LOG.info("[CodingMemoryRail] Initialized");
    }

    @Override
    public void uninit(Object agent) {
        LOG.info("[CodingMemoryRail] Uninitialized");
    }
}
