/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.session.AgentSessionApi;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for event handler base types.
 *
 * <p>Mirrors Python's {@code EventHandlerInput} and {@code EventHandler} in
 * {@code openjiuwen/core/controller/modules/event_handler.py}.</p>
 */
class EventHandlerTest {

    @Test
    void inputStoresEventAndSessionReferences() {
        Event event = new Event(EventType.INPUT);
        FakeSession session = new FakeSession("session-1");

        EventHandlerInput input = new EventHandlerInput(event, session);

        assertThat(input.getEvent()).isSameAs(event);
        assertThat(input.getSession()).isSameAs(session);
    }

    @Test
    void dependencyPropertiesRoundTripLikePythonSetters() {
        ControllerConfig config = new ControllerConfig();
        ContextEngine contextEngine = new ContextEngine();
        Object abilityManager = new Object();
        TaskManager taskManager = new TaskManager(config);
        EventQueue eventQueue = new EventQueue(config);
        TaskScheduler taskScheduler = new TaskScheduler(
                config,
                taskManager,
                contextEngine,
                abilityManager,
                eventQueue,
                new BaseCard("agent-1", "agent", "test agent")
        );
        NoOpEventHandler handler = new NoOpEventHandler();

        handler.setConfig(config);
        handler.setContextEngine(contextEngine);
        handler.setAbilityManager(abilityManager);
        handler.setTaskManager(taskManager);
        handler.setTaskScheduler(taskScheduler);

        assertThat(handler.getConfig()).isSameAs(config);
        assertThat(handler.getContextEngine()).isSameAs(contextEngine);
        assertThat(handler.getAbilityManager()).isSameAs(abilityManager);
        assertThat(handler.getTaskManager()).isSameAs(taskManager);
        assertThat(handler.getTaskScheduler()).isSameAs(taskScheduler);
    }

    @Test
    void defaultFollowUpReturnsNonEmptyUnsupportedStatus() {
        NoOpEventHandler handler = new NoOpEventHandler();
        EventHandlerInput input = new EventHandlerInput(new Event(EventType.INPUT), new FakeSession("session-1"));

        assertThat(handler.handleFollowUp(input)).containsEntry("status", "not_supported");
    }

    @Test
    void defaultRoundLifecycleCompletesImmediatelyAndAbortIsNoOp() {
        NoOpEventHandler handler = new NoOpEventHandler();

        assertThat(handler.prepareRound()).isZero();
        assertThat(handler.waitCompletion()).containsEntry("status", "completed");
        assertThat(handler.waitCompletion(0.5D)).containsEntry("status", "completed");

        handler.onAbort();
    }

    /**
     * Test helper subclass.
     *
     * <p>Mirrors Python's {@code EventHandler} extension point in
     * {@code openjiuwen/core/controller/modules/event_handler.py}.</p>
     */
    private static final class NoOpEventHandler extends EventHandler {

        @Override
        public Map<String, Object> handleInput(EventHandlerInput inputs) {
            return Map.of();
        }

        @Override
        public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
            return Map.of();
        }

        @Override
        public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
            return Map.of();
        }

        @Override
        public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
            return Map.of();
        }
    }

    /**
     * Test helper session dependency.
     *
     * <p>Mirrors Python's {@code EventHandlerInput.session} dependency in
     * {@code openjiuwen/core/controller/modules/event_handler.py}.</p>
     */
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
