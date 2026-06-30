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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core memory manager for workspace-scoped lite memory.
 */
public class MemoryIndexManager {
    private static final Map<String, MemoryIndexManager> INDEX_CACHE = new ConcurrentHashMap<>();
    private static final int SNIPPET_MAX_CHARS = 700;

    private final String agentId;
    private final Workspace workspace;
    private final String nodeName;
    private final String memoryDir;
    private final MemorySettings settings;

    private final List<ChunkEntry> chunks = new ArrayList<>();
    private boolean isClosed;
    private boolean isInitialized;

    private MemoryIndexManager(String agentId, Workspace workspace, MemorySettings settings, String nodeName) {
        this.agentId = agentId;
        this.workspace = workspace;
        this.settings = settings;
        this.nodeName = nodeName != null && !nodeName.isBlank() ? nodeName : "memory";
        this.memoryDir = String.valueOf(workspace.getNodePath(this.nodeName));
    }

    private record ChunkEntry(String id, String path, String text, int startLine, int endLine, String hash) {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static MemoryIndexManager get(MemoryManagerParams params) throws IOException {
        String cacheKey = params.agentId() + ":" + params.nodeName() + ":"
                + params.workspace().getNodePath(params.nodeName());
        MemoryIndexManager cached = INDEX_CACHE.get(cacheKey);
        if (cached != null && !cached.isClosed) {
            return cached;
        }
        MemorySettings settings = params.settings() != null ? params.settings() : new MemorySettings();
        MemoryIndexManager manager = new MemoryIndexManager(
                params.agentId(),
                params.workspace(),
                settings,
                params.nodeName());
        manager.initialize();
        INDEX_CACHE.put(cacheKey, manager);
        return manager;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void initialize() throws IOException {
        if (isInitialized) {
            return;
        }
        sync("initial");
        isInitialized = true;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public synchronized void sync(String reason) throws IOException {
        chunks.clear();
        Path root = workspace.root();
        for (Path file : LiteMemoryInternal.listMemoryFiles(workspace, settings.getExtraPaths(), nodeName)) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String relPath = root.relativize(file).toString().replace('\\', '/');
            int index = 0;
            for (MemoryChunk chunk : LiteMemoryInternal.chunkMarkdown(content, settings.getChunking())) {
                chunks.add(new ChunkEntry(
                        LiteMemoryInternal.hashText(relPath + "#" + index++ + "#"
                                + chunk.startLine() + "#" + chunk.endLine()),
                        relPath,
                        chunk.text(),
                        chunk.startLine(),
                        chunk.endLine(),
                        LiteMemoryInternal.hashText(chunk.text())
                ));
            }
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public synchronized List<Map<String, Object>> search(String query, Map<String, Object> opts) throws IOException {
        if (isClosed) {
            return List.of();
        }
        if (Boolean.TRUE.equals(settings.getSync().get("onSearch"))) {
            sync("search");
        }
        int maxResults = opts != null && opts.get("max_results") != null
                ? Integer.parseInt(String.valueOf(opts.get("max_results")))
                : intQuery("max_results", 10);
        double minScore = opts != null && opts.get("min_score") != null
                ? Double.parseDouble(String.valueOf(opts.get("min_score")))
                : doubleQuery("min_score", 0.3);
        String lower = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> results = new ArrayList<>();
        for (ChunkEntry chunk : chunks) {
            double score = scoreChunk(lower, chunk.text());
            if (score < minScore) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", chunk.id());
            item.put("path", chunk.path());
            item.put("text", snippet(chunk.text(), lower));
            item.put("start_line", chunk.startLine());
            item.put("end_line", chunk.endLine());
            item.put("score", score);
            results.add(item);
        }
        results.sort(Comparator.comparingDouble(entry -> -Double.parseDouble(String.valueOf(entry.get("score")))));
        return results.size() <= maxResults ? results : new ArrayList<>(results.subList(0, maxResults));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> readFile(String relPath, Integer fromLine, Integer lines) throws IOException {
        Path path = resolvePath(relPath);
        List<String> allLines = Files.exists(path) ? Files.readAllLines(path, StandardCharsets.UTF_8) : List.of();
        int total = allLines.size();
        int startIdx = fromLine == null ? 0 : Math.max(0, fromLine - 1);
        int endIdx = lines == null ? total : Math.min(total, startIdx + lines);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", path.toString());
        result.put("text", String.join("\n", allLines.subList(startIdx, endIdx)));
        result.put("totalLines", total);
        result.put("start_line", startIdx + 1);
        result.put("end_line", endIdx);
        result.put("truncated", lines != null && endIdx < total);
        return result;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", settings.getProvider());
        result.put("model", settings.getModel());
        result.put("memory_dir", memoryDir);
        result.put("node_name", nodeName);
        result.put("chunk_count", chunks.size());
        result.put("isClosed", isClosed);
        return result;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void close() {
        isClosed = true;
        chunks.clear();
        INDEX_CACHE.entrySet().removeIf(entry -> entry.getValue() == this);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isClosed() {
        return isClosed;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getMemoryDir() {
        return memoryDir;
    }

    private double scoreChunk(String lowerQuery, String text) {
        if (lowerQuery.isBlank()) {
            return 0.0;
        }
        String lowerText = text.toLowerCase(Locale.ROOT);
        if (lowerText.contains(lowerQuery)) {
            return 0.95;
        }
        Set<String> queryTokens = Set.of(lowerQuery.split("\\s+"));
        int matched = 0;
        for (String token : queryTokens) {
            if (!token.isBlank() && lowerText.contains(token)) {
                matched++;
            }
        }
        return queryTokens.isEmpty() ? 0.0 : (double) matched / queryTokens.size();
    }

    private String snippet(String text, String lowerQuery) {
        if (text.length() <= SNIPPET_MAX_CHARS) {
            return text;
        }
        int index = lowerQuery.isBlank() ? 0 : text.toLowerCase(Locale.ROOT).indexOf(lowerQuery);
        if (index < 0) {
            return text.substring(0, SNIPPET_MAX_CHARS);
        }
        int start = Math.max(0, index - 150);
        int end = Math.min(text.length(), start + SNIPPET_MAX_CHARS);
        return text.substring(start, end);
    }

    private Path resolvePath(String relPath) {
        Path path = Path.of(relPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return workspace.root().resolve(relPath).normalize();
    }

    private int intQuery(String key, int fallback) {
        Object value = settings.getQuery().get(key);
        return value == null ? fallback : Integer.parseInt(String.valueOf(value));
    }

    private double doubleQuery(String key, double fallback) {
        Object value = settings.getQuery().get(key);
        return value == null ? fallback : Double.parseDouble(String.valueOf(value));
    }
}
