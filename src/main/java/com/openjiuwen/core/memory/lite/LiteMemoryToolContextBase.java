/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.core.common.logging.Loggers;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Common workspace-scoped state for MemoryIndexManager-backed tool surfaces.
 * <p>
 * Mirrors Python's {@code LiteMemoryToolContextBase} dataclass from
 * {@code core/memory/lite/memory_tool_context_base.py}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LiteMemoryToolContextBase {

    private Object workspace;
    private MemorySettings settings;
    private String agentId = "default";
    private Object embeddingConfig;
    private Object sysOperation;
    private Object manager;
    private String nodeName = "memory";

    /**
     * Check if the manager is initialized and not closed.
     */
    public boolean hasActiveManager() {
        if (manager == null) {
            return false;
        }
        try {
            // Check for closed flag via reflection or interface
            var method = manager.getClass().getMethod("isClosed");
            return !((Boolean) method.invoke(manager));
        } catch (Exception e) {
            return manager != null;
        }
    }

    /**
     * Search memory content.
     */
    public Map<String, Object> search(String query, int maxResults, double minScore) {
        if (this instanceof MemoryToolContext ctx) {
            return MemoryToolOps.memorySearchWithContext(ctx, query, maxResults, minScore, null).join();
        }
        return disabledMemoryResult(query, maxResults, minScore);
    }

    /**
     * Get memory content by path.
     */
    public Map<String, Object> get(String path, Integer fromLine, Integer lines) {
        if (this instanceof MemoryToolContext ctx) {
            return MemoryToolOps.memoryGetWithContext(ctx, path, fromLine, lines).join();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", path);
        result.put("text", "");
        result.put("from_line", fromLine);
        result.put("lines", lines);
        result.put("disabled", true);
        result.put("error", "Memory context not available");
        return result;
    }

    /**
     * Write memory content.
     *
     * @return result map with success status
     */
    public Map<String, Object> write(String path, String content, boolean append) {
        if (this instanceof MemoryToolContext ctx) {
            return MemoryToolOps.writeMemoryWithContext(ctx, path, content, append).join();
        }
        return failedFileResult(path, "Memory context not available");
    }

    /**
     * Edit memory content via string replacement.
     *
     * @return result map with success status
     */
    public Map<String, Object> edit(String path, String oldText, String newText) {
        if (this instanceof MemoryToolContext ctx) {
            return MemoryToolOps.editMemoryWithContext(ctx, path, oldText, newText).join();
        }
        return failedFileResult(path, "Memory context not available");
    }

    private Map<String, Object> disabledMemoryResult(String query, int maxResults, double minScore) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("max_results", maxResults);
        result.put("min_score", minScore);
        result.put("results", java.util.Collections.emptyList());
        result.put("disabled", true);
        result.put("error", "Memory context not available");
        return result;
    }

    private Map<String, Object> failedFileResult(String path, String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", path);
        result.put("success", false);
        result.put("error", error);
        return result;
    }
}
