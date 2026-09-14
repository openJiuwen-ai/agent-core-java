/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow_agent;

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
 * Mirrors Python's {@code TestMultiWorkflowDefaultResponse} in
 * {@code tests/unit_tests/agent/workflow_agent/test_mock_workflow_agent_multi_workflow_default_response.py}.
 */
public class WorkflowAgentDefaultResponseMissingTest {

    private static final String DEFAULT_TEXT = "Sorry, I cannot understand your question";

    @Test
    void testDefaultResponseWithConfig() {
        Fixture fixture = Fixture.withDefaultResponse(DEFAULT_TEXT);

        fixture.handleInput("blahblah random xyz");

        OutputSchema chunk = fixture.firstWorkflowFinal();
        Map<String, Object> payload = map(chunk.getPayload());
        assertThat(payload).containsEntry("status", "default_response");
        assertThat(payload).containsEntry("response", DEFAULT_TEXT);
        assertChatRoles(fixture, "user", "assistant");
    }

    @Test
    void testFallbackToFirstWorkflow() {
        Fixture fixture = Fixture.withoutDefaultResponse();

        Task task = fixture.handleInput("blahblah random xyz");
        WorkflowOutput output = fixture.execute(task);

        assertThat(output.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        assertThat(map(output.getResult()).get("response").toString()).contains("weather:");
        assertChatRoles(fixture, "user", "assistant");
    }

    @Test
    void testDefaultResponseStream() {
        Fixture fixture = Fixture.withDefaultResponse(DEFAULT_TEXT);

        fixture.handleInput("blahblah random xyz");

        OutputSchema chunk = fixture.firstWorkflowFinal();
        assertThat(chunk.getType()).isEqualTo("workflow_final");
        assertThat(map(chunk.getPayload()).get("response")).isEqualTo(DEFAULT_TEXT);
        assertChatRoles(fixture, "user", "assistant");
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

        private Fixture(String defaultText) {
            ControllerConfig config = new ControllerConfig();
            config.setEnableIntentRecognition(true);
            config.setDefaultResponse(new ControllerConfig.DefaultResponse("text", defaultText));
            this.session = new RecordingSession("default-response-session");
            this.contextEngine = new ContextEngine();
            this.contextEngine.createContext(ContextEngine.DEFAULT_CONTEXT_ID, session);
            this.taskManager = new TaskManager(config);

            AbilityManager abilityManager = new AbilityManager();
            abilityManager.add(List.of(
                    workflow("weather_flow", "weather_query", "Query weather, temperature, forecast"),
                    workflow("stock_flow", "stock_query", "Query stock price, market trends")
            ));

            this.handler = new WorkflowEventHandler();
            handler.setConfig(config);
            handler.setAbilityManager(abilityManager);
            handler.setTaskManager(taskManager);
            handler.setContextEngine(contextEngine);
            handler.setIntentDetector(new NoMatchIntentDetector());

            this.executor = new RecordingWorkflowExecutor(new TaskExecutorDependencies(
                    config,
                    abilityManager,
                    contextEngine,
                    taskManager,
                    new EventQueue(config)
            ));
        }

        private static Fixture withDefaultResponse(String defaultText) {
            return new Fixture(defaultText);
        }

        private static Fixture withoutDefaultResponse() {
            return new Fixture(null);
        }

        private Task handleInput(Object query) {
            List<String> beforeTaskIds = taskManager.getTask(TaskFilter.bySessionId(session.getSessionId()))
                    .stream()
                    .map(Task::getTaskId)
                    .toList();
            handler.handleInput(new EventHandlerInput(jsonEvent(query), session));
            List<Task> tasks = taskManager.getTask(TaskFilter.bySessionId(session.getSessionId()));
            return tasks.stream()
                    .filter(task -> !beforeTaskIds.contains(task.getTaskId()))
                    .findFirst()
                    .orElse(null);
        }

        private WorkflowOutput execute(Task task) {
            assertThat(task).isNotNull();
            Iterator<ControllerOutputChunk> iterator = executor.executeAbility(task.getTaskId(), session);
            assertThat(iterator.hasNext()).isTrue();
            return workflowOutput(iterator.next());
        }

        private OutputSchema firstWorkflowFinal() {
            return session.stream.stream()
                    .filter(OutputSchema.class::isInstance)
                    .map(OutputSchema.class::cast)
                    .filter(chunk -> "workflow_final".equals(chunk.getType()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected workflow_final chunk"));
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
            Map<String, Object> arguments = map(inputs);
            String query = String.valueOf(arguments.getOrDefault("query", ""));
            String prefix = workflow.toString().startsWith("stock") ? "stock:" : "weather:";
            return List.of(new OutputSchema("workflow_final", 0, Map.of("response", prefix + query))).iterator();
        }

        @Override
        protected Object createWorkflowSession(AgentSessionApi session) {
            return session;
        }
    }

    private static final class NoMatchIntentDetector extends WorkflowEventHandler.IntentDetector {
        @Override
        public List<WorkflowEventHandler.TaskResult> processMessage(Event event) {
            return List.of();
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
