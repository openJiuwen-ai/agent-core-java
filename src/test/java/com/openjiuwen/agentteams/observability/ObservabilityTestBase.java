/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.observability;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared test infrastructure for the agent_teams observability tests.
 *
 * <p>Sets up an {@link InMemorySpanExporter} backed by a private
 * {@link SdkTracerProvider} and injects it into {@link ObservabilitySetup}
 * via reflection, because {@code initObservability} does not accept a span
 * exporter override (unlike the Python counterpart). This ensures that all
 * spans created by {@link ObservabilityRail} and
 * {@link ObservabilitySetup#startTeamTrace} flow to the in-memory exporter
 * for inspection.</p>
 *
 * <p>Mirrors the Python test fixture {@code in_memory_exporter} and the
 * Java {@code ConftestOtel} pattern from the {@code tracerotel} extension.</p>
 *
 * @since 0.1.7
 */
public abstract class ObservabilityTestBase {

    /** Per-test in-memory span exporter for inspection. */
    protected InMemorySpanExporter exporter;

    /**
     * Set up a fresh observability environment before each test.
     *
     * <p>Creates an {@link InMemorySpanExporter} with an
     * {@link SdkTracerProvider} using {@link Sampler#alwaysOn()} and
     * {@link SimpleSpanProcessor}, then injects the provider and a test
     * {@link ObservabilityConfig} into {@link ObservabilitySetup} via
     * reflection so that {@link ObservabilitySetup#getTracer(String)} and
     * {@link ObservabilitySetup#startTeamTrace(String, String)} route spans
     * to the in-memory exporter.</p>
     *
     * @throws Exception if reflection injection fails
     */
    @BeforeEach
    void setUpObservability() throws Exception {
        // Clean up any leftover state from a previous test or class.
        if (ObservabilitySetup.isInitialized()) {
            ObservabilitySetup.shutdownObservability();
        }
        OtelSpanContext.resetAll();

        exporter = InMemorySpanExporter.create();

        SdkTracerProvider provider = SdkTracerProvider.builder()
                .setResource(Resource.create(Attributes.of(
                        AttributeKey.stringKey("service.name"), "openjiuwen-test")))
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .setSampler(Sampler.alwaysOn())
                .build();

        setStaticField("provider", provider);

        ObservabilityConfig config = ObservabilityConfig.builder()
                .isEnabled(true)
                .serviceName("openjiuwen-test")
                .sampleRate(1.0)
                .shouldRedactPrompts(false)
                .shouldRedactCompletions(false)
                .build();
        setStaticField("config", config);
        setStaticField("isInitialized", Boolean.TRUE);
    }

    /**
     * Tear down the observability environment after each test.
     *
     * <p>Calls {@link ObservabilitySetup#shutdownObservability()} which
     * flushes and shuts down the injected provider, resets config and
     * initialized flag, and calls {@link OtelSpanContext#resetAll()}.
     * An additional {@link OtelSpanContext#resetAll()} is called for
     * extra safety.</p>
     */
    @AfterEach
    void tearDownObservability() {
        ObservabilitySetup.shutdownObservability();
        OtelSpanContext.resetAll();
    }

    // ================================================================
    // Reflection helper
    // ================================================================

    /**
     * Set a private static field on {@link ObservabilitySetup}.
     *
     * @param name  the field name
     * @param value the value to set
     * @throws Exception if the field does not exist or is inaccessible
     */
    private static void setStaticField(String name, Object value) throws Exception {
        Field field = ObservabilitySetup.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    // ================================================================
    // Span inspection helpers
    // ================================================================

    /**
     * Return all finished spans collected by the in-memory exporter.
     *
     * @return a list of finished {@link SpanData}
     */
    protected List<SpanData> finishedSpans() {
        return exporter.getFinishedSpanItems();
    }

    /**
     * Return all finished spans whose name exactly matches the given name.
     *
     * @param name the span name to match
     * @return a list of matching {@link SpanData}
     */
    protected List<SpanData> spansByName(String name) {
        List<SpanData> result = new ArrayList<>();
        for (SpanData s : finishedSpans()) {
            if (s.getName().equals(name)) {
                result.add(s);
            }
        }
        return result;
    }

    /**
     * Return all finished spans whose name starts with the given prefix.
     *
     * @param prefix the span name prefix
     * @return a list of matching {@link SpanData}
     */
    protected List<SpanData> spansByPrefix(String prefix) {
        List<SpanData> result = new ArrayList<>();
        for (SpanData s : finishedSpans()) {
            if (s.getName().startsWith(prefix)) {
                result.add(s);
            }
        }
        return result;
    }

    /**
     * Return the first finished span, asserting that at least one exists.
     *
     * @return the first finished {@link SpanData}
     */
    protected SpanData firstSpan() {
        List<SpanData> spans = finishedSpans();
        assertThat(spans).isNotEmpty();
        return spans.get(0);
    }

    /**
     * Look up an attribute on a span as a string, returning {@code null} if absent.
     *
     * <p>OpenTelemetry attributes are typed. This helper first tries the
     * String key type, then falls back to Long and Boolean so that numeric
     * attributes (e.g. token counts) and boolean attributes (e.g. broadcast
     * flags) can be read uniformly in assertions.</p>
     *
     * @param span the span data
     * @param key  the attribute key
     * @return the attribute value as a string, or {@code null}
     */
    protected String attr(SpanData span, String key) {
        Object value = span.getAttributes().get(AttributeKey.stringKey(key));
        if (value != null) {
            return value.toString();
        }
        Long longValue = span.getAttributes().get(AttributeKey.longKey(key));
        if (longValue != null) {
            return longValue.toString();
        }
        Boolean boolValue = span.getAttributes().get(AttributeKey.booleanKey(key));
        return boolValue != null ? boolValue.toString() : null;
    }

    /**
     * Check whether a span has a given attribute (any type).
     *
     * @param span the span data
     * @param key  the attribute key
     * @return {@code true} if the attribute is present
     */
    protected boolean hasAttr(SpanData span, String key) {
        return span.getAttributes().get(AttributeKey.stringKey(key)) != null
                || span.getAttributes().get(AttributeKey.longKey(key)) != null
                || span.getAttributes().get(AttributeKey.booleanKey(key)) != null;
    }

    // ================================================================
    // Stub agent
    // ================================================================

    /**
     * Create a stub agent for testing.
     *
     * <p>The stub extends {@link BaseAgent} so that {@link ObservabilityRail}
     * can resolve the member name via {@code instanceof} type checks rather
     * than reflection. The agent card's name is set to the member name.</p>
     *
     * @param teamName   the team name
     * @param memberName the member (agent) name
     * @return a new stub agent
     */
    protected static StubAgent stubAgent(String teamName, String memberName) {
        return new StubAgent(teamName, memberName);
    }

    /**
     * Minimal stub agent that extends {@link BaseAgent} for observability
     * name resolution without reflection.
     */
    protected static class StubAgent extends BaseAgent {
        private final String teamName;
        private final String memberName;

        StubAgent(String teamName, String memberName) {
            super(AgentCard.builder().name(memberName).build());
            this.teamName = teamName;
            this.memberName = memberName;
        }

        /**
         * Return the team name.
         *
         * @return the team name
         */
        public String getTeamName() {
            return teamName;
        }

        /**
         * Return the member name.
         *
         * @return the member name
         */
        public String getMemberName() {
            return memberName;
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public Object getConfig() {
            return null;
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            return null;
        }

        @Override
        public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
            return List.<Object>of().iterator();
        }
    }

    // ================================================================
    // Context and inputs builders
    // ================================================================

    /**
     * Build an {@link AgentCallbackContext} for an invoke event.
     *
     * @param agent the stub agent
     * @param query the user query
     * @return a new callback context with {@link InvokeInputs}
     */
    protected static AgentCallbackContext invokeContext(StubAgent agent, String query) {
        InvokeInputs inputs = InvokeInputs.builder()
                .query(query)
                .build();
        return AgentCallbackContext.builder()
                .agent(agent)
                .inputs(inputs)
                .build();
    }

    /**
     * Build {@link ToolCallInputs} for a tool call event.
     *
     * @param toolName the tool name
     * @param toolId   the tool call ID
     * @param toolArgs the tool arguments (may be {@code null})
     * @return a new {@link ToolCallInputs}
     */
    protected static ToolCallInputs toolCallInputs(String toolName, String toolId, Object toolArgs) {
        ToolCall toolCall = ToolCall.builder()
                .id(toolId)
                .name(toolName)
                .build();
        return ToolCallInputs.builder()
                .toolName(toolName)
                .toolArgs(toolArgs)
                .toolCall(toolCall)
                .build();
    }

    /**
     * Build {@link ToolCallInputs} with a result for the after-tool-call event.
     *
     * @param toolName   the tool name
     * @param toolId     the tool call ID
     * @param toolArgs   the tool arguments (may be {@code null})
     * @param toolResult the tool result
     * @return a new {@link ToolCallInputs}
     */
    protected static ToolCallInputs toolCallInputs(String toolName, String toolId,
                                                    Object toolArgs, Object toolResult) {
        ToolCall toolCall = ToolCall.builder()
                .id(toolId)
                .name(toolName)
                .build();
        return ToolCallInputs.builder()
                .toolName(toolName)
                .toolArgs(toolArgs)
                .toolCall(toolCall)
                .toolResult(toolResult)
                .build();
    }

    /**
     * Build {@link ModelCallInputs} for a model call event.
     *
     * @param messages the messages list (may be {@code null})
     * @param response the response object (may be {@code null})
     * @return a new {@link ModelCallInputs}
     */
    protected static ModelCallInputs modelCallInputs(List<Object> messages, Object response) {
        return ModelCallInputs.builder()
                .messages(messages)
                .response(response)
                .build();
    }

    /**
     * Build {@link InvokeInputs} with a result for the after-invoke event.
     *
     * @param query  the user query
     * @param result the invoke result map
     * @return a new {@link InvokeInputs}
     */
    protected static InvokeInputs invokeInputsWithResult(String query, Map<String, Object> result) {
        return InvokeInputs.builder()
                .query(query)
                .result(result)
                .build();
    }

    /**
     * Create a simple result map with a single "content" key.
     *
     * @param content the result content
     * @return a map with one entry
     */
    protected static Map<String, Object> simpleResult(String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        return result;
    }
}
