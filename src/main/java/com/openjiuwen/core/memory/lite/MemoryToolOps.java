/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.sys_operation.protocal.BaseFsProtocal;
import com.openjiuwen.harness.workspace.Workspace;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/**
 * General lite memory tool implementations without tool decorators.
 *
 * <p>Mirrors Python's {@code memory_tool_ops} module in
 * {@code openjiuwen/core/memory/lite/memory_tool_ops.py}.</p>
 */
public final class MemoryToolOps {

    private static final Pattern DAILY_MEMORY_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}\\.md$");

    private MemoryToolOps() {
    }

    public static ValidationResult validateMemoryPath(String path, Workspace workspace) {
        if (workspace == null) {
            return new ValidationResult(false, "Workspace not initialized");
        }
        if (path.contains("..") || path.startsWith("/")) {
            return new ValidationResult(false, "Invalid path: directory traversal not allowed");
        }

        String basename = basename(path);
        Path memoryDir = workspace.getNodePath("memory");
        Path resolvedPath = null;
        if ("USER.md".equals(basename)) {
            resolvedPath = workspace.getNodePath("USER.md");
        } else if ("MEMORY.md".equals(basename)) {
            String memoryRel = workspace.getDirectory("MEMORY.md");
            if (memoryDir != null && memoryRel != null && !memoryRel.isBlank()) {
                resolvedPath = memoryDir.resolve(memoryRel).normalize();
            }
        } else if (DAILY_MEMORY_PATTERN.matcher(basename).matches()) {
            String dailyRel = workspace.getDirectory("daily_memory");
            if (memoryDir != null && dailyRel != null && !dailyRel.isBlank()) {
                resolvedPath = memoryDir.resolve(dailyRel).resolve(basename).normalize();
            }
        } else if (memoryDir != null) {
            resolvedPath = memoryDir.resolve(basename).normalize();
        }

        if (resolvedPath == null) {
            return new ValidationResult(false, "Cannot resolve path: " + path);
        }
        return new ValidationResult(true, resolvedPath.toString());
    }

    public static CompletionStage<Map<String, Object>> memorySearchWithContext(
            MemoryToolContext ctx,
            String query,
            Integer maxResults,
            Double minScore,
            String sessionKey
    ) {
        return runAsync(() -> {
            if (ctx == null) {
                return mapOf(
                        "results", Collections.emptyList(),
                        "disabled", true,
                        "error", "Memory manager not available"
                );
            }
            if (!ensureManager(ctx)) {
                return mapOf(
                        "results", Collections.emptyList(),
                        "disabled", true,
                        "error", "Memory manager not available"
                );
            }
            Object manager = ctx.getManager();
            if (manager == null) {
                return mapOf(
                        "results", Collections.emptyList(),
                        "disabled", true,
                        "error", "Memory manager not initialized"
                );
            }

            try {
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
                List<Map<String, Object>> results = invokeManagerSearch(manager, query, opts.isEmpty() ? null : opts);
                for (Map<String, Object> result : results) {
                    Integer startLine = asInteger(result.get("start_line"));
                    Integer endLine = asInteger(result.get("end_line"));
                    String resultPath = asString(result.get("path"));
                    if (startLine != null && endLine != null && resultPath != null) {
                        String citation = startLine.equals(endLine)
                                ? resultPath + "#L" + startLine
                                : resultPath + "#L" + startLine + "-L" + endLine;
                        result.put("citation", citation);
                    }
                }
                Map<String, Object> status = getManagerStatus(manager);
                return mapOf(
                        "results", results,
                        "provider", status.get("provider"),
                        "model", status.get("model"),
                        "disabled", false
                );
            } catch (Exception exception) {
                Loggers.MEMORY.error("Memory search failed: {}", rootMessage(exception), exception);
                return mapOf(
                        "results", Collections.emptyList(),
                        "disabled", true,
                        "error", rootMessage(exception)
                );
            }
        });
    }

    public static CompletionStage<Map<String, Object>> memoryGetWithContext(
            MemoryToolContext ctx,
            String path,
            Integer fromLine,
            Integer lines
    ) {
        return runAsync(() -> {
            Workspace workspace = ctx == null ? null : ctx.getWorkspace();
            if (workspace == null) {
                return mapOf("path", path, "text", "", "disabled", true, "error", "Workspace not initialized");
            }

            ValidationResult validation = validateMemoryPath(path, workspace);
            if (!validation.valid()) {
                return mapOf("path", path, "text", "", "disabled", true, "error", validation.value());
            }
            String resolvedPath = validation.value();
            if (!ensureManager(ctx)) {
                return mapOf("path", resolvedPath, "text", "", "disabled", true,
                        "error", "Memory manager not available");
            }
            Object manager = ctx.getManager();
            if (manager == null) {
                return mapOf("path", resolvedPath, "text", "", "disabled", true,
                        "error", "Memory manager not initialized");
            }

            try {
                Map<String, Object> readResult = invokeManagerReadFile(manager, resolvedPath, fromLine, lines);
                readResult.put("disabled", false);
                return readResult;
            } catch (Exception exception) {
                Loggers.MEMORY.error("Memory get failed: {}", rootMessage(exception), exception);
                return mapOf("path", resolvedPath, "text", "", "disabled", true, "error", rootMessage(exception));
            }
        });
    }

    public static CompletionStage<Map<String, Object>> writeMemoryWithContext(
            MemoryToolContext ctx,
            String path,
            String content,
            boolean append
    ) {
        return runAsync(() -> {
            try {
                if (ctx == null) {
                    return mapOf("success", false, "path", path, "error", "Workspace not initialized");
                }
                Workspace workspace = ctx.getWorkspace();
                if (workspace == null) {
                    return mapOf("success", false, "path", path, "error", "Workspace not initialized");
                }
                ValidationResult validation = validateMemoryPath(path, workspace);
                if (!validation.valid()) {
                    return mapOf("success", false, "path", path, "error", validation.value());
                }
                String resolvedPath = validation.value();
                Object sysOperation = ctx.getSysOperation();
                if (sysOperation != null) {
                    Object writeResult = writeFile(sysOperation, resolvedPath, content, true, append, false, true);
                    boolean fileExisted = asInt(property(property(writeResult, "data"), "size")) > 0;
                    Loggers.MEMORY.info("{} file: {}", append ? "Appended to" : "Wrote", resolvedPath);
                    return mapOf(
                            "success", true,
                            "path", resolvedPath,
                            "fullPath", resolvedPath,
                            "appended", append,
                            "fileExisted", fileExisted
                    );
                }
                Loggers.MEMORY.error("Memory write failed, no available sys_operation");
            } catch (Exception exception) {
                Loggers.MEMORY.error("Write failed: {}", rootMessage(exception), exception);
                return mapOf("success", false, "path", path, "error", rootMessage(exception));
            }
            return mapOf("success", false, "path", path,
                    "error", "Memory write failed, no available sys_operation");
        });
    }

    public static CompletionStage<Map<String, Object>> editMemoryWithContext(
            MemoryToolContext ctx,
            String path,
            String oldText,
            String newText
    ) {
        return runAsync(() -> {
            try {
                Workspace workspace = ctx == null ? null : ctx.getWorkspace();
                if (workspace == null) {
                    return mapOf("success", false, "path", path, "error", "Workspace not initialized");
                }
                ValidationResult validation = validateMemoryPath(path, workspace);
                if (!validation.valid()) {
                    return mapOf("success", false, "path", path, "error", validation.value());
                }
                String resolvedPath = validation.value();
                Object sysOperation = ctx.getSysOperation();
                if (sysOperation != null) {
                    String currentContent = readFile(sysOperation, resolvedPath, null);
                    if (!currentContent.contains(oldText)) {
                        return mapOf(
                                "success", false,
                                "path", path,
                                "error", "old_text not found in file. Use read_memory tool to check exact content."
                        );
                    }
                    int occurrences = countOccurrences(currentContent, oldText);
                    if (occurrences > 1) {
                        return mapOf(
                                "success", false,
                                "path", path,
                                "error", "old_text appears " + occurrences + " times in file. Be more specific."
                        );
                    }
                    String newContent = currentContent.replaceFirst(
                            Pattern.quote(oldText),
                            java.util.regex.Matcher.quoteReplacement(newText)
                    );
                    writeFile(sysOperation, resolvedPath, newContent, true, false, false, false);
                    Loggers.MEMORY.info("Edited file: {}", resolvedPath);
                    return mapOf("success", true, "path", resolvedPath, "replaced", oldText, "new_text", newText);
                }
                Loggers.MEMORY.error("Edit failed, no available sys_operation");
                return mapOf("success", false, "path", path,
                        "error", "Edit failed, no available sys_operation.");
            } catch (Exception exception) {
                Loggers.MEMORY.error("Edit failed: {}", rootMessage(exception), exception);
                return mapOf("success", false, "path", path, "error", rootMessage(exception));
            }
        });
    }

    public static CompletionStage<Map<String, Object>> readMemoryWithContext(
            MemoryToolContext ctx,
            String path,
            Integer offset,
            Integer limit
    ) {
        return runAsync(() -> {
            try {
                Workspace workspace = ctx == null ? null : ctx.getWorkspace();
                if (workspace == null) {
                    return mapOf("success", false, "path", path, "content", "",
                            "error", "Workspace not initialized");
                }
                ValidationResult validation = validateMemoryPath(path, workspace);
                if (!validation.valid()) {
                    return mapOf("success", false, "path", path, "content", "", "error", validation.value());
                }
                String fullPath = validation.value();
                Object sysOperation = ctx.getSysOperation();
                if (sysOperation == null) {
                    Loggers.MEMORY.error("Read memory failed, no available sys_operation");
                    return mapOf("success", false, "path", path,
                            "error", "Read failed, no available sys_operation.");
                }
                BaseFsProtocal.LineRange lineRange = lineRangeToFsRead(offset, limit);
                String content = readFile(sysOperation, fullPath, lineRange);
                String[] rows = content.split("\\n", -1);
                ViewResult view = viewLines(rows, offset, limit);
                return mapOf(
                        "success", true,
                        "path", fullPath,
                        "content", view.text(),
                        "totalLines", view.total(),
                        "start_line", view.startIdx() + 1,
                        "end_line", view.endIdx(),
                        "truncated", view.truncated()
                );
            } catch (Exception exception) {
                Loggers.MEMORY.error("Read failed: {}", rootMessage(exception), exception);
                return mapOf("success", false, "path", path, "content", "", "error", rootMessage(exception));
            }
        });
    }

    private static BaseFsProtocal.LineRange lineRangeToFsRead(Integer firstLine, Integer lineCap) {
        if (firstLine == null) {
            return null;
        }
        if (lineCap != null) {
            return new BaseFsProtocal.LineRange(firstLine, firstLine + lineCap - 1);
        }
        return new BaseFsProtocal.LineRange(firstLine, -1);
    }

    private static ViewResult viewLines(String[] allLines, Integer firstLine, Integer lineCap) {
        int total = allLines.length;
        int startIdx = firstLine == null ? 0 : Math.max(0, firstLine - 1);
        int endIdx = lineCap == null ? total : Math.min(startIdx + lineCap, total);
        StringBuilder text = new StringBuilder();
        for (int index = startIdx; index < endIdx && index < total; index++) {
            if (index > startIdx) {
                text.append('\n');
            }
            text.append(allLines[index]);
        }
        boolean truncated = lineCap != null && endIdx < total;
        return new ViewResult(text.toString(), total, startIdx, endIdx, truncated);
    }

    private static boolean ensureManager(MemoryToolContext ctx) {
        try {
            return Boolean.TRUE.equals(ctx.ensureManager().toCompletableFuture().join());
        } catch (CompletionException exception) {
            throw exception;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> invokeManagerSearch(
            Object manager,
            String query,
            Map<String, Object> opts
    ) {
        Object result = invokeFirst(manager, new String[]{"search"},
                new Class<?>[]{String.class, Map.class}, query, opts);
        result = await(result);
        if (result instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    rows.add((Map<String, Object>) map);
                }
            }
            return rows;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getManagerStatus(Object manager) {
        try {
            Object result = await(invokeFirst(manager, new String[]{"status"}, new Class<?>[0]));
            if (result instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        } catch (RuntimeException ignored) {
            // Python only uses provider/model opportunistically from manager.status().
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invokeManagerReadFile(
            Object manager,
            String resolvedPath,
            Integer fromLine,
            Integer lines
    ) {
        Object result = invokeFirst(manager, new String[]{"readFile", "read_file"},
                new Class<?>[]{String.class, Integer.class, Integer.class}, resolvedPath, fromLine, lines);
        result = await(result);
        if (result instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return mapOf("path", resolvedPath, "text", "");
    }

    private static Object writeFile(
            Object sysOperation,
            String path,
            String content,
            boolean createIfNotExist,
            boolean prependNewline,
            boolean appendNewline,
            boolean append
    ) {
        Object fs = invokeNoArg(sysOperation, "fs");
        try {
            return await(invokeFirst(fs, new String[]{"writeFile", "write_file"},
                    new Class<?>[]{
                            String.class,
                            String.class,
                            String.class,
                            boolean.class,
                            boolean.class,
                            boolean.class,
                            boolean.class,
                            String.class,
                            String.class,
                            Map.class
                    },
                    path,
                    content,
                    BaseFsProtocal.MODE_TEXT,
                    prependNewline,
                    appendNewline,
                    append,
                    createIfNotExist,
                    BaseFsProtocal.DEFAULT_PERMISSIONS,
                    BaseFsProtocal.DEFAULT_ENCODING,
                    Collections.emptyMap()));
        } catch (RuntimeException ignored) {
            try {
                return await(invokeFirst(fs, new String[]{"writeFile", "write_file"},
                        new Class<?>[]{String.class, String.class, boolean.class, boolean.class},
                        path, content, createIfNotExist, append));
            } catch (RuntimeException secondFailure) {
                return await(invokeFirst(fs, new String[]{"writeFile", "write_file"},
                        new Class<?>[]{String.class, String.class}, path, content));
            }
        }
    }

    private static String readFile(Object sysOperation, String path, BaseFsProtocal.LineRange lineRange) {
        Object fs = invokeNoArg(sysOperation, "fs");
        RuntimeException lineRangeFailure = null;
        if (lineRange != null) {
            try {
                Object result = await(invokeFirst(fs, new String[]{"readFile", "read_file"},
                        new Class<?>[]{
                                String.class,
                                String.class,
                                Integer.class,
                                Integer.class,
                                BaseFsProtocal.LineRange.class,
                                String.class,
                                int.class,
                                Map.class
                        },
                        path,
                        BaseFsProtocal.MODE_TEXT,
                        null,
                        null,
                        lineRange,
                        BaseFsProtocal.DEFAULT_ENCODING,
                        BaseFsProtocal.DEFAULT_READ_CHUNK_SIZE,
                        Collections.emptyMap()));
                return asString(property(property(result, "data"), "content"));
            } catch (RuntimeException exception) {
                lineRangeFailure = exception;
                if (lineRange.endLine() > 0) {
                    throw exception;
                }
            }
        }
        try {
            Object result = await(invokeFirst(fs, new String[]{"readFile", "read_file"},
                    new Class<?>[]{String.class}, path));
            return asString(property(property(result, "data"), "content"));
        } catch (RuntimeException exception) {
            if (lineRangeFailure != null) {
                throw lineRangeFailure;
            }
            throw exception;
        }
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
            } catch (NoSuchMethodException ignored) {
                // Try the next method spelling.
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new IllegalArgumentException(rootMessage(exception), exception);
            }
        }
        throw new IllegalArgumentException("method not found: " + String.join("/", methodNames));
    }

    private static Object property(Object target, String name) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            Object value = map.get(name);
            if (value == null && name.contains("_")) {
                value = map.get(toCamelCase(name));
            }
            return value;
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

    private static Object await(Object value) {
        if (value instanceof CompletionStage<?> stage) {
            return stage.toCompletableFuture().join();
        }
        return value;
    }

    private static CompletionStage<Map<String, Object>> runAsync(ThrowingMapSupplier supplier) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private static int countOccurrences(String text, String needle) {
        if (needle.isEmpty()) {
            return text.length() + 1;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static String basename(String path) {
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static String toCamelCase(String name) {
        StringBuilder builder = new StringBuilder();
        boolean upper = false;
        for (char ch : name.toCharArray()) {
            if (ch == '_') {
                upper = true;
            } else if (upper) {
                builder.append(Character.toUpperCase(ch));
                upper = false;
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return null;
    }

    private static int asInt(Object value) {
        Integer integer = asInteger(value);
        return integer == null ? 0 : integer;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof InvocationTargetException
                || current instanceof CompletionException
                || current.getCause() != null && current.getMessage() == null) {
            Throwable cause = current instanceof InvocationTargetException invocation
                    ? invocation.getTargetException()
                    : current.getCause();
            if (cause == null) {
                break;
            }
            current = cause;
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    /**
     * Async supplier used to preserve Python coroutine-style tool entry points.
     *
     * <p>Mirrors Python's async functions in
     * {@code openjiuwen/core/memory/lite/memory_tool_ops.py}.</p>
     */
    @FunctionalInterface
    private interface ThrowingMapSupplier {
        Map<String, Object> get() throws Exception;
    }

    /**
     * Memory path validation result.
     *
     * <p>Mirrors Python's {@code validate_memory_path} tuple return in
     * {@code openjiuwen/core/memory/lite/memory_tool_ops.py}.</p>
     */
    public record ValidationResult(boolean valid, String value) {
    }

    /**
     * Line excerpt metadata.
     *
     * <p>Mirrors Python's {@code _view_lines} tuple return in
     * {@code openjiuwen/core/memory/lite/memory_tool_ops.py}.</p>
     */
    private record ViewResult(String text, int total, int startIdx, int endIdx, boolean truncated) {
    }
}
