/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.skills;

import com.openjiuwen.harness.rails.DeepAgentRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rail for skill creation workflow.
 * <p>
 * Mirrors Python's {@code SkillCreateRail} in
 * {@code openjiuwen.harness.rails.skills.skill_create_rail}.
 */
public class SkillCreateRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(SkillCreateRail.class);

    public SkillCreateRail() {
        super();
    }

    @Override
    public void init(Object agent) {
        LOG.info("[SkillCreateRail] Initialized");
    }

    @Override
    public void uninit(Object agent) {
        LOG.info("[SkillCreateRail] Uninitialized");
    }
}
