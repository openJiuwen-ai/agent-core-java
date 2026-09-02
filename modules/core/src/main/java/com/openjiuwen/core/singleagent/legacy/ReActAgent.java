/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig;
import com.openjiuwen.core.workflow.Workflow;

import java.util.List;

/**
 * Alias for the legacy ReActAgent wrapper.
 * 
 * @since 0.1.7
 */
public class ReActAgent extends LegacyReActAgent {
    /**
     * ReActAgent.
     * 
     * @param agentConfig agentConfig
     * @param workflows workflows
     * @param tools tools
     * @since 0.1.7
     */
    public ReActAgent(LegacyReActAgentConfig agentConfig, List<Workflow> workflows, List<Tool> tools) {
        super(agentConfig, workflows, tools);
    }

    /**
     * ReActAgent.
     * 
     * @param agentConfig agentConfig
     * @since 0.1.7
     */
    public ReActAgent(LegacyReActAgentConfig agentConfig) {
        super(agentConfig);
    }
}
