/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.checkpointer.redis.storage;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.step.StepCursor;
import com.openjiuwen.core.session.checkpointer.step.StepStatus;
import com.openjiuwen.core.foundation.store.BasedKVStorePipeline;
import com.openjiuwen.core.multitenant.TenantKVStoreKeyResolver;
import com.openjiuwen.extensions.store.kv.RedisStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
            Object state = baseSession.state().getState();
            if (state == null) {
                // Empty state needs no checkpoint write; distinguish from serialize failure.
                return CompletableFuture.completedFuture(null);
            }
            var stateBlob = serializeState(state);

            String dumpTypeKey = Checkpointer.resolveNsKey(
                    sessionId, Checkpointer.SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS_DUMP_TYPE);
            String blobKey = Checkpointer.resolveNsKey(
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

            String dumpTypeKey = Checkpointer.resolveNsKey(
                    sessionId, Checkpointer.SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS_DUMP_TYPE);
            String blobKey = Checkpointer.resolveNsKey(
                    sessionId, Checkpointer.SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS);

            BasedKVStorePipeline pipeline = redisStore.pipeline();
            pipeline.get(dumpTypeKey);
            pipeline.get(blobKey);
            List<Object> results = pipeline.execute().join();

            if (results == null || results.size() != KEY_NUMS) {
                return CompletableFuture.completedFuture(null);
            }
            if (results.get(0) == null && results.get(1) == null) {
                return CompletableFuture.completedFuture(null);
            }

            Object state = deserializeState(results.get(0), results.get(1));
            if (!(state instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("Redis agent state must be a Map");
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
            String dumpTypeKey = Checkpointer.resolveNsKey(
                    sessionId, Checkpointer.SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS_DUMP_TYPE);
            String blobKey = Checkpointer.resolveNsKey(
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

            String dumpTypeKey = Checkpointer.resolveNsKey(
                    sessionId, Checkpointer.SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS_DUMP_TYPE);
            String blobKey = Checkpointer.resolveNsKey(
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

    /**
     * Scan all persisted agent state blobs for cursors left in RUNNING state
     * by a crashed process. Reads every blob under the agent namespace through
     * the KV prefix query and deserializes the step cursor from the snapshot.
     *
     * @return one RUNNING cursor per session that has one, empty when none
     */
    public CompletableFuture<List<StepCursor>> scanRunningCursors() {
        try {
            Map<String, Object> blobs = redisStore
                    .getByPrefix(TenantKVStoreKeyResolver.resolvePrefix("")).join();
            List<StepCursor> running = new ArrayList<>();
            if (blobs == null || blobs.isEmpty()) {
                return CompletableFuture.completedFuture(running);
            }
            for (Map.Entry<String, Object> entry : blobs.entrySet()) {
                if (!entry.getKey().endsWith(":" + STATE_BLOBS)
                        || !entry.getKey().contains(":" + Checkpointer.SESSION_NAMESPACE_AGENT + ":")) {
                    continue;
                }
                Object dumpType = blobs.get(entry.getKey().substring(0,
                        entry.getKey().length() - STATE_BLOBS.length()) + STATE_BLOBS_DUMP_TYPE);
                Object state = deserializeState(dumpType, entry.getValue());
                if (!(state instanceof Map<?, ?> stateMap)) {
                    continue;
                }
                Map<String, Object> normalized = new LinkedHashMap<>();
                stateMap.forEach((key, value) -> normalized.put(String.valueOf(key), value));
                StepCursor cursor = StepCursor.fromState(normalized);
                if (cursor != null && cursor.getStatus() == StepStatus.RUNNING) {
                    running.add(cursor);
                }
            }
            return CompletableFuture.completedFuture(running);
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
