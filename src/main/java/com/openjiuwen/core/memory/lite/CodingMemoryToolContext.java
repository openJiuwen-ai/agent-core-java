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
        Map<String, Object> result = new HashMap<>();
        try {
            String validatedPath = CodingMemoryTools.validateCodingMemoryPath(path);
            Path fullPath = Paths.get(validatedPath);

            boolean existed = Files.exists(fullPath);
            String mode = existed ? "update" : "create";

            // Ensure parent directory exists
            Files.createDirectories(fullPath.getParent());

            // Write content
            Files.writeString(fullPath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            result.put("success", true);
            result.put("mode", mode);
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
        Map<String, Object> result = new HashMap<>();
        try {
            String content = CodingMemoryTools.readFileSafe(path);
            result.put("success", true);
            result.put("content", content);
        } catch (Exception e) {
            result.put("success", false);
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
    public static Map<String, Object> edit(String path, String oldText, String newText) {
        Map<String, Object> result = new HashMap<>();
        try {
            String validatedPath = CodingMemoryTools.validateCodingMemoryPath(path);
            Path fullPath = Paths.get(validatedPath);

            if (!Files.exists(fullPath)) {
                result.put("success", false);
                result.put("error", "File not found: " + path);
                return result;
            }

            String content = Files.readString(fullPath);
            if (!content.contains(oldText)) {
                result.put("success", false);
                result.put("error", "Old text not found in file");
                return result;
            }

            String newContent = content.replace(oldText, newText);
            Files.writeString(fullPath, newContent, StandardOpenOption.TRUNCATE_EXISTING);

            result.put("success", true);
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
        CodingMemoryTools.upsertMemoryIndex(filePath, metadata);
    }
}