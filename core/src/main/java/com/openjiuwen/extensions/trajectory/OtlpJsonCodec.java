/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.trajectory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Encodes one finished or running OTel span as a single-span OTLP/JSON
 * {@code ExportTraceServiceRequest} payload.
 *
 * <p>The output is the protobuf-JSON mapping the swarm WebUI trajectory
 * viewer consumes: {@code resourceSpans[0].scopeSpans[0].spans[0]} with
 * lower-case hex {@code traceId}/{@code spanId}/{@code parentSpanId},
 * decimal-string {@code intValue} values, and numeric span {@code kind} /
 * {@code status.code} codes. Large GenAI attributes stay inline in this
 * profile; content-addressed sequence references are a planned follow-up.</p>
 *
 * @since 0.1.18
 */
public final class OtlpJsonCodec {
    /** Instrumentation scope name stamped on every exported span. */
    public static final String SCOPE_NAME = "openjiuwen.observability";

    /** Instrumentation scope version stamped on every exported span. */
    public static final String SCOPE_VERSION = "1";

    static final String SERVICE_NAME_KEY = "service.name";

    private static final Logger LOG = LoggerFactory.getLogger(OtlpJsonCodec.class);

    /** Shared Jackson mapper; OTLP JSON encoding is JSON-tree based and thread safe. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OtlpJsonCodec() {
    }

    /**
     * Encode a finished span into a single-span OTLP JSON document.
     *
     * @param span the ended span data
     * @param serviceName value of the {@code service.name} resource attribute
     * @return OTLP JSON text, never null
     */
    public static String encodeFinalSpan(SpanData span, String serviceName) {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode resourceSpans = root.putArray("resourceSpans");
        ObjectNode resourceSpan = resourceSpans.addObject();
        resourceSpan.set("resource", resource(serviceName));
        ArrayNode scopeSpans = resourceSpan.putArray("scopeSpans");
        ObjectNode scopeSpan = scopeSpans.addObject();
        scopeSpan.set("scope", scope());
        ArrayNode spans = scopeSpan.putArray("spans");
        spans.add(spanNode(span, span.getEndEpochNanos()));
        return writeJson(root);
    }

    /**
     * Encode a still-running span snapshot into a single-span OTLP JSON
     * document without {@code endTimeUnixNano}.
     *
     * @param span the running span data
     * @param serviceName value of the {@code service.name} resource attribute
     * @return OTLP JSON text, never null
     */
    public static String encodeRunningSpan(SpanData span, String serviceName) {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode resourceSpans = root.putArray("resourceSpans");
        ObjectNode resourceSpan = resourceSpans.addObject();
        resourceSpan.set("resource", resource(serviceName));
        ArrayNode scopeSpans = resourceSpan.putArray("scopeSpans");
        ObjectNode scopeSpan = scopeSpans.addObject();
        scopeSpan.set("scope", scope());
        ArrayNode spans = scopeSpan.putArray("spans");
        spans.add(spanNode(span, null));
        return writeJson(root);
    }

    /**
     * Serialize an arbitrary value to JSON text for span attributes.
     *
     * @param value value to serialize, may be null
     * @return JSON text, or {@code "null"} when serialization fails
     */
    public static String writeValueAsJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            LOG.warn("Failed to serialize attribute JSON: {}", ex.getMessage());
            return "null";
        }
    }

    private static ObjectNode resource(String serviceName) {
        ObjectNode resource = MAPPER.createObjectNode();
        ArrayNode attributes = resource.putArray("attributes");
        if (serviceName != null && !serviceName.isEmpty()) {
            attributes.add(attribute(SERVICE_NAME_KEY, MAPPER.createObjectNode()
                    .put("stringValue", serviceName)));
        }
        return resource;
    }

    private static ObjectNode scope() {
        ObjectNode scope = MAPPER.createObjectNode();
        scope.put("name", SCOPE_NAME);
        scope.put("version", SCOPE_VERSION);
        return scope;
    }

    private static ObjectNode spanNode(SpanData span, Long endEpochNanos) {
        ObjectNode spanNode = MAPPER.createObjectNode();
        spanNode.put("traceId", span.getTraceId());
        spanNode.put("spanId", span.getSpanId());
        if (span.getParentSpanId() != null && !span.getParentSpanId().isEmpty()
                && !"0000000000000000".equals(span.getParentSpanId())) {
            spanNode.put("parentSpanId", span.getParentSpanId());
        }
        spanNode.put("name", span.getName());
        spanNode.put("kind", kindCode(span.getKind()));
        spanNode.put("startTimeUnixNano", String.valueOf(span.getStartEpochNanos()));
        if (endEpochNanos != null) {
            spanNode.put("endTimeUnixNano", String.valueOf(endEpochNanos));
        }
        ArrayNode attributes = spanNode.putArray("attributes");
        for (Map.Entry<AttributeKey<?>, Object> entry : span.getAttributes().asMap().entrySet()) {
            attributeValue(entry.getValue()).ifPresent(value ->
                    attributes.add(attribute(entry.getKey().getKey(), value)));
        }
        if (span.getStatus() != null && span.getStatus().getStatusCode() != StatusCode.UNSET) {
            ObjectNode status = spanNode.putObject("status");
            status.put("code", statusCode(span.getStatus().getStatusCode()));
            if (span.getStatus().getDescription() != null) {
                status.put("message", span.getStatus().getDescription());
            }
        }
        return spanNode;
    }

    private static ObjectNode attribute(String key, ObjectNode value) {
        ObjectNode attribute = MAPPER.createObjectNode();
        attribute.put("key", key);
        attribute.set("value", value);
        return attribute;
    }

    private static Optional<ObjectNode> attributeValue(Object raw) {
        ObjectNode node = MAPPER.createObjectNode();
        if (raw instanceof String text) {
            node.put("stringValue", text);
            return Optional.of(node);
        }
        if (raw instanceof Long number) {
            node.put("intValue", String.valueOf(number));
            return Optional.of(node);
        }
        if (raw instanceof Integer number) {
            node.put("intValue", String.valueOf(number));
            return Optional.of(node);
        }
        if (raw instanceof Boolean) {
            node.put("boolValue", (Boolean) raw);
            return Optional.of(node);
        }
        if (raw instanceof Double number) {
            node.put("doubleValue", number);
            return Optional.of(node);
        }
        if (raw instanceof Float number) {
            node.put("doubleValue", number.doubleValue());
            return Optional.of(node);
        }
        if (raw instanceof List<?> values) {
            ArrayNode array = node.putArray("arrayValue").addObject().putArray("values");
            for (Object item : values) {
                array.add(attributeValue(item).orElseGet(MAPPER::createObjectNode));
            }
            return Optional.of(node);
        }
        LOG.debug("Skipping unsupported span attribute type: {}", raw.getClass().getName());
        return Optional.empty();
    }

    private static int kindCode(SpanKind kind) {
        if (kind == null) {
            return 1;
        }
        return switch (kind) {
            case INTERNAL -> 1;
            case SERVER -> 2;
            case CLIENT -> 3;
            case PRODUCER -> 4;
            case CONSUMER -> 5;
        };
    }

    private static int statusCode(StatusCode code) {
        return switch (code) {
            case UNSET -> 0;
            case OK -> 1;
            case ERROR -> 2;
        };
    }

    private static String writeJson(JsonNode root) {
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to encode OTLP span JSON", ex);
        }
    }
}
