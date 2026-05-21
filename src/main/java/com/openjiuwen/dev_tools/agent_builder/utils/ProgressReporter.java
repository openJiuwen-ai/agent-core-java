/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Progress reporter for agent builder.
 * <p>
 * Mirrors Python's {@code ProgressReporter} in
 * {@code openjiuwen.dev_tools.agent_builder.utils.progress}.
 */
public class ProgressReporter {

    private static final Logger LOG = LoggerFactory.getLogger(ProgressReporter.class);

    public void report(AgentBuilderEnums.ProgressStage stage, AgentBuilderEnums.ProgressStatus status,
                        String message) {
        LOG.info("[AgentBuilder] {} - {}: {}", stage, status, message);
    }

    public void report(AgentBuilderEnums.ProgressStage stage, AgentBuilderEnums.ProgressStatus status) {
        report(stage, status, "");
    }
}
