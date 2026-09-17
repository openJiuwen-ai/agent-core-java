/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.checkpointer.redis;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.graph.store.GraphStoreState;
import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.multitenant.TenantKVStoreKeyResolver;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerProvider;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.extensions.checkpointer.redis.storage.AgentStorage;
import com.openjiuwen.extensions.checkpointer.redis.storage.GraphStore;
import com.openjiuwen.extensions.checkpointer.redis.storage.WorkflowStorage;
import com.openjiuwen.extensions.store.kv.RedisStore;
import com.openjiuwen.spi.store.KVStoreFactory;


import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis-based checkpointer implementation.
 * <p>
 * This checkpointer only interacts with RedisStore and does not directly use
 * Redis client APIs. All Redis operations are performed through RedisStore.
 * <p>
 * Mirrors Python's {@code openjiuwen.extensions.checkpointer.redis.checkpointer.RedisCheckpointer}.
 * 
 * @since 0.1.7
 */
public class RedisCheckpointer extends Checkpointer {
    private static final long CHECKPOINT_JOIN_POLL_MS = 1_000L;
    private static final long CHECKPOINT_JOIN_DEADLINE_MS = 5_000L;

    private final RedisStore redisStore;
    private final AgentStorage agentStorage;
    private final WorkflowStorage workflowStorage;
    private final GraphStore graphState;
    private final Store graphStoreAdapter;

    /**
     * Initialize RedisCheckpointer with a RedisStore instance.
     * 
     * @param redisStore The RedisStore instance for all Redis operations
     * @param ttl Optional TTL configuration for stored data
     * @since 0.1.7
     */
    public RedisCheckpointer(RedisStore redisStore, Map<String, Object> ttl) {
        this.redisStore = redisStore;
        this.agentStorage = new AgentStorage(redisStore, ttl);
        this.workflowStorage = new WorkflowStorage(redisStore, ttl);
        this.graphState = new GraphStore(redisStore, ttl);
        this.graphStoreAdapter = new RedisGraphStoreAdapter(graphState);
    }

    /**
     * Prepare agent execution by recovering checkpoint state from Redis.
     * 
     * @param session The session for the agent
     * @param inputs Input data to update in the session state
     * @since 0.1.7
     */
    @Override
    public void preAgentExecute(BaseSession session, Object inputs) {
        agentStorage.recover(session, inputs).join();
        if (inputs != null) {
            session.state().update(Map.of(Constant.INTERACTIVE_INPUT, List.of(inputs)));
        }
    }

    /**
     * Handle agent execution interruption by saving checkpoint state to Redis.
     * 
     * @param session The session for the agent
     * @since 0.1.7
     */
    @Override
    public void interruptAgentExecute(BaseSession session) {
        agentStorage.save(session).join();
    }

    /**
     * Finalize agent execution by saving checkpoint state to Redis.
     * <p>
     * On virtual-thread runtimes (JDK 21+), the Redis write runs on a dedicated
     * background thread and the caller waits for it to finish. The consumer-side
     * close hook interrupts the calling agent thread (stream cancel) right after
     * the END_FRAME is emitted; the fresh thread keeps the socket I/O of the
     * checkpoint write immune to that interrupt. On platform-thread runtimes
     * (JDK 17) the save runs inline, matching the historical behavior.
     *
     * @param session The session for the agent
     * @since 0.1.7
     */
    @Override
    public void postAgentExecute(BaseSession session) {
        if (!OpenJiuwenExecutors.isVirtualThreadSupported()) {
            agentStorage.save(session).join();
            return;
        }
        Thread writer = OpenJiuwenExecutors.newThread(
                () -> agentStorage.save(session).join(),
                "redis-checkpoint-" + session.sessionId(), true);
        writer.start();
        awaitCheckpointWriter(writer, session.sessionId());
    }

    /**
     * Wait for the checkpoint writer thread, tolerating interrupts directed at
     * the calling thread. A bounded poll keeps repeated interrupts from spinning;
     * past the deadline the wait is abandoned (the writer keeps running in the
     * background and still persists the state).
     *
     * @param writer checkpoint writer thread
     * @param sessionId session id for diagnostics
     */
    private void awaitCheckpointWriter(Thread writer, String sessionId) {
        long deadlineNanos = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(CHECKPOINT_JOIN_DEADLINE_MS);
        boolean isInterruptedDuringWait = false;
        while (writer.isAlive()) {
            long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
            if (remainingMs <= 0) {
                Loggers.SESSION.error("redis checkpoint join exceeded {}ms, sessionId={}, "
                                + "abandoning wait; writer continues in background",
                        CHECKPOINT_JOIN_DEADLINE_MS, sessionId);
                if (isInterruptedDuringWait) {
                    Thread.currentThread().interrupt();
                }
                return;
            }
            try {
                writer.join(Math.min(CHECKPOINT_JOIN_POLL_MS, remainingMs));
            } catch (InterruptedException e) {
                isInterruptedDuringWait = true;
            }
        }
        if (isInterruptedDuringWait) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Prepare workflow execution by recovering or clearing workflow state from Redis.
     * 
     * @param session The session for the workflow
     * @param inputs The interactive input for the workflow execution, or null for a fresh start
     * @since 0.1.7
     */
    @Override
    public void preWorkflowExecute(BaseSession session, InteractiveInput inputs) {
        if (inputs != null) {
            workflowStorage.recover(session, inputs).join();
            return;
        }

        if (!workflowStorage.isExists(session).join()) {
            return;
        }

        Object forceDelete = session.config() != null
                ? session.config().getEnv(SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY, false)
                : false;
        if (Boolean.TRUE.equals(forceDelete)) {
            String workflowId = getWorkflowId(session);
            graphState.delete(session.sessionId(), workflowId).join();
            workflowStorage.clear(workflowId, session.sessionId()).join();
            return;
        }

        throw ErrorHelper.buildError(StatusCode.CHECKPOINTER_PRE_WORKFLOW_EXECUTION_ERROR, "session_id",
                session.sessionId(), "workflow", getWorkflowId(session), "reason",
                "workflow state exists but non-interactive input and cleanup is disabled");
    }

    /**
     * Finalize workflow execution by saving or clearing workflow state in Redis.
     * <p>
     * If an exception occurred or the workflow was interrupted, the state is saved.
     * Otherwise, the workflow state is cleared.
     * 
     * @param session The session for the workflow
     * @param result The execution result
     * @param exception Any exception that occurred during execution
     * @since 0.1.7
     */
    @Override
    public void postWorkflowExecute(BaseSession session, Object result, Exception exception) {
        if (exception != null) {
            workflowStorage.save(session).join();
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(exception);
        }

        if (result instanceof Map<?, ?> resultMap && resultMap.containsKey(PregelConstants.TASK_STATUS_INTERRUPT)) {
            workflowStorage.save(session).join();
            return;
        }

        String workflowId = getWorkflowId(session);
        graphState.delete(session.sessionId(), workflowId).join();
        workflowStorage.clear(workflowId, session.sessionId()).join();
    }

    /**
     * Check if a session exists in Redis by looking up keys with the session ID prefix.
     * 
     * @param sessionId The session ID to check
     * @return {@code true} if at least one key exists for the session, {@code false} otherwise
     * @since 0.1.7
     */
    @Override
    public boolean sessionExists(String sessionId) {
        if (redisStore == null) {
            return false;
        }

        return !redisStore.getByPrefix(TenantKVStoreKeyResolver.resolvePrefix(sessionId + ":")).isEmpty();
    }

    /**
     * Release resources for a session in Redis.
     * <p>
     * If an agent ID is provided, only that agent's data is cleared.
     * Otherwise, all keys with the session ID prefix are deleted.
     * 
     * @param sessionId The session ID to release resources for
     * @param agentId The agent ID to release resources for a specific agent, or null to release all
     * @since 0.1.7
     */
    public void release(String sessionId, String agentId) {
        if (redisStore == null) {
            return;
        }

        if (agentId != null) {
            agentStorage.clear(agentId, sessionId).join();
        } else {
            redisStore.deleteByPrefix(TenantKVStoreKeyResolver.resolvePrefix(sessionId + ":"), 500);
        }
    }

    /**
     * Release all resources for a session in Redis.
     * 
     * @param sessionId The session ID to release resources for
     * @since 0.1.7
     */
    @Override
    public void release(String sessionId) {
        release(sessionId, null);
    }

    /**
     * Get the graph store.
     * 
     * @return The GraphStore instance
     * @since 0.1.7
     */
    public GraphStore getGraphStore() {
        return graphState;
    }

    /**
     * Get the agent storage.
     * 
     * @return The AgentStorage instance
     * @since 0.1.7
     */
    public AgentStorage getAgentStorage() {
        return agentStorage;
    }

    /**
     * Get the workflow storage.
     * 
     * @return The WorkflowStorage instance
     * @since 0.1.7
     */
    public WorkflowStorage getWorkflowStorage() {
        return workflowStorage;
    }

    /**
     * Get the underlying RedisStore instance.
     * 
     * @return The RedisStore instance
     * @since 0.1.7
     */
    public RedisStore getRedisStore() {
        return redisStore;
    }

    /**
     * Get the graph store adapter for graph state operations.
     * 
     * @return The Store adapter backed by the Redis graph store
     * @since 0.1.7
     */
    @Override
    public Store graphStore() {
        return graphStoreAdapter;
    }

    /**
     * Close the Redis client and release its connection pool.
     *
     * @since 0.1.14
     */
    @Override
    public void close() {
        redisStore.close();
    }

    /**
     * Provider for creating Redis checkpointers from the Python-compatible configuration map.
     * 
     * @since 0.1.7
     */
    public static final class Provider implements CheckpointerProvider {

        /**
         * typeName.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String typeName() {
            return "redis";
        }

        /**
         * Create a new RedisCheckpointer from the provided configuration map.
         * 
         * @param conf The configuration map containing connection and TTL settings
         * @return A new RedisCheckpointer instance
         * @since 0.1.7
         */
        @Override
        public Checkpointer create(Map<String, Object> conf) {
            if (conf != null && conf.containsKey("kv_store")) {
                if (conf.containsKey("connection") || conf.containsKey("storeRef")) {
                    throw new IllegalArgumentException("kv_store conflicts with connection or storeRef");
                }
                if (!(conf.get("kv_store") instanceof RedisStore store)) {
                    throw new IllegalArgumentException("Redis checkpointer requires a RedisStore");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> ttl = (Map<String, Object>) conf.get("ttl");
                return new RedisCheckpointer(store, ttl == null ? null : RedisTTLConfig.fromMap(ttl).toMap());
            }
            RedisCheckpointerConfig config;
            try {
                config = RedisCheckpointerConfig.fromMap(conf);
                config.validate();
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Invalid Redis checkpointer configuration: " + e.getMessage()
                        + ". Configuration must include a 'connection' map" + " with either 'redis_client' or 'url'.",
                        e);
            }

            RedisStore store = (RedisStore) KVStoreFactory.create("redis",
                    Map.of("connection", conf.get("connection")));
            return new RedisCheckpointer(store, config.getTtlMap());
        }
    }

    private static final class RedisGraphStoreAdapter implements Store {
        private final GraphStore delegate;

        /**
         * RedisGraphStoreAdapter.
         * 
         * @param delegate delegate
         * @since 0.1.7
         */
        private RedisGraphStoreAdapter(GraphStore delegate) {
            this.delegate = delegate;
        }

        /**
         * Retrieve the graph store state for the given session and namespace.
         * 
         * @param sessionId The session ID
         * @param ns        The namespace within the session
         * @return An Optional containing the GraphStoreState if found, otherwise empty
         * @since 0.1.7
         */
        @Override
        public Optional<GraphStoreState> get(String sessionId, String ns) {
            Object state = delegate.get(sessionId, ns).join();
            if (state instanceof GraphStoreState graphState) {
                return Optional.of(graphState);
            }
            return Optional.empty();
        }

        /**
         * Save the graph store state for the given session and namespace.
         * 
         * @param sessionId The session ID
         * @param ns        The namespace within the session
         * @param state     The graph store state to save
         * @since 0.1.7
         */
        @Override
        public void save(String sessionId, String ns, GraphStoreState state) {
            delegate.save(sessionId, ns, state).join();
        }

        /**
         * Delete the graph store state for the given session and namespace.
         * 
         * @param sessionId The session ID
         * @param ns        The namespace within the session
         * @since 0.1.7
         */
        @Override
        public void delete(String sessionId, String ns) {
            delegate.delete(sessionId, ns).join();
        }
    }

}
