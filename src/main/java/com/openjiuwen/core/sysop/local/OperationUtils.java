/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.sysop.local;

import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.common.logging.Loggers;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for common subprocess operation helper methods.
 * <p>
 * Mirrors Python's {@code OperationUtils} in {@code local/utils.py}.
 */
public final class OperationUtils {

    private OperationUtils() {
    }

    /**
     * Create a unique temporary file and write content to it.
     *
     * @param fileContent content to be written into the temporary file (UTF-8 encoded)
     * @param fileSuffix  suffix of the temporary file (e.g., ".py", ".sh", ".txt")
     * @return absolute path of the created temporary file, or null if creation fails
     */
    public static String createTmpFile(String fileContent, String fileSuffix) {
        try {
            Path tmpFile = Files.createTempFile("sysop_", fileSuffix);
            Files.writeString(tmpFile, fileContent, StandardCharsets.UTF_8);
            return tmpFile.toAbsolutePath().toString();
        } catch (IOException e) {
            Loggers.SYS_OPERATION.warning("Failed to create tmp file",
                    LogEventType.SYS_OP_ERROR.getValue(), e);
            return null;
        }
    }

    /**
     * Delete the specified temporary file.
     *
     * @param filePath absolute path of the temporary file to be deleted
     * @return true if the file was deleted successfully, false otherwise
     */
    public static boolean deleteTmpFile(String filePath) {
        Path path = Path.of(filePath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return false;
        }
        try {
            Files.delete(path);
            return true;
        } catch (IOException e) {
            Loggers.SYS_OPERATION.warning("Failed to delete tmp file",
                    LogEventType.SYS_OP_ERROR.getValue(), e);
            return false;
        }
    }

    /**
     * Create a merged environment dictionary for subprocess execution.
     *
     * @param customEnv optional custom environment variables to add/override (can be null)
     * @return merged environment map (system env + custom env)
     */
    public static Map<String, String> prepareEnvironment(Map<String, String> customEnv) {
        Map<String, String> env = new HashMap<>(System.getenv());
        if (customEnv != null) {
            env.putAll(customEnv);
        }
        return env;
    }

    /**
     * Factory method to create a ProcessHandler instance.
     *
     * @param process   Java Process instance to monitor
     * @param chunkSize max char size for each stream read operation (default: 1024)
     * @param encoding  text encoding (default: UTF-8)
     * @param timeout   overall timeout in seconds (default: 300)
     * @return initialized ProcessHandler
     */
    public static ProcessHandler createHandler(Process process, int chunkSize,
                                               Charset encoding, int timeout) {
        return new ProcessHandler(process, chunkSize, encoding, timeout);
    }

    /**
     * Factory method to create a ProcessHandler with defaults.
     *
     * @param process Java Process instance to monitor
     * @return initialized ProcessHandler with default settings
     */
    public static ProcessHandler createHandler(Process process) {
        return new ProcessHandler(process);
    }
}
