package com.openjiuwen.core.multiagent.team_runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code test_subscription_manager.py} in
 * {@code tests/unit_tests/multi_agent/team/test_subscription_manager.py}.
 */
class SubscriptionManagerTest {

    private SubscriptionManager manager;

    @BeforeEach
    void setUp() {
        manager = new SubscriptionManager();
    }

    @Test
    void subscribeRegistersAgentToTopic() {
        manager.subscribe("agent_a", "code_events");

        assertThat(manager.getSubscribers("code_events")).contains("agent_a");
    }

    @Test
    void subscribeMultipleAgentsToSameTopic() {
        manager.subscribe("agent_a", "events");
        manager.subscribe("agent_b", "events");

        assertThat(manager.getSubscribers("events")).containsExactlyInAnyOrder("agent_a", "agent_b");
    }

    @Test
    void subscribeSameAgentToMultipleTopics() {
        manager.subscribe("agent_a", "topic1");
        manager.subscribe("agent_a", "topic2");

        assertThat(manager.getSubscribers("topic1")).contains("agent_a");
        assertThat(manager.getSubscribers("topic2")).contains("agent_a");
    }

    @Test
    void subscribeIsIdempotentForSameAgentTopic() {
        manager.subscribe("agent_a", "events");
        manager.subscribe("agent_a", "events");

        assertThat(manager.getSubscribers("events")).containsExactly("agent_a");
    }

    @Test
    void unsubscribeRemovesAgentFromTopic() {
        manager.subscribe("agent_a", "events");

        manager.unsubscribe("agent_a", "events");

        assertThat(manager.getSubscribers("events")).doesNotContain("agent_a");
    }

    @Test
    void unsubscribeCleansEmptyTopicEntry() {
        manager.subscribe("agent_a", "events");

        manager.unsubscribe("agent_a", "events");

        Map<String, Object> result = manager.listSubscriptions();
        @SuppressWarnings("unchecked")
        Map<String, ?> subscriptions = (Map<String, ?>) result.get("subscriptions");
        assertThat(subscriptions).doesNotContainKey("events");
    }

    @Test
    void unsubscribeNonexistentAgentIsSafe() {
        manager.unsubscribe("ghost_agent", "no_topic");

        assertThat(manager.getSubscriptionCount()).isZero();
    }

    @Test
    void unsubscribeAllRemovesAllSubscriptions() {
        manager.subscribe("agent_a", "topic1");
        manager.subscribe("agent_a", "topic2");

        manager.unsubscribeAll("agent_a");

        assertThat(manager.getSubscribers("topic1")).doesNotContain("agent_a");
        assertThat(manager.getSubscribers("topic2")).doesNotContain("agent_a");
    }

    @Test
    void unsubscribeAllLeavesOtherAgentsIntact() {
        manager.subscribe("agent_a", "events");
        manager.subscribe("agent_b", "events");

        manager.unsubscribeAll("agent_a");

        assertThat(manager.getSubscribers("events")).containsExactly("agent_b");
    }

    @Test
    void unsubscribeAllNonexistentAgentIsSafe() {
        manager.unsubscribeAll("ghost");

        assertThat(manager.getSubscriptionCount()).isZero();
    }

    @Test
    void getSubscribersExactMatch() {
        manager.subscribe("agent_a", "code_events");

        assertThat(manager.getSubscribers("code_events")).contains("agent_a");
    }

    @Test
    void getSubscribersNoMatchReturnsEmpty() {
        assertThat(manager.getSubscribers("unknown_topic")).isEmpty();
    }

    @Test
    void wildcardStarMatchesAnySequence() {
        manager.subscribe("agent_a", "code_*");

        assertThat(manager.getSubscribers("code_events")).contains("agent_a");
        assertThat(manager.getSubscribers("code_review")).contains("agent_a");
        assertThat(manager.getSubscribers("code_")).contains("agent_a");
    }

    @Test
    void wildcardStarDoesNotMatchDifferentPrefix() {
        manager.subscribe("agent_a", "code_*");

        assertThat(manager.getSubscribers("data_events")).doesNotContain("agent_a");
    }

    @Test
    void wildcardQuestionMarkMatchesSingleCharacter() {
        manager.subscribe("agent_a", "event_?");

        assertThat(manager.getSubscribers("event_A")).contains("agent_a");
        assertThat(manager.getSubscribers("event_1")).contains("agent_a");
        assertThat(manager.getSubscribers("event_AB")).doesNotContain("agent_a");
    }

    @Test
    void globalWildcardMatchesAll() {
        manager.subscribe("agent_a", "*");

        assertThat(manager.getSubscribers("anything")).contains("agent_a");
        assertThat(manager.getSubscribers("code_events")).contains("agent_a");
    }

    @Test
    void multiplePatternsFanOut() {
        manager.subscribe("agent_a", "*");
        manager.subscribe("agent_b", "code_*");
        manager.subscribe("agent_c", "code_events");

        assertThat(manager.getSubscribers("code_events"))
                .containsExactlyInAnyOrder("agent_a", "agent_b", "agent_c");
    }

    @Test
    void getSubscriptionCountEmpty() {
        assertThat(manager.getSubscriptionCount()).isZero();
    }

    @Test
    void getSubscriptionCountIncrements() {
        manager.subscribe("agent_a", "t1");
        manager.subscribe("agent_b", "t1");
        manager.subscribe("agent_a", "t2");

        assertThat(manager.getSubscriptionCount()).isEqualTo(3);
    }

    @Test
    void getSubscriptionCountDecrementsOnUnsubscribe() {
        manager.subscribe("agent_a", "t1");
        manager.subscribe("agent_a", "t2");

        manager.unsubscribe("agent_a", "t1");

        assertThat(manager.getSubscriptionCount()).isEqualTo(1);
    }

    @Test
    void listSubscriptionsAll() {
        manager.subscribe("agent_a", "t1");
        manager.subscribe("agent_b", "t2");

        Map<String, Object> result = manager.listSubscriptions();

        assertThat(result).containsKey("subscriptions");
        @SuppressWarnings("unchecked")
        Map<String, ?> subscriptions = (Map<String, ?>) result.get("subscriptions");
        assertThat(subscriptions).containsKeys("t1", "t2");
    }

    @Test
    void listSubscriptionsFilteredByAgent() {
        manager.subscribe("agent_a", "t1");
        manager.subscribe("agent_a", "t2");
        manager.subscribe("agent_b", "t3");

        Map<String, Object> result = manager.listSubscriptions("agent_a");

        assertThat(result.get("agent_id")).isEqualTo("agent_a");
        @SuppressWarnings("unchecked")
        List<String> topics = (List<String>) result.get("topics");
        assertThat(topics).contains("t1", "t2").doesNotContain("t3");
    }

    @Test
    void listSubscriptionsForUnknownAgent() {
        Map<String, Object> result = manager.listSubscriptions("unknown");

        assertThat((List<?>) result.get("topics")).isEmpty();
    }

    @Test
    void matchPatternHandlesExactAndWildcards() {
        assertThat(SubscriptionManager.matchPattern("topic", "topic")).isTrue();
        assertThat(SubscriptionManager.matchPattern("topic", "to?ic")).isTrue();
        assertThat(SubscriptionManager.matchPattern("topic", "to*")).isTrue();
        assertThat(SubscriptionManager.matchPattern("topic", "other*")).isFalse();
        assertThat(Set.copyOf(manager.getSubscribers("none"))).isEmpty();
    }
}
