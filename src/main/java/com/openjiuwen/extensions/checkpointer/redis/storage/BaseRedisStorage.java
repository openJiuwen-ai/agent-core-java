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
    protected final String dumpType;
    protected Integer ttlSeconds;
    protected boolean refreshOnRead;

    private static final String DEFAULT_DUMP_TYPE = "java";
    private static final String DUMP_TYPE = "dump_type";
    private static final String DEFAULT_TTL = "default_ttl";
    private static final int SECONDS_PER_MINUTE = 60;
    private static final String REFRESH_ON_READ = "refresh_on_read";

    protected BaseRedisStorage(RedisStore redisStore, Map<String, Object> config) {
        this.redisStore = redisStore;
        this.dumpType = resolveDumpType(config);
        this.serializer = Serializer.create(dumpType);
        this.ttlSeconds = null;
        this.refreshOnRead = false;

        if (config != null && config.containsKey(DEFAULT_TTL)) {
            this.ttlSeconds = (int) (((Number) config.get(DEFAULT_TTL)).doubleValue() * SECONDS_PER_MINUTE);
        }
        if (config != null && config.containsKey(REFRESH_ON_READ)) {
            this.refreshOnRead = Boolean.TRUE.equals(config.get(REFRESH_ON_READ));
        }
    }

    protected Serializer.TypedBytes serializeState(Object state) {
        if (state == null) {
            return null;
        }
        return serializer.dumpsTyped(state);
    }

    protected Object deserializeState(Object dumpType, Object blob) {
        if (dumpType == null && blob == null) {
            return null;
        }
        if (dumpType == null || blob == null) {
            throw new IllegalArgumentException("Redis checkpoint is incomplete: dump type and blob must both exist");
        }
        if (!(blob instanceof byte[] bytes)) {
            throw new IllegalArgumentException("Redis checkpoint blob must be byte[]");
        }
        String dumpTypeText = decodeDumpType(dumpType);
        validateStoredDumpType(dumpTypeText);
        return Serializer.create(dumpTypeText).loadsTyped(new Serializer.TypedBytes(dumpTypeText, bytes));
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

    private static String resolveDumpType(Map<String, Object> config) {
        Object dumpTypeValue = config != null ? config.get(DUMP_TYPE) : null;
        String resolvedDumpType = dumpTypeValue != null ? String.valueOf(dumpTypeValue) : DEFAULT_DUMP_TYPE;
        validateConfiguredDumpType(resolvedDumpType);
        return resolvedDumpType;
    }

    private static void validateConfiguredDumpType(String dumpType) {
        if (!DEFAULT_DUMP_TYPE.equals(dumpType) && !"json".equals(dumpType)) {
            throw new IllegalArgumentException("Unsupported dump_type: " + dumpType);
        }
    }

    private static void validateStoredDumpType(String dumpType) {
        if (!DEFAULT_DUMP_TYPE.equals(dumpType) && !"json".equals(dumpType)) {
            throw new IllegalArgumentException("Unsupported Redis checkpoint dump type: " + dumpType);
        }
    }

    protected static String makeRedisKey(String... args) {
        return String.join(":", args);
    }
}
