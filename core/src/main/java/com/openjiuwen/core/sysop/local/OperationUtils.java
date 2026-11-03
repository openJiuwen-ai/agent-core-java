/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.local;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code OperationUtils} in
 * {@code openjiuwen/core/sys_operation/local/utils.py}.
 */
public final class OperationUtils {

    private static final LoggerProtocol LOGGER = Loggers.SYS_OPERATION;

    private OperationUtils() {
    }

    public static CompletableFuture<String> createTmpFile(String fileContent, String fileSuffix) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path path = Files.createTempFile("openjiuwen-", fileSuffix);
                Files.writeString(path, fileContent != null ? fileContent : "", StandardCharsets.UTF_8);
                return path.toAbsolutePath().toString();
            } catch (Exception exception) {
                LOGGER.warning("Failed to create tmp file: {}", exception.getMessage());
                return null;
            }
        });
    }

    public static CompletableFuture<Boolean> deleteTmpFile(String filePath) {
        return CompletableFuture.supplyAsync(() -> {
            if (filePath == null || filePath.isBlank()) {
                return false;
            }
            try {
                Path path = Path.of(filePath);
                if (!Files.exists(path) || !Files.isRegularFile(path)) {
                    return false;
                }
                Files.delete(path);
                return true;
            } catch (Exception exception) {
                LOGGER.warning("Failed to delete tmp file: {}", exception.getMessage());
                return false;
            }
        });
    }

    public static Map<String, String> prepareEnvironment(Map<String, String> customEnv) {
        Map<String, String> environment = new LinkedHashMap<>(System.getenv());
        if (customEnv != null) {
            environment.putAll(customEnv);
        }
        return environment;
    }

    public static AsyncProcessHandler createHandler(Process process, int chunkSize, String encoding, int timeout) {
        return new AsyncProcessHandler(process, chunkSize, encoding, timeout);
    }

    public static AsyncProcessHandler createHandler(Process process, String encoding, int timeout) {
        return createHandler(process, 1024, encoding, timeout);
    }

    public static AsyncProcessHandler createHandler(Process process) {
        return createHandler(process, 1024, "utf-8", 300);
    }
}
