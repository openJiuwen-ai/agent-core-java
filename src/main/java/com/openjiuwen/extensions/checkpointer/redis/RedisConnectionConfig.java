/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.checkpointer.redis;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Redis connection configuration.
 *
 * <p>This class provides a structured way to configure Redis connections
 * with validation and type safety.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.checkpointer.redis.checkpointer.RedisConnectionConfig}.
 */
public class RedisConnectionConfig {

    /**
     * Pre-configured Redis client instance.
     */
    private Object redisClient;

    /**
     * Redis connection URL.
     */
    private String url;

    /**
     * Explicitly enable/disable cluster mode.
     */
    private Boolean clusterMode;

    /**
     * Additional connection arguments.
     */
    private Map<String, Object> connectionArgs;

    /**
     * Default constructor.
     */
    public RedisConnectionConfig() {
        this.connectionArgs = new LinkedHashMap<>();
    }

    /**
     * Constructor with URL.
     *
     * @param url Redis connection URL
     */
    public RedisConnectionConfig(String url) {
        this.url = url;
        this.connectionArgs = new LinkedHashMap<>();
    }

    /**
     * Constructor with pre-configured client.
     *
     * @param redisClient Pre-configured Redis client
     */
    public RedisConnectionConfig(Object redisClient) {
        this.redisClient = redisClient;
        this.connectionArgs = new LinkedHashMap<>();
    }

    /**
     * Create from a configuration map.
     *
     * @param config Configuration map
     * @return RedisConnectionConfig instance
     */
    public static RedisConnectionConfig fromMap(Map<String, Object> config) {
        if (config == null) {
            throw new IllegalArgumentException("Connection configuration cannot be null");
        }

        RedisConnectionConfig connectionConfig = new RedisConnectionConfig();
        connectionConfig.setRedisClient(config.get("redis_client"));
        Object urlValue = config.get("url");
        if (urlValue != null) {
            connectionConfig.setUrl(String.valueOf(urlValue));
        }
        
        if (config.get("cluster_mode") != null) {
            connectionConfig.setClusterMode((Boolean) config.get("cluster_mode"));
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) config.get("connection_args");
        connectionConfig.setConnectionArgs(args);

        return connectionConfig;
    }

    /**
     * Validate the configuration.
     *
     * @throws IllegalArgumentException if configuration is invalid
     */
    public void validate() {
        if (redisClient == null && (url == null || url.isBlank())) {
            throw new IllegalArgumentException(
                "Either 'redis_client' or 'url' must be provided in RedisConnectionConfig"
            );
        }

        if (url != null && !url.isBlank()) {
            if (!url.startsWith("redis://") && !url.startsWith("rediss://") &&
                !url.startsWith("redis+cluster://") && !url.startsWith("rediss+cluster://")) {
                throw new IllegalArgumentException(
                    "Invalid Redis URL format: " + url + ". " +
                    "URL must start with redis://, rediss://, redis+cluster://, or rediss+cluster://"
                );
            }
        }
    }

    /**
     * Determine if cluster mode should be used.
     *
     * @return True if cluster mode should be used
     */
    public boolean isClusterMode() {
        if (redisClient != null) {
            // Check if client is a cluster client
            return redisClient.getClass().getSimpleName().contains("Cluster");
        }

        if (clusterMode != null) {
            return clusterMode;
        }

        if (connectionArgs != null) {
            Object argsClusterMode = connectionArgs.get("cluster_mode");
            if (argsClusterMode instanceof Boolean booleanValue) {
                return booleanValue;
            }
            if (argsClusterMode instanceof String stringValue) {
                return Boolean.parseBoolean(stringValue);
            }
        }

        if (url != null) {
            return url.startsWith("redis+cluster://") || url.startsWith("rediss+cluster://");
        }

        return false;
    }

    /**
     * Get the connection URL, normalizing cluster URLs if needed.
     *
     * @return Normalized connection URL
     */
    public String getConnectionUrl() {
        if (url == null) {
            return null;
        }

        // Remove +cluster suffix for RedisCluster.from_url
        if (url.startsWith("redis+cluster://")) {
            return url.replace("redis+cluster://", "redis://");
        } else if (url.startsWith("rediss+cluster://")) {
            return url.replace("rediss+cluster://", "rediss://");
        }

        return url;
    }

    // Getters and Setters
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getRedisClient() {
        return redisClient;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setRedisClient(Object redisClient) {
        this.redisClient = redisClient;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setUrl(String url) {
        this.url = url != null ? url.trim() : null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Boolean getClusterMode() {
        return clusterMode;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setClusterMode(Boolean clusterMode) {
        this.clusterMode = clusterMode;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getConnectionArgs() {
        if (connectionArgs == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(connectionArgs);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setConnectionArgs(Map<String, Object> connectionArgs) {
        if (connectionArgs == null) {
            this.connectionArgs = new LinkedHashMap<>();
            return;
        }
        this.connectionArgs = new LinkedHashMap<>(connectionArgs);
    }
}
