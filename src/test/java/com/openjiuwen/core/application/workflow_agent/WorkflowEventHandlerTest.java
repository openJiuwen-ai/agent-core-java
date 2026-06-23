/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow_agent;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.modules.EventHandlerInput;
import com.openjiuwen.core.controller.modules.TaskFilter;
import com.openjiuwen.core.controller.modules.TaskManager;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.workflow.WorkflowCard;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the workflow event handler.
 *
 * <p>Mirrors Python's {@code WorkflowEventHandler} in
 * {@code openjiuwen/core/application/workflow_agent/workflow_event_handler.py}.</p>
 */
class WorkflowEventHandlerTest {

    @Test
    void handleInputRequiresConfiguredWorkflows() {
        WorkflowEventHandler handler = newHandler(List.of());
        RecordingSession session = new RecordingSession("s-1");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> handler.handleInput(new EventHandlerInput(jsonEvent("hello"), session))
        );

        assertEquals("No workflows configured", error.getMessage());
    }

    @Test
    void handleInputCreatesSubmittedWorkflowTaskWithFilteredInputs() {
        WorkflowCard workflow = workflow("wf.main", "Main flow", Map.of(
                "properties", Map.of(
                        "query", Map.of("type", "string"),
                        "tenant", Map.of("type", "string")
                ),
                "required", List.of("query")
        ));
        WorkflowEventHandler handler = newHandler(List.of(workflow));
        RecordingSession session = new RecordingSession("s-create");
        InputEvent event = jsonEvent("run report");
        event.setMetadata(Map.of("extensions", Map.of("tenant", "acme", "ignored", "drop")));

        Map<String, Object> result = handler.handleInput(new EventHandlerInput(event, session));

        assertEquals(Map.of("status", "success"), result);
        List<Task> tasks = handler.getTaskManager().getTask(TaskFilter.bySessionId("s-create"));
        assertEquals(1, tasks.size());
        Task task = tasks.getFirst();
        assertTrue(task.getTaskId().startsWith("wf_"));
        assertEquals("workflow", task.getTaskType());
        assertEquals("Main flow", task.getDescription());
        assertEquals(TaskStatus.SUBMITTED, task.getStatus());
        assertEquals("wf.main", task.getExtensions().get("workflow_id"));
        assertEquals("1.0.0", task.getExtensions().get("workflow_version"));
        assertEquals("new", task.getExtensions().get("resume_mode"));
        assertEquals(Map.of("query", "run report", "tenant", "acme"), task.getExtensions().get("filtered_inputs"));
    }

    @Test
    void defaultResponseWritesWorkflowFinalAndCompletionSignal() {
        WorkflowCard first = workflow("wf.one", "First", Map.of());
        WorkflowCard second = workflow("wf.two", "Second", Map.of());
        WorkflowEventHandler handler = newHandler(List.of(first, second));
        ControllerConfig config = new ControllerConfig();
        config.setEnableIntentRecognition(true);
        config.setDefaultResponse(new ControllerConfig.DefaultResponse("text", "Choose a workflow"));
        handler.setConfig(config);
        handler.setIntentDetector(new WorkflowEventHandler.IntentDetector() {
            @Override
            public List<WorkflowEventHandler.TaskResult> processMessage(com.openjiuwen.core.controller.schema.Event event) {
                return List.of();
            }
        });
        RecordingSession session = new RecordingSession("s-default");

        handler.handleInput(new EventHandlerInput(jsonEvent("unclear"), session));

        assertEquals(2, session.stream.size());
        OutputSchema workflowFinal = assertInstanceOf(OutputSchema.class, session.stream.get(0));
        assertEquals("workflow_final", workflowFinal.getType());
        assertEquals("Choose a workflow", map(workflowFinal.getPayload()).get("response"));
        ControllerOutputChunk completion = assertInstanceOf(ControllerOutputChunk.class, session.stream.get(1));
        assertTrue(completion.isLastChunk());
        assertEquals("all_tasks_processed", completion.getControllerPayload().getType());
        assertTrue(handler.getTaskManager().getTask(TaskFilter.bySessionId("s-default")).isEmpty());
    }

    @Test
    void structuredInterruptionIsReturnedWithoutCreatingTask() {
        WorkflowCard workflow = workflow("wf.main", "Main flow", Map.of());
        WorkflowEventHandler handler = newHandler(List.of(workflow));
        RecordingSession session = new RecordingSession("s-return");
        session.updateState(Map.of("workflow_controller", interruptedState(
                "wf_main",
                "questioner",
                Map.of("choices", List.of("A", "B")),
                Map.of("workflow_id", "wf.main", "workflow_version", "1.0.0", "filtered_inputs", Map.of())
        )));

        handler.handleInput(new EventHandlerInput(new InputEvent(List.of(new DataFrame.TextDataFrame("ignored"))), session));

        assertEquals(2, session.stream.size());
        OutputSchema interaction = assertInstanceOf(OutputSchema.class, session.stream.get(0));
        assertEquals(Constant.INTERACTION, interaction.getType());
        InteractionOutput payload = assertInstanceOf(InteractionOutput.class, interaction.getPayload());
        assertEquals("questioner", payload.getId());
        assertEquals(Map.of("choices", List.of("A", "B")), payload.getValue());
        ControllerOutputChunk completion = assertInstanceOf(ControllerOutputChunk.class, session.stream.get(1));
        assertTrue(completion.isLastChunk());
        assertTrue(handler.getTaskManager().getTask(TaskFilter.bySessionId("s-return")).isEmpty());
    }

    @Test
    void textAnswerForInterruptedWorkflowCreatesResumeTask() {
        WorkflowCard workflow = workflow("wf.main", "Main flow", Map.of());
        WorkflowEventHandler handler = newHandler(List.of(workflow));
        RecordingSession session = new RecordingSession("s-resume");
        session.updateState(Map.of("workflow_controller", interruptedState(
                "wf_main",
                "answer",
                Map.of("prompt", "Confirm?"),
                Map.of("workflow_id", "wf.main", "workflow_version", "1.0.0", "filtered_inputs", Map.of("query", "old"))
        )));

        handler.handleInput(new EventHandlerInput(jsonEvent("yes"), session));

        List<Task> tasks = handler.getTaskManager().getTask(TaskFilter.bySessionId("s-resume"));
        assertEquals(1, tasks.size());
        Task resumeTask = tasks.getFirst();
        assertEquals("resume", resumeTask.getExtensions().get("resume_mode"));
        InteractiveInput interactiveInput = assertInstanceOf(
                InteractiveInput.class,
                resumeTask.getExtensions().get("interactive_input")
        );
        assertEquals(Map.of("answer", "yes"), interactiveInput.getUserInputs());
        assertEquals("wf.main", resumeTask.getExtensions().get("workflow_id"));
    }

    private static WorkflowEventHandler newHandler(List<WorkflowCard> workflows) {
        ControllerConfig config = new ControllerConfig();
        WorkflowEventHandler handler = new WorkflowEventHandler();
        AbilityManager abilityManager = new AbilityManager();
        abilityManager.add(workflows);
        TaskManager taskManager = new TaskManager(config);
        handler.setConfig(config);
        handler.setAbilityManager(abilityManager);
        handler.setTaskManager(taskManager);
        return handler;
    }

    private static WorkflowCard workflow(String id, String name, Object inputParams) {
        return new WorkflowCard(id, name, "description for " + name, "1.0.0", inputParams);
    }

    private static InputEvent jsonEvent(String query) {
        return new InputEvent(List.of(new DataFrame.JsonDataFrame(Map.of("query", query))));
    }

    private static Map<String, Object> interruptedState(String stateKey, String componentId,
                                                        Object lastInteractionValue,
                                                        Map<String, Object> extensions) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("extensions", extensions);

        Map<String, Object> interrupted = new LinkedHashMap<>();
        interrupted.put("component_id", componentId);
        interrupted.put("last_interaction_value", lastInteractionValue);
        interrupted.put("task", task);

        Map<String, Object> interruptedTasks = new LinkedHashMap<>();
        interruptedTasks.put(stateKey, interrupted);

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("interrupted_tasks", interruptedTasks);
        return state;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertInstanceOf(Map.class, value);
        return (Map<String, Object>) value;
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
