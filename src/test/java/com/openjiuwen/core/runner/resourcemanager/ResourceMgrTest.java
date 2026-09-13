// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.

package com.openjiuwen.core.runner.resourcemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.base.Result;
import com.openjiuwen.core.runner.base.Tag;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.WorkflowCard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
        ToolCard card =
            ToolCard.builder().id(toolId).name(name.isEmpty() ? toolId : name).description("tool " + toolId).build();
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
            Exception ex = assertThrows(Exception.class, () -> resourceMgr.addAgent(null, () -> "mock_agent", null));
            assertTrue(ex.getMessage().contains("cannot be None"));
        }

        @Test
        @DisplayName("Add agent with invalid card type throws error")
        void testAddAgentWithInvalidCardType() {
            // In Java, type safety prevents passing non-AgentCard to addAgent
            // but null card still triggers validation
            Exception ex = assertThrows(Exception.class, () -> resourceMgr.addAgent(null, () -> "mock_agent", null));
            assertTrue(ex.getMessage().contains("cannot be None"));
        }

        @Test
        @DisplayName("Add agent with empty id auto-generates id")
        void testAddAgentWithEmptyId() {
            AgentCard card = AgentCard.builder().id("").name("Test Agent").build();
            Result<?> result = resourceMgr.addAgent(card, () -> "mock_agent", null);
            assertTrue(result.isOk());
            assertFalse(card.getId().isBlank());
        }

        @Test
        @DisplayName("Add agent with whitespace-only id auto-generates id")
        void testAddAgentWithWhitespaceId() {
            AgentCard card = AgentCard.builder().id("   ").name("Test Agent").build();
            Result<?> result = resourceMgr.addAgent(card, () -> "mock_agent", null);
            assertTrue(result.isOk());
            assertFalse(card.getId().isBlank());
        }

        @Test
        @DisplayName("Add agent with null provider throws error")
        void testAddAgentWithNullProvider() {
            AgentCard card = AgentCard.builder().id("test_agent").name("Test Agent").build();
            Exception ex = assertThrows(Exception.class, () -> resourceMgr.addAgent(card, null, null));
            assertTrue(ex.getMessage().contains("provider cannot be None"));
        }
    }

    @Nested
    @DisplayName("Add Tool Validation")
    class AddToolValidation {
        @Test
        @DisplayName("Add null tool throws error")
        void testAddToolNull() {
            Exception ex = assertThrows(Exception.class, () -> resourceMgr.addTool(null, null));
            assertTrue(ex.getMessage().contains("cannot be None"));
        }

        @Test
        @DisplayName("Add tool with empty tools list throws error")
        void testAddToolsEmptyList() {
            Exception ex = assertThrows(Exception.class, () -> resourceMgr.addTools(List.of(), null));
            assertTrue(ex.getMessage().contains("cannot be empty"));
        }

        @Test
        @DisplayName("Tool card validation preserves its cause")
        void testAddToolInvalidCardPreservesCause() {
            Tool toolWithMissingCard = new SimpleTool(makeTool("delegate").getCard()) {
                @Override
                public ToolCard getCard() {
                    return null;
                }
            };
            ValidationError singleError = assertThrows(ValidationError.class,
                    () -> resourceMgr.addTool(toolWithMissingCard, null));
            ValidationError bulkError = assertThrows(ValidationError.class,
                    () -> resourceMgr.addTools(List.of(toolWithMissingCard), null));

            assertTrue(singleError.getCause() instanceof BaseError);
            assertTrue(bulkError.getCause() instanceof BaseError);
        }

        @Test
        @DisplayName("Malformed tool list is rejected before any tool is added")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void testAddToolsMalformedListIsAtomic() {
            Tool validTool = makeTool("valid_tool");
            List malformedTools = List.of(validTool, "abc");

            ValidationError error = assertThrows(ValidationError.class,
                    () -> resourceMgr.addTools(malformedTools, null));

            assertTrue(error.getMessage().contains(
                    "invalid tool type at index 1: expected Tool, got str"));
            assertNull(resourceMgr.getTool("valid_tool"));
        }

        @Test
        @DisplayName("Empty tool id is rejected for get and remove")
        void testEmptyToolIdRejected() {
            ValidationError getError = assertThrows(ValidationError.class, () -> resourceMgr.getTool(""));
            ValidationError removeError = assertThrows(ValidationError.class,
                    () -> resourceMgr.removeTool("", null, null, true));
            ValidationError listError = assertThrows(ValidationError.class,
                    () -> resourceMgr.removeTool(List.of(""), null, null, true));

            assertTrue(getError.getMessage().contains("tool id list cannot be empty or None"));
            assertTrue(removeError.getMessage().contains("tool id list cannot be empty or None"));
            assertTrue(listError.getCause() instanceof BaseError);
        }

        @Test
        @DisplayName("Malformed workflow provider list has a validation error")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void testAddWorkflowsMalformedProviderList() {
            List malformedWorkflows = List.of(List.of());

            BaseError error = assertThrows(BaseError.class,
                    () -> resourceMgr.addWorkflows(malformedWorkflows, null));

            assertEquals(StatusCode.RESOURCE_PROVIDER_INVALID.getCode(), error.getCode());
            assertTrue(error.getMessage().contains("invalid provider format at idx 0: "
                    + "expected tuple[WorkflowCard, Callable], got list (length=0)"));
        }

        @Test
        @DisplayName("Workflow provider id validation preserves its cause")
        void testAddWorkflowInvalidIdPreservesCause() {
            WorkflowCard card = new WorkflowCard("", "workflow");
            ResourceMgr.WorkflowEntry entry = new ResourceMgr.WorkflowEntry(card, () -> null);

            BaseError error = assertThrows(BaseError.class,
                    () -> resourceMgr.addWorkflows(List.of(entry), null));

            assertEquals(StatusCode.RESOURCE_PROVIDER_INVALID.getCode(), error.getCode());
            assertTrue(error.getCause() instanceof BaseError);
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
                List<String> ids =
                    list.stream().filter(t -> t instanceof Tool).map(t -> ((Tool) t).getCard().getId()).toList();
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
                List<String> ids =
                    list.stream().filter(t -> t instanceof Tool).map(t -> ((Tool) t).getCard().getId()).toList();
                assertTrue(ids.contains("tool_for_agent1"));
                assertFalse(ids.contains("tool_for_agent2"));
            }

            // agent_2 can only see its own tool
            Object agent2Found = resourceMgr.getTool(null, "agent_2", null);
            if (agent2Found instanceof List<?> list) {
                List<String> ids =
                    list.stream().filter(t -> t instanceof Tool).map(t -> ((Tool) t).getCard().getId()).toList();
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
        @DisplayName("Add duplicate agent returns an error and keeps existing registration")
        void testAddDuplicateAgent() {
            AgentCard card = AgentCard.builder().id("agent1").name("Agent One").build();
            Result<?> first = resourceMgr.addAgent(card, () -> "agent_instance", null);
            Result<?> duplicate = resourceMgr.addAgent(card, () -> "agent_instance2", null);

            assertTrue(first.isOk());
            assertTrue(duplicate.isError());
            assertTrue(duplicate.getError() instanceof BaseError);
            BaseError error = (BaseError) duplicate.getError();
            assertEquals("resource already exist", error.getParams().get("reason"));
            assertEquals("agent_instance", resourceMgr.getAgent("agent1"));
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

        @Test
        @DisplayName("Get resources by tag omits ids without resource cards")
        void testGetResourceByTagOmitsMissingCards() {
            assertTrue(resourceMgr.addResourceTag("missing_resource", "missing_tag").isOk());

            assertTrue(resourceMgr.getResourceByTag("missing_tag").isEmpty());
        }
    }
}
