/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.extensions.store.kv;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.Connection;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.util.JedisURIHelper;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;

/**
 * Optional Jedis client factory, loaded only for Core-created Redis
 * connections.
 */
public final class JedisRedisClientFactory {
    private JedisRedisClientFactory() {
    }

    public static Object create(RedisConnectionConfig connection) {
        connection.validate();
        try {
            return createValidated(connection);
        } catch (IllegalArgumentException | redis.clients.jedis.exceptions.JedisException e) {
            throw new IllegalArgumentException("Failed to create Redis client", e);
        }
    }

    private static Object createValidated(RedisConnectionConfig connection) {
        URI uri = URI.create(connection.getConnectionUrl());
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("Redis URL requires a host");
        }
        HostAndPort endpoint = JedisURIHelper.getHostAndPort(uri);
        DefaultJedisClientConfig config = clientConfig(uri, connection.getConnectionArgs());
        GenericObjectPoolConfig<Connection> pool = poolConfig(connection.getConnectionArgs());
        if (connection.isClusterMode()) {
            if (config.getDatabase() != 0) {
                throw new IllegalArgumentException("Redis Cluster supports only database 0");
            }
            return new JedisCluster(Set.of(endpoint), config, attempts(connection.getConnectionArgs()), pool);
        }
        return new JedisPooled(endpoint, config, pool);
    }

    static DefaultJedisClientConfig clientConfig(URI uri, Map<String, Object> args) {
        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder()
                .database(JedisURIHelper.getDBIndex(uri)).ssl(JedisURIHelper.isRedisSSLScheme(uri));
        String user = JedisURIHelper.getUser(uri);
        String password = JedisURIHelper.getPassword(uri);
        if (args.containsKey("username")) {
            user = (String) args.get("username");
        }
        if (args.containsKey("password")) {
            password = (String) args.get("password");
        }
        if (user != null) {
            builder.user(user);
        }
        if (password != null) {
            builder.password(password);
        }
        if (args.containsKey("database")) {
            builder.database(integer(args.get("database"), "database", 0));
        }
        if (args.containsKey("socket_connect_timeout")) {
            builder.connectionTimeoutMillis(timeout(args.get("socket_connect_timeout")));
        }
        if (args.containsKey("socket_timeout")) {
            builder.socketTimeoutMillis(timeout(args.get("socket_timeout")));
        }
        if (args.containsKey("ssl_socket_factory")) {
            builder.sslSocketFactory((SSLSocketFactory) args.get("ssl_socket_factory"));
        }
        if (args.containsKey("ssl_parameters")) {
            builder.sslParameters((SSLParameters) args.get("ssl_parameters"));
        }
        if (args.containsKey("hostname_verifier")) {
            builder.hostnameVerifier((HostnameVerifier) args.get("hostname_verifier"));
        }
        return builder.build();
    }

    static int timeout(Object raw) {
        if (!(raw instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() <= 0
                || n.doubleValue() * 1000 > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Redis timeout must be positive finite seconds within integer milliseconds");
        }
        return (int) Math.ceil(n.doubleValue() * 1000);
    }

    private static int integer(Object raw, String key, int min) {
        if (!(raw instanceof Number n) || !Double.isFinite(n.doubleValue())
                || n.doubleValue() != Math.rint(n.doubleValue())
                || n.doubleValue() < min || n.doubleValue() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(key + " must be an integer >= " + min);
        }
        return n.intValue();
    }

    static int attempts(Map<String, Object> args) {
        Object raw = args.get("retry");
        if (raw == null) {
            return JedisCluster.DEFAULT_MAX_ATTEMPTS;
        }
        if (!(raw instanceof Map<?, ?> retry)) {
            throw new IllegalArgumentException("retry must be a map");
        }
        return retry.containsKey("attempts") ? integer(retry.get("attempts"), "retry.attempts", 1)
                : JedisCluster.DEFAULT_MAX_ATTEMPTS;
    }

    private static GenericObjectPoolConfig<Connection> poolConfig(Map<String, Object> args) {
        GenericObjectPoolConfig<Connection> pool = new GenericObjectPoolConfig<>();
        if (!args.containsKey("pool")) {
            return pool;
        }
        if (!(args.get("pool") instanceof Map<?, ?> fields)) {
            throw new IllegalArgumentException("pool must be a map");
        }
        if (fields.containsKey("max_total")) {
            pool.setMaxTotal(integer(fields.get("max_total"), "pool.max_total", 1));
        }
        if (fields.containsKey("max_idle")) {
            pool.setMaxIdle(integer(fields.get("max_idle"), "pool.max_idle", 0));
        }
        if (fields.containsKey("min_idle")) {
            pool.setMinIdle(integer(fields.get("min_idle"), "pool.min_idle", 0));
        }
        if (fields.containsKey("max_wait")) {
            pool.setMaxWait(Duration.ofMillis(timeout(fields.get("max_wait"))));
        }
        if (pool.getMinIdle() > pool.getMaxIdle() || pool.getMaxIdle() > pool.getMaxTotal()) {
            throw new IllegalArgumentException("pool requires min_idle <= max_idle <= max_total");
        }
        return pool;
    }
}
