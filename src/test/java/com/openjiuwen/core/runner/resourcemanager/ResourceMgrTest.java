// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.base.Ok;
import com.openjiuwen.core.runner.base.Result;
import com.openjiuwen.core.runner.base.Tag;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ResourceMgr: validation, tool tag isolation, and basic CRUD.
 * Translated from Python test_resource_manager.py
 */
@DisplayName("ResourceMgr Tests")
class ResourceMgrTest {

    private ResourceMgr resourceMgr;

    @BeforeEach
    void setup() {
        resourceMgr = new ResourceMgr();
    }

    // Simple Tool subclass for testing
    static class SimpleTool extends Tool {
        SimpleTool(ToolCard card) {
            super(card);
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return "ok";
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return List.of((Object) "ok").iterator();
        }
    }

    static Tool makeTool(String toolId, String name) {
        ToolCard card = ToolCard.builder()
                .id(toolId)
                .name(name.isEmpty() ? toolId : name)
                .description("tool " + toolId)
                .build();
        return new SimpleTool(card);
    }

    static Tool makeTool(String toolId) {
        return makeTool(toolId, "");
    }

    // ========== Validation Tests ==========

    @Nested
    @DisplayName("Add Agent Validation")
    class AddAgentValidation {

        @Test
        @DisplayName("Add agent with null card throws error")
        void testAddAgentWithNullCard() {
            Exception ex = assertThrows(Exception.class, () ->
                    resourceMgr.addAgent(null, () -> "mock_agent", null));
            assertTrue(ex.getMessage().contains("cannot be None"));
        }

        @Test
        @DisplayName("Add agent with invalid card type throws error")
        void testAddAgentWithInvalidCardType() {
            // In Java, type safety prevents passing non-AgentCard to addAgent
            // but null card still triggers validation
            Exception ex = assertThrows(Exception.class, () ->
                    resourceMgr.addAgent(null, () -> "mock_agent", null));
            assertTrue(ex.getMessage().contains("cannot be None"));
        }

        @Test
        @DisplayName("Add agent with empty id throws error")
        void testAddAgentWithEmptyId() {
            AgentCard card = AgentCard.builder().id("").name("Test Agent").build();
            Exception ex = assertThrows(Exception.class, () ->
                    resourceMgr.addAgent(card, () -> "mock_agent", null));
            assertTrue(ex.getMessage().contains("cannot be empty"));
        }

        @Test
        @DisplayName("Add agent with whitespace-only id throws error")
        void testAddAgentWithWhitespaceId() {
            AgentCard card = AgentCard.builder().id("   ").name("Test Agent").build();
            Exception ex = assertThrows(Exception.class, () ->
                    resourceMgr.addAgent(card, () -> "mock_agent", null));
            assertTrue(ex.getMessage().contains("whitespace"));
        }

        @Test
        @DisplayName("Add agent with null provider throws error")
        void testAddAgentWithNullProvider() {
            AgentCard card = AgentCard.builder().id("test_agent").name("Test Agent").build();
            Exception ex = assertThrows(Exception.class, () ->
                    resourceMgr.addAgent(card, null, null));
            assertTrue(ex.getMessage().contains("provider cannot be None"));
        }
    }

    @Nested
    @DisplayName("Add Tool Validation")
    class AddToolValidation {

        @Test
        @DisplayName("Add null tool throws error")
        void testAddToolNull() {
            Exception ex = assertThrows(Exception.class, () ->
                    resourceMgr.addTool(null, null));
            assertTrue(ex.getMessage().contains("cannot be None"));
        }

        @Test
        @DisplayName("Add tool with empty tools list throws error")
        void testAddToolsEmptyList() {
            Exception ex = assertThrows(Exception.class, () ->
                    resourceMgr.addTools(List.of(), null));
            assertTrue(ex.getMessage().contains("cannot be empty"));
        }
    }

    // ========== Tool Tag Isolation Tests ==========

    @Nested
    @DisplayName("Tool Tag Isolation")
    class ToolTagIsolation {

        @Test
        @DisplayName("Add tool with tag and get by same tag")
        void testAddToolWithTagAndGetBySameTag() {
            Tool tool = makeTool("tool_a");
            Result<?> result = resourceMgr.addTool(tool, "agent_1");
            assertTrue(result.isOk());

            Object found = resourceMgr.getTool("tool_a", "agent_1", null);
            assertNotNull(found);
        }

        @Test
        @DisplayName("Get tool by tag only returns tagged tools")
        @SuppressWarnings("unchecked")
        void testGetToolByTagOnlyReturnsTaggedTools() {
            Tool toolA = makeTool("tool_a1", "search");
            Tool toolB = makeTool("tool_b1", "calculator");
            resourceMgr.addTool(toolA, "agent_1");
            resourceMgr.addTool(toolB, "agent_2");

            Object found = resourceMgr.getTool(null, "agent_1", null);
            assertNotNull(found);
            if (found instanceof List<?> list) {
                List<String> ids = list.stream()
                        .filter(t -> t instanceof Tool)
                        .map(t -> ((Tool) t).getCard().getId())
                        .toList();
                assertTrue(ids.contains("tool_a1"));
                assertFalse(ids.contains("tool_b1"));
            }
        }

        @Test
        @DisplayName("Add tool without tag gets GLOBAL tag")
        void testAddToolWithoutTagGetsGlobal() {
            Tool tool = makeTool("tool_c");
            resourceMgr.addTool(tool, null);
            assertTrue(resourceMgr.resourceHasTag("tool_c", Tag.GLOBAL));
        }

        @Test
        @DisplayName("Add tool with tag does not get GLOBAL tag")
        void testAddToolWithTagDoesNotGetGlobal() {
            Tool tool = makeTool("tool_d");
            resourceMgr.addTool(tool, "agent_1");
            assertFalse(resourceMgr.resourceHasTag("tool_d", Tag.GLOBAL));
            assertTrue(resourceMgr.resourceHasTag("tool_d", "agent_1"));
        }

        @Test
        @DisplayName("Two agents tools isolated by tag")
        @SuppressWarnings("unchecked")
        void testTwoAgentsToolsIsolatedByTag() {
            Tool tool1 = makeTool("tool_for_agent1", "search");
            Tool tool2 = makeTool("tool_for_agent2", "search2");
            resourceMgr.addTool(tool1, "agent_1");
            resourceMgr.addTool(tool2, "agent_2");

            // agent_1 can only see its own tool
            Object agent1Found = resourceMgr.getTool(null, "agent_1", null);
            if (agent1Found instanceof List<?> list) {
                List<String> ids = list.stream()
                        .filter(t -> t instanceof Tool)
                        .map(t -> ((Tool) t).getCard().getId())
                        .toList();
                assertTrue(ids.contains("tool_for_agent1"));
                assertFalse(ids.contains("tool_for_agent2"));
            }

            // agent_2 can only see its own tool
            Object agent2Found = resourceMgr.getTool(null, "agent_2", null);
            if (agent2Found instanceof List<?> list) {
                List<String> ids = list.stream()
                        .filter(t -> t instanceof Tool)
                        .map(t -> ((Tool) t).getCard().getId())
                        .toList();
                assertTrue(ids.contains("tool_for_agent2"));
                assertFalse(ids.contains("tool_for_agent1"));
            }
        }
    }

    // ========== Resource CRUD Tests ==========

    @Nested
    @DisplayName("Resource CRUD")
    class ResourceCRUD {

        @Test
        @DisplayName("Add and get agent")
        void testAddAndGetAgent() {
            AgentCard card = AgentCard.builder().id("agent1").name("Agent One").build();
            Result<?> result = resourceMgr.addAgent(card, () -> "agent_instance", null);
            assertTrue(result.isOk());

            Object agent = resourceMgr.getAgent("agent1");
            assertNotNull(agent);
        }

        @Test
        @DisplayName("Add duplicate agent fails")
        void testAddDuplicateAgent() {
            AgentCard card = AgentCard.builder().id("agent1").name("Agent One").build();
            resourceMgr.addAgent(card, () -> "agent_instance", null);
            Result<?> result = resourceMgr.addAgent(card, () -> "agent_instance2", null);
            assertFalse(result.isOk());
        }

        @Test
        @DisplayName("Add and get tool")
        void testAddAndGetTool() {
            Tool tool = makeTool("my_tool", "My Tool");
            Result<?> result = resourceMgr.addTool(tool, null);
            assertTrue(result.isOk());

            Object found = resourceMgr.getTool("my_tool");
            assertNotNull(found);
        }

        @Test
        @DisplayName("Remove tool")
        void testRemoveTool() {
            Tool tool = makeTool("remove_tool");
            resourceMgr.addTool(tool, null);
            resourceMgr.removeTool("remove_tool", null, null, true);
            // After removal, tag should be gone
            assertFalse(resourceMgr.resourceHasTag("remove_tool", Tag.GLOBAL));
        }

        @Test
        @DisplayName("Get resource tag")
        void testGetResourceTag() {
            Tool tool = makeTool("tagged_tool");
            resourceMgr.addTool(tool, "my_tag");
            List<String> tags = resourceMgr.getResourceTag("tagged_tool");
            assertNotNull(tags);
            assertTrue(tags.contains("my_tag"));
        }
    }
}
