  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.extensions.checkpointer.redis.storage;

import com.openjiuwen.core.graph.store.Serializer;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.extensions.store.kv.RedisStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code openjiuwen.extensions.checkpointer.redis.storage.BaseRedisStorage}.
 *
 * <p>Base class for Redis-based storage implementations with common functionality.
 */
public abstract class BaseRedisStorage {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final RedisStore redisStore;
    protected final Serializer serializer;
    protected Integer ttlSeconds;
    protected boolean refreshOnRead;

    private static final String DEFAULT_TTL = "default_ttl";
    private static final int SECONDS_PER_MINUTE = 60;
    private static final String REFRESH_ON_READ = "refresh_on_read";

    protected BaseRedisStorage(RedisStore redisStore, Map<String, Object> ttl) {
        this.redisStore = redisStore;
        this.serializer = Serializer.create("java");
        this.ttlSeconds = null;
        this.refreshOnRead = false;

        if (ttl != null && ttl.containsKey(DEFAULT_TTL)) {
            this.ttlSeconds = (int) (((Number) ttl.get(DEFAULT_TTL)).doubleValue() * SECONDS_PER_MINUTE);
        }
        if (ttl != null && ttl.containsKey(REFRESH_ON_READ)) {
            this.refreshOnRead = Boolean.TRUE.equals(ttl.get(REFRESH_ON_READ));
        }
    }

    protected Serializer.TypedBytes serializeState(Object state) {
        if (state == null) {
            return null;
        }
        try {
            return serializer.dumpsTyped(state);
        } catch (RuntimeException e) {
            log.warn("Failed to serialize Redis state: {}", e.getMessage());
            return null;
        }
    }

    protected Object deserializeState(Object dumpType, Object blob) {
        if (dumpType == null || blob == null) {
            return null;
        }
        if (!(blob instanceof byte[] bytes)) {
            return null;
        }
        String dumpTypeText = decodeDumpType(dumpType);
        if (dumpTypeText.isEmpty()) {
            return null;
        }
        try {
            return serializer.loadsTyped(new Serializer.TypedBytes(dumpTypeText, bytes));
        } catch (RuntimeException e) {
            log.warn("Failed to deserialize Redis state: {}", e.getMessage());
            return null;
        }
    }

    protected String decodeDumpType(Object dumpType) {
        if (dumpType == null) {
            return "";
        }
        if (dumpType instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(dumpType);
    }

    protected CompletableFuture<Void> refreshTtl(List<String> keys, String entityName, String entityId) {
        if (!(refreshOnRead && ttlSeconds != null) || keys == null || keys.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            redisStore.refreshTtl(keys, ttlSeconds);
            log.debug("Refreshed TTL for {} {}", entityName, entityId);
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException e) {
            log.warn("Failed to refresh TTL for {} {}: {}", entityName, entityId, e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    protected BaseSession requireSession(Object session) {
        if (session instanceof BaseSession baseSession) {
            return baseSession;
        }
        throw new IllegalArgumentException("Redis checkpointer storage requires BaseSession");
    }

    protected InteractiveInput asInteractiveInput(Object inputs) {
        if (inputs instanceof InteractiveInput interactiveInput) {
            return interactiveInput;
        }
        return null;
    }

    protected boolean keyExists(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.longValue() > 0L;
        }
        return value != null;
    }

    protected RuntimeException wrapFailure(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new RuntimeException(throwable);
    }

    protected static String makeRedisKey(String... args) {
        return String.join(":", args);
    }
}
