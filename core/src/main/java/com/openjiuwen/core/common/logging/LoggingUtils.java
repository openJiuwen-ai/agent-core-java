/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.security.PathChecker;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Logging utility functions.
 * <p>
 * Mirrors Python's {@code openjiuwen/core/common/logging/utils.py}.
 * </p>
 */
public final class LoggingUtils {

    private static final String DEFAULT_TRACE_ID = "default_trace_id";
    private static final long DEFAULT_LOG_MAX_BYTES = 100L * 1024L * 1024L;

    private static final InheritableThreadLocal<String> TRACE_ID_CONTEXT =
            new InheritableThreadLocal<>() {
                @Override
                protected String initialValue() {
                    return DEFAULT_TRACE_ID;
                }
            };

    private static final InheritableThreadLocal<String> MEMBER_ID_CONTEXT =
            new InheritableThreadLocal<>() {
                @Override
                protected String initialValue() {
                    return "";
                }
            };

    private LoggingUtils() {
    }

    public static void setSessionId() {
        setSessionId(DEFAULT_TRACE_ID);
    }

    public static void setSessionId(String traceId) {
        TRACE_ID_CONTEXT.set(traceId == null ? DEFAULT_TRACE_ID : traceId);
    }

    public static String getSessionId() {
        String traceId = TRACE_ID_CONTEXT.get();
        return traceId == null ? DEFAULT_TRACE_ID : traceId;
    }

    public static void setMemberId(String memberId) {
        MEMBER_ID_CONTEXT.set(memberId == null ? "" : memberId);
    }

    public static String getMemberId() {
        String memberId = MEMBER_ID_CONTEXT.get();
        return memberId == null ? "" : memberId;
    }

    public static int getLogMaxBytes(Object maxBytesConfig) {
        long maxBytes;
        try {
            maxBytes = Long.parseLong(String.valueOf(maxBytesConfig));
        } catch (NumberFormatException | NullPointerException exception) {
            throw ErrorHelper.buildError(
                    StatusCode.COMMON_LOG_CONFIG_INVALID,
                    "error_msg",
                    "invalid max_bytes configuration: " + maxBytesConfig + ", error: " + exception
            );
        }
        if (maxBytes <= 0 || maxBytes > DEFAULT_LOG_MAX_BYTES) {
            maxBytes = DEFAULT_LOG_MAX_BYTES;
        }
        return Math.toIntExact(maxBytes);
    }

    public static String normalizeAndValidateLogPath(Object pathValue) {
        String pathString = coercePathString(pathValue);
        if (pathString.trim().isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.COMMON_LOG_PATH_INVALID,
                    "error_msg",
                    "the path_str is " + pathString
            );
        }

        String realPath = normalizePath(pathString);
        if (PathChecker.isSensitivePath(realPath)) {
            throw ErrorHelper.buildError(
                    StatusCode.COMMON_LOG_PATH_INVALID,
                    "error_msg",
                    "the real_path is " + realPath
            );
        }
        return realPath;
    }

    private static String coercePathString(Object pathValue) {
        if (pathValue instanceof String stringValue) {
            return stringValue;
        }
        if (pathValue instanceof Path path) {
            return path.toString();
        }
        if (pathValue instanceof File file) {
            return file.getPath();
        }
        throw ErrorHelper.buildError(
                StatusCode.COMMON_LOG_PATH_INVALID,
                "error_msg",
                "the path_value is " + pathValue
        );
    }

    private static String normalizePath(String pathString) {
        String expanded = expandUser(pathString);
        try {
            return Path.of(expanded).toRealPath().toString();
        } catch (IOException exception) {
            try {
                return Path.of(expanded).toAbsolutePath().normalize().toString();
            } catch (InvalidPathException invalidPathException) {
                throw ErrorHelper.buildError(
                        StatusCode.COMMON_LOG_PATH_INVALID,
                        "error_msg",
                        "the path_str is " + pathString
                );
            }
        } catch (InvalidPathException invalidPathException) {
            throw ErrorHelper.buildError(
                    StatusCode.COMMON_LOG_PATH_INVALID,
                    "error_msg",
                    "the path_str is " + pathString
            );
        }
    }

    private static String expandUser(String pathString) {
        if (pathString.startsWith("~")) {
            return System.getProperty("user.home") + pathString.substring(1);
        }
        return pathString;
    }
}
