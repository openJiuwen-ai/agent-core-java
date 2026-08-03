/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.adapters.agents;

import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.AdapterRegistry;
import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.BaseAgentAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's package export behavior in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/adapters/agents/__init__.py}.
 */
class AgentsPackageTest {

    @Test
    void exposesPythonAllSymbols() {
        assertEquals(
                "openjiuwen/agent_evolving/evaluator/evaluator_pipeline/adapters/agents/__init__.py",
                AgentsPackage.PYTHON_MODULE);
        assertEquals(JiuWenSwarmAgent.class, AgentsPackage.JIU_WEN_SWARM_AGENT);
        assertEquals(List.of("JiuWenSwarmAgent"), AgentsPackage.EXPORTED_SYMBOLS);
        assertEquals(List.of(JiuWenSwarmAgent.class), AgentsPackage.exportedAgentAdapters());
    }

    @Test
    void registerAllKeepsJiuwenswarmFactoryAvailable() {
        AgentsPackage.registerAll();

        assertTrue(AdapterRegistry.getRegisteredAgentNames().contains("jiuwenswarm"));
        BaseAgentAdapter adapter = AdapterRegistry.createAgent("jiuwenswarm", Map.of());
        assertInstanceOf(JiuWenSwarmAgent.class, adapter);
    }
}
