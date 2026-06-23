/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.subagents.ExploreAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_explore_agent} in
 * {@code tests/unit_tests/harness/test_explore_agent.py}.
 */
class ExploreAgentPythonParityTest {

    @TempDir
    private Path tempDir;

    @Test
    void buildExploreAgentConfigDefaults() {
        DeepAgentConfig.SubAgentConfig spec = ExploreAgent.buildExploreAgentConfig(
                new Object(), null, null, null, null, "en", false, 15);

        assertEquals("explore_agent", spec.getCard().getName());
        assertNotNull(spec.getCard().getDescription());
        assertFalse(spec.getCard().getDescription().isBlank());
        assertNotNull(spec.getSystemPrompt());
        assertFalse(spec.getSystemPrompt().isBlank());
        assertEquals(1, spec.getConfig().getRails().size());
        SysOperationRail rail = assertInstanceOf(SysOperationRail.class, spec.getConfig().getRails().getFirst());
        assertTrue(rail.isReadOnly());
    }

    @Test
    void createSubagentExploreInitializesTools() {
        Runner.start().toCompletableFuture().join();
        try {
            DeepAgentConfig.SubAgentConfig exploreSpec = ExploreAgent.buildExploreAgentConfig(
                    new Object(), null, null, null, null, "en", false, 15);
            DeepAgentConfig parentConfig = new DeepAgentConfig();
            parentConfig.setModel(new Object());
            parentConfig.setCard(new AgentCard("parent", "parent", "test"));
            parentConfig.setSystemPrompt("parent prompt");
            parentConfig.setSubagents(Map.of("explore_agent", exploreSpec));
            parentConfig.setWorkspace(tempDir.toString());

            DeepAgent parentAgent = new DeepAgent(new AgentCard("parent", "parent", "test"));
            parentAgent.configure(parentConfig);

            DeepAgent subagent = parentAgent.createSubagent("explore_agent", "sub_session_id");

            assertEquals("explore_agent", subagent.getCard().getName());
            assertAbilityPresent(subagent, "read_file");
            assertAbilityPresent(subagent, "glob");
            assertAbilityPresent(subagent, "list_files");
            assertAbilityPresent(subagent, "grep");
            assertAbilityPresent(subagent, "bash");
            assertAbilityAbsent(subagent, "write_file");
            assertAbilityAbsent(subagent, "edit_file");
        } finally {
            Runner.stop().toCompletableFuture().join();
        }
    }

    private static void assertAbilityPresent(DeepAgent agent, String name) {
        assertTrue(agent.getAbilityManager().get(name).isPresent(), name);
    }

    private static void assertAbilityAbsent(DeepAgent agent, String name) {
        assertTrue(agent.getAbilityManager().get(name).isEmpty(), name);
    }
}
