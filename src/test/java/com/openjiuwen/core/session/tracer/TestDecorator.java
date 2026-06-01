/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.TraceSchema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for tracer decorators.
 * Mirrors Python's {@code tests/unit_tests/core/session/tracer/test_decorator.py}.
 */
class TestDecorator {

    @Test
    @DisplayName("test_decorate_tool")
    void testDecorateTool() {
        AgentSessionApi session = new AgentSessionApi();
        ToolLike wrappedTool = TracerDecorator.decorateToolWithTrace(new MockTool(), session);

        assertTrue(Proxy.isProxyClass(wrappedTool.getClass()));
        assertEquals(Map.of(), wrappedTool.invoke(Map.of("a", "a"), 3));

        List<TraceAgentSpan> spans = takeTraceSpans(session, 2);
        assertEquals(InvokeType.PLUGIN.getValue(), spans.get(0).getInvokeType());
        assertEquals("test_tool", spans.get(0).getName());
        assertEquals("test_tool", spans.get(0).getMetaData().get("class_name"));
        assertEquals(NodeStatus.FINISH.getValue(), spans.get(1).getStatus());
    }

    @Test
    @DisplayName("test_decorate_workflow")
    void testDecorateWorkflow() {
        AgentSessionApi session = new AgentSessionApi();
        WorkflowLike wrappedWorkflow = TracerDecorator.decorateWorkflowWithTrace(new MockWorkflow(), session);

        assertTrue(Proxy.isProxyClass(wrappedWorkflow.getClass()));
        assertEquals(Map.of("a", "a"), wrappedWorkflow.invoke(Map.of("a", "a"), new Object(), null));

        List<TraceAgentSpan> spans = takeTraceSpans(session, 2);
        assertEquals(InvokeType.WORKFLOW.getValue(), spans.get(0).getInvokeType());
        assertEquals("weather", spans.get(0).getName());
        assertEquals("weather", spans.get(0).getMetaData().get("class_name"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) spans.get(0).getMetaData().get("metadata");
        assertEquals("test_weather_agent", metadata.get("id"));
        assertEquals(NodeStatus.FINISH.getValue(), spans.get(1).getStatus());
    }

    @Test
    @DisplayName("test_decorate_model")
    void testDecorateModel() {
        AgentSessionApi session = new AgentSessionApi();
        ModelLike mockedModel = TracerDecorator.decorateModelWithTrace(new MockModel(), session);

        assertTrue(Proxy.isProxyClass(mockedModel.getClass()));
        List<Map<String, Object>> messages = List.of(baseMessage("aa"));

        Map<String, Object> kwargs = mapWithNulls(
                "model", "a",
                "tools", null,
                "temperature", null,
                "top_p", null,
                "stop", null,
                "max_tokens", null);
        assertEquals(messages, mockedModel.invoke(messages, kwargs));

        List<TraceAgentSpan> invokeSpans = takeTraceSpans(session, 3);
        assertEquals("Qwen/Qwen3-32B", invokeSpans.get(0).getName());
        assertEquals(InvokeType.LLM.getValue(), invokeSpans.get(0).getInvokeType());
        assertLlmParams(invokeSpans.get(1), false, messages);
        assertEquals(NodeStatus.FINISH.getValue(), invokeSpans.get(2).getStatus());

        Iterator<Object> stream = mockedModel.stream(messages, new LinkedHashMap<>(kwargs));
        assertTrue(stream.hasNext());
        assertEquals(messages, stream.next());

        List<TraceAgentSpan> streamSpans = takeTraceSpans(session, 3);
        assertEquals("Qwen/Qwen3-32B", streamSpans.get(0).getName());
        assertLlmParams(streamSpans.get(1), true, messages);
        assertEquals(NodeStatus.FINISH.getValue(), streamSpans.get(2).getStatus());

        assertEquals(messages, mockedModel.invoke(messages));
        List<TraceAgentSpan> positionalSpans = takeTraceSpans(session, 2);
        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) positionalSpans.get(0).getInputs();
        assertEquals(messages, inputs.get("inputs"));
    }

    private static void assertLlmParams(TraceAgentSpan span, boolean stream, List<Map<String, Object>> messages) {
        assertEquals(NodeStatus.RUNNING.getValue(), span.getStatus());
        assertNotNull(span.getOnInvokeData());
        @SuppressWarnings("unchecked")
        Map<String, Object> llmParams = (Map<String, Object>) span.getOnInvokeData().get(0).get("llm_params");
        assertEquals(messages, llmParams.get("messages"));
        assertEquals("a", llmParams.get("model"));
        assertEquals(stream, llmParams.get("stream"));
        assertTrue(llmParams.containsKey("tools"));
        assertTrue(llmParams.containsKey("temperature"));
        assertTrue(llmParams.containsKey("top_p"));
        assertTrue(llmParams.containsKey("stop"));
        assertTrue(llmParams.containsKey("max_tokens"));
    }

    private static List<TraceAgentSpan> takeTraceSpans(AgentSessionApi session, int count) {
        List<TraceAgentSpan> spans = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Object item = session.getInner()
                    .streamWriterManager()
                    .getStreamEmitter()
                    .getStreamQueue()
                    .receive(1_000);
            assertNotNull(item, "expected trace frame " + i);
            TraceSchema schema = assertInstanceOf(TraceSchema.class, item);
            spans.add(assertInstanceOf(TraceAgentSpan.class, schema.getPayload()));
        }
        return spans;
    }

    private static Map<String, Object> baseMessage(String role) {
        return mapWithNulls("role", role, "content", "", "name", null);
    }

    private static Map<String, Object> mapWithNulls(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return result;
    }

    interface ToolLike {
        Object invoke(Map<String, Object> inputs, Object context);
    }

    static final class MockTool implements ToolLike {
        public TestCard getCard() {
            return new TestCard("test_tool", "test_tool", "test tool", "1.0");
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Object context) {
            return Map.of();
        }
    }

    interface WorkflowLike {
        Object invoke(Map<String, Object> inputs, Object session, Object context);
    }

    static final class MockWorkflow implements WorkflowLike {
        public TestCard getCard() {
            return new TestCard("test_weather_agent", "weather", null, "1.0");
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Object session, Object context) {
            return inputs;
        }
    }

    interface ModelLike {
        Object invoke(Object messages, Map<String, Object> kwargs);

        Object invoke(Object messages);

        Iterator<Object> stream(Object messages, Map<String, Object> kwargs);
    }

    static final class MockModel implements ModelLike {
        public MockConfig getConfig() {
            return new MockConfig();
        }

        @Override
        public Object invoke(Object messages, Map<String, Object> kwargs) {
            recordRequest(messages, kwargs, false);
            return messages;
        }

        @Override
        public Object invoke(Object messages) {
            return messages;
        }

        @Override
        public Iterator<Object> stream(Object messages, Map<String, Object> kwargs) {
            recordRequest(messages, kwargs, true);
            return List.of(messages).iterator();
        }

        @SuppressWarnings("unchecked")
        private void recordRequest(Object messages, Map<String, Object> kwargs, boolean stream) {
            Object callback = kwargs.remove("tracer_record_data");
            Map<String, Object> params = mapWithNulls(
                    "messages", messages,
                    "tools", kwargs.get("tools"),
                    "temperature", kwargs.get("temperature"),
                    "top_p", kwargs.get("top_p"),
                    "model", kwargs.get("model"),
                    "stop", kwargs.get("stop"),
                    "max_tokens", kwargs.get("max_tokens"),
                    "stream", stream);
            if (callback instanceof Consumer<?> consumer) {
                ((Consumer<Map<String, Object>>) consumer).accept(Map.of("llm_params", params));
            }
        }
    }

    static final class MockConfig {
        public MockModelConfig getModelConfig() {
            return new MockModelConfig();
        }
    }

    static final class MockModelConfig {
        public String getModelName() {
            return "Qwen/Qwen3-32B";
        }
    }

    static final class TestCard {
        private final String id;
        private final String name;
        private final String description;
        private final String version;

        TestCard(String id, String name, String description, String version) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.version = version;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getVersion() {
            return version;
        }
    }
}
