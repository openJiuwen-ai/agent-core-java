/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.harness.workspace.Workspace;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Coding Memory tools for JiuWenClaw.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.memory.lite.coding_memory_tools}.
 */
public final class CodingMemoryTools {

    /** Default directory name for coding memory */
    public static final String CODING_MEMORY_DIR = "coding_memory";

    /** Maximum lines to read when extracting context */
    public static final int MAX_INDEX_LINES = 50;

    // Runtime context (thread-safe)
    private static volatile CodingMemoryToolContext defaultContext = null;
    private static volatile MemoryIndexManager codingMemoryManager = null;
    private static volatile Workspace codingMemoryWorkspace = null;
    private static volatile Object codingMemorySysOperation = null;

    // File-level lock registry
    private static final ConcurrentHashMap<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>();

    // Index lock for MEMORY.md file
    private static final ReentrantLock memoryIndexLock = new ReentrantLock();

    private CodingMemoryTools() {
    }

    /**
     * Bind coding memory runtime context.
     *
     * @param workspace       the workspace
     * @param sysOperation    the system operation
     * @param codingMemoryDir the coding memory directory path
     */
    public static void bindCodingMemoryRuntime(Workspace workspace, Object sysOperation, String codingMemoryDir) {
        defaultContext = new CodingMemoryToolContext(workspace, sysOperation, codingMemoryDir);
        codingMemoryWorkspace = workspace;
        codingMemorySysOperation = sysOperation;
        codingMemoryManager = new MemoryIndexManager(codingMemoryDir);
    }

    /**
     * Clear coding memory runtime context.
     */
    public static void clearCodingMemoryRuntime() {
        defaultContext = null;
        codingMemoryWorkspace = null;
        codingMemorySysOperation = null;
        codingMemoryManager = null;
        fileLocks.clear();
    }

    /**
     * Get the current coding memory context.
     */
    public static CodingMemoryToolContext getCodingMemoryContext() {
        return defaultContext;
    }

    /**
     * Get the memory index manager.
     */
    public static MemoryIndexManager getMemoryIndexManager() {
        return codingMemoryManager;
    }

    /**
     * Validate coding memory path.
     *
     * @param path the path to validate
     * @return validated path
     */
    public static String validateCodingMemoryPath(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        // Normalize path
        String normalizedPath = path.replace("\\", "/");

        // Ensure it's within coding memory directory
        if (defaultContext != null) {
            String baseDir = defaultContext.getCodingMemoryDir();
            if (!normalizedPath.startsWith(baseDir)) {
                // If path is relative, prepend base directory
                if (!normalizedPath.startsWith("/") && !normalizedPath.contains(":")) {
                    normalizedPath = baseDir + "/" + normalizedPath;
                }
            }
        }

        return normalizedPath;
    }

    /**
     * Upsert memory index entry.
     *
     * @param filePath  the file path
     * @param frontmatter the frontmatter map
     */
    public static void upsertMemoryIndex(String filePath, Map<String, String> frontmatter) {
        if (codingMemoryManager == null) {
            Loggers.MEMORY.warn("Coding memory manager not initialized");
            return;
        }

        memoryIndexLock.lock();
        try {
            codingMemoryManager.upsertIndex(filePath, frontmatter);
        } finally {
            memoryIndexLock.unlock();
        }
    }

    /**
     * Remove from memory index.
     *
     * @param filePath the file path to remove
     */
    public static void removeFromMemoryIndex(String filePath) {
        if (codingMemoryManager == null) {
            Loggers.MEMORY.warn("Coding memory manager not initialized");
            return;
        }

        memoryIndexLock.lock();
        try {
            codingMemoryManager.removeFromIndex(filePath);
        } finally {
            memoryIndexLock.unlock();
        }
    }

    /**
     * Count memory files asynchronously.
     *
     * @return count of memory files
     */
    public static int countMemoryFiles() {
        if (defaultContext == null) {
            return 0;
        }

        String dirPath = defaultContext.getCodingMemoryDir();
        try {
            File dir = new File(dirPath);
            if (!dir.exists() || !dir.isDirectory()) {
                return 0;
            }

            File[] files = dir.listFiles((d, name) ->
                    name.endsWith(".md") && !name.equals("MEMORY.md"));
            return files != null ? files.length : 0;
        } catch (Exception e) {
            Loggers.MEMORY.error("Failed to count memory files: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Read file safely with error handling.
     *
     * @param filePath the file path
     * @return file content, or empty string if read fails
     */
    public static String readFileSafe(String filePath) {
        try {
            String validatedPath = validateCodingMemoryPath(filePath);
            return Files.readString(Paths.get(validatedPath));
        } catch (Exception e) {
            Loggers.MEMORY.error("Failed to read file {}: {}", filePath, e.getMessage());
            return "";
        }
    }

    /**
     * Get file lock for a specific file path.
     *
     * @param filePath the file path
     * @return the lock for this file
     */
    public static ReentrantLock getFileLock(String filePath) {
        return fileLocks.computeIfAbsent(filePath, k -> new ReentrantLock());
    }

    /**
     * Read with context (extract first MAX_INDEX_LINES lines).
     *
     * @param filePath the file path
     * @return content with context preview
     */
    public static String codingMemoryReadWithContext(String filePath) {
        String content = readFileSafe(filePath);
        if (content.isEmpty()) {
            return "";
        }

        // Extract first MAX_INDEX_LINES lines for context preview
        String[] lines = content.split("\n");
        int limit = Math.min(lines.length, MAX_INDEX_LINES);
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            context.append(lines[i]);
            if (i < limit - 1) {
                context.append("\n");
            }
        }
        return context.toString();
    }
}
