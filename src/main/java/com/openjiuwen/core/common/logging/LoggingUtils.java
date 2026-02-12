// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.logging;

import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.security.PathChecker;

import java.nio.file.*;

/**
 * Logging utility functions
 * 
 * <p>Provides session ID management and log path validation.
 * Uses ThreadLocal for session ID storage to support multi-threaded environments.
 * 
 * @since 0.1.4
 */
public final class LoggingUtils {
    
    private static final ThreadLocal<String> traceIdContext = 
            ThreadLocal.withInitial(() -> "default_trace_id");
    
    private static final int DEFAULT_LOG_MAX_BYTES = 100 * 1024 * 1024; // 100MB
    
    private LoggingUtils() {
        // Utility class
    }
    
    /**
     * Set trace ID in current thread context
     * 
     * @param traceId the trace ID
     */
    public static void setSessionId(String traceId) {
        if (traceId == null) {
            traceId = "default_trace_id";
        }
        traceIdContext.set(traceId);
    }
    
    /**
     * Get trace ID from current thread context
     * 
     * @return the trace ID
     */
    public static String getSessionId() {
        return traceIdContext.get();
    }
    
    /**
     * Clear trace ID from current thread context
     * 
     * <p>Should be called when thread finishes to prevent memory leak.
     */
    public static void clearSessionId() {
        traceIdContext.remove();
    }
    
    /**
     * Get log max bytes from configuration
     * 
     * @param maxBytesConfig the configuration value
     * @return the max bytes (defaults to 100MB if invalid)
     */
    public static int getLogMaxBytes(Object maxBytesConfig) {
        try {
            int maxBytes = Integer.parseInt(String.valueOf(maxBytesConfig));
            
            if (maxBytes <= 0 || maxBytes > DEFAULT_LOG_MAX_BYTES) {
                return DEFAULT_LOG_MAX_BYTES;
            }
            
            return maxBytes;
        } catch (NumberFormatException e) {
            ErrorBuilder.raise(
                    StatusCode.COMMON_LOG_CONFIG_INVALID,
                    null,
                    null,
                    e,
                    java.util.Map.of("error_msg", "invalid max_bytes configuration: " + maxBytesConfig)
            );
            return DEFAULT_LOG_MAX_BYTES; // unreachable
        }
    }
    
    /**
     * Normalize and validate log path
     * 
     * <p>Checks if path is valid and not sensitive.
     * 
     * @param pathValue the path value
     * @return the normalized path
     * @throws com.openjiuwen.core.common.exception.BaseError if path is invalid or sensitive
     */
    public static String normalizeAndValidateLogPath(Object pathValue) {
        // Validate path type
        if (pathValue == null) {
            ErrorBuilder.raise(
                    StatusCode.COMMON_LOG_PATH_INVALID,
                    null,
                    null,
                    null,
                    java.util.Map.of("error_msg", "path_value is null")
            );
        }
        
        String pathStr = pathValue.toString();
        
        if (pathStr.trim().isEmpty()) {
            ErrorBuilder.raise(
                    StatusCode.COMMON_LOG_PATH_INVALID,
                    null,
                    null,
                    null,
                    java.util.Map.of("error_msg", "path_str is empty: " + pathStr)
            );
        }
        
        try {
            // Normalize path
            Path path = Paths.get(pathStr);
            String realPath = path.toRealPath(LinkOption.NOFOLLOW_LINKS).toString();
            
            // Check if path is sensitive
            if (PathChecker.checkSensitivePath(realPath)) {
                ErrorBuilder.raise(
                        StatusCode.COMMON_LOG_PATH_INVALID,
                        null,
                        null,
                        null,
                        java.util.Map.of("error_msg", "the path is sensitive: " + realPath)
                );
            }
            
            return realPath;
        } catch (InvalidPathException e) {
            ErrorBuilder.raise(
                    StatusCode.COMMON_LOG_PATH_INVALID,
                    null,
                    null,
                    e,
                    java.util.Map.of("error_msg", "invalid path: " + pathStr)
            );
        } catch (NoSuchFileException e) {
            // Path doesn't exist yet - use absolute path
            try {
                Path path = Paths.get(pathStr);
                return path.toAbsolutePath().normalize().toString();
            } catch (InvalidPathException ex) {
                ErrorBuilder.raise(
                        StatusCode.COMMON_LOG_PATH_INVALID,
                        null,
                        null,
                        ex,
                        java.util.Map.of("error_msg", "invalid path: " + pathStr)
                );
            }
        } catch (Exception e) {
            ErrorBuilder.raise(
                    StatusCode.COMMON_LOG_PATH_INVALID,
                    null,
                    null,
                    e,
                    java.util.Map.of("error_msg", "failed to normalize path: " + pathStr)
            );
        }
        
        return pathStr; // unreachable
    }
}

