/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqRequestMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.stream.CustomSchema;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.TraceSchema;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code TestMessageSerializer} in
 * {@code tests/unit_tests/core/runner/dunner/test_message_serializer.py}.
 *
 * <p>Also keeps basic serializer contract checks for
 * {@code openjiuwen/core/runner/drunner/dmessage_queue/message_serializer.py}.</p>
 */
class MessageSerializerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String INFO = "\u4fe1\u606f";
    private static final String SHANGHAI = "\u4e0a\u6d77";
    private static final String HELLO = "\u4f60\u597d";
    private static final String HELLO_EXCLAMATION = "\u4f60\u597d\uff01";

    @Test
    @SuppressWarnings("unchecked")
    void serializesRequestWithPythonClassMarkerAndSnakeCaseFields() throws Exception {
        DmqRequestMessage message = new DmqRequestMessage();
        message.setMessageId("message-1");
        message.setPayload(Map.of("role", "user"));
        message.setReplyTopic("reply-topic");
        message.setRequestId("request-1");
        message.setSenderId("sender-1");
        message.setReceiverId("receiver-1");
        message.setEnableStream(true);
        message.setExpireAt(12.5d);

        byte[] bytes = MessageSerializer.serializeMessage(message);
        Map<String, Object> raw = MAPPER.readValue(bytes, Map.class);

        assertThat(raw).containsEntry("__class__", "DmqRequestMessage");
        assertThat(raw).containsEntry("message_id", "message-1");
        assertThat(raw).containsEntry("reply_topic", "reply-topic");
        assertThat(raw).containsEntry("enable_stream", true);
        assertThat(raw).containsEntry("expire_at", 12.5d);
        assertThat(raw).containsEntry("type", "INPUT");
        assertThat(raw).doesNotContainKey("messageId");
    }

    @Test
    void rejectsUnknownPayloadClass() {
        String json = "{\"__class__\":\"UnknownPayload\",\"value\":1}";

        assertThatThrownBy(() -> MessageSerializer.deserializeMessage(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown payload class");
    }

    @Test
    void testOutputSchema() {
        OutputSchema payload = new OutputSchema(
                Constant.INTERACTION,
                0,
                new InteractionOutput("l.2", "Please enter any key"));

        DmqResponseMessage restored = roundTripResponse(payload);

        assertThat(restored.getBody()).isInstanceOf(OutputSchema.class);
        OutputSchema schema = (OutputSchema) restored.getBody();
        assertThat(schema.getPayload()).isInstanceOf(InteractionOutput.class);
    }

    @Test
    void testListOfOutputSchema() {
        List<OutputSchema> payload = List.of(
                new OutputSchema(Constant.INTERACTION, 0, new InteractionOutput("questioner", INFO)),
                new OutputSchema("answer", 0, linkedMap("output", "123", "result_type", "answer")),
                new OutputSchema("workflow_final", 0, linkedMap("error", true, "message", "aaa", "status", "failed")),
                new OutputSchema("workflow_final", 0,
                        new WorkflowOutput(linkedMap("response", SHANGHAI, "output", Map.of()),
                                WorkflowExecutionState.COMPLETED)));

        DmqResponseMessage restored = roundTripResponse(payload);

        assertThat(restored.getBody()).isInstanceOf(List.class);
        List<?> restoredItems = (List<?>) restored.getBody();
        assertThat(restoredItems).hasSize(4);

        OutputSchema first = (OutputSchema) restoredItems.get(0);
        assertThat(first.getType()).isEqualTo(Constant.INTERACTION);
        assertThat(first.getPayload()).isInstanceOf(InteractionOutput.class);
        InteractionOutput firstPayload = (InteractionOutput) first.getPayload();
        assertThat(firstPayload.getId()).isEqualTo("questioner");
        assertThat(firstPayload.getValue()).isEqualTo(INFO);

        OutputSchema second = (OutputSchema) restoredItems.get(1);
        assertThat(second.getType()).isEqualTo("answer");
        assertThat(asMap(second.getPayload()))
                .containsEntry("output", "123")
                .containsEntry("result_type", "answer");

        OutputSchema third = (OutputSchema) restoredItems.get(2);
        assertThat(third.getType()).isEqualTo("workflow_final");
        assertThat(asMap(third.getPayload()))
                .containsEntry("error", true)
                .containsEntry("message", "aaa")
                .containsEntry("status", "failed");

        OutputSchema fourth = (OutputSchema) restoredItems.get(3);
        assertThat(fourth.getType()).isEqualTo("workflow_final");
        assertThat(fourth.getPayload()).isInstanceOf(WorkflowOutput.class);
        WorkflowOutput workflowOutput = (WorkflowOutput) fourth.getPayload();
        assertThat(workflowOutput.getResult()).isEqualTo(linkedMap("response", SHANGHAI, "output", Map.of()));
        assertThat(workflowOutput.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
    }

    @Test
    void testCustomschemaWithWorkflowOutput() {
        CustomSchema payload = new CustomSchema();
        payload.put("output", List.of(List.of("aaa")));
        payload.put("result_type", "answer");
        payload.put("aaa", "{\"aaa\":\"123\"}");
        payload.put("work", new WorkflowOutput(
                linkedMap("response", SHANGHAI, "output", Map.of()),
                WorkflowExecutionState.COMPLETED));

        DmqResponseMessage restored = roundTripResponse(payload);

        assertThat(restored.getBody()).isInstanceOf(CustomSchema.class);
        CustomSchema schema = (CustomSchema) restored.getBody();
        assertThat(schema.get("output")).isInstanceOf(List.class);
        assertThat(((List<?>) schema.get("output")).get(0)).isInstanceOf(List.class);
        assertThat(schema.get("work")).isInstanceOf(WorkflowOutput.class);
        WorkflowOutput work = (WorkflowOutput) schema.get("work");
        assertThat(work.getResult()).isEqualTo(linkedMap("response", SHANGHAI, "output", Map.of()));
        assertThat(work.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
    }

    @Test
    void testPlainDictInRequest() {
        DmqRequestMessage message = new DmqRequestMessage();
        message.setPayload(linkedMap("query", HELLO));

        DmqRequestMessage restored = roundTripRequest(message);

        assertThat(restored).isInstanceOf(DmqRequestMessage.class);
        assertThat(restored.getBody()).isInstanceOf(Map.class);
        assertThat(asMap(restored.getBody())).containsEntry("query", HELLO);
    }

    @Test
    void testDictResponse() {
        DmqResponseMessage restored = roundTripResponse(linkedMap("output", HELLO_EXCLAMATION, "result_type", "answer"));

        assertThat(restored.getBody()).isInstanceOf(Map.class);
        assertThat(asMap(restored.getBody())).containsEntry("output", HELLO_EXCLAMATION);
    }

    @Test
    void testDictWithEmbeddedBasemodel() {
        Map<String, Object> payload = linkedMap(
                "output",
                new WorkflowOutput(linkedMap("response", SHANGHAI, "output", Map.of()),
                        WorkflowExecutionState.COMPLETED),
                "result_type",
                "answer");

        DmqResponseMessage restored = roundTripResponse(payload);

        assertThat(restored.getBody()).isInstanceOf(Map.class);
        assertThat(asMap(restored.getBody()).get("output")).isInstanceOf(WorkflowOutput.class);
    }

    @Test
    void testWorkflowOutput() {
        WorkflowOutput payload = new WorkflowOutput(
                linkedMap("response", SHANGHAI, "output", Map.of()),
                WorkflowExecutionState.COMPLETED);

        DmqResponseMessage restored = roundTripResponse(payload);

        assertThat(restored.getBody()).isInstanceOf(WorkflowOutput.class);
        WorkflowOutput workflowOutput = (WorkflowOutput) restored.getBody();
        assertThat(workflowOutput.getResult()).isEqualTo(linkedMap("response", SHANGHAI, "output", Map.of()));
        assertThat(workflowOutput.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
    }

    @Test
    void testOutputSchemaInWorkflowOutput() {
        List<OutputSchema> listOutputSchema = List.of(
                new OutputSchema(Constant.INTERACTION, 0, new InteractionOutput("questioner", INFO)),
                new OutputSchema("answer", 0, linkedMap("output", "123", "result_type", "answer")),
                new OutputSchema("workflow_final", 0, new WorkflowOutput(
                        new OutputSchema("answer", 0, linkedMap("output", "456", "result_type", "answer")),
                        WorkflowExecutionState.COMPLETED)));
        WorkflowOutput payload = new WorkflowOutput(listOutputSchema, WorkflowExecutionState.COMPLETED);

        DmqResponseMessage restored = roundTripResponse(payload);

        assertThat(restored.getBody()).isInstanceOf(WorkflowOutput.class);
        WorkflowOutput workflowOutput = (WorkflowOutput) restored.getBody();
        assertThat(workflowOutput.getResult()).isInstanceOf(List.class);
        List<?> result = (List<?>) workflowOutput.getResult();
        assertThat(result).hasSize(3);
        assertThat(workflowOutput.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);

        OutputSchema first = (OutputSchema) result.get(0);
        assertThat(first.getType()).isEqualTo(Constant.INTERACTION);
        assertThat(first.getPayload()).isInstanceOf(InteractionOutput.class);
        InteractionOutput firstPayload = (InteractionOutput) first.getPayload();
        assertThat(firstPayload.getId()).isEqualTo("questioner");
        assertThat(firstPayload.getValue()).isEqualTo(INFO);

        OutputSchema second = (OutputSchema) result.get(1);
        assertThat(second.getType()).isEqualTo("answer");
        assertThat(asMap(second.getPayload()))
                .containsEntry("output", "123")
                .containsEntry("result_type", "answer");

        OutputSchema third = (OutputSchema) result.get(2);
        assertThat(third.getType()).isEqualTo("workflow_final");
        assertThat(third.getPayload()).isInstanceOf(WorkflowOutput.class);
        WorkflowOutput nestedWorkflow = (WorkflowOutput) third.getPayload();
        assertThat(nestedWorkflow.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        assertThat(nestedWorkflow.getResult()).isInstanceOf(OutputSchema.class);
        OutputSchema nestedSchema = (OutputSchema) nestedWorkflow.getResult();
        assertThat(nestedSchema.getType()).isEqualTo("answer");
        assertThat(asMap(nestedSchema.getPayload()))
                .containsEntry("output", "456")
                .containsEntry("result_type", "answer");
    }

    @Test
    void testTraceSchemaPayload() {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", HELLO);
        message.put("name", null);

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("inputs", List.of(message));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("class_name", "OpenAILLM");
        metadata.put("type", "llm");

        Map<String, Object> tracePayload = new LinkedHashMap<>();
        tracePayload.put("traceId", "94884432-1558-40d3-aded-b09e1111e171");
        tracePayload.put("startTime", LocalDateTime.of(2025, 11, 18, 19, 22, 5, 728_961_000));
        tracePayload.put("endTime", null);
        tracePayload.put("inputs", inputs);
        tracePayload.put("outputs", null);
        tracePayload.put("error", null);
        tracePayload.put("invokeId", "ab4f7f56-e69e-460d-8ba2-ed6e2e2f6bb0");
        tracePayload.put("parentInvokeId", "bcfd261a-39b5-4897-832d-37522d337b8a");
        tracePayload.put("childInvokes", List.of());
        tracePayload.put("invokeType", "llm");
        tracePayload.put("name", "OpenAILLM");
        tracePayload.put("elapsedTime", null);
        tracePayload.put("metaData", metadata);

        DmqResponseMessage restored = roundTripResponse(new TraceSchema("tracer_agent", tracePayload));

        assertThat(restored.getBody()).isInstanceOf(TraceSchema.class);
        TraceSchema schema = (TraceSchema) restored.getBody();
        assertThat(schema.getType()).isEqualTo("tracer_agent");
        Map<String, Object> payload = asMap(schema.getPayload());
        assertThat(payload).containsEntry("traceId", "94884432-1558-40d3-aded-b09e1111e171");
        assertThat(payload).containsEntry("invokeType", "llm");
        assertThat(payload).containsEntry("name", "OpenAILLM");

        Map<String, Object> restoredInputs = asMap(payload.get("inputs"));
        List<?> restoredInputItems = (List<?>) restoredInputs.get("inputs");
        assertThat(restoredInputItems).hasSize(1);
        assertThat(asMap(restoredInputItems.get(0)))
                .containsEntry("content", HELLO)
                .containsEntry("role", "user");

        assertThat(payload.get("startTime"))
                .isEqualTo(LocalDateTime.of(2025, 11, 18, 19, 22, 5, 728_961_000));
        assertThat(payload.get("endTime")).isNull();
        assertThat(payload.get("elapsedTime")).isNull();

        assertThat(asMap(payload.get("metaData")))
                .containsEntry("class_name", "OpenAILLM")
                .containsEntry("type", "llm");
        assertThat(payload).containsEntry("invokeId", "ab4f7f56-e69e-460d-8ba2-ed6e2e2f6bb0");
        assertThat(payload).containsEntry("parentInvokeId", "bcfd261a-39b5-4897-832d-37522d337b8a");
        assertThat(payload.get("childInvokes")).isEqualTo(List.of());
        assertThat(payload.get("outputs")).isNull();
        assertThat(payload.get("error")).isNull();
    }

    @Test
    void testSerializeExceedMaxDepth() {
        Object nested = new OutputSchema("x", 0, "final");
        for (int index = 0; index < 11; index++) {
            nested = new WorkflowOutput(nested, WorkflowExecutionState.COMPLETED);
            nested = new OutputSchema("wrap", 0, nested);
        }
        DmqResponseMessage message = response(new WorkflowOutput(nested, WorkflowExecutionState.COMPLETED));

        assertThatThrownBy(() -> MessageSerializer.serializeMessage(message))
                .isInstanceOf(StackOverflowError.class)
                .hasMessageContaining("Payload nested too deep");
    }

    @Test
    void testSerializeLargeListShouldPass() {
        List<OutputSchema> items = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            items.add(new OutputSchema("answer", index, linkedMap("value", index)));
        }

        DmqResponseMessage restored = roundTripResponse(new WorkflowOutput(items, WorkflowExecutionState.COMPLETED));

        assertThat(restored.getBody()).isInstanceOf(WorkflowOutput.class);
        WorkflowOutput workflowOutput = (WorkflowOutput) restored.getBody();
        assertThat(workflowOutput.getResult()).isInstanceOf(List.class);
        List<?> result = (List<?>) workflowOutput.getResult();
        assertThat(result).hasSize(20);
        for (int index = 0; index < 20; index++) {
            OutputSchema item = (OutputSchema) result.get(index);
            assertThat(item.getIndex()).isEqualTo(index);
            assertThat(asMap(item.getPayload())).containsEntry("value", index);
        }
    }

    private static DmqResponseMessage roundTripResponse(Object payload) {
        return (DmqResponseMessage) MessageSerializer.deserializeMessage(MessageSerializer.serializeMessage(response(payload)));
    }

    private static DmqRequestMessage roundTripRequest(DmqRequestMessage message) {
        return (DmqRequestMessage) MessageSerializer.deserializeMessage(MessageSerializer.serializeMessage(message));
    }

    private static DmqResponseMessage response(Object payload) {
        DmqResponseMessage message = new DmqResponseMessage();
        message.setPayload(payload);
        return message;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> linkedMap(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            result.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return result;
    }
}
