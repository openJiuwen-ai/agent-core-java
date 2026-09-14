/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.extensions.context_evolver.core.db_connector.MilvusConnector;
import com.openjiuwen.extensions.context_evolver.core.file_connector.JSONFileConnector;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared persistence helper for context evolver memory nodes.
 * <p>
 * Mirrors Python's {@code MemoryPersistenceHelper} in
 * {@code openjiuwen/extensions/context_evolver/core/persistence.py}.
 * </p>
 */
public class MemoryPersistenceHelper {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    private final String persistType;
    private final String persistPath;
    private final String milvusHost;
    private final int milvusPort;
    private final String milvusCollection;
    private final JSONFileConnector jsonConnector;
    private MilvusConnector milvusConnector;
    private String resolvedType;

    public MemoryPersistenceHelper() {
        this("auto", "./memories/{algo_name}/{user_id}.json", "localhost", 19530, "vector_nodes");
    }

    public MemoryPersistenceHelper(String persistType,
                                   String persistPath,
                                   String milvusHost,
                                   int milvusPort,
                                   String milvusCollection) {
        this(persistType, persistPath, milvusHost, milvusPort, milvusCollection, null);
    }

    public MemoryPersistenceHelper(String persistType,
                                   String persistPath,
                                   String milvusHost,
                                   int milvusPort,
                                   String milvusCollection,
                                   Path allowedRoot) {
        this.persistType = persistType == null ? "auto" : persistType;
        this.persistPath = persistPath == null ? "./memories/{algo_name}/{user_id}.json" : persistPath;
        this.milvusHost = milvusHost == null ? "localhost" : milvusHost;
        this.milvusPort = milvusPort;
        this.milvusCollection = milvusCollection == null ? "vector_nodes" : milvusCollection;
        Path root = allowedRoot != null
                ? allowedRoot
                : Path.of("").toAbsolutePath().normalize();
        this.jsonConnector = new JSONFileConnector(root);
    }

    public String getResolvedType() {
        return resolvedType;
    }

    public String getPersistType() {
        return persistType;
    }

    public String getPersistPath() {
        return persistPath;
    }

    public void setMilvusConnector(MilvusConnector connector) {
        this.milvusConnector = connector;
    }

    public void save(String userId, String algoName, Map<String, Object> nodesDict) {
        if (nodesDict == null || nodesDict.isEmpty()) {
            LOGGER.debug("PersistMemoryHelper: nothing to persist for user=%s", userId);
            return;
        }

        String backend = resolveBackend();
        switch (backend) {
            case "json" -> saveJson(userId, algoName, nodesDict);
            case "milvus" -> saveMilvus(userId, algoName, nodesDict);
            default -> throw unknownPersistType();
        }
    }

    public Map<String, Object> load(String userId, String algoName) {
        String backend = resolveBackend();
        return switch (backend) {
            case "json" -> loadJson(userId, algoName);
            case "milvus" -> loadMilvus(userId, algoName);
            default -> throw unknownPersistType();
        };
    }

    public String jsonPath(String userId, String algoName) {
        validateFileNameComponent(userId, "User ID");
        validateFileNameComponent(algoName, "Algorithm name");
        return persistPath
                .replace("{user_id}", userId == null ? "" : userId)
                .replace("{algo_name}", algoName == null ? "" : algoName);
    }

    static void validateFileNameComponent(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
        if (".".equals(value) || value.contains("..") || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException(label + " contains an invalid path sequence.");
        }
        Path component = Path.of(value);
        if (component.isAbsolute() || component.getNameCount() != 1) {
            throw new IllegalArgumentException(label + " must be a single file-name component.");
        }
    }

    public void saveJson(String userId, String algoName, Map<String, Object> nodesDict) {
        String path = jsonPath(userId, algoName);
        Map<String, Object> existing = new LinkedHashMap<>();
        if (jsonConnector.exists(path)) {
            existing.putAll(jsonConnector.loadFromFile(path));
        }
        existing.putAll(nodesDict);
        jsonConnector.saveToFile(path, existing);
        LOGGER.info("Persisted %d %s memories to JSON file: %s", nodesDict.size(), algoName, path);
    }

    public Map<String, Object> loadJson(String userId, String algoName) {
        String path = jsonPath(userId, algoName);
        if (!jsonConnector.exists(path)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> data = jsonConnector.loadFromFile(path);
        LOGGER.info("Loaded %d %s memories from JSON file: %s", data.size(), algoName, path);
        return data;
    }

    public static String namespace(String userId, String algoName) {
        return "memory_" + algoName + "_" + userId;
    }

    public void saveMilvus(String userId, String algoName, Map<String, Object> nodesDict) {
        String namespace = namespace(userId, algoName);
        getMilvus().saveToDb(namespace, toNodeMap(nodesDict));
        LOGGER.info("Persisted %d %s memories to Milvus namespace '%s'", nodesDict.size(), algoName, namespace);
    }

    public Map<String, Object> loadMilvus(String userId, String algoName) {
        String namespace = namespace(userId, algoName);
        MilvusConnector connector = getMilvus();
        if (!connector.exists(namespace)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> data = new LinkedHashMap<>(connector.loadFromDb(namespace));
        LOGGER.info("Loaded %d %s memories from Milvus namespace '%s'", data.size(), algoName, namespace);
        return data;
    }

    private String resolveBackend() {
        if (!"auto".equals(persistType)) {
            return persistType;
        }
        if (resolvedType != null) {
            return resolvedType;
        }

        try {
            milvusConnector = newMilvusConnector();
            resolvedType = "milvus";
            LOGGER.info("Auto-detected Milvus at %s:%s - using Milvus persistence", milvusHost, milvusPort);
        } catch (RuntimeException exception) {
            resolvedType = "json";
            LOGGER.warning(
                    "Milvus not reachable at %s:%s (%s) - falling back to JSON persistence",
                    milvusHost,
                    milvusPort,
                    exception
            );
        }
        return resolvedType;
    }

    private MilvusConnector getMilvus() {
        if (milvusConnector == null) {
            milvusConnector = newMilvusConnector();
        }
        return milvusConnector;
    }

    private MilvusConnector newMilvusConnector() {
        return new MilvusConnector(milvusHost, milvusPort, milvusCollection, null, "default", "COSINE");
    }

    private IllegalArgumentException unknownPersistType() {
        return new IllegalArgumentException(
                "Unknown persist_type '" + persistType + "'. Must be 'auto', 'json', or 'milvus'."
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> toNodeMap(Map<String, Object> nodesDict) {
        Map<String, Map<String, Object>> converted = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : nodesDict.entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Milvus persistence requires each node value to be a map");
            }
            converted.put(entry.getKey(), new LinkedHashMap<>((Map<String, Object>) map));
        }
        return converted;
    }

    @Override
    public String toString() {
        return "MemoryPersistenceHelper(persist_type='"
                + persistType
                + "', persist_path='"
                + persistPath
                + "')";
    }
}
