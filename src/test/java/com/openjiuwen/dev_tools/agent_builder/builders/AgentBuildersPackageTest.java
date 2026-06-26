/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders;

import com.openjiuwen.dev_tools.agent_builder.builders.llm_agent.LlmAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.WorkflowBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Agent builders package parity tests.
 *
 * <p>Mirrors Python's module exports in
 * {@code openjiuwen/dev_tools/agent_builder/builders/__init__.py}.</p>
 */
class AgentBuildersPackageTest {

    @Test
    void exportsMirrorPythonAllOrderAndClasses() {
        assertEquals(
                "openjiuwen/dev_tools/agent_builder/builders/__init__.py",
                AgentBuildersPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "BaseAgentBuilder",
                "LlmAgentBuilder",
                "WorkflowBuilder",
                "AgentBuilderFactory"
        ), AgentBuildersPackage.ALL);
        assertSame(BaseAgentBuilder.class, AgentBuildersPackage.BASE_AGENT_BUILDER);
        assertSame(LlmAgentBuilder.class, AgentBuildersPackage.LLM_AGENT_BUILDER);
        assertSame(WorkflowBuilder.class, AgentBuildersPackage.WORKFLOW_BUILDER);
        assertSame(AgentBuilderFactory.class, AgentBuildersPackage.AGENT_BUILDER_FACTORY);
    }
}
