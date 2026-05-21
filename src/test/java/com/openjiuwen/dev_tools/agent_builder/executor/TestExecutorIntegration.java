/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.executor;

import com.openjiuwen.dev_tools.agent_builder.builders.AgentBuilderFactory;
import com.openjiuwen.dev_tools.agent_builder.builders.BaseAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for AgentBuildExecutor module.
 * <p>
 * Mirrors Python's {@code test_executor_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.executor}.
 */
class TestExecutorIntegration {

    private BaseAgentBuilder builder;
    private HistoryManager historyManager;
    private AgentBuildExecutor executor;

    @BeforeEach
    void setUp() {
        builder = AgentBuilderFactory.create(AgentBuilderEnums.AgentType.LLM_AGENT);
        historyManager = new HistoryManager();
        executor = new AgentBuildExecutor(builder, historyManager);
    }

    @Nested
    class TestExecutorIntegrationInner {

        @Test
        void executorCreation() {
            assertThat(executor).isNotNull();
        }

        @Test
        void executorExecuteQuery() {
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("query", "test build");

            Map<String, Object> result = executor.execute(query);
            assertThat(result).containsKey("status");
        }

        @Test
        void executorRecordsHistory() {
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("query", "test build");

            executor.execute(query);
            assertThat(historyManager.getHistory()).hasSize(1);
        }

        @Test
        void executorMultipleExecutions() {
            for (int i = 0; i < 3; i++) {
                executor.execute(Map.of("query", "build " + i));
            }
            assertThat(historyManager.getHistory()).hasSize(3);
        }
    }
}
