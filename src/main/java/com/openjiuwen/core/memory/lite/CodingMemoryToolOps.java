/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.harness.workspace.Workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import java.util.List;
import java.util.Map;

/**
 * Coding memory tool implementations.
 */
public final class CodingMemoryToolOps {
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final int MAX_INDEX_LINES = 200;

    private CodingMemoryToolOps() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Map.Entry<Boolean, String> validateCodingMemoryPath(String path, Workspace workspace) {
        if (workspace == null) {
            return Map.entry(false, "Workspace not initialized");
        }
        if (path.contains("..") || path.startsWith("/")) {
            return Map.entry(false, "Invalid path: directory traversal not allowed");
        }
        if (!path.endsWith(".md")) {
            return Map.entry(false, "Path must end with .md");
        }
        Path memoryDir = workspace.getNodePath("coding_memory");
        if (memoryDir == null) {
            return Map.entry(false, "coding_memory node not configured");
        }
        return Map.entry(true, memoryDir.resolve(Path.of(path).getFileName().toString()).normalize().toString());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void upsertCodingMemoryIndex(
            String memoryDir,
            String filename,
            Map<String, String> frontmatter
    ) throws IOException {
        Path indexPath = Path.of(memoryDir).resolve("MEMORY.md");
        String newEntry = "- [" + frontmatter.get("name") + "](" + filename + ") — " + frontmatter.get("description");
        List<String> lines = Files.exists(indexPath)
                ? Files.readAllLines(indexPath, StandardCharsets.UTF_8)
                : new ArrayList<>();
        boolean isEntryFound = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("](" + filename + ")")) {
                lines.set(i, newEntry);
                isEntryFound = true;
                break;
            }
        }
        if (!isEntryFound) {
            lines.add(0, newEntry);
        }
        Files.createDirectories(indexPath.getParent());
        Files.writeString(indexPath,
                String.join("\n", lines.subList(0, Math.min(lines.size(), MAX_INDEX_LINES))),
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void removeFromCodingMemoryIndex(String memoryDir, String filename) throws IOException {
        Path indexPath = Path.of(memoryDir).resolve("MEMORY.md");
        if (!Files.exists(indexPath)) {
            return;
        }
        List<String> lines = Files.readAllLines(indexPath, StandardCharsets.UTF_8);
        lines.removeIf(line -> line.contains("](" + filename + ")"));
        Files.writeString(indexPath, String.join("\n", lines), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Map<String, Object> codingMemoryReadWithContext(
            CodingMemoryToolContext ctx,
            String path,
            Integer offset,
            Integer limit
    ) {
        Map.Entry<Boolean, String> valid = validateCodingMemoryPath(path, ctx != null ? ctx.getWorkspace() : null);
        if (!valid.getKey()) {
            return Map.of("success", false, "path", path, "content", "", "error", valid.getValue());
        }
        try {
            Path isResolved = Path.of(valid.getValue());
            List<String> lines = Files.exists(isResolved)
                    ? Files.readAllLines(isResolved, StandardCharsets.UTF_8)
                    : List.of();
            int total = lines.size();
            int start = offset == null ? 0 : Math.max(0, offset - 1);
            int end = limit == null ? total : Math.min(total, start + limit);
            return Map.of(
                    "success", true,
                    "path", isResolved.toString(),
                    "content", String.join("\n", lines.subList(start, end)),
                    "totalLines", total,
                    "start_line", start + 1,
                    "end_line", end,
                    "truncated", limit != null && end < total
            );
        } catch (IOException e) {
            return Map.of("success", false, "path", path, "content", "", "error", e.getMessage());
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Map<String, Object> codingMemoryWriteWithContext(
            CodingMemoryToolContext ctx,
            String path,
            String content
    ) {
        Map.Entry<Boolean, String> valid = validateCodingMemoryPath(path, ctx != null ? ctx.getWorkspace() : null);
        if (!valid.getKey()) {
            return Map.of("success", false, "path", path, "error", valid.getValue());
        }
        Map<String, String> frontmatter = FrontmatterUtils.parseFrontmatter(content);
        if (frontmatter == null) {
            return Map.of("success", false, "path", path, "error", "must contain frontmatter(name/description/type)");
        }
        Map.Entry<Boolean, String> validatedFrontmatter = FrontmatterUtils.validateFrontmatter(frontmatter);
        if (!validatedFrontmatter.getKey()) {
            return Map.of("success", false, "path", path, "error", validatedFrontmatter.getValue());
        }
        try {
            Path isResolved = Path.of(valid.getValue());
            Files.createDirectories(isResolved.getParent());
            boolean isExisted = Files.exists(isResolved) && Files.size(isResolved) > 0;
            Files.writeString(isResolved, content, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
            upsertCodingMemoryIndex(ctx.getCodingMemoryDir(), isResolved.getFileName().toString(), frontmatter);
            if (ctx.ensureManager() && ctx.getManager() != null) {
                ctx.getManager().sync("coding_memory_write");
            }
            return Map.of(
                    "success", true,
                    "path", isResolved.toString(),
                    "fullPath", isResolved.toString(),
                    "appended", true,
                    "fileExisted", isExisted,
                    "type", frontmatter.get("type")
            );
        } catch (IOException e) {
            return Map.of("success", false, "path", path, "error", e.getMessage());
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Map<String, Object> codingMemoryEditWithContext(
            CodingMemoryToolContext ctx,
            String path,
            String oldText,
            String newText
    ) {
        if (oldText == null || oldText.isEmpty()) {
            return Map.of("success", false, "error", "old_text cannot be empty");
        }
        Map.Entry<Boolean, String> valid = validateCodingMemoryPath(path, ctx != null ? ctx.getWorkspace() : null);
        if (!valid.getKey()) {
            return Map.of("success", false, "error", valid.getValue());
        }
        try {
            Path isResolved = Path.of(valid.getValue());
            String content = Files.exists(isResolved) ? Files.readString(isResolved, StandardCharsets.UTF_8) : "";
            long occurrences = content.split(java.util.regex.Pattern.quote(oldText), -1).length - 1L;
            if (occurrences == 0) {
                return Map.of("success", false, "error", "old_text not found in file");
            }
            if (occurrences > 1) {
                return Map.of(
                        "success", false,
                        "error", "old_text appears " + occurrences + " times, please be more specific");
            }
            String newContent = content.replace(oldText, newText);
            Files.writeString(isResolved, newContent, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            Map<String, String> frontmatter = FrontmatterUtils.parseFrontmatter(newContent);
            if (frontmatter != null && FrontmatterUtils.validateFrontmatter(frontmatter).getKey()) {
                upsertCodingMemoryIndex(ctx.getCodingMemoryDir(), isResolved.getFileName().toString(), frontmatter);
            }
            if (ctx.ensureManager() && ctx.getManager() != null) {
                ctx.getManager().sync("coding_memory_edit");
            }
            return Map.of("success", true, "path", isResolved.toString(), "new_content", newContent);
        } catch (IOException e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}
