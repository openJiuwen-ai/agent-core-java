/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * File-based span exporter that appends OTLP-shaped JSON lines.
 *
 * <p>Mirrors Python's {@code TraceFileExporter} in
 * {@code openjiuwen/agent_teams/observability/file_exporter.py}.</p>
 *
 * <p>All spans are appended to a per-day file {@code traces-YYYY-MM-DD.jsonl}
 * under {@code rootDir}. Each line is a standalone OTLP JSON
 * {@code ExportTraceServiceRequest} carrying hex {@code traceId}/{@code spanId}.
 * {@code forceFlush}/{@code shutdown} are no-ops (writes are direct).</p>
 *
 * @since 0.1.14
 */
public final class TraceFileExporter {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final int CLEANUP_INTERVAL = 64;
    private static final long SECONDS_PER_DAY = 86_400L;

    private final Path rootDir;
    private final int retentionDays;
    private final Object lock = new Object();
    private final AtomicInteger writeCount = new AtomicInteger();

    public TraceFileExporter() {
        this("./traces", 7);
    }

    public TraceFileExporter(String rootDir, int retentionDays) {
        this.rootDir = Path.of(Objects.requireNonNullElse(rootDir, "./traces"));
        this.retentionDays = Math.max(0, retentionDays);
        try {
            Files.createDirectories(this.rootDir);
        } catch (IOException exception) {
            TEAM_LOGGER.warning("file_exporter: cannot create traces_dir={} - {}", this.rootDir, exception);
        }
    }

    public Path getRootDir() {
        return rootDir;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    /**
     * Append every ended span (one OTLP JSON line each) to today's file.
     *
     * @param spans ended telemetry spans
     */
    public void export(Collection<TelemetrySpan> spans) {
        if (spans == null || spans.isEmpty()) {
            maybeCleanup();
            return;
        }
        List<String> lines = new ArrayList<>();
        for (TelemetrySpan span : spans) {
            if (span == null || !span.isEnded()) {
                continue;
            }
            try {
                lines.add(MAPPER.writeValueAsString(encodeSpanLine(span)));
            } catch (IOException exception) {
                TEAM_LOGGER.warning("file_exporter: encode failed - {}", exception);
            }
        }
        if (!lines.isEmpty()) {
            Path filePath = rootDir.resolve("traces-" + DAY.format(Instant.now()) + ".jsonl");
            synchronized (lock) {
                try {
                    Files.write(
                            filePath,
                            lines.stream().map(line -> line + System.lineSeparator()).toList(),
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND
                    );
                } catch (IOException exception) {
                    TEAM_LOGGER.warning("file_exporter: append failed to {} - {}", filePath, exception);
                }
            }
        }
        maybeCleanup();
    }

    /**
     * No-op: {@link #export} writes straight to disk.
     *
     * @return always {@code true}
     */
    public boolean forceFlush() {
        return true;
    }

    /**
     * No-op: nothing buffered.
     */
    public void shutdown() {
        // intentionally empty
    }

    void cleanupOldFilesForTest() {
        cleanupOldFiles();
    }

    private void maybeCleanup() {
        if (writeCount.incrementAndGet() % CLEANUP_INTERVAL != 0) {
            return;
        }
        try {
            cleanupOldFiles();
        } catch (RuntimeException exception) {
            TEAM_LOGGER.warning("file_exporter: cleanup failed - {}", exception);
        }
    }

    private void cleanupOldFiles() {
        if (retentionDays <= 0) {
            return;
        }
        if (!Files.isDirectory(rootDir)) {
            return;
        }
        long cutoff = Instant.now().getEpochSecond() - ((long) retentionDays * SECONDS_PER_DAY);
        try (Stream<Path> entries = Files.list(rootDir)) {
            entries.filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            long mtime = Files.getLastModifiedTime(path).toInstant().getEpochSecond();
                            if (mtime < cutoff) {
                                Files.deleteIfExists(path);
                            }
                        } catch (IOException exception) {
                            TEAM_LOGGER.warning("file_exporter: cannot prune {} - {}", path, exception);
                        }
                    });
        } catch (IOException exception) {
            TEAM_LOGGER.warning("file_exporter: cannot list {} - {}", rootDir, exception);
        }
    }

    private static Map<String, Object> encodeSpanLine(TelemetrySpan span) {
        String traceId = randomHex(16);
        String spanId = randomHex(8);
        String parentSpanId = span.getParent() == null ? null : randomHex(8);

        Map<String, Object> spanNode = new LinkedHashMap<>();
        spanNode.put("traceId", traceId);
        spanNode.put("spanId", spanId);
        if (parentSpanId != null) {
            spanNode.put("parentSpanId", parentSpanId);
        }
        spanNode.put("name", span.getName());
        spanNode.put("kind", span.getKind() == null ? "INTERNAL" : span.getKind().name());
        spanNode.put("attributes", span.getAttributes());
        spanNode.put("status", Map.of(
                "code", span.getStatusCode() == null ? "UNSET" : span.getStatusCode().name(),
                "message", span.getStatusDescription() == null ? "" : span.getStatusDescription()
        ));

        Map<String, Object> scopeSpans = new LinkedHashMap<>();
        scopeSpans.put("scope", Map.of("name", "openjiuwen.agent_teams.observability"));
        scopeSpans.put("spans", List.of(spanNode));

        Map<String, Object> resourceSpans = new LinkedHashMap<>();
        resourceSpans.put("resource", Map.of("attributes", List.of()));
        resourceSpans.put("scopeSpans", List.of(scopeSpans));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("resourceSpans", List.of(resourceSpans));
        return root;
    }

    private static String randomHex(int byteCount) {
        byte[] bytes = new byte[byteCount];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
