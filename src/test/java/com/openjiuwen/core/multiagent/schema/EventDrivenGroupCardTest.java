// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.multiagent.schema;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EventDrivenGroupCard.
 */
class EventDrivenGroupCardTest {
    
    @Test
    void testDefaultConstructor() {
        EventDrivenGroupCard card = new EventDrivenGroupCard();
        
        assertNotNull(card.getId());
        assertEquals("", card.getName());
        assertTrue(card.getSubscriptions().isEmpty());
    }
    
    @Test
    void testConstructorWithName() {
        EventDrivenGroupCard card = new EventDrivenGroupCard("event_group");
        
        assertEquals("event_group", card.getName());
        assertTrue(card.getSubscriptions().isEmpty());
    }
    
    @Test
    void testConstructorWithNameAndDescription() {
        EventDrivenGroupCard card = new EventDrivenGroupCard("event_group", "Event-driven group");
        
        assertEquals("event_group", card.getName());
        assertEquals("Event-driven group", card.getDescription());
    }
    
    @Test
    void testAddSubscription() {
        EventDrivenGroupCard card = new EventDrivenGroupCard("group1");
        
        card.addSubscription("agent1", "topic_a")
            .addSubscription("agent1", "topic_b")
            .addSubscription("agent2", "topic_a");
        
        List<String> agent1Topics = card.getSubscribedTopics("agent1");
        assertEquals(2, agent1Topics.size());
        assertTrue(agent1Topics.contains("topic_a"));
        assertTrue(agent1Topics.contains("topic_b"));
        
        List<String> agent2Topics = card.getSubscribedTopics("agent2");
        assertEquals(1, agent2Topics.size());
        assertTrue(agent2Topics.contains("topic_a"));
    }
    
    @Test
    void testAddSubscriptions() {
        EventDrivenGroupCard card = new EventDrivenGroupCard("group1");
        
        card.addSubscriptions("agent1", Arrays.asList("topic_a", "topic_b", "topic_c"));
        
        List<String> topics = card.getSubscribedTopics("agent1");
        assertEquals(3, topics.size());
    }
    
    @Test
    void testRemoveSubscriptions() {
        EventDrivenGroupCard card = new EventDrivenGroupCard("group1");
        card.addSubscription("agent1", "topic_a");
        card.addSubscription("agent2", "topic_b");
        
        card.removeSubscriptions("agent1");
        
        assertTrue(card.getSubscribedTopics("agent1").isEmpty());
        assertFalse(card.getSubscribedTopics("agent2").isEmpty());
    }
    
    @Test
    void testGetSubscribers() {
        EventDrivenGroupCard card = new EventDrivenGroupCard("group1");
        card.addSubscription("agent1", "shared_topic");
        card.addSubscription("agent2", "shared_topic");
        card.addSubscription("agent3", "other_topic");
        
        List<String> subscribers = card.getSubscribers("shared_topic");
        
        assertEquals(2, subscribers.size());
        assertTrue(subscribers.contains("agent1"));
        assertTrue(subscribers.contains("agent2"));
        assertFalse(subscribers.contains("agent3"));
    }
    
    @Test
    void testGetSubscribedTopicsForNonexistentAgent() {
        EventDrivenGroupCard card = new EventDrivenGroupCard("group1");
        
        List<String> topics = card.getSubscribedTopics("nonexistent");
        
        assertTrue(topics.isEmpty());
    }
    
    @Test
    void testToolInfo() {
        EventDrivenGroupCard card = new EventDrivenGroupCard("event_group", "Event description");
        card.addSubscription("agent1", "topic_a");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> info = (Map<String, Object>) card.toolInfo();
        
        assertEquals("event_group", info.get("name"));
        assertEquals("event_driven_group", info.get("type"));
        assertNotNull(info.get("subscriptions"));
    }
    
    @Test
    void testNullSafetyInSetters() {
        EventDrivenGroupCard card = new EventDrivenGroupCard();
        
        card.setSubscriptions(null);
        
        assertNotNull(card.getSubscriptions());
    }
    
    @Test
    void testNullSafetyInAddMethods() {
        EventDrivenGroupCard card = new EventDrivenGroupCard();
        
        // Should not throw
        card.addSubscription(null, "topic");
        card.addSubscription("agent", null);
        card.addSubscriptions(null, Arrays.asList("topic"));
        card.addSubscriptions("agent", null);
        card.removeSubscriptions(null);
        
        assertTrue(card.getSubscriptions().isEmpty());
    }
}

