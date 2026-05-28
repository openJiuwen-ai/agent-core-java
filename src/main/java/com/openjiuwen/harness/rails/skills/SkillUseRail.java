/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.skills;

import com.openjiuwen.harness.rails.DeepAgentRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rail for skill usage orchestration.
 * <p>
 * Mirrors Python's {@code SkillUseRail} in
 * {@code openjiuwen.harness.rails.skills.skill_use_rail}.
 */
public class SkillUseRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(SkillUseRail.class);

    public SkillUseRail() {
        super();
    }

    @Override
    public void init(Object agent) {
        LOG.info("[SkillUseRail] Initialized");
    }

    @Override
    public void uninit(Object agent) {
        LOG.info("[SkillUseRail] Uninitialized");
    }
}
