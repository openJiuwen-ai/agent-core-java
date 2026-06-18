/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder;

import com.openjiuwen.dev_tools.agent_builder.builders.AgentBuilderFactory;
import com.openjiuwen.dev_tools.agent_builder.builders.BaseAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.builders.llm_agent.LlmAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.WorkflowBuilder;
import com.openjiuwen.dev_tools.agent_builder.executor.AgentBuilderExecutor;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent builder package parity tests.
 *
 * <p>Mirrors Python's module exports in
 * {@code openjiuwen/dev_tools/agent_builder/__init__.py}.</p>
 */
class AgentBuilderPackageTest {

    @Test
    void exportsMirrorPythonAllOrderAndVersion() {
        assertEquals("openjiuwen/dev_tools/agent_builder/__init__.py", AgentBuilderPackage.PYTHON_MODULE);
        assertEquals("2.0.0", AgentBuilderPackage.VERSION);
        assertEquals(List.of(
                "AgentBuilder",
                "AgentBuilderExecutor",
                "HistoryManager",
                "HistoryCache",
                "BaseAgentBuilder",
                "LlmAgentBuilder",
                "WorkflowBuilder",
                "AgentBuilderFactory"
        ), AgentBuilderPackage.ALL);
    }

    @Test
    void classExportsPointToTranslatedTypes() {
        assertSame(AgentBuilder.class, AgentBuilderPackage.AGENT_BUILDER);
        assertSame(AgentBuilderExecutor.class, AgentBuilderPackage.AGENT_BUILDER_EXECUTOR);
        assertSame(HistoryManager.class, AgentBuilderPackage.HISTORY_MANAGER);
        assertSame(HistoryManager.HistoryCache.class, AgentBuilderPackage.HISTORY_CACHE);
        assertSame(BaseAgentBuilder.class, AgentBuilderPackage.BASE_AGENT_BUILDER);
        assertSame(LlmAgentBuilder.class, AgentBuilderPackage.LLM_AGENT_BUILDER);
        assertSame(WorkflowBuilder.class, AgentBuilderPackage.WORKFLOW_BUILDER);
        assertSame(AgentBuilderFactory.class, AgentBuilderPackage.AGENT_BUILDER_FACTORY);
    }

    @Test
    void exportsOnlyPythonAllSymbols() {
        assertTrue(AgentBuilderPackage.exports("AgentBuilder"));
        assertTrue(AgentBuilderPackage.exports("HistoryCache"));
        assertFalse(AgentBuilderPackage.exports("DialogueMessage"));
    }
}
