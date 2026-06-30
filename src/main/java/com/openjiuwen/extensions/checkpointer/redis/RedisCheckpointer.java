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
import com.openjiuwen.extensions.checkpointer.redis.storage.AgentGroupStorage;
import com.openjiuwen.extensions.checkpointer.redis.storage.AgentStorage;
import com.openjiuwen.extensions.checkpointer.redis.storage.GraphStore;
import com.openjiuwen.extensions.checkpointer.redis.storage.WorkflowStorage;
import com.openjiuwen.extensions.store.kv.JedisClusterRedisStore;
import com.openjiuwen.extensions.store.kv.RedisStore;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisCluster;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis-based checkpointer implementation.
 *
 * <p>This checkpointer only interacts with RedisStore and does not directly use
 * Redis client APIs. All Redis operations are performed through RedisStore.
 *
 * <p>Mirrors Python's {@code RedisCheckpointer} in
 * {@code openjiuwen/extensions/checkpointer/redis/checkpointer.py}.</p>
 */
public class RedisCheckpointer extends Checkpointer implements AutoCloseable {

    private final RedisStore redisStore;
    private final AutoCloseable ownedClient;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AgentStorage agentStorage;
    private final AgentGroupStorage agentGroupStorage;
    private final WorkflowStorage workflowStorage;
    private final GraphStore graphState;
    private final Store graphStoreAdapter;

    /**
     * Initialize RedisCheckpointer with a RedisStore instance.
     *
     * @param redisStore The RedisStore instance for all Redis operations
     * @param ttl        Optional storage configuration, including TTL and dump type
     */
    public RedisCheckpointer(RedisStore redisStore, Map<String, Object> ttl) {
        this(redisStore, ttl, null);
    }

    RedisCheckpointer(RedisStore redisStore, Map<String, Object> ttl, AutoCloseable ownedClient) {
        this.redisStore = redisStore;
        this.ownedClient = ownedClient;
        this.agentStorage = new AgentStorage(redisStore, ttl);
        this.agentGroupStorage = new AgentGroupStorage(redisStore, ttl);
        this.workflowStorage = new WorkflowStorage(redisStore, ttl);
        this.graphState = new GraphStore(redisStore, ttl);
        this.graphStoreAdapter = new RedisGraphStoreAdapter(graphState);
    }

    /**
     * Prepare agent execution by recovering checkpoint.
     *
     * @param session The session for the agent
     * @param inputs  Input data
     * @return CompletableFuture for async operation
     */
    @Override
    public void preAgentExecute(BaseSession session, Object inputs) {
        agentStorage.recover(session, inputs).join();
        if (inputs != null) {
            session.state().update(Map.of(Constant.INTERACTIVE_INPUT, List.of(inputs)));
        }
    }

    /**
     * Handle agent execution interruption by saving checkpoint.
     *
     * @param session The session for the agent
     * @return CompletableFuture for async operation
     */
    @Override
    public void interruptAgentExecute(BaseSession session) {
        agentStorage.save(session).join();
    }

    /**
     * Finalize agent execution by saving checkpoint.
     *
     * @param session The session for the agent
     * @return CompletableFuture for async operation
     */
    @Override
    public void postAgentExecute(BaseSession session) {
        agentStorage.save(session).join();
    }

    @Override
    public void preAgentTeamExecute(BaseSession session, Object inputs) {
        agentGroupStorage.recover(session, inputs).join();
        if (inputs != null) {
            session.state().updateGlobal(Map.of(Constant.INTERACTIVE_INPUT, List.of(inputs)));
        }
    }

    @Override
    public void postAgentTeamExecute(BaseSession session) {
        agentGroupStorage.save(session).join();
    }

    /**
     * Prepare workflow execution by recovering or clearing workflow state.
     *
     * @param session The session for the workflow
     * @param inputs  The input for the workflow execution
     * @return CompletableFuture for async operation
     */
    @Override
    public void preWorkflowExecute(BaseSession session, InteractiveInput inputs) {
        if (inputs != null) {
            workflowStorage.recover(session, inputs).join();
            return;
        }

        if (!workflowStorage.exists(session).join()) {
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
     * Finalize workflow execution.
     *
     * @param session   The session for the workflow
     * @param result    The execution result
     * @param exception Any exception that occurred
     * @return CompletableFuture for async operation
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

        if (result instanceof Map<?, ?> resultMap
                && resultMap.get(PregelConstants.TASK_STATUS_INTERRUPT) != null) {
            workflowStorage.save(session).join();
            return;
        }

        String workflowId = getWorkflowId(session);
        graphState.delete(session.sessionId(), workflowId).join();
        workflowStorage.clear(workflowId, session.sessionId()).join();
    }

    /**
     * Check if a session exists in Redis.
     *
     * @param sessionId The session ID to check
     * @return CompletableFuture containing True if session exists
     */
    @Override
    public boolean sessionExists(String sessionId) {
        if (redisStore == null) {
            return false;
        }

        return !redisStore.getByPrefix(sessionId + ":").join().isEmpty();
    }

    /**
     * Release resources for a session.
     *
     * @param sessionId The session ID to release resources for
     * @param agentId   Optional agent ID to release resources for a specific agent
     * @return CompletableFuture for async operation
     */
    public void release(String sessionId, String agentId) {
        if (redisStore == null) {
            return;
        }

        if (agentId != null) {
            agentStorage.clear(agentId, sessionId).join();
        } else {
            redisStore.deleteByPrefix(sessionId + ":", 500).join();
        }
    }

    @Override
    public void release(String sessionId) {
        release(sessionId, null);
    }

    @Override
    public void close() {
        if (ownedClient == null || !closed.compareAndSet(false, true)) {
            return;
        }
        try {
            ownedClient.close();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to close Redis checkpointer client", e);
        }
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

    public AgentGroupStorage getAgentGroupStorage() {
        return agentGroupStorage;
    }

    public RedisStore getRedisStore() {
        return redisStore;
    }

    @Override
    public Store graphStore() {
        return graphStoreAdapter;
    }

    private String getWorkflowId(BaseSession session) {
        String workflowId = session.workflowId();
        return workflowId == null || workflowId.isBlank() ? session.sessionId() : workflowId;
    }

    /**
     * Provider for creating Redis checkpointers from the Python-compatible configuration map.
     */
    public static final class Provider implements CheckpointerProvider {

        @FunctionalInterface
        interface JedisClusterFactory {
            JedisCluster create(Set<HostAndPort> nodes, JedisClientConfig clientConfig);
        }

        private final JedisClusterFactory jedisClusterFactory;

        public Provider() {
            this(JedisCluster::new);
        }

        Provider(JedisClusterFactory jedisClusterFactory) {
            this.jedisClusterFactory = jedisClusterFactory;
        }

        @Override
        public Checkpointer create(Map<String, Object> conf) {
            RedisCheckpointerConfig config;
            try {
                config = RedisCheckpointerConfig.fromMap(conf);
                config.validate();
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                        "Invalid Redis checkpointer configuration: " + e.getMessage()
                                + ". Configuration must include a 'connection' map with either 'redis_client', 'url', "
                                + "or 'nodes'.",
                        e);
            }

            RedisConnectionConfig connection = config.getConnection();
            Object redisClient = connection.getRedisClient();
            if (redisClient instanceof JedisCluster jedisCluster) {
                return new RedisCheckpointer(new JedisClusterRedisStore(jedisCluster), config.getStorageConfigMap());
            }
            if (redisClient != null) {
                return new RedisCheckpointer(new RedisStore(redisClient), config.getStorageConfigMap());
            }
            if (!connection.getNodes().isEmpty()) {
                JedisCluster jedisCluster = jedisClusterFactory.create(connection.getClusterNodes(),
                        buildClientConfig(connection));
                return new RedisCheckpointer(new JedisClusterRedisStore(jedisCluster), config.getStorageConfigMap(),
                        jedisCluster);
            }

            String connectionUrl = connection.getConnectionUrl();
            if (connectionUrl == null) {
                throw new IllegalArgumentException(
                        "Either 'redis_client', 'url', or 'nodes' must be provided in connection configuration");
            }
            redisClient = connection.isClusterMode()
                    ? new UrlBackedRedisClusterClient(connectionUrl, connection.getConnectionArgs())
                    : new UrlBackedRedisClient(connectionUrl, connection.getConnectionArgs());

            RedisStore redisStore = new RedisStore(redisClient);
            return new RedisCheckpointer(redisStore, config.getStorageConfigMap());
        }

        private JedisClientConfig buildClientConfig(RedisConnectionConfig connection) {
            DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder()
                    .connectionTimeoutMillis(connection.getTimeoutMillis())
                    .socketTimeoutMillis(connection.getTimeoutMillis())
                    .blockingSocketTimeoutMillis(connection.getTimeoutMillis())
                    .ssl(connection.isSsl());
            if (connection.getPassword() != null) {
                builder.password(connection.getPassword());
            }
            return builder.build();
        }
    }

    private static final class RedisGraphStoreAdapter implements Store {
        private final GraphStore delegate;

        private RedisGraphStoreAdapter(GraphStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public CompletionStage<Optional<GraphStoreState>> get(String sessionId, String ns) {
            return delegate.get(sessionId, ns).thenApply(state -> {
                if (state instanceof GraphStoreState graphState) {
                    return Optional.of(graphState);
                }
                return Optional.empty();
            });
        }

        @Override
        public CompletionStage<Void> save(String sessionId, String ns, GraphStoreState state) {
            return delegate.save(sessionId, ns, state);
        }

        @Override
        public CompletionStage<Void> delete(String sessionId, String ns) {
            return delegate.delete(sessionId, ns);
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

        public String getUrl() {
            return url;
        }

        public Map<String, Object> getConnectionArgs() {
            return Map.copyOf(connectionArgs);
        }

        public void set(String key, Object value) {
            cleanup(key);
            values.put(key, value);
            expiryAt.remove(key);
        }

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

        public Object get(String key) {
            cleanup(key);
            return values.get(key);
        }

        public long exists(String key) {
            cleanup(key);
            return values.containsKey(key) ? 1L : 0L;
        }

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

        public List<Object> mget(String... keys) {
            List<Object> results = new ArrayList<>(keys.length);
            for (String key : keys) {
                results.add(get(key));
            }
            return results;
        }

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

        public boolean expire(String key, int ttlSeconds) {
            cleanup(key);
            if (!values.containsKey(key)) {
                return false;
            }
            expiryAt.put(key, System.currentTimeMillis() + Duration.ofSeconds(ttlSeconds).toMillis());
            return true;
        }

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

        public UrlBackedRedisPipeline expire(String key, int ttlSeconds) {
            operations.add(() -> client.expire(key, ttlSeconds));
            return this;
        }

        public List<Object> execute() {
            operations.forEach(Runnable::run);
            operations.clear();
            return List.of();
        }
    }
}
