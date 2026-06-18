/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.controller.modules.EventHandler;
import com.openjiuwen.core.controller.modules.EventHandlerInput;
import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.session.AgentSessionApi;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for {@link Controller}.
 *
 * <p>Mirrors Python's {@code Controller} in
 * {@code openjiuwen/core/controller/base.py}.</p>
 */
class ControllerTest {

    @Test
    void initCreatesControllerInfrastructureAndConfigPropagates() {
        Controller controller = controller();

        assertThat(controller.getTaskManager()).isNotNull();
        assertThat(controller.getEventQueue()).isNotNull();
        assertThat(controller.getTaskScheduler()).isNotNull();
        assertThat(controller.getContextEngine()).isNotNull();

        ControllerConfig replacement = new ControllerConfig();
        replacement.setScheduleInterval(2.0);
        controller.setConfig(replacement);

        assertThat(controller.getConfig()).isSameAs(replacement);
        assertThat(controller.getTaskManager().getConfig()).isSameAs(replacement);
        assertThat(controller.getEventQueue().getConfig()).isSameAs(replacement);
        assertThat(controller.getTaskScheduler().getConfig()).isSameAs(replacement);
    }

    @Test
    void setEventHandlerWiresControllerDependencies() {
        Controller controller = controller();
        NoOpEventHandler handler = new NoOpEventHandler();

        controller.setEventHandler(handler);

        assertThat(handler.getConfig()).isSameAs(controller.getConfig());
        assertThat(handler.getContextEngine()).isSameAs(controller.getContextEngine());
        assertThat(handler.getTaskScheduler()).isSameAs(controller.getTaskScheduler());
        assertThat(handler.getTaskManager()).isSameAs(controller.getTaskManager());
        assertThat(handler.getAbilityManager()).isSameAs(controller.getAbilityManager());
    }

    @Test
    void invokeReturnsCompletionOutputWhenNoTasksWereCreated() {
        Controller controller = controller();
        controller.setEventHandler(new NoOpEventHandler());
        FakeSession session = new FakeSession("session-1");

        ControllerOutput output = controller.invoke(new InputEvent(), session);

        assertThat(output.getType()).isEqualTo(EventType.TASK_COMPLETION.getValue());
        assertThat(output.getData()).asList().isEmpty();
        assertThat(session.state()).doesNotContainKey("controller");
    }

    @Test
    @SuppressWarnings("unchecked")
    void invokeSavesTaskManagerStateWhenPersistenceIsEnabled() {
        ControllerConfig config = new ControllerConfig();
        config.setEnableTaskPersistence(true);
        Controller controller = controller(config);
        controller.setEventHandler(new NoOpEventHandler());
        FakeSession session = new FakeSession("session-1");

        ControllerOutput output = controller.invoke(new InputEvent(), session);

        assertThat(output.getType()).isEqualTo(EventType.TASK_COMPLETION.getValue());
        assertThat(output.getData()).asList().isEmpty();
        assertThat(session.state()).containsKey("controller");
        Map<String, Object> controllerState = (Map<String, Object>) session.state().get("controller");
        assertThat(controllerState).containsKey("task_manager_state");
        Map<String, Object> taskManagerState =
                (Map<String, Object>) controllerState.get("task_manager_state");
        assertThat(taskManagerState).containsKeys("tasks", "root_tasks");
    }

    private static Controller controller() {
        return controller(new ControllerConfig());
    }

    private static Controller controller(ControllerConfig config) {
        Controller controller = new Controller();
        controller.init(new BaseCard("agent-1", "agent", "test agent"),
                config,
                new Object(),
                new ContextEngine());
        return controller;
    }

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

        private Map<String, Object> state() {
            return state;
        }
    }
}
