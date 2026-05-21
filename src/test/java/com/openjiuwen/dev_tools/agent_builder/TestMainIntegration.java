/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder;

import com.openjiuwen.dev_tools.agent_builder.builders.AgentBuilderFactory;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for agent_builder main module.
 * <p>
 * Mirrors Python's {@code test_main_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder}.
 */
class TestMainIntegration {

    @Nested
    class TestMainIntegrationInner {

        @Test
        void factoryCanBeUsedToCreateBuilders() {
            var builder = AgentBuilderFactory.create(AgentBuilderEnums.AgentType.LLM_AGENT);
            assertThat(builder).isNotNull();
        }

        @Test
        void builderCanBeBuilt() {
            var builder = AgentBuilderFactory.create(AgentBuilderEnums.AgentType.WORKFLOW);
            Map<String, Object> result = builder.build(Map.of("query", "test"), List.of());
            assertThat(result).containsKey("status");
        }
    }
}
