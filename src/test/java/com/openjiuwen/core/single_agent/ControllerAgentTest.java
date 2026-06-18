/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for {@link ControllerAgent}.
 *
 * <p>Mirrors Python's {@code ControllerAgent} in
 * {@code openjiuwen/core/single_agent/base.py}.</p>
 */
class ControllerAgentTest {

    @Test
    void constructorCreatesDefaultConfigAndInitializesController() {
        AgentCard card = card();
        RecordingController controller = new RecordingController();

        ControllerAgent agent = new ControllerAgent(card, controller);

        assertSame(card, controller.card);
        assertSame(agent.getAbilityManager(), controller.abilityManager);
        assertSame(agent.getContextEngine(), controller.contextEngine);
        assertInstanceOf(ControllerConfig.class, agent.getConfig());
        assertNotNull(controller.config);
    }

    @Test
    void configureMapMergesIntoControllerConfigAndPropagates() {
        RecordingController controller = new RecordingController();
        ControllerAgent agent = new ControllerAgent(card(), controller);

        ControllerConfig before = (ControllerConfig) agent.getConfig();
        agent.configure(Map.of(
                "schedule_interval", 2.0,
                "enable_task_persistence", true,
                "max_concurrent_tasks", 3
        ));
        ControllerConfig after = (ControllerConfig) agent.getConfig();

        assertEquals(1, before.getDefaultTaskPriority());
        assertEquals(2.0, after.getScheduleInterval());
        assertEquals(3, after.getMaxConcurrentTasks());
        assertTrue(after.isEnableTaskPersistence());
        assertSame(after, controller.config);
    }

    @Test
    void invokeConvertsUserInputToInputEventBeforeDelegating() {
        RecordingController controller = new RecordingController();
        ControllerAgent agent = new ControllerAgent(card(), controller);
        FakeSession session = new FakeSession("session-1");

        Object result = agent.invoke("hello", session).toCompletableFuture().join();

        assertEquals("invoked", result);
        assertSame(session, controller.session);
        assertInstanceOf(InputEvent.class, controller.inputEvent);
        DataFrame frame = controller.inputEvent.getInputData().getFirst();
        assertInstanceOf(DataFrame.TextDataFrame.class, frame);
        assertEquals("hello", ((DataFrame.TextDataFrame) frame).text());
    }

    @Test
    void streamConvertsUserInputAndReturnsControllerIterator() {
        RecordingController controller = new RecordingController();
        ControllerAgent agent = new ControllerAgent(card(), controller);
        FakeSession session = new FakeSession("session-1");

        Iterator<Object> iterator = agent.stream(
                Map.of("query", "hello"), session, List.of(StreamMode.OUTPUT));

        assertEquals("chunk-1", iterator.next());
        assertSame(session, controller.session);
        assertEquals(List.of(StreamMode.OUTPUT), controller.streamModes);
        DataFrame frame = controller.inputEvent.getInputData().getFirst();
        assertInstanceOf(DataFrame.JsonDataFrame.class, frame);
        assertEquals("hello", ((DataFrame.JsonDataFrame) frame).data().get("query"));
    }

    @Test
    void releaseSessionUnsubscribesControllerEventQueue() {
        RecordingController controller = new RecordingController();
        ControllerAgent agent = new ControllerAgent(card(), controller);

        agent.releaseSession("session-99").toCompletableFuture().join();

        assertEquals("agent-1", controller.eventQueue.agentId);
        assertEquals("session-99", controller.eventQueue.sessionId);
    }

    private static AgentCard card() {
        return new AgentCard("agent-1", "agent", "test agent");
    }

    public static final class RecordingController {
        private AgentCard card;
        private ControllerConfig config;
        private AbilityManager abilityManager;
        private ContextEngine contextEngine;
        private InputEvent inputEvent;
        private AgentSessionApi session;
        private List<StreamMode> streamModes;
        private final RecordingEventQueue eventQueue = new RecordingEventQueue();

        public void init(AgentCard card, ControllerConfig config, AbilityManager abilityManager,
                         ContextEngine contextEngine) {
            this.card = card;
            this.config = config;
            this.abilityManager = abilityManager;
            this.contextEngine = contextEngine;
        }

        public void setConfig(ControllerConfig config) {
            this.config = config;
        }

        public Object invoke(InputEvent inputs, AgentSessionApi session) {
            this.inputEvent = inputs;
            this.session = session;
            return "invoked";
        }

        public Iterator<Object> stream(InputEvent inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            this.inputEvent = inputs;
            this.session = session;
            this.streamModes = streamModes;
            return List.<Object>of("chunk-1").iterator();
        }

        public RecordingEventQueue getEventQueue() {
            return eventQueue;
        }
    }

    public static final class RecordingEventQueue {
        private String agentId;
        private String sessionId;

        public void unsubscribe(String agentId, String sessionId) {
            this.agentId = agentId;
            this.sessionId = sessionId;
        }
    }

    private static final class FakeSession implements AgentSessionApi {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> stream = new ArrayList<>();

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
            stream.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return new ArrayList<>(stream).iterator();
        }
    }
}
