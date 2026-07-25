/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * File-based span exporter that appends spans as JSON lines.
 *
 * <p>Each span is serialized as a single JSON object on one line in
 * {@code <root_dir>/traces-<YYYY-MM-DD>.jsonl}. The JSON contains
 * traceId, spanId, parentSpanId, name, kind, start/end timestamps,
 * status, and attributes — enough for downstream tools to reconstruct
 * the trace tree.</p>
 *
 * <p>Pair this exporter with {@code BatchSpanProcessor} so span-end does
 * not block the business thread. {@code export()} appends straight to
 * disk with no in-memory buffer; {@code flush()} and {@code shutdown()}
 * are no-ops.</p>
 *
 * <p>Trace files whose last-modified time predicates
 * {@code retentionDays} are lazily pruned at most every
 * {@link #CLEANUP_INTERVAL} exports.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.observability.file_exporter.TraceFileExporter}.</p>
 *
 * @since 0.1.7
 */
public class TraceFileExporter implements SpanExporter {
    private static final Logger LOG = LoggerFactory.getLogger(TraceFileExporter.class);

    /** Cleanup runs at most every N export cycles. */
    private static final int CLEANUP_INTERVAL = 64;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Path rootDir;
    private final int retentionDays;
    private final Object lock = new Object();
    private final AtomicInteger writeCount = new AtomicInteger(0);

    /**
     * Construct a file exporter.
     *
     * @param rootDir       the directory to write trace files into
     * @param retentionDays trace files older than this many days are pruned; {@code <= 0} disables pruning
     * @since 0.1.7
     */
    public TraceFileExporter(String rootDir, int retentionDays) {
        this.rootDir = Paths.get(rootDir != null ? rootDir : "./traces");
        this.retentionDays = Math.max(0, retentionDays);
        try {
            Files.createDirectories(this.rootDir);
        } catch (IOException e) {
            LOG.warn("file_exporter: cannot create traces_dir={} - {}", this.rootDir, e.getMessage());
        }
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        if (spans == null || spans.isEmpty()) {
            return CompletableResultCode.ofSuccess();
        }

        String fileName = "traces-" + LocalDate.now().format(DATE_FMT) + ".jsonl";
        Path filePath = rootDir.resolve(fileName);

        StringBuilder sb = new StringBuilder();
        for (SpanData span : spans) {
            sb.append(serializeSpan(span));
            sb.append('\n');
        }

        try {
            synchronized (lock) {
                Files.write(filePath, sb.toString().getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            LOG.warn("file_exporter: append failed to {} - {}", filePath, e.getMessage());
            return CompletableResultCode.ofFailure();
        }

        maybeCleanup();
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }

    /**
     * Serialize a span to a compact JSON line.
     *
     * @param span the span data
     * @return a JSON string
     * @since 0.1.7
     */
    private static String serializeSpan(SpanData span) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"traceId\":\"").append(span.getTraceId()).append('"');
        sb.append(",\"spanId\":\"").append(span.getSpanId()).append('"');

        String parentSpanId = span.getParentSpanId();
        if (parentSpanId != null && !parentSpanId.isEmpty()) {
            sb.append(",\"parentSpanId\":\"").append(parentSpanId).append('"');
        }

        sb.append(",\"name\":\"").append(escapeJson(span.getName())).append('"');
        sb.append(",\"kind\":\"").append(span.getKind()).append('"');
        sb.append(",\"startEpochNanos\":").append(span.getStartEpochNanos());
        sb.append(",\"endEpochNanos\":").append(span.getEndEpochNanos());
        sb.append(",\"status\":\"").append(span.getStatus().getStatusCode()).append('"');

        String statusDesc = span.getStatus().getDescription();
        if (statusDesc != null && !statusDesc.isEmpty()) {
            sb.append(",\"statusDescription\":\"").append(escapeJson(statusDesc)).append('"');
        }

        Attributes attrs = span.getAttributes();
        if (!attrs.isEmpty()) {
            sb.append(",\"attributes\":{");
            boolean isFirst = true;
            for (var entry : attrs.asMap().entrySet()) {
                if (!isFirst) {
                    sb.append(',');
                }
                isFirst = false;
                sb.append('"').append(escapeJson(entry.getKey().getKey())).append("\":");
                sb.append(attributeToJson(entry.getKey(), entry.getValue()));
            }
            sb.append('}');
        }

        sb.append('}');
        return sb.toString();
    }

    /**
     * Convert an attribute value to its JSON representation.
     *
     * @param key   the attribute key
     * @param value the attribute value
     * @return JSON string fragment
     * @since 0.1.7
     */
    private static String attributeToJson(AttributeKey<?> key, Object value) {
        if (value == null) {
            return "null";
        }
        switch (key.getType()) {
            case STRING:
                return "\"" + escapeJson(value.toString()) + "\"";
            case BOOLEAN:
                return value.toString();
            case LONG:
                return value.toString();
            case DOUBLE:
                return value.toString();
            default:
                return "\"" + escapeJson(value.toString()) + "\"";
        }
    }

    /**
     * Escape special JSON characters in a string.
     *
     * @param s the raw string
     * @return the escaped string
     * @since 0.1.7
     */
    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * Prune old trace files periodically.
     *
     * @since 0.1.7
     */
    private void maybeCleanup() {
        if (writeCount.incrementAndGet() % CLEANUP_INTERVAL != 0) {
            return;
        }
        if (retentionDays <= 0) {
            return;
        }
        try {
            cleanupOldFiles();
        } catch (IOException e) {
            LOG.warn("file_exporter: cleanup failed - {}", e.getMessage());
        }
    }

    /**
     * Delete trace files older than the retention cutoff.
     *
     * @throws IOException if listing the directory fails
     * @since 0.1.7
     */
    private void cleanupOldFiles() throws IOException {
        long cutoffMillis = System.currentTimeMillis()
                - (long) retentionDays * 86_400_000L;

        try (Stream<Path> stream = Files.list(rootDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                    .filter(Files::isRegularFile)
                    .forEach(p -> {
                        try {
                            if (Files.getLastModifiedTime(p).toMillis() < cutoffMillis) {
                                Files.deleteIfExists(p);
                            }
                        } catch (IOException e) {
                            LOG.warn("file_exporter: cannot prune {} - {}", p, e.getMessage());
                        }
                    });
        }
    }
}
