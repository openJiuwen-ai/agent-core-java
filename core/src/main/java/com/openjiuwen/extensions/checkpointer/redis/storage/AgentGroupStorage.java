/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.checkpointer.redis.storage;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.foundation.store.BasedKVStorePipeline;
import com.openjiuwen.extensions.store.kv.RedisStore;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Stores only the shared/global state for an agent-team session.
 *
 * <p>Mirrors Python's {@code AgentGroupStorage} in
 * {@code openjiuwen/extensions/checkpointer/redis/storage.py}.</p>
 */
public class AgentGroupStorage extends BaseRedisStorage {

    private static final String STATE_BLOBS = "agent_group_state_blobs";
    private static final String STATE_BLOBS_DUMP_TYPE = "agent_group_state_blobs_dump_type";
    private static final int KEY_NUMS = 2;

    public AgentGroupStorage(RedisStore redisStore, Map<String, Object> ttl) {
        super(redisStore, ttl);
    }

    public CompletableFuture<Void> save(Object session) {
        try {
            BaseSession baseSession = requireSession(session);
            String sessionId = baseSession.sessionId();
            String teamId = resolveTeamId(baseSession);
            var stateBlob = serializeState(baseSession.state().getGlobal(null));
            if (stateBlob == null) {
                log.warn("Failed to serialize state for agent_team {}, session {}", teamId, sessionId);
                return CompletableFuture.completedFuture(null);
            }

            String dumpTypeKey = key(sessionId, teamId, STATE_BLOBS_DUMP_TYPE);
            String blobKey = key(sessionId, teamId, STATE_BLOBS);
            BasedKVStorePipeline pipeline = redisStore.pipeline();
            pipeline.set(dumpTypeKey, stateBlob.type(), ttlSeconds);
            pipeline.set(blobKey, stateBlob.data(), ttlSeconds);
            pipeline.execute().join();
            return CompletableFuture.completedFuture(null);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(wrapFailure(throwable));
        }
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<Void> recover(Object session, Object inputs) {
        try {
            BaseSession baseSession = requireSession(session);
            String sessionId = baseSession.sessionId();
            String teamId = resolveTeamId(baseSession);

            String dumpTypeKey = key(sessionId, teamId, STATE_BLOBS_DUMP_TYPE);
            String blobKey = key(sessionId, teamId, STATE_BLOBS);
            BasedKVStorePipeline pipeline = redisStore.pipeline();
            pipeline.get(dumpTypeKey);
            pipeline.get(blobKey);
            List<Object> results = pipeline.execute().join();
            if (results == null || results.size() != KEY_NUMS) {
                return CompletableFuture.completedFuture(null);
            }

            Object state = deserializeState(results.get(0), results.get(1));
            if (!(state instanceof Map<?, ?> stateMap)) {
                return CompletableFuture.completedFuture(null);
            }
            try {
                restoreGlobalState(baseSession, (Map<String, Object>) stateMap);
            } finally {
                refreshTtl(List.of(dumpTypeKey, blobKey), "agent_team", teamId).join();
            }
            return CompletableFuture.completedFuture(null);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(wrapFailure(throwable));
        }
    }

    public CompletableFuture<Void> clear(String teamId, String sessionId) {
        try {
            redisStore.batchDelete(List.of(
                    key(sessionId, teamId, STATE_BLOBS_DUMP_TYPE),
                    key(sessionId, teamId, STATE_BLOBS)), null).join();
            return CompletableFuture.completedFuture(null);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(wrapFailure(throwable));
        }
    }

    public CompletableFuture<Boolean> exists(Object session) {
        try {
            BaseSession baseSession = requireSession(session);
            String teamId = resolveTeamId(baseSession);
            String dumpTypeKey = key(baseSession.sessionId(), teamId, STATE_BLOBS_DUMP_TYPE);
            String blobKey = key(baseSession.sessionId(), teamId, STATE_BLOBS);
            BasedKVStorePipeline pipeline = redisStore.pipeline();
            pipeline.exists(dumpTypeKey);
            pipeline.exists(blobKey);
            List<Object> results = pipeline.execute().join();
            return CompletableFuture.completedFuture(results != null
                    && results.size() == KEY_NUMS
                    && keyExists(results.get(0))
                    && keyExists(results.get(1)));
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(wrapFailure(throwable));
        }
    }

    private static String key(String sessionId, String teamId, String suffix) {
        return Checkpointer.buildKeyWithNamespace(
                sessionId, Checkpointer.SESSION_NAMESPACE_AGENT_TEAM, teamId, suffix);
    }

    private String resolveTeamId(BaseSession session) {
        for (String methodName : List.of("groupId", "teamId")) {
            try {
                Method method = session.getClass().getMethod(methodName);
                Object value = method.invoke(session);
                if (value instanceof String text && !text.isBlank()) {
                    return text;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next Python-compatible/Java-compatible team id accessor.
            }
        }
        return session.sessionId();
    }

    private void restoreGlobalState(BaseSession session, Map<String, Object> state) {
        Object stateCollection = session.state();
        try {
            Object globalState = stateCollection.getClass().getMethod("getGlobalState").invoke(stateCollection);
            globalState.getClass().getMethod("setState", Map.class).invoke(globalState, state);
            return;
        } catch (ReflectiveOperationException ignored) {
            // Fall back to merging when the concrete state object has no direct global-state setter.
        }
        session.state().updateGlobal(state);
    }
}
