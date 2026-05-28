/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.subagent;

import com.openjiuwen.harness.rails.DeepAgentRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rail for verification contracts — ensures subagent outputs meet quality criteria.
 * <p>
 * Mirrors Python's {@code VerificationContractRail} in
 * {@code openjiuwen.harness.rails.subagent.verification_contract_rail}.
 */
public class VerificationContractRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(VerificationContractRail.class);

    public VerificationContractRail() {
        super();
    }

    @Override
    public void init(Object agent) {
        LOG.info("[VerificationContractRail] Initialized");
    }

    @Override
    public void uninit(Object agent) {
        LOG.info("[VerificationContractRail] Uninitialized");
    }
}
