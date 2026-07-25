/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tests for the file-based {@link TraceFileExporter}.
 *
 * <p>Translates the Python test
 * {@code tests/unit_tests/agent_teams/observability/test_file_exporter.py}
 * into JUnit 5. The Java exporter writes a compact JSON line per span
 * (not OTLP format), so assertions verify the Java-specific JSON schema:
 * {@code traceId}, {@code spanId}, {@code parentSpanId}, {@code name},
 * {@code kind}, {@code startEpochNanos}, {@code endEpochNanos},
 * {@code status}, and {@code attributes}.</p>
 *
 * @since 0.1.7
 */
@DisplayName("TraceFileExporter tests")
class TraceFileExporterTest {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private SdkTracerProvider provider;
    private InMemorySpanExporter inMemoryExporter;
    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUpProvider() {
        inMemoryExporter = InMemorySpanExporter.create();
        provider = SdkTracerProvider.builder()
                .setResource(Resource.create(Attributes.of(
                        AttributeKey.stringKey("service.name"), "file-exporter-test")))
                .addSpanProcessor(SimpleSpanProcessor.create(inMemoryExporter))
                .setSampler(Sampler.alwaysOn())
                .build();
    }

    @AfterEach
    void shutdownProvider() {
        provider.shutdown();
    }

    // ================================================================
    // Export in isolation
    // ================================================================

    @Test
    @DisplayName("export() writes immediately to today's file")
    void test_export_writes_immediately() throws IOException {
        TraceFileExporter exporter = new TraceFileExporter(tempDir.toString(), 7);
        SpanData span = makeFinishedSpan("llm.call");

        CompletableResultCode result = exporter.export(List.of(span));
        assertTrue(result.isSuccess());

        Path dayFile = dayFile();
        assertTrue(Files.exists(dayFile), "trace file should exist after export()");

        List<JsonNode> spans = readSpans(dayFile);
        assertEquals(1, spans.size());
        assertEquals("llm.call", spans.get(0).get("name").asText());
    }

    @Test
    @DisplayName("each line is valid JSON with hex traceId and spanId")
    void test_each_line_is_valid_json_with_hex_ids() throws IOException {
        TraceFileExporter exporter = new TraceFileExporter(tempDir.toString(), 7);
        SpanData span = makeFinishedSpan("llm.call");

        exporter.export(List.of(span));

        List<String> lines = Files.readAllLines(dayFile(), StandardCharsets.UTF_8);
        assertEquals(1, lines.size());

        JsonNode data = mapper.readTree(lines.get(0));
        String traceId = data.get("traceId").asText();
        String spanId = data.get("spanId").asText();

        assertEquals(32, traceId.length(), "traceId must be 32 hex chars");
        assertTrue(isHex(traceId), "traceId must be hex: " + traceId);
        assertEquals(16, spanId.length(), "spanId must be 16 hex chars");
        assertTrue(isHex(spanId), "spanId must be hex: " + spanId);
    }

    @Test
    @DisplayName("spans of same trace interleave in one file with parentSpanId link")
    void test_spans_of_same_trace_interleaved() throws IOException {
        TraceFileExporter exporter = new TraceFileExporter(tempDir.toString(), 7);

        io.opentelemetry.api.trace.Tracer tracer = provider.get("ut");
        io.opentelemetry.api.trace.Span parent = tracer.spanBuilder("parent")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
        parent.setAttribute("session.id", "abc");
        parent.setStatus(StatusCode.OK);

        io.opentelemetry.api.trace.Span child = tracer.spanBuilder("child")
                .setSpanKind(SpanKind.INTERNAL)
                .setParent(Context.current().with(parent))
                .startSpan();
        child.setAttribute("session.id", "abc");
        child.setStatus(StatusCode.OK);

        parent.end();
        child.end();
        provider.forceFlush();

        SpanData parentData = findSpanByName("parent");
        SpanData childData = findSpanByName("child");

        exporter.export(List.of(parentData));
        exporter.export(List.of(childData));

        List<JsonNode> spans = readSpans(dayFile());
        assertEquals(2, spans.size());

        Set<String> names = spans.stream()
                .map(s -> s.get("name").asText())
                .collect(Collectors.toSet());
        assertTrue(names.contains("parent"));
        assertTrue(names.contains("child"));

        JsonNode parentJson = findSpanJson(spans, "parent");
        JsonNode childJson = findSpanJson(spans, "child");
        assertEquals(parentJson.get("spanId").asText(),
                childJson.get("parentSpanId").asText(),
                "child parentSpanId should equal parent spanId");
    }

    @Test
    @DisplayName("spans of different traces share one file")
    void test_spans_of_different_traces_share_one_file() throws IOException {
        TraceFileExporter exporter = new TraceFileExporter(tempDir.toString(), 7);

        SpanData s1 = makeFinishedSpan("a.span");
        SpanData s2 = makeFinishedSpan("b.span");

        exporter.export(List.of(s1));
        exporter.export(List.of(s2));

        List<JsonNode> spans = readSpans(dayFile());
        assertEquals(2, spans.size());

        Set<String> traceIds = spans.stream()
                .map(s -> s.get("traceId").asText())
                .collect(Collectors.toSet());
        assertEquals(2, traceIds.size(), "two distinct traces in one file");
    }

    @Test
    @DisplayName("span without session attribute still written")
    void test_no_session_attribute_still_written() throws IOException {
        TraceFileExporter exporter = new TraceFileExporter(tempDir.toString(), 7);
        SpanData span = makeFinishedSpanWithoutSession("orphan.span");

        exporter.export(List.of(span));

        List<JsonNode> spans = readSpans(dayFile());
        assertEquals(1, spans.size());
        assertEquals("orphan.span", spans.get(0).get("name").asText());
    }

    @Test
    @DisplayName("repeated export appends without duplication")
    void test_repeated_export_appends_no_duplication() throws IOException {
        TraceFileExporter exporter = new TraceFileExporter(tempDir.toString(), 7);
        SpanData span = makeFinishedSpan("x.span");

        exporter.export(List.of(span));
        exporter.flush();

        List<JsonNode> spans = readSpans(dayFile());
        assertEquals(1, spans.size(), "flush should not duplicate the line");
    }

    @Test
    @DisplayName("shutdown is no-op, does not lose data")
    void test_shutdown_is_noop_does_not_lose_data() throws IOException {
        TraceFileExporter exporter = new TraceFileExporter(tempDir.toString(), 7);
        SpanData span = makeFinishedSpan("late.span");

        exporter.export(List.of(span));
        exporter.shutdown();

        List<JsonNode> spans = readSpans(dayFile());
        assertEquals(1, spans.size(), "data should persist after shutdown");
    }

    @Test
    @DisplayName("empty collection returns success without writing")
    void test_empty_collection_returns_success() throws IOException {
        TraceFileExporter exporter = new TraceFileExporter(tempDir.toString(), 7);

        CompletableResultCode result = exporter.export(List.of());
        assertTrue(result.isSuccess());
        assertFalse(Files.exists(dayFile()), "no file should be created for empty export");
    }

    @Test
    @DisplayName("null collection returns success without writing")
    void test_null_collection_returns_success() throws IOException {
        TraceFileExporter exporter = new TraceFileExporter(tempDir.toString(), 7);

        CompletableResultCode result = exporter.export(null);
        assertTrue(result.isSuccess());
        assertFalse(Files.exists(dayFile()), "no file should be created for null export");
    }

    // ================================================================
    // JSON attribute serialization
    // ================================================================

    @Test
    @DisplayName("span attributes serialized as JSON object")
    void test_span_attributes_serialized_as_json() throws IOException {
        TraceFileExporter exporter = new TraceFileExporter(tempDir.toString(), 7);

        io.opentelemetry.api.trace.Tracer tracer = provider.get("ut");
        io.opentelemetry.api.trace.Span span = tracer.spanBuilder("attr.span")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
        span.setAttribute("session.id", "sess-1");
        span.setAttribute("gen_ai.tool.name", "search");
        span.setAttribute("gen_ai.usage.total_tokens", 42L);
        span.setAttribute("agentteam.task.status", "completed");
        span.setStatus(StatusCode.OK);
        span.end();
        provider.forceFlush();

        SpanData spanData = findSpanByName("attr.span");
        exporter.export(List.of(spanData));

        List<JsonNode> spans = readSpans(dayFile());
        assertEquals(1, spans.size());
        JsonNode attrs = spans.get(0).get("attributes");
        assertTrue(attrs != null && attrs.isObject(), "attributes should be a JSON object");
        assertEquals("sess-1", attrs.get("session.id").asText());
        assertEquals("search", attrs.get("gen_ai.tool.name").asText());
        assertEquals(42, attrs.get("gen_ai.usage.total_tokens").asInt());
        assertEquals("completed", attrs.get("agentteam.task.status").asText());
    }

    @Test
    @DisplayName("span status description included when present")
    void test_span_status_description_included() throws IOException {
        TraceFileExporter exporter = new TraceFileExporter(tempDir.toString(), 7);

        io.opentelemetry.api.trace.Tracer tracer = provider.get("ut");
        io.opentelemetry.api.trace.Span span = tracer.spanBuilder("error.span")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
        span.setStatus(StatusCode.ERROR, "tool execution failed");
        span.end();
        provider.forceFlush();

        SpanData spanData = findSpanByName("error.span");
        exporter.export(List.of(spanData));

        List<JsonNode> spans = readSpans(dayFile());
        JsonNode spanJson = spans.get(0);
        assertEquals("ERROR", spanJson.get("status").asText());
        assertEquals("tool execution failed", spanJson.get("statusDescription").asText());
    }

    @Test
    @DisplayName("span kind serialized correctly")
    void test_span_kind_serialized_correctly() throws IOException {
        TraceFileExporter exporter = new TraceFileExporter(tempDir.toString(), 7);

        io.opentelemetry.api.trace.Tracer tracer = provider.get("ut");
        io.opentelemetry.api.trace.Span span = tracer.spanBuilder("kind.span")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();
        span.setStatus(StatusCode.OK);
        span.end();
        provider.forceFlush();

        SpanData spanData = findSpanByName("kind.span");
        exporter.export(List.of(spanData));

        List<JsonNode> spans = readSpans(dayFile());
        assertEquals("SERVER", spans.get(0).get("kind").asText());
    }

    @Test
    @DisplayName("span timestamps serialized as numeric nanos")
    void test_span_timestamps_serialized_as_numeric_nanos() throws IOException {
        TraceFileExporter exporter = new TraceFileExporter(tempDir.toString(), 7);
        SpanData span = makeFinishedSpan("ts.span");

        exporter.export(List.of(span));

        List<JsonNode> spans = readSpans(dayFile());
        JsonNode spanJson = spans.get(0);
        assertTrue(spanJson.has("startEpochNanos"), "should have startEpochNanos");
        assertTrue(spanJson.has("endEpochNanos"), "should have endEpochNanos");
        assertTrue(spanJson.get("startEpochNanos").isNumber(), "startEpochNanos should be numeric");
        assertTrue(spanJson.get("endEpochNanos").isNumber(), "endEpochNanos should be numeric");
        assertTrue(spanJson.get("endEpochNanos").asLong() >= spanJson.get("startEpochNanos").asLong(),
                "end should be >= start");
    }

    // ================================================================
    // Cleanup / retention
    // ================================================================

    @Test
    @DisplayName("cleanup deletes old trace files")
    void test_cleanup_deletes_old_trace_files() throws IOException {
        TraceFileExporter exporter = new TraceFileExporter(tempDir.toString(), 1);
        SpanData span = makeFinishedSpan("old.span");
        exporter.export(List.of(span));

        Path oldFile = dayFile();
        assertTrue(Files.isRegularFile(oldFile));

        long oldTime = System.currentTimeMillis() - 2L * 86_400_000L;
        Files.setLastModifiedTime(oldFile, FileTime.fromMillis(oldTime));

        invokeCleanup(exporter);
        assertFalse(Files.exists(oldFile), "old trace file should be deleted");
    }

    @Test
    @DisplayName("cleanup keeps recent trace files")
    void test_cleanup_keeps_recent_trace_files() throws IOException {
        TraceFileExporter exporter = new TraceFileExporter(tempDir.toString(), 7);
        SpanData span = makeFinishedSpan("fresh.span");
        exporter.export(List.of(span));

        invokeCleanup(exporter);
        assertTrue(Files.isRegularFile(dayFile()), "recent trace file should be kept");
    }

    @Test
    @DisplayName("retention zero disables cleanup")
    void test_retention_zero_disables_cleanup() throws IOException {
        TraceFileExporter exporter = new TraceFileExporter(tempDir.toString(), 0);
        SpanData span = makeFinishedSpan("retention.span");
        exporter.export(List.of(span));

        Path file = dayFile();
        long oldTime = System.currentTimeMillis() - 30L * 86_400_000L;
        Files.setLastModifiedTime(file, FileTime.fromMillis(oldTime));

        invokeCleanup(exporter);
        assertTrue(Files.exists(file), "file should not be deleted when retention is 0");
    }

    @Test
    @DisplayName("cleanup only deletes .jsonl files")
    void test_cleanup_only_deletes_jsonl_files() throws IOException {
        TraceFileExporter exporter = new TraceFileExporter(tempDir.toString(), 1);

        // Create a non-jsonl file with old timestamp.
        Path otherFile = tempDir.resolve("config.txt");
        Files.writeString(otherFile, "keep me");
        long oldTime = System.currentTimeMillis() - 10L * 86_400_000L;
        Files.setLastModifiedTime(otherFile, FileTime.fromMillis(oldTime));

        invokeCleanup(exporter);
        assertTrue(Files.exists(otherFile), "non-jsonl files should not be deleted");
    }

    // ================================================================
    // Default directory creation
    // ================================================================

    @Test
    @DisplayName("exporter creates root directory if not exists")
    void test_exporter_creates_root_directory() {
        Path nestedDir = tempDir.resolve("nested").resolve("traces");
        TraceFileExporter exporter = new TraceFileExporter(nestedDir.toString(), 7);
        assertTrue(Files.isDirectory(nestedDir), "exporter should create root directory");
    }

    @Test
    @DisplayName("null rootDir defaults to ./traces")
    void test_null_root_dir_defaults_to_traces() {
        TraceFileExporter exporter = new TraceFileExporter(null, 7);
        assertNotNull(exporter);
    }

    // ================================================================
    // Helpers
    // ================================================================

    private SpanData makeFinishedSpan(String name) {
        io.opentelemetry.api.trace.Tracer tracer = provider.get("ut");
        io.opentelemetry.api.trace.Span span = tracer.spanBuilder(name)
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
        span.setAttribute("session.id", "sess-1");
        span.setStatus(StatusCode.OK);
        span.end();
        provider.forceFlush();
        return findSpanByName(name);
    }

    private SpanData makeFinishedSpanWithoutSession(String name) {
        io.opentelemetry.api.trace.Tracer tracer = provider.get("ut");
        io.opentelemetry.api.trace.Span span = tracer.spanBuilder(name)
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
        span.setStatus(StatusCode.OK);
        span.end();
        provider.forceFlush();
        return findSpanByName(name);
    }

    private SpanData findSpanByName(String name) {
        for (SpanData s : inMemoryExporter.getFinishedSpanItems()) {
            if (name.equals(s.getName())) {
                return s;
            }
        }
        throw new AssertionError("span not found: " + name
                + ", available: " + inMemoryExporter.getFinishedSpanItems());
    }

    private Path dayFile() {
        return tempDir.resolve("traces-" + LocalDate.now().format(DATE_FMT) + ".jsonl");
    }

    private List<JsonNode> readSpans(Path file) throws IOException {
        List<JsonNode> spans = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            spans.add(mapper.readTree(trimmed));
        }
        return spans;
    }

    private JsonNode findSpanJson(List<JsonNode> spans, String name) {
        for (JsonNode s : spans) {
            if (name.equals(s.get("name").asText())) {
                return s;
            }
        }
        throw new AssertionError("span not found: " + name);
    }

    private boolean isHex(String s) {
        for (char c : s.toCharArray()) {
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) {
                return false;
            }
        }
        return true;
    }

    private void invokeCleanup(TraceFileExporter exporter) {
        try {
            Method m = TraceFileExporter.class.getDeclaredMethod("cleanupOldFiles");
            m.setAccessible(true);
            m.invoke(exporter);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("cleanupOldFiles not accessible", e);
        }
    }
}
