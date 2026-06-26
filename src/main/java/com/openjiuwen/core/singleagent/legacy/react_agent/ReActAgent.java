/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy.react_agent;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig;
import com.openjiuwen.core.workflow.Workflow;

import java.util.List;

/**
 * Legacy ReAct agent alias.
 *
 * <p>Mirrors Python's {@code ReActAgent} compatibility import in
 * {@code openjiuwen/core/single_agent/legacy/react_agent.py}.</p>
 */
public class ReActAgent extends LegacyReActAgent {

    public ReActAgent(LegacyReActAgentConfig agentConfig) {
        super(agentConfig);
    }

    public ReActAgent(LegacyReActAgentConfig agentConfig, List<Workflow> workflows, List<Tool> tools) {
        super(agentConfig, workflows, tools);
    }
}
