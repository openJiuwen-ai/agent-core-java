/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.core.common.logging.Loggers;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core memory index manager with SQLite-backed vector + FTS storage.
 * <p>
 * Manages memory chunks with embedding-based vector search and full-text search.
 * Uses an in-memory cache for recently accessed chunks and persists to a local
 * SQLite database.
 * <p>
 * <b>Note:</b> The full SQLite-based indexing is deferred to a future iteration.
 * This skeleton provides the lifecycle management, caching, and API surface.
 * <p>
 * Mirrors Python's {@code MemoryIndexManager} from
 * {@code memory/lite/manager.py}.
 */
public class MemoryIndexManager {

    private static final Map<String, MemoryIndexManager> INDEX_CACHE = new ConcurrentHashMap<>();

    private final String agentId;
    private final MemoryManagerParams params;
    private final String codingMemoryDir;
    private volatile boolean closed = false;
    private final Map<String, Map<String, String>> indexEntries = new ConcurrentHashMap<>();

    private MemoryIndexManager(MemoryManagerParams params) {
        this.params = params;
        this.agentId = params.getAgentId();
        this.codingMemoryDir = "";
    }

    /**
     * Create a MemoryIndexManager for a specific coding memory directory.
     * Used by CodingMemoryTools.
     */
    public MemoryIndexManager(String codingMemoryDir) {
        this.params = null;
        this.agentId = "coding_memory_" + codingMemoryDir.hashCode();
        this.codingMemoryDir = codingMemoryDir;
    }

    /**
     * Get or create a MemoryIndexManager for the given parameters.
     * <p>
     * Uses a global cache keyed by agent ID.
     */
    public static CompletableFuture<MemoryIndexManager> get(MemoryManagerParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String key = params.getAgentId();
            return INDEX_CACHE.computeIfAbsent(key, k -> {
                Loggers.MEMORY.info("[MemoryIndexManager] Creating for agent: {}", k);
                return new MemoryIndexManager(params);
            });
        });
    }

    /**
     * Search memory chunks by query string.
     */
    public CompletableFuture<List<MemoryChunk>> search(String query, int maxResults) {
        if (closed) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        // Full vector + FTS search deferred
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    /**
     * Add a memory chunk to the index.
     */
    public CompletableFuture<Void> addChunk(MemoryChunk chunk) {
        if (closed) {
            return CompletableFuture.completedFuture(null);
        }
        // Full indexing deferred
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Close the manager and release resources.
     */
    public CompletableFuture<Void> close() {
        closed = true;
        INDEX_CACHE.remove(agentId);
        return CompletableFuture.completedFuture(null);
    }

    public boolean isClosed() {
        return closed;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getCodingMemoryDir() {
        return codingMemoryDir;
    }

    /**
     * Upsert an index entry for a file.
     *
     * @param filePath    the file path
     * @param frontmatter the frontmatter map
     */
    public void upsertIndex(String filePath, Map<String, String> frontmatter) {
        if (closed) {
            return;
        }
        indexEntries.put(filePath, frontmatter != null ? frontmatter : new HashMap<>());
        Loggers.MEMORY.info("[MemoryIndexManager] Upserted index for: {}", filePath);
    }

    /**
     * Remove an index entry for a file.
     *
     * @param filePath the file path to remove
     */
    public void removeFromIndex(String filePath) {
        if (closed) {
            return;
        }
        indexEntries.remove(filePath);
        Loggers.MEMORY.info("[MemoryIndexManager] Removed index for: {}", filePath);
    }

    /**
     * Get all index entries.
     */
    public Map<String, Map<String, String>> getIndexEntries() {
        return Collections.unmodifiableMap(indexEntries);
    }
}
