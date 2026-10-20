/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.store;

import com.openjiuwen.core.context.offload.api.OffloadErrorCode;
import com.openjiuwen.core.context.offload.api.OffloadException;
import com.openjiuwen.core.context.offload.api.OffloadStore;
import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.model.OffloadPayload;
import com.openjiuwen.core.context.offload.model.OffloadRedisConfig;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis store over a dedicated {@link JedisPool}: {@code SET key value EX ttl},
 * GET, DEL, EXISTS; every {@code JedisException} maps to
 * {@code OffloadException(OFFLOAD_REDIS_DOWN)} so the composite chain can
 * fall through to the next backend (架构文档 §1.2, AC-2-06/07).
 *
 * @since 2026-01-01
 */
public class RedisOffloadStore implements OffloadStore, AutoCloseable {
    /** Minimum TTL (seconds) Redis accepts; smaller values are clamped. */
    private static final long MIN_REDIS_TTL_SECONDS = 1L;

    private final JedisPool pool;
    private final boolean hasOwnPool;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Builds a store owning its own pool built from the given config
     * (架构文档 §8.1: offload uses an independent pool, not the global one).
     *
     * @param config Redis connection settings
     */
    public RedisOffloadStore(OffloadRedisConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(config.getMaxTotal());
        poolConfig.setMaxIdle(config.getMaxIdle());
        poolConfig.setMinIdle(0);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setBlockWhenExhausted(true);
        this.pool = new JedisPool(poolConfig, config.getHost(), config.getPort(),
                config.getSoTimeoutMs(), config.getPassword(), config.getDatabase());
        this.hasOwnPool = true;
    }

    /**
     * Test-visible constructor injecting an externally managed pool.
     *
     * @param pool an externally managed Jedis pool
     */
    public RedisOffloadStore(JedisPool pool) {
        this.pool = Objects.requireNonNull(pool, "pool must not be null");
        this.hasOwnPool = false;
    }

    @Override
    public OffloadBackend backend() {
        return OffloadBackend.REDIS;
    }

    @Override
    public void save(OffloadNamespace ns, String handle, OffloadPayload payload, Duration ttl)
            throws OffloadException {
        String key = ns.toRedisKey(handle);
        try (Jedis jedis = pool.getResource()) {
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                jedis.set(key, payload.serializeJson());
            } else {
                jedis.setex(key, Math.max(MIN_REDIS_TTL_SECONDS, ttl.getSeconds()), payload.serializeJson());
            }
        } catch (JedisException ex) {
            throw new OffloadException(OffloadErrorCode.OFFLOAD_REDIS_DOWN,
                    "redis offload save failed: " + handle, ex);
        } catch (RuntimeException ex) {
            throw new OffloadException(OffloadErrorCode.OFFLOAD_REDIS_DOWN,
                    "redis offload save rejected: " + handle, ex);
        }
    }

    @Override
    public OffloadPayload load(OffloadNamespace ns, String handle) {
        String key = ns.toRedisKey(handle);
        try (Jedis jedis = pool.getResource()) {
            String json = jedis.get(key);
            if (json == null) {
                throw new OffloadException(OffloadErrorCode.OFFLOAD_MISSING, "offload key missing: " + handle);
            }
            try {
                return OffloadPayload.deserializeJson(json);
            } catch (IllegalStateException ex) {
                // Intentional: deserializeJson wraps JsonProcessingException in
                // IllegalStateException; all indicate a corrupt payload → OFFLOAD_CORRUPT.
                throw new OffloadException(OffloadErrorCode.OFFLOAD_CORRUPT,
                        "offload payload unparsable: " + handle, ex);
            }
        } catch (JedisException ex) {
            throw new OffloadException(OffloadErrorCode.OFFLOAD_REDIS_DOWN,
                    "redis offload load failed: " + handle, ex);
        }
    }

    @Override
    public boolean delete(OffloadNamespace ns, String handle) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.del(ns.toRedisKey(handle)) > 0;
        } catch (JedisException ex) {
            throw new OffloadException(OffloadErrorCode.OFFLOAD_REDIS_DOWN,
                    "redis offload delete failed: " + handle, ex);
        }
    }

    @Override
    public boolean exists(OffloadNamespace ns, String handle) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.exists(ns.toRedisKey(handle));
        } catch (JedisException ex) {
            throw new OffloadException(OffloadErrorCode.OFFLOAD_REDIS_DOWN,
                    "redis offload exists failed: " + handle, ex);
        }
    }

    @Override
    public void close() {
        if (hasOwnPool && closed.compareAndSet(false, true)) {
            pool.close();
        }
    }
}
