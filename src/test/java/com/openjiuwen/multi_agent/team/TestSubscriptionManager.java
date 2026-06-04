/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.multi_agent.team;

import com.openjiuwen.core.multiagent.teamruntime.SubscriptionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SubscriptionManager.
 *
 * <p>Mirrors Python's {@code test_subscription_manager.py} in
 * {@code tests.unit_tests.multi_agent.team}.
 */
class TestSubscriptionManager {
    private SubscriptionManager manager;

    @BeforeEach
    void setup() {
        manager = new SubscriptionManager();
    }

    @Test
    void testSubscribeRegistersAgentToTopic() {
        manager.subscribe("agent_a", "code_events");
        assertTrue(manager.getSubscribers("code_events").contains("agent_a"));
    }

    @Test
    void testSubscribeMultipleAgentsToSameTopic() {
        manager.subscribe("agent_a", "events");
        manager.subscribe("agent_b", "events");

        assertEquals(Set.of("agent_a", "agent_b"), manager.getSubscribers("events"));
    }

    @Test
    void testSubscribeSameAgentToMultipleTopics() {
        manager.subscribe("agent_a", "topic1");
        manager.subscribe("agent_a", "topic2");

        assertTrue(manager.getSubscribers("topic1").contains("agent_a"));
        assertTrue(manager.getSubscribers("topic2").contains("agent_a"));
    }

    @Test
    void testSubscribeIdempotentForSameAgentTopic() {
        manager.subscribe("agent_a", "events");
        manager.subscribe("agent_a", "events");

        assertEquals(1, manager.getSubscribers("events").stream().filter("agent_a"::equals).count());
    }

    @Test
    void testUnsubscribeRemovesAgentFromTopic() {
        manager.subscribe("agent_a", "events");
        manager.unsubscribe("agent_a", "events");

        assertFalse(manager.getSubscribers("events").contains("agent_a"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testUnsubscribeCleansEmptyTopicEntry() {
        manager.subscribe("agent_a", "events");
        manager.unsubscribe("agent_a", "events");

        Map<String, Object> result = manager.listSubscriptions(null);
        assertFalse(((Map<String, Object>) result.get("subscriptions")).containsKey("events"));
    }

    @Test
    void testUnsubscribeNonexistentAgentIsSafe() {
        assertDoesNotThrow(() -> manager.unsubscribe("ghost_agent", "no_topic"));
    }

    @Test
    void testUnsubscribeAllRemovesAllSubscriptions() {
        manager.subscribe("agent_a", "topic1");
        manager.subscribe("agent_a", "topic2");
        manager.unsubscribeAll("agent_a");

        assertFalse(manager.getSubscribers("topic1").contains("agent_a"));
        assertFalse(manager.getSubscribers("topic2").contains("agent_a"));
    }

    @Test
    void testUnsubscribeAllLeavesOtherAgentsIntact() {
        manager.subscribe("agent_a", "events");
        manager.subscribe("agent_b", "events");
        manager.unsubscribeAll("agent_a");

        assertTrue(manager.getSubscribers("events").contains("agent_b"));
        assertFalse(manager.getSubscribers("events").contains("agent_a"));
    }

    @Test
    void testUnsubscribeAllNonexistentAgentIsSafe() {
        assertDoesNotThrow(() -> manager.unsubscribeAll("ghost"));
    }

    @Test
    void testGetSubscribersExactMatch() {
        manager.subscribe("agent_a", "code_events");
        assertTrue(manager.getSubscribers("code_events").contains("agent_a"));
    }

    @Test
    void testGetSubscribersNoMatchReturnsEmpty() {
        assertEquals(Set.of(), manager.getSubscribers("unknown_topic"));
    }

    @Test
    void testWildcardStarMatchesAnySequence() {
        manager.subscribe("agent_a", "code_*");

        assertTrue(manager.getSubscribers("code_events").contains("agent_a"));
        assertTrue(manager.getSubscribers("code_review").contains("agent_a"));
        assertTrue(manager.getSubscribers("code_").contains("agent_a"));
    }

    @Test
    void testWildcardStarDoesNotMatchDifferentPrefix() {
        manager.subscribe("agent_a", "code_*");

        assertFalse(manager.getSubscribers("data_events").contains("agent_a"));
    }

    @Test
    void testWildcardQuestionMarkMatchesSingleChar() {
        manager.subscribe("agent_a", "event_?");

        assertTrue(manager.getSubscribers("event_A").contains("agent_a"));
        assertTrue(manager.getSubscribers("event_1").contains("agent_a"));
        assertFalse(manager.getSubscribers("event_AB").contains("agent_a"));
    }

    @Test
    void testGlobalWildcardMatchesAll() {
        manager.subscribe("agent_a", "*");

        assertTrue(manager.getSubscribers("anything").contains("agent_a"));
        assertTrue(manager.getSubscribers("code_events").contains("agent_a"));
    }

    @Test
    void testMultiplePatternsFanOut() {
        manager.subscribe("agent_a", "*");
        manager.subscribe("agent_b", "code_*");
        manager.subscribe("agent_c", "code_events");

        assertEquals(Set.of("agent_a", "agent_b", "agent_c"), manager.getSubscribers("code_events"));
    }

    @Test
    void testGetSubscriptionCountEmpty() {
        assertEquals(0, manager.getSubscriptionCount());
    }

    @Test
    void testGetSubscriptionCountIncrements() {
        manager.subscribe("agent_a", "t1");
        manager.subscribe("agent_b", "t1");
        manager.subscribe("agent_a", "t2");

        assertEquals(3, manager.getSubscriptionCount());
    }

    @Test
    void testGetSubscriptionCountDecrementsOnUnsubscribe() {
        manager.subscribe("agent_a", "t1");
        manager.subscribe("agent_a", "t2");
        manager.unsubscribe("agent_a", "t1");

        assertEquals(1, manager.getSubscriptionCount());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testListSubscriptionsAll() {
        manager.subscribe("agent_a", "t1");
        manager.subscribe("agent_b", "t2");

        Map<String, Object> result = manager.listSubscriptions(null);
        Map<String, Object> subscriptions = (Map<String, Object>) result.get("subscriptions");

        assertTrue(result.containsKey("subscriptions"));
        assertTrue(subscriptions.containsKey("t1"));
        assertTrue(subscriptions.containsKey("t2"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testListSubscriptionsFilteredByAgent() {
        manager.subscribe("agent_a", "t1");
        manager.subscribe("agent_a", "t2");
        manager.subscribe("agent_b", "t3");

        Map<String, Object> result = manager.listSubscriptions("agent_a");
        List<String> topics = (List<String>) result.get("topics");

        assertEquals("agent_a", result.get("agent_id"));
        assertTrue(topics.contains("t1"));
        assertTrue(topics.contains("t2"));
        assertFalse(topics.contains("t3"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testListSubscriptionsForUnknownAgent() {
        Map<String, Object> result = manager.listSubscriptions("unknown");

        assertEquals(List.of(), (List<String>) result.get("topics"));
    }
}
