/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.rails;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.harness.rails.McpRail;
import com.openjiuwen.harness.tools.ListMcpResourcesTool;
import com.openjiuwen.harness.tools.ReadMcpResourceTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for McpRail.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.harness.rails.test_mcp_rail}.
 */
@ExtendWith(MockitoExtension.class)
class TestMcpRail {

    public static class MockPromptBuilder {
        private final String language;

        MockPromptBuilder(String language) {
            this.language = language;
        }

        public String getLanguage() {
            return language;
        }
    }

    public static class MockCard {
        private final String id;

        MockCard(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    public static class MockAbilityManager {
        private final List<Object> added = new ArrayList<>();
        private final List<String> removed = new ArrayList<>();

        public void add(Object card) {
            added.add(card);
        }

        public void remove(String name) {
            removed.add(name);
        }
    }

    public static class MockAgent {
        private final MockPromptBuilder systemPromptBuilder;
        private final MockCard card;
        private final MockAbilityManager abilityManager;

        MockAgent(String language, String agentId) {
            this.systemPromptBuilder = new MockPromptBuilder(language);
            this.card = new MockCard(agentId);
            this.abilityManager = new MockAbilityManager();
        }

        public MockPromptBuilder getSystemPromptBuilder() {
            return systemPromptBuilder;
        }

        public MockCard getCard() {
            return card;
        }

        public MockAbilityManager getAbilityManager() {
            return abilityManager;
        }
    }

    public static class AgentWithoutCard {
        private final MockPromptBuilder systemPromptBuilder = new MockPromptBuilder("cn");
        private final MockAbilityManager abilityManager = new MockAbilityManager();

        public MockPromptBuilder getSystemPromptBuilder() {
            return systemPromptBuilder;
        }

        public MockAbilityManager getAbilityManager() {
            return abilityManager;
        }
    }

    public static class AgentWithoutAbilityManager {
        private final MockPromptBuilder systemPromptBuilder = new MockPromptBuilder("cn");
        private final MockCard card = new MockCard("test-agent-id");

        public MockPromptBuilder getSystemPromptBuilder() {
            return systemPromptBuilder;
        }

        public MockCard getCard() {
            return card;
        }
    }

    private static ToolCard card(String id, String name) {
        return ToolCard.builder().id(id).name(name).description(name).build();
    }

    @Nested
    class TestMcpRailConstructor {

        @Test
        @Tag("level0")
        void testToolsInitiallyNull() {
            assertNull(new McpRail().getMcpTools());
        }

        @Test
        @Tag("level0")
        void testPriorityIs95() {
            assertEquals(95, new McpRail().getPriority());
            assertEquals(95, McpRail.getStaticPriority());
        }
    }

    @Nested
    class TestMcpRailInit {

        @Test
        @Tag("level0")
        void testRegistersTwoToolsInResourceManager() {
            McpRail rail = new McpRail();
            ResourceMgr resourceMgr = mock(ResourceMgr.class);
            try (MockedStatic<Runner> mockedRunner = mockStatic(Runner.class)) {
                mockedRunner.when(Runner::resourceMgr).thenReturn(resourceMgr);
                rail.init(new MockAgent("cn", "test-agent-id"));
            }
            assertNotNull(rail.getMcpTools());
            assertEquals(2, rail.getMcpTools().size());
        }

        @Test
        @Tag("level0")
        void testAddsBothCardsToAbilityManager() {
            McpRail rail = new McpRail();
            MockAgent agent = new MockAgent("cn", "test-agent-id");
            ResourceMgr resourceMgr = mock(ResourceMgr.class);
            try (MockedStatic<Runner> mockedRunner = mockStatic(Runner.class)) {
                mockedRunner.when(Runner::resourceMgr).thenReturn(resourceMgr);
                rail.init(agent);
            }
            assertEquals(2, agent.abilityManager.added.size());
        }

        @Test
        @Tag("level0")
        void testToolsAttributeSetAfterInit() {
            McpRail rail = new McpRail();
            ResourceMgr resourceMgr = mock(ResourceMgr.class);
            try (MockedStatic<Runner> mockedRunner = mockStatic(Runner.class)) {
                mockedRunner.when(Runner::resourceMgr).thenReturn(resourceMgr);
                rail.init(new MockAgent("cn", "test-agent-id"));
            }
            assertNotNull(rail.getMcpTools());
            assertEquals(2, rail.getMcpTools().size());
        }

        @Test
        @Tag("level0")
        void testToolsConstructedWithAgentLanguageAndId() {
            ResourceMgr resourceMgr = mock(ResourceMgr.class);
            List<List<?>> capturedArgs = new ArrayList<>();
            try (MockedStatic<Runner> mockedRunner = mockStatic(Runner.class);
                 MockedConstruction<ListMcpResourcesTool> listConstruction = org.mockito.Mockito.mockConstruction(
                         ListMcpResourcesTool.class,
                         (mock, context) -> {
                             capturedArgs.add(context.arguments());
                             when(mock.getCard()).thenReturn(card("lid", "list_mcp_resources"));
                         });
                 MockedConstruction<ReadMcpResourceTool> readConstruction = org.mockito.Mockito.mockConstruction(
                         ReadMcpResourceTool.class,
                         (mock, context) -> {
                             capturedArgs.add(context.arguments());
                             when(mock.getCard()).thenReturn(card("rid", "read_mcp_resource"));
                         })) {
                mockedRunner.when(Runner::resourceMgr).thenReturn(resourceMgr);
                new McpRail().init(new MockAgent("en", "my-agent"));
                assertEquals(List.of("en", "my-agent"), capturedArgs.get(0));
                assertEquals(List.of("en", "my-agent"), capturedArgs.get(1));
            }
        }

        @Test
        @Tag("level0")
        void testAgentWithoutCardUsesNoneId() {
            ResourceMgr resourceMgr = mock(ResourceMgr.class);
            List<List<?>> capturedArgs = new ArrayList<>();
            try (MockedStatic<Runner> mockedRunner = mockStatic(Runner.class);
                 MockedConstruction<ListMcpResourcesTool> listConstruction = org.mockito.Mockito.mockConstruction(
                         ListMcpResourcesTool.class,
                         (mock, context) -> {
                             capturedArgs.add(context.arguments());
                             when(mock.getCard()).thenReturn(card("lid", "list_mcp_resources"));
                         });
                 MockedConstruction<ReadMcpResourceTool> readConstruction = org.mockito.Mockito.mockConstruction(
                         ReadMcpResourceTool.class,
                         (mock, context) -> {
                             capturedArgs.add(context.arguments());
                             when(mock.getCard()).thenReturn(card("rid", "read_mcp_resource"));
                         })) {
                mockedRunner.when(Runner::resourceMgr).thenReturn(resourceMgr);
                new McpRail().init(new AgentWithoutCard());
                assertEquals(java.util.Arrays.asList("cn", null), capturedArgs.get(0));
                assertEquals(java.util.Arrays.asList("cn", null), capturedArgs.get(1));
            }
        }
    }

    @Nested
    class TestMcpRailUninit {

        @Test
        @Tag("level0")
        void testRemovesToolNamesFromAbilityManager() {
            McpRail rail = new McpRail();
            MockAgent agent = new MockAgent("cn", "test-agent-id");
            ResourceMgr resourceMgr = mock(ResourceMgr.class);
            try (MockedStatic<Runner> mockedRunner = mockStatic(Runner.class)) {
                mockedRunner.when(Runner::resourceMgr).thenReturn(resourceMgr);
                rail.init(agent);
                rail.uninit(agent);
            }
            assertTrue(agent.abilityManager.removed.contains("list_mcp_resources"));
            assertTrue(agent.abilityManager.removed.contains("read_mcp_resource"));
        }

        @Test
        @Tag("level0")
        void testRemovesToolIdsFromResourceManager() {
            McpRail rail = new McpRail();
            MockAgent agent = new MockAgent("cn", "test-agent-id");
            ResourceMgr resourceMgr = mock(ResourceMgr.class);
            try (MockedStatic<Runner> mockedRunner = mockStatic(Runner.class)) {
                mockedRunner.when(Runner::resourceMgr).thenReturn(resourceMgr);
                rail.init(agent);
                rail.uninit(agent);
            }
            assertNull(rail.getMcpTools());
        }

        @Test
        @Tag("level0")
        void testUninitWithoutInitDoesNotRaise() {
            assertDoesNotThrow(() -> new McpRail().uninit(new MockAgent("cn", "test-agent-id")));
        }

        @Test
        @Tag("level0")
        void testUninitSkipsAbilityManagerIfAbsent() {
            McpRail rail = new McpRail();
            ResourceMgr resourceMgr = mock(ResourceMgr.class);
            try (MockedStatic<Runner> mockedRunner = mockStatic(Runner.class)) {
                mockedRunner.when(Runner::resourceMgr).thenReturn(resourceMgr);
                rail.init(new MockAgent("cn", "test-agent-id"));
                assertDoesNotThrow(() -> rail.uninit(new AgentWithoutAbilityManager()));
            }
        }
    }
}
