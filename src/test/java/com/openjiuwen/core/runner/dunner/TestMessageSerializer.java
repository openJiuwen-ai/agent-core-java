/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.runner.dunner;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.runner.drunner.dmessage_queue.MessageSerializer;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqRequestMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.stream.CustomSchema;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.TraceSchema;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MessageSerializer.
 * Mirrors Python's tests/unit_tests/core/runner/dunner/test_message_serializer.py
 */
class TestMessageSerializer {

    @BeforeAll
    static void registerTypes() {
        MessageSerializer.registerType("OutputSchema", fields -> OutputSchema.fromMap(fields));
        MessageSerializer.registerType("InteractionOutput", fields -> {
            String id = (String) fields.get("id");
            Object value = fields.get("value");
            return new InteractionOutput(id, value);
        });
        MessageSerializer.registerType("WorkflowOutput", fields -> {
            Object result = fields.get("result");
            Object stateObj = fields.get("state");
            WorkflowExecutionState state = null;
            if (stateObj instanceof String) {
                state = WorkflowExecutionState.valueOf((String) stateObj);
            } else if (stateObj instanceof WorkflowExecutionState) {
                state = (WorkflowExecutionState) stateObj;
            }
            return new WorkflowOutput(result, state);
        });
        MessageSerializer.registerType("CustomSchema", fields -> CustomSchema.fromMap(fields));
        MessageSerializer.registerType("TraceSchema", fields -> TraceSchema.fromMap(fields));
    }

    @Nested
    @DisplayName("MessageSerializer tests")
    class SerializerTests {

        @Test
        @DisplayName("test output schema")
        void testOutputSchema() throws Exception {
            InteractionOutput interactionOutput = new InteractionOutput("l.2", "Please enter any key");
            OutputSchema payload = new OutputSchema(Constant.INTERACTION, 0, interactionOutput);
            DmqResponseMessage msg = new DmqResponseMessage();
            msg.setBody(payload);

            byte[] data = MessageSerializer.serializeMessage(msg);
            DmqMessage msg2 = MessageSerializer.deserializeMessage(data);

            assertTrue(msg2 instanceof DmqResponseMessage);
            Object body = msg2.getBody();
            assertTrue(body instanceof OutputSchema);
            OutputSchema outputSchema = (OutputSchema) body;
            assertEquals(Constant.INTERACTION, outputSchema.getType());
            assertTrue(outputSchema.getPayload() instanceof InteractionOutput);
        }

        @Test
        @DisplayName("test list of output schema")
        void testListOfOutputSchema() throws Exception {
            List<OutputSchema> payload = new ArrayList<>();
            payload.add(new OutputSchema(Constant.INTERACTION, 0,
                    new InteractionOutput("questioner", "信息")));
            Map<String, Object> dictPayload1 = new LinkedHashMap<>();
            dictPayload1.put("output", "123");
            dictPayload1.put("result_type", "answer");
            payload.add(new OutputSchema("answer", 0, dictPayload1));
            Map<String, Object> dictPayload2 = new LinkedHashMap<>();
            dictPayload2.put("error", true);
            dictPayload2.put("message", "aaa");
            dictPayload2.put("status", "failed");
            payload.add(new OutputSchema("workflow_final", 0, dictPayload2));
            payload.add(new OutputSchema("workflow_final", 0,
                    new WorkflowOutput(Map.of("response", "上海", "output", Map.of()),
                            WorkflowExecutionState.COMPLETED)));

            DmqResponseMessage msg = new DmqResponseMessage();
            msg.setBody(payload);

            byte[] data = MessageSerializer.serializeMessage(msg);
            DmqMessage msg2 = MessageSerializer.deserializeMessage(data);

            assertTrue(msg2 instanceof DmqResponseMessage);
            Object body = msg2.getBody();
            assertTrue(body instanceof List);
            List<?> list = (List<?>) body;
            assertEquals(4, list.size());

            OutputSchema item0 = (OutputSchema) list.get(0);
            assertEquals(Constant.INTERACTION, item0.getType());
            assertTrue(item0.getPayload() instanceof InteractionOutput);
            InteractionOutput interactionOutput0 = (InteractionOutput) item0.getPayload();
            assertEquals("questioner", interactionOutput0.getId());
            assertEquals("信息", interactionOutput0.getValue());

            OutputSchema item1 = (OutputSchema) list.get(1);
            assertEquals("answer", item1.getType());
            assertTrue(item1.getPayload() instanceof Map);
            Map<?, ?> dict1 = (Map<?, ?>) item1.getPayload();
            assertEquals("123", dict1.get("output"));
            assertEquals("answer", dict1.get("result_type"));

            OutputSchema item2 = (OutputSchema) list.get(2);
            assertEquals("workflow_final", item2.getType());
            assertTrue(item2.getPayload() instanceof Map);
            Map<?, ?> dict2 = (Map<?, ?>) item2.getPayload();
            assertEquals(true, dict2.get("error"));
            assertEquals("aaa", dict2.get("message"));
            assertEquals("failed", dict2.get("status"));

            OutputSchema item3 = (OutputSchema) list.get(3);
            assertEquals("workflow_final", item3.getType());
            assertTrue(item3.getPayload() instanceof WorkflowOutput);
            WorkflowOutput workflowOutput3 = (WorkflowOutput) item3.getPayload();
            assertTrue(workflowOutput3.getResult() instanceof Map);
            Map<?, ?> resultMap = (Map<?, ?>) workflowOutput3.getResult();
            assertEquals("上海", resultMap.get("response"));
            assertTrue(resultMap.get("output") instanceof Map);
            assertEquals(WorkflowExecutionState.COMPLETED, workflowOutput3.getState());
        }

        @Test
        @DisplayName("test customschema with workflow output")
        void testCustomschemaWithWorkflowOutput() throws Exception {
            Map<String, Object> properties = new HashMap<>();
            properties.put("output", List.of(List.of("aaa")));
            properties.put("result_type", "answer");
            properties.put("aaa", "{\"aaa\":\"123\"}");
            properties.put("work", new WorkflowOutput(
                    Map.of("response", "上海", "output", Map.of()),
                    WorkflowExecutionState.COMPLETED));
            CustomSchema payload = new CustomSchema(properties);

            DmqResponseMessage msg = new DmqResponseMessage();
            msg.setBody(payload);

            byte[] data = MessageSerializer.serializeMessage(msg);
            DmqMessage msg2 = MessageSerializer.deserializeMessage(data);

            assertTrue(msg2 instanceof DmqResponseMessage);
            Object body = msg2.getBody();
            assertTrue(body instanceof CustomSchema);
            CustomSchema customSchema = (CustomSchema) body;
            assertTrue(customSchema.get("output") instanceof List);
            List<?> outputList = (List<?>) customSchema.get("output");
            assertTrue(outputList.get(0) instanceof List);
            assertTrue(customSchema.get("work") instanceof WorkflowOutput);
            WorkflowOutput workOutput = (WorkflowOutput) customSchema.get("work");
            assertTrue(workOutput.getResult() instanceof Map);
            Map<?, ?> resultMap = (Map<?, ?>) workOutput.getResult();
            assertEquals("上海", resultMap.get("response"));
            assertTrue(resultMap.get("output") instanceof Map);
            assertEquals(WorkflowExecutionState.COMPLETED, workOutput.getState());
        }

        @Test
        @DisplayName("test plain dict in request")
        void testPlainDictInRequest() throws Exception {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("query", "你好");

            DmqRequestMessage msg = new DmqRequestMessage();
            msg.setBody(payload);

            byte[] data = MessageSerializer.serializeMessage(msg);
            DmqMessage msg2 = MessageSerializer.deserializeMessage(data);

            assertTrue(msg2 instanceof DmqRequestMessage);
            Object body = msg2.getBody();
            assertTrue(body instanceof Map);
            Map<?, ?> dict = (Map<?, ?>) body;
            assertEquals("你好", dict.get("query"));
        }

        @Test
        @DisplayName("test dict response")
        void testDictResponse() throws Exception {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("output", "你好！");
            payload.put("result_type", "answer");

            DmqResponseMessage msg = new DmqResponseMessage();
            msg.setBody(payload);

            byte[] data = MessageSerializer.serializeMessage(msg);
            DmqMessage msg2 = MessageSerializer.deserializeMessage(data);

            assertTrue(msg2 instanceof DmqResponseMessage);
            Object body = msg2.getBody();
            assertTrue(body instanceof Map);
            Map<?, ?> dict = (Map<?, ?>) body;
            assertEquals("你好！", dict.get("output"));
        }

        @Test
        @DisplayName("test dict with embedded basemodel")
        void testDictWithEmbeddedBasemodel() throws Exception {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("output", new WorkflowOutput(
                    Map.of("response", "上海", "output", Map.of()),
                    WorkflowExecutionState.COMPLETED));
            payload.put("result_type", "answer");

            DmqResponseMessage msg = new DmqResponseMessage();
            msg.setBody(payload);

            byte[] data = MessageSerializer.serializeMessage(msg);
            DmqMessage msg2 = MessageSerializer.deserializeMessage(data);

            assertTrue(msg2 instanceof DmqResponseMessage);
            Object body = msg2.getBody();
            assertTrue(body instanceof Map);
            Map<?, ?> dict = (Map<?, ?>) body;
            assertTrue(dict.get("output") instanceof WorkflowOutput);
        }

        @Test
        @DisplayName("test workflow output")
        void testWorkflowOutput() throws Exception {
            WorkflowOutput payload = new WorkflowOutput(
                    Map.of("response", "上海", "output", Map.of()),
                    WorkflowExecutionState.COMPLETED);

            DmqResponseMessage msg = new DmqResponseMessage();
            msg.setBody(payload);

            byte[] data = MessageSerializer.serializeMessage(msg);
            DmqMessage msg2 = MessageSerializer.deserializeMessage(data);

            assertTrue(msg2 instanceof DmqResponseMessage);
            Object body = msg2.getBody();
            assertTrue(body instanceof WorkflowOutput);
            WorkflowOutput workflowOutput = (WorkflowOutput) body;
            assertTrue(workflowOutput.getResult() instanceof Map);
            Map<?, ?> resultMap = (Map<?, ?>) workflowOutput.getResult();
            assertEquals("上海", resultMap.get("response"));
            assertTrue(resultMap.get("output") instanceof Map);
            assertEquals(WorkflowExecutionState.COMPLETED, workflowOutput.getState());
        }

        @Test
        @DisplayName("test output schema in workflow output")
        void testOutputSchemaInWorkflowOutput() throws Exception {
            List<OutputSchema> listOutputSchema = new ArrayList<>();
            listOutputSchema.add(new OutputSchema(Constant.INTERACTION, 0,
                    new InteractionOutput("questioner", "信息")));
            Map<String, Object> dictPayload1 = new LinkedHashMap<>();
            dictPayload1.put("output", "123");
            dictPayload1.put("result_type", "answer");
            listOutputSchema.add(new OutputSchema("answer", 0, dictPayload1));
            Map<String, Object> dictPayload2 = new LinkedHashMap<>();
            dictPayload2.put("output", "456");
            dictPayload2.put("result_type", "answer");
            listOutputSchema.add(new OutputSchema("workflow_final", 0,
                    new WorkflowOutput(
                            new OutputSchema("answer", 0, dictPayload2),
                            WorkflowExecutionState.COMPLETED)));

            WorkflowOutput payload = new WorkflowOutput(listOutputSchema, WorkflowExecutionState.COMPLETED);

            DmqResponseMessage msg = new DmqResponseMessage();
            msg.setBody(payload);

            byte[] data = MessageSerializer.serializeMessage(msg);
            DmqMessage msg2 = MessageSerializer.deserializeMessage(data);

            assertTrue(msg2 instanceof DmqResponseMessage);
            Object body = msg2.getBody();
            assertTrue(body instanceof WorkflowOutput);
            WorkflowOutput workflowOutput = (WorkflowOutput) body;
            assertTrue(workflowOutput.getResult() instanceof List);
            List<?> result = (List<?>) workflowOutput.getResult();
            assertEquals(3, result.size());
            assertEquals(WorkflowExecutionState.COMPLETED, workflowOutput.getState());

            OutputSchema item0 = (OutputSchema) result.get(0);
            assertEquals(Constant.INTERACTION, item0.getType());
            assertTrue(item0.getPayload() instanceof InteractionOutput);
            InteractionOutput interactionOutput0 = (InteractionOutput) item0.getPayload();
            assertEquals("questioner", interactionOutput0.getId());
            assertEquals("信息", interactionOutput0.getValue());

            OutputSchema item1 = (OutputSchema) result.get(1);
            assertEquals("answer", item1.getType());
            assertTrue(item1.getPayload() instanceof Map);
            Map<?, ?> dict1 = (Map<?, ?>) item1.getPayload();
            assertEquals("123", dict1.get("output"));
            assertEquals("answer", dict1.get("result_type"));

            OutputSchema item2 = (OutputSchema) result.get(2);
            assertEquals("workflow_final", item2.getType());
            assertTrue(item2.getPayload() instanceof WorkflowOutput);
            WorkflowOutput nestedWorkflowOutput = (WorkflowOutput) item2.getPayload();
            assertEquals(WorkflowExecutionState.COMPLETED, nestedWorkflowOutput.getState());
            assertTrue(nestedWorkflowOutput.getResult() instanceof OutputSchema);
            OutputSchema nestedOutputSchema = (OutputSchema) nestedWorkflowOutput.getResult();
            assertEquals("answer", nestedOutputSchema.getType());
            assertTrue(nestedOutputSchema.getPayload() instanceof Map);
            Map<?, ?> nestedDict = (Map<?, ?>) nestedOutputSchema.getPayload();
            assertEquals("456", nestedDict.get("output"));
            assertEquals("answer", nestedDict.get("result_type"));
        }

        @Test
        @DisplayName("test trace schema payload")
        void testTraceSchemaPayload() throws Exception {
            LocalDateTime startTime = LocalDateTime.of(2025, 11, 18, 19, 22, 5, 728961000);

            Map<String, Object> inputsInner = new LinkedHashMap<>();
            List<Map<String, Object>> inputsList = new ArrayList<>();
            Map<String, Object> inputItem = new LinkedHashMap<>();
            inputItem.put("role", "user");
            inputItem.put("content", "你好");
            inputItem.put("name", null);
            inputsList.add(inputItem);
            inputsInner.put("inputs", inputsList);

            Map<String, Object> metaData = new LinkedHashMap<>();
            metaData.put("class_name", "OpenAILLM");
            metaData.put("type", "llm");

            Map<String, Object> payloadData = new LinkedHashMap<>();
            payloadData.put("traceId", "94884432-1558-40d3-aded-b09e1111e171");
            payloadData.put("startTime", startTime);
            payloadData.put("endTime", null);
            payloadData.put("inputs", inputsInner);
            payloadData.put("outputs", null);
            payloadData.put("error", null);
            payloadData.put("invokeId", "ab4f7f56-e69e-460d-8ba2-ed6e2e2f6bb0");
            payloadData.put("parentInvokeId", "bcfd261a-39b5-4897-832d-37522d337b8a");
            payloadData.put("childInvokes", new ArrayList<>());
            payloadData.put("invokeType", "llm");
            payloadData.put("name", "OpenAILLM");
            payloadData.put("elapsedTime", null);
            payloadData.put("metaData", metaData);

            TraceSchema payload = new TraceSchema("tracer_agent", payloadData);

            DmqResponseMessage msg = new DmqResponseMessage();
            msg.setBody(payload);

            byte[] data = MessageSerializer.serializeMessage(msg);
            DmqMessage msg2 = MessageSerializer.deserializeMessage(data);

            assertTrue(msg2 instanceof DmqResponseMessage);
            Object body = msg2.getBody();
            assertTrue(body instanceof TraceSchema);
            TraceSchema traceSchema = (TraceSchema) body;
            assertEquals("tracer_agent", traceSchema.getType());
            assertTrue(traceSchema.getPayload() instanceof Map);
            Map<?, ?> payloadMap = (Map<?, ?>) traceSchema.getPayload();
            assertEquals("94884432-1558-40d3-aded-b09e1111e171", payloadMap.get("traceId"));
            assertEquals("llm", payloadMap.get("invokeType"));
            assertEquals("OpenAILLM", payloadMap.get("name"));

            assertTrue(payloadMap.get("inputs") instanceof Map);
            Map<?, ?> inputs = (Map<?, ?>) payloadMap.get("inputs");
            assertTrue(inputs.get("inputs") instanceof List);
            List<?> inputsListResult = (List<?>) inputs.get("inputs");
            assertEquals(1, inputsListResult.size());
            assertTrue(inputsListResult.get(0) instanceof Map);
            Map<?, ?> inputItemResult = (Map<?, ?>) inputsListResult.get(0);
            assertEquals("你好", inputItemResult.get("content"));
            assertEquals("user", inputItemResult.get("role"));

            assertTrue(payloadMap.get("startTime") instanceof LocalDateTime
                    || payloadMap.get("startTime") instanceof OffsetDateTime);
            assertEquals(startTime, payloadMap.get("startTime"));
            assertNull(payloadMap.get("endTime"));
            assertNull(payloadMap.get("elapsedTime"));

            assertTrue(payloadMap.get("metaData") instanceof Map);
            Map<?, ?> metaDataResult = (Map<?, ?>) payloadMap.get("metaData");
            assertEquals("OpenAILLM", metaDataResult.get("class_name"));
            assertEquals("llm", metaDataResult.get("type"));

            assertEquals("ab4f7f56-e69e-460d-8ba2-ed6e2e2f6bb0", payloadMap.get("invokeId"));
            assertEquals("bcfd261a-39b5-4897-832d-37522d337b8a", payloadMap.get("parentInvokeId"));
            assertTrue(payloadMap.get("childInvokes") instanceof List);
            List<?> childInvokes = (List<?>) payloadMap.get("childInvokes");
            assertEquals(0, childInvokes.size());
            assertNull(payloadMap.get("outputs"));
            assertNull(payloadMap.get("error"));
        }

        @Test
        @DisplayName("test serialize exceed max depth")
        void testSerializeExceedMaxDepth() {
            Object nested = new OutputSchema("x", 0, "final");

            for (int i = 0; i < 11; i++) {
                nested = new WorkflowOutput(nested, WorkflowExecutionState.COMPLETED);
                nested = new OutputSchema("wrap", 0, nested);
            }

            WorkflowOutput payload = new WorkflowOutput(nested, WorkflowExecutionState.COMPLETED);
            DmqResponseMessage msg = new DmqResponseMessage();
            msg.setBody(payload);

            assertThrows(StackOverflowError.class, () -> MessageSerializer.serializeMessage(msg));
        }

        @Test
        @DisplayName("test serialize large list should pass")
        void testSerializeLargeListShouldPass() throws Exception {
            List<OutputSchema> items = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                Map<String, Object> payloadDict = new LinkedHashMap<>();
                payloadDict.put("value", i);
                items.add(new OutputSchema("answer", i, payloadDict));
            }

            WorkflowOutput payload = new WorkflowOutput(items, WorkflowExecutionState.COMPLETED);
            DmqResponseMessage msg = new DmqResponseMessage();
            msg.setBody(payload);

            byte[] data = MessageSerializer.serializeMessage(msg);
            DmqMessage msg2 = MessageSerializer.deserializeMessage(data);

            assertTrue(msg2 instanceof DmqResponseMessage);
            Object body = msg2.getBody();
            assertTrue(body instanceof WorkflowOutput);
            WorkflowOutput workflowOutput = (WorkflowOutput) body;
            assertTrue(workflowOutput.getResult() instanceof List);
            List<?> result = (List<?>) workflowOutput.getResult();
            assertEquals(20, result.size());

            for (int i = 0; i < 20; i++) {
                OutputSchema item = (OutputSchema) result.get(i);
                assertEquals(i, item.getIndex());
                assertTrue(item.getPayload() instanceof Map);
                Map<?, ?> payloadMap = (Map<?, ?>) item.getPayload();
                assertEquals(i, payloadMap.get("value"));
            }
        }
    }
}