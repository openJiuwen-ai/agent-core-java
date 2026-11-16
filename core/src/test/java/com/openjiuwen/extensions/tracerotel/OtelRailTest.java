/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.session.AgentGroupSession;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.AgentTeamSession;
import com.openjiuwen.core.session.tracer.TracerHandlerRegistry;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.deep_agent.DeepAgentSession;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;

/**
 * Verifies OtelRail exports spans through supported session facades, including DeepAgentSession.
 */
class OtelRailTest {
    private final String handlerName = "otel-rail-test-" + UUID.randomUUID();
    private InMemorySpanExporter exporter;
    private SdkTracerProvider provider;
    private OtelRail rail;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        TracerHandlerRegistry.registerHandler(handlerName,
                new OtelAgentHandler(provider.get("otel-rail-test"), OtelTracerConfig.builder().build()));
        rail = new OtelRail();
    }

    @AfterEach
    void tearDown() {
        TracerHandlerRegistry.unregisterHandler(handlerName);
        provider.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"agent", "team", "group", "deep"})
    void callbacks_supportedFacade_exportChainWithModelAndToolChildren(String sessionType) {
        AgentCallbackContext ctx = AgentCallbackContext.builder().session(newSession(sessionType)).build();
        InvokeInputs invokeInputs = new InvokeInputs();
        invokeInputs.setQuery("call a tool");
        Span parent = provider.get("otel-rail-test").spanBuilder("http.request").startSpan();
        try (Scope ignored = parent.makeCurrent()) {
            ctx.setInputs(invokeInputs);
            rail.beforeInvoke(ctx);
            ctx.setInputs(new ModelCallInputs());
            rail.beforeModelCall(ctx);
            rail.afterModelCall(ctx);
            ToolCallInputs toolInputs = new ToolCallInputs();
            toolInputs.setToolName("call_mcp");
            ctx.setInputs(toolInputs);
            rail.beforeToolCall(ctx);
            rail.afterToolCall(ctx);
            ctx.setInputs(invokeInputs);
            rail.afterInvoke(ctx);
            assertThat(Span.current().getSpanContext()).isEqualTo(parent.getSpanContext());
        } finally {
            parent.end();
        }

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertThat(spans).extracting(SpanData::getName)
                .containsExactlyInAnyOrder("http.request", "chain.unknown", "llm.LLM", "tool.call_mcp");
        SpanData chain = spanNamed("chain.unknown");
        assertThat(chain.getParentSpanId()).isEqualTo(parent.getSpanContext().getSpanId());
        for (String name : List.of("llm.LLM", "tool.call_mcp")) {
            SpanData child = spanNamed(name);
            assertThat(child.getParentSpanId()).isEqualTo(chain.getSpanId());
            assertThat(child.getTraceId()).isEqualTo(chain.getTraceId());
        }
        assertThat(ctx.getExtra()).doesNotContainKeys("_otel_root_span", "_otel_llm_span", "_otel_tool_span");
    }

    @ParameterizedTest
    @ValueSource(strings = {"invoke", "model", "tool"})
    void callbacks_deepSessionFailure_endSpansWithError(String failure) {
        AgentCallbackContext ctx = AgentCallbackContext.builder().session(newSession("deep")).build();
        InvokeInputs invokeInputs = new InvokeInputs();
        ctx.setInputs(invokeInputs);
        rail.beforeInvoke(ctx);
        if ("model".equals(failure)) {
            ctx.setInputs(new ModelCallInputs());
            rail.beforeModelCall(ctx);
        } else if ("tool".equals(failure)) {
            ToolCallInputs toolInputs = new ToolCallInputs();
            toolInputs.setToolName("call_mcp");
            ctx.setInputs(toolInputs);
            rail.beforeToolCall(ctx);
        }
        ctx.setException(new IllegalStateException("test failure"));
        if ("model".equals(failure)) {
            rail.onModelException(ctx);
            rail.afterModelCall(ctx);
        } else if ("tool".equals(failure)) {
            rail.onToolException(ctx);
            rail.afterToolCall(ctx);
        }
        ctx.setInputs(invokeInputs);
        rail.afterInvoke(ctx);

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertThat(spans).hasSize("invoke".equals(failure) ? 1 : 2);
        assertThat(spans).allSatisfy(span -> assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR));
        assertThat(ctx.getExtra()).doesNotContainKeys("_otel_root_span", "_otel_llm_span", "_otel_tool_span");
    }

    private SpanData spanNamed(String name) {
        return exporter.getFinishedSpanItems().stream().filter(span -> name.equals(span.getName()))
                .findFirst().orElseThrow();
    }

    private AgentSessionApi newSession(String type) {
        String sessionId = "otel-rail-" + UUID.randomUUID();
        return switch (type) {
            case "agent" -> new AgentSession(sessionId, null, null);
            case "team" -> new AgentTeamSession(sessionId, null, "otel-team");
            case "group" -> new AgentGroupSession(sessionId);
            case "deep" -> new DeepAgentSession(sessionId);
            default -> throw new IllegalArgumentException("Unknown session type: " + type);
        };
    }
}
