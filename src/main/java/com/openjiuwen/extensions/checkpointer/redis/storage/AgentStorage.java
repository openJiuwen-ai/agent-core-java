/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.checkpointer.redis.storage;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.foundation.store.BasedKVStorePipeline;
import com.openjiuwen.extensions.store.kv.RedisStore;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Redis-based storage for agent session state.
 *
 * <p>Mirrors Python's {@code AgentStorage} in
 * {@code openjiuwen/extensions/checkpointer/redis/storage.py}.</p>
 */
public class AgentStorage extends BaseRedisStorage {

    private static final String STATE_BLOBS = "agent_state_blobs";
    private static final String STATE_BLOBS_DUMP_TYPE = "agent_state_blobs_dump_type";
    private static final int KEY_NUMS = 2;

    public AgentStorage(RedisStore redisStore, Map<String, Object> ttl) {
        super(redisStore, ttl);
    }

    /**
     * Save agent session state.
     */
    public CompletableFuture<Void> save(Object session) {
        try {
            BaseSession baseSession = requireSession(session);
            String sessionId = baseSession.sessionId();
            String agentId = resolveAgentId(baseSession);
            var stateBlob = serializeState(baseSession.state().getState());
            if (stateBlob == null) {
                log.warn("Failed to serialize state for agent {}, session {}", agentId, sessionId);
                return CompletableFuture.completedFuture(null);
            }

            String dumpTypeKey = Checkpointer.buildKeyWithNamespace(
                    sessionId, Checkpointer.SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS_DUMP_TYPE);
            String blobKey = Checkpointer.buildKeyWithNamespace(
                    sessionId, Checkpointer.SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS);

            BasedKVStorePipeline pipeline = redisStore.pipeline();
            pipeline.set(dumpTypeKey, stateBlob.type(), ttlSeconds);
            pipeline.set(blobKey, stateBlob.data(), ttlSeconds);
            pipeline.execute().join();
            return CompletableFuture.completedFuture(null);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(wrapFailure(throwable));
        }
    }

    /**
     * Recover agent session state.
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Void> recover(Object session, Object inputs) {
        try {
            BaseSession baseSession = requireSession(session);
            String sessionId = baseSession.sessionId();
            String agentId = resolveAgentId(baseSession);

            String dumpTypeKey = Checkpointer.buildKeyWithNamespace(
                    sessionId, Checkpointer.SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS_DUMP_TYPE);
            String blobKey = Checkpointer.buildKeyWithNamespace(
                    sessionId, Checkpointer.SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS);

            BasedKVStorePipeline pipeline = redisStore.pipeline();
            pipeline.get(dumpTypeKey);
            pipeline.get(blobKey);
            List<Object> results = pipeline.execute().join();

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
     */
    public CompletableFuture<Void> clear(String agentId, String sessionId) {
        try {
            String dumpTypeKey = Checkpointer.buildKeyWithNamespace(
                    sessionId, Checkpointer.SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS_DUMP_TYPE);
            String blobKey = Checkpointer.buildKeyWithNamespace(
                    sessionId, Checkpointer.SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS);
            redisStore.batchDelete(List.of(dumpTypeKey, blobKey), null).join();
            return CompletableFuture.completedFuture(null);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(wrapFailure(throwable));
        }
    }

    /**
     * Check if agent session exists.
     */
    public CompletableFuture<Boolean> exists(Object session) {
        try {
            BaseSession baseSession = requireSession(session);
            String sessionId = baseSession.sessionId();
            String agentId = resolveAgentId(baseSession);

            String dumpTypeKey = Checkpointer.buildKeyWithNamespace(
                    sessionId, Checkpointer.SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS_DUMP_TYPE);
            String blobKey = Checkpointer.buildKeyWithNamespace(
                    sessionId, Checkpointer.SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS);

            BasedKVStorePipeline pipeline = redisStore.pipeline();
            pipeline.exists(dumpTypeKey);
            pipeline.exists(blobKey);
            List<Object> results = pipeline.execute().join();
            boolean exists = results != null
                    && results.size() == KEY_NUMS
                    && keyExists(results.get(0))
                    && keyExists(results.get(1));
            return CompletableFuture.completedFuture(exists);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(wrapFailure(throwable));
        }
    }

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
