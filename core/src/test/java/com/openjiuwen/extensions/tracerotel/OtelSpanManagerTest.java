/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OtelAgentSpanManager} and {@link OtelWorkflowSpanManager}.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/extensions/tracer_otel/test_span_manager.py}.</p>
 */
@DisplayName("OtelSpanManager tests")
class OtelSpanManagerTest {

    /** Minimal mock for {@link Span}. */
    private static Span mockSpan() {
        return io.opentelemetry.api.trace.Span.getInvalid();
    }

    @Nested
    @DisplayName("OtelAgentSpanManager")
    class TestOtelAgentSpanManager {

        @Test
        @DisplayName("push and get")
        void testPushAndGet() {
            OtelAgentSpanManager mgr = new OtelAgentSpanManager();
            OtelSpanState state = new OtelSpanState(mockSpan(), null, "inv1", null);
            mgr.push("inv1", state);
            assertThat(mgr.get("inv1")).isSameAs(state);
        }

        @Test
        @DisplayName("pop removes mapping")
        void testPopRemovesMapping() {
            OtelAgentSpanManager mgr = new OtelAgentSpanManager();
            OtelSpanState state = new OtelSpanState(mockSpan(), null, "inv1", null);
            mgr.push("inv1", state);
            OtelSpanState popped = mgr.pop("inv1");
            assertThat(popped).isSameAs(state);
            assertThat(mgr.get("inv1")).isNull();
        }

        @Test
        @DisplayName("pop nonexistent returns null")
        void testPopNonexistentReturnsNull() {
            OtelAgentSpanManager mgr = new OtelAgentSpanManager();
            assertThat(mgr.pop("nonexistent")).isNull();
        }

        @Test
        @DisplayName("get nonexistent returns null")
        void testGetNonexistentReturnsNull() {
            OtelAgentSpanManager mgr = new OtelAgentSpanManager();
            assertThat(mgr.get("nonexistent")).isNull();
        }

        @Test
        @DisplayName("parent context resolution works")
        void testParentContextResolution() {
            OtelAgentSpanManager mgr = new OtelAgentSpanManager();
            OtelSpanState parentState = new OtelSpanState(mockSpan(), null, "parent_inv", null);
            mgr.push("parent_inv", parentState);
            assertThat(mgr.get("parent_inv")).isSameAs(parentState);

            mgr.pop("parent_inv");
            assertThat(mgr.get("parent_inv")).isNull();
        }
    }

    @Nested
    @DisplayName("OtelWorkflowSpanManager")
    class TestOtelWorkflowSpanManager {

        @Test
        @DisplayName("push and get")
        void testPushAndGet() {
            OtelWorkflowSpanManager mgr = new OtelWorkflowSpanManager();
            OtelSpanState state = new OtelSpanState(mockSpan(), null, "inv1", null);
            mgr.push("inv1", state);
            assertThat(mgr.get("inv1")).isSameAs(state);
        }

        @Test
        @DisplayName("pop removes mapping and buffers")
        void testPopRemovesMappingAndBuffers() {
            OtelWorkflowSpanManager mgr = new OtelWorkflowSpanManager();
            OtelSpanState state = new OtelSpanState(mockSpan(), null, "inv1", null);
            mgr.push("inv1", state);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("step", 1);
            mgr.appendOnInvokeData("inv1", data);
            mgr.appendStreamInput("inv1", Map.of("chunk", "a"));
            mgr.appendStreamOutput("inv1", Map.of("chunk", "b"));

            OtelSpanState popped = mgr.pop("inv1");
            assertThat(popped).isSameAs(state);
            assertThat(mgr.get("inv1")).isNull();
            assertThat(mgr.getOnInvokeData("inv1")).isEmpty();
            assertThat(mgr.getStreamInputs("inv1")).isEmpty();
            assertThat(mgr.getStreamOutputs("inv1")).isEmpty();
        }

        @Test
        @DisplayName("pop nonexistent returns null")
        void testPopNonexistentReturnsNull() {
            OtelWorkflowSpanManager mgr = new OtelWorkflowSpanManager();
            assertThat(mgr.pop("nonexistent")).isNull();
        }

        @Test
        @DisplayName("append on_invoke_data")
        void testAppendOnInvokeData() {
            OtelWorkflowSpanManager mgr = new OtelWorkflowSpanManager();
            OtelSpanState state = new OtelSpanState(mockSpan(), null, "inv1", null);
            mgr.push("inv1", state);
            Map<String, Object> d1 = new LinkedHashMap<>();
            d1.put("step", 1);
            Map<String, Object> d2 = new LinkedHashMap<>();
            d2.put("step", 2);
            mgr.appendOnInvokeData("inv1", d1);
            mgr.appendOnInvokeData("inv1", d2);
            List<Map<String, Object>> data = mgr.getOnInvokeData("inv1");
            assertThat(data).hasSize(2);
            assertThat(data.get(0)).containsEntry("step", 1);
            assertThat(data.get(1)).containsEntry("step", 2);
        }

        @Test
        @DisplayName("get on_invoke_data nonexistent returns empty")
        void testGetOnInvokeDataNonexistentReturnsEmpty() {
            OtelWorkflowSpanManager mgr = new OtelWorkflowSpanManager();
            assertThat(mgr.getOnInvokeData("nonexistent")).isEmpty();
        }

        @Test
        @DisplayName("append stream input")
        void testAppendStreamInput() {
            OtelWorkflowSpanManager mgr = new OtelWorkflowSpanManager();
            OtelSpanState state = new OtelSpanState(mockSpan(), null, "inv1", null);
            mgr.push("inv1", state);
            mgr.appendStreamInput("inv1", Map.of("text", "hello"));
            mgr.appendStreamInput("inv1", Map.of("text", "world"));
            assertThat(mgr.getStreamInputs("inv1")).hasSize(2);
        }

        @Test
        @DisplayName("append stream output")
        void testAppendStreamOutput() {
            OtelWorkflowSpanManager mgr = new OtelWorkflowSpanManager();
            OtelSpanState state = new OtelSpanState(mockSpan(), null, "inv1", null);
            mgr.push("inv1", state);
            mgr.appendStreamOutput("inv1", Map.of("result", "ok"));
            assertThat(mgr.getStreamOutputs("inv1")).hasSize(1);
        }

        @Test
        @DisplayName("buffer is empty after push")
        void testBufferIsEmptyAfterPush() {
            OtelWorkflowSpanManager mgr = new OtelWorkflowSpanManager();
            OtelSpanState state = new OtelSpanState(mockSpan(), null, "inv1", null);
            mgr.push("inv1", state);
            assertThat(mgr.getOnInvokeData("inv1")).isEmpty();
            assertThat(mgr.getStreamInputs("inv1")).isEmpty();
            assertThat(mgr.getStreamOutputs("inv1")).isEmpty();
        }
    }
}
