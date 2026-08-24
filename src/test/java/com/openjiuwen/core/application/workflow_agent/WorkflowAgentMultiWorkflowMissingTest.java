/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow_agent;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.constants.TaskType;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.modules.EventHandlerInput;
import com.openjiuwen.core.controller.modules.EventQueue;
import com.openjiuwen.core.controller.modules.TaskExecutorDependencies;
import com.openjiuwen.core.controller.modules.TaskFilter;
import com.openjiuwen.core.controller.modules.TaskManager;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestWorkflowAgentMultiWorkflow} in
 * {@code tests/unit_tests/agent/workflow_agent/test_mock_workflow_agent_multi_workflow.py}.
 */
class WorkflowAgentMultiWorkflowMissingTest {

    @Test
    void testMultiWorkflowRouting() {
        Fixture fixture = new Fixture(
                "test_multi_wf_agent",
                List.of(
                        workflow("weather_flow", "天气查询", "查询某地的天气情况、温度、气象信息"),
                        workflow("stock_flow", "股票查询", "查询股票价格、股市行情、股票走势等金融信息")
                ),
                new SequencedIntentDetector("股票查询")
        );

        Task task = fixture.handleInput("查看上海股票走势");
        assertThat(task.getExtensions()).containsEntry("workflow_id", "stock_flow");
        assertThat(map(task.getExtensions().get("filtered_inputs"))).containsEntry("query", "查看上海股票走势");

        WorkflowOutput output = fixture.execute(task);

        assertThat(output.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        Map<String, Object> result = map(output.getResult());
        assertThat(result.get("response").toString())
                .contains("stock:")
                .contains("查看上海股票走势");
        assertChatRoles(fixture, "user", "assistant");
    }

    @Test
    void testMultiWorkflowJumpAndRecovery() {
        Fixture fixture = new Fixture(
                "test_jump_recovery",
                List.of(
                        workflow("weather_flow_jump", "天气查询", "查询某地的天气情况、温度、气象信息"),
                        workflow("stock_flow_jump", "股票查询", "查询股票价格、股市行情、股票走势等金融信息")
                ),
                new SequencedIntentDetector("天气查询", "股票查询", "天气查询", "股票查询")
        );

        WorkflowOutput first = fixture.execute(fixture.handleInput("查询天气"));
        assertThat(first.getState()).isEqualTo(WorkflowExecutionState.INPUT_REQUIRED);
        assertThat(fixture.interruptedWorkflowIds()).containsExactly("weather_flow_jump");

        WorkflowOutput second = fixture.execute(fixture.handleInput("查看股票"));
        assertThat(second.getState()).isEqualTo(WorkflowExecutionState.INPUT_REQUIRED);
        assertThat(fixture.interruptedWorkflowIds())
                .containsExactlyInAnyOrder("weather_flow_jump", "stock_flow_jump");

        WorkflowOutput third = fixture.execute(fixture.handleInput("查询北京天气"));
        assertThat(third.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        assertThat(map(third.getResult()).get("response").toString()).contains("查询北京天气");
        assertThat(fixture.interruptedWorkflowIds()).containsExactly("stock_flow_jump");

        WorkflowOutput fourth = fixture.execute(fixture.handleInput("查看AAPL股票"));
        assertThat(fourth.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        assertThat(map(fourth.getResult()).get("response").toString()).contains("查看AAPL股票");
        assertThat(fixture.interruptedWorkflowIds()).isEmpty();
        assertChatRoles(fixture, "user", "assistant", "user", "assistant",
                "user", "assistant", "user", "assistant");
    }

    @Test
    void testInteractiveInputFastPath() {
        SequencedIntentDetector detector = new SequencedIntentDetector("天气查询");
        Fixture fixture = new Fixture(
                "test_interactive_fast_path",
                List.of(
                        workflow("weather_flow_skip", "天气查询", "查询某地的天气情况、温度、气象信息"),
                        workflow("stock_flow_skip", "股票查询", "查询股票价格、股市行情、股票走势等金融信息")
                ),
                detector
        );

        WorkflowOutput first = fixture.execute(fixture.handleInput("查询天气"));
        assertThat(first.getState()).isEqualTo(WorkflowExecutionState.INPUT_REQUIRED);
        String nodeId = fixture.lastInteractionId();

        InteractiveInput interactiveInput = new InteractiveInput();
        interactiveInput.update(nodeId, "北京");
        WorkflowOutput second = fixture.execute(fixture.handleInput(interactiveInput));

        assertThat(second.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        assertThat(map(second.getResult()).get("response").toString()).contains("北京");
        assertThat(detector.callCount()).isEqualTo(1);
        assertChatRoles(fixture, "user", "assistant", "user", "assistant");
    }

    @Test
    void testInteractiveInputTargetsCorrectWorkflow() {
        SequencedIntentDetector detector = new SequencedIntentDetector("天气查询", "股票查询");
        Fixture fixture = new Fixture(
                "test_precise_resume",
                List.of(
                        workflow("weather_flow_resume", "天气查询", "查询某地的天气情况、温度、气象信息"),
                        workflow("stock_flow_resume", "股票查询", "查询股票价格、股市行情、股票走势等金融信息")
                ),
                detector
        );

        WorkflowOutput first = fixture.execute(fixture.handleInput("查询天气"));
        assertThat(first.getState()).isEqualTo(WorkflowExecutionState.INPUT_REQUIRED);
        String weatherNode = fixture.lastInteractionId();

        WorkflowOutput second = fixture.execute(fixture.handleInput("查询股票"));
        assertThat(second.getState()).isEqualTo(WorkflowExecutionState.INPUT_REQUIRED);
        String stockNode = fixture.lastInteractionId();
        assertThat(stockNode).isNotEqualTo(weatherNode);

        InteractiveInput weatherAnswer = new InteractiveInput();
        weatherAnswer.update(weatherNode, "北京");
        Task weatherResume = fixture.handleInput(weatherAnswer);
        WorkflowOutput third = fixture.execute(weatherResume);

        assertThat(weatherResume.getExtensions()).containsEntry("workflow_id", "weather_flow_resume");
        assertThat(third.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        assertThat(map(third.getResult()).get("response").toString()).contains("北京");

        InteractiveInput stockAnswer = new InteractiveInput();
        stockAnswer.update(stockNode, "AAPL");
        Task stockResume = fixture.handleInput(stockAnswer);
        WorkflowOutput fourth = fixture.execute(stockResume);

        assertThat(stockResume.getExtensions()).containsEntry("workflow_id", "stock_flow_resume");
        assertThat(fourth.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        assertThat(map(fourth.getResult()).get("response").toString()).contains("AAPL");
        assertThat(detector.callCount()).isEqualTo(2);
        assertChatRoles(fixture, "user", "assistant", "user", "assistant",
                "user", "assistant", "user", "assistant");
    }

    private static WorkflowCard workflow(String id, String name, String description) {
        return new WorkflowCard(id, name, description, "1.0", Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string")),
                "required", List.of("query")
        ));
    }

    private static InputEvent jsonEvent(Object query) {
        return new InputEvent(List.of(new DataFrame.JsonDataFrame(Map.of("query", query))));
    }

    private static WorkflowOutput workflowOutput(ControllerOutputChunk chunk) {
        DataFrame.JsonDataFrame frame = (DataFrame.JsonDataFrame) chunk.getControllerPayload().getData().get(0);
        return (WorkflowOutput) frame.data().get("result");
    }

    private static void assertChatRoles(Fixture fixture, String... roles) {
        List<BaseMessage> messages = fixture.chatMessages();
        assertThat(messages).extracting(BaseMessage::getRole).containsExactly(roles);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    private static final class Fixture {
        private final RecordingSession session;
        private final WorkflowEventHandler handler;
        private final RecordingWorkflowExecutor executor;
        private final TaskManager taskManager;
        private final ContextEngine contextEngine;

        private Fixture(String sessionId, List<WorkflowCard> workflows, SequencedIntentDetector detector) {
            ControllerConfig config = new ControllerConfig();
            config.setEnableIntentRecognition(true);
            this.session = new RecordingSession(sessionId);
            this.contextEngine = new ContextEngine();
            this.contextEngine.createContext(ContextEngine.DEFAULT_CONTEXT_ID, session);
            this.taskManager = new TaskManager(config);

            AbilityManager abilityManager = new AbilityManager();
            abilityManager.add(workflows);

            this.handler = new WorkflowEventHandler();
            handler.setConfig(config);
            handler.setAbilityManager(abilityManager);
            handler.setTaskManager(taskManager);
            handler.setContextEngine(contextEngine);
            handler.setIntentDetector(detector);

            this.executor = new RecordingWorkflowExecutor(new TaskExecutorDependencies(
                    config,
                    abilityManager,
                    contextEngine,
                    taskManager,
                    new EventQueue(config)
            ));
        }

        private Task handleInput(Object query) {
            List<String> beforeTaskIds = taskManager.getTask(TaskFilter.bySessionId(session.getSessionId()))
                    .stream()
                    .map(Task::getTaskId)
                    .toList();
            Event event = jsonEvent(query);
            handler.handleInput(new EventHandlerInput(event, session));
            List<Task> tasks = taskManager.getTask(TaskFilter.bySessionId(session.getSessionId()));
            return tasks.stream()
                    .filter(task -> !beforeTaskIds.contains(task.getTaskId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected handleInput to create one workflow task"));
        }

        private WorkflowOutput execute(Task task) {
            Iterator<ControllerOutputChunk> iterator = executor.executeAbility(task.getTaskId(), session);
            assertThat(iterator.hasNext()).isTrue();
            return workflowOutput(iterator.next());
        }

        private String lastInteractionId() {
            List<OutputSchema> interactions = session.stream.stream()
                    .filter(OutputSchema.class::isInstance)
                    .map(OutputSchema.class::cast)
                    .filter(chunk -> Constant.INTERACTION.equals(chunk.getType()))
                    .toList();
            assertThat(interactions).isNotEmpty();
            InteractionOutput payload = (InteractionOutput) interactions.get(interactions.size() - 1).getPayload();
            return payload.getId();
        }

        private List<String> interruptedWorkflowIds() {
            Map<String, Object> state = map(session.getState("workflow_controller"));
            Map<String, Object> interrupted = map(state.get("interrupted_tasks"));
            return new ArrayList<>(interrupted.keySet());
        }

        private List<BaseMessage> chatMessages() {
            return contextEngine.getContext(ContextEngine.DEFAULT_CONTEXT_ID, session.getSessionId())
                    .getMessages(null, true);
        }
    }

    private static final class RecordingWorkflowExecutor extends WorkflowTaskExecutor {
        private RecordingWorkflowExecutor(TaskExecutorDependencies dependencies) {
            super(dependencies);
        }

        @Override
        protected Object findWorkflow(String workflowId, AgentSessionApi session, String agentId) {
            return workflowId;
        }

        @Override
        protected Iterator<?> runWorkflowStreaming(Object workflow, Object inputs, Object workflowSession,
                                                   com.openjiuwen.core.context.ModelContext context) {
            String workflowId = workflow.toString();
            if (inputs instanceof InteractiveInput interactiveInput) {
                Object answer = interactiveInput.getUserInputs().values().iterator().next();
                return List.of(workflowFinal(Map.of("response", answer.toString()))).iterator();
            }
            if (workflowId.endsWith("_jump") || workflowId.endsWith("_skip") || workflowId.endsWith("_resume")) {
                return List.of(new OutputSchema(
                        Constant.INTERACTION,
                        0,
                        new InteractionOutput(questionerId(workflowId), questionFor(workflowId))
                )).iterator();
            }
            Map<String, Object> arguments = map(inputs);
            String query = String.valueOf(arguments.getOrDefault("query", ""));
            String prefix = workflowId.startsWith("stock") ? "stock:" : "weather:";
            return List.of(workflowFinal(Map.of("response", prefix + query))).iterator();
        }

        @Override
        protected Object createWorkflowSession(AgentSessionApi session) {
            return session;
        }

        private static OutputSchema workflowFinal(Map<String, Object> payload) {
            return new OutputSchema("workflow_final", 0, payload);
        }

        private static String questionerId(String workflowId) {
            return workflowId.startsWith("stock") ? "stock_questioner" : "weather_questioner";
        }

        private static String questionFor(String workflowId) {
            return workflowId.startsWith("stock") ? "请提供股票代码" : "请提供地点";
        }
    }

    private static final class SequencedIntentDetector extends WorkflowEventHandler.IntentDetector {
        private final List<String> targetNames;
        private int index;

        private SequencedIntentDetector(String... targetNames) {
            this.targetNames = List.of(targetNames);
        }

        @Override
        public List<WorkflowEventHandler.TaskResult> processMessage(Event event) {
            String targetName = targetNames.get(Math.min(index, targetNames.size() - 1));
            index++;
            return List.of(new WorkflowEventHandler.TaskResult(
                    "intent-" + index,
                    TaskType.WORKFLOW,
                    new WorkflowEventHandler.TaskInput(targetName, targetName, null)
            ));
        }

        private int callCount() {
            return index;
        }
    }

    private static final class RecordingSession implements AgentSessionApi, ContextEngine.SessionPort {
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
