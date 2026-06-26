/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.harness.workspace.Workspace;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Coding memory tool implementations without tool decorators.
 *
 * <p>Mirrors Python's module in
 * {@code openjiuwen/core/memory/lite/coding_memory_tool_ops.py}.</p>
 */
public final class CodingMemoryToolOps {

    public static final int MAX_INDEX_LINES = 200;

    private static final int MAX_CONFLICT_RETRIES = 2;
    private static final String MEMORY_INDEX_FILE = "MEMORY.md";
    private static final ConcurrentHashMap<String, ReentrantLock> FILE_LOCKS = new ConcurrentHashMap<>();
    private static final ReentrantLock MEMORY_INDEX_LOCK = new ReentrantLock();

    private CodingMemoryToolOps() {
    }

    public static ValidationResult validateCodingMemoryPath(String path, Workspace workspace) {
        if (workspace == null) {
            return new ValidationResult(false, "Workspace not initialized");
        }
        if (path == null || path.contains("..") || path.startsWith("/")) {
            return new ValidationResult(false, "Invalid path: directory traversal not allowed");
        }
        if (!path.endsWith(".md")) {
            return new ValidationResult(false, "Path must end with .md");
        }
        Path memoryDir = workspace.getNodePath("coding_memory");
        if (memoryDir == null) {
            return new ValidationResult(false, "coding_memory node not configured");
        }
        Path basename = Paths.get(path).getFileName();
        return new ValidationResult(true, memoryDir.resolve(basename).normalize().toString());
    }

    public static CompletableFuture<Map<String, Object>> codingMemoryReadWithContext(
            CodingMemoryToolContext ctx,
            String path,
            Integer offset,
            Integer limit
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Workspace workspace = ctx == null ? null : ctx.getWorkspace();
                if (workspace == null) {
                    return mapOf(
                            "success", false,
                            "path", path,
                            "content", "",
                            "error", "Workspace not initialized"
                    );
                }
                ValidationResult validation = validateCodingMemoryPath(path, workspace);
                if (!validation.valid()) {
                    return mapOf(
                            "success", false,
                            "path", path,
                            "content", "",
                            "error", validation.value()
                    );
                }
                Object sysOperation = ctx.getSysOperation();
                if (sysOperation == null) {
                    Loggers.MEMORY.error("Read memory failed, no available sys_operation");
                    return mapOf(
                            "success", false,
                            "path", path,
                            "error", "Read failed, no available sys_operation."
                    );
                }
                int[] lineRange = lineRange(offset, limit);
                String data = readFile(sysOperation, validation.value(), lineRange);
                String[] rows = data.split("\\n", -1);
                int total = rows.length;
                int fromIdx = offset == null ? 0 : Math.max(0, offset - 1);
                int toIdx = limit == null ? total : Math.min(fromIdx + limit, total);
                return mapOf(
                        "success", true,
                        "path", validation.value(),
                        "content", String.join("\n", List.of(rows).subList(fromIdx, toIdx)),
                        "totalLines", total,
                        "start_line", fromIdx + 1,
                        "end_line", toIdx,
                        "truncated", limit != null && toIdx < total
                );
            } catch (Exception ex) {
                Loggers.MEMORY.error("Read failed: {}", rootMessage(ex), ex);
                return mapOf(
                        "success", false,
                        "path", path,
                        "content", "",
                        "error", rootMessage(ex)
                );
            }
        });
    }

    public static CompletableFuture<Map<String, Object>> codingMemoryWriteWithContext(
            CodingMemoryToolContext ctx,
            String path,
            String content
    ) {
        return CompletableFuture.supplyAsync(() -> writeWithContextBlocking(ctx, path, content));
    }

    public static CompletableFuture<Map<String, Object>> codingMemoryEditWithContext(
            CodingMemoryToolContext ctx,
            String path,
            String oldText,
            String newText
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (oldText == null || oldText.isEmpty()) {
                    return mapOf("success", false, "error", "old_text cannot be empty");
                }
                if (ctx == null || ctx.getWorkspace() == null) {
                    return mapOf("success", false, "error", "Workspace not initialized");
                }
                ValidationResult validation = validateCodingMemoryPath(path, ctx.getWorkspace());
                if (!validation.valid()) {
                    return mapOf("success", false, "error", validation.value());
                }
                Object sysOperation = ctx.getSysOperation();
                if (sysOperation == null) {
                    return mapOf("success", false, "error", "no available sys_operation");
                }
                String memoryDir = resolveMemoryDir(ctx, validation.value());
                ReentrantLock lock = getFileLock(validation.value());
                String newContent;
                lock.lock();
                try {
                    String current = readFile(sysOperation, validation.value(), null);
                    int occurrences = countOccurrences(current, oldText);
                    if (occurrences == 0) {
                        return mapOf("success", false, "error", "old_text not found in file");
                    }
                    if (occurrences > 1) {
                        return mapOf(
                                "success", false,
                                "error", "old_text appears " + occurrences + " times, please be more specific"
                        );
                    }
                    newContent = current.replaceFirst(java.util.regex.Pattern.quote(oldText),
                            java.util.regex.Matcher.quoteReplacement(newText));
                    writeFile(sysOperation, validation.value(), newContent, true, false, false);
                } finally {
                    lock.unlock();
                }
                Map<String, String> frontmatter = FrontmatterUtils.parseFrontmatter(newContent);
                if (frontmatter != null && FrontmatterUtils.validateFrontmatter(frontmatter).valid()) {
                    upsertMemoryIndex(ctx, memoryDir, basename(validation.value()), frontmatter);
                }
                return mapOf("success", true, "path", validation.value(), "new_content", newContent);
            } catch (Exception ex) {
                Loggers.MEMORY.error("coding_memory_edit failed: {}", rootMessage(ex), ex);
                return mapOf("success", false, "error", rootMessage(ex));
            }
        });
    }

    private static Map<String, Object> writeWithContextBlocking(
            CodingMemoryToolContext ctx,
            String path,
            String content
    ) {
        try {
            if (ctx == null) {
                return mapOf("success", false, "path", path, "error", "Workspace not initialized");
            }
            Workspace workspace = ctx.getWorkspace();
            if (workspace == null) {
                return mapOf("success", false, "path", path, "error", "Workspace not initialized");
            }
            ValidationResult validation = validateCodingMemoryPath(path, workspace);
            if (!validation.valid()) {
                return mapOf("success", false, "path", path, "error", validation.value());
            }
            Map<String, String> frontmatter = FrontmatterUtils.parseFrontmatter(content);
            if (frontmatter == null) {
                return mapOf(
                        "success", false,
                        "path", path,
                        "error", "must contain frontmatter(name/description/type)"
                );
            }
            FrontmatterUtils.ValidationResult frontmatterValidation =
                    FrontmatterUtils.validateFrontmatter(frontmatter);
            if (!frontmatterValidation.valid()) {
                return mapOf("success", false, "path", path, "error", frontmatterValidation.message());
            }
            frontmatter = FrontmatterUtils.enrichFrontmatter(frontmatter, false);
            String rebuiltContent = FrontmatterUtils.rebuildContentWithFrontmatter(content, frontmatter);
            String body = extractBody(rebuiltContent);
            if (body.isBlank()) {
                return mapOf("success", false, "path", path, "error", "no content body");
            }
            Object sysOperation = ctx.getSysOperation();
            if (sysOperation == null) {
                return mapOf("success", false, "path", path, "error", "no available coding_memory_sys_operation");
            }
            String resolved = validation.value();
            String filename = basename(resolved);
            String memoryDir = resolveMemoryDir(ctx, resolved);
            Map<String, Object> conflictResult = new LinkedHashMap<>();
            for (int attempt = 0; attempt < MAX_CONFLICT_RETRIES; attempt++) {
                Set<String> snapshot = snapshotMemoryFiles(ctx, memoryDir);
                boolean fileExists = snapshot.contains(filename);
                if (fileExists) {
                    conflictResult = prepareAppendMode(ctx, resolved, filename, body, frontmatter);
                    if (Objects.equals(conflictResult.get("mode"), WriteMode.SKIP.value())) {
                        return conflictResult;
                    }
                } else {
                    conflictResult = createModeConflictResult(ctx, body, filename);
                    if (Objects.equals(conflictResult.get("mode"), WriteMode.SKIP.value())) {
                        return conflictResult;
                    }
                }
                ReentrantLock lock = getFileLock(resolved);
                boolean snapshotStale;
                lock.lock();
                try {
                    snapshotStale = !snapshotMemoryFiles(ctx, memoryDir).equals(snapshot);
                    if (!snapshotStale) {
                        if (!fileExists) {
                            writeFile(sysOperation, resolved, rebuiltContent, true, false, false);
                        } else {
                            appendToExistingFile(ctx, resolved, body, frontmatter);
                        }
                    }
                } finally {
                    lock.unlock();
                }
                if (snapshotStale) {
                    Loggers.MEMORY.info("Snapshot stale on attempt {}, retrying conflict detection", attempt + 1);
                    continue;
                }
                upsertMemoryIndex(ctx, memoryDir, filename, frontmatter);
                return writeResult(true, resolved, fileExists ? WriteMode.APPEND : WriteMode.CREATE,
                        frontmatter.get("type"), conflictResult);
            }
            Loggers.MEMORY.warn("Exceeded max conflict retries ({}), writing without snapshot validation",
                    MAX_CONFLICT_RETRIES);
            ReentrantLock lock = getFileLock(resolved);
            boolean fileExistsNow;
            lock.lock();
            try {
                fileExistsNow = snapshotMemoryFiles(ctx, memoryDir).contains(filename);
                if (!fileExistsNow) {
                    writeFile(sysOperation, resolved, rebuiltContent, true, false, false);
                } else {
                    appendToExistingFile(ctx, resolved, body, frontmatter);
                }
            } finally {
                lock.unlock();
            }
            upsertMemoryIndex(ctx, memoryDir, filename, frontmatter);
            return writeResult(true, resolved, fileExistsNow ? WriteMode.APPEND : WriteMode.CREATE,
                    frontmatter.get("type"), conflictResult);
        } catch (Exception ex) {
            Loggers.MEMORY.error("coding_memory_write failed: {}", rootMessage(ex), ex);
            return mapOf("success", false, "path", path, "error", rootMessage(ex));
        }
    }

    private static Map<String, Object> prepareAppendMode(
            CodingMemoryToolContext ctx,
            String resolved,
            String filename,
            String body,
            Map<String, String> frontmatter
    ) {
        Map<String, String> oldMemories = new LinkedHashMap<>();
        String existingBody = extractBody(readFileSafe(ctx, resolved));
        if (!existingBody.isBlank()) {
            oldMemories.put("__self__", existingBody);
        }
        oldMemories.putAll(searchSimilar(ctx, body, filename));
        if (!oldMemories.isEmpty() && managerHasLlm(ctx)) {
            return runCheckerResult(filename, body, oldMemories, frontmatter.get("type"));
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> createModeConflictResult(CodingMemoryToolContext ctx, String body, String filename) {
        Map<String, String> oldMemories = searchSimilar(ctx, body, filename);
        if (!oldMemories.isEmpty() && managerHasLlm(ctx)) {
            return runCheckerResult(filename, body, oldMemories, null);
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> runCheckerResult(
            String filename,
            String body,
            Map<String, String> oldMemories,
            String type
    ) {
        // The Java MemUpdateChecker dependency is not present in this package yet. The Python
        // branch returns an empty action list when no LLM/checker is available, so the default
        // local behavior is "no conflict".
        return new LinkedHashMap<>();
    }

    private static Map<String, String> searchSimilar(
            CodingMemoryToolContext ctx,
            String body,
            String excludePath
    ) {
        Map<String, String> oldMemories = new LinkedHashMap<>();
        if (ctx == null || ctx.getManager() == null) {
            return oldMemories;
        }
        List<Map<String, Object>> results = invokeManagerSearch(ctx.getManager(), body, Map.of("max_results", 5));
        String memoryDir = ctx.getCodingMemoryDir() == null ? "" : ctx.getCodingMemoryDir();
        for (Map<String, Object> result : results) {
            double score = asDouble(result.get("score"));
            String resultPath = asString(result.get("path"));
            if (score > 0.75d
                    && !MEMORY_INDEX_FILE.equals(resultPath)
                    && !Objects.equals(resultPath, excludePath)) {
                String oldContent = readFileSafe(ctx, Paths.get(memoryDir, resultPath).toString());
                String oldBody = extractBody(oldContent);
                if (!oldBody.isBlank()) {
                    oldMemories.put(resultPath, oldBody);
                }
            }
        }
        return oldMemories;
    }

    private static void appendToExistingFile(
            CodingMemoryToolContext ctx,
            String resolved,
            String body,
            Map<String, String> frontmatter
    ) {
        Object sysOperation = ctx.getSysOperation();
        if (sysOperation == null) {
            Loggers.MEMORY.error("_append_to_existing_file: coding_memory_sys_operation is None");
            return;
        }
        writeFile(sysOperation, resolved, "\n\n" + body, false, true, true);
        String fullContent = readFileSafe(ctx, resolved);
        Map<String, String> parsed = FrontmatterUtils.parseFrontmatter(fullContent);
        if (parsed != null) {
            FrontmatterUtils.enrichFrontmatter(parsed, true);
            String updatedContent = FrontmatterUtils.rebuildContentWithFrontmatter(fullContent, parsed);
            writeFile(sysOperation, resolved, updatedContent, false, false, false);
        }
    }

    private static void upsertMemoryIndex(
            CodingMemoryToolContext ctx,
            String memoryDir,
            String filename,
            Map<String, String> frontmatter
    ) {
        MEMORY_INDEX_LOCK.lock();
        try {
            Object sysOperation = ctx == null ? null : ctx.getSysOperation();
            if (sysOperation == null) {
                return;
            }
            String indexPath = Paths.get(memoryDir, MEMORY_INDEX_FILE).toString();
            String newEntry = "- [" + frontmatter.get("name") + "](" + filename + ") — "
                    + frontmatter.get("description");
            List<String> lines = new ArrayList<>();
            try {
                String content = readFile(sysOperation, indexPath, null);
                if (!content.isEmpty()) {
                    lines.addAll(List.of(content.split("\\n", -1)));
                }
            } catch (Exception ex) {
                Loggers.MEMORY.warn("Failed to read memory index: {}", rootMessage(ex));
            }
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains("](" + filename + ")")) {
                    lines.set(i, newEntry);
                    found = true;
                    break;
                }
            }
            if (!found) {
                lines.add(0, newEntry);
            }
            String newContent = String.join("\n", lines.subList(0, Math.min(lines.size(), MAX_INDEX_LINES)));
            writeFile(sysOperation, indexPath, newContent, true, false, false);
        } finally {
            MEMORY_INDEX_LOCK.unlock();
        }
    }

    private static Set<String> snapshotMemoryFiles(CodingMemoryToolContext ctx, String memoryDir) {
        if (ctx == null || memoryDir == null || memoryDir.isBlank() || ctx.getSysOperation() == null) {
            return Set.of();
        }
        try {
            List<FileItemView> files = listFiles(ctx.getSysOperation(), memoryDir);
            Set<String> names = new LinkedHashSet<>();
            for (FileItemView file : files) {
                if (!file.directory()
                        && file.name().toLowerCase(Locale.ROOT).endsWith(".md")
                        && !MEMORY_INDEX_FILE.equalsIgnoreCase(file.name())) {
                    names.add(file.name());
                }
            }
            return names;
        } catch (Exception ex) {
            return Set.of();
        }
    }

    private static ReentrantLock getFileLock(String path) {
        return FILE_LOCKS.computeIfAbsent(path, ignored -> new ReentrantLock());
    }

    private static String resolveMemoryDir(CodingMemoryToolContext ctx, String resolvedPath) {
        if (ctx != null && ctx.getCodingMemoryDir() != null && !ctx.getCodingMemoryDir().isBlank()) {
            return ctx.getCodingMemoryDir();
        }
        Path parent = Paths.get(resolvedPath).getParent();
        return parent == null ? "" : parent.toString();
    }

    private static String readFileSafe(CodingMemoryToolContext ctx, String filePath) {
        try {
            return ctx == null || ctx.getSysOperation() == null ? "" : readFile(ctx.getSysOperation(), filePath, null);
        } catch (Exception ex) {
            return "";
        }
    }

    private static String readFile(Object sysOperation, String path, int[] lineRange) {
        Object fs = invokeNoArg(sysOperation, "fs");
        Object result;
        if (lineRange == null) {
            result = invokeFirst(fs, new String[]{"readFile", "read_file"}, new Class<?>[]{String.class}, path);
        } else {
            result = invokeFirst(fs, new String[]{"readFile", "read_file"},
                    new Class<?>[]{String.class, int[].class}, path, lineRange);
        }
        Object data = property(result, "data");
        Object content = property(data, "content");
        return content == null ? "" : String.valueOf(content);
    }

    private static void writeFile(
            Object sysOperation,
            String path,
            String content,
            boolean createIfNotExist,
            boolean append,
            boolean preferAppendSignature
    ) {
        Object fs = invokeNoArg(sysOperation, "fs");
        try {
            invokeFirst(fs, new String[]{"writeFile", "write_file"},
                    new Class<?>[]{String.class, String.class, boolean.class, boolean.class},
                    path, content, createIfNotExist, append);
        } catch (RuntimeException ex) {
            if (preferAppendSignature) {
                invokeFirst(fs, new String[]{"writeFile", "write_file"},
                        new Class<?>[]{String.class, String.class, boolean.class},
                        path, content, append);
            } else {
                throw ex;
            }
        }
    }

    private static List<FileItemView> listFiles(Object sysOperation, String directory) {
        Object fs = invokeNoArg(sysOperation, "fs");
        Object result = invokeFirst(fs, new String[]{"listFiles", "list_files"},
                new Class<?>[]{String.class, boolean.class}, directory, false);
        Object data = property(result, "data");
        Object listItems = property(data, "listItems");
        if (listItems == null) {
            listItems = property(data, "list_items");
        }
        if (!(listItems instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<FileItemView> files = new ArrayList<>();
        for (Object item : iterable) {
            files.add(new FileItemView(asString(property(item, "name")), asBoolean(property(item, "directory"))));
        }
        return files;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> invokeManagerSearch(
            LiteMemoryToolContextBase.MemoryIndexManagerView manager,
            String body,
            Map<String, Object> options
    ) {
        try {
            Object result = invokeFirst(manager, new String[]{"search"},
                    new Class<?>[]{String.class, Map.class}, body, options);
            if (result instanceof CompletableFuture<?> future) {
                result = future.join();
            }
            if (result instanceof List<?> list) {
                return (List<Map<String, Object>>) list;
            }
        } catch (Exception ignored) {
            // Match Python's permissive helper path when manager search is unavailable.
        }
        return List.of();
    }

    private static boolean managerHasLlm(CodingMemoryToolContext ctx) {
        Object manager = ctx == null ? null : ctx.getManager();
        if (manager == null) {
            return false;
        }
        Object llm = property(manager, "llm");
        return llm != null;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        return invokeFirst(target, new String[]{methodName}, new Class<?>[0]);
    }

    private static Object invokeFirst(Object target, String[] methodNames, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            throw new IllegalArgumentException("target is null");
        }
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method.invoke(target, args);
            } catch (ReflectiveOperationException ignored) {
                // Try the next Java/Python-style method spelling.
            }
        }
        throw new IllegalArgumentException("method not found: " + String.join("/", methodNames));
    }

    private static Object property(Object target, String name) {
        if (target == null) {
            return null;
        }
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String methodName : new String[]{"get" + suffix, "is" + suffix, name}) {
            try {
                Method method = target.getClass().getMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // Try field fallback.
            }
        }
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static int[] lineRange(Integer offset, Integer limit) {
        if (offset != null && limit != null) {
            return new int[]{offset, offset + limit - 1};
        }
        if (offset != null) {
            return new int[]{offset, -1};
        }
        return null;
    }

    private static String extractBody(String content) {
        String text = content == null ? "" : content.trim();
        if (!text.startsWith("---")) {
            return text;
        }
        int end = text.indexOf("---", 3);
        if (end == -1) {
            return "";
        }
        return text.substring(end + 3).trim();
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static Map<String, Object> writeResult(
            boolean success,
            String path,
            WriteMode mode,
            String type,
            Map<String, Object> extras
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("path", path);
        result.put("mode", mode.value());
        if (type != null) {
            result.put("type", type);
        }
        result.putAll(extras);
        return result;
    }

    private static String basename(String path) {
        Path fileName = Paths.get(path).getFileName();
        return fileName == null ? path : fileName.toString();
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean asBoolean(Object value) {
        return value instanceof Boolean booleanValue && booleanValue;
    }

    private static double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0d;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put(String.valueOf(values[i]), values[i + 1]);
        }
        return result;
    }

    public record ValidationResult(boolean valid, String value) {
    }

    private record FileItemView(String name, boolean directory) {
    }
}
