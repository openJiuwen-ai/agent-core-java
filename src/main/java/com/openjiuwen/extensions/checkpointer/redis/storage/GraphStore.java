/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.extensions.checkpointer.redis.storage;

import com.openjiuwen.core.graph.store.GraphStoreState;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.extensions.store.kv.RedisStore;
import com.openjiuwen.spi.store.KVStorePipeline;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code openjiuwen.extensions.checkpointer.redis.storage.GraphStore}.
 *
 * <p>Redis-based graph state store implementation.
 */
public class GraphStore extends BaseRedisStorage {

    private static final String DATA_TYPE = "checkpoint_data_type";
    private static final String DATA_VALUE = "checkpoint_data_value";
    private static final int KEY_NUMS = 2;

    public GraphStore(RedisStore redisStore, Map<String, Object> ttl) {
        super(redisStore, ttl);
    }

    /**
     * Get graph state from Redis.
     */
    public CompletableFuture<Object> get(String sessionId, String ns) {
        try {
            String keyType = Checkpointer.buildKeyWithNamespace(
                    sessionId, Checkpointer.WORKFLOW_NAMESPACE_GRAPH, ns, DATA_TYPE);
            String keyValue = Checkpointer.buildKeyWithNamespace(
                    sessionId, Checkpointer.WORKFLOW_NAMESPACE_GRAPH, ns, DATA_VALUE);

            KVStorePipeline pipeline = redisStore.pipeline();
            pipeline.get(keyType);
            pipeline.get(keyValue);
            List<Object> results = pipeline.execute();

            if (results == null || results.size() != KEY_NUMS) {
                return CompletableFuture.completedFuture(null);
            }
            if (results.get(0) == null || results.get(1) == null) {
                return CompletableFuture.completedFuture(null);
            }

            try {
                Object state = deserializeState(results.get(0), results.get(1));
                if (state instanceof GraphStoreState) {
                    return CompletableFuture.completedFuture(state);
                }
                return CompletableFuture.completedFuture(null);
            } finally {
                refreshTtl(List.of(keyType, keyValue), "graph", sessionId + ":" + ns).join();
            }
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(wrapFailure(throwable));
        }
    }

    /**
     * Save graph state to Redis.
     */
    public CompletableFuture<Void> save(String sessionId, String ns, Object state) {
        try {
            var stateBlob = serializeState(state);
            if (stateBlob == null) {
                log.warn("Failed to serialize graph state for session {}, ns {}", sessionId, ns);
                return CompletableFuture.completedFuture(null);
            }

            String keyType = Checkpointer.buildKeyWithNamespace(
                    sessionId, Checkpointer.WORKFLOW_NAMESPACE_GRAPH, ns, DATA_TYPE);
            String keyValue = Checkpointer.buildKeyWithNamespace(
                    sessionId, Checkpointer.WORKFLOW_NAMESPACE_GRAPH, ns, DATA_VALUE);

            KVStorePipeline pipeline = redisStore.pipeline();
            pipeline.set(keyType, stateBlob.type(), ttlSeconds);
            pipeline.set(keyValue, stateBlob.data(), ttlSeconds);
            pipeline.execute();
            return CompletableFuture.completedFuture(null);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(wrapFailure(throwable));
        }
    }

    /**
     * Delete graph state from Redis.
     */
    public CompletableFuture<Void> delete(String sessionId, String ns) {
        try {
            if (ns == null || ns.isEmpty()) {
                redisStore.deleteByPrefix(Checkpointer.buildKey(sessionId, Checkpointer.WORKFLOW_NAMESPACE_GRAPH), 500);
            } else {
                redisStore.deleteByPrefix(
                        Checkpointer.buildKeyWithNamespace(sessionId, Checkpointer.WORKFLOW_NAMESPACE_GRAPH, ns), 500);
            }
            return CompletableFuture.completedFuture(null);
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(wrapFailure(throwable));
        }
    }
}
