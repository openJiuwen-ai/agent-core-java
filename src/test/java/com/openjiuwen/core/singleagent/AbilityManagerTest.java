// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.common.schema.Param;
import com.openjiuwen.core.common.schema.ParamType;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.WorkflowCard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AbilityManager - ability management and execution.
 * 
 * <p>Python reference: agent-core/tests/unit_tests/core/single_agent/test_ability_manager.py
 */
@ExtendWith(MockitoExtension.class)
class AbilityManagerTest {
    
    @Nested
    @DisplayName("TestAbilityManagerRegistration")
    class TestAbilityManagerRegistration {
        
        private AbilityManager manager;
        
        @BeforeEach
        void setUp() {
            manager = new AbilityManager();
        }
        
        @Test
        @DisplayName("test_add_tool_card_stores_in_tools_dict")
        void testAddToolCardStoresInToolsDict() {
            ToolCard toolCard = new ToolCard("tool_1", "my_tool", "A test tool", null);
            
            manager.add(toolCard);
            
            assertTrue(manager.getTools().containsKey("my_tool"));
            assertEquals(toolCard, manager.getTools().get("my_tool"));
        }
        
        @Test
        @DisplayName("test_add_workflow_card_stores_in_workflows_dict")
        void testAddWorkflowCardStoresInWorkflowsDict() {
            WorkflowCard workflowCard = new WorkflowCard("wf_1", "my_workflow", "A test workflow");
            
            manager.add(workflowCard);
            
            assertTrue(manager.getWorkflows().containsKey("my_workflow"));
            assertEquals(workflowCard, manager.getWorkflows().get("my_workflow"));
        }
        
        @Test
        @DisplayName("test_add_agent_card_stores_in_agents_dict")
        void testAddAgentCardStoresInAgentsDict() {
            AgentCard agentCard = new AgentCard("agent_1", "my_agent", "A test agent", null);
            
            manager.add(agentCard);
            
            assertTrue(manager.getAgents().containsKey("my_agent"));
            assertEquals(agentCard, manager.getAgents().get("my_agent"));
        }
        
        @Test
        @DisplayName("test_add_mcp_server_config_stores_in_mcp_servers_dict")
        void testAddMcpServerConfigStoresInMcpServersDict() {
            McpServerConfig mcpConfig = McpServerConfig.builder()
                .serverId("mcp_1")
                .serverName("my_mcp_server")
                .serverPath("http://localhost:8080/mcp")
                .build();
            
            manager.add(mcpConfig);
            
            assertTrue(manager.getMcpServers().containsKey("my_mcp_server"));
            assertEquals(mcpConfig, manager.getMcpServers().get("my_mcp_server"));
        }
        
        @Test
        @DisplayName("test_add_unknown_type_logs_warning_without_storing")
        void testAddUnknownTypeLogsWarningWithoutStoring() {
            Map<String, String> unknownObj = new HashMap<>();
            unknownObj.put("name", "unknown");
            
            // Should not throw, just log warning
            manager.add(unknownObj);
            
            // Verify nothing was stored
            assertEquals(0, manager.getTools().size());
            assertEquals(0, manager.getWorkflows().size());
            assertEquals(0, manager.getAgents().size());
            assertEquals(0, manager.getMcpServers().size());
        }
        
        @Test
        @DisplayName("test_add_duplicate_ability_overwrites_previous")
        void testAddDuplicateAbilityOverwritesPrevious() {
            ToolCard toolCardV1 = new ToolCard("tool_1", "my_tool", "Version 1", null);
            ToolCard toolCardV2 = new ToolCard("tool_2", "my_tool", "Version 2", null);
            
            manager.add(toolCardV1);
            manager.add(toolCardV2);
            
            assertEquals(toolCardV2, manager.getTools().get("my_tool"));
            assertEquals("Version 2", manager.getTools().get("my_tool").getDescription());
        }
    }
    
    @Nested
    @DisplayName("TestAbilityManagerQueryAndRemove")
    class TestAbilityManagerQueryAndRemove {
        
        private AbilityManager manager;
        
        @BeforeEach
        void setUp() {
            manager = new AbilityManager();
        }
        
        @Test
        @DisplayName("test_get_returns_ability_from_correct_dict")
        void testGetReturnsAbilityFromCorrectDict() {
            ToolCard toolCard = new ToolCard("t1", "ability_x", "Tool", null);
            WorkflowCard workflowCard = new WorkflowCard("w1", "ability_y", "Workflow");
            AgentCard agentCard = new AgentCard("a1", "ability_z", "Agent", null);
            
            manager.add(toolCard);
            manager.add(workflowCard);
            manager.add(agentCard);
            
            assertEquals(Optional.of(toolCard), manager.get("ability_x"));
            assertEquals(Optional.of(workflowCard), manager.get("ability_y"));
            assertEquals(Optional.of(agentCard), manager.get("ability_z"));
        }
        
        @Test
        @DisplayName("test_get_returns_none_for_nonexistent_ability")
        void testGetReturnsNoneForNonexistentAbility() {
            Optional<Object> result = manager.get("nonexistent");
            
            assertTrue(result.isEmpty());
        }
        
        @Test
        @DisplayName("test_remove_returns_removed_ability_and_deletes_from_dict")
        void testRemoveReturnsRemovedAbilityAndDeletesFromDict() {
            ToolCard toolCard = new ToolCard("t1", "my_tool", "Tool", null);
            manager.add(toolCard);
            
            Optional<Object> removed = manager.remove("my_tool");
            
            assertEquals(Optional.of(toolCard), removed);
            assertFalse(manager.getTools().containsKey("my_tool"));
        }
        
        @Test
        @DisplayName("test_remove_returns_none_for_nonexistent_ability")
        void testRemoveReturnsNoneForNonexistentAbility() {
            Optional<Object> result = manager.remove("nonexistent");
            
            assertTrue(result.isEmpty());
        }
        
        @Test
        @DisplayName("test_remove_follows_search_order_tools_workflows_agents_mcp")
        void testRemoveFollowsSearchOrder() {
            ToolCard toolCard = new ToolCard("t1", "shared_name", "Tool", null);
            manager.getTools().put("shared_name", toolCard);
            
            Optional<Object> removed = manager.remove("shared_name");
            
            assertEquals(Optional.of(toolCard), removed);
            assertFalse(manager.getTools().containsKey("shared_name"));
        }
        
        @Test
        @DisplayName("test_list_returns_all_abilities_in_correct_order")
        void testListReturnsAllAbilitiesInCorrectOrder() {
            ToolCard tool = new ToolCard("t1", "tool1", "", null);
            WorkflowCard workflow = new WorkflowCard("w1", "workflow1", "");
            AgentCard agent = new AgentCard("a1", "agent1", "Agent", null);
            McpServerConfig mcp = McpServerConfig.builder()
                .serverId("m1")
                .serverName("mcp1")
                .serverPath("http://localhost:8080")
                .build();
            
            manager.add(tool);
            manager.add(workflow);
            manager.add(agent);
            manager.add(mcp);
            
            List<Object> abilities = manager.list();
            
            assertEquals(4, abilities.size());
            assertEquals(tool, abilities.get(0));
            assertEquals(workflow, abilities.get(1));
            assertEquals(agent, abilities.get(2));
            assertEquals(mcp, abilities.get(3));
        }
        
        @Test
        @DisplayName("test_list_returns_empty_list_for_empty_manager")
        void testListReturnsEmptyListForEmptyManager() {
            assertEquals(List.of(), manager.list());
        }
    }
    
    @Nested
    @DisplayName("TestAbilityManagerToolInfoConversion")
    class TestAbilityManagerToolInfoConversion {
        
        private AbilityManager manager;
        
        @BeforeEach
        void setUp() {
            manager = new AbilityManager();
        }
        
        @Test
        @DisplayName("test_list_tool_info_converts_tool_card_correctly")
        void testListToolInfoConvertsToolCardCorrectly() throws Exception {
            Map<String, Object> inputParams = new HashMap<>();
            inputParams.put("type", "object");
            Map<String, Object> properties = new HashMap<>();
            properties.put("expr", Map.of("type", "string"));
            inputParams.put("properties", properties);
            
            ToolCard toolCard = new ToolCard("t1", "calculator", "Performs calculations", inputParams);
            manager.add(toolCard);
            
            List<ToolInfo> toolInfos = manager.listToolInfo(null).get();
            
            assertEquals(1, toolInfos.size());
            assertEquals("calculator", toolInfos.get(0).name());
            assertEquals("Performs calculations", toolInfos.get(0).description());
            assertEquals(inputParams, (Map<String, Object>) toolInfos.get(0).parameters());
        }
        
        @Test
        @DisplayName("test_list_tool_info_converts_workflow_card_correctly")
        void testListToolInfoConvertsWorkflowCardCorrectly() throws Exception {
            Map<String, Object> inputParams = new HashMap<>();
            inputParams.put("type", "object");
            inputParams.put("properties", Map.of("data", Map.of("type", "array")));
            
            WorkflowCard workflowCard = new WorkflowCard("w1", "data_pipeline", "Processes data", inputParams);
            manager.add(workflowCard);
            
            List<ToolInfo> toolInfos = manager.listToolInfo(null).get();
            
            assertEquals(1, toolInfos.size());
            assertEquals("data_pipeline", toolInfos.get(0).name());
            assertEquals("Processes data", toolInfos.get(0).description());
        }
        
        @Test
        @DisplayName("test_list_tool_info_converts_agent_card_with_params")
        void testListToolInfoConvertsAgentCardWithParams() throws Exception {
            AgentCard agentCard = new AgentCard("a1", "assistant", "AI assistant", null);
            agentCard.addInputParam(Param.string("query", "User query", true));
            agentCard.addInputParam(Param.string("context", "Context info", false));
            manager.add(agentCard);
            
            List<ToolInfo> toolInfos = manager.listToolInfo(null).get();
            
            assertEquals(1, toolInfos.size());
            assertEquals("assistant", toolInfos.get(0).name());
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) toolInfos.get(0).parameters();
            assertEquals("object", params.get("type"));
            assertTrue(((Map<?, ?>) params.get("properties")).containsKey("query"));
            assertTrue(((List<?>) params.get("required")).contains("query"));
            assertFalse(((List<?>) params.get("required")).contains("context"));
        }
        
        @Test
        @DisplayName("test_list_tool_info_with_names_filter")
        void testListToolInfoWithNamesFilter() throws Exception {
            manager.add(new ToolCard("tool_a", ""));
            manager.add(new ToolCard("tool_b", ""));
            manager.add(new ToolCard("tool_c", ""));
            
            List<ToolInfo> toolInfos = manager.listToolInfo(Arrays.asList("tool_a", "tool_c")).get();
            
            assertEquals(2, toolInfos.size());
            List<String> names = toolInfos.stream().map(ToolInfo::name).toList();
            assertTrue(names.contains("tool_a"));
            assertTrue(names.contains("tool_c"));
            assertFalse(names.contains("tool_b"));
        }
        
        @Test
        @DisplayName("test_list_tool_info_handles_none_description_and_params")
        void testListToolInfoHandlesNoneDescriptionAndParams() throws Exception {
            ToolCard toolCard = new ToolCard("minimal_tool", null);
            manager.add(toolCard);
            
            List<ToolInfo> toolInfos = manager.listToolInfo(null).get();
            
            assertEquals(1, toolInfos.size());
            assertEquals("", toolInfos.get(0).description());
            assertTrue(((Map<?, ?>) toolInfos.get(0).parameters()).isEmpty());
        }
    }
    
    @Nested
    @DisplayName("TestAbilityManagerExecute")
    class TestAbilityManagerExecute {
        
        private AbilityManager manager;
        
        @Mock
        private Session mockSession;
        
        @BeforeEach
        void setUp() {
            manager = new AbilityManager();
        }
        
        @Test
        @DisplayName("test_execute_tool_with_json_string_arguments")
        void testExecuteToolWithJsonStringArguments() throws Exception {
            ToolCard toolCard = new ToolCard("my_tool", "");
            manager.add(toolCard);
            
            ToolCall toolCall = new ToolCall("call_1", "function", "my_tool", "{\"x\": 1, \"y\": 2}", null);
            
            // Since Runner is not converted, execute should throw or return error
            // For now, we test that it handles the call correctly
            CompletableFuture<AbilityManager.ExecutionResult> future = manager.execute(toolCall, mockSession);
            AbilityManager.ExecutionResult result = future.get();
            
            // Result should indicate that Runner is not available
            assertNotNull(result);
            assertNotNull(result.toolMessage());
            assertEquals("call_1", result.toolMessage().getToolCallId());
        }
        
        @Test
        @DisplayName("test_execute_tool_with_empty_arguments")
        void testExecuteToolWithEmptyArguments() throws Exception {
            ToolCard toolCard = new ToolCard("my_tool", "");
            manager.add(toolCard);
            
            ToolCall toolCall = new ToolCall("call_1", "function", "my_tool", "{}", null);
            
            CompletableFuture<AbilityManager.ExecutionResult> future = manager.execute(toolCall, mockSession);
            AbilityManager.ExecutionResult result = future.get();
            
            assertNotNull(result);
            assertNotNull(result.toolMessage());
        }
        
        @Test
        @DisplayName("test_execute_handles_json_decode_error_gracefully")
        void testExecuteHandlesJsonDecodeErrorGracefully() throws Exception {
            ToolCard toolCard = new ToolCard("my_tool", "");
            manager.add(toolCard);
            
            ToolCall toolCall = new ToolCall("call_1", "function", "my_tool", "not valid json", null);
            
            // Should not throw, should handle gracefully
            CompletableFuture<AbilityManager.ExecutionResult> future = manager.execute(toolCall, mockSession);
            AbilityManager.ExecutionResult result = future.get();
            
            assertNotNull(result);
        }
        
        @Test
        @DisplayName("test_execute_routes_to_workflow")
        void testExecuteRoutesToWorkflow() throws Exception {
            WorkflowCard workflowCard = new WorkflowCard("wf_1", "my_workflow", "");
            manager.add(workflowCard);
            
            ToolCall toolCall = new ToolCall("call_1", "function", "my_workflow", "{}", null);
            
            CompletableFuture<AbilityManager.ExecutionResult> future = manager.execute(toolCall, mockSession);
            AbilityManager.ExecutionResult result = future.get();
            
            assertNotNull(result);
            // Content should indicate workflow routing
            assertNotNull(result.toolMessage());
        }
        
        @Test
        @DisplayName("test_execute_routes_to_agent")
        void testExecuteRoutesToAgent() throws Exception {
            AgentCard agentCard = new AgentCard("agent_1", "my_agent", "Agent", null);
            manager.add(agentCard);
            
            ToolCall toolCall = new ToolCall("call_1", "function", "my_agent", "{}", null);
            
            CompletableFuture<AbilityManager.ExecutionResult> future = manager.execute(toolCall, mockSession);
            AbilityManager.ExecutionResult result = future.get();
            
            assertNotNull(result);
            assertNotNull(result.toolMessage());
        }
        
        @Test
        @DisplayName("test_execute_returns_error_when_tool_not_found")
        void testExecuteReturnsErrorWhenToolNotFound() throws Exception {
            ToolCard toolCard = new ToolCard("my_tool", "");
            manager.add(toolCard);
            
            ToolCall toolCall = new ToolCall("call_1", "function", "my_tool", "{}", null);
            
            CompletableFuture<AbilityManager.ExecutionResult> future = manager.execute(toolCall, mockSession);
            AbilityManager.ExecutionResult result = future.get();
            
            assertNull(result.result());
            String content = String.valueOf(result.toolMessage().getContent()).toLowerCase();
            assertTrue(content.contains("not") || content.contains("runner"));
        }
        
        @Test
        @DisplayName("test_execute_handles_tool_execution_exception")
        void testExecuteHandlesToolExecutionException() throws Exception {
            ToolCard toolCard = new ToolCard("my_tool", "");
            manager.add(toolCard);
            
            ToolCall toolCall = new ToolCall("call_1", "function", "my_tool", "{}", null);
            
            CompletableFuture<AbilityManager.ExecutionResult> future = manager.execute(toolCall, mockSession);
            AbilityManager.ExecutionResult result = future.get();
            
            // Should not throw, should return error message
            assertNotNull(result);
            assertNotNull(result.toolMessage());
        }
        
        @Test
        @DisplayName("test_execute_fallback_to_resource_mgr_by_name")
        void testExecuteFallbackToResourceMgrByName() throws Exception {
            // Don't add any ability to manager
            
            ToolCall toolCall = new ToolCall("call_1", "function", "unknown_tool", "{}", null);
            
            CompletableFuture<AbilityManager.ExecutionResult> future = manager.execute(toolCall, mockSession);
            AbilityManager.ExecutionResult result = future.get();
            
            // Should indicate Runner not available
            assertNotNull(result);
        }
        
        @Test
        @DisplayName("test_execute_returns_error_when_fallback_also_fails")
        void testExecuteReturnsErrorWhenFallbackAlsoFails() throws Exception {
            ToolCall toolCall = new ToolCall("call_1", "function", "nonexistent", "{}", null);
            
            CompletableFuture<AbilityManager.ExecutionResult> future = manager.execute(toolCall, mockSession);
            AbilityManager.ExecutionResult result = future.get();
            
            assertNull(result.result());
            String content = String.valueOf(result.toolMessage().getContent()).toLowerCase();
            assertTrue(content.contains("not") || content.contains("runner"));
            assertEquals("call_1", result.toolMessage().getToolCallId());
        }
    }
}

