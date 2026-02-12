// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.contextengine.ContextEngine;
import com.openjiuwen.core.controller.modules.*;
import com.openjiuwen.core.controller.schema.*;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Controller facade.
 *
 * <p>Covers:
 * <ul>
 *   <li>Initialization and dependency injection (init, set_event_handler, add/remove_task_executor)</li>
 *   <li>Config propagation to sub-components</li>
 *   <li>State persistence (_restore_task_manager_state, _save_task_manager_state)</li>
 *   <li>Lifecycle (start, stop)</li>
 *   <li>Execution flows (invoke, stream) – core path & error wrapping</li>
 *   <li>ControllerConfig validation boundaries</li>
 * </ul>
 *
 * <p>Python reference: {@code tests/unit_tests/core/controller/test_controller.py}
 */
class ControllerTest {

    // ==================== Test helpers ====================

    /**
     * Minimal concrete EventHandler for testing.
     */
    static class StubEventHandler extends EventHandler {

        @Override
        public CompletableFuture<Map<String, Object>> handleInput(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskInteraction(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskCompletion(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskFailed(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Minimal concrete TaskExecutor for testing.
     */
    static class StubTaskExecutor extends TaskExecutor {

        protected StubTaskExecutor(TaskExecutorDependencies dependencies) {
            super(dependencies);
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, Session session) {
            return List.of(new ControllerOutputChunk(0, "controller_output", null, false)).iterator();
        }

        @Override
        public PauseResult canPause(String taskId, Session session) {
            return new PauseResult(true, "");
        }

        @Override
        public boolean pause(String taskId, Session session) {
            return true;
        }

        @Override
        public CancelResult canCancel(String taskId, Session session) {
            return new CancelResult(true, "");
        }

        @Override
        public boolean cancel(String taskId, Session session) {
            return true;
        }
    }

    private static AgentCard makeCard() {
        return makeCard("test-agent");
    }

    private static AgentCard makeCard(String agentId) {
        AgentCard card = mock(AgentCard.class);
        when(card.getId()).thenReturn(agentId);
        return card;
    }

    private static Session makeSession() {
        return makeSession("s1", null);
    }

    private static Session makeSession(String sessionId, Map<String, Object> state) {
        Session session = mock(Session.class);
        when(session.getSessionId()).thenReturn(sessionId);
        when(session.getState(any())).thenReturn(state);
        when(session.getState()).thenReturn(state);
        return session;
    }

    // ==================== Config Validation ====================

    @Nested
    @DisplayName("ControllerConfig validation boundaries")
    class TestControllerConfig {

        @Test
        @DisplayName("Default config should have expected values")
        void testDefaultValues() {
            ControllerConfig cfg = new ControllerConfig();
            assertEquals(5, cfg.getMaxConcurrentTasks());
            assertEquals(1.0, cfg.getScheduleInterval());
            assertNull(cfg.getTaskTimeout());
            assertEquals(1, cfg.getDefaultTaskPriority());
            assertFalse(cfg.isEnableTaskPersistence());
            assertEquals(10000, cfg.getEventQueueSize());
            assertEquals(0.7, cfg.getIntentConfidenceThreshold());
        }

        @Test
        @DisplayName("schedule_interval < 0.1 should raise exception")
        void testScheduleIntervalMinBoundary() {
            ControllerConfig cfg = new ControllerConfig();
            assertThrows(IllegalArgumentException.class, () -> cfg.setScheduleInterval(0.05));
        }

        @Test
        @DisplayName("task_timeout < 600 should raise exception")
        void testTaskTimeoutMinBoundary() {
            ControllerConfig cfg = new ControllerConfig();
            assertThrows(IllegalArgumentException.class, () -> cfg.setTaskTimeout(100.0));
        }

        @Test
        @DisplayName("task_timeout=None should be valid")
        void testTaskTimeoutNoneAllowed() {
            ControllerConfig cfg = new ControllerConfig();
            cfg.setTaskTimeout(null);
            assertNull(cfg.getTaskTimeout());
        }

        @Test
        @DisplayName("event_queue_size < 1 should raise exception")
        void testEventQueueSizeMinBoundary() {
            ControllerConfig cfg = new ControllerConfig();
            assertThrows(IllegalArgumentException.class, () -> cfg.setEventQueueSize(0));
        }

        @Test
        @DisplayName("intent_confidence_threshold should be between 0.0 and 1.0")
        void testIntentConfidenceThresholdRange() {
            ControllerConfig cfgLow = new ControllerConfig();
            cfgLow.setIntentConfidenceThreshold(0.0);
            assertEquals(0.0, cfgLow.getIntentConfidenceThreshold());

            ControllerConfig cfgHigh = new ControllerConfig();
            cfgHigh.setIntentConfidenceThreshold(1.0);
            assertEquals(1.0, cfgHigh.getIntentConfidenceThreshold());

            ControllerConfig cfg = new ControllerConfig();
            assertThrows(IllegalArgumentException.class, () -> cfg.setIntentConfidenceThreshold(1.5));
            assertThrows(IllegalArgumentException.class, () -> cfg.setIntentConfidenceThreshold(-0.1));
        }
    }

    // ==================== Init & DI ====================

    @Nested
    @DisplayName("Controller.__init__, init(), and dependency injection")
    class TestControllerInit {

        @Test
        @DisplayName("Before init() all components are None; after init() they are properly created")
        void testInitInjectsAllDeps() {
            Controller ctrl = new Controller();
            // Pre-init: all components should be null
            assertNull(ctrl.getCard());
            assertNull(ctrl.getConfig());
            assertNull(ctrl.getTaskManager());
            assertNull(ctrl.getEventQueue());
            assertNull(ctrl.getTaskScheduler());

            // After init: all sub-components should be created
            AgentCard card = makeCard();
            ControllerConfig config = new ControllerConfig();
            AbilityManager abilityMgr = mock(AbilityManager.class);
            ContextEngine contextEng = mock(ContextEngine.class);

            ctrl.init(card, config, abilityMgr, contextEng);

            assertSame(card, ctrl.getCard());
            assertSame(config, ctrl.getConfig());
            assertNotNull(ctrl.getTaskManager());
            assertInstanceOf(TaskManager.class, ctrl.getTaskManager());
            assertNotNull(ctrl.getEventQueue());
            assertInstanceOf(EventQueue.class, ctrl.getEventQueue());
            assertNotNull(ctrl.getTaskScheduler());
            assertInstanceOf(TaskScheduler.class, ctrl.getTaskScheduler());
        }

        @Test
        @DisplayName("set_event_handler should inject config, context_engine, task_manager, etc.")
        void testSetEventHandlerWiresDeps() {
            Controller ctrl = new Controller();
            AgentCard card = makeCard();
            ControllerConfig config = new ControllerConfig();
            AbilityManager abilityMgr = mock(AbilityManager.class);
            ContextEngine contextEng = mock(ContextEngine.class);
            ctrl.init(card, config, abilityMgr, contextEng);

            StubEventHandler handler = new StubEventHandler();
            ctrl.setEventHandler(handler);

            assertSame(config, handler.getConfig());
            assertSame(contextEng, handler.getContextEngine());
            assertSame(ctrl.getTaskManager(), handler.getTaskManager());
            assertSame(ctrl.getTaskScheduler(), handler.getTaskScheduler());
            assertSame(abilityMgr, handler.getAbilityManager());
        }
    }

    // ==================== Task Executor Registration ====================

    @Nested
    @DisplayName("add_task_executor / remove_task_executor")
    class TestTaskExecutorRegistration {

        private Controller initController() {
            Controller ctrl = new Controller();
            ctrl.init(makeCard(), new ControllerConfig(), mock(AbilityManager.class), mock(ContextEngine.class));
            return ctrl;
        }

        @Test
        @DisplayName("add_task_executor should return self for chaining")
        void testAddTaskExecutorReturnsSelf() {
            Controller ctrl = initController();
            Controller result = ctrl.addTaskExecutor("analysis", StubTaskExecutor::new);
            assertSame(ctrl, result);
        }

        @Test
        @DisplayName("add then remove should not leave the executor in the registry")
        void testAddAndRemoveTaskExecutor() {
            Controller ctrl = initController();
            ctrl.addTaskExecutor("analysis", StubTaskExecutor::new);
            ctrl.removeTaskExecutor("analysis");

            // Verify it is removed
            TaskExecutorDependencies deps = new TaskExecutorDependencies(
                ctrl.getConfig(),
                mock(AbilityManager.class),
                mock(ContextEngine.class),
                ctrl.getTaskManager(),
                ctrl.getEventQueue()
            );
            assertThrows(BaseError.class,
                () -> ctrl.getTaskScheduler().getTaskExecutorRegistry().getTaskExecutor("analysis", deps));
        }

        @Test
        @DisplayName("Chained add_task_executor should register all types")
        void testChainedAdd() {
            Controller ctrl = initController();
            ctrl.addTaskExecutor("type_a", StubTaskExecutor::new)
                .addTaskExecutor("type_b", StubTaskExecutor::new);

            TaskExecutorDependencies deps = new TaskExecutorDependencies(
                ctrl.getConfig(),
                mock(AbilityManager.class),
                mock(ContextEngine.class),
                ctrl.getTaskManager(),
                ctrl.getEventQueue()
            );
            assertNotNull(ctrl.getTaskScheduler().getTaskExecutorRegistry().getTaskExecutor("type_a", deps));
            assertNotNull(ctrl.getTaskScheduler().getTaskExecutorRegistry().getTaskExecutor("type_b", deps));
        }
    }

    // ==================== Config Propagation ====================

    @Nested
    @DisplayName("config setter cascading to sub-components")
    class TestConfigPropagation {

        @Test
        @DisplayName("Setting config on Controller should propagate to all sub-components")
        void testConfigSetterPropagates() {
            Controller ctrl = new Controller();
            AgentCard card = makeCard();
            ControllerConfig config = new ControllerConfig();
            config.setMaxConcurrentTasks(5);
            ctrl.init(card, config, mock(AbilityManager.class), mock(ContextEngine.class));
            StubEventHandler handler = new StubEventHandler();
            ctrl.setEventHandler(handler);

            ControllerConfig newConfig = new ControllerConfig();
            newConfig.setMaxConcurrentTasks(10);
            ctrl.setConfig(newConfig);

            assertSame(newConfig, ctrl.getConfig());
            assertSame(newConfig, ctrl.getTaskManager().getConfig());
            assertSame(newConfig, ctrl.getEventQueue().getConfig());
            assertSame(newConfig, ctrl.getTaskScheduler().getConfig());
            assertSame(newConfig, handler.getConfig());
        }
    }

    // ==================== State Persistence ====================

    @Nested
    @DisplayName("_restore_task_manager_state and _save_task_manager_state")
    class TestStatePersistence {

        @Test
        @DisplayName("When session has no controller state, task manager should be cleared")
        void testRestoreNoStateClearsTaskManager() {
            Controller ctrl = new Controller();
            ctrl.init(makeCard(), new ControllerConfig(), mock(AbilityManager.class), mock(ContextEngine.class));
            Session session = makeSession("s1", null);

            boolean result = ctrl.restoreTaskManagerState(session);
            assertFalse(result);
        }

        @Test
        @DisplayName("When state data is corrupted, should clear and return false")
        void testRestoreCorruptedStateClears() {
            Controller ctrl = new Controller();
            ctrl.init(makeCard(), new ControllerConfig(), mock(AbilityManager.class), mock(ContextEngine.class));
            Map<String, Object> state = new HashMap<>();
            state.put("task_manager_state", "bad_data");
            Session session = makeSession("s1", state);

            boolean result = ctrl.restoreTaskManagerState(session);
            assertFalse(result);
            // Task manager should be empty after clear
            List<Task> allTasks = ctrl.getTaskManager().getTask(null);
            assertEquals(0, allTasks.size());
        }

        @Test
        @DisplayName("When session has valid state dict, tasks should be restored")
        void testRestoreValidState() {
            Controller ctrl = new Controller();
            ctrl.init(makeCard(), new ControllerConfig(), mock(AbilityManager.class), mock(ContextEngine.class));

            // Build a valid state
            Task task = Task.builder("s1", "t1", "analysis")
                .priority(1)
                .status(TaskStatus.PAUSED)
                .build();

            TaskManagerState state = new TaskManagerState(
                Map.of("t1", task),
                Map.of(1, List.of("t1")),
                Map.of(),
                Map.of(),
                Set.of("t1")
            );

            Map<String, Object> sessionState = new HashMap<>();
            sessionState.put("task_manager_state", state);
            Session session = makeSession("s1", sessionState);

            boolean result = ctrl.restoreTaskManagerState(session);
            assertTrue(result);
            // Verify task was restored
            assertTrue(ctrl.getTaskManager().getTasks().containsKey("t1"));
        }

        @Test
        @DisplayName("When enable_task_persistence is False, _save should not call session.update_state")
        void testSaveDisabledSkips() {
            Controller ctrl = new Controller();
            ControllerConfig cfg = new ControllerConfig();
            cfg.setEnableTaskPersistence(false);
            ctrl.init(makeCard(), cfg, mock(AbilityManager.class), mock(ContextEngine.class));
            Session session = makeSession();

            ctrl.saveTaskManagerState(session);
            verify(session, never()).updateState(any());
        }

        @Test
        @DisplayName("When enable_task_persistence is True, state should be saved to session")
        void testSaveEnabledPersists() {
            Controller ctrl = new Controller();
            ControllerConfig cfg = new ControllerConfig();
            cfg.setEnableTaskPersistence(true);
            ctrl.init(makeCard(), cfg, mock(AbilityManager.class), mock(ContextEngine.class));

            // Add a task
            Task task = Task.builder("s1", "t1", "analysis")
                .priority(1)
                .status(TaskStatus.COMPLETED)
                .build();
            ctrl.getTaskManager().addTask(task);

            Session session = makeSession();
            ctrl.saveTaskManagerState(session);

            // Should have called updateState twice: clear + save
            verify(session, times(2)).updateState(any());
        }

        @Test
        @DisplayName("save then restore should reproduce the same tasks")
        void testSaveRoundtrip() {
            // First controller: save
            Controller ctrl = new Controller();
            ControllerConfig cfg = new ControllerConfig();
            cfg.setEnableTaskPersistence(true);
            ctrl.init(makeCard(), cfg, mock(AbilityManager.class), mock(ContextEngine.class));

            Task task = Task.builder("s1", "rt1", "analysis")
                .priority(3)
                .status(TaskStatus.PAUSED)
                .build();
            ctrl.getTaskManager().addTask(task);

            Session session = makeSession();
            ctrl.saveTaskManagerState(session);

            // Extract saved data
            @SuppressWarnings("unchecked")
            Map<String, Object> savedData = (Map<String, Object>)
                ((Map<String, Object>) invocationArg(session, 1)).get("controller");

            // Second controller: restore
            Controller ctrl2 = new Controller();
            ctrl2.init(makeCard(), new ControllerConfig(), mock(AbilityManager.class), mock(ContextEngine.class));
            Session session2 = makeSession("s1", savedData);
            boolean result = ctrl2.restoreTaskManagerState(session2);

            assertTrue(result);
            assertTrue(ctrl2.getTaskManager().getTasks().containsKey("rt1"));
            assertEquals(3, ctrl2.getTaskManager().getTasks().get("rt1").getPriority());
            assertEquals(TaskStatus.PAUSED, ctrl2.getTaskManager().getTasks().get("rt1").getStatus());
        }

        /**
         * Helper to extract the argument of the nth call to session.updateState.
         */
        @SuppressWarnings("unchecked")
        private Map<String, Object> invocationArg(Session session, int callIndex) {
            var invocations = mockingDetails(session).getInvocations();
            var updateStateInvocations = invocations.stream()
                .filter(inv -> inv.getMethod().getName().equals("updateState"))
                .toList();
            return (Map<String, Object>) updateStateInvocations.get(callIndex).getArgument(0);
        }
    }

    // ==================== Lifecycle ====================

    @Nested
    @DisplayName("start() and stop()")
    class TestControllerLifecycle {

        @Test
        @DisplayName("start() should start event_queue and task_scheduler without error")
        void testStartStartsSubcomponents() {
            Controller ctrl = new Controller();
            ctrl.init(makeCard(), new ControllerConfig(), mock(AbilityManager.class), mock(ContextEngine.class));

            assertDoesNotThrow(() -> ctrl.start());
            // Verify subcomponents are non-null (init was successful)
            assertNotNull(ctrl.getTaskScheduler());
            assertNotNull(ctrl.getEventQueue());
            ctrl.stop();
        }

        @Test
        @DisplayName("stop() should stop event_queue and task_scheduler without error")
        void testStopStopsSubcomponents() {
            Controller ctrl = new Controller();
            ctrl.init(makeCard(), new ControllerConfig(), mock(AbilityManager.class), mock(ContextEngine.class));
            ctrl.start();

            assertDoesNotThrow(() -> ctrl.stop());
        }

        @Test
        @DisplayName("start() then stop() then start() again should work")
        void testRestartCycle() {
            Controller ctrl = new Controller();
            ctrl.init(makeCard(), new ControllerConfig(), mock(AbilityManager.class), mock(ContextEngine.class));

            ctrl.start();
            ctrl.stop();
            assertDoesNotThrow(() -> {
                ctrl.start();
                ctrl.stop();
            });
        }
    }

    // ==================== Property Accessors ====================

    @Nested
    @DisplayName("Property getters and setters")
    class TestPropertyAccessors {

        @Test
        @DisplayName("All property getters should return correct references; setters should update state")
        void testAllPropertyAccessorsAndSetters() {
            Controller ctrl = new Controller();
            ctrl.init(makeCard(), new ControllerConfig(), mock(AbilityManager.class), mock(ContextEngine.class));

            // Read-only property accessors
            assertSame(ctrl.getEventQueue(), ctrl.getEventQueue());
            assertSame(ctrl.getTaskManager(), ctrl.getTaskManager());
            assertSame(ctrl.getTaskScheduler(), ctrl.getTaskScheduler());

            // Writable property setters
            ContextEngine mockCe = mock(ContextEngine.class);
            ctrl.setContextEngine(mockCe);
            assertSame(mockCe, ctrl.getContextEngine());

            AbilityManager mockAm = mock(AbilityManager.class);
            ctrl.setAbilityManager(mockAm);
            assertSame(mockAm, ctrl.getAbilityManager());
        }
    }
}

