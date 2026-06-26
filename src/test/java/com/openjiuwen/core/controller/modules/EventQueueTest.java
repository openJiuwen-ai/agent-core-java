/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.session.AgentSessionApi;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused parity tests for event queue dispatch.
 *
 * <p>Mirrors Python's {@code EventQueue} in
 * {@code openjiuwen/core/controller/modules/event_queue.py}.</p>
 */
class EventQueueTest {

    @Test
    void subscribeRegistersAllPythonEventTypesAndReturnsTopics() {
        EventQueue queue = new EventQueue(new ControllerConfig());
        queue.setEventHandler(new RecordingEventHandler());

        EventQueue.SubscriptionResult result = queue.subscribe("agent-1", "session-1");

        assertThat(result.subscriptions()).containsOnlyKeys(
                EventType.INPUT,
                EventType.TASK_INTERACTION,
                EventType.TASK_COMPLETION,
                EventType.TASK_FAILED,
                EventType.FOLLOW_UP
        );
        assertThat(result.topics()).containsEntry(
                EventType.FOLLOW_UP,
                "agent-1_session-1_follow_up"
        );
    }

    @Test
    void publishEventDispatchesToMatchingHandlerAndWaitsForResult() {
        RecordingEventHandler handler = new RecordingEventHandler();
        EventQueue queue = subscribedQueue(handler);
        FakeSession session = new FakeSession("session-1");

        queue.publishEvent("agent-1", session, new Event(EventType.INPUT));
        queue.publishEvent("agent-1", session, new Event(EventType.TASK_INTERACTION));
        queue.publishEvent("agent-1", session, new Event(EventType.TASK_COMPLETION));
        queue.publishEvent("agent-1", session, new Event(EventType.TASK_FAILED));
        queue.publishEvent("agent-1", session, new Event(EventType.FOLLOW_UP));

        assertThat(handler.calls).containsExactly(
                "input:session-1",
                "interaction:session-1",
                "completion:session-1",
                "failed:session-1",
                "follow_up:session-1"
        );
    }

    @Test
    void unsubscribeReturnsTopicsAndPreventsLaterDispatch() {
        RecordingEventHandler handler = new RecordingEventHandler();
        EventQueue queue = subscribedQueue(handler);
        FakeSession session = new FakeSession("session-1");

        Map<EventType, String> topics = queue.unsubscribe("agent-1", "session-1");
        queue.publishEvent("agent-1", session, new Event(EventType.INPUT));

        assertThat(topics).containsEntry(EventType.INPUT, "agent-1_session-1_input");
        assertThat(topics).containsEntry(EventType.FOLLOW_UP, "agent-1_session-1_follow_up");
        assertThat(handler.calls).isEmpty();
    }

    @Test
    void publishEventWrapsNonFrameworkHandlerFailure() {
        EventQueue queue = subscribedQueue(new FailingEventHandler(new IllegalStateException("boom")));

        assertThatThrownBy(() -> queue.publishEvent("agent-1", new FakeSession("session-1"),
                new Event(EventType.INPUT)))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("controller event handler error")
                .hasRootCauseMessage("boom");
    }

    @Test
    void publishEventReraisesFrameworkErrorsDirectly() {
        BaseError expected = ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_EVENT_QUEUE_ERROR,
                "error_msg", "framework failure");
        EventQueue queue = subscribedQueue(new FailingEventHandler(expected));

        assertThatThrownBy(() -> queue.publishEvent("agent-1", new FakeSession("session-1"),
                new Event(EventType.INPUT)))
                .isSameAs(expected);
    }

    @Test
    void publishEventAsyncReturnsBeforeHandlerResultCanBeObservedByCaller() throws Exception {
        RecordingEventHandler handler = new RecordingEventHandler();
        EventQueue queue = subscribedQueue(handler);

        queue.publishEventAsync("agent-1", new FakeSession("session-1"), new Event(EventType.FOLLOW_UP))
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        assertThat(handler.calls).containsExactly("follow_up:session-1");
    }

    @Test
    void buildTopicUsesPythonTopicFormat() {
        assertThat(EventQueue.buildTopic("agent", "session", EventType.TASK_FAILED))
                .isEqualTo("agent_session_task_failed");
    }

    private static EventQueue subscribedQueue(EventHandler handler) {
        EventQueue queue = new EventQueue(new ControllerConfig());
        queue.setEventHandler(handler);
        queue.subscribe("agent-1", "session-1");
        return queue;
    }

    private static class RecordingEventHandler extends EventHandler {
        private final List<String> calls = new ArrayList<>();

        @Override
        public Map<String, Object> handleInput(EventHandlerInput inputs) {
            calls.add("input:" + inputs.getSession().getSessionId());
            return Map.of("status", "handled");
        }

        @Override
        public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
            calls.add("interaction:" + inputs.getSession().getSessionId());
            return Map.of("status", "handled");
        }

        @Override
        public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
            calls.add("completion:" + inputs.getSession().getSessionId());
            return Map.of("status", "handled");
        }

        @Override
        public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
            calls.add("failed:" + inputs.getSession().getSessionId());
            return Map.of("status", "handled");
        }

        @Override
        public Map<String, Object> handleFollowUp(EventHandlerInput inputs) {
            calls.add("follow_up:" + inputs.getSession().getSessionId());
            return Map.of("status", "handled");
        }
    }

    private static final class FailingEventHandler extends RecordingEventHandler {
        private final RuntimeException failure;

        private FailingEventHandler(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public Map<String, Object> handleInput(EventHandlerInput inputs) {
            throw failure;
        }
    }

    private static final class FakeSession implements AgentSessionApi {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();

        private FakeSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {
        }

        @Override
        public Iterator<Object> streamIterator() {
            return java.util.Collections.emptyIterator();
        }
    }
}
