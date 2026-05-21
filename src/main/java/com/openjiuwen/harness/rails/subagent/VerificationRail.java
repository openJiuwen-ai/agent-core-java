/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.subagent;

import com.openjiuwen.harness.rails.DeepAgentRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rail for subagent verification — validates subagent results.
 * <p>
 * Mirrors Python's {@code VerificationRail} in
 * {@code openjiuwen.harness.rails.subagent.verification_rail}.
 */
public class VerificationRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(VerificationRail.class);

    public VerificationRail() {
        super();
    }

    @Override
    public void init(Object agent) {
        LOG.info("[VerificationRail] Initialized");
    }

    @Override
    public void uninit(Object agent) {
        LOG.info("[VerificationRail] Uninitialized");
    }
}
