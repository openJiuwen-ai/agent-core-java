/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow_agent;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.constants.TaskType;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.modules.EventQueue;
import com.openjiuwen.core.controller.modules.TaskExecutor;
import com.openjiuwen.core.controller.modules.TaskExecutorDependencies;
import com.openjiuwen.core.controller.modules.TaskFilter;
import com.openjiuwen.core.controller.modules.TaskManager;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for workflow task execution.
 *
 * <p>Mirrors Python's {@code WorkflowTaskExecutor} in
 * {@code openjiuwen/core/application/workflow_agent/workflow_task_executor.py}.</p>
 */
class WorkflowTaskExecutorTest {

    @Test
    void pauseAndCancelCapabilitiesMatchPython() {
        RecordingExecutor executor = newExecutor(new TaskManager(new ControllerConfig()), List.of());
        RecordingSession session = new RecordingSession("s-cap");

        TaskExecutor.PauseCheckResult pause = executor.canPause("task-1", session);
        TaskExecutor.CancelCheckResult cancel = executor.canCancel("task-1", session);

        assertFalse(pause.canPause());
        assertEquals("Workflow tasks do not support pause", pause.reason());
        assertFalse(executor.pause("task-1", session));
        assertTrue(cancel.canCancel());
        assertEquals("", cancel.reason());
    }

    @Test
    void cancelRemovesTaskFromTaskManager() {
        TaskManager taskManager = new TaskManager(new ControllerConfig());
        Task task = workflowTask("task-cancel", "s-cancel");
        taskManager.addTask(task);
        RecordingExecutor executor = newExecutor(taskManager, List.of());

        assertTrue(executor.cancel("task-cancel", new RecordingSession("s-cancel")));

        assertTrue(taskManager.getTask(TaskFilter.bySessionId("s-cancel")).isEmpty());
    }

    @Test
    void completionStreamWritesWorkflowFinalAndYieldsCompletionChunk() {
        TaskManager taskManager = new TaskManager(new ControllerConfig());
        Task task = workflowTask("task-complete", "s-complete");
        taskManager.addTask(task);
        Map<String, Object> finalPayload = Map.of("answer", "done");
        RecordingExecutor executor = newExecutor(taskManager, List.of(
                new OutputSchema("workflow_final", 0, finalPayload)
        ));
        RecordingSession session = new RecordingSession("s-complete");
        session.updateState(Map.of("workflow_controller", interruptedState("wf_main")));

        List<ControllerOutputChunk> chunks = iteratorToList(executor.executeAbility("task-complete", session));

        assertEquals(1, session.stream.size());
        OutputSchema workflowFinal = assertInstanceOf(OutputSchema.class, session.stream.get(0));
        assertEquals("workflow_final", workflowFinal.getType());
        assertEquals(finalPayload, workflowFinal.getPayload());
        assertEquals(1, chunks.size());
        assertEquals(EventType.TASK_COMPLETION.getValue(), chunks.get(0).getControllerPayload().getType());
        DataFrame.JsonDataFrame frame = assertInstanceOf(
                DataFrame.JsonDataFrame.class,
                chunks.get(0).getControllerPayload().getData().get(0)
        );
        WorkflowOutput output = assertInstanceOf(WorkflowOutput.class, frame.data().get("result"));
        assertEquals(WorkflowExecutionState.COMPLETED, output.getState());
        assertEquals(finalPayload, output.getResult());
        assertTrue(map(session.getState("workflow_controller")).get("interrupted_tasks") instanceof Map<?, ?>);
        assertTrue(map(map(session.getState("workflow_controller")).get("interrupted_tasks")).isEmpty());
    }

    @Test
    void interactionStreamSavesInterruptStateAndYieldsInteractionChunk() {
        TaskManager taskManager = new TaskManager(new ControllerConfig());
        Task task = workflowTask("task-interrupt", "s-interrupt");
        taskManager.addTask(task);
        OutputSchema interaction = new OutputSchema(
                Constant.INTERACTION,
                0,
                new InteractionOutput("questioner", Map.of("prompt", "Need input"))
        );
        RecordingExecutor executor = newExecutor(taskManager, List.of(interaction));
        RecordingSession session = new RecordingSession("s-interrupt");

        List<ControllerOutputChunk> chunks = iteratorToList(executor.executeAbility("task-interrupt", session));

        assertEquals(1, session.stream.size());
        OutputSchema writtenInteraction = assertInstanceOf(OutputSchema.class, session.stream.get(0));
        assertEquals(Constant.INTERACTION, writtenInteraction.getType());
        assertEquals(1, chunks.size());
        assertEquals(EventType.TASK_INTERACTION.getValue(), chunks.get(0).getControllerPayload().getType());

        Map<String, Object> state = map(session.getState("workflow_controller"));
        Map<String, Object> interrupted = map(state.get("interrupted_tasks"));
        Map<String, Object> info = map(interrupted.get("wf_main"));
        assertEquals("questioner", info.get("component_id"));
        assertEquals(Map.of("prompt", "Need input"), info.get("last_interaction_value"));

        Task updatedTask = taskManager.getTask(TaskFilter.byTaskId("task-interrupt")).get(0);
        assertEquals("questioner", updatedTask.getExtensions().get("component_id"));
    }

    @Test
    void missingTaskProducesNoChunks() {
        RecordingExecutor executor = newExecutor(new TaskManager(new ControllerConfig()), List.of());

        List<ControllerOutputChunk> chunks = iteratorToList(
                executor.executeAbility("missing", new RecordingSession("s-missing"))
        );

        assertTrue(chunks.isEmpty());
    }

    private static RecordingExecutor newExecutor(TaskManager taskManager, List<Object> stream) {
        ControllerConfig config = new ControllerConfig();
        return new RecordingExecutor(new TaskExecutorDependencies(
                config,
                null,
                null,
                taskManager,
                new EventQueue(config)
        ), stream);
    }

    private static Task workflowTask(String taskId, String sessionId) {
        Task task = new Task(sessionId, taskId, TaskType.WORKFLOW.getValue());
        task.setStatus(TaskStatus.SUBMITTED);
        task.setDescription("workflow");
        task.setExtensions(new LinkedHashMap<>(Map.of(
                "workflow_id", "wf.main",
                "workflow_version", "1.0.0",
                "resume_mode", "new",
                "filtered_inputs", Map.of("query", "run")
        )));
        return task;
    }

    private static Map<String, Object> interruptedState(String stateKey) {
        return new LinkedHashMap<>(Map.of("interrupted_tasks", new LinkedHashMap<>(Map.of(
                stateKey,
                Map.of("task", Map.of(), "component_id", "old", "last_interaction_value", "old")
        ))));
    }

    private static List<ControllerOutputChunk> iteratorToList(Iterator<ControllerOutputChunk> iterator) {
        List<ControllerOutputChunk> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertInstanceOf(Map.class, value);
        return (Map<String, Object>) value;
    }

    private static final class RecordingExecutor extends WorkflowTaskExecutor {
        private final List<Object> stream;

        private RecordingExecutor(TaskExecutorDependencies dependencies, List<Object> stream) {
            super(dependencies);
            this.stream = stream;
        }

        @Override
        protected Object findWorkflow(String workflowId, AgentSessionApi session, String agentId) {
            return new Object();
        }

        @Override
        protected Iterator<?> runWorkflowStreaming(Object workflow, Object inputs, Object workflowSession,
                                                   com.openjiuwen.core.context_engine.ModelContext context) {
            return stream.iterator();
        }

        @Override
        protected Object createWorkflowSession(AgentSessionApi session) {
            return session;
        }
    }

    private static final class RecordingSession implements AgentSessionApi {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> stream = new ArrayList<>();

        private RecordingSession(String sessionId) {
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
            if (data != null) {
                state.putAll(data);
            }
        }

        @Override
        public void writeStream(Object data) {
            stream.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return stream.iterator();
        }
    }
}
