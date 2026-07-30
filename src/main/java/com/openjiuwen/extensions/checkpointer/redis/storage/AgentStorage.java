/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.checkpointer.redis.storage;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.multitenant.TenantKVStoreKeyResolver;
import com.openjiuwen.extensions.store.kv.RedisStore;
import com.openjiuwen.spi.store.KVStorePipeline;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code openjiuwen.extensions.checkpointer.redis.storage.AgentStorage}.
 * <p>
 * Redis-based storage for agent session state.
 * 
 * @since 0.1.7
 */
public class AgentStorage extends BaseRedisStorage {
    private static final String STATE_BLOBS = "agent_state_blobs";
    private static final String STATE_BLOBS_DUMP_TYPE = "agent_state_blobs_dump_type";
    private static final int KEY_NUMS = 2;

    /**
     * AgentStorage.
     * 
     * @param redisStore redisStore
     * @param ttl ttl
     * @since 0.1.7
     */
    public AgentStorage(RedisStore redisStore, Map<String, Object> ttl) {
        super(redisStore, ttl);
    }

    /**
     * Save agent session state.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Void> save(Object session) {
        try {
            BaseSession baseSession = requireSession(session);
            String sessionId = baseSession.sessionId();
            String agentId = resolveAgentId(baseSession);
            var stateBlob = serializeState(baseSession.state().getState());
            if (stateBlob == null) {
                // Null agent state means nothing to persist (not a serialization failure).
                return CompletableFuture.completedFuture(null);
            }

            String dumpTypeKey = TenantKVStoreKeyResolver.resolveKey(
                Checkpointer.buildKeyWithNamespace(sessionId, Checkpointer.SESSION_NAMESPACE_AGENT,
                    agentId, STATE_BLOBS_DUMP_TYPE));
            String blobKey = TenantKVStoreKeyResolver.resolveKey(
                Checkpointer.buildKeyWithNamespace(sessionId, Checkpointer.SESSION_NAMESPACE_AGENT,
                    agentId, STATE_BLOBS));

            KVStorePipeline pipeline = redisStore.pipeline();
            pipeline.set(dumpTypeKey, stateBlob.type(), ttlSeconds);
            pipeline.set(blobKey, stateBlob.data(), ttlSeconds);
            pipeline.execute();
            return CompletableFuture.completedFuture(null);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(wrapFailure(throwable));
        }
    }

    /**
     * recover.
     * 
     * @param session session
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Void> recover(Object session, Object inputs) {
        try {
            BaseSession baseSession = requireSession(session);
            String sessionId = baseSession.sessionId();
            String agentId = resolveAgentId(baseSession);

            String dumpTypeKey = TenantKVStoreKeyResolver.resolveKey(
                Checkpointer.buildKeyWithNamespace(sessionId, Checkpointer.SESSION_NAMESPACE_AGENT,
                    agentId, STATE_BLOBS_DUMP_TYPE));
            String blobKey = TenantKVStoreKeyResolver.resolveKey(
                Checkpointer.buildKeyWithNamespace(sessionId, Checkpointer.SESSION_NAMESPACE_AGENT,
                    agentId, STATE_BLOBS));

            KVStorePipeline pipeline = redisStore.pipeline();
            pipeline.get(dumpTypeKey);
            pipeline.get(blobKey);
            List<Object> results = pipeline.execute();

            if (results == null || results.size() != KEY_NUMS) {
                return CompletableFuture.completedFuture(null);
            }

            Object state = deserializeState(results.get(0), results.get(1));
            if (!(state instanceof Map<?, ?>)) {
                return CompletableFuture.completedFuture(null);
            }

            try {
                baseSession.state().setState((Map<String, Object>) state);
            } finally {
                refreshTtl(List.of(dumpTypeKey, blobKey), "agent", agentId).join();
            }
            return CompletableFuture.completedFuture(null);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(wrapFailure(throwable));
        }
    }

    /**
     * Clear agent session state.
     * 
     * @param agentId agentId
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Void> clear(String agentId, String sessionId) {
        try {
            String dumpTypeKey = TenantKVStoreKeyResolver.resolveKey(
                Checkpointer.buildKeyWithNamespace(sessionId, Checkpointer.SESSION_NAMESPACE_AGENT,
                    agentId, STATE_BLOBS_DUMP_TYPE));
            String blobKey = TenantKVStoreKeyResolver.resolveKey(
                Checkpointer.buildKeyWithNamespace(sessionId, Checkpointer.SESSION_NAMESPACE_AGENT,
                    agentId, STATE_BLOBS));
            redisStore.batchDelete(List.of(dumpTypeKey, blobKey), null);
            return CompletableFuture.completedFuture(null);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(wrapFailure(throwable));
        }
    }

    /**
     * Check if agent session exists.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Boolean> isExists(Object session) {
        try {
            BaseSession baseSession = requireSession(session);
            String sessionId = baseSession.sessionId();
            String agentId = resolveAgentId(baseSession);

            String dumpTypeKey = TenantKVStoreKeyResolver.resolveKey(
                Checkpointer.buildKeyWithNamespace(sessionId, Checkpointer.SESSION_NAMESPACE_AGENT,
                    agentId, STATE_BLOBS_DUMP_TYPE));
            String blobKey = TenantKVStoreKeyResolver.resolveKey(
                Checkpointer.buildKeyWithNamespace(sessionId, Checkpointer.SESSION_NAMESPACE_AGENT,
                    agentId, STATE_BLOBS));

            KVStorePipeline pipeline = redisStore.pipeline();
            pipeline.isExists(dumpTypeKey);
            pipeline.isExists(blobKey);
            List<Object> results = pipeline.execute();
            boolean exists =
                results != null && results.size() == KEY_NUMS && keyExists(results.get(0)) && keyExists(results.get(1));
            return CompletableFuture.completedFuture(exists);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(wrapFailure(throwable));
        }
    }

    /**
     * resolveAgentId.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private String resolveAgentId(BaseSession session) {
        try {
            Object agentId = session.getClass().getMethod("agentId").invoke(session);
            if (agentId instanceof String text && !text.isBlank()) {
                return text;
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall back to session id when the session does not expose agentId().
        }
        return session.sessionId();
    }
}
