// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.common.schema.Param;
import com.openjiuwen.core.common.schema.ParamType;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BaseAgent - ability management and parallel execution.
 * 
 * <p>Python reference: agent-core/tests/unit_tests/core/single_agent/test_base_agent.py
 */
@ExtendWith(MockitoExtension.class)
class BaseAgentTest {
    
    /**
     * Concrete implementation of BaseAgent for testing purposes.
     */
    static class ConcreteAgent extends BaseAgent {
        
        private Object config;
        
        public ConcreteAgent(AgentCard card) {
            super(card);
        }
        
        @Override
        public BaseAgent configure(Object config) {
            this.config = config;
            return this;
        }
        
        @Override
        public CompletableFuture<Object> invoke(Object inputs, Session session) {
            return CompletableFuture.completedFuture(Map.of("output", "test_output", "result_type", "answer"));
        }
        
        @Override
        public CompletableFuture<Iterator<Object>> stream(Object inputs, Session session, List<StreamMode> streamModes) {
            return CompletableFuture.completedFuture(List.<Object>of(
                Map.of("output", "chunk1"),
                Map.of("output", "chunk2")
            ).iterator());
        }
    }
    
    @Nested
    @DisplayName("TestBaseAgentConstruction")
    class TestBaseAgentConstruction {
        
        @Test
        @DisplayName("test_init_with_agent_card_sets_card_and_ability_manager")
        void testInitWithAgentCardSetsCardAndAbilityManager() {
            AgentCard card = new AgentCard("agent_1", "test_agent", "Test agent", null);
            
            ConcreteAgent agent = new ConcreteAgent(card);
            
            assertEquals(card, agent.getCard());
            assertNotNull(agent.getAbilityManager());
            assertInstanceOf(AbilityManager.class, agent.getAbilityManager());
        }
    }
    
    @Nested
    @DisplayName("TestBaseAgentAbilityManagement")
    class TestBaseAgentAbilityManagement {
        
        private ConcreteAgent agent;
        
        @BeforeEach
        void setUp() {
            AgentCard card = new AgentCard("a1", "agent", "Agent", null);
            agent = new ConcreteAgent(card);
        }
        
        @Test
        @DisplayName("test_add_ability_single_returns_self_for_chaining")
        void testAddAbilitySingleReturnsSelfForChaining() {
            ToolCard toolCard = new ToolCard("tool", "");
            
            BaseAgent result = agent.addAbility(toolCard);
            
            assertSame(agent, result);
            assertEquals(toolCard, agent.getAbility("tool").orElse(null));
        }
        
        @Test
        @DisplayName("test_add_ability_list_adds_all_and_returns_self")
        void testAddAbilityListAddsAllAndReturnsSelf() {
            ToolCard tool1 = new ToolCard("tool1", "");
            ToolCard tool2 = new ToolCard("tool2", "");
            WorkflowCard workflow = new WorkflowCard("w1", "workflow1", "");
            
            BaseAgent result = agent.addAbility(Arrays.asList(tool1, tool2, workflow));
            
            assertSame(agent, result);
            assertEquals(tool1, agent.getAbility("tool1").orElse(null));
            assertEquals(tool2, agent.getAbility("tool2").orElse(null));
            assertEquals(workflow, agent.getAbility("workflow1").orElse(null));
        }
        
        @Test
        @DisplayName("test_remove_ability_single_returns_self_for_chaining")
        void testRemoveAbilitySingleReturnsSelfForChaining() {
            ToolCard tool = new ToolCard("tool", "");
            agent.addAbility(tool);
            
            BaseAgent result = agent.removeAbility("tool");
            
            assertSame(agent, result);
            assertTrue(agent.getAbility("tool").isEmpty());
        }
        
        @Test
        @DisplayName("test_remove_ability_list_removes_all_and_returns_self")
        void testRemoveAbilityListRemovesAllAndReturnsSelf() {
            ToolCard tool1 = new ToolCard("tool1", "");
            ToolCard tool2 = new ToolCard("tool2", "");
            agent.addAbility(Arrays.asList(tool1, tool2));
            
            BaseAgent result = agent.removeAbility(Arrays.asList("tool1", "tool2"));
            
            assertSame(agent, result);
            assertTrue(agent.getAbility("tool1").isEmpty());
            assertTrue(agent.getAbility("tool2").isEmpty());
        }
        
        @Test
        @DisplayName("test_list_abilities_returns_all_registered_abilities")
        void testListAbilitiesReturnsAllRegisteredAbilities() {
            ToolCard tool = new ToolCard("tool", "");
            WorkflowCard workflow = new WorkflowCard("w1", "workflow", "");
            agent.addAbility(Arrays.asList(tool, workflow));
            
            List<Object> abilities = agent.listAbilities();
            
            assertEquals(2, abilities.size());
            assertTrue(abilities.contains(tool));
            assertTrue(abilities.contains(workflow));
        }
        
        @Test
        @DisplayName("test_list_tool_info_delegates_to_ability_manager")
        void testListToolInfoDelegatesToAbilityManager() throws Exception {
            ToolCard tool = new ToolCard("my_tool", "Tool desc");
            agent.addAbility(tool);
            
            List<ToolInfo> toolInfos = agent.listToolInfo(null).get();
            
            assertEquals(1, toolInfos.size());
            assertEquals("my_tool", toolInfos.get(0).name());
        }
        
        @Test
        @DisplayName("test_list_tool_info_with_names_filter")
        void testListToolInfoWithNamesFilter() throws Exception {
            agent.addAbility(new ToolCard("tool1", ""));
            agent.addAbility(new ToolCard("tool2", ""));
            
            List<ToolInfo> toolInfos = agent.listToolInfo(List.of("tool1")).get();
            
            assertEquals(1, toolInfos.size());
            assertEquals("tool1", toolInfos.get(0).name());
        }
    }
    
    @Nested
    @DisplayName("TestBaseAgentGetToolInfo")
    class TestBaseAgentGetToolInfo {
        
        @Test
        @DisplayName("test_get_tool_info_returns_tool_info_with_card_fields")
        void testGetToolInfoReturnsToolInfoWithCardFields() {
            AgentCard card = new AgentCard("a1", "my_agent", "Agent description", null);
            ConcreteAgent agent = new ConcreteAgent(card);
            
            ToolInfo toolInfo = agent.getToolInfo();
            
            assertNotNull(toolInfo);
            assertEquals("my_agent", toolInfo.name());
            assertEquals("Agent description", toolInfo.description());
        }
        
        @Test
        @DisplayName("test_get_tool_info_with_input_params_builds_parameters_schema")
        void testGetToolInfoWithInputParamsBuildsParametersSchema() {
            AgentCard card = new AgentCard("a1", "my_agent", "Agent", null);
            card.addInputParam(Param.string("query", "User query", true));
            card.addInputParam(Param.integer("limit", "Max results", false));
            ConcreteAgent agent = new ConcreteAgent(card);
            
            ToolInfo toolInfo = agent.getToolInfo();
            
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) toolInfo.parameters();
            assertEquals("object", params.get("type"));
            assertTrue(((Map<?, ?>) params.get("properties")).containsKey("query"));
            assertTrue(((Map<?, ?>) params.get("properties")).containsKey("limit"));
            assertTrue(((List<?>) params.get("required")).contains("query"));
            assertFalse(((List<?>) params.get("required")).contains("limit"));
        }
        
        @Test
        @DisplayName("test_get_tool_info_with_empty_input_params_returns_empty_schema")
        void testGetToolInfoWithEmptyInputParamsReturnsEmptySchema() {
            AgentCard card = new AgentCard("a1", "my_agent", "", null);
            ConcreteAgent agent = new ConcreteAgent(card);
            
            ToolInfo toolInfo = agent.getToolInfo();
            
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) toolInfo.parameters();
            assertEquals("object", params.get("type"));
            assertTrue(((Map<?, ?>) params.get("properties")).isEmpty());
            assertTrue(((List<?>) params.get("required")).isEmpty());
        }
        
        @Test
        @DisplayName("test_get_tool_info_handles_none_description")
        void testGetToolInfoHandlesNoneDescription() {
            AgentCard card = new AgentCard("a1", "my_agent", null, null);
            ConcreteAgent agent = new ConcreteAgent(card);
            
            ToolInfo toolInfo = agent.getToolInfo();
            
            assertEquals("", toolInfo.description());
        }
    }
    
    @Nested
    @DisplayName("TestBaseAgentExecuteAbility")
    class TestBaseAgentExecuteAbility {
        
        @Mock
        private Session mockSession;
        
        @Test
        @DisplayName("test_execute_ability_single_tool_call_returns_list_with_one_result")
        void testExecuteAbilitySingleToolCallReturnsListWithOneResult() throws Exception {
            AgentCard card = new AgentCard("a1", "agent", "Agent", null);
            ConcreteAgent agent = new ConcreteAgent(card);
            
            ToolCall toolCall = new ToolCall("call_1", "function", "tool", "{}", null);
            
            List<AbilityManager.ExecutionResult> results = agent.executeAbility(toolCall, mockSession).get();
            
            assertEquals(1, results.size());
            assertNotNull(results.get(0).toolMessage());
        }
        
        @Test
        @DisplayName("test_execute_ability_multiple_tool_calls_executes_in_parallel")
        void testExecuteAbilityMultipleToolCallsExecutesInParallel() throws Exception {
            AgentCard card = new AgentCard("a1", "agent", "Agent", null);
            ConcreteAgent agent = new ConcreteAgent(card);
            
            List<ToolCall> toolCalls = Arrays.asList(
                new ToolCall("call_1", "function", "tool1", "{}", null),
                new ToolCall("call_2", "function", "tool2", "{}", null),
                new ToolCall("call_3", "function", "tool3", "{}", null)
            );
            
            List<AbilityManager.ExecutionResult> results = agent.executeAbility(toolCalls, mockSession).get();
            
            assertEquals(3, results.size());
            // All tool calls should have been executed
            List<String> callIds = results.stream()
                .map(r -> r.toolMessage().getToolCallId())
                .toList();
            assertTrue(callIds.contains("call_1"));
            assertTrue(callIds.contains("call_2"));
            assertTrue(callIds.contains("call_3"));
        }
        
        @Test
        @DisplayName("test_execute_ability_catches_exception_and_returns_error_message")
        void testExecuteAbilityCatchesExceptionAndReturnsErrorMessage() throws Exception {
            AgentCard card = new AgentCard("a1", "agent", "Agent", null);
            ConcreteAgent agent = new ConcreteAgent(card);
            
            List<ToolCall> toolCalls = Arrays.asList(
                new ToolCall("call_1", "function", "tool1", "{}", null),
                new ToolCall("call_2", "function", "tool2", "{}", null)
            );
            
            List<AbilityManager.ExecutionResult> results = agent.executeAbility(toolCalls, mockSession).get();
            
            assertEquals(2, results.size());
            
            // Results should have tool call IDs
            AbilityManager.ExecutionResult result1 = results.stream()
                .filter(r -> "call_1".equals(r.toolMessage().getToolCallId()))
                .findFirst().orElse(null);
            AbilityManager.ExecutionResult result2 = results.stream()
                .filter(r -> "call_2".equals(r.toolMessage().getToolCallId()))
                .findFirst().orElse(null);
            
            assertNotNull(result1);
            assertNotNull(result2);
            assertEquals("call_1", result1.toolMessage().getToolCallId());
            assertEquals("call_2", result2.toolMessage().getToolCallId());
        }
        
        @Test
        @DisplayName("test_execute_ability_all_succeed_returns_all_results")
        void testExecuteAbilityAllSucceedReturnsAllResults() throws Exception {
            AgentCard card = new AgentCard("a1", "agent", "Agent", null);
            ConcreteAgent agent = new ConcreteAgent(card);
            
            List<ToolCall> toolCalls = Arrays.asList(
                new ToolCall("call_1", "function", "tool1", "{}", null),
                new ToolCall("call_2", "function", "tool2", "{}", null)
            );
            
            List<AbilityManager.ExecutionResult> results = agent.executeAbility(toolCalls, mockSession).get();
            
            assertEquals(2, results.size());
            assertNotNull(results.get(0).toolMessage());
            assertNotNull(results.get(1).toolMessage());
        }
    }
    
    @Nested
    @DisplayName("TestBaseAgentChainedConfiguration")
    class TestBaseAgentChainedConfiguration {
        
        @Test
        @DisplayName("test_add_ability_chain_multiple_operations")
        void testAddAbilityChainMultipleOperations() {
            AgentCard card = new AgentCard("a1", "agent", "Agent", null);
            ConcreteAgent agent = new ConcreteAgent(card);
            
            BaseAgent result = agent
                .addAbility(new ToolCard("tool1", ""))
                .addAbility(new ToolCard("tool2", ""))
                .addAbility(new WorkflowCard("w1", "workflow1", ""));
            
            assertSame(agent, result);
            List<Object> abilities = agent.listAbilities();
            assertEquals(3, abilities.size());
        }
        
        @Test
        @DisplayName("test_mixed_add_remove_chain")
        void testMixedAddRemoveChain() {
            AgentCard card = new AgentCard("a1", "agent", "Agent", null);
            ConcreteAgent agent = new ConcreteAgent(card);
            
            BaseAgent result = agent
                .addAbility(new ToolCard("tool1", ""))
                .addAbility(new ToolCard("tool2", ""))
                .removeAbility("tool1");
            
            assertSame(agent, result);
            assertTrue(agent.getAbility("tool1").isEmpty());
            assertTrue(agent.getAbility("tool2").isPresent());
        }
    }
}
