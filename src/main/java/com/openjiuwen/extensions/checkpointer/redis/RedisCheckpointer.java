/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.checkpointer.redis;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.graph.store.GraphStoreState;
import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerProvider;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.extensions.checkpointer.redis.storage.AgentStorage;
import com.openjiuwen.extensions.checkpointer.redis.storage.GraphStore;
import com.openjiuwen.extensions.checkpointer.redis.storage.WorkflowStorage;
import com.openjiuwen.extensions.store.kv.RedisStore;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis-based checkpointer implementation.
 *
 * <p>This checkpointer only interacts with RedisStore and does not directly use
 * Redis client APIs. All Redis operations are performed through RedisStore.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.checkpointer.redis.checkpointer.RedisCheckpointer}.
 *
 * @since 0.1.12
 */
public class RedisCheckpointer extends Checkpointer {

    private final RedisStore redisStore;
    private final AgentStorage agentStorage;
    private final WorkflowStorage workflowStorage;
    private final GraphStore graphState;
    private final Store graphStoreAdapter;

    /**
     * Initialize RedisCheckpointer with a RedisStore instance.
     *
     * @param redisStore The RedisStore instance for all Redis operations
     * @param ttl        Optional TTL configuration for stored data
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
     * @param inputs  Input data to update in the session state
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
     */
    @Override
    public void interruptAgentExecute(BaseSession session) {
        agentStorage.save(session).join();
    }

    /**
     * Finalize agent execution by saving checkpoint state to Redis.
     *
     * @param session The session for the agent
     */
    @Override
    public void postAgentExecute(BaseSession session) {
        agentStorage.save(session).join();
    }

    /**
     * Prepare workflow execution by recovering or clearing workflow state from Redis.
     *
     * @param session The session for the workflow
     * @param inputs  The interactive input for the workflow execution, or null for a fresh start
     * @throws RuntimeException if workflow state exists but no interactive input is provided and cleanup is disabled
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

        throw ErrorHelper.buildError(StatusCode.CHECKPOINTER_PRE_WORKFLOW_EXECUTION_ERROR,
                "session_id", session.sessionId(),
                "workflow", getWorkflowId(session),
                "reason", "workflow state exists but non-interactive input and cleanup is disabled");
    }

    /**
     * Finalize workflow execution by saving or clearing workflow state in Redis.
     *
     * <p>If an exception occurred or the workflow was interrupted, the state is saved.
     * Otherwise, the workflow state is cleared.
     *
     * @param session   The session for the workflow
     * @param result    The execution result
     * @param exception Any exception that occurred during execution
     * @throws RuntimeException if the provided exception is a runtime exception, or wrapped in one
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
     */
    @Override
    public boolean sessionExists(String sessionId) {
        if (redisStore == null) {
            return false;
        }

        return !redisStore.getByPrefix(sessionId + ":").isEmpty();
    }

    /**
     * Release resources for a session in Redis.
     *
     * <p>If an agent ID is provided, only that agent's data is cleared.
     * Otherwise, all keys with the session ID prefix are deleted.
     *
     * @param sessionId The session ID to release resources for
     * @param agentId   The agent ID to release resources for a specific agent, or null to release all
     */
    public void release(String sessionId, String agentId) {
        if (redisStore == null) {
            return;
        }

        if (agentId != null) {
            agentStorage.clear(agentId, sessionId).join();
        } else {
            redisStore.deleteByPrefix(sessionId + ":", 500);
        }
    }

    /**
     * Release all resources for a session in Redis.
     *
     * @param sessionId The session ID to release resources for
     */
    @Override
    public void release(String sessionId) {
        release(sessionId, null);
    }

    /**
     * Get the graph store.
     *
     * @return The GraphStore instance
     */
    public GraphStore getGraphStore() {
        return graphState;
    }

    /**
     * Get the agent storage.
     *
     * @return The AgentStorage instance
     */
    public AgentStorage getAgentStorage() {
        return agentStorage;
    }

    /**
     * Get the workflow storage.
     *
     * @return The WorkflowStorage instance
     */
    public WorkflowStorage getWorkflowStorage() {
        return workflowStorage;
    }

    /**
     * Get the underlying RedisStore instance.
     *
     * @return The RedisStore instance
     */
    public RedisStore getRedisStore() {
        return redisStore;
    }

    /**
     * Get the graph store adapter for graph state operations.
     *
     * @return The Store adapter backed by the Redis graph store
     */
    @Override
    public Store graphStore() {
        return graphStoreAdapter;
    }

    /**
     * Provider for creating Redis checkpointers from the Python-compatible configuration map.
     */
    public static final class Provider implements CheckpointerProvider {
        /**
         * Return the type name identifier for this provider.
         *
         * @return The string "redis"
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
         * @throws IllegalArgumentException if the configuration is invalid or missing required fields
         */
        @Override
        public Checkpointer create(Map<String, Object> conf) {
            RedisCheckpointerConfig config;
            try {
                config = RedisCheckpointerConfig.fromMap(conf);
                config.validate();
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                        "Invalid Redis checkpointer configuration: " + e.getMessage()
                                + ". Configuration must include a 'connection' map"
                                + " with either 'redis_client' or 'url'.",
                        e);
            }

            RedisConnectionConfig connection = config.getConnection();
            Object redisClient = connection.getRedisClient();
            if (redisClient == null) {
                String connectionUrl = connection.getConnectionUrl();
                if (connectionUrl == null) {
                    throw new IllegalArgumentException(
                            "Either 'redis_client' or 'url' must be provided in connection configuration");
                }
                redisClient = connection.isClusterMode()
                        ? new UrlBackedRedisClusterClient(connectionUrl, connection.getConnectionArgs())
                        : new UrlBackedRedisClient(connectionUrl, connection.getConnectionArgs());
            }

            return new RedisCheckpointer(new RedisStore(redisClient), config.getTtlMap());
        }
    }

    private static final class RedisGraphStoreAdapter implements Store {
        private final GraphStore delegate;

        private RedisGraphStoreAdapter(GraphStore delegate) {
            this.delegate = delegate;
        }

        /**
         * Retrieve the graph store state for the given session and namespace.
         *
         * @param sessionId The session ID
         * @param ns        The namespace within the session
         * @return An Optional containing the GraphStoreState if found, otherwise empty
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
         */
        @Override
        public void delete(String sessionId, String ns) {
            delegate.delete(sessionId, ns).join();
        }
    }

    private static class UrlBackedRedisClient {
        private final String url;
        private final Map<String, Object> connectionArgs;
        private final Map<String, Object> values = new ConcurrentHashMap<>();
        private final Map<String, Long> expiryAt = new ConcurrentHashMap<>();

        private UrlBackedRedisClient(String url, Map<String, Object> connectionArgs) {
            this.url = url;
            this.connectionArgs = new LinkedHashMap<>(connectionArgs);
        }

        /**
         * Get the Redis connection URL.
         *
         * @return The connection URL string
         */
        public String getUrl() {
            return url;
        }

        /**
         * Get the connection arguments map.
         *
         * @return An unmodifiable copy of the connection arguments
         */
        public Map<String, Object> getConnectionArgs() {
            return Map.copyOf(connectionArgs);
        }

        /**
         * Set a key-value pair without expiration.
         *
         * @param key   The key to set
         * @param value The value to associate with the key
         */
        public void set(String key, Object value) {
            cleanup(key);
            values.put(key, value);
            expiryAt.remove(key);
        }

        /**
         * Set a key-value pair with optional NX condition and expiration.
         *
         * @param key    The key to set
         * @param value  The value to associate with the key
         * @param nx     If true, only set when the key does not already exist
         * @param expiry Optional expiration time in seconds, or null for no expiration
         * @return {@code true} if the key was set, {@code false} if NX condition prevented it
         */
        public boolean set(String key, Object value, boolean nx, Integer expiry) {
            cleanup(key);
            if (nx && values.containsKey(key)) {
                return false;
            }
            values.put(key, value);
            if (expiry != null && expiry > 0) {
                expiryAt.put(key, System.currentTimeMillis() + Duration.ofSeconds(expiry).toMillis());
            } else {
                expiryAt.remove(key);
            }
            return true;
        }

        /**
         * Get the value associated with the given key.
         *
         * @param key The key to look up
         * @return The value associated with the key, or null if not found or expired
         */
        public Object get(String key) {
            cleanup(key);
            return values.get(key);
        }

        /**
         * Check if a key exists and is not expired.
         *
         * @param key The key to check
         * @return 1 if the key exists, 0 otherwise
         */
        public long isExists(String key) {
            cleanup(key);
            return values.containsKey(key) ? 1L : 0L;
        }

        /**
         * Delete one or more keys from the store.
         *
         * @param keys The keys to delete
         * @return The number of keys that were actually removed
         */
        public long delete(String... keys) {
            long deleted = 0L;
            for (String key : keys) {
                cleanup(key);
                if (values.remove(key) != null) {
                    expiryAt.remove(key);
                    deleted++;
                }
            }
            return deleted;
        }

        /**
         * Get multiple values by their keys.
         *
         * @param keys The keys to look up
         * @return A list of values in the same order as the provided keys; null for missing keys
         */
        public List<Object> mget(String... keys) {
            List<Object> results = new ArrayList<>(keys.length);
            for (String key : keys) {
                results.add(get(key));
            }
            return results;
        }

        /**
         * Scan keys matching the given pattern (supports trailing wildcard only).
         *
         * @param pattern The key pattern, typically ending with "*"
         * @return A sorted list of matching keys
         */
        public List<String> scanIter(String pattern) {
            String prefix = pattern.endsWith("*") ? pattern.substring(0, pattern.length() - 1) : pattern;
            List<String> keys = new ArrayList<>();
            for (String key : new ArrayList<>(values.keySet())) {
                cleanup(key);
                if (values.containsKey(key) && key.startsWith(prefix)) {
                    keys.add(key);
                }
            }
            keys.sort(String::compareTo);
            return keys;
        }

        /**
         * Set an expiration time on an existing key.
         *
         * @param key        The key to set expiration on
         * @param ttlSeconds The time-to-live in seconds
         * @return {@code true} if the expiration was set, {@code false} if the key does not exist
         */
        public boolean expire(String key, int ttlSeconds) {
            cleanup(key);
            if (!values.containsKey(key)) {
                return false;
            }
            expiryAt.put(key, System.currentTimeMillis() + Duration.ofSeconds(ttlSeconds).toMillis());
            return true;
        }

        /**
         * Create a new pipeline for batching Redis operations.
         *
         * @return A new UrlBackedRedisPipeline instance
         */
        public UrlBackedRedisPipeline pipeline() {
            return new UrlBackedRedisPipeline(this);
        }

        private void cleanup(String key) {
            Long expiresAt = expiryAt.get(key);
            if (expiresAt != null && expiresAt <= System.currentTimeMillis()) {
                values.remove(key);
                expiryAt.remove(key);
            }
        }
    }

    private static final class UrlBackedRedisClusterClient extends UrlBackedRedisClient {
        private UrlBackedRedisClusterClient(String url, Map<String, Object> connectionArgs) {
            super(url, connectionArgs);
        }
    }

    private static class UrlBackedRedisPipeline {
        private final UrlBackedRedisClient client;
        private final List<Runnable> operations = new ArrayList<>();

        private UrlBackedRedisPipeline(UrlBackedRedisClient client) {
            this.client = client;
        }

        /**
         * Queue an expiration operation in the pipeline.
         *
         * @param key        The key to set expiration on
         * @param ttlSeconds The time-to-live in seconds
         * @return This pipeline instance for method chaining
         */
        public UrlBackedRedisPipeline expire(String key, int ttlSeconds) {
            operations.add(() -> client.expire(key, ttlSeconds));
            return this;
        }

        /**
         * Execute all queued operations in the pipeline and clear the queue.
         *
         * @return An empty list (pipeline results are not collected)
         */
        public List<Object> execute() {
            operations.forEach(Runnable::run);
            operations.clear();
            return List.of();
        }
    }
}
