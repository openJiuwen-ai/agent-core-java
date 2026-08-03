/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's tracer decorator helpers in
 * {@code openjiuwen/core/session/tracer/decorator.py}.
 *
 * <p>Mirrors Python's {@code TestDecator} in
 * {@code tests/unit_tests/core/session/tracer/test_decorator.py}.</p>
 */
class TracerDecoratorTest {

    @Test
    void decorateToolWithTraceSupportsWrappedInnerSession() {
        FakeTracer tracer = new FakeTracer();
        ToolApi decorated = TracerDecorator.decorateToolWithTrace(new ToolImpl(), new WrappedSession(tracer));

        assertTrue(Proxy.isProxyClass(decorated.getClass()));
        assertEquals("ok:ping", decorated.invoke("ping"));
        assertEquals(2, tracer.events.size());
        assertEquals("on_plugin_start", tracer.events.get(0).eventName());
        @SuppressWarnings("unchecked")
        Map<String, Object> instanceInfo = (Map<String, Object>) tracer.events.get(0).payload().get("instance_info");
        assertEquals("test_tool", instanceInfo.get("class_name"));
        assertEquals("on_plugin_end", tracer.events.get(1).eventName());
    }

    @Test
    void decorateWorkflowWithTraceCapturesMetadataAndStreamOutputs() {
        FakeTracer tracer = new FakeTracer();
        WorkflowApi decorated = TracerDecorator.decorateWorkflowWithTrace(new WorkflowImpl(), new DirectSession(tracer));

        Iterator<String> iterator = decorated.stream("weather");
        List<String> outputs = new ArrayList<>();
        while (iterator.hasNext()) {
            outputs.add(iterator.next());
        }

        assertEquals(List.of("weather", "done"), outputs);
        assertEquals(2, tracer.events.size());
        assertEquals("on_workflow_start", tracer.events.get(0).eventName());
        @SuppressWarnings("unchecked")
        Map<String, Object> workflowInfo = (Map<String, Object>) tracer.events.get(0).payload().get("instance_info");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) workflowInfo.get("metadata");
        assertEquals("test_weather_agent", metadata.get("id"));
        assertEquals("weather", workflowInfo.get("class_name"));
        assertEquals("on_workflow_end", tracer.events.get(1).eventName());
    }

    @Test
    void decorateModelWithTraceInjectsTracerRecordCallback() {
        FakeTracer tracer = new FakeTracer();
        ModelApi decorated = TracerDecorator.decorateModelWithTrace(new ModelImpl(), new WrappedSession(tracer));

        String result = decorated.invoke("hello", new LinkedHashMap<>(Map.of("model", "qwen")));

        assertEquals("hello", result);
        assertEquals(3, tracer.events.size());
        assertEquals("on_llm_start", tracer.events.get(0).eventName());
        @SuppressWarnings("unchecked")
        Map<String, Object> modelInfo = (Map<String, Object>) tracer.events.get(0).payload().get("instance_info");
        assertEquals("demo-model", modelInfo.get("class_name"));
        assertEquals("on_llm_request", tracer.events.get(1).eventName());
        assertEquals(Map.of("messages", "hello", "model", "qwen"), tracer.events.get(1).payload().get("llm_params"));
        assertEquals("on_llm_end", tracer.events.get(2).eventName());
    }

    private record EventRecord(String handlerName, String eventName, Map<String, Object> payload) {
    }

    private static final class FakeTracer {
        private final List<EventRecord> events = new ArrayList<>();
        private final FakeSpanManager tracerAgentSpanManager = new FakeSpanManager();

        public FakeSpanManager getTracerAgentSpanManager() {
            return tracerAgentSpanManager;
        }

        public void trigger(String handlerName, String eventName, Map<String, Object> payload) {
            events.add(new EventRecord(handlerName, eventName, payload));
        }
    }

    private static final class FakeSpanManager {
        private final AtomicInteger counter = new AtomicInteger();

        public TraceAgentSpan createAgentSpan(Object parentSpan) {
            String parentInvokeId = parentSpan instanceof Span span ? span.getInvokeId() : null;
            return new TraceAgentSpan("trace-1", "invoke-" + counter.incrementAndGet(), parentInvokeId);
        }
    }

    private static final class WrappedSession {
        private final InnerSession _inner;

        private WrappedSession(FakeTracer tracer) {
            this._inner = new InnerSession(tracer);
        }
    }

    private static final class DirectSession extends InnerSession {
        private DirectSession(FakeTracer tracer) {
            super(tracer);
        }
    }

    private static class InnerSession {
        private final FakeTracer tracer;
        private final TraceAgentSpan span = new TraceAgentSpan("trace-1", "parent", null);

        private InnerSession(FakeTracer tracer) {
            this.tracer = tracer;
        }

        public FakeTracer tracer() {
            return tracer;
        }

        public TraceAgentSpan span() {
            return span;
        }
    }

    private interface ToolApi {
        String invoke(String input);

        ToolCard getCard();
    }

    private static final class ToolImpl implements ToolApi {
        @Override
        public String invoke(String input) {
            return "ok:" + input;
        }

        @Override
        public ToolCard getCard() {
            return new ToolCard("test_tool", "demo");
        }
    }

    private record ToolCard(String name, String description) {
        public String getName() {
            return name;
        }
    }

    private interface WorkflowApi {
        Iterator<String> stream(String input);

        WorkflowCard getCard();
    }

    private static final class WorkflowImpl implements WorkflowApi {
        @Override
        public Iterator<String> stream(String input) {
            return List.of(input, "done").iterator();
        }

        @Override
        public WorkflowCard getCard() {
            return new WorkflowCard("weather", "test_weather_agent", "1.0", "demo workflow");
        }
    }

    private record WorkflowCard(String name, String id, String version, String description) {
        public String getName() {
            return name;
        }

        public String getId() {
            return id;
        }

        public String getVersion() {
            return version;
        }

        public String getDescription() {
            return description;
        }
    }

    private interface ModelApi {
        String invoke(String messages, Map<String, Object> kwargs);

        Iterator<String> stream(String messages, Map<String, Object> kwargs);

        ModelConfigHolder getConfig();
    }

    private static final class ModelImpl implements ModelApi {
        private final ModelConfigHolder config = new ModelConfigHolder();

        @Override
        public String invoke(String messages, Map<String, Object> kwargs) {
            Object callback = kwargs.get("tracer_record_data");
            assertInstanceOf(Consumer.class, callback);
            @SuppressWarnings("unchecked")
            Consumer<Map<String, Object>> tracerRecordData = (Consumer<Map<String, Object>>) callback;
            tracerRecordData.accept(Map.of("llm_params", Map.of("messages", messages, "model", kwargs.get("model"))));
            return messages;
        }

        @Override
        public Iterator<String> stream(String messages, Map<String, Object> kwargs) {
            return List.of(messages).iterator();
        }

        @Override
        public ModelConfigHolder getConfig() {
            return config;
        }
    }

    private static final class ModelConfigHolder {
        private final ModelMetadata modelConfig = new ModelMetadata();

        public ModelMetadata getModelConfig() {
            return modelConfig;
        }
    }

    private static final class ModelMetadata {
        public String getModelName() {
            return "demo-model";
        }
    }
}
