/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow_agent;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.constants.TaskType;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.modules.EventHandlerInput;
import com.openjiuwen.core.controller.modules.EventQueue;
import com.openjiuwen.core.controller.modules.TaskExecutorDependencies;
import com.openjiuwen.core.controller.modules.TaskFilter;
import com.openjiuwen.core.controller.modules.TaskManager;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.resourcemanager.ResourceManagerBase;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.legacy.config.WorkflowAgentConfig;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowKeys;
import com.openjiuwen.core.workflow.WorkflowOutput;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's workflow-agent mock tests in
 * {@code tests/unit_tests/agent/workflow_agent/test_workflow_agent_mock.py}.
 *
 * <p>Mirrors Python's {@code TestWorkflowAgent.test_invoke_single} in
 * {@code tests/unit_tests/agent/workflow_agent/test_workflow_agent.py}.</p>
 */
class WorkflowAgentMockPythonParityTest {

    @Test
    void testWorkflowAgentBasicExecution() {
        WorkflowAgentConfig config = workflowConfig("simple_workflow_agent");
        WorkflowAgent agent = new WorkflowAgent(config);
        RecordingController controller = new RecordingController();
        RecordingSession session = new RecordingSession("test_basic");
        Map<String, Object> inputs = new LinkedHashMap<>(Map.of("query", "hello"));
        agent.setController(controller);

        Object result = agent.invoke(inputs, session).toCompletableFuture().join();

        Map<String, Object> resultMap = map(result);
        assertThat(resultMap).containsEntry("result_type", "answer");
        WorkflowOutput output = (WorkflowOutput) resultMap.get("output");
        assertThat(output.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        assertThat(output.getResult()).isEqualTo(Map.of("query", "hello"));
        assertThat(controller.invokeInputs).isSameAs(inputs);
        assertThat(controller.invokeSession).isSameAs(session);
    }

    @Test
    void testInvokeSingleFromWorkflowAgentPy() {
        WorkflowAgent agent = new WorkflowAgent(workflowConfig("test_workflow_agent"));
        SingleInvokeController controller = new SingleInvokeController();
        RecordingSession session = new RecordingSession("test_invoke_single");
        Map<String, Object> inputs = new LinkedHashMap<>(Map.of("query", "hi"));
        agent.setController(controller);

        Object result = agent.invoke(inputs, session).toCompletableFuture().join();

        Map<String, Object> resultMap = map(result);
        assertThat(resultMap).containsEntry("result_type", "answer");
        WorkflowOutput output = (WorkflowOutput) resultMap.get("output");
        assertThat(output.getResult()).isEqualTo(Map.of("result", "hi"));
        assertThat(output.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        assertThat(controller.invokeInputs).isSameAs(inputs);
        assertThat(controller.invokeSession).isSameAs(session);
    }

    @Test
    void testWorkflowAgentWithInterrupt() {
        TaskManager taskManager = new TaskManager(new ControllerConfig());
        Task task = workflowTask("task-interrupt", "test_interrupt", "location_workflow_1.0");
        taskManager.addTask(task);
        OutputSchema interaction = new OutputSchema(
                Constant.INTERACTION,
                0,
                new InteractionOutput("questioner", Map.of("prompt", "Need location"))
        );
        RecordingExecutor executor = executor(taskManager, List.of(interaction));
        RecordingSession session = new RecordingSession("test_interrupt");

        List<ControllerOutputChunk> chunks = iteratorToList(executor.executeAbility("task-interrupt", session));

        assertThat(session.stream).hasSize(1);
        OutputSchema written = (OutputSchema) session.stream.get(0);
        assertThat(written.getType()).isEqualTo(Constant.INTERACTION);
        InteractionOutput payload = (InteractionOutput) written.getPayload();
        assertThat(payload.getId()).isEqualTo("questioner");
        assertThat(payload.getValue()).isEqualTo(Map.of("prompt", "Need location"));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getControllerPayload().getType())
                .isEqualTo(EventType.TASK_INTERACTION.getValue());
        WorkflowOutput output = workflowOutput(chunks.get(0));
        assertThat(output.getState()).isEqualTo(WorkflowExecutionState.INPUT_REQUIRED);
        assertThat(output.getResult()).isSameAs(written);

        Map<String, Object> state = map(session.getState("workflow_controller"));
        Map<String, Object> interrupted = map(state.get("interrupted_tasks"));
        Map<String, Object> info = map(interrupted.get("location_workflow_1_0"));
        assertThat(info).containsEntry("component_id", "questioner");
        assertThat(info).containsEntry("last_interaction_value", Map.of("prompt", "Need location"));
    }

    @Test
    void testWorkflowAgentInterruptResume() {
        WorkflowCard workflow = workflowCard("location_workflow_resume", "地点查询");
        WorkflowEventHandler handler = eventHandler(List.of(workflow));
        RecordingSession session = new RecordingSession("test_resume");
        session.updateState(Map.of("workflow_controller", interruptedState(
                "location_workflow_resume",
                "questioner",
                Map.of("prompt", "Need location"),
                Map.of(
                        "workflow_id", "location_workflow_resume",
                        "workflow_version", "1.0",
                        "filtered_inputs", Map.of("query", "查询天气")
                )
        )));

        handler.handleInput(new EventHandlerInput(jsonEvent("上海"), session));

        List<Task> tasks = handler.getTaskManager().getTask(TaskFilter.bySessionId("test_resume"));
        assertThat(tasks).hasSize(1);
        Task resumeTask = tasks.get(0);
        assertThat(resumeTask.getExtensions()).containsEntry("resume_mode", "resume");
        InteractiveInput interactiveInput = (InteractiveInput) resumeTask.getExtensions().get("interactive_input");
        assertThat(interactiveInput.getUserInputs()).containsExactlyEntriesOf(Map.of("questioner", "上海"));
        assertThat(resumeTask.getExtensions()).containsEntry("workflow_id", "location_workflow_resume");
    }

    @Test
    void testWorkflowAgentWorkflowTaggedWithAgentId() {
        String workflowId = unique("tag_test_wf");
        String agentId = unique("wf_agent_tag_test");
        String workflowKey = WorkflowKeys.generateWorkflowKey(workflowId, "1.0");
        WorkflowAgent agent = new WorkflowAgent(workflowConfig(agentId));
        Workflow workflow = new Workflow(workflowCard(workflowId, "Tag Test WF"));

        try {
            agent.addWorkflows(List.of(workflow));

            assertThat(Runner.resourceMgr().resourceHasTag(workflowKey, agentId)).isTrue();
            assertThat(Runner.resourceMgr().resourceHasTag(workflowKey, ResourceManagerBase.GLOBAL)).isFalse();
        } finally {
            Runner.resourceMgr().removeWorkflow(workflowKey);
        }
    }

    @Test
    void testTwoWorkflowAgentsIsolated() {
        String workflowIdA = unique("wf_iso_a");
        String workflowIdB = unique("wf_iso_b");
        String agentIdA = unique("iso_agent_a");
        String agentIdB = unique("iso_agent_b");
        String workflowKeyA = WorkflowKeys.generateWorkflowKey(workflowIdA, "1.0");
        String workflowKeyB = WorkflowKeys.generateWorkflowKey(workflowIdB, "1.0");
        WorkflowAgent agentA = new WorkflowAgent(workflowConfig(agentIdA));
        WorkflowAgent agentB = new WorkflowAgent(workflowConfig(agentIdB));

        try {
            agentA.addWorkflows(List.of(new Workflow(workflowCard(workflowIdA, "WF A"))));
            agentB.addWorkflows(List.of(new Workflow(workflowCard(workflowIdB, "WF B"))));

            assertThat(Runner.resourceMgr().resourceHasTag(workflowKeyA, agentIdA)).isTrue();
            assertThat(Runner.resourceMgr().resourceHasTag(workflowKeyA, agentIdB)).isFalse();
            assertThat(Runner.resourceMgr().resourceHasTag(workflowKeyB, agentIdB)).isTrue();
            assertThat(Runner.resourceMgr().resourceHasTag(workflowKeyB, agentIdA)).isFalse();
        } finally {
            Runner.resourceMgr().removeWorkflow(workflowKeyA);
            Runner.resourceMgr().removeWorkflow(workflowKeyB);
        }
    }

    private static WorkflowAgentConfig workflowConfig(String agentId) {
        WorkflowAgentConfig config = new WorkflowAgentConfig();
        config.setId(agentId);
        config.setVersion("1.0");
        config.setDescription("workflow agent parity test");
        config.setWorkflows(List.of());
        return config;
    }

    private static WorkflowCard workflowCard(String id, String name) {
        return new WorkflowCard(id, name, "Simple workflow: " + name, "1.0", Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string")),
                "required", List.of("query")
        ));
    }

    private static Task workflowTask(String taskId, String sessionId, String workflowId) {
        Task task = new Task(sessionId, taskId, TaskType.WORKFLOW.getValue());
        task.setStatus(TaskStatus.SUBMITTED);
        task.setDescription("workflow");
        task.setExtensions(new LinkedHashMap<>(Map.of(
                "workflow_id", workflowId,
                "workflow_version", "1.0",
                "resume_mode", "new",
                "filtered_inputs", Map.of("query", "查询天气")
        )));
        return task;
    }

    private static RecordingExecutor executor(TaskManager taskManager, List<Object> stream) {
        ControllerConfig config = new ControllerConfig();
        return new RecordingExecutor(new TaskExecutorDependencies(
                config,
                null,
                null,
                taskManager,
                new EventQueue(config)
        ), stream);
    }

    private static WorkflowEventHandler eventHandler(List<WorkflowCard> workflows) {
        ControllerConfig config = new ControllerConfig();
        WorkflowEventHandler handler = new WorkflowEventHandler();
        AbilityManager abilityManager = new AbilityManager();
        abilityManager.add(workflows);
        handler.setConfig(config);
        handler.setAbilityManager(abilityManager);
        handler.setTaskManager(new TaskManager(config));
        return handler;
    }

    private static InputEvent jsonEvent(String query) {
        return new InputEvent(List.of(new DataFrame.JsonDataFrame(Map.of("query", query))));
    }

    private static Map<String, Object> interruptedState(
            String stateKey,
            String componentId,
            Object lastInteractionValue,
            Map<String, Object> extensions) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("extensions", new LinkedHashMap<>(extensions));

        Map<String, Object> interruptedInfo = new LinkedHashMap<>();
        interruptedInfo.put("task", task);
        interruptedInfo.put("component_id", componentId);
        interruptedInfo.put("last_interaction_value", lastInteractionValue);

        Map<String, Object> interruptedTasks = new LinkedHashMap<>();
        interruptedTasks.put(stateKey, interruptedInfo);

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("interrupted_tasks", interruptedTasks);
        return state;
    }

    private static List<ControllerOutputChunk> iteratorToList(Iterator<ControllerOutputChunk> iterator) {
        List<ControllerOutputChunk> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result;
    }

    private static WorkflowOutput workflowOutput(ControllerOutputChunk chunk) {
        DataFrame.JsonDataFrame frame = (DataFrame.JsonDataFrame) chunk.getControllerPayload().getData().get(0);
        return (WorkflowOutput) frame.data().get("result");
    }

    private static String unique(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    public static final class RecordingController {
        private Map<String, Object> invokeInputs;
        private AgentSessionApi invokeSession;

        public CompletionStage<Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            invokeInputs = inputs;
            invokeSession = session;
            return CompletableFuture.completedFuture(new LinkedHashMap<>(Map.of(
                    "result_type", "answer",
                    "output", new WorkflowOutput(new LinkedHashMap<>(inputs), WorkflowExecutionState.COMPLETED)
            )));
        }
    }

    public static final class SingleInvokeController {
        private Map<String, Object> invokeInputs;
        private AgentSessionApi invokeSession;

        public CompletionStage<Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            invokeInputs = inputs;
            invokeSession = session;
            return CompletableFuture.completedFuture(new LinkedHashMap<>(Map.of(
                    "result_type", "answer",
                    "output", new WorkflowOutput(
                            Map.of("result", inputs.get("query")),
                            WorkflowExecutionState.COMPLETED)
            )));
        }
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
            if (data == null) {
                return;
            }
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (entry.getValue() == null) {
                    state.remove(entry.getKey());
                } else {
                    state.put(entry.getKey(), entry.getValue());
                }
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
