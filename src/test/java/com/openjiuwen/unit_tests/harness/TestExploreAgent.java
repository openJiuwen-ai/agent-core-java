/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.subagents.ExploreAgent;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code test_explore_agent} in
 * {@code tests.unit_tests.harness.test_explore_agent}.
 */
class TestExploreAgent {

    @Test
    @Tag("level0")
    @DisplayName("buildExploreAgentConfig provides default card prompt rails and tools")
    void testBuildExploreAgentConfigDefaults() {
        DeepAgentConfig config = ExploreAgent.buildExploreAgentConfig("en");

        assertNotNull(config.getCard());
        assertEquals("explore_agent", config.getCard().getName());
        assertNotNull(config.getSystemPrompt());
        assertTrue(config.getSystemPrompt().contains("read-only") || config.getSystemPrompt().contains("locating files"));
        assertEquals(15, config.getMaxIterations());
        assertFalse(config.getEnableTaskLoop());
        assertNotNull(config.getRails());
        assertEquals(1, config.getRails().size());
        assertNotNull(config.getTools());
        assertFalse(config.getTools().isEmpty());
    }

    @Test
    @Tag("level0")
    @DisplayName("created explore subagent registers expected tools")
    void testCreateSubagentExploreInitializesTools() {
        DeepAgent subagent = ExploreAgent.createExploreSubagent("en", new Workspace());

        assertEquals("explore_agent", subagent.getCard().getName());
        assertNotNull(subagent.getDelegate().getAbilityManager().get("read_file"));
        assertNotNull(subagent.getDelegate().getAbilityManager().get("glob"));
        assertNotNull(subagent.getDelegate().getAbilityManager().get("list_files"));
        assertNotNull(subagent.getDelegate().getAbilityManager().get("grep"));
        assertNotNull(subagent.getDelegate().getAbilityManager().get("bash"));
        assertNotNull(subagent.getDelegate().getAbilityManager().get("write_file"));
        assertNotNull(subagent.getDelegate().getAbilityManager().get("edit_file"));
    }
}
