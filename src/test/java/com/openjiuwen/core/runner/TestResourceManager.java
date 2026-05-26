/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.runner;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.multiagent.schema.GroupCard;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.runner.base.Result;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.runner.base.Tag;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ResourceManager.
 * Mirrors Python's tests/unit_tests/core/runner/test_resource_manager.py
 */
class TestResourceManager {

    @Nested
    @DisplayName("TestResourceMgrAddMethodsValidation")
    class TestResourceMgrAddMethodsValidation {

        private ResourceMgr resourceMgr;
        private AgentCard mockAgentCard;
        private Supplier<Object> mockAgentProvider;
        private Tool mockTool;
        private Supplier<Object> mockModelProvider;

        @BeforeEach
        void setUp() {
            resourceMgr = new ResourceMgr();
            mockAgentCard = new AgentCard();
            mockAgentCard.setId("test_agent_1");
            mockAgentCard.setName("Test Agent");
            mockAgentProvider = () -> mock(Object.class);
            mockTool = mock(Tool.class);
            ToolCard toolCard = mock(ToolCard.class);
            when(toolCard.getId()).thenReturn("test_tool_1");
            when(toolCard.getName()).thenReturn("Test Tool");
            when(mockTool.getCard()).thenReturn(toolCard);
            mockModelProvider = () -> mock(Object.class);
        }

        private void assertStatusCode(ValidationError err, StatusCode expectError, String expectMessage) {
            assertEquals(expectError.getCode(), err.getCode());
            assertTrue(err.getMessage().contains(expectMessage));
        }

        @Test
        @DisplayName("test_add_agent_with_invalid_card_type")
        void testAddAgentWithInvalidCardType() {
            ValidationError err = assertThrows(ValidationError.class, () ->
                    resourceMgr.addAgent(null, mockAgentProvider, null));
            assertStatusCode(err,
                    StatusCode.RESOURCE_CARD_VALUE_INVALID,
                    "cannot be None, must be an instance of AgentCard");

            err = assertThrows(ValidationError.class, () -> {
                AgentCard invalidCard = mock(AgentCard.class);
                when(invalidCard.getId()).thenReturn("invalid_id");
                resourceMgr.addAgent(invalidCard, mockAgentProvider, null);
            });
            assertEquals(StatusCode.RESOURCE_CARD_VALUE_INVALID.getCode(), err.getCode());
        }

        @Test
        @DisplayName("test_add_agent_with_invalid_card_id")
        void testAddAgentWithInvalidCardId() {
            AgentCard card = new AgentCard();

            ValidationError err = assertThrows(ValidationError.class, () -> {
                card.setId("");
                resourceMgr.addAgent(card, mockAgentProvider, null);
            });
            assertStatusCode(err,
                    StatusCode.RESOURCE_ID_VALUE_INVALID,
                    "cannot be empty or None");

            err = assertThrows(ValidationError.class, () -> {
                card.setId(null);
                resourceMgr.addAgent(card, mockAgentProvider, null);
            });
            assertStatusCode(err,
                    StatusCode.RESOURCE_ID_VALUE_INVALID,
                    "cannot be empty or None");

            err = assertThrows(ValidationError.class, () -> {
                card.setId("   ");
                resourceMgr.addAgent(card, mockAgentProvider, null);
            });
            assertStatusCode(err,
                    StatusCode.RESOURCE_ID_VALUE_INVALID,
                    "string id cannot be empty or whitespace only");
        }

        @Test
        @DisplayName("test_add_agent_with_invalid_provider")
        void testAddAgentWithInvalidProvider() {
            ValidationError err = assertThrows(ValidationError.class, () ->
                    resourceMgr.addAgent(mockAgentCard, null, null));
            assertStatusCode(err,
                    StatusCode.RESOURCE_PROVIDER_INVALID,
                    "provider cannot be None, must be a callable function");

            err = assertThrows(ValidationError.class, () -> {
                Supplier<Object> invalidProvider = null;
                resourceMgr.addAgent(mockAgentCard, invalidProvider, null);
            });
            assertStatusCode(err,
                    StatusCode.RESOURCE_PROVIDER_INVALID,
                    "invalid provider type");
        }

        @Test
        @DisplayName("test_add_agents_with_invalid_cards_and_providers")
        void testAddAgentsWithInvalidCardsAndProviders() {
            ValidationError err = assertThrows(ValidationError.class, () -> {
                List<ResourceMgr.AgentEntry> agents = Collections.singletonList(
                        new ResourceMgr.AgentEntry(null, mockAgentProvider));
                resourceMgr.addAgents(agents, null);
            });
            assertStatusCode(err,
                    StatusCode.RESOURCE_PROVIDER_INVALID,
                    "invalid card at idx 0: card cannot be None, must be an instance of AgentCard");

            err = assertThrows(ValidationError.class, () -> {
                AgentCard card = mock(AgentCard.class);
                when(card.getId()).thenReturn("test_agent");
                List<ResourceMgr.AgentEntry> agents = Collections.singletonList(
                        new ResourceMgr.AgentEntry(card, null));
                resourceMgr.addAgents(agents, null);
            });
            assertStatusCode(err,
                    StatusCode.RESOURCE_PROVIDER_INVALID,
                    "invalid provider at idx 0: provider cannot be None, must be a callable function");
        }

        @Test
        @DisplayName("test_add_tool_with_invalid_tool")
        void testAddToolWithInvalidTool() {
            ValidationError err = assertThrows(ValidationError.class, () ->
                    resourceMgr.addTool(null, null));
            assertStatusCode(err,
                    StatusCode.RESOURCE_VALUE_INVALID,
                    "tool cannot be None: expected an instance or list of Tool");

            err = assertThrows(ValidationError.class, () ->
                    resourceMgr.addTool(mock(Tool.class), null));
            assertStatusCode(err,
                    StatusCode.RESOURCE_VALUE_INVALID,
                    "invalid tool type: expected Tool, got");

            err = assertThrows(ValidationError.class, () ->
                    resourceMgr.addTools(Collections.emptyList(), null));
            assertStatusCode(err,
                    StatusCode.RESOURCE_VALUE_INVALID,
                    "tool list cannot be empty: expected a non-empty list of Tool");

            err = assertThrows(ValidationError.class, () -> {
                Tool mockTool1 = mock(Tool.class);
                ToolCard card1 = mock(ToolCard.class);
                when(card1.getId()).thenReturn("test_tool");
                when(mockTool1.getCard()).thenReturn(card1);
                List<Tool> tools = Arrays.asList(mockTool1, null);
                resourceMgr.addTools(tools, null);
            });
            assertStatusCode(err,
                    StatusCode.RESOURCE_VALUE_INVALID,
                    "invalid tool type at index");
        }

        @Test
        @DisplayName("test_add_tool_with_invalid_tool_card")
        void testAddToolWithInvalidToolCard() {
            ValidationError err = assertThrows(ValidationError.class, () -> {
                Tool tool = mock(Tool.class);
                when(tool.getCard()).thenReturn(null);
                resourceMgr.addTool(tool, null);
            });
            assertStatusCode(err,
                    StatusCode.RESOURCE_VALUE_INVALID,
                    "invalid tool type: expected Tool, got Mock");

            err = assertThrows(ValidationError.class, () -> {
                Tool tool = mock(Tool.class);
                ToolCard card = mock(ToolCard.class);
                when(card.getId()).thenReturn("");
                when(tool.getCard()).thenReturn(card);
                resourceMgr.addTool(tool, null);
            });
            assertStatusCode(err,
                    StatusCode.RESOURCE_VALUE_INVALID,
                    "invalid tool type: expected Tool, got Mock");
        }
    }

    private static class SimpleTool extends Tool {
        public SimpleTool(ToolCard card) {
            super(card);
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return "ok";
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return Collections.<Object>singleton("ok").iterator();
        }
    }

    private static Tool makeTool(String toolId, String name) {
        ToolCard card = ToolCard.builder()
                .id(toolId)
                .name(name.isEmpty() ? toolId : name)
                .description("tool " + toolId)
                .build();
        return new SimpleTool(card);
    }

    @Nested
    @DisplayName("TestResourceMgrToolTagIsolation")
    class TestResourceMgrToolTagIsolation {

        private ResourceMgr resourceMgr;

        @BeforeEach
        void setUp() {
            resourceMgr = new ResourceMgr();
        }

        @Test
        @DisplayName("test_add_tool_with_tag_and_get_by_same_tag")
        void testAddToolWithTagAndGetBySameTag() {
            Tool tool = makeTool("tool_a", "");
            Result<ToolCard> result = resourceMgr.addTool(tool, "agent_1");
            assertTrue(result.isOk());

            Object found = resourceMgr.getTool("tool_a", "agent_1", TagMatchStrategy.ALL);
            assertNotNull(found);
        }

        @Test
        @DisplayName("test_get_tool_by_tag_only_returns_tagged_tools")
        void testGetToolByTagOnlyReturnsTaggedTools() {
            Tool toolA = makeTool("tool_a1", "search");
            Tool toolB = makeTool("tool_b1", "calculator");
            resourceMgr.addTool(toolA, "agent_1");
            resourceMgr.addTool(toolB, "agent_2");

            Object foundList = resourceMgr.getTool(null, "agent_1", TagMatchStrategy.ALL);
            assertNotNull(foundList);
        }

        @Test
        @DisplayName("test_add_tool_without_tag_gets_global")
        void testAddToolWithoutTagGetsGlobal() {
            Tool tool = makeTool("tool_c", "");
            resourceMgr.addTool(tool, null);

            assertTrue(resourceMgr.resourceHasTag("tool_c", Tag.GLOBAL));
        }

        @Test
        @DisplayName("test_add_tool_with_tag_does_not_get_global")
        void testAddToolWithTagDoesNotGetGlobal() {
            Tool tool = makeTool("tool_d", "");
            resourceMgr.addTool(tool, "agent_1");

            assertFalse(resourceMgr.resourceHasTag("tool_d", Tag.GLOBAL));
            assertTrue(resourceMgr.resourceHasTag("tool_d", "agent_1"));
        }

        @Test
        @DisplayName("test_two_agents_tools_isolated_by_tag")
        void testTwoAgentsToolsIsolatedByTag() {
            Tool tool1 = makeTool("tool_for_agent1", "search");
            Tool tool2 = makeTool("tool_for_agent2", "search2");
            resourceMgr.addTool(tool1, "agent_1");
            resourceMgr.addTool(tool2, "agent_2");

            Object agent1Tools = resourceMgr.getTool(null, "agent_1", TagMatchStrategy.ALL);
            assertNotNull(agent1Tools);

            Object agent2Tools = resourceMgr.getTool(null, "agent_2", TagMatchStrategy.ALL);
            assertNotNull(agent2Tools);
        }

        @Test
        @DisplayName("test_get_tool_infos_with_tag")
        void testGetToolInfosWithTag() {
            Tool tool1 = makeTool("info_tool_1", "tool_one");
            Tool tool2 = makeTool("info_tool_2", "tool_two");
            resourceMgr.addTool(tool1, "agent_x");
            resourceMgr.addTool(tool2, "agent_y");

            List<ToolInfo> infos = resourceMgr.getToolInfos(null, null, "agent_x", TagMatchStrategy.ALL);
            assertNotNull(infos);
        }

        @Test
        @DisplayName("test_add_workflow_with_tag_and_get_by_same_tag")
        void testAddWorkflowWithTagAndGetBySameTag() {
            WorkflowCard card = WorkflowCard.builder()
                    .id("wf_1")
                    .name("workflow_1")
                    .build();
            Supplier<Workflow> provider = () -> mock(Workflow.class);
            resourceMgr.addWorkflow(card, provider, "agent_1");

            Object found = resourceMgr.getWorkflow("wf_1", "agent_1", TagMatchStrategy.ALL);
            assertNotNull(found);
        }

        @Test
        @DisplayName("test_get_workflow_by_tag_only_returns_tagged_workflows")
        void testGetWorkflowByTagOnlyReturnsTaggedWorkflows() {
            WorkflowCard card1 = WorkflowCard.builder()
                    .id("wf_agent1")
                    .name("workflow_1")
                    .build();
            WorkflowCard card2 = WorkflowCard.builder()
                    .id("wf_agent2")
                    .name("workflow_2")
                    .build();
            Supplier<Workflow> provider1 = () -> mock(Workflow.class);
            Supplier<Workflow> provider2 = () -> mock(Workflow.class);
            resourceMgr.addWorkflow(card1, provider1, "agent_1");
            resourceMgr.addWorkflow(card2, provider2, "agent_2");

            Object foundList = resourceMgr.getWorkflow(null, "agent_1", TagMatchStrategy.ALL);
            assertNotNull(foundList);
        }
    }

    @Nested
    @DisplayName("TestResourceMgrGetSysOpToolCards")
    class TestResourceMgrGetSysOpToolCards {

        private ResourceMgr resourceMgr;
        private SysOperationCard sysOperationCard;

        @BeforeEach
        void setUp() {
            resourceMgr = new ResourceMgr();
            sysOperationCard = SysOperationCard.builder()
                    .id("test_sys_op")
                    .mode(OperationMode.LOCAL)
                    .workConfig(LocalWorkConfig.builder()
                            .workDir("/tmp/test")
                            .build())
                    .build();
        }

        @Test
        @DisplayName("test_scenario1_single_tool_card")
        void testScenario1SingleToolCard() {
            Result<SysOperationCard> result = resourceMgr.addSysOperation(sysOperationCard, null);
            assertTrue(result.isOk());

            Object toolCard = resourceMgr.getSysOpToolCards("test_sys_op", "fs", "read_file");
            assertNotNull(toolCard);
        }

        @Test
        @DisplayName("test_scenario1_nonexistent_tool")
        void testScenario1NonexistentTool() {
            Result<SysOperationCard> result = resourceMgr.addSysOperation(sysOperationCard, null);
            assertTrue(result.isOk());

            Object toolCard = resourceMgr.getSysOpToolCards("test_sys_op", "fs", "nonexistent_tool");
            assertNull(toolCard);
        }

        @Test
        @DisplayName("test_scenario2_multiple_tool_cards")
        void testScenario2MultipleToolCards() {
            Result<SysOperationCard> result = resourceMgr.addSysOperation(sysOperationCard, null);
            assertTrue(result.isOk());

            Object toolCards = resourceMgr.getSysOpToolCards("test_sys_op", "fs",
                    Arrays.asList("read_file", "write_file"));
            assertNotNull(toolCards);
        }

        @Test
        @DisplayName("test_scenario3_all_tool_cards_from_single_operation")
        void testScenario3AllToolCardsFromSingleOperation() {
            Result<SysOperationCard> result = resourceMgr.addSysOperation(sysOperationCard, null);
            assertTrue(result.isOk());

            Object toolCards = resourceMgr.getSysOpToolCards("test_sys_op", "fs", null);
            assertNotNull(toolCards);
        }

        @Test
        @DisplayName("test_scenario4_all_tool_cards_from_multiple_operations")
        void testScenario4AllToolCardsFromMultipleOperations() {
            Result<SysOperationCard> result = resourceMgr.addSysOperation(sysOperationCard, null);
            assertTrue(result.isOk());

            Object toolCards = resourceMgr.getSysOpToolCards("test_sys_op",
                    Arrays.asList("fs", "shell"), null);
            assertNotNull(toolCards);
        }

        @Test
        @DisplayName("test_scenario5_all_tool_cards_from_all_operations")
        void testScenario5AllToolCardsFromAllOperations() {
            Result<SysOperationCard> result = resourceMgr.addSysOperation(sysOperationCard, null);
            assertTrue(result.isOk());

            Object toolCards = resourceMgr.getSysOpToolCards("test_sys_op", null, null);
            assertNotNull(toolCards);
        }

        @Test
        @DisplayName("test_nonexistent_sys_operation")
        void testNonexistentSysOperation() {
            Object toolCards = resourceMgr.getSysOpToolCards("nonexistent_sys_op", null, null);
            assertNull(toolCards);
        }

        @Test
        @DisplayName("test_error_operation_list_with_tool_name")
        void testErrorOperationListWithToolName() {
            Result<SysOperationCard> result = resourceMgr.addSysOperation(sysOperationCard, null);
            assertTrue(result.isOk());

            Exception err = assertThrows(Exception.class, () ->
                    resourceMgr.getSysOpToolCards("test_sys_op",
                            Arrays.asList("fs", "shell"), "read_file"));
            assertTrue(err.getMessage().contains("tool_name cannot be specified when operation_name is a list"));
        }
    }

    @Nested
    @DisplayName("TestResourceMgrAgentGroupRemove")
    class TestResourceMgrAgentGroupRemove {

        private ResourceMgr resourceMgr;

        @BeforeEach
        void setUp() {
            resourceMgr = new ResourceMgr();
        }

        @Test
        @DisplayName("test_remove_agent_group_returns_ok_with_removed_card")
        void testRemoveAgentGroupReturnsOkWithRemovedCard() {
            GroupCard groupCard = GroupCard.builder()
                    .id("test_group")
                    .name("test_group")
                    .description("test team")
                    .build();

            Supplier<Object> provider = () -> mock(Object.class);
            Result<GroupCard> addResult = resourceMgr.addAgentGroup(groupCard, provider, null);
            assertTrue(addResult.isOk());

            List<Result<GroupCard>> removeResult = resourceMgr.removeAgentGroup(groupCard.getId(), null, null, false);

            assertTrue(removeResult.get(0).isOk());

            Object removedGroup = resourceMgr.getAgentGroup(groupCard.getId(), null, null);
            assertNull(removedGroup);
        }
    }
}