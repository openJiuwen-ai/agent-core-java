/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders;

import com.openjiuwen.dev_tools.agent_builder.builders.llm_agent.LlmAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.WorkflowBuilder;

import java.util.List;

/**
 * Package-level compatibility exports for agent builders.
 *
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.agent_builder.builders} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/__init__.py}.</p>
 */
public final class AgentBuildersPackage {

    public static final String PYTHON_MODULE =
            "openjiuwen/dev_tools/agent_builder/builders/__init__.py";
    public static final List<String> ALL = List.of(
            "BaseAgentBuilder",
            "LlmAgentBuilder",
            "WorkflowBuilder",
            "AgentBuilderFactory"
    );
    public static final Class<BaseAgentBuilder> BASE_AGENT_BUILDER = BaseAgentBuilder.class;
    public static final Class<LlmAgentBuilder> LLM_AGENT_BUILDER = LlmAgentBuilder.class;
    public static final Class<WorkflowBuilder> WORKFLOW_BUILDER = WorkflowBuilder.class;
    public static final Class<AgentBuilderFactory> AGENT_BUILDER_FACTORY = AgentBuilderFactory.class;

    private AgentBuildersPackage() {
    }
}
