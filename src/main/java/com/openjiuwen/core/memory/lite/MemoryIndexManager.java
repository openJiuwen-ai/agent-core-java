/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.harness.workspace.Workspace;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages memory indexing and search.
 *
 * <p>Mirrors Python's {@code MemoryIndexManager} in
 * {@code openjiuwen/core/memory/lite/manager.py}.</p>
 */
public class MemoryIndexManager implements LiteMemoryToolContextBase.MemoryIndexManagerView {

    public static final String META_KEY = "memory_index_meta_v1";
    public static final int SNIPPET_MAX_CHARS = 700;
    public static final String VECTOR_TABLE = "chunks_vec";
    public static final String FTS_TABLE = "chunks_fts";
    public static final String EMBEDDING_CACHE_TABLE = "embedding_cache";
    public static final int SESSION_DIRTY_DEBOUNCE_MS = 5000;

    private static final ConcurrentHashMap<String, MemoryIndexManager> INDEX_CACHE = new ConcurrentHashMap<>();

    private final String agentId;
    private final Workspace workspace;
    private final MemorySettings settings;
    private final String nodeName;
    private final String memoryDir;
    private Object embeddingConfig;
    private Object sysOperation;
    private Object llm;
    private boolean dirty = true;
    private boolean sessionsDirty;
    private boolean closed;

    public MemoryIndexManager(MemoryManagerParams params) {
        this.agentId = params.getAgentId() == null ? "default" : params.getAgentId();
        this.workspace = params.getWorkspace();
        this.settings = params.getSettings() == null ? new MemorySettings() : params.getSettings();
        this.nodeName = params.getNodeName() == null ? "memory" : params.getNodeName();
        Path nodePath = this.workspace == null ? null : this.workspace.getNodePath(this.nodeName);
        this.memoryDir = nodePath == null ? "" : nodePath.toString();
        this.embeddingConfig = params.getEmbeddingConfig();
        this.sysOperation = params.getSysOperation();
    }

    public static CompletableFuture<MemoryIndexManager> get(MemoryManagerParams params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (params == null || params.getWorkspace() == null) {
                    return null;
                }
                MemorySettings settings = params.getSettings() == null ? new MemorySettings() : params.getSettings();
                Path nodePath = params.getWorkspace().getNodePath(params.getNodeName());
                String memoryDir = nodePath == null ? "" : nodePath.toString();
                String cacheKey = params.getAgentId() + ":" + params.getNodeName() + ":" + memoryDir;
                MemoryIndexManager cached = INDEX_CACHE.get(cacheKey);
                if (cached != null && !cached.isClosed()) {
                    return cached;
                }
                MemoryManagerParams effectiveParams = new MemoryManagerParams(
                        params.getAgentId(),
                        params.getWorkspace(),
                        settings,
                        params.getEmbeddingConfig(),
                        params.getSysOperation(),
                        params.getNodeName()
                );
                MemoryIndexManager manager = new MemoryIndexManager(effectiveParams);
                manager.initialize().join();
                INDEX_CACHE.put(cacheKey, manager);
                return manager;
            } catch (Exception ex) {
                Loggers.MEMORY.error("Failed to initialize memory manager: {}", ex.getMessage(), ex);
                return null;
            }
        });
    }

    public static void clearMemoryManagerCache() {
        INDEX_CACHE.clear();
    }

    public static CompletableFuture<MemoryIndexManager> getMemoryManager(
            String agentId,
            Workspace workspace,
            MemorySettings settings
    ) {
        return get(new MemoryManagerParams(agentId, workspace, settings, null, null, "memory"));
    }

    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            MemoryLiteInternalUtils.ensureDir(memoryDir);
            dirty = true;
            Loggers.MEMORY.info("Memory manager initialized for agent: {}", agentId);
        });
    }

    public CompletableFuture<List<Map<String, Object>>> search(String query, Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> {
            if (closed) {
                return List.of();
            }
            String cleaned = query == null ? "" : query.trim();
            if (cleaned.isEmpty()) {
                return List.of();
            }
            int maxResults = intOption(options, "max_results", intSetting(settings.getQuery(), "max_results", 10));
            double minScore = doubleOption(options, "min_score", doubleSetting(settings.getQuery(), "min_score", 0.7d));
            List<Map<String, Object>> rows = new ArrayList<>();
            for (String file : MemoryLiteInternalUtils.listMemoryFiles(workspace, settings.getExtraPaths(), nodeName)) {
                try {
                    String text = Files.readString(Path.of(file), StandardCharsets.UTF_8);
                    int hit = text.toLowerCase().indexOf(cleaned.toLowerCase());
                    double score = hit >= 0 ? 1.0d : 0.0d;
                    if (score < minScore) {
                        continue;
                    }
                    int startLine = lineNumber(text, Math.max(hit, 0));
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", file + ":" + startLine + ":" + startLine);
                    row.put("path", Path.of(memoryDir).relativize(Path.of(file)).toString());
                    row.put("source", nodeName);
                    row.put("start_line", startLine);
                    row.put("end_line", startLine);
                    row.put("snippet", text.substring(0, Math.min(text.length(), SNIPPET_MAX_CHARS)));
                    row.put("score", score);
                    rows.add(row);
                    if (rows.size() >= maxResults) {
                        break;
                    }
                } catch (IOException | IllegalArgumentException ignored) {
                    // Python search skips unreadable files and continues.
                }
            }
            return rows;
        });
    }

    public CompletableFuture<Map<String, Object>> readFile(String relPath, Integer fromLine, Integer lines) {
        return CompletableFuture.supplyAsync(() -> {
            Path fullPath = resolveReadPath(relPath);
            try {
                String content = Files.readString(fullPath, StandardCharsets.UTF_8);
                String[] allLines = content.split("\\n", -1);
                int totalLines = allLines.length;
                int start = fromLine == null ? 0 : Math.max(0, fromLine - 1);
                int end = lines == null ? totalLines : Math.min(totalLines, start + lines);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("path", relPath);
                result.put("text", String.join("\n", List.of(allLines).subList(start, end)));
                result.put("totalLines", totalLines);
                result.put("fromLine", fromLine == null ? 1 : fromLine);
                result.put("toLine", (fromLine == null ? 1 : fromLine) + Math.max(0, end - start) - 1);
                return result;
            } catch (IOException ex) {
                Loggers.MEMORY.error("no available sys_operation when reading memory");
                return new LinkedHashMap<>();
            }
        });
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", !closed);
        result.put("provider", settings.getProvider());
        result.put("model", settings.getModel());
        result.put("files", MemoryLiteInternalUtils.listMemoryFiles(workspace, settings.getExtraPaths(), nodeName).size());
        result.put("chunks", 0);
        result.put("dirty", dirty);
        result.put("sessionsDirty", sessionsDirty);
        result.put("fts", Map.of("enabled", true, "available", false, "error", "sqlite FTS backend not initialized"));
        result.put("vector", Map.of("enabled", true, "available", false, "error", "sqlite vector backend not initialized"));
        result.put("cache", Map.of("enabled", settings.getCache().getOrDefault("enabled", true), "entries", 0));
        return result;
    }

    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            closed = true;
            INDEX_CACHE.entrySet().removeIf(entry -> entry.getValue() == this);
            Loggers.MEMORY.info("Memory manager closed");
        });
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getMemoryDir() {
        return memoryDir;
    }

    public MemorySettings getSettings() {
        return settings;
    }

    public Object getEmbeddingConfig() {
        return embeddingConfig;
    }

    public void setEmbeddingConfig(Object embeddingConfig) {
        this.embeddingConfig = embeddingConfig;
    }

    public Object getSysOperation() {
        return sysOperation;
    }

    public void setSysOperation(Object sysOperation) {
        this.sysOperation = sysOperation;
    }

    public Object getLlm() {
        return llm;
    }

    public void setLlm(Object llm) {
        this.llm = llm;
    }

    public static byte[] vectorToBlob(List<Float> embedding) {
        ByteBuffer buffer = ByteBuffer.allocate(embedding.size() * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (Float value : embedding) {
            buffer.putFloat(value == null ? 0.0f : value);
        }
        return buffer.array();
    }

    public static List<Float> blobToVector(byte[] blob) {
        ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
        List<Float> values = new ArrayList<>();
        while (buffer.remaining() >= Float.BYTES) {
            values.add(buffer.getFloat());
        }
        return values;
    }

    public static boolean isRecentSessionFile(String filename) {
        return filename != null && filename.matches("^\\d{4}-\\d{2}-\\d{2}\\.md$");
    }

    public static List<Map<String, Object>> mergeHybridResults(
            List<Map<String, Object>> vectorResults,
            List<Map<String, Object>> keywordResults,
            double vectorWeight,
            double textWeight
    ) {
        Map<Object, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> row : vectorResults == null ? List.<Map<String, Object>>of() : vectorResults) {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            copy.put("_vector_score", copy.get("score"));
            copy.put("_text_score", 0.0d);
            byId.put(copy.get("id"), copy);
        }
        for (Map<String, Object> row : keywordResults == null ? List.<Map<String, Object>>of() : keywordResults) {
            Map<String, Object> copy = byId.computeIfAbsent(row.get("id"), ignored -> new LinkedHashMap<>(row));
            copy.putIfAbsent("_vector_score", 0.0d);
            copy.put("_text_score", row.get("score"));
        }
        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map<String, Object> row : byId.values()) {
            double score = vectorWeight * asDouble(row.get("_vector_score")) + textWeight * asDouble(row.get("_text_score"));
            row.remove("_vector_score");
            row.remove("_text_score");
            row.put("score", score);
            merged.add(row);
        }
        merged.sort((left, right) -> Double.compare(asDouble(right.get("score")), asDouble(left.get("score"))));
        return merged;
    }

    private Path resolveReadPath(String relPath) {
        Path candidate = Path.of(relPath);
        if (candidate.isAbsolute()) {
            return candidate;
        }
        if ("USER.md".equals(relPath) && workspace != null) {
            Path user = workspace.getNodePath("USER.md");
            return user == null ? candidate : user;
        }
        return memoryDir == null || memoryDir.isBlank() ? candidate : Path.of(memoryDir).resolve(relPath).normalize();
    }

    private static int lineNumber(String text, int charIndex) {
        int line = 1;
        for (int i = 0; i < Math.min(charIndex, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static int intOption(Map<String, Object> options, String key, int defaultValue) {
        Object value = options == null ? null : options.get(key);
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private static double doubleOption(Map<String, Object> options, String key, double defaultValue) {
        Object value = options == null ? null : options.get(key);
        return value instanceof Number number ? number.doubleValue() : defaultValue;
    }

    private static int intSetting(Map<String, Object> options, String key, int defaultValue) {
        Object value = options == null ? null : options.get(key);
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private static double doubleSetting(Map<String, Object> options, String key, double defaultValue) {
        Object value = options == null ? null : options.get(key);
        return value instanceof Number number ? number.doubleValue() : defaultValue;
    }

    private static double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0d;
    }

    /**
     * Tracks incremental changes to a session file.
     *
     * <p>Mirrors Python's {@code SessionDeltaState} in
     * {@code openjiuwen/core/memory/lite/manager.py}.</p>
     */
    public record SessionDeltaState(int lastSize, int pendingBytes, int pendingMessages) {
    }
}
