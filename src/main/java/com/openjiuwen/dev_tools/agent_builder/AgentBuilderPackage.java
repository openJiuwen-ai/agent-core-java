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

import java.util.List;

/**
 * Package-level compatibility exports for agent builder.
 *
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.agent_builder} in
 * {@code openjiuwen/dev_tools/agent_builder/__init__.py}.</p>
 */
public final class AgentBuilderPackage {

    public static final String PYTHON_MODULE = "openjiuwen/dev_tools/agent_builder/__init__.py";
    public static final String VERSION = "2.0.0";
    public static final List<String> ALL = List.of(
            "AgentBuilder",
            "AgentBuilderExecutor",
            "HistoryManager",
            "HistoryCache",
            "BaseAgentBuilder",
            "LlmAgentBuilder",
            "WorkflowBuilder",
            "AgentBuilderFactory"
    );

    public static final Class<AgentBuilder> AGENT_BUILDER = AgentBuilder.class;
    public static final Class<AgentBuilderExecutor> AGENT_BUILDER_EXECUTOR = AgentBuilderExecutor.class;
    public static final Class<HistoryManager> HISTORY_MANAGER = HistoryManager.class;
    public static final Class<HistoryManager.HistoryCache> HISTORY_CACHE = HistoryManager.HistoryCache.class;
    public static final Class<BaseAgentBuilder> BASE_AGENT_BUILDER = BaseAgentBuilder.class;
    public static final Class<LlmAgentBuilder> LLM_AGENT_BUILDER = LlmAgentBuilder.class;
    public static final Class<WorkflowBuilder> WORKFLOW_BUILDER = WorkflowBuilder.class;
    public static final Class<AgentBuilderFactory> AGENT_BUILDER_FACTORY = AgentBuilderFactory.class;

    private AgentBuilderPackage() {
    }

    public static boolean exports(String symbolName) {
        return ALL.contains(symbolName);
    }
}
