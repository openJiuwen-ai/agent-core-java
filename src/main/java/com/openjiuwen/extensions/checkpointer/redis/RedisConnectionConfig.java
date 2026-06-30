/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.checkpointer.redis;

import redis.clients.jedis.HostAndPort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redis connection configuration.
 *
 * <p>This class provides a structured way to configure Redis connections
 * with validation and type safety.
 *
 * <p>Mirrors Python's {@code RedisConnectionConfig} in
 * {@code openjiuwen/extensions/checkpointer/redis/checkpointer.py}.</p>
 */
public class RedisConnectionConfig {

    private static final int DEFAULT_TIMEOUT_MILLIS = 2000;

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
     * Redis cluster node addresses in host:port form.
     */
    private List<String> nodes = new ArrayList<>();

    /**
     * Redis password.
     */
    private String password;

    /**
     * Whether SSL should be used for the Redis connection.
     */
    private boolean ssl;

    /**
     * Redis connection timeout in milliseconds.
     */
    private Integer timeoutMillis;

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

        Object nodesValue = config.get("nodes");
        if (nodesValue != null) {
            connectionConfig.setNodes(parseNodesValue(nodesValue));
        }

        Object passwordValue = config.get("password");
        if (passwordValue != null) {
            connectionConfig.setPassword(String.valueOf(passwordValue));
        }

        Object sslValue = config.get("ssl");
        if (sslValue != null) {
            connectionConfig.setSsl(parseSslValue(sslValue));
        }

        Object timeoutValue = config.get("timeout_millis");
        if (timeoutValue != null) {
            connectionConfig.setTimeoutMillis(parseTimeoutValue(timeoutValue));
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
        if (redisClient == null && (url == null || url.isBlank()) && nodes.isEmpty()) {
            throw new IllegalArgumentException(
                "Either 'redis_client', 'url', or 'nodes' must be provided in RedisConnectionConfig"
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

        if (!nodes.isEmpty()) {
            getClusterNodes();
        }

        if (timeoutMillis != null && timeoutMillis <= 0) {
            throw new IllegalArgumentException("'timeout_millis' must be greater than 0");
        }
    }

    /**
     * Determine if cluster mode should be used.
     *
     * @return True if cluster mode should be used
     */
    public boolean isClusterMode() {
        if (!nodes.isEmpty()) {
            return true;
        }

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
    public Object getRedisClient() {
        return redisClient;
    }

    public void setRedisClient(Object redisClient) {
        this.redisClient = redisClient;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url != null ? url.trim() : null;
    }

    public Boolean getClusterMode() {
        return clusterMode;
    }

    public void setClusterMode(Boolean clusterMode) {
        this.clusterMode = clusterMode;
    }

    public List<String> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    public void setNodes(Collection<String> nodes) {
        this.nodes = new ArrayList<>();
        if (nodes == null) {
            return;
        }
        for (String node : nodes) {
            this.nodes.add(node == null ? null : node.trim());
        }
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password == null || password.isEmpty() ? null : password;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public int getTimeoutMillis() {
        return timeoutMillis == null ? DEFAULT_TIMEOUT_MILLIS : timeoutMillis;
    }

    public void setTimeoutMillis(Integer timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public Set<HostAndPort> getClusterNodes() {
        Set<HostAndPort> clusterNodes = new LinkedHashSet<>();
        for (String node : nodes) {
            clusterNodes.add(parseNode(node));
        }
        return clusterNodes;
    }

    public Map<String, Object> getConnectionArgs() {
        if (connectionArgs == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(connectionArgs);
    }

    public void setConnectionArgs(Map<String, Object> connectionArgs) {
        if (connectionArgs == null) {
            this.connectionArgs = new LinkedHashMap<>();
            return;
        }
        this.connectionArgs = new LinkedHashMap<>(connectionArgs);
    }

    public static HostAndPort parseNode(String node) {
        if (node == null || node.isBlank()) {
            throw new IllegalArgumentException("Redis cluster node must be in host:port form");
        }

        String trimmedNode = node.trim();
        int separatorIndex = trimmedNode.lastIndexOf(':');
        if (separatorIndex <= 0 || separatorIndex == trimmedNode.length() - 1) {
            throw new IllegalArgumentException("Redis cluster node must be in host:port form: " + node);
        }

        String host = trimmedNode.substring(0, separatorIndex).trim();
        String portText = trimmedNode.substring(separatorIndex + 1).trim();
        if (host.isEmpty() || portText.isEmpty()) {
            throw new IllegalArgumentException("Redis cluster node must be in host:port form: " + node);
        }

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Redis cluster node port must be a number: " + node, e);
        }

        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Redis cluster node port must be between 1 and 65535: " + node);
        }

        return new HostAndPort(host, port);
    }

    private static List<String> parseNodesValue(Object nodesValue) {
        if (nodesValue instanceof Collection<?> nodeCollection) {
            List<String> parsedNodes = new ArrayList<>();
            for (Object node : nodeCollection) {
                parsedNodes.add(node == null ? null : String.valueOf(node));
            }
            return parsedNodes;
        }

        if (nodesValue instanceof String stringValue) {
            return List.of(stringValue);
        }

        throw new IllegalArgumentException("'nodes' must be a collection or string");
    }

    private static boolean parseSslValue(Object sslValue) {
        if (sslValue instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (sslValue instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        throw new IllegalArgumentException("'ssl' must be a boolean or string");
    }

    private static Integer parseTimeoutValue(Object timeoutValue) {
        if (timeoutValue instanceof Number numberValue) {
            return numberValue.intValue();
        }
        if (timeoutValue instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'timeout_millis' must be a number", e);
            }
        }
        throw new IllegalArgumentException("'timeout_millis' must be a number or string");
    }
}
