/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.persistence;

import com.openjiuwen.extensions.context_evolver.core.db_connector.MilvusConnector;
import com.openjiuwen.extensions.context_evolver.core.file_connector.JSONFileConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.core.persistence.MemoryPersistenceHelper}.
 * 
 * Shared persistence helper for memory PersistMemoryOp classes.
 * 
 * Handles persistence of {nodeId: nodeDict} data to a backend.
 * Supported values for persistType:
 * - "json"   – always use the local JSON file backend.
 * - "milvus" – always use the Milvus backend (raises if unavailable).
 * - "auto"   – probe Milvus once on first use; if the server is reachable use Milvus,
 *              otherwise fall back to JSON. The resolved backend is cached for the lifetime
 *              of this helper instance.
 */
public class MemoryPersistenceHelper {
    
    private static final Logger log = LoggerFactory.getLogger(MemoryPersistenceHelper.class);
    
    private final String persistType;
    private final String persistPath;
    private final String milvusHost;
    private final int milvusPort;
    private final String milvusCollection;
    
    // JSON connector is cheap — create eagerly.
    private final JSONFileConnector jsonConnector;
    // Milvus connector requires a live server — create lazily.
    private MilvusConnector milvusConnector;
    // Resolved backend for "auto" mode; null means not yet probed.
    private String resolvedType;
    
    /**
     * Create a MemoryPersistenceHelper with default settings.
     */
    public MemoryPersistenceHelper() {
        this("auto", "./memories/{algo_name}/{user_id}.json", "localhost", 19530, "vector_nodes");
    }
    
    /**
     * Create a MemoryPersistenceHelper.
     *
     * @param persistType      "auto" (default), "json", or "milvus"
     * @param persistPath      File-path template for the JSON backend.
     *                         {user_id} and {algo_name} are expanded at runtime.
     *                         Default: "./memories/{algo_name}/{user_id}.json"
     * @param milvusHost       Milvus server hostname (default: "localhost")
     * @param milvusPort       Milvus gRPC port (default: 19530)
     * @param milvusCollection Milvus collection name (default: "vector_nodes")
     */
    public MemoryPersistenceHelper(
        String persistType,
        String persistPath,
        String milvusHost,
        int milvusPort,
        String milvusCollection
    ) {
        this.persistType = persistType;
        this.persistPath = persistPath;
        this.milvusHost = milvusHost;
        this.milvusPort = milvusPort;
        this.milvusCollection = milvusCollection;
        
        // JSON connector is cheap — create eagerly.
        this.jsonConnector = new JSONFileConnector();
        // Milvus connector requires a live server — create lazily.
        this.milvusConnector = null;
        // Resolved backend for "auto" mode; null means not yet probed.
        this.resolvedType = null;
    }
    
    /**
     * The effective backend type after auto-detection, or null if not yet resolved.
     */
    public String getResolvedType() {
        return resolvedType;
    }
    
    /**
     * Inject a Milvus connector directly, bypassing the auto-probe.
     * 
     * Useful in tests to supply a mock connector without a live Milvus server.
     */
    public void setMilvusConnector(MilvusConnector connector) {
        this.milvusConnector = connector;
    }
    
    // ------------------------------------------------------------------
    // Auto-detection
    // ------------------------------------------------------------------
    
    /**
     * Return the effective backend type, probing Milvus if needed.
     * 
     * For persistType="auto" this method is called once on first use.
     * It tries to establish a Milvus connection; on success the resolved type is "milvus",
     * on any failure it falls back to "json". The result is cached in resolvedType.
     */
    private String resolveBackend() {
        if (!"auto".equals(persistType)) {
            return persistType;
        }
        
        if (resolvedType != null) {
            return resolvedType;
        }
        
        // Probe Milvus
        try {
            MilvusConnector conn = new MilvusConnector(
                milvusHost,
                milvusPort,
                milvusCollection,
                null,
                "default",
                "COSINE"
            );
            this.milvusConnector = conn;
            this.resolvedType = "milvus";
            log.info("Auto-detected Milvus at {}:{} — using Milvus persistence", milvusHost, milvusPort);
        } catch (Exception e) {
            this.resolvedType = "json";
            log.warn("Milvus not reachable at {}:{} ({}) — falling back to JSON persistence", 
                milvusHost, milvusPort, e.getMessage());
        }
        
        return resolvedType;
    }
    
    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------
    
    /**
     * Upsert nodesDict for userId into the configured backend.
     * 
     * For the JSON backend the existing file is loaded first so that data
     * from previous runs is merged rather than overwritten.
     * 
     * In "auto" mode, Milvus is probed on the first call; if it is reachable
     * the data is written to Milvus, otherwise to a JSON file.
     *
     * @param userId    User / workspace identifier
     * @param algoName  Short algorithm tag used in paths/namespaces ("ace", "rb", "reme")
     * @param nodesDict {nodeId: nodeDict} mapping produced by VectorNode.toDict()
     */
    public void save(String userId, String algoName, Map<String, Map<String, Object>> nodesDict) {
        if (nodesDict == null || nodesDict.isEmpty()) {
            log.debug("PersistMemoryHelper: nothing to persist for user={}", userId);
            return;
        }
        
        String backend = resolveBackend();
        if ("json".equals(backend)) {
            saveJson(userId, algoName, nodesDict);
        } else if ("milvus".equals(backend)) {
            saveMilvus(userId, algoName, nodesDict);
        } else {
            throw new IllegalArgumentException(
                "Unknown persistType '" + persistType + "'. Must be 'auto', 'json', or 'milvus'.");
        }
    }
    
    /**
     * Load previously persisted nodes for userId.
     * 
     * Returns an empty map when no data has been saved yet.
     * In "auto" mode, Milvus is probed on the first call.
     *
     * @param userId    User / workspace identifier
     * @param algoName  Short algorithm tag used in paths/namespaces
     * @return {nodeId: nodeDict} mapping
     */
    public Map<String, Map<String, Object>> load(String userId, String algoName) {
        String backend = resolveBackend();
        if ("json".equals(backend)) {
            return loadJson(userId, algoName);
        } else if ("milvus".equals(backend)) {
            return loadMilvus(userId, algoName);
        } else {
            throw new IllegalArgumentException(
                "Unknown persistType '" + persistType + "'. Must be 'auto', 'json', or 'milvus'.");
        }
    }
    
    // ------------------------------------------------------------------
    // JSON backend
    // ------------------------------------------------------------------
    
    private String jsonPath(String userId, String algoName) {
        return persistPath
            .replace("{user_id}", userId)
            .replace("{algo_name}", algoName);
    }
    
    private void saveJson(String userId, String algoName, Map<String, Map<String, Object>> nodesDict) {
        String path = jsonPath(userId, algoName);
        // Merge with existing data (upsert semantics)
        Map<String, Object> existing = new HashMap<>();
        if (jsonConnector.exists(path)) {
            existing = jsonConnector.loadFromFile(path);
        }
        existing.putAll(nodesDict);
        jsonConnector.saveToFile(path, existing);
        log.info("Persisted {} {} memories to JSON file: {}", nodesDict.size(), algoName, path);
    }
    
    private Map<String, Map<String, Object>> loadJson(String userId, String algoName) {
        String path = jsonPath(userId, algoName);
        if (!jsonConnector.exists(path)) {
            return new HashMap<>();
        }
        
        Map<String, Object> data = jsonConnector.loadFromFile(path);
        // Convert to Map<String, Map<String, Object>>
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getValue() instanceof Map) {
                result.put(entry.getKey(), (Map<String, Object>) entry.getValue());
            }
        }
        
        log.info("Loaded {} {} memories from JSON file: {}", result.size(), algoName, path);
        return result;
    }
    
    // ------------------------------------------------------------------
    // Milvus backend
    // ------------------------------------------------------------------
    
    private MilvusConnector getMilvus() {
        if (milvusConnector == null) {
            milvusConnector = new MilvusConnector(
                milvusHost,
                milvusPort,
                milvusCollection,
                null,
                "default",
                "COSINE"
            );
        }
        return milvusConnector;
    }
    
    private static String namespace(String userId, String algoName) {
        return "memory_" + algoName + "_" + userId;
    }
    
    private void saveMilvus(String userId, String algoName, Map<String, Map<String, Object>> nodesDict) {
        String ns = namespace(userId, algoName);
        getMilvus().saveToDb(ns, nodesDict);
        log.info("Persisted {} {} memories to Milvus namespace '{}'", nodesDict.size(), algoName, ns);
    }
    
    private Map<String, Map<String, Object>> loadMilvus(String userId, String algoName) {
        String ns = namespace(userId, algoName);
        MilvusConnector conn = getMilvus();
        if (!conn.exists(ns)) {
            return new HashMap<>();
        }
        Map<String, Map<String, Object>> data = conn.loadFromDb(ns);
        log.info("Loaded {} {} memories from Milvus namespace '{}'", data.size(), algoName, ns);
        return data;
    }
    
    // ------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------
    
    public String getPersistType() {
        return persistType;
    }
    
    public String getPersistPath() {
        return persistPath;
    }
    
    public String getMilvusHost() {
        return milvusHost;
    }
    
    public int getMilvusPort() {
        return milvusPort;
    }
    
    public String getMilvusCollection() {
        return milvusCollection;
    }
    
    @Override
    public String toString() {
        return "MemoryPersistenceHelper(" +
            "persistType='" + persistType + "', " +
            "persistPath='" + persistPath + "')";
    }
}