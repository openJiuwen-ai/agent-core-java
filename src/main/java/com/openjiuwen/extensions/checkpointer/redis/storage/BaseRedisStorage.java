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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code openjiuwen.extensions.checkpointer.redis.storage.BaseRedisStorage}.
 * <p>
 * Base class for Redis-based storage implementations with common functionality.
 * 
 * @since 0.1.7
 */
public abstract class BaseRedisStorage {
    /**
     * log.
     * 
     * @since 0.1.7
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * redisStore.
     * 
     * @since 0.1.7
     */
    protected final RedisStore redisStore;

    /**
     * serializer.
     * 
     * @since 0.1.7
     */
    protected final Serializer serializer;

    /**
     * ttlSeconds.
     * 
     * @since 0.1.7
     */
    protected Integer ttlSeconds;

    /**
     * refreshOnRead.
     * 
     * @since 0.1.7
     */
    protected boolean refreshOnRead;

    private static final String DEFAULT_TTL = "default_ttl";
    private static final int SECONDS_PER_MINUTE = 60;
    private static final String REFRESH_ON_READ = "refresh_on_read";

    /**
     * BaseRedisStorage.
     * 
     * @param redisStore redisStore
     * @param ttl ttl
     * @since 0.1.7
     */
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

    /**
     * serializeState.
     * 
     * @param state state
     * @return the result
     * @since 0.1.7
     */
    protected Serializer.TypedBytes serializeState(Object state) {
        if (state == null) {
            return null;
        }
        try {
            return serializer.dumpsTyped(state);
        } catch (RuntimeException e) {
            log.warn("Failed to serialize Redis state: {}", rootCauseMessage(e));
            log.warn("Redis state diagnostic: {}", describeState(state));
            throw e;
        }
    }

    /**
     * deserializeState.
     * 
     * @param dumpType dumpType
     * @param blob blob
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * decodeDumpType.
     * 
     * @param dumpType dumpType
     * @return the result
     * @since 0.1.7
     */
    protected String decodeDumpType(Object dumpType) {
        if (dumpType == null) {
            return "";
        }
        if (dumpType instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(dumpType);
    }

    /**
     * refreshTtl.
     * 
     * @param keys keys
     * @param entityName entityName
     * @param entityId entityId
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * requireSession.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    protected BaseSession requireSession(Object session) {
        if (session instanceof BaseSession baseSession) {
            return baseSession;
        }
        throw new IllegalArgumentException("Redis checkpointer storage requires BaseSession");
    }

    /**
     * asInteractiveInput.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    protected InteractiveInput asInteractiveInput(Object inputs) {
        if (inputs instanceof InteractiveInput interactiveInput) {
            return interactiveInput;
        }
        return null;
    }

    /**
     * keyExists.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    protected boolean keyExists(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.longValue() > 0L;
        }
        return value != null;
    }

    /**
     * wrapFailure.
     * 
     * @param throwable throwable
     * @return the result
     * @since 0.1.7
     */
    protected RuntimeException wrapFailure(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new RuntimeException(throwable);
    }

    /**
     * rootCauseMessage.
     * 
     * @param throwable throwable
     * @return the result
     * @since 0.1.7
     */
    private String rootCauseMessage(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause.getClass().getSimpleName() + ": " + rootCause.getMessage();
    }

    /**
     * makeRedisKey.
     * 
     * @param args args
     * @return the result
     * @since 0.1.7
     */
    protected static String makeRedisKey(String... args) {
        return String.join(":", args);
    }

    /**
     * describeState.
     * 
     * @param state state
     * @return the result
     * @since 0.1.7
     */
    protected String describeState(Object state) {
        List<String> lines = new ArrayList<String>();
        appendStateDescription(lines, "root", state, 0);
        return String.join(" | ", lines);
    }

    @SuppressWarnings("unchecked")
    /**
     * appendStateDescription.
     * 
     * @param lines lines
     * @param path path
     * @param value value
     * @param depth depth
     * @since 0.1.7
     */
    private void appendStateDescription(List<String> lines, String path, Object value, int depth) {
        if (depth > 3) {
            lines.add(path + "=<max-depth>");
            return;
        }
        if (value == null) {
            lines.add(path + "=null");
            return;
        }

        lines.add(path + "=" + value.getClass().getName());

        if (value instanceof Map<?, ?> map) {
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (count >= 10) {
                    lines.add(path + ".<more>=truncated");
                    break;
                }
                String childKey = entry.getKey() != null ? String.valueOf(entry.getKey()) : "<null>";
                appendStateDescription(lines, path + "." + childKey, entry.getValue(), depth + 1);
                count++;
            }
            return;
        }

        if (value instanceof Collection<?> collection) {
            int index = 0;
            for (Object item : collection) {
                if (index >= 5) {
                    lines.add(path + "[<more>]=truncated");
                    break;
                }
                appendStateDescription(lines, path + "[" + index + "]", item, depth + 1);
                index++;
            }
            return;
        }

        if (value.getClass().isArray() && value instanceof Object[] array) {
            for (int i = 0; i < array.length && i < 5; i++) {
                appendStateDescription(lines, path + "[" + i + "]", array[i], depth + 1);
            }
        }
    }
}
