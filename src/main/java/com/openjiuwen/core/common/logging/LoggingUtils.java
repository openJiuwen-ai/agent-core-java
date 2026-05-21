/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.security.PathChecker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Logging utility functions.
 * <p>
 * Uses {@link ThreadLocal} (or {@link InheritableThreadLocal}) to maintain trace/session IDs
 * across threads — the Java equivalent of Python's {@code contextvars.ContextVar}.
 */
public final class LoggingUtils {

    private static final String DEFAULT_TRACE_ID = "default_trace_id";

    /**
     * InheritableThreadLocal so that child threads (virtual-thread or platform-thread)
     * inherit the parent's trace ID automatically.
     */
    private static final InheritableThreadLocal<String> TRACE_ID_CONTEXT =
        new InheritableThreadLocal<>() {
            @Override
            protected String initialValue() {
                return DEFAULT_TRACE_ID;
            }
        };

    private LoggingUtils() {
    }

    /** Set trace / session ID in current thread context. */
    public static void setSessionId(String traceId) {
        TRACE_ID_CONTEXT.set(traceId != null ? traceId : DEFAULT_TRACE_ID);
    }

    /** Get trace / session ID from current thread context. */
    public static String getSessionId() {
        String id = TRACE_ID_CONTEXT.get();
        return id != null ? id : DEFAULT_TRACE_ID;
    }

    /** Clear the current thread's trace ID (useful for thread-pool cleanup). */
    public static void clearSessionId() {
        TRACE_ID_CONTEXT.remove();
    }

    /**
     * Parse and validate max_bytes config value.
     *
     * @param maxBytesConfig raw config value
     * @return validated max bytes (capped at 100 MB)
     * @throws IllegalArgumentException if the value is not a valid integer
     */
    public static int getLogMaxBytes(Object maxBytesConfig) {
        int maxBytes;
        try {
            maxBytes = Integer.parseInt(String.valueOf(maxBytesConfig));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid max_bytes configuration: " + maxBytesConfig, e);
        }
        int defaultLogMaxBytes = 100 * 1024 * 1024; // 100 MB
        if (maxBytes <= 0 || maxBytes > defaultLogMaxBytes) {
            maxBytes = defaultLogMaxBytes;
        }
        return maxBytes;
    }

    /**
     * Normalize log path (resolve to real path) and validate it is not a sensitive path.
     * <p>
     * Mirrors Python's {@code normalize_and_validate_log_path()}.
     *
     * @param pathValue the raw path value (String or Path)
     * @return the normalized, validated real path string
     * @throws RuntimeException if the path is invalid, empty, or points to a sensitive location
     */
    public static String normalizeAndValidateLogPath(Object pathValue) {
        if (pathValue == null) {
            throw ErrorHelper.buildError(StatusCode.COMMON_LOG_PATH_INVALID,
                    "error_msg", "the path_value is null");
        }

        String pathStr = pathValue.toString().trim();
        if (pathStr.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.COMMON_LOG_PATH_INVALID,
                    "error_msg", "the path_str is empty");
        }

        String realPath;
        try {
            Path path = Paths.get(pathStr).toRealPath();
            realPath = path.toString();
        } catch (IOException | IllegalArgumentException e) {
            try {
                realPath = Paths.get(pathStr).toAbsolutePath().normalize().toString();
            } catch (Exception ex) {
                throw ErrorHelper.buildError(StatusCode.COMMON_LOG_PATH_INVALID,
                        "error_msg", "cannot resolve path: " + pathStr);
            }
        }

        if (PathChecker.isSensitivePath(realPath)) {
            throw ErrorHelper.buildError(StatusCode.COMMON_LOG_PATH_INVALID,
                    "error_msg", "the real_path is sensitive: " + realPath);
        }

        return realPath;
    }

    // ==================== Log Type Label Resolution ====================

    /**
     * Map internal logger types to the label rendered in outputs.
     * <p>
     * Mirrors Python's {@code resolve_log_type_label()} from base_impl.py.
     *
     * @param logType the internal log type string
     * @return the label to render in outputs
     */
    public static String resolveLogTypeLabel(String logType) {
        if ("performance".equals(logType)) {
            return "perf";
        }
        return logType;
    }

    // ==================== Log Filename Formatting ====================

    /**
     * Apply the configured filename pattern to a log file path.
     * <p>
     * Mirrors Python's {@code format_log_filename()} from base_impl.py.
     * <p>
     * Supported placeholders:
     * <ul>
     *   <li>{name} - base filename without extension</li>
     *   <li>{ext} - original extension including the dot</li>
     *   <li>{pid} - process ID</li>
     *   <li>{timestamp} - YYYYMMDDHHMMSS</li>
     *   <li>{date} - YYYYMMDD</li>
     *   <li>{time} - HHMMSS</li>
     *   <li>{datetime} - YYYY-MM-DD_HH-MM-SS</li>
     * </ul>
     *
     * @param baseFilename the original filename (may include path)
     * @param pattern      the pattern to apply
     * @return the formatted filename
     */
    public static String formatLogFilename(String baseFilename, String pattern) {
        if (baseFilename == null || baseFilename.isEmpty()) {
            return baseFilename;
        }
        if (pattern == null || pattern.isEmpty()) {
            return baseFilename;
        }

        File file = new File(baseFilename);
        String dirPath = file.getParent();
        String fileName = file.getName();

        // Split name and extension
        String namePart;
        String ext;
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            namePart = fileName.substring(0, dotIndex);
            ext = fileName.substring(dotIndex); // includes the dot
        } else {
            namePart = fileName;
            ext = "";
        }

        // Build replacements map
        Instant now = Instant.now();
        DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .withZone(ZoneId.of("UTC"));
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
                .withZone(ZoneId.of("UTC"));
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HHmmss")
                .withZone(ZoneId.of("UTC"));
        DateTimeFormatter datetimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-MM-SS")
                .withZone(ZoneId.of("UTC"));

        Map<String, String> replacements = new HashMap<>();
        replacements.put("name", namePart);
        replacements.put("ext", ext);
        replacements.put("pid", ProcessHandle.current().pid() + "");
        replacements.put("timestamp", timestampFormatter.format(now));
        replacements.put("date", dateFormatter.format(now));
        replacements.put("time", timeFormatter.format(now));
        replacements.put("datetime", datetimeFormatter.format(now));

        // Apply pattern
        String formattedName = pattern;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            formattedName = formattedName.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        // Append extension if not in pattern and original had extension
        if (!pattern.contains("{ext}") && !ext.isEmpty() && !formattedName.endsWith(ext)) {
            formattedName = formattedName + ext;
        }

        // Reconstruct full path if directory was present
        if (dirPath != null && !dirPath.isEmpty()) {
            return new File(dirPath, formattedName).getPath();
        }
        return formattedName;
    }
}
