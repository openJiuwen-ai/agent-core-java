package com.openjiuwen.core.multiagent.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionManagerCompatibilityTest {

    @Test
    void shouldResolveExactAndWildcardSubscriptions() {
        SubscriptionManager manager = new SubscriptionManager();
        manager.subscribe("reviewer", "code_review");
        manager.subscribe("auditor", "code_*");

        assertThat(manager.getSubscribers("code_review"))
                .containsExactlyInAnyOrder("reviewer", "auditor");
        assertThat(manager.getSubscribers("code_fix"))
                .containsExactly("auditor");
    }

    @Test
    void unsubscribeAllShouldClearAgentTopics() {
        SubscriptionManager manager = new SubscriptionManager();
        manager.subscribe("reviewer", "code_review");
        manager.subscribe("reviewer", "task_*");

        manager.unsubscribeAll("reviewer");

        assertThat(manager.getSubscribers("code_review")).isEmpty();
        assertThat(manager.getSubscriptionCount()).isZero();
        assertThat(manager.listSubscriptions("reviewer"))
                .isEqualTo(Map.of("agent_id", "reviewer", "topics", List.of()));
    }
}
