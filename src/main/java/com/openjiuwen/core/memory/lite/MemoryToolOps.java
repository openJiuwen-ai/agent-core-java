/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.harness.workspace.Workspace;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * General lite memory tool implementations.
 * <p>
 * Mirrors Python's {@code memory_tool_ops} module from
 * {@code openjiuwen.core.memory.lite.memory_tool_ops}.
 * </p>
 *
 * <p>Provides utility methods for memory path validation, search, read, write, and edit operations.</p>
 */
public final class MemoryToolOps {

    private static final LoggerProtocol LOG = Loggers.MEMORY;

    /** Pattern for daily memory files: YYYY-MM-DD.md */
    private static final Pattern DAILY_MEMORY_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}\\.md$");

    private MemoryToolOps() {
        // Utility class - no instantiation
    }

    /**
     * Validate that path is within the memory directory. Workspace is required.
     * <p>
     * Mirrors Python's {@code validate_memory_path} function.
     * </p>
     *
     * @param path     the relative path to validate
     * @param workspace the workspace for path resolution
     * @return a tuple of (isValid, resolvedPathOrErrorMessage)
     */
    public static ValidationResult validateMemoryPath(String path, Workspace workspace) {
        if (workspace == null) {
            return new ValidationResult(false, "Workspace not initialized");
        }
        if (path == null || path.contains("..") || new File(path).isAbsolute()) {
            return new ValidationResult(false, "Invalid path: directory traversal not allowed");
        }

        String basename = new File(path).getName();
        // Use workspace.resolve() to get the full path
        // Memory directory is typically at workspace_root/memory
        String memoryDir = workspace.resolve("memory").toString();

        String resolvedPath;
        if ("USER.md".equals(basename)) {
            resolvedPath = workspace.resolve("USER.md").toString();
        } else if ("MEMORY.md".equals(basename)) {
            // MEMORY.md is typically at memory/MEMORY.md
            resolvedPath = memoryDir + File.separator + "MEMORY.md";
        } else if (DAILY_MEMORY_PATTERN.matcher(basename).matches()) {
            // Daily memory files are at memory/daily_memory/YYYY-MM-DD.md
            resolvedPath = memoryDir + File.separator + "daily_memory" + File.separator + basename;
        } else {
            // Default to memory directory
            resolvedPath = memoryDir + File.separator + basename;
        }

        if (resolvedPath == null) {
            return new ValidationResult(false, "Cannot resolve path: " + path);
        }
        return new ValidationResult(true, resolvedPath);
    }

    /**
     * Search memory with context.
     * <p>
     * Mirrors Python's {@code memory_search_with_context} async function.
     * </p>
     *
     * @param ctx        the memory tool context
     * @param query      the search query
     * @param maxResults maximum number of results (optional)
     * @param minScore   minimum score threshold (optional)
     * @param sessionKey session key for context (optional)
     * @return CompletableFuture with search results
     */
    public static CompletableFuture<Map<String, Object>> memorySearchWithContext(
            MemoryToolContext ctx,
            String query,
            Integer maxResults,
            Double minScore,
            String sessionKey) {

        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new LinkedHashMap<>();

            if (ctx == null) {
                result.put("results", Collections.emptyList());
                result.put("disabled", true);
                result.put("error", "Memory manager not available");
                return result;
            }

            // Check if manager is available
            if (!ctx.hasActiveManager()) {
                result.put("results", Collections.emptyList());
                result.put("disabled", true);
                result.put("error", "Memory manager not initialized");
                return result;
            }

            try {
                // Build options map
                Map<String, Object> opts = new LinkedHashMap<>();
                if (maxResults != null) {
                    opts.put("max_results", maxResults);
                }
                if (minScore != null) {
                    opts.put("min_score", minScore);
                }
                if (sessionKey != null) {
                    opts.put("session_key", sessionKey);
                }

                // Perform search - using the context's manager
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> searchResults = invokeManagerSearch(ctx, query, opts);

                // Add citation to each result
                for (Map<String, Object> r : searchResults) {
                    Integer startLine = (Integer) r.get("start_line");
                    Integer endLine = (Integer) r.get("end_line");
                    String rPath = (String) r.get("path");
                    if (startLine != null && endLine != null && rPath != null) {
                        if (startLine.equals(endLine)) {
                            r.put("citation", rPath + "#L" + startLine);
                        } else {
                            r.put("citation", rPath + "#L" + startLine + "-L" + endLine);
                        }
                    }
                }

                Map<String, Object> status = getManagerStatus(ctx);
                result.put("query", query);
                result.put("max_results", maxResults);
                result.put("min_score", minScore);
                result.put("results", searchResults);
                result.put("provider", status.get("provider"));
                result.put("model", status.get("model"));
                result.put("disabled", false);

            } catch (Exception e) {
                LOG.error("Memory search failed: {}", e.getMessage(), e);
                result.put("results", Collections.emptyList());
                result.put("disabled", true);
                result.put("error", e.getMessage());
            }

            return result;
        });
    }

    /**
     * Get memory content with context.
     * <p>
     * Mirrors Python's {@code memory_get_with_context} async function.
     * </p>
     *
     * @param ctx      the memory tool context
     * @param path     the memory file path
     * @param fromLine starting line number (optional, 1-based)
     * @param lines    number of lines to read (optional)
     * @return CompletableFuture with file content
     */
    public static CompletableFuture<Map<String, Object>> memoryGetWithContext(
            MemoryToolContext ctx,
            String path,
            Integer fromLine,
            Integer lines) {

        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", path);

            Workspace ws = (ctx != null) ? (Workspace) ctx.getWorkspace() : null;
            if (ws == null) {
                result.put("text", "");
                result.put("disabled", true);
                result.put("error", "Workspace not initialized");
                return result;
            }

            ValidationResult validation = validateMemoryPath(path, ws);
            if (!validation.isValid()) {
                result.put("text", "");
                result.put("disabled", true);
                result.put("error", validation.getMessage());
                return result;
            }

            String resolvedPath = validation.getMessage();

            if (ctx == null || !ctx.hasActiveManager()) {
                result.put("path", resolvedPath);
                result.put("text", "");
                result.put("disabled", true);
                result.put("error", "Memory manager not available");
                return result;
            }

            try {
                result = invokeManagerReadFile(ctx, resolvedPath, fromLine, lines);
                result.put("disabled", false);
            } catch (Exception e) {
                LOG.error("Memory get failed: {}", e.getMessage(), e);
                result.put("path", resolvedPath);
                result.put("text", "");
                result.put("disabled", true);
                result.put("error", e.getMessage());
            }

            return result;
        });
    }

    /**
     * Write memory content with context.
     * <p>
     * Mirrors Python's {@code write_memory_with_context} async function.
     * </p>
     *
     * @param ctx     the memory tool context
     * @param path    the memory file path
     * @param content the content to write
     * @param append  whether to append to existing file
     * @return CompletableFuture with write result
     */
    public static CompletableFuture<Map<String, Object>> writeMemoryWithContext(
            MemoryToolContext ctx,
            String path,
            String content,
            boolean append) {

        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", path);

            try {
                if (ctx == null) {
                    result.put("success", false);
                    result.put("error", "Workspace not initialized");
                    return result;
                }

                Workspace ws = (Workspace) ctx.getWorkspace();
                if (ws == null) {
                    result.put("success", false);
                    result.put("error", "Workspace not initialized");
                    return result;
                }

                ValidationResult validation = validateMemoryPath(path, ws);
                if (!validation.isValid()) {
                    result.put("success", false);
                    result.put("error", validation.getMessage());
                    return result;
                }

                String resolvedPath = validation.getMessage();

                // Use sysOperation to write file
                Object sysOp = ctx.getSysOperation();
                if (sysOp != null) {
                    boolean fileExisted = invokeFsWriteFile(sysOp, resolvedPath, content, append);
                    LOG.info("{} file: {}", append ? "Appended to" : "Wrote", resolvedPath);
                    result.put("success", true);
                    result.put("path", resolvedPath);
                    result.put("fullPath", resolvedPath);
                    result.put("appended", append);
                    result.put("fileExisted", fileExisted);
                } else {
                    LOG.error("Memory write failed, no available sys_operation");
                    result.put("success", false);
                    result.put("error", "Memory write failed, no available sys_operation");
                }
            } catch (Exception e) {
                LOG.error("Write failed: {}", e.getMessage(), e);
                result.put("success", false);
                result.put("error", e.getMessage());
            }

            return result;
        });
    }

    /**
     * Edit memory content with context.
     * <p>
     * Mirrors Python's {@code edit_memory_with_context} async function.
     * </p>
     *
     * @param ctx     the memory tool context
     * @param path    the memory file path
     * @param oldText the text to replace
     * @param newText the replacement text
     * @return CompletableFuture with edit result
     */
    public static CompletableFuture<Map<String, Object>> editMemoryWithContext(
            MemoryToolContext ctx,
            String path,
            String oldText,
            String newText) {

        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", path);

            try {
                Workspace ws = (ctx != null) ? (Workspace) ctx.getWorkspace() : null;
                if (ws == null) {
                    result.put("success", false);
                    result.put("error", "Workspace not initialized");
                    return result;
                }

                ValidationResult validation = validateMemoryPath(path, ws);
                if (!validation.isValid()) {
                    result.put("success", false);
                    result.put("error", validation.getMessage());
                    return result;
                }

                String resolvedPath = validation.getMessage();

                Object sysOp = (ctx != null) ? ctx.getSysOperation() : null;
                if (sysOp != null) {
                    // Read current content
                    String currentContent = invokeFsReadFile(sysOp, resolvedPath);

                    if (!currentContent.contains(oldText)) {
                        result.put("success", false);
                        result.put("error", "old_text not found in file. Use read_memory tool to check exact content.");
                        return result;
                    }

                    int occurrences = countOccurrences(currentContent, oldText);
                    if (occurrences > 1) {
                        result.put("success", false);
                        result.put("error", "old_text appears " + occurrences + " times in file. Be more specific.");
                        return result;
                    }

                    // Replace and write back
                    String newContent = currentContent.replaceFirst(Pattern.quote(oldText), newText);
                    invokeFsWriteFile(sysOp, resolvedPath, newContent, false);

                    LOG.info("Edited file: {}", resolvedPath);
                    result.put("success", true);
                    result.put("path", resolvedPath);
                    result.put("replaced", oldText);
                    result.put("new_text", newText);
                } else {
                    LOG.error("Edit failed, no available sys_operation");
                    result.put("success", false);
                    result.put("error", "Edit failed, no available sys_operation.");
                }
            } catch (Exception e) {
                LOG.error("Edit failed: {}", rootMessage(e), e);
                result.put("success", false);
                result.put("error", rootMessage(e));
            }

            return result;
        });
    }

    /**
     * Read memory content with context.
     * <p>
     * Mirrors Python's {@code read_memory_with_context} async function.
     * </p>
     *
     * @param ctx    the memory tool context
     * @param path   the memory file path
     * @param offset starting line number (optional, 1-based)
     * @param limit  maximum number of lines to read (optional)
     * @return CompletableFuture with read result
     */
    public static CompletableFuture<Map<String, Object>> readMemoryWithContext(
            MemoryToolContext ctx,
            String path,
            Integer offset,
            Integer limit) {

        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", path);
            result.put("content", "");

            try {
                Workspace ws = (ctx != null) ? (Workspace) ctx.getWorkspace() : null;
                if (ws == null) {
                    result.put("success", false);
                    result.put("error", "Workspace not initialized");
                    return result;
                }

                ValidationResult validation = validateMemoryPath(path, ws);
                if (!validation.isValid()) {
                    result.put("success", false);
                    result.put("error", validation.getMessage());
                    return result;
                }

                String fullPath = validation.getMessage();

                Object sysOp = (ctx != null) ? ctx.getSysOperation() : null;
                if (sysOp == null) {
                    LOG.error("Read memory failed, no available sys_operation");
                    result.put("success", false);
                    result.put("error", "Read failed, no available sys_operation.");
                    return result;
                }

                // Read file content
                String content = invokeFsReadFileWithRange(sysOp, fullPath, offset, limit);
                String[] lineList = content.split("\n");

                ViewResult viewResult = viewLines(lineList, offset, limit);

                result.put("success", true);
                result.put("path", fullPath);
                result.put("content", viewResult.text);
                result.put("totalLines", viewResult.total);
                result.put("start_line", viewResult.startIdx + 1);
                result.put("end_line", viewResult.endIdx);
                result.put("truncated", viewResult.truncated);

            } catch (Exception e) {
                LOG.error("Read failed: {}", rootMessage(e), e);
                result.put("success", false);
                result.put("error", rootMessage(e));
            }

            return result;
        });
    }

    // ==================== Helper Methods ====================

    /**
     * Map tool offset/limit to read_file line_range (1-based file lines; -1 = through EOF).
     * <p>
     * Mirrors Python's {@code _line_range_to_fs_read} function.
     * </p>
     */
    private static int[] lineRangeToFsRead(Integer firstLine, Integer lineCap) {
        if (firstLine == null) {
            return null;
        }
        if (lineCap != null) {
            return new int[]{firstLine, firstLine + lineCap - 1};
        }
        return new int[]{firstLine, -1};
    }

    /**
     * View lines from content.
     * <p>
     * Mirrors Python's {@code _view_lines} function.
     * firstLine is 1-based; returns (text, total, startIdx, endIdx, truncated).
     * </p>
     */
    private static ViewResult viewLines(String[] allLines, Integer firstLine, Integer lineCap) {
        int total = allLines.length;
        int startIdx = (firstLine != null) ? Math.max(0, firstLine - 1) : 0;
        int endIdx;

        if (lineCap == null) {
            endIdx = total;
        } else {
            endIdx = Math.min(startIdx + lineCap, total);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = startIdx; i < endIdx; i++) {
            if (i > startIdx) {
                sb.append("\n");
            }
            sb.append(allLines[i]);
        }

        boolean truncated = (lineCap != null) && (endIdx < total);
        return new ViewResult(sb.toString(), total, startIdx, endIdx, truncated);
    }

    /**
     * Count occurrences of substring in string.
     */
    private static int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message != null ? message : current.getClass().getSimpleName();
    }

    // ==================== Reflection-based Manager Operations ====================

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> invokeManagerSearch(
            MemoryToolContext ctx, String query, Map<String, Object> opts) throws Exception {
        Object manager = ctx.getManager();
        if (manager == null) {
            return Collections.emptyList();
        }
        // Try to invoke search method via reflection
        try {
            var method = manager.getClass().getMethod("search", String.class, Map.class);
            return (List<Map<String, Object>>) method.invoke(manager, query, opts.isEmpty() ? null : opts);
        } catch (NoSuchMethodException e) {
            LOG.warn("Manager does not have search method, returning empty results");
            return Collections.emptyList();
        }
    }

    private static Map<String, Object> getManagerStatus(MemoryToolContext ctx) {
        Map<String, Object> status = new LinkedHashMap<>();
        Object manager = ctx.getManager();
        if (manager != null) {
            try {
                var method = manager.getClass().getMethod("status");
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) method.invoke(manager);
                return result;
            } catch (Exception e) {
                LOG.debug("Could not get manager status: {}", e.getMessage());
            }
        }
        return status;
    }

    private static Map<String, Object> invokeManagerReadFile(
            MemoryToolContext ctx, String resolvedPath, Integer fromLine, Integer lines) throws Exception {
        Object manager = ctx.getManager();
        if (manager == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", resolvedPath);
            result.put("text", "");
            return result;
        }
        try {
            var method = manager.getClass().getMethod("readFile", String.class, Integer.class, Integer.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) method.invoke(manager, resolvedPath, fromLine, lines);
            return result;
        } catch (NoSuchMethodException e) {
            // Fallback
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", resolvedPath);
            result.put("text", "");
            return result;
        }
    }

    private static boolean invokeFsWriteFile(Object sysOp, String path, String content, boolean append) throws Exception {
        try {
            var fsMethod = sysOp.getClass().getMethod("fs");
            fsMethod.setAccessible(true);
            Object fs = fsMethod.invoke(sysOp);
            var writeMethod = fs.getClass().getMethod("writeFile", String.class, String.class, boolean.class, boolean.class);
            writeMethod.setAccessible(true);
            Object writeResult = writeMethod.invoke(fs, path, content, true, append);
            // Extract size from result if possible
            try {
                var dataMethod = writeResult.getClass().getMethod("getData");
                dataMethod.setAccessible(true);
                Object data = dataMethod.invoke(writeResult);
                var sizeMethod = data.getClass().getMethod("getSize");
                sizeMethod.setAccessible(true);
                return ((Number) sizeMethod.invoke(data)).intValue() > 0;
            } catch (Exception e) {
                return true;
            }
        } catch (NoSuchMethodException e) {
            LOG.error("sysOperation does not have required fs().writeFile() method");
            return false;
        }
    }

    private static String invokeFsReadFile(Object sysOp, String path) throws Exception {
        try {
            var fsMethod = sysOp.getClass().getMethod("fs");
            fsMethod.setAccessible(true);
            Object fs = fsMethod.invoke(sysOp);
            var readMethod = fs.getClass().getMethod("readFile", String.class);
            readMethod.setAccessible(true);
            Object readResult = readMethod.invoke(fs, path);
            var dataMethod = readResult.getClass().getMethod("getData");
            dataMethod.setAccessible(true);
            Object data = dataMethod.invoke(readResult);
            var contentMethod = data.getClass().getMethod("getContent");
            contentMethod.setAccessible(true);
            return (String) contentMethod.invoke(data);
        } catch (NoSuchMethodException e) {
            LOG.error("sysOperation does not have required fs().readFile() method");
            return "";
        }
    }

    private static String invokeFsReadFileWithRange(Object sysOp, String path, Integer offset, Integer limit) throws Exception {
        try {
            var fsMethod = sysOp.getClass().getMethod("fs");
            fsMethod.setAccessible(true);
            Object fs = fsMethod.invoke(sysOp);
            int[] lineRange = lineRangeToFsRead(offset, limit);
            var readMethod = fs.getClass().getMethod("readFile", String.class, int[].class);
            readMethod.setAccessible(true);
            Object readResult = readMethod.invoke(fs, path, lineRange);
            var dataMethod = readResult.getClass().getMethod("getData");
            dataMethod.setAccessible(true);
            Object data = dataMethod.invoke(readResult);
            var contentMethod = data.getClass().getMethod("getContent");
            contentMethod.setAccessible(true);
            return (String) contentMethod.invoke(data);
        } catch (NoSuchMethodException e) {
            LOG.error("sysOperation does not have required fs().readFile() method");
            return "";
        }
    }

    // ==================== Inner Classes ====================

    /** Result of path validation. */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }
    }

    /** Result of viewing lines. */
    private static class ViewResult {
        final String text;
        final int total;
        final int startIdx;
        final int endIdx;
        final boolean truncated;

        ViewResult(String text, int total, int startIdx, int endIdx, boolean truncated) {
            this.text = text;
            this.total = total;
            this.startIdx = startIdx;
            this.endIdx = endIdx;
            this.truncated = truncated;
        }
    }
}
