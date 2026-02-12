// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.multiagent;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.multiagent.schema.GroupCard;
import com.openjiuwen.core.session.AgentGroupSessionWrapper;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BaseGroup.
 * 
 * <p>Converted from Python: agent-core/tests/unit_tests/core/multi_agent/test_group.py
 */
class BaseGroupTest {
    
    /**
     * Concrete implementation of BaseGroup for testing purposes.
     */
    static class ConcreteGroup extends BaseGroup {
        
        public ConcreteGroup(GroupCard card) {
            super(card);
        }
        
        public ConcreteGroup(GroupCard card, GroupConfig config) {
            super(card, config);
        }
        
        @Override
        public CompletableFuture<Object> invoke(Object message, AgentGroupSessionWrapper session) {
            Map<String, Object> result = new HashMap<>();
            result.put("message", message);
            result.put("agents", listAgents());
            return CompletableFuture.completedFuture(result);
        }
        
        @Override
        public CompletableFuture<Stream<Object>> stream(Object message, AgentGroupSessionWrapper session) {
            List<Object> chunks = new ArrayList<>();
            for (String agentId : agents.keySet()) {
                Map<String, Object> chunk = new HashMap<>();
                chunk.put("agent", agentId);
                chunk.put("chunk", message);
                chunks.add(chunk);
            }
            return CompletableFuture.completedFuture(chunks.stream());
        }
    }
    
    /**
     * Create a mock agent with card.name attribute.
     */
    static BaseAgent createMockAgent(String name, boolean withController) {
        BaseAgent agent = mock(BaseAgent.class);
        AgentCard card = new AgentCard(name, "Mock agent " + name);
        when(agent.getCard()).thenReturn(card);
        return agent;
    }
    
    // ========== TestBaseGroupInitialization ==========
    
    @Nested
    class TestBaseGroupInitialization {
        
        @Test
        void testInitWithCardOnlyUsesDefaultConfig() {
            GroupCard card = new GroupCard("test_group", "Test");
            
            ConcreteGroup group = new ConcreteGroup(card);
            
            assertSame(card, group.getCard());
            assertEquals("test_group", group.getGroupId());
            assertNotNull(group.getConfig());
            assertEquals(10, group.getConfig().getMaxAgents());
            assertEquals(100, group.getConfig().getMaxConcurrentMessages());
            assertEquals(30.0, group.getConfig().getMessageTimeout(), 0.001);
            assertTrue(group.getAgents().isEmpty());
        }
        
        @Test
        void testInitWithCardAndCustomConfig() {
            GroupCard card = new GroupCard("custom_group");
            GroupConfig config = new GroupConfig(5, 60.0);
            
            ConcreteGroup group = new ConcreteGroup(card, config);
            
            assertSame(card, group.getCard());
            assertSame(config, group.getConfig());
            assertEquals(5, group.getConfig().getMaxAgents());
            assertEquals(60.0, group.getConfig().getMessageTimeout(), 0.001);
        }
        
        @Test
        void testGroupIdEqualsCardName() {
            GroupCard card = new GroupCard("my_unique_group");
            
            ConcreteGroup group = new ConcreteGroup(card);
            
            assertEquals("my_unique_group", group.getGroupId());
        }
        
        @Test
        void testAgentsInitializedAsEmptyMap() {
            GroupCard card = new GroupCard("group");
            
            ConcreteGroup group = new ConcreteGroup(card);
            
            assertNotNull(group.getAgents());
            assertEquals(0, group.getAgents().size());
        }
    }
    
    // ========== TestBaseGroupConfigure ==========
    
    @Nested
    class TestBaseGroupConfigure {
        
        @Test
        void testConfigureUpdatesConfigAndReturnsSelf() {
            GroupCard card = new GroupCard("group");
            ConcreteGroup group = new ConcreteGroup(card);
            GroupConfig newConfig = new GroupConfig(20, 120.0);
            
            BaseGroup result = group.configure(newConfig);
            
            assertSame(group, result);
            assertSame(newConfig, group.getConfig());
            assertEquals(20, group.getConfig().getMaxAgents());
            assertEquals(120.0, group.getConfig().getMessageTimeout(), 0.001);
        }
        
        @Test
        void testConfigureChainWithOtherMethods() {
            GroupCard card = new GroupCard("group");
            ConcreteGroup group = new ConcreteGroup(card);
            BaseAgent agent = createMockAgent("agent1", false);
            GroupConfig newConfig = new GroupConfig(5, 60.0);
            
            BaseGroup result = group.configure(newConfig).addAgent(agent);
            
            assertSame(group, result);
            assertEquals(5, group.getConfig().getMaxAgents());
            assertTrue(group.getAgents().containsKey("agent1"));
        }
    }
    
    // ========== TestBaseGroupAddAgent ==========
    
    @Nested
    class TestBaseGroupAddAgent {
        
        @Test
        void testAddAgentWithCardNameRegistersSuccessfully() {
            GroupCard card = new GroupCard("group");
            ConcreteGroup group = new ConcreteGroup(card);
            BaseAgent agent = createMockAgent("agent_one", false);
            
            BaseGroup result = group.addAgent(agent);
            
            assertSame(group, result);
            assertTrue(group.getAgents().containsKey("agent_one"));
            assertSame(agent, group.getAgents().get("agent_one"));
        }
        
        @Test
        void testAddAgentWithCustomIdUsesProvidedId() {
            GroupCard card = new GroupCard("group");
            ConcreteGroup group = new ConcreteGroup(card);
            BaseAgent agent = createMockAgent("original_name", false);
            
            BaseGroup result = group.addAgent(agent, "custom_identifier");
            
            assertSame(group, result);
            assertTrue(group.getAgents().containsKey("custom_identifier"));
            assertFalse(group.getAgents().containsKey("original_name"));
            assertSame(agent, group.getAgents().get("custom_identifier"));
        }
        
        @Test
        void testAddAgentSyncsAgentCardToGroupCard() {
            GroupCard card = new GroupCard("group");
            ConcreteGroup group = new ConcreteGroup(card);
            BaseAgent agent = createMockAgent("agent1", false);
            
            group.addAgent(agent);
            
            assertEquals(1, group.getCard().getAgentCards().size());
            assertSame(agent.getCard(), group.getCard().getAgentCards().get(0));
        }
        
        @Test
        void testAddAgentWithoutControllerDoesNotFail() {
            GroupCard card = new GroupCard("group");
            ConcreteGroup group = new ConcreteGroup(card);
            BaseAgent agent = createMockAgent("agent1", false);
            
            group.addAgent(agent);
            
            assertTrue(group.getAgents().containsKey("agent1"));
        }
        
        @Test
        void testAddAgentChainMultipleAgents() {
            GroupCard card = new GroupCard("group");
            ConcreteGroup group = new ConcreteGroup(card);
            BaseAgent agent1 = createMockAgent("agent1", false);
            BaseAgent agent2 = createMockAgent("agent2", false);
            BaseAgent agent3 = createMockAgent("agent3", false);
            
            BaseGroup result = group.addAgent(agent1).addAgent(agent2).addAgent(agent3);
            
            assertSame(group, result);
            assertEquals(3, group.getAgents().size());
            assertTrue(group.getAgents().containsKey("agent1"));
            assertTrue(group.getAgents().containsKey("agent2"));
            assertTrue(group.getAgents().containsKey("agent3"));
            assertEquals(3, group.getCard().getAgentCards().size());
        }
        
        @Test
        void testAddAgentRaisesWhenNoCardNameAndNoAgentId() {
            GroupCard card = new GroupCard("group");
            ConcreteGroup group = new ConcreteGroup(card);
            BaseAgent agent = mock(BaseAgent.class);
            when(agent.getCard()).thenReturn(null);
            
            JiuWenBaseException exception = assertThrows(
                JiuWenBaseException.class,
                () -> group.addAgent(agent)
            );
            
            assertEquals(StatusCode.AGENT_GROUP_ADD_FAILED.getCode(), exception.getErrorCode());
            assertTrue(exception.getMessage().contains("Agent must have card.name or provide agent_id"));
        }
        
        @Test
        void testAddAgentRaisesWhenAgentIdAlreadyExists() {
            GroupCard card = new GroupCard("group");
            ConcreteGroup group = new ConcreteGroup(card);
            BaseAgent agent1 = createMockAgent("duplicate_id", false);
            BaseAgent agent2 = createMockAgent("duplicate_id", false);
            group.addAgent(agent1);
            
            JiuWenBaseException exception = assertThrows(
                JiuWenBaseException.class,
                () -> group.addAgent(agent2)
            );
            
            assertEquals(StatusCode.AGENT_GROUP_ADD_FAILED.getCode(), exception.getErrorCode());
            assertTrue(exception.getMessage().contains("already exists"));
        }
        
        @Test
        void testAddAgentRaisesWhenExceedsMaxAgents() {
            GroupCard card = new GroupCard("group");
            GroupConfig config = new GroupConfig(2, 30.0);
            ConcreteGroup group = new ConcreteGroup(card, config);
            
            group.addAgent(createMockAgent("agent1", false));
            group.addAgent(createMockAgent("agent2", false));
            
            JiuWenBaseException exception = assertThrows(
                JiuWenBaseException.class,
                () -> group.addAgent(createMockAgent("agent3", false))
            );
            
            assertEquals(StatusCode.AGENT_GROUP_ADD_FAILED.getCode(), exception.getErrorCode());
            assertTrue(exception.getMessage().contains("exceeds max_agents"));
        }
    }
    
    // ========== TestBaseGroupRemoveAgent ==========
    
    @Nested
    class TestBaseGroupRemoveAgent {
        
        @Test
        void testRemoveAgentByIdStringRemovesFromAgents() {
            GroupCard card = new GroupCard("group");
            ConcreteGroup group = new ConcreteGroup(card);
            BaseAgent agent = createMockAgent("agent_to_remove", false);
            group.addAgent(agent);
            
            BaseGroup result = group.removeAgent("agent_to_remove");
            
            assertSame(group, result);
            assertFalse(group.getAgents().containsKey("agent_to_remove"));
        }
        
        @Test
        void testRemoveAgentSyncsAgentCards() {
            GroupCard card = new GroupCard("group");
            ConcreteGroup group = new ConcreteGroup(card);
            BaseAgent agent = createMockAgent("agent1", false);
            group.addAgent(agent);
            assertEquals(1, group.getCard().getAgentCards().size());
            
            group.removeAgent("agent1");
            
            assertEquals(0, group.getCard().getAgentCards().size());
        }
        
        @Test
        void testRemoveAgentByInstanceUsesCardName() {
            GroupCard card = new GroupCard("group");
            ConcreteGroup group = new ConcreteGroup(card);
            BaseAgent agent = createMockAgent("agent_instance", false);
            group.addAgent(agent);
            
            BaseGroup result = group.removeAgent(agent);
            
            assertSame(group, result);
            assertFalse(group.getAgents().containsKey("agent_instance"));
        }
        
        @Test
        void testRemoveAgentNonexistentIdReturnsSelfSilently() {
            GroupCard card = new GroupCard("group");
            ConcreteGroup group = new ConcreteGroup(card);
            
            BaseGroup result = group.removeAgent("nonexistent_agent");
            
            assertSame(group, result);
        }
        
        @Test
        void testRemoveAgentInstanceWithoutCardNameLogsWarning() {
            GroupCard card = new GroupCard("group");
            ConcreteGroup group = new ConcreteGroup(card);
            BaseAgent agentNoCard = mock(BaseAgent.class);
            when(agentNoCard.getCard()).thenReturn(null);
            
            BaseGroup result = group.removeAgent(agentNoCard);
            
            assertSame(group, result);
            // Warning is logged but no exception thrown
        }
        
        @Test
        void testRemoveAgentChainMultipleRemovals() {
            GroupCard card = new GroupCard("group");
            ConcreteGroup group = new ConcreteGroup(card);
            group.addAgent(createMockAgent("a1", false));
            group.addAgent(createMockAgent("a2", false));
            group.addAgent(createMockAgent("a3", false));
            
            BaseGroup result = group.removeAgent("a1").removeAgent("a2");
            
            assertSame(group, result);
            assertEquals(1, group.getAgents().size());
            assertTrue(group.getAgents().containsKey("a3"));
        }
    }
    
    // ========== TestBaseGroupQueryMethods ==========
    
    @Nested
    class TestBaseGroupQueryMethods {
        
        @Test
        void testGetAgentReturnsAgentWhenExists() {
            GroupCard card = new GroupCard("group");
            ConcreteGroup group = new ConcreteGroup(card);
            BaseAgent agent = createMockAgent("target_agent", false);
            group.addAgent(agent);
            
            BaseAgent result = group.getAgent("target_agent");
            
            assertSame(agent, result);
        }
        
        @Test
        void testGetAgentReturnsNullWhenNotExists() {
            GroupCard card = new GroupCard("group");
            ConcreteGroup group = new ConcreteGroup(card);
            
            BaseAgent result = group.getAgent("nonexistent");
            
            assertNull(result);
        }
        
        @Test
        void testGetAgentCountReturnsZeroForEmptyGroup() {
            GroupCard card = new GroupCard("group");
            ConcreteGroup group = new ConcreteGroup(card);
            
            int count = group.getAgentCount();
            
            assertEquals(0, count);
        }
        
        @Test
        void testGetAgentCountReturnsCorrectCount() {
            GroupCard card = new GroupCard("group");
            ConcreteGroup group = new ConcreteGroup(card);
            group.addAgent(createMockAgent("a1", false));
            group.addAgent(createMockAgent("a2", false));
            group.addAgent(createMockAgent("a3", false));
            
            int count = group.getAgentCount();
            
            assertEquals(3, count);
        }
        
        @Test
        void testListAgentsReturnsEmptyListForEmptyGroup() {
            GroupCard card = new GroupCard("group");
            ConcreteGroup group = new ConcreteGroup(card);
            
            List<String> agents = group.listAgents();
            
            assertTrue(agents.isEmpty());
        }
        
        @Test
        void testListAgentsReturnsAllAgentIdsInOrder() {
            GroupCard card = new GroupCard("group");
            ConcreteGroup group = new ConcreteGroup(card);
            group.addAgent(createMockAgent("first", false));
            group.addAgent(createMockAgent("second", false));
            group.addAgent(createMockAgent("third", false));
            
            List<String> agents = group.listAgents();
            
            assertEquals(Arrays.asList("first", "second", "third"), agents);
        }
    }
    
    // ========== TestBaseGroupAbstractMethods ==========
    
    @Nested
    class TestBaseGroupAbstractMethods {
        
        @Test
        void testConcreteGroupInvokeReturnsExpectedResult() throws Exception {
            GroupCard card = new GroupCard("group");
            ConcreteGroup group = new ConcreteGroup(card);
            group.addAgent(createMockAgent("agent1", false));
            group.addAgent(createMockAgent("agent2", false));
            
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) group.invoke("test_message", null).get();
            
            assertEquals("test_message", result.get("message"));
            @SuppressWarnings("unchecked")
            List<String> agents = (List<String>) result.get("agents");
            assertTrue(agents.contains("agent1"));
            assertTrue(agents.contains("agent2"));
        }
        
        @Test
        void testConcreteGroupStreamYieldsChunksForEachAgent() throws Exception {
            GroupCard card = new GroupCard("group");
            ConcreteGroup group = new ConcreteGroup(card);
            group.addAgent(createMockAgent("agent_a", false));
            group.addAgent(createMockAgent("agent_b", false));
            
            List<Object> chunks = group.stream("stream_msg", null).get().toList();
            
            assertEquals(2, chunks.size());
            List<String> agentIds = new ArrayList<>();
            for (Object chunk : chunks) {
                @SuppressWarnings("unchecked")
                Map<String, Object> chunkMap = (Map<String, Object>) chunk;
                agentIds.add((String) chunkMap.get("agent"));
            }
            assertTrue(agentIds.contains("agent_a"));
            assertTrue(agentIds.contains("agent_b"));
        }
    }
    
    // ========== TestBaseGroupIntegration ==========
    
    @Nested
    class TestBaseGroupIntegration {
        
        @Test
        void testFullLifecycleAddQueryRemoveAgents() {
            GroupCard card = new GroupCard("lifecycle_group", "Integration test");
            GroupConfig config = new GroupConfig(5, 60.0);
            ConcreteGroup group = new ConcreteGroup(card, config);
            
            // Add agents
            BaseAgent agent1 = createMockAgent("worker1", false);
            BaseAgent agent2 = createMockAgent("worker2", false);
            BaseAgent agent3 = createMockAgent("worker3", false);
            
            group.addAgent(agent1).addAgent(agent2).addAgent(agent3);
            
            // Verify state
            assertEquals(3, group.getAgentCount());
            assertEquals(Arrays.asList("worker1", "worker2", "worker3"), group.listAgents());
            assertSame(agent2, group.getAgent("worker2"));
            assertEquals(3, group.getCard().getAgentCards().size());
            
            // Remove one agent
            group.removeAgent("worker2");
            
            assertEquals(2, group.getAgentCount());
            assertFalse(group.listAgents().contains("worker2"));
            assertNull(group.getAgent("worker2"));
        }
        
        @Test
        void testConfigureAfterAddingAgents() {
            GroupCard card = new GroupCard("reconfig_group");
            ConcreteGroup group = new ConcreteGroup(card);
            
            group.addAgent(createMockAgent("a1", false));
            group.addAgent(createMockAgent("a2", false));
            
            GroupConfig newConfig = new GroupConfig(2, 120.0);
            group.configure(newConfig);
            
            // Existing agents remain
            assertEquals(2, group.getAgentCount());
            // New config applied
            assertEquals(2, group.getConfig().getMaxAgents());
            assertEquals(120.0, group.getConfig().getMessageTimeout(), 0.001);
            
            // Cannot add more agents now
            assertThrows(JiuWenBaseException.class,
                () -> group.addAgent(createMockAgent("a3", false)));
        }
    }
}

