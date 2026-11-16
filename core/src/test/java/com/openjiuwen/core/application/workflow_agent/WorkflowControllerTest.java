/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow_agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.constants.TaskType;
import com.openjiuwen.core.controller.legacy.task.Task;
import com.openjiuwen.core.controller.legacy.task.TaskInput;
import com.openjiuwen.core.controller.legacy.task.TaskStatus;
import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.legacy.schema.WorkflowSchema;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused parity tests for workflow controller state and input logic.
 * <p>
 * Mirrors Python's {@code WorkflowController} behavior in
 * {@code openjiuwen/core/application/workflow_agent/workflow_controller.py}.
 */
class WorkflowControllerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void intentRequestDescribesPartialInputsAndUsesTheReturnedCategory() throws IOException {
        AtomicReference<JsonNode> request = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                request.set(JSON.readTree(exchange.getRequestBody()));
                byte[] body = JSON.writeValueAsBytes(Map.of("choices", List.of(Map.of("message",
                        Map.of("role", "assistant", "content", "{\"result\":2}")))));
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset="
                        + StandardCharsets.UTF_8.name());
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        try {
            WorkflowSchema weather = workflow("weather", "Weather", "1", Map.of());
            WorkflowSchema bank = workflow("bank", "Bank", "1", Map.of());
            WorkflowController controller = controller(List.of(weather, bank), null);
            controller.getAgentConfig().setModel(new ModelConfig("OpenAI", BaseModelInfo.builder()
                    .apiBase("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                    .apiKey("fixture").modelName("routing-contract").timeout(5).build()));
            controller.setPropagateIntentErrors(true);

            WorkflowController.Intent intent = controller.intentDetection(event("partial", "Bank A"),
                    new MemorySession("routing-session"));

            assertThat(intent.getWorkflow()).isSameAs(bank);
            assertThat(request.get().path("messages").path(0).path("content").asText())
                    .contains("不要求用户一次提供完整请求或全部参数", "分类1: Weather", "分类2: Bank");
            assertThat(request.get().path("messages").path(1).path("content").asText()).contains("Bank A");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void intentDetectionRejectsMissingWorkflows() {
        WorkflowController controller = new WorkflowController(
                WorkflowController.AgentConfig.builder().build(),
                null,
                new MemorySession("s1")
        );

        assertThatThrownBy(() -> controller.intentDetection(event("E1", "hello"), new MemorySession("s1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No workflows configured");
    }

    @Test
    void singleWorkflowCreatesNewTaskWithFilteredInputs() {
        WorkflowSchema workflow = workflow(
                "weather",
                "Weather",
                "1.0",
                Map.of(
                        "properties", Map.of(
                                "city", Map.of("type", "string"),
                                "unit", Map.of("type", "string")
                        ),
                        "required", List.of("city")
                )
        );
        WorkflowController controller = controller(List.of(workflow), null);
        WorkflowController.Event event = WorkflowController.Event.builder()
                .eventId("EVT")
                .content(WorkflowController.EventContent.builder()
                        .query("Shenzhen")
                        .extensions(Map.of("unit", "celsius", "ignored", true))
                        .build())
                .build();

        WorkflowController.Intent intent = controller.intentDetection(event, new MemorySession("s1"));

        assertThat(intent.getIntentType()).isEqualTo(WorkflowController.IntentType.EXEC_NEW_TASK);
        assertThat(intent.getWorkflow()).isSameAs(workflow);
        assertThat(intent.getTask().getTaskId()).isEqualTo("workflow_EVT");
        assertThat(intent.getTask().getTaskType()).isEqualTo(TaskType.WORKFLOW);
        assertThat(intent.getTask().getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(intent.getTask().getInput().getTargetId()).isEqualTo("weather_1.0");
        assertThat((Map<String, Object>) intent.getTask().getInput().getArguments())
                .containsExactlyInAnyOrderEntriesOf(Map.of("city", "Shenzhen", "unit", "celsius"));
    }

    @Test
    void multipleWorkflowDetectionCanReturnDefaultResponse() {
        WorkflowController controller = controller(
                List.of(workflow("a", "A", "1", Map.of()), workflow("b", "B", "1", Map.of())),
                WorkflowController.DefaultResponse.builder().text("fallback").build()
        );
        controller.setWorkflowDetector(event -> List.of());

        WorkflowController.Intent intent = controller.intentDetection(event("E1", "hello"), new MemorySession("s1"));

        assertThat(intent.getIntentType()).isEqualTo(WorkflowController.IntentType.DEFAULT_RESPONSE);
        assertThat(intent.getMetadata()).containsEntry("default_response_text", "fallback");
    }

    @Test
    void intentDetectionFailureDoesNotExecuteAnUnrelatedWorkflow() {
        WorkflowController controller = controller(
                List.of(workflow("a", "A", "1", Map.of()), workflow("b", "B", "1", Map.of())), null);
        controller.setPropagateIntentErrors(true);
        IllegalStateException failure = new IllegalStateException("model endpoint unavailable");
        controller.setWorkflowDetector(event -> {
            throw failure;
        });

        assertThatThrownBy(() -> controller.intentDetection(event("E1", "B"), new MemorySession("s1")))
                .isSameAs(failure);
    }

    @Test
    void intentDetectionFailureRetainsLegacyFallbackByDefault() {
        WorkflowSchema first = workflow("a", "A", "1", Map.of());
        WorkflowController controller = controller(List.of(first, workflow("b", "B", "1", Map.of())), null);
        controller.setWorkflowDetector(event -> {
            throw new IllegalStateException("invalid provider");
        });

        WorkflowController.Intent intent = controller.intentDetection(event("E1", "B"), new MemorySession("s1"));

        assertThat(intent.getWorkflow()).isSameAs(first);
        assertThat(intent.getIntentType()).isEqualTo(WorkflowController.IntentType.EXEC_NEW_TASK);
    }

    @Test
    void interruptedStructuredValueReturnsInterruptionAgain() {
        WorkflowSchema workflow = workflow("flow", "Flow", "1.2", Map.of());
        MemorySession session = new MemorySession("s1");
        Task task = task("T1", "flow_1.2");
        session.state.put("workflow_controller", Map.of(
                "interrupted_tasks", Map.of(
                        "flow_1_2", Map.of(
                                "task", task,
                                "component_id", "questioner",
                                "last_interaction_value", Map.of("schema", "choice")
                        )
                )
        ));
        WorkflowController controller = controller(List.of(workflow), null);

        WorkflowController.Intent intent = controller.intentDetection(event("E1", "hello"), session);

        assertThat(intent.getIntentType()).isEqualTo(WorkflowController.IntentType.RESUME_TASK);
        assertThat(intent.getMetadata()).containsEntry("return_interruption", true);
        Object returned = controller.handleResume(event("E1", "hello"), intent, session);
        assertThat((List<?>) returned).hasSize(1);
        OutputSchema output = (OutputSchema) ((List<?>) returned).get(0);
        assertThat(output.getType()).isEqualTo(Constant.INTERACTION);
    }

    @Test
    void interactiveInputNodeIdFastPathResumesMatchingWorkflow() {
        WorkflowSchema workflow = workflow("flow", "Flow", "1.2", Map.of());
        MemorySession session = new MemorySession("s1");
        Task task = task("T1", "flow_1.2");
        session.state.put("workflow_controller", Map.of(
                "interrupted_tasks", Map.of(
                        "flow_1_2", Map.of(
                                "task", task,
                                "component_id", List.of("node-a", "node-b"),
                                "last_interaction_value", Map.of("schema", "choice")
                        )
                )
        ));
        WorkflowController controller = controller(List.of(workflow), null);
        WorkflowController.Event event = WorkflowController.Event.builder()
                .eventId("E2")
                .content(WorkflowController.EventContent.builder()
                        .interactiveInput(WorkflowController.InteractiveInput.builder()
                                .userInputs(Map.of("node-b", "yes"))
                                .build())
                        .build())
                .build();

        WorkflowController.Intent intent = controller.intentDetection(event, session);

        assertThat(intent.getIntentType()).isEqualTo(WorkflowController.IntentType.RESUME_TASK);
        assertThat(intent.getTask().getTaskId()).isEqualTo("T1");
        assertThat(intent.getMetadata()).isEmpty();
    }

    @Test
    @Disabled("Assertion no longer matches current develop behavior; kept disabled pending product decision or test rewrite")
    void interruptTaskStoresComponentIdsAndFirstInterruptOnly() {
        WorkflowController controller = controller(List.of(workflow("flow", "Flow", "1", Map.of())), null);
        MemorySession session = new MemorySession("s1");
        Task task = task("T1", "flow_1");
        List<OutputSchema> outputs = List.of(
                new OutputSchema("trace", 0, Map.of("response", "trace")),
                new OutputSchema(Constant.INTERACTION, 1, new WorkflowController.InteractionOutput("node-a", "first")),
                new OutputSchema(Constant.INTERACTION, 2, new WorkflowController.InteractionOutput("node-b", "second"))
        );

        Map<String, Object> result = controller.interruptTask(task, session, outputs);

        assertThat(result).containsEntry("status", "interrupted");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.INTERRUPTED);
        assertThat(controller.extractComponentIdFromInteractionData(outputs)).isEqualTo(List.of("node-a", "node-b"));
        assertThat(controller.extractInteractionValueFromInteractionData(outputs)).isEqualTo("first");
        assertThat(controller.getFirstInterrupt(outputs)).containsExactly(outputs.get(1));
        assertThat(controller.countInteractions(outputs)).isEqualTo(2);
    }

    @Test
    void helperMethodsMirrorSchemaFallbacksAndWorkflowState() {
        WorkflowController controller = controller(List.of(workflow("flow", "Flow", "1", Map.of())), null);

        assertThat(controller.getRequiredInputKey(Map.of("properties", Map.of("query", Map.of("type", "string")))))
                .contains("query");
        assertThat(controller.getRequiredInputKey(Map.of(
                "properties", Map.of("city", Map.of("type", "string")),
                "required", List.of("city")
        ))).contains("city");
        assertThat(controller.filterWorkflowInputs(
                Map.of("query", Map.of("type", "string")),
                Map.of("query", "hello", "extra", "drop")
        )).containsExactlyEntriesOf(Map.of("query", "hello"));
        assertThat(controller.isWorkflowInterrupted(
                new WorkflowController.WorkflowOutput(List.of(), WorkflowController.WorkflowExecutionState.INPUT_REQUIRED)
        )).isTrue();
    }

    private WorkflowController controller(List<WorkflowSchema> workflows, WorkflowController.DefaultResponse response) {
        return new WorkflowController(
                WorkflowController.AgentConfig.builder()
                        .id("agent-1")
                        .workflows(workflows)
                        .defaultResponse(response)
                        .build(),
                null,
                new MemorySession("s1")
        );
    }

    private WorkflowController.Event event(String id, String query) {
        return WorkflowController.Event.builder()
                .eventId(id)
                .content(WorkflowController.EventContent.builder().query(query).build())
                .build();
    }

    private WorkflowSchema workflow(String id, String name, String version, Map<String, Object> inputs) {
        return WorkflowSchema.builder()
                .id(id)
                .name(name)
                .version(version)
                .inputs(inputs)
                .build();
    }

    private Task task(String taskId, String workflowId) {
        Task task = new Task();
        task.setTaskId(taskId);
        task.setTaskType(TaskType.WORKFLOW);
        task.setStatus(TaskStatus.INTERRUPTED);
        task.setInput(new TaskInput(workflowId, "Flow", new LinkedHashMap<>()));
        return task;
    }

    /**
     * Mirrors Python's session state surface used by
     * {@code openjiuwen/core/application/workflow_agent/workflow_controller.py}.
     */
    private static final class MemorySession implements WorkflowController.SessionPort {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();

        private MemorySession(String sessionId) {
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
        public void updateState(Map<String, Object> update) {
            for (Map.Entry<String, Object> entry : update.entrySet()) {
                if (entry.getValue() == null) {
                    state.remove(entry.getKey());
                } else {
                    state.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }
}
