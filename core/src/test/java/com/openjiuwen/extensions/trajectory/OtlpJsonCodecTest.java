/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.trajectory;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Golden-shape tests for the single-span OTLP JSON encoding.
 *
 * @since 0.1.18
 */
class OtlpJsonCodecTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private InMemorySpanExporter exporter;
    private SdkTracerProvider provider;
    private Tracer tracer;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        tracer = provider.get("test", "1");
    }

    @AfterEach
    void tearDown() {
        provider.shutdown().join(1, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test
    void encodesFinalSpanWithHexIdsAndResource() throws Exception {
        Span span = tracer.spanBuilder("chat test-model")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(TrajectorySemConv.GEN_AI_OPERATION_NAME, "chat")
                .startSpan();
        span.end();

        JsonNode root = MAPPER.readTree(
                OtlpJsonCodec.encodeFinalSpan(exporter.getFinishedSpanItems().get(0),
                        "test-service"));

        JsonNode resourceSpan = root.get("resourceSpans").get(0);
        assertThat(resourceSpan.get("resource").get("attributes").get(0).get("key").asText())
                .isEqualTo("service.name");
        assertThat(resourceSpan.get("resource").get("attributes").get(0)
                .get("value").get("stringValue").asText()).isEqualTo("test-service");
        JsonNode scopeSpan = resourceSpan.get("scopeSpans").get(0);
        assertThat(scopeSpan.get("scope").get("name").asText())
                .isEqualTo(OtlpJsonCodec.SCOPE_NAME);
        JsonNode spanNode = scopeSpan.get("spans").get(0);
        assertThat(spanNode.get("traceId").asText()).hasSize(32).matches("[0-9a-f]+");
        assertThat(spanNode.get("spanId").asText()).hasSize(16).matches("[0-9a-f]+");
        assertThat(spanNode.get("name").asText()).isEqualTo("chat test-model");
        assertThat(spanNode.get("kind").asInt()).isEqualTo(3);
        assertThat(spanNode.get("startTimeUnixNano").asText()).matches("\\d+");
        assertThat(spanNode.get("endTimeUnixNano").asText()).matches("\\d+");
    }

    @Test
    void omitsParentSpanIdForRootSpan() throws Exception {
        Span span = tracer.spanBuilder("root").setSpanKind(SpanKind.SERVER).startSpan();
        span.end();

        JsonNode root = MAPPER.readTree(
                OtlpJsonCodec.encodeFinalSpan(exporter.getFinishedSpanItems().get(0), "svc"));

        assertThat(root.at("/resourceSpans/0/scopeSpans/0/spans/0").has("parentSpanId"))
                .isFalse();
    }

    @Test
    void keepsParentSpanIdForChildSpan() throws Exception {
        Span parent = tracer.spanBuilder("parent").startSpan();
        Span child = tracer.spanBuilder("child")
                .setParent(parent.storeInContext(Context.current()))
                .startSpan();
        child.end();
        parent.end();

        SpanData childData = exportByName("child");
        SpanData parentData = exportByName("parent");
        JsonNode root = MAPPER.readTree(OtlpJsonCodec.encodeFinalSpan(childData, "svc"));

        assertThat(root.at("/resourceSpans/0/scopeSpans/0/spans/0/parentSpanId").asText())
                .isEqualTo(parentData.getSpanId());
    }

    private SpanData exportByName(String name) {
        return exporter.getFinishedSpanItems().stream()
                .filter(span -> name.equals(span.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("span not exported: " + name));
    }

    @Test
    void mapsAttributeValueTypesToProtobufJson() throws Exception {
        Span span = tracer.spanBuilder("attrs").startSpan();
        span.setAttribute("string.key", "value");
        span.setAttribute("long.key", 42L);
        span.setAttribute("bool.key", true);
        span.setAttribute("double.key", 0.5d);
        span.end();

        JsonNode attributes = MAPPER
                .readTree(OtlpJsonCodec.encodeFinalSpan(
                        exporter.getFinishedSpanItems().get(0), "svc"))
                .at("/resourceSpans/0/scopeSpans/0/spans/0/attributes");
        Map<String, JsonNode> byKey = new java.util.LinkedHashMap<>();
        for (JsonNode attribute : attributes) {
            byKey.put(attribute.get("key").asText(), attribute.get("value"));
        }
        assertThat(byKey.get("string.key").get("stringValue").asText()).isEqualTo("value");
        assertThat(byKey.get("long.key").get("intValue").asText()).isEqualTo("42");
        assertThat(byKey.get("bool.key").get("boolValue").asBoolean()).isTrue();
        assertThat(byKey.get("double.key").get("doubleValue").asDouble()).isEqualTo(0.5d);
    }

    @Test
    void runningSpanOmitsEndTimeAndKeepsAttributes() throws Exception {
        AtomicReference<SpanData> runningSpan = new AtomicReference<>();
        SpanProcessor capturing = new SpanProcessor() {
            @Override
            public void onStart(Context parentContext, ReadWriteSpan span) {
                runningSpan.set(span.toSpanData());
            }

            @Override
            public void onEnd(io.opentelemetry.sdk.trace.ReadableSpan span) {
            }

            @Override
            public boolean isStartRequired() {
                return true;
            }

            @Override
            public boolean isEndRequired() {
                return false;
            }
        };
        SdkTracerProvider runningProvider = SdkTracerProvider.builder()
                .addSpanProcessor(capturing)
                .build();
        try {
            Tracer runningTracer = runningProvider.get("test", "1");
            Span span = runningTracer.spanBuilder("running")
                    .setAttribute(TrajectorySemConv.GEN_AI_CONVERSATION_ID, "session-1")
                    .startSpan();

            JsonNode root = MAPPER.readTree(
                    OtlpJsonCodec.encodeRunningSpan(runningSpan.get(), "svc"));
            JsonNode spanNode = root.at("/resourceSpans/0/scopeSpans/0/spans/0");
            assertThat(spanNode.has("endTimeUnixNano")).isFalse();
            assertThat(spanNode.get("startTimeUnixNano").asText()).matches("\\d+");
            span.end();
        } finally {
            runningProvider.shutdown().join(1, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Test
    void errorStatusEncodesCodeTwo() throws Exception {
        Span span = tracer.spanBuilder("failed").startSpan();
        span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, "boom");
        span.end();

        JsonNode root = MAPPER.readTree(
                OtlpJsonCodec.encodeFinalSpan(exporter.getFinishedSpanItems().get(0), "svc"));
        JsonNode status = root.at("/resourceSpans/0/scopeSpans/0/spans/0/status");
        assertThat(status.get("code").asInt()).isEqualTo(2);
        assertThat(status.get("message").asText()).isEqualTo("boom");
    }

    @Test
    void unsetStatusOmitsStatusField() throws Exception {
        Span span = tracer.spanBuilder("plain").startSpan();
        span.end();

        JsonNode root = MAPPER.readTree(
                OtlpJsonCodec.encodeFinalSpan(exporter.getFinishedSpanItems().get(0), "svc"));
        assertThat(root.at("/resourceSpans/0/scopeSpans/0/spans/0").has("status")).isFalse();
    }

    @Test
    void serializesListsAsJsonText() {
        String json = OtlpJsonCodec.writeValueAsJson(List.of(Map.of("type", "text")));
        assertThat(json).contains("\"type\"").contains("text");
    }
}
