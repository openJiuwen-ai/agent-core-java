/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.controller.legacy.event.Event;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests legacy message-queue controller behavior.
 *
 * <p>Mirrors Python's {@code BaseController} in
 * {@code openjiuwen/core/controller/legacy/controller.py}.</p>
 */
class BaseControllerTest {

    @Test
    void createMessageBuildsUserEventWithConversationAndExtensions() {
        TestController controller = new TestController();
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", "hello");
        inputs.put("conversation_id", "conv-1");
        inputs.put("user_id", "user-1");
        inputs.put("extra", 42);

        Event event = controller.createMessage(inputs);

        assertThat(event.getContent().getQuery()).isEqualTo("hello");
        assertThat(event.getSource().getConversationId()).isEqualTo("conv-1");
        assertThat(event.getSource().getUserId()).isEqualTo("user-1");
        assertThat(event.getContent().getExtensions()).containsEntry("extra", 42);
        assertThat(event.getContent().getExtensions()).doesNotContainKeys(
                "query", "conversation_id", "user_id"
        );
    }

    @Test
    void invokeUsesConversationSpecificSubscriptionAndDefaultResult() {
        TestController controller = new TestController();
        Map<String, Object> inputs = Map.of(
                "query", "hello",
                "conversation_id", "conv-1"
        );

        Map<String, Object> result = controller.invoke(inputs, "session");

        assertThat(result).containsEntry("output", "processed");
        assertThat(controller.lastEvent.getContent().getQuery()).isEqualTo("hello");
        assertThat(controller.lastSession).isEqualTo("session");
        assertThat(controller.subscriptionCount()).isEqualTo(1);
    }

    @Test
    void setupFromAgentInjectsConfigAndContextEngine() {
        TestController controller = new TestController();
        Object config = new Object();
        ContextEngine contextEngine = new ContextEngine();

        controller.setupFromAgent(new AgentWithFields(config, contextEngine));

        assertThat(controller.getConfig()).isSameAs(config);
        assertThat(controller.getContextEngine()).isSameAs(contextEngine);
    }

    @Test
    void sendToAgentRequiresGroupController() {
        TestController controller = new TestController();

        assertThatThrownBy(() -> controller.sendToAgent("agent-1", new Event(), "session"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Agent is not part of a group");
    }

    private static final class TestController extends BaseController {
        private Event lastEvent;
        private Object lastSession;

        @Override
        protected Map<String, Object> handleEvent(Event event, Object session) {
            this.lastEvent = event;
            this.lastSession = session;
            return null;
        }
    }

    private static final class AgentWithFields {
        private final Object agentConfig;
        private final ContextEngine contextEngine;

        private AgentWithFields(Object agentConfig, ContextEngine contextEngine) {
            this.agentConfig = agentConfig;
            this.contextEngine = contextEngine;
        }
    }
}
