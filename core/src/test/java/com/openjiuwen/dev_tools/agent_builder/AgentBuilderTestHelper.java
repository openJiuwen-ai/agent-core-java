package com.openjiuwen.dev_tools.agent_builder;

import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.builders.BaseAgentBuilder;

import java.util.Map;

/**
 * Test helper that exposes the package-private AgentBuilder constructor
 * with custom ExecutorFactory for unit testing without real model clients.
 */
class AgentBuilderTestHelper {

    static AgentBuilder createWithMockExecutor(
            Map<String, Object> modelInfo,
            Map<String, HistoryManager> historyManagerMap,
            Map<String, BaseAgentBuilder> agentBuilderMap,
            AgentBuilder.ExecutorFactory executorFactory) {
        return new AgentBuilder(modelInfo, historyManagerMap, agentBuilderMap, executorFactory);
    }

    private AgentBuilderTestHelper() {}
}
