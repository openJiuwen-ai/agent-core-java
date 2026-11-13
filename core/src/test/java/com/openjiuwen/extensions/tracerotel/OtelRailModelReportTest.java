/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.tracer.SpanManager;
import com.openjiuwen.core.session.tracer.Tracer;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests that {@link OtelRail} reports the model actually used for the call,
 * falling back to static configuration when the rail inputs carry no model name.
 *
 * @since 0.1.16
 */
@DisplayName("OtelRail model reporting")
class OtelRailModelReportTest {

    /**
     * Attach a tracer-backed session to the context builder.
     *
     * @param builder the context builder to enrich
     * @return the mocked tracer used by the session
     */
    private static Tracer attachSession(AgentCallbackContext.Builder builder) {
        Tracer tracer = mock(Tracer.class);
        when(tracer.getTracerAgentSpanManager()).thenReturn(mock(SpanManager.class));
        // OtelRail resolves the tracer by unwrapping the public session facade
        // via getInner() and reading the internal session's tracer.
        com.openjiuwen.core.session.internal.AgentSession inner =
                mock(com.openjiuwen.core.session.internal.AgentSession.class);
        when(inner.tracer()).thenReturn(tracer);
        AgentSession session = mock(AgentSession.class);
        when(session.getInner()).thenReturn(inner);
        builder.session(session).extra(new HashMap<>());
        return tracer;
    }

    /**
     * Run {@link OtelRail#beforeModelCall} and return the reported instance info.
     *
     * @param tracer the tracer backing the context session
     * @param ctx the context handed to the rail
     * @return the {@code instance_info} map reported to the tracer
     */
    private static Map<?, ?> instanceInfoOf(Tracer tracer, AgentCallbackContext ctx) {
        AtomicReference<Object> captured = new AtomicReference<>();
        doAnswer(invocation -> {
            captured.set(invocation.getArgument(2));
            return null;
        }).when(tracer).trigger(anyString(), anyString(), any());

        new OtelRail().beforeModelCall(ctx);

        assertThat(captured.get()).isInstanceOf(Map.class);
        Map<?, ?> kwargs = (Map<?, ?>) captured.get();
        assertThat(kwargs.get("instance_info")).isInstanceOf(Map.class);
        return (Map<?, ?>) kwargs.get("instance_info");
    }

    @Test
    @DisplayName("beforeModelCall reports the actual model name and raw pool key")
    void reportsActualModel() {
        ModelCallInputs inputs = ModelCallInputs.builder()
                .messages(List.of())
                .modelName("model-b")
                .modelId("pkg-b")
                .build();
        AgentCallbackContext.Builder builder = AgentCallbackContext.builder().inputs(inputs);
        Tracer tracer = attachSession(builder);

        Map<?, ?> info = instanceInfoOf(tracer, builder.build());

        assertThat(info.get("class_name")).isEqualTo("model-b");
        assertThat(info.get("model_id")).isEqualTo("pkg-b");
    }

    @Test
    @DisplayName("beforeModelCall falls back to the configured model name when inputs carry none")
    void fallsBackToConfiguredModel() {
        ReActAgent agent = new ReActAgent(AgentCard.builder().id("otel-rail-agent").name("otel-rail-agent").build());
        agent.configure(ReActAgentConfig.builder().modelName("cfg-model").build());
        ModelCallInputs inputs = ModelCallInputs.builder().messages(List.of()).build();
        AgentCallbackContext.Builder builder =
                AgentCallbackContext.builder().agent(agent).inputs(inputs);
        Tracer tracer = attachSession(builder);

        Map<?, ?> info = instanceInfoOf(tracer, builder.build());

        assertThat(info.get("class_name")).isEqualTo("cfg-model");
        assertThat(info.containsKey("model_id")).isFalse();
    }

    @Test
    @DisplayName("beforeModelCall falls back when the reported model name is blank")
    void fallsBackWhenModelNameBlank() {
        ReActAgent agent = new ReActAgent(AgentCard.builder().id("otel-rail-blank").name("otel-rail-blank").build());
        agent.configure(ReActAgentConfig.builder().modelName("cfg-model").build());
        ModelCallInputs inputs = ModelCallInputs.builder().messages(List.of()).modelName("   ").build();
        AgentCallbackContext.Builder builder =
                AgentCallbackContext.builder().agent(agent).inputs(inputs);
        Tracer tracer = attachSession(builder);

        Map<?, ?> info = instanceInfoOf(tracer, builder.build());

        assertThat(info.get("class_name")).isEqualTo("cfg-model");
    }

    @Test
    @DisplayName("beforeModelCall tolerates a blank pool key without failing the model call")
    void blankPoolKeyDoesNotInterruptModelCall() {
        ReActAgent agent =
                new ReActAgent(AgentCard.builder().id("otel-rail-blank-id").name("otel-rail-blank-id").build());
        agent.configure(ReActAgentConfig.builder().modelName("cfg-model").build());
        // Blank pool key on the inputs snapshot means "no dynamic model": the rail
        // must skip the pool lookup instead of letting id validation fail the call.
        ModelCallInputs inputs = ModelCallInputs.builder().messages(List.of()).modelId("   ").build();
        AgentCallbackContext.Builder builder =
                AgentCallbackContext.builder().agent(agent).inputs(inputs);
        Tracer tracer = attachSession(builder);

        Map<?, ?> info = instanceInfoOf(tracer, builder.build());

        assertThat(info.get("class_name")).isEqualTo("cfg-model");
        assertThat(info.containsKey("model_id")).isFalse();
    }
}
