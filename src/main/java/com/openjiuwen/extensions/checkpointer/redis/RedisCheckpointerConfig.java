/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.checkpointer.redis;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Complete configuration for Redis checkpointer.
 *
 * <p>This class provides a structured, type-safe configuration for Redis checkpointer
 * with automatic validation and sensible defaults.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.checkpointer.redis.checkpointer.RedisCheckpointerConfig}.
 */
public class RedisCheckpointerConfig {

    private static final String DEFAULT_DUMP_TYPE = "java";
    private static final String DUMP_TYPE = "dump_type";

    /**
     * Redis connection configuration.
     */
    private RedisConnectionConfig connection;

    /**
     * TTL configuration for stored data.
     */
    private RedisTTLConfig ttl;

    /**
     * Serialization protocol for checkpoint payloads.
     */
    private String dumpType = DEFAULT_DUMP_TYPE;

    /**
     * Default constructor.
     */
    public RedisCheckpointerConfig() {
    }

    /**
     * Constructor with connection config.
     *
     * @param connection Redis connection configuration
     */
    public RedisCheckpointerConfig(RedisConnectionConfig connection) {
        this.connection = connection;
    }

    /**
     * Constructor with all parameters.
     *
     * @param connection Redis connection configuration
     * @param ttl        TTL configuration for stored data
     */
    public RedisCheckpointerConfig(RedisConnectionConfig connection, RedisTTLConfig ttl) {
        this.connection = connection;
        this.ttl = ttl;
    }

    /**
     * Constructor with all parameters.
     *
     * @param connection Redis connection configuration
     * @param ttl        TTL configuration for stored data
     * @param dumpType   Serialization protocol for checkpoint payloads
     */
    public RedisCheckpointerConfig(RedisConnectionConfig connection, RedisTTLConfig ttl, String dumpType) {
        this.connection = connection;
        this.ttl = ttl;
        setDumpType(dumpType);
    }

    /**
     * Create from a configuration map.
     *
     * @param config Configuration map
     * @return RedisCheckpointerConfig instance
     */
    public static RedisCheckpointerConfig fromMap(Map<String, Object> config) {
        if (config == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> connectionMap = (Map<String, Object>) config.get("connection");
        if (connectionMap == null) {
            throw new IllegalArgumentException("'connection' is required in configuration");
        }

        RedisConnectionConfig connection = RedisConnectionConfig.fromMap(connectionMap);
        connection.validate();

        @SuppressWarnings("unchecked")
        Map<String, Object> ttlMap = (Map<String, Object>) config.get("ttl");
        RedisTTLConfig ttl = ttlMap != null ? RedisTTLConfig.fromMap(ttlMap) : null;

        Object dumpTypeValue = config.get(DUMP_TYPE);
        String dumpType = dumpTypeValue != null ? String.valueOf(dumpTypeValue) : DEFAULT_DUMP_TYPE;

        return new RedisCheckpointerConfig(connection, ttl, dumpType);
    }

    /**
     * Validate the configuration.
     */
    public void validate() {
        if (connection == null) {
            throw new IllegalArgumentException("Connection configuration is required");
        }
        connection.validate();
        validateDumpType(dumpType);
    }

    // Getters and Setters
    public RedisConnectionConfig getConnection() {
        return connection;
    }

    public void setConnection(RedisConnectionConfig connection) {
        this.connection = connection;
    }

    public RedisTTLConfig getTtl() {
        return ttl;
    }

    public void setTtl(RedisTTLConfig ttl) {
        this.ttl = ttl;
    }

    public String getDumpType() {
        return dumpType;
    }

    public void setDumpType(String dumpType) {
        this.dumpType = dumpType != null ? dumpType : DEFAULT_DUMP_TYPE;
        validateDumpType(this.dumpType);
    }

    /**
     * Convert the TTL portion into the storage-facing map contract.
     *
     * @return TTL map or {@code null} when unset
     */
    public Map<String, Object> getTtlMap() {
        return ttl != null ? ttl.toMap() : null;
    }

    /**
     * Convert storage-facing options into the map contract used by Redis storage helpers.
     *
     * @return storage configuration map containing TTL settings when present and dump type
     */
    public Map<String, Object> getStorageConfigMap() {
        Map<String, Object> storageConfig = new LinkedHashMap<>();
        if (ttl != null) {
            storageConfig.putAll(ttl.toMap());
        }
        storageConfig.put(DUMP_TYPE, dumpType);
        return storageConfig;
    }

    private static void validateDumpType(String dumpType) {
        if (!DEFAULT_DUMP_TYPE.equals(dumpType) && !"json".equals(dumpType)) {
            throw new IllegalArgumentException("Unsupported dump_type: " + dumpType);
        }
    }
}
