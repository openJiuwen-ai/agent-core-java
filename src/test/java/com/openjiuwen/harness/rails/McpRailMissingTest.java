/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.tools.ListMcpResourcesTool;
import com.openjiuwen.harness.tools.ReadMcpResourceTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/harness/rails/test_mcp_rail.py}.
 */
class McpRailMissingTest {

    @AfterEach
    void cleanupResourceManager() {
        removeToolIfPresent("ListMcpResourcesTool_test-agent-id");
        removeToolIfPresent("ReadMcpResourceTool_test-agent-id");
        removeToolIfPresent("ListMcpResourcesTool_my-agent");
        removeToolIfPresent("ReadMcpResourceTool_my-agent");
    }

    @Test
    void toolsInitiallyNull() {
        assertNull(new McpRail().getTools());
    }

    @Test
    void priorityIs95() {
        assertEquals(95, new McpRail().getPriority());
    }

    @Test
    void registersTwoToolsInResourceManager() {
        McpRail rail = new McpRail();
        TestAgent agent = makeAgent("cn", "test-agent-id");

        rail.init(agent);

        List<Tool> tools = rail.getTools();
        assertNotNull(tools);
        assertEquals(2, tools.size());
        assertSame(tools.get(0), Runner.resourceMgr().getTool(tools.get(0).getCard().getId()));
        assertSame(tools.get(1), Runner.resourceMgr().getTool(tools.get(1).getCard().getId()));
    }

    @Test
    void addsBothCardsToAbilityManager() {
        McpRail rail = new McpRail();
        TestAgent agent = makeAgent("cn", "test-agent-id");

        rail.init(agent);

        List<Tool> tools = rail.getTools();
        assertNotNull(tools);
        assertSame(tools.get(0).getCard(), agent.getAbilityManager().get("list_mcp_resources").orElse(null));
        assertSame(tools.get(1).getCard(), agent.getAbilityManager().get("read_mcp_resource").orElse(null));
    }

    @Test
    void toolsAttributeSetAfterInit() {
        McpRail rail = new McpRail();

        rail.init(makeAgent("cn", "test-agent-id"));

        List<Tool> tools = rail.getTools();
        assertNotNull(tools);
        assertEquals(2, tools.size());
        assertInstanceOf(ListMcpResourcesTool.class, tools.get(0));
        assertInstanceOf(ReadMcpResourceTool.class, tools.get(1));
    }

    @Test
    void toolsConstructedWithAgentLanguageAndId() {
        McpRail rail = new McpRail();

        rail.init(makeAgent("en", "my-agent"));

        List<Tool> tools = rail.getTools();
        assertNotNull(tools);
        ListMcpResourcesTool listTool = assertInstanceOf(ListMcpResourcesTool.class, tools.get(0));
        ReadMcpResourceTool readTool = assertInstanceOf(ReadMcpResourceTool.class, tools.get(1));
        assertEquals("en", listTool.getLanguage());
        assertEquals("my-agent", listTool.getAgentId());
        assertEquals("ListMcpResourcesTool_my-agent", listTool.getCard().getId());
        assertEquals("en", readTool.getLanguage());
        assertEquals("my-agent", readTool.getAgentId());
        assertEquals("ReadMcpResourceTool_my-agent", readTool.getCard().getId());
    }

    @Test
    void agentWithoutCardUsesNullId() {
        McpRail rail = new McpRail();
        TestAgent agent = new NullCardAgent();
        agent.configure(config("cn"));

        rail.init(agent);

        List<Tool> tools = rail.getTools();
        assertNotNull(tools);
        ListMcpResourcesTool listTool = assertInstanceOf(ListMcpResourcesTool.class, tools.get(0));
        ReadMcpResourceTool readTool = assertInstanceOf(ReadMcpResourceTool.class, tools.get(1));
        assertNull(listTool.getAgentId());
        assertNull(readTool.getAgentId());
        assertTrue(listTool.getCard().getId().startsWith("ListMcpResourcesTool_"));
        assertTrue(readTool.getCard().getId().startsWith("ReadMcpResourceTool_"));

        rail.uninit(agent);
    }

    @Test
    void removesToolNamesFromAbilityManager() {
        McpRail rail = new McpRail();
        TestAgent agent = makeAgent("cn", "test-agent-id");
        rail.init(agent);

        rail.uninit(agent);

        assertTrue(agent.getAbilityManager().get("list_mcp_resources").isEmpty());
        assertTrue(agent.getAbilityManager().get("read_mcp_resource").isEmpty());
    }

    @Test
    void removesToolIdsFromResourceManager() {
        McpRail rail = new McpRail();
        TestAgent agent = makeAgent("cn", "test-agent-id");
        rail.init(agent);
        List<Tool> tools = rail.getTools();
        assertNotNull(tools);

        rail.uninit(agent);

        assertNull(Runner.resourceMgr().getTool(tools.get(0).getCard().getId()));
        assertNull(Runner.resourceMgr().getTool(tools.get(1).getCard().getId()));
    }

    @Test
    void uninitWithoutInitDoesNotRaise() {
        McpRail rail = new McpRail();

        assertDoesNotThrow(() -> rail.uninit(makeAgent("cn", "test-agent-id")));
    }

    @Test
    void uninitSkipsAbilityManagerIfAbsent() {
        McpRail rail = new McpRail();
        ToggleAbilityAgent agent = new ToggleAbilityAgent(new AgentCard("test-agent-id", "test-agent", "test"));
        agent.configure(config("cn"));
        rail.init(agent);

        agent.hideAbilityManager();

        assertDoesNotThrow(() -> rail.uninit(agent));
    }

    private static TestAgent makeAgent(String language, String agentId) {
        TestAgent agent = new TestAgent(new AgentCard(agentId, "test-agent", "test"));
        agent.configure(config(language));
        return agent;
    }

    private static DeepAgentConfig config(String language) {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setLanguage(language);
        return config;
    }

    private static void removeToolIfPresent(String toolId) {
        if (Runner.resourceMgr().getTool(toolId) != null) {
            Runner.resourceMgr().removeTool(toolId);
        }
    }

    /**
     * Mirrors Python's MagicMock agent helper in
     * {@code tests/unit_tests/harness/rails/test_mcp_rail.py}.
     */
    private static class TestAgent extends DeepAgent {
        private TestAgent() {
            super(new AgentCard("test-agent-id", "test-agent", "test"));
        }

        private TestAgent(AgentCard card) {
            super(card);
        }
    }

    /**
     * Mirrors Python's agent where {@code card} is absent.
     */
    private static final class NullCardAgent extends TestAgent {
        @Override
        public AgentCard getCard() {
            return null;
        }
    }

    /**
     * Mirrors Python's uninit case where {@code ability_manager} is absent.
     */
    private static final class ToggleAbilityAgent extends TestAgent {
        private boolean abilityManagerHidden;

        private ToggleAbilityAgent(AgentCard card) {
            super(card);
        }

        @Override
        public AbilityManager getAbilityManager() {
            return abilityManagerHidden ? null : super.getAbilityManager();
        }

        private void hideAbilityManager() {
            abilityManagerHidden = true;
        }
    }
}
