/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rail that injects heartbeat system prompt.
 * <p>
 * Detects heartbeat runs and injects heartbeat-specific system prompt section.
 * <p>
 * Mirrors Python's {@code HeartbeatRail} in
 * {@code openjiuwen.harness.rails.heartbeat_rail}.
 */
public class HeartbeatRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(HeartbeatRail.class);

    /** Rail priority (higher = runs later). */
    public static final int PRIORITY = 80;

    private Object systemPromptBuilder;

    public HeartbeatRail() {
        super();
    }

    @Override
    public void init(Object agent) {
        try {
            this.systemPromptBuilder = agent.getClass().getMethod("getSystemPromptBuilder").invoke(agent);
        } catch (Exception e) {
            LOG.debug("[HeartbeatRail] Could not get system_prompt_builder");
        }
        LOG.info("[HeartbeatRail] Initialized");
    }

    @Override
    public void uninit(Object agent) {
        if (this.systemPromptBuilder != null) {
            try {
                this.systemPromptBuilder.getClass().getMethod("removeSection", String.class)
                        .invoke(this.systemPromptBuilder, "heartbeat");
            } catch (Exception e) {
                LOG.debug("[HeartbeatRail] Could not remove heartbeat section");
            }
        }
        LOG.info("[HeartbeatRail] Uninitialized");
    }
}
