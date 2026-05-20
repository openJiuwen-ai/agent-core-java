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
 */
public class ReActAgent extends LegacyReActAgent {

    /**
     * Auto-generated for codecheck compliance.
     */
    public ReActAgent(LegacyReActAgentConfig agentConfig, List<Workflow> workflows, List<Tool> tools) {
        super(agentConfig, workflows, tools);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ReActAgent(LegacyReActAgentConfig agentConfig) {
        super(agentConfig);
    }
}
