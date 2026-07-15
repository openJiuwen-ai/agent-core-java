/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * File-backed memory helpers for the Java harness baseline.
 * 
 * @since 0.1.7
 */
public class MemoryTools {
    private final Path memoryRoot;

    /**
     * MemoryTools.
     * 
     * @param memoryRoot memoryRoot
     * @since 0.1.7
     */
    public MemoryTools(String memoryRoot) {
        this.memoryRoot = Path.of(memoryRoot).toAbsolutePath().normalize();
    }

    /**
     * writeMemory.
     * 
     * @param relativePath relativePath
     * @param content content
     * @param isAppend isAppend
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput writeMemory(String relativePath, String content, boolean isAppend) {
        try {
            Path target = resolve(relativePath);
            Files.createDirectories(target.getParent());
            if (isAppend && Files.exists(target)) {
                Files.writeString(target, content, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
            } else {
                Files.writeString(target, content, StandardCharsets.UTF_8);
            }
            return ToolOutput.builder().success(true).data(Map.of("path", target.toString())).build();
        } catch (IOException ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }

    /**
     * readMemory.
     * 
     * @param relativePath relativePath
     * @param offset offset
     * @param limit limit
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput readMemory(String relativePath, Integer offset, Integer limit) {
        try {
            Path target = resolve(relativePath);
            List<String> lines = Files.readAllLines(target, StandardCharsets.UTF_8);
            int start = Math.max(0, offset != null ? offset : 0);
            int end = Math.min(lines.size(), limit != null ? start + Math.max(0, limit) : lines.size());
            String content = String.join(System.lineSeparator(), lines.subList(start, end));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("path", target.toString());
            payload.put("content", content);
            payload.put("line_count", lines.size());
            return ToolOutput.builder().success(true).data(payload).build();
        } catch (IOException ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }

    /**
     * editMemory.
     * 
     * @param relativePath relativePath
     * @param oldText oldText
     * @param newText newText
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput editMemory(String relativePath, String oldText, String newText) {
        try {
            Path target = resolve(relativePath);
            String content = Files.readString(target, StandardCharsets.UTF_8);
            if (!content.contains(oldText)) {
                return ToolOutput.builder().success(false).error("old_text not found").build();
            }
            Files.writeString(target, content.replace(oldText, newText), StandardCharsets.UTF_8);
            return ToolOutput.builder().success(true).data(Map.of("path", target.toString())).build();
        } catch (IOException ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }

    /**
     * memoryGet.
     * 
     * @param relativePath relativePath
     * @param fromLine fromLine
     * @param lines lines
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput memoryGet(String relativePath, Integer fromLine, Integer lines) {
        return readMemory(relativePath, fromLine, lines);
    }

    /**
     * memorySearch.
     * 
     * @param query query
     * @param maxResults maxResults
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput memorySearch(String query, Integer maxResults) {
        int limit = maxResults != null ? Math.max(1, maxResults) : 10;
        try (Stream<Path> stream = Files.walk(memoryRoot)) {
            List<Map<String, Object>> matches =
                stream.filter(Files::isRegularFile).map(path -> match(path, query)).filter(map -> !map.isEmpty())
                        .sorted(Comparator.comparing(map -> String.valueOf(map.get("path")))).limit(limit).toList();
            return ToolOutput.builder().success(true).data(matches).build();
        } catch (IOException ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }

    /**
     * match.
     * 
     * @param path path
     * @param query query
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> match(Path path, String query) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains(query)) {
                    return Map.of("path", memoryRoot.relativize(path).toString(), "line", i, "snippet", lines.get(i));
                }
            }
            return Map.of();
        } catch (IOException ex) {
            return Map.of();
        }
    }

    /**
     * resolve.
     * 
     * @param relativePath relativePath
     * @return the result
     * @since 0.1.7
     */
    private Path resolve(String relativePath) {
        return memoryRoot.resolve(relativePath).normalize();
    }
}
