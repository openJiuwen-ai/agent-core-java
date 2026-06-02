/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.harness.workspace.Workspace;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Holds state for coding_memory_* tools (node "coding_memory").
 * <p>
 * Mirrors Python's {@code CodingMemoryToolContext} dataclass from
 * {@code core/memory/lite/coding_memory_tool_context.py}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class CodingMemoryToolContext extends LiteMemoryToolContextBase {

    private Workspace workspace;
    private Object sysOperation;
    private String codingMemoryDir = "";

    public CodingMemoryToolContext(String codingMemoryDir) {
        this.codingMemoryDir = codingMemoryDir;
        setNodeName("coding_memory");
    }

    public CodingMemoryToolContext(Workspace workspace, Object sysOperation, String codingMemoryDir) {
        this.workspace = workspace;
        this.sysOperation = sysOperation;
        this.codingMemoryDir = codingMemoryDir;
        setNodeName("coding_memory");
    }

    // ========== Static proxy methods for test compatibility ==========

    /**
     * Bind coding memory runtime context (static proxy for tests).
     */
    public static void bind(Workspace workspace, Object sysOperation, String codingMemoryDir) {
        CodingMemoryTools.bindCodingMemoryRuntime(workspace, sysOperation, codingMemoryDir);
    }

    /**
     * Reset/clear coding memory runtime context (static proxy for tests).
     */
    public static void reset() {
        CodingMemoryTools.clearCodingMemoryRuntime();
    }

    /**
     * Write content to a file in coding memory.
     *
     * @param path    the file path (relative or absolute)
     * @param content the content to write
     * @return result map with success, mode, and conflict_detected keys
     */
    public static Map<String, Object> write(String path, String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", path);
        try {
            String validatedPath = CodingMemoryTools.validateCodingMemoryPath(path);
            Path fullPath = Paths.get(validatedPath);

            Map<String, String> frontmatter = Frontmatter.parseFrontmatter(content);
            if (frontmatter == null) {
                result.put("success", false);
                result.put("error", "must contain frontmatter");
                return result;
            }

            Frontmatter.ValidationResult validation = Frontmatter.validateFrontmatter(frontmatter);
            if (!validation.isValid()) {
                result.put("success", false);
                result.put("error", validation.getErrorMessage());
                return result;
            }

            String body = Frontmatter.extractBody(content);
            if (body == null || body.isBlank()) {
                result.put("success", false);
                result.put("error", "no content body");
                return result;
            }

            boolean existed = Files.exists(fullPath);
            String mode = existed ? "update" : "create";

            // Ensure parent directory exists
            Files.createDirectories(fullPath.getParent());

            Map<String, String> enrichedFrontmatter = Frontmatter.enrichFrontmatter(
                    new LinkedHashMap<>(frontmatter), existed);
            String rebuiltContent = Frontmatter.rebuildContentWithFrontmatter(content, enrichedFrontmatter);
            if (existed) {
                String existing = Files.readString(fullPath);
                Files.writeString(fullPath, existing + "\n\n" + body, StandardOpenOption.TRUNCATE_EXISTING);
                mode = "append";
            } else {
                Files.writeString(fullPath, rebuiltContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }

            String indexDir = CodingMemoryTools.getCodingMemoryContext() != null
                    ? CodingMemoryTools.getCodingMemoryContext().getCodingMemoryDir()
                    : fullPath.getParent().toString();
            upsertMemoryIndex(indexDir, fullPath.getFileName().toString(), enrichedFrontmatter);

            result.put("success", true);
            result.put("mode", mode);
            result.put("type", enrichedFrontmatter.get("type"));
            result.put("path", fullPath.toString());
            result.put("conflict_detected", false);
            result.put("conflicting_files", Collections.emptyList());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * Read content from a file in coding memory.
     *
     * @param path   the file path
     * @param offset optional offset (unused in simple implementation)
     * @param limit  optional limit (unused in simple implementation)
     * @return result map with success and content keys
     */
    public static Map<String, Object> read(String path, Object offset, Object limit) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", path);
        try {
            String validatedPath = CodingMemoryTools.validateCodingMemoryPath(path);
            Path fullPath = Paths.get(validatedPath);
            if (!Files.exists(fullPath)) {
                result.put("success", false);
                result.put("content", "");
                result.put("error", "File not found: " + path);
                return result;
            }

            List<String> lines = Files.readAllLines(fullPath);
            int totalLines = lines.size();
            Integer firstLine = toInteger(offset);
            Integer lineLimit = toInteger(limit);
            int startIndex = firstLine != null ? Math.max(0, firstLine - 1) : 0;
            int endIndex = lineLimit != null ? Math.min(totalLines, startIndex + lineLimit) : totalLines;
            String content = String.join("\n", lines.subList(startIndex, endIndex));

            result.put("success", true);
            result.put("path", fullPath.toString());
            result.put("content", content);
            result.put("totalLines", totalLines);
            result.put("start_line", totalLines == 0 ? 0 : startIndex + 1);
            result.put("end_line", endIndex);
            result.put("truncated", lineLimit != null && endIndex < totalLines);
        } catch (Exception e) {
            result.put("success", false);
            result.put("content", "");
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * Edit content in a file in coding memory (replace old_text with new_text).
     *
     * @param path     the file path
     * @param oldText  the text to replace
     * @param newText  the new text
     * @return result map with success key
     */
    @Override
    public Map<String, Object> edit(String path, String oldText, String newText) {
        return doEdit(path, oldText, newText);
    }
    
    /**
     * Static convenience method for edit operation.
     *
     * @param path     the file path
     * @param oldText  the text to replace
     * @param newText  the new text
     * @return result map with success key
     */
    public static Map<String, Object> staticEdit(String path, String oldText, String newText) {
        return doEdit(path, oldText, newText);
    }
    
    /**
     * Internal edit implementation.
     */
    private static Map<String, Object> doEdit(String path, String oldText, String newText) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", path);
        try {
            if (oldText == null || oldText.isEmpty()) {
                result.put("success", false);
                result.put("error", "old_text cannot be empty");
                return result;
            }

            String validatedPath = CodingMemoryTools.validateCodingMemoryPath(path);
            Path fullPath = Paths.get(validatedPath);

            if (!Files.exists(fullPath)) {
                result.put("success", false);
                result.put("error", "File not found: " + path);
                return result;
            }

            String content = Files.readString(fullPath);
            int firstMatch = content.indexOf(oldText);
            if (firstMatch < 0) {
                result.put("success", false);
                result.put("error", "old_text not found in file");
                return result;
            }
            int secondMatch = content.indexOf(oldText, firstMatch + oldText.length());
            if (secondMatch >= 0) {
                result.put("success", false);
                result.put("error", "old_text appears 2 times, please be more specific");
                return result;
            }

            String replacement = newText != null ? newText : "";
            String newContent = content.substring(0, firstMatch) + replacement
                    + content.substring(firstMatch + oldText.length());
            Files.writeString(fullPath, newContent, StandardOpenOption.TRUNCATE_EXISTING);
            Map<String, String> frontmatter = Frontmatter.parseFrontmatter(newContent);
            if (frontmatter != null && Frontmatter.validateFrontmatter(frontmatter).isValid()) {
                String indexDir = CodingMemoryTools.getCodingMemoryContext() != null
                        ? CodingMemoryTools.getCodingMemoryContext().getCodingMemoryDir()
                        : fullPath.getParent().toString();
                upsertMemoryIndex(indexDir, fullPath.getFileName().toString(), frontmatter);
            }

            result.put("success", true);
            result.put("path", fullPath.toString());
            result.put("new_content", newContent);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * Upsert memory index entry.
     *
     * @param codingMemoryDir the coding memory directory
     * @param filePath        the file path
     * @param metadata        the metadata map
     */
    public static void upsertMemoryIndex(String codingMemoryDir, String filePath, Map<String, String> metadata) {
        try {
            Path indexPath = Paths.get(codingMemoryDir, "MEMORY.md");
            Files.createDirectories(indexPath.getParent());

            String filename = Paths.get(filePath).getFileName().toString();
            String name = metadata.getOrDefault("name", filename);
            String description = metadata.getOrDefault("description", "");
            String newEntry = "- [" + name + "](" + filename + ") - " + description;

            List<String> lines = Files.exists(indexPath)
                    ? new ArrayList<>(Files.readAllLines(indexPath))
                    : new ArrayList<>();
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
            if (lines.size() > CodingMemoryTools.MAX_INDEX_LINES) {
                lines = new ArrayList<>(lines.subList(0, CodingMemoryTools.MAX_INDEX_LINES));
            }
            Files.writeString(indexPath, String.join("\n", lines),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to update coding memory index", e);
        }
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }
}
