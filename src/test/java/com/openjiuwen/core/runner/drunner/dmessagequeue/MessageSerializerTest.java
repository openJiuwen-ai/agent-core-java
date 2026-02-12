// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.dmessagequeue;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.stream.CustomSchema;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.TraceSchema;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 MessageSerializer 消息序列化/反序列化。
 * 
 * 对应Python: test_message_serializer.py - TestMessageSerializer
 */
class MessageSerializerTest {

    @Test
    @DisplayName("测试 OutputSchema 序列化反序列化")
    void testOutputSchema() {
        OutputSchema payload = new OutputSchema(
                Constant.INTERACTION,
                0,
                new InteractionOutput("l.2", "Please enter any key")
        );
        DmqResponseMessage msg = DmqResponseMessage.builder()
                .payload(payload)
                .build();

        byte[] b = MessageSerializer.serializeMessage(msg);
        DmqResponseMessage msg2 = (DmqResponseMessage) MessageSerializer.deserializeMessage(b);

        assertInstanceOf(OutputSchema.class, msg2.getPayload());
        OutputSchema os = (OutputSchema) msg2.getPayload();
        assertInstanceOf(InteractionOutput.class, os.payload());
    }

    @Test
    @DisplayName("测试 OutputSchema 列表序列化反序列化")
    void testListOfOutputSchema() {
        List<Object> payload = List.of(
                // Interrupt
                new OutputSchema(Constant.INTERACTION, 0,
                        new InteractionOutput("questioner", "信息")),
                // Regular dict
                new OutputSchema("answer", 0,
                        Map.of("output", "123", "result_type", "answer")),
                // Error dict
                new OutputSchema("workflow_final", 0,
                        Map.of("error", true, "message", "aaa", "status", "failed")),
                // WorkflowOutput
                new OutputSchema("workflow_final", 0,
                        new WorkflowOutput(
                                Map.of("response", "上海", "output", Map.of()),
                                WorkflowExecutionState.COMPLETED
                        ))
        );
        DmqResponseMessage msg = DmqResponseMessage.builder()
                .payload(payload)
                .build();

        byte[] data = MessageSerializer.serializeMessage(msg);
        DmqResponseMessage msg2 = (DmqResponseMessage) MessageSerializer.deserializeMessage(data);

        assertInstanceOf(List.class, msg2.getPayload());
        @SuppressWarnings("unchecked")
        List<Object> resultList = (List<Object>) msg2.getPayload();
        assertEquals(4, resultList.size());

        // First item - OutputSchema with InteractionOutput
        assertInstanceOf(OutputSchema.class, resultList.get(0));
        OutputSchema os0 = (OutputSchema) resultList.get(0);
        assertEquals(Constant.INTERACTION, os0.type());
        assertInstanceOf(InteractionOutput.class, os0.payload());
        InteractionOutput io0 = (InteractionOutput) os0.payload();
        assertEquals("questioner", io0.id());
        assertEquals("信息", io0.value());

        // Second item - OutputSchema with plain dict
        assertInstanceOf(OutputSchema.class, resultList.get(1));
        OutputSchema os1 = (OutputSchema) resultList.get(1);
        assertEquals("answer", os1.type());
        assertInstanceOf(Map.class, os1.payload());
        @SuppressWarnings("unchecked")
        Map<String, Object> map1 = (Map<String, Object>) os1.payload();
        assertEquals("123", map1.get("output"));
        assertEquals("answer", map1.get("result_type"));

        // Third item - OutputSchema with error dict
        assertInstanceOf(OutputSchema.class, resultList.get(2));
        OutputSchema os2 = (OutputSchema) resultList.get(2);
        assertEquals("workflow_final", os2.type());
        assertInstanceOf(Map.class, os2.payload());
        @SuppressWarnings("unchecked")
        Map<String, Object> map2 = (Map<String, Object>) os2.payload();
        assertEquals(true, map2.get("error"));
        assertEquals("aaa", map2.get("message"));
        assertEquals("failed", map2.get("status"));

        // Fourth item - OutputSchema with WorkflowOutput
        assertInstanceOf(OutputSchema.class, resultList.get(3));
        OutputSchema os3 = (OutputSchema) resultList.get(3);
        assertEquals("workflow_final", os3.type());
        assertInstanceOf(WorkflowOutput.class, os3.payload());
        WorkflowOutput wo = (WorkflowOutput) os3.payload();
        assertInstanceOf(Map.class, wo.getResult());
        assertEquals(WorkflowExecutionState.COMPLETED, wo.getState());
    }

    @Test
    @DisplayName("测试 CustomSchema 与 WorkflowOutput")
    void testCustomSchemaWithWorkflowOutput() {
        Map<String, Object> customData = new LinkedHashMap<>();
        customData.put("output", List.of(List.of("aaa")));
        customData.put("result_type", "answer");
        customData.put("aaa", "{\"aaa\":\"123\"}");
        customData.put("work", new WorkflowOutput(
                Map.of("response", "上海", "output", Map.of()),
                WorkflowExecutionState.COMPLETED
        ));
        CustomSchema payload = new CustomSchema(customData);

        DmqResponseMessage msg = DmqResponseMessage.builder()
                .payload(payload)
                .build();

        byte[] data = MessageSerializer.serializeMessage(msg);
        DmqResponseMessage msg2 = (DmqResponseMessage) MessageSerializer.deserializeMessage(data);

        assertInstanceOf(CustomSchema.class, msg2.getPayload());
        CustomSchema cs = (CustomSchema) msg2.getPayload();
        assertInstanceOf(List.class, cs.get("output"));
        @SuppressWarnings("unchecked")
        List<Object> output = (List<Object>) cs.get("output");
        assertInstanceOf(List.class, output.getFirst());
        assertInstanceOf(WorkflowOutput.class, cs.get("work"));
        WorkflowOutput wo = (WorkflowOutput) cs.get("work");
        assertInstanceOf(Map.class, wo.getResult());
        assertEquals(WorkflowExecutionState.COMPLETED, wo.getState());
    }

    @Test
    @DisplayName("测试请求消息中的普通 dict")
    void testPlainDictInRequest() {
        Map<String, Object> payload = Map.of("query", "你好");
        DmqRequestMessage msg = DmqRequestMessage.builder()
                .payload(payload)
                .build();

        byte[] data = MessageSerializer.serializeMessage(msg);
        DmqRequestMessage msg2 = (DmqRequestMessage) MessageSerializer.deserializeMessage(data);

        assertInstanceOf(DmqRequestMessage.class, msg2);
        assertInstanceOf(Map.class, msg2.getPayload());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) msg2.getPayload();
        assertEquals("你好", result.get("query"));
    }

    @Test
    @DisplayName("测试响应消息中的普通 dict")
    void testDictResponse() {
        DmqResponseMessage msg = DmqResponseMessage.builder()
                .payload(Map.of("output", "你好！", "result_type", "answer"))
                .build();

        byte[] data = MessageSerializer.serializeMessage(msg);
        DmqResponseMessage msg2 = (DmqResponseMessage) MessageSerializer.deserializeMessage(data);

        assertInstanceOf(Map.class, msg2.getPayload());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) msg2.getPayload();
        assertEquals("你好！", result.get("output"));
    }

    @Test
    @DisplayName("测试 dict 中嵌入 BaseModel 对象")
    void testDictWithEmbeddedBaseModel() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("output", new WorkflowOutput(
                Map.of("response", "上海", "output", Map.of()),
                WorkflowExecutionState.COMPLETED
        ));
        payload.put("result_type", "answer");

        DmqResponseMessage msg = DmqResponseMessage.builder()
                .payload(payload)
                .build();

        byte[] data = MessageSerializer.serializeMessage(msg);
        DmqResponseMessage msg2 = (DmqResponseMessage) MessageSerializer.deserializeMessage(data);

        assertInstanceOf(Map.class, msg2.getPayload());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) msg2.getPayload();
        assertInstanceOf(WorkflowOutput.class, result.get("output"));
    }

    @Test
    @DisplayName("测试 WorkflowOutput 序列化反序列化")
    void testWorkflowOutput() {
        DmqResponseMessage msg = DmqResponseMessage.builder()
                .payload(new WorkflowOutput(
                        Map.of("response", "上海", "output", Map.of()),
                        WorkflowExecutionState.COMPLETED
                ))
                .build();

        byte[] data = MessageSerializer.serializeMessage(msg);
        DmqResponseMessage msg2 = (DmqResponseMessage) MessageSerializer.deserializeMessage(data);

        assertInstanceOf(WorkflowOutput.class, msg2.getPayload());
        WorkflowOutput wo = (WorkflowOutput) msg2.getPayload();
        assertInstanceOf(Map.class, wo.getResult());
        assertEquals(WorkflowExecutionState.COMPLETED, wo.getState());
    }

    @Test
    @DisplayName("测试 OutputSchema 嵌套在 WorkflowOutput 中")
    void testOutputSchemaInWorkflowOutput() {
        List<Object> listOutputSchema = List.of(
                // Interrupt
                new OutputSchema(Constant.INTERACTION, 0,
                        new InteractionOutput("questioner", "信息")),
                // Regular dict
                new OutputSchema("answer", 0,
                        Map.of("output", "123", "result_type", "answer")),
                // WorkflowOutput with nested OutputSchema
                new OutputSchema("workflow_final", 0,
                        new WorkflowOutput(
                                new OutputSchema("answer", 0,
                                        Map.of("output", "456", "result_type", "answer")),
                                WorkflowExecutionState.COMPLETED
                        ))
        );

        DmqResponseMessage msg = DmqResponseMessage.builder()
                .payload(new WorkflowOutput(
                        listOutputSchema,
                        WorkflowExecutionState.COMPLETED
                ))
                .build();

        byte[] data = MessageSerializer.serializeMessage(msg);
        DmqResponseMessage msg2 = (DmqResponseMessage) MessageSerializer.deserializeMessage(data);

        assertInstanceOf(WorkflowOutput.class, msg2.getPayload());
        WorkflowOutput wo = (WorkflowOutput) msg2.getPayload();
        assertInstanceOf(List.class, wo.getResult());
        @SuppressWarnings("unchecked")
        List<Object> resultList = (List<Object>) wo.getResult();
        assertEquals(3, resultList.size());
        assertEquals(WorkflowExecutionState.COMPLETED, wo.getState());

        // First item
        assertInstanceOf(OutputSchema.class, resultList.get(0));
        OutputSchema os0 = (OutputSchema) resultList.get(0);
        assertEquals(Constant.INTERACTION, os0.type());
        assertInstanceOf(InteractionOutput.class, os0.payload());
        assertEquals("questioner", ((InteractionOutput) os0.payload()).id());
        assertEquals("信息", ((InteractionOutput) os0.payload()).value());

        // Second item
        assertInstanceOf(OutputSchema.class, resultList.get(1));
        OutputSchema os1 = (OutputSchema) resultList.get(1);
        assertEquals("answer", os1.type());

        // Third item - nested OutputSchema within WorkflowOutput
        assertInstanceOf(OutputSchema.class, resultList.get(2));
        OutputSchema os2 = (OutputSchema) resultList.get(2);
        assertEquals("workflow_final", os2.type());
        assertInstanceOf(WorkflowOutput.class, os2.payload());
        WorkflowOutput innerWo = (WorkflowOutput) os2.payload();
        assertEquals(WorkflowExecutionState.COMPLETED, innerWo.getState());
        assertInstanceOf(OutputSchema.class, innerWo.getResult());
        OutputSchema innerOs = (OutputSchema) innerWo.getResult();
        assertEquals("answer", innerOs.type());
        assertInstanceOf(Map.class, innerOs.payload());
        @SuppressWarnings("unchecked")
        Map<String, Object> innerMap = (Map<String, Object>) innerOs.payload();
        assertEquals("456", innerMap.get("output"));
        assertEquals("answer", innerMap.get("result_type"));
    }

    @Test
    @DisplayName("测试 TraceSchema 序列化反序列化")
    void testTraceSchemaPayload() {
        Map<String, Object> tracePayload = new LinkedHashMap<>();
        tracePayload.put("traceId", "94884432-1558-40d3-aded-b09e1111e171");
        tracePayload.put("startTime", LocalDateTime.of(2025, 11, 18, 19, 22, 5, 728961000));
        tracePayload.put("endTime", null);
        tracePayload.put("inputs", Map.of("inputs", List.of(
                Map.of("role", "user", "content", "你好", "name", "")
        )));
        tracePayload.put("outputs", null);
        tracePayload.put("error", null);
        tracePayload.put("invokeId", "ab4f7f56-e69e-460d-8ba2-ed6e2e2f6bb0");
        tracePayload.put("parentInvokeId", "bcfd261a-39b5-4897-832d-37522d337b8a");
        tracePayload.put("childInvokes", List.of());
        tracePayload.put("invokeType", "llm");
        tracePayload.put("name", "OpenAILLM");
        tracePayload.put("elapsedTime", null);
        tracePayload.put("metaData", Map.of("class_name", "OpenAILLM", "type", "llm"));

        TraceSchema payload = new TraceSchema("tracer_agent", tracePayload);
        DmqResponseMessage msg = DmqResponseMessage.builder()
                .payload(payload)
                .build();

        byte[] data = MessageSerializer.serializeMessage(msg);
        DmqResponseMessage msg2 = (DmqResponseMessage) MessageSerializer.deserializeMessage(data);

        assertInstanceOf(TraceSchema.class, msg2.getPayload());
        TraceSchema ts = (TraceSchema) msg2.getPayload();
        assertEquals("tracer_agent", ts.type());
        assertInstanceOf(Map.class, ts.payload());
        @SuppressWarnings("unchecked")
        Map<String, Object> tsPayload = (Map<String, Object>) ts.payload();
        assertEquals("94884432-1558-40d3-aded-b09e1111e171", tsPayload.get("traceId"));
        assertEquals("llm", tsPayload.get("invokeType"));
        assertEquals("OpenAILLM", tsPayload.get("name"));

        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) tsPayload.get("inputs");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> inputsList = (List<Map<String, Object>>) inputs.get("inputs");
        assertEquals(1, inputsList.size());
        assertEquals("你好", inputsList.getFirst().get("content"));
        assertEquals("user", inputsList.getFirst().get("role"));

        // Time consistency
        LocalDateTime expectedStartTime = LocalDateTime.of(2025, 11, 18, 19, 22, 5, 728961000);
        assertEquals(expectedStartTime, tsPayload.get("startTime"));
        assertNull(tsPayload.get("endTime"));
        assertNull(tsPayload.get("elapsedTime"));

        // Metadata
        @SuppressWarnings("unchecked")
        Map<String, Object> metaData = (Map<String, Object>) tsPayload.get("metaData");
        assertEquals("OpenAILLM", metaData.get("class_name"));
        assertEquals("llm", metaData.get("type"));
        assertEquals("ab4f7f56-e69e-460d-8ba2-ed6e2e2f6bb0", tsPayload.get("invokeId"));
        assertEquals("bcfd261a-39b5-4897-832d-37522d337b8a", tsPayload.get("parentInvokeId"));
        assertInstanceOf(List.class, tsPayload.get("childInvokes"));
        assertNull(tsPayload.get("outputs"));
        assertNull(tsPayload.get("error"));
    }

    @Test
    @DisplayName("测试超过最大深度限制时抛出异常")
    void testSerializeExceedMaxDepth() {
        // Build deeply nested structure (> 10 levels)
        Object nested = new OutputSchema("x", 0, "final");
        for (int i = 0; i < 11; i++) {
            nested = new WorkflowOutput(nested, WorkflowExecutionState.COMPLETED);
            nested = new OutputSchema("wrap", 0, nested);
        }

        DmqResponseMessage msg = DmqResponseMessage.builder()
                .payload(new WorkflowOutput(nested, WorkflowExecutionState.COMPLETED))
                .build();

        final DmqResponseMessage finalMsg = msg;
        assertThrows(StackOverflowError.class, () -> MessageSerializer.serializeMessage(finalMsg));
    }

    @Test
    @DisplayName("测试大列表不触发深度限制")
    void testSerializeLargeListShouldPass() {
        List<Object> items = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            items.add(new OutputSchema("answer", i, Map.of("value", i)));
        }

        DmqResponseMessage msg = DmqResponseMessage.builder()
                .payload(new WorkflowOutput(items, WorkflowExecutionState.COMPLETED))
                .build();

        byte[] data = MessageSerializer.serializeMessage(msg);
        DmqResponseMessage msg2 = (DmqResponseMessage) MessageSerializer.deserializeMessage(data);

        assertInstanceOf(WorkflowOutput.class, msg2.getPayload());
        WorkflowOutput wo = (WorkflowOutput) msg2.getPayload();
        assertInstanceOf(List.class, wo.getResult());
        @SuppressWarnings("unchecked")
        List<Object> resultList = (List<Object>) wo.getResult();
        assertEquals(20, resultList.size());

        for (int i = 0; i < 20; i++) {
            OutputSchema item = (OutputSchema) resultList.get(i);
            assertInstanceOf(OutputSchema.class, item);
            assertEquals(i, item.index());
            assertInstanceOf(Map.class, item.payload());
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) item.payload();
            assertEquals(i, map.get("value"));
        }
    }
}

