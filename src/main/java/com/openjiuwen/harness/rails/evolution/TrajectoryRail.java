/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.openjiuwen.harness.rails.DeepAgentRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rail for trajectory collection during agent execution.
 * <p>
 * Mirrors Python's {@code TrajectoryRail} in
 * {@code openjiuwen.harness.rails.evolution.trajectory_rail}.
 */
public class TrajectoryRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(TrajectoryRail.class);

    public TrajectoryRail() {
        super();
    }

    @Override
    public void init(Object agent) {
        LOG.info("[TrajectoryRail] Initialized");
    }

    @Override
    public void uninit(Object agent) {
        LOG.info("[TrajectoryRail] Uninitialized");
    }
}
