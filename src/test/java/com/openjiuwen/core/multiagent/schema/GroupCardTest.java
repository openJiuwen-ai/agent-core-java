// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.multiagent.schema;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GroupCard.
 */
class GroupCardTest {
    
    @Test
    void testDefaultConstructor() {
        GroupCard card = new GroupCard();
        
        assertNotNull(card.getId());
        assertEquals("", card.getName());
        assertEquals("", card.getDescription());
        assertEquals("", card.getTopic());
        assertEquals("1.0.0", card.getVersion());
        assertTrue(card.getAgentCards().isEmpty());
        assertTrue(card.getTags().isEmpty());
    }
    
    @Test
    void testConstructorWithName() {
        GroupCard card = new GroupCard("test_group");
        
        assertEquals("test_group", card.getName());
        assertEquals("", card.getDescription());
        assertEquals("1.0.0", card.getVersion());
    }
    
    @Test
    void testConstructorWithNameAndDescription() {
        GroupCard card = new GroupCard("my_group", "A test group");
        
        assertEquals("my_group", card.getName());
        assertEquals("A test group", card.getDescription());
        assertEquals("1.0.0", card.getVersion());
    }
    
    @Test
    void testConstructorWithFullDetails() {
        GroupCard card = new GroupCard("prod_group", "Production group", "AI", "2.0.0");
        
        assertEquals("prod_group", card.getName());
        assertEquals("Production group", card.getDescription());
        assertEquals("AI", card.getTopic());
        assertEquals("2.0.0", card.getVersion());
    }
    
    @Test
    void testAddAgentCard() {
        GroupCard card = new GroupCard("group1");
        AgentCard agent1 = new AgentCard("agent1", "Agent 1");
        AgentCard agent2 = new AgentCard("agent2", "Agent 2");
        
        card.addAgentCard(agent1).addAgentCard(agent2);
        
        assertEquals(2, card.getAgentCards().size());
        assertEquals("agent1", card.getAgentCards().get(0).getName());
        assertEquals("agent2", card.getAgentCards().get(1).getName());
    }
    
    @Test
    void testAddTag() {
        GroupCard card = new GroupCard("group1");
        
        card.addTag("production").addTag("critical");
        
        assertEquals(2, card.getTags().size());
        assertTrue(card.getTags().contains("production"));
        assertTrue(card.getTags().contains("critical"));
    }
    
    @Test
    void testWithTopic() {
        GroupCard card = new GroupCard("group1")
            .withTopic("Machine Learning");
        
        assertEquals("Machine Learning", card.getTopic());
    }
    
    @Test
    void testWithVersion() {
        GroupCard card = new GroupCard("group1")
            .withVersion("3.0.0");
        
        assertEquals("3.0.0", card.getVersion());
    }
    
    @Test
    void testToolInfo() {
        GroupCard card = new GroupCard("test_group", "Test description");
        card.withTopic("AI").withVersion("1.0.0");
        card.addAgentCard(new AgentCard("agent1", ""));
        card.addTag("test");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> info = (Map<String, Object>) card.toolInfo();
        
        assertEquals("test_group", info.get("name"));
        assertEquals("Test description", info.get("description"));
        assertEquals("group", info.get("type"));
        assertEquals("AI", info.get("topic"));
        assertEquals("1.0.0", info.get("version"));
        assertEquals(1, info.get("agentCount"));
        
        @SuppressWarnings("unchecked")
        List<String> agents = (List<String>) info.get("agents");
        assertEquals(1, agents.size());
        assertEquals("agent1", agents.get(0));
    }
    
    @Test
    void testNullSafetyInSetters() {
        GroupCard card = new GroupCard();
        
        card.setTopic(null);
        card.setVersion(null);
        card.setTags(null);
        card.setAgentCards(null);
        
        assertEquals("", card.getTopic());
        assertEquals("1.0.0", card.getVersion());
        assertNotNull(card.getTags());
        assertNotNull(card.getAgentCards());
    }
}

