/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Parity tests for {@link TraceFileExporter}.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent_teams/observability/test_file_exporter.py}.</p>
 */
class TraceFileExporterTest {

    @TempDir
    Path tempDir;

    @Test
    void exportWritesOtLpJsonLineWithHexIds() throws Exception {
        TraceFileExporter exporter = new TraceFileExporter(tempDir.toString(), 7);
        TelemetrySpan span = new TelemetrySpan("llm.invoke", TelemetrySpan.Kind.CLIENT);
        span.setAttribute(ObservabilitySemconv.GEN_AI_REQUEST_MODEL, "fake");
        span.end();

        exporter.export(List.of(span));

        List<Path> files;
        try (Stream<Path> stream = Files.list(tempDir)) {
            files = stream.filter(path -> path.getFileName().toString().startsWith("traces-")).toList();
        }
        assertThat(files).hasSize(1);
        String line = Files.readString(files.get(0)).trim();
        assertThat(line).contains("\"resourceSpans\"");
        assertThat(line).contains("\"traceId\"");
        assertThat(line).contains("llm.invoke");
        assertThat(line).doesNotContain("\n");
    }

    @Test
    void fileBackedTracerExportsOnEnd() throws Exception {
        TraceFileExporter exporter = new TraceFileExporter(tempDir.toString(), 7);
        TelemetryTracer.FileBacked tracer = new TelemetryTracer.FileBacked(exporter);

        TelemetrySpan span = tracer.startSpan("agent.invoke", TelemetrySpan.Kind.INTERNAL);
        span.setAttribute(ObservabilitySemconv.AT_EVENT_TYPE, "created");
        span.end();

        List<Path> files;
        try (Stream<Path> stream = Files.list(tempDir)) {
            files = stream.filter(path -> path.getFileName().toString().endsWith(".jsonl")).toList();
        }
        assertThat(files).hasSize(1);
        assertThat(Files.readString(files.get(0))).contains("agent.invoke");
    }

    @Test
    void forceFlushAndShutdownAreNoOps() {
        TraceFileExporter exporter = new TraceFileExporter(tempDir.toString(), 7);
        assertThat(exporter.forceFlush()).isTrue();
        exporter.shutdown();
    }
}
