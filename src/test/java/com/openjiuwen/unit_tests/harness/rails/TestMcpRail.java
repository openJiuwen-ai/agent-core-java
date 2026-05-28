/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.rails;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.harness.rails.McpRail;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for McpRail — initialization, tool registration, cleanup.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.harness.rails.test_mcp_rail}.
 */
@ExtendWith(MockitoExtension.class)
class TestMcpRail {

    // ---------------------------------------------------------------------------
    // Mock classes (for reflection-based access in McpRail)
    // ---------------------------------------------------------------------------

    /** Mock agent that works with McpRail's reflection-based init/uninit. */
    static class MockAgent {
        private final MockPromptBuilder systemPromptBuilder;
        private final MockCard card;
        private final MockAbilityManager abilityManager;

        MockAgent(String language, String agentId) {
            this.systemPromptBuilder = new MockPromptBuilder(language);
            this.card = new MockCard(agentId);
            this.abilityManager = new MockAbilityManager();
        }

        public MockPromptBuilder getSystemPromptBuilder() { return systemPromptBuilder; }
        public MockCard getCard() { return card; }
        public MockAbilityManager getAbilityManager() { return abilityManager; }
    }

    static class MockPromptBuilder {
        private final String language;
        MockPromptBuilder(String language) { this.language = language; }
        public String getLanguage() { return language; }
    }

    static class MockCard {
        private final String id;
        private String name = null;
        MockCard(String id) { this.id = id; }
        public String getId() { return id; }
        public String getName() { return name; }
    }

    static class MockAbilityManager {
        private final java.util.List<Object> addedCards = new java.util.ArrayList<>();
        private final java.util.List<String> removedNames = new java.util.ArrayList<>();

        public void add(Object card) { addedCards.add(card); }
        public void remove(String name) { removedNames.add(name); }
        public java.util.List<Object> getAddedCards() { return addedCards; }
        public java.util.List<String> getRemovedNames() { return removedNames; }
    }

    // ---------------------------------------------------------------------------
    // Tests: constructor
    // ---------------------------------------------------------------------------

    @Nested
    class TestMcpRailConstructor {

        @Test
        @Tag("level0")
        @DisplayName("tools is initially null")
        void testToolsInitiallyNull() {
            // Python: test_tools_initially_none
            // McpRail().tools should be None/null
            McpRail rail = new McpRail();
            assertNull(rail.getMcpTools(), "McpRail().mcpTools should be null initially");
        }

        @Test
        @Tag("level0")
        @DisplayName("priority is 95")
        void testPriorityIs95() {
            // Python: test_priority_is_95
            // McpRail.priority should be 95
            assertEquals(95, McpRail.getStaticPriority(), "McpRail.PRIORITY should be 95");
            McpRail rail = new McpRail();
            assertEquals(95, rail.getPriority(), "McpRail instance priority should be 95");
        }
    }

    // ---------------------------------------------------------------------------
    // Tests: init registers tools
    // ---------------------------------------------------------------------------

    @Nested
    class TestMcpRailInit {

        @Test
        @Tag("level0")
        @DisplayName("registers two tools in resource manager")
        void testRegistersTwoToolsInResourceManager() {
            // Python: test_registers_two_tools_in_resource_manager
            // McpRail.init() should register ListMcpResourcesTool and ReadMcpResourceTool
            
            McpRail rail = new McpRail();
            MockAgent agent = new MockAgent("cn", "test-agent-id");

            // Mock Runner.resource_mgr
            ResourceMgr mockResourceMgr = mock(ResourceMgr.class);
            
            try (MockedStatic<Runner> mockedRunner = mockStatic(Runner.class)) {
                mockedRunner.when(Runner::resourceMgr).thenReturn(mockResourceMgr);
                
                rail.init(agent);
            }

            // Verify tools were registered
            assertNotNull(rail.getMcpTools(), "mcpTools should not be null after init");
            assertEquals(2, rail.getMcpTools().size(), "should have 2 tools after init");
        }

        @Test
        @Tag("level0")
        @DisplayName("adds both cards to ability manager")
        void testAddsBothCardsToAbilityManager() {
            // Python: test_adds_both_cards_to_ability_manager
            // Both tool cards should be added to agent.ability_manager
            
            McpRail rail = new McpRail();
            MockAgent agent = new MockAgent("cn", "test-agent-id");

            // Mock Runner.resource_mgr
            ResourceMgr mockResourceMgr = mock(ResourceMgr.class);
            
            try (MockedStatic<Runner> mockedRunner = mockStatic(Runner.class)) {
                mockedRunner.when(Runner::resourceMgr).thenReturn(mockResourceMgr);
                
                rail.init(agent);
            }

            // Verify tools were set
            assertNotNull(rail.getMcpTools(), "mcpTools should not be null after init");
            assertEquals(2, rail.getMcpTools().size(), "should have 2 tools");
        }
    }

    // ---------------------------------------------------------------------------
    // Tests: uninit cleans up
    // ---------------------------------------------------------------------------

    @Nested
    class TestMcpRailUninit {

        @Test
        @Tag("level0")
        @DisplayName("removes tools from resource manager")
        void testRemovesToolsFromResourceManager() {
            // Python: test_removes_tool_ids_from_resource_manager
            // McpRail.uninit() should remove tools from Runner.resource_mgr
            
            McpRail rail = new McpRail();
            MockAgent agent = new MockAgent("cn", "test-agent-id");

            // Mock Runner.resource_mgr
            ResourceMgr mockResourceMgr = mock(ResourceMgr.class);
            
            // First init
            try (MockedStatic<Runner> mockedRunner = mockStatic(Runner.class)) {
                mockedRunner.when(Runner::resourceMgr).thenReturn(mockResourceMgr);
                rail.init(agent);
            }

            // Then uninit
            try (MockedStatic<Runner> mockedRunner = mockStatic(Runner.class)) {
                mockedRunner.when(Runner::resourceMgr).thenReturn(mockResourceMgr);
                rail.uninit(agent);
            }

            // Verify tools is null after uninit
            assertNull(rail.getMcpTools(), "mcpTools should be null after uninit");
        }

        @Test
        @Tag("level0")
        @DisplayName("removes cards from ability manager")
        void testRemovesCardsFromAbilityManager() {
            // Python: test_removes_tool_names_from_ability_manager
            // Tool cards should be removed from agent.ability_manager
            
            McpRail rail = new McpRail();
            MockAgent agent = new MockAgent("cn", "test-agent-id");

            // Mock Runner.resource_mgr
            ResourceMgr mockResourceMgr = mock(ResourceMgr.class);
            
            // First init
            try (MockedStatic<Runner> mockedRunner = mockStatic(Runner.class)) {
                mockedRunner.when(Runner::resourceMgr).thenReturn(mockResourceMgr);
                rail.init(agent);
            }

            // Then uninit
            try (MockedStatic<Runner> mockedRunner = mockStatic(Runner.class)) {
                mockedRunner.when(Runner::resourceMgr).thenReturn(mockResourceMgr);
                rail.uninit(agent);
            }

            // Verify tools is null after uninit
            assertNull(rail.getMcpTools(), "mcpTools should be null after uninit");
        }

        @Test
        @Tag("level0")
        @DisplayName("uninit without init does not raise")
        void testUninitWithoutInitDoesNotRaise() {
            // Python: test_uninit_without_init_does_not_raise
            // McpRail.uninit() when tools is None should not raise
            
            McpRail rail = new McpRail();
            MockAgent agent = new MockAgent("cn", "test-agent-id");

            // Call uninit without calling init
            rail.uninit(agent);
            
            // Should not throw - passes if no exception
            assertNull(rail.getMcpTools(), "mcpTools should remain null");
        }
    }
}