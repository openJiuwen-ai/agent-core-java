package com.openjiuwen.extensions.checkpointer.redis;

import com.openjiuwen.core.graph.store.GraphStoreState;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerConfig;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.extensions.store.kv.JedisClusterRedisStore;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisCluster;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RedisCheckpointerProviderTest {

    @Test
    void providerUsesSuppliedRedisClient() {
        RedisCheckpointer.Provider provider = new RedisCheckpointer.Provider();
        FakeRedisClient redisClient = new FakeRedisClient();

        Checkpointer checkpointer = provider.create(Map.of(
                "connection", Map.of("redis_client", redisClient),
                "dump_type", "json"
        ));

        RedisCheckpointer redisCheckpointer = assertInstanceOf(RedisCheckpointer.class, checkpointer);
        redisCheckpointer.getRedisStore().set("session-1:agent:key", "value");

        assertTrue(redisCheckpointer.sessionExists("session-1"));
    }

    @Test
    void providerUsesJedisClusterRedisStoreForJedisClusterClient() {
        RedisCheckpointer.Provider provider = new RedisCheckpointer.Provider();
        JedisCluster jedisCluster = mock(JedisCluster.class);

        Checkpointer checkpointer = provider.create(Map.of(
                "connection", Map.of("redis_client", jedisCluster),
                "dump_type", "json"
        ));

        RedisCheckpointer redisCheckpointer = assertInstanceOf(RedisCheckpointer.class, checkpointer);
        assertInstanceOf(JedisClusterRedisStore.class, redisCheckpointer.getRedisStore());
    }

    @Test
    void providerBuildsOwnedJedisClusterFromSerializableNodes() {
        JedisCluster jedisCluster = mock(JedisCluster.class);
        AtomicReference<Set<HostAndPort>> capturedNodes = new AtomicReference<>();
        AtomicReference<JedisClientConfig> capturedConfig = new AtomicReference<>();
        RedisCheckpointer.Provider provider = new RedisCheckpointer.Provider((nodes, clientConfig) -> {
            capturedNodes.set(nodes);
            capturedConfig.set(clientConfig);
            return jedisCluster;
        });

        Checkpointer checkpointer = provider.create(Map.of(
                "connection", Map.of(
                        "cluster_mode", true,
                        "nodes", List.of("127.0.0.1:7000", "127.0.0.1:7001"),
                        "password", "secret",
                        "ssl", true,
                        "timeout_millis", 1500
                ),
                "dump_type", "json"
        ));

        RedisCheckpointer redisCheckpointer = assertInstanceOf(RedisCheckpointer.class, checkpointer);
        JedisClusterRedisStore redisStore = assertInstanceOf(JedisClusterRedisStore.class,
                redisCheckpointer.getRedisStore());
        assertEquals(jedisCluster, redisStore.getJedisCluster());
        assertEquals(Set.of(new HostAndPort("127.0.0.1", 7000), new HostAndPort("127.0.0.1", 7001)),
                capturedNodes.get());
        assertEquals("secret", capturedConfig.get().getPassword());
        assertTrue(capturedConfig.get().isSsl());
        assertEquals(1500, capturedConfig.get().getConnectionTimeoutMillis());
    }

    @Test
    void closeClosesOnlyOwnedJedisCluster() {
        JedisCluster ownedCluster = mock(JedisCluster.class);
        RedisCheckpointer.Provider provider = new RedisCheckpointer.Provider((nodes, clientConfig) -> ownedCluster);

        RedisCheckpointer ownedCheckpointer = assertInstanceOf(RedisCheckpointer.class, provider.create(Map.of(
                "connection", Map.of("nodes", List.of("127.0.0.1:7000")),
                "dump_type", "json"
        )));

        ownedCheckpointer.close();
        ownedCheckpointer.close();

        verify(ownedCluster).close();

        JedisCluster externalCluster = mock(JedisCluster.class);
        RedisCheckpointer externalCheckpointer = assertInstanceOf(RedisCheckpointer.class, provider.create(Map.of(
                "connection", Map.of("redis_client", externalCluster),
                "dump_type", "json"
        )));

        externalCheckpointer.close();

        verify(externalCluster, never()).close();
    }

    @Test
    void providerPropagatesJsonDumpTypeToStorages() {
        RedisCheckpointer.Provider provider = new RedisCheckpointer.Provider();
        FakeRedisClient redisClient = new FakeRedisClient();

        Checkpointer checkpointer = provider.create(Map.of(
                "connection", Map.of("redis_client", redisClient),
                "dump_type", "json"
        ));

        RedisCheckpointer redisCheckpointer = assertInstanceOf(RedisCheckpointer.class, checkpointer);
        Config config = new Config();
        config.setAgentConfig(agentMetadata("agent-1"));
        AgentSession session = agentSession("session-1", config, redisCheckpointer);
        session.state().updateGlobal(Map.of("sentinel", "provider-json"));
        redisCheckpointer.postAgentExecute(session);
        redisCheckpointer.graphStore().save("session-1", "workflow-1", GraphStoreState.create(
                "workflow-1", 1, Map.of("sentinel", "provider-json"), List.of(), Map.of(), Map.of()))
                .toCompletableFuture()
                .join();

        assertEquals("json", redisClient.get("session-1:agent:agent-1:agent_state_blobs_dump_type"));
        assertEquals("json", redisClient.get("session-1:workflow-graph:workflow-1:checkpoint_data_type"));
    }

    @Test
    void providerRejectsUnsupportedDumpType() {
        RedisCheckpointer.Provider provider = new RedisCheckpointer.Provider();
        FakeRedisClient redisClient = new FakeRedisClient();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> provider.create(Map.of(
                        "connection", Map.of("redis_client", redisClient),
                        "dump_type", "yaml"
                )));

        assertTrue(error.getMessage().contains("dump_type"));
    }

    @Test
    void providerBuildsStandaloneClientFromUrlAndPropagatesTtl() throws Exception {
        RedisCheckpointer.Provider provider = new RedisCheckpointer.Provider();

        Checkpointer checkpointer = provider.create(Map.of(
                "connection", Map.of("url", "redis://127.0.0.1:6379"),
                "ttl", Map.of("default_ttl", 5, "refresh_on_read", true),
                "dump_type", "json"
        ));

        RedisCheckpointer redisCheckpointer = assertInstanceOf(RedisCheckpointer.class, checkpointer);
        assertFalse(redisCheckpointer.getRedisStore().isCluster());
        redisCheckpointer.getRedisStore().set("session-2:workflow:key", "value");
        assertTrue(redisCheckpointer.sessionExists("session-2"));
        assertEquals(300, readField(redisCheckpointer.getAgentStorage(), "ttlSeconds"));
        assertEquals(Boolean.TRUE, readField(redisCheckpointer.getAgentStorage(), "refreshOnRead"));
    }

    @Test
    void clusterUrlsNormalizeAndFactoryUsesRedisProvider() {
        RedisConnectionConfig connection = RedisConnectionConfig.fromMap(Map.of(
                "url", "redis+cluster://127.0.0.1:7000"
        ));

        assertTrue(connection.isClusterMode());
        assertEquals("redis://127.0.0.1:7000", connection.getConnectionUrl());

        Checkpointer checkpointer = CheckpointerFactory.create(new CheckpointerConfig("redis", Map.of(
                "connection", Map.of("url", "redis://127.0.0.1:6379"),
                "dump_type", "json"
        )));

        RedisCheckpointer redisCheckpointer = assertInstanceOf(RedisCheckpointer.class, checkpointer);
        assertNotNull(redisCheckpointer.graphStore());
    }

    @Test
    void connectionConfigParsesClusterNodesAndBasicOptions() {
        RedisConnectionConfig connection = RedisConnectionConfig.fromMap(Map.of(
                "cluster_mode", true,
                "nodes", List.of("127.0.0.1:7000", "127.0.0.1:7001"),
                "password", "secret",
                "ssl", true,
                "timeout_millis", 1500
        ));

        connection.validate();

        assertTrue(connection.isClusterMode());
        assertEquals(List.of("127.0.0.1:7000", "127.0.0.1:7001"), connection.getNodes());
        assertEquals("secret", connection.getPassword());
        assertTrue(connection.isSsl());
        assertEquals(1500, connection.getTimeoutMillis());
        assertEquals(Set.of(new HostAndPort("127.0.0.1", 7000), new HostAndPort("127.0.0.1", 7001)),
                connection.getClusterNodes());
    }

    @Test
    void connectionConfigDefaultsClusterOptions() {
        RedisConnectionConfig connection = RedisConnectionConfig.fromMap(Map.of(
                "nodes", List.of("127.0.0.1:7000"),
                "password", ""
        ));

        connection.validate();

        assertTrue(connection.isClusterMode());
        assertEquals(2000, connection.getTimeoutMillis());
        assertFalse(connection.isSsl());
        assertEquals(null, connection.getPassword());
    }

    @Test
    void connectionConfigRejectsInvalidClusterNodesAndTimeout() {
        assertThrows(IllegalArgumentException.class, () -> RedisConnectionConfig.fromMap(Map.of(
                "cluster_mode", true,
                "nodes", List.of("127.0.0.1")
        )).validate());

        assertThrows(IllegalArgumentException.class, () -> RedisConnectionConfig.fromMap(Map.of(
                "cluster_mode", true,
                "nodes", List.of("127.0.0.1:abc")
        )).validate());

        assertThrows(IllegalArgumentException.class, () -> RedisConnectionConfig.fromMap(Map.of(
                "cluster_mode", true,
                "nodes", List.of("redis://127.0.0.1:7000")
        )).validate());

        assertThrows(IllegalArgumentException.class, () -> RedisConnectionConfig.fromMap(Map.of(
                "cluster_mode", true,
                "nodes", List.of("127.0.0.1:7000,127.0.0.1:7001")
        )).validate());

        assertThrows(IllegalArgumentException.class, () -> RedisConnectionConfig.fromMap(Map.of(
                "cluster_mode", false,
                "nodes", List.of("127.0.0.1:7000")
        )).validate());

        assertThrows(IllegalArgumentException.class, () -> RedisConnectionConfig.fromMap(Map.of(
                "cluster_mode", true,
                "nodes", List.of("127.0.0.1:7000"),
                "timeout_millis", 0
        )).validate());
    }

    @Test
    void connectionConfigIgnoresBlankClusterNodes() {
        RedisConnectionConfig connection = RedisConnectionConfig.fromMap(Map.of(
                "nodes", List.of("", "  ", "127.0.0.1:7000")
        ));

        connection.validate();

        assertEquals(List.of("127.0.0.1:7000"), connection.getNodes());
        assertEquals(Set.of(new HostAndPort("127.0.0.1", 7000)), connection.getClusterNodes());
    }

    @Test
    void connectionConfigIgnoresNullAndBlankClusterNodes() {
        RedisConnectionConfig connection = RedisConnectionConfig.fromMap(Map.of(
                "nodes", Arrays.asList(null, "", "  ", "127.0.0.1:7000")
        ));

        connection.validate();

        assertEquals(List.of("127.0.0.1:7000"), connection.getNodes());
        assertEquals(Set.of(new HostAndPort("127.0.0.1", 7000)), connection.getClusterNodes());
    }

    @Test
    void connectionConfigAllowsUrlWhenNodesAreBlank() {
        RedisConnectionConfig connection = RedisConnectionConfig.fromMap(Map.of(
                "url", "redis://127.0.0.1:6379",
                "nodes", List.of("", "  ")
        ));

        connection.validate();

        assertFalse(connection.isClusterMode());
        assertEquals(List.of(), connection.getNodes());
        assertEquals("redis://127.0.0.1:6379", connection.getConnectionUrl());
    }

    @Test
    void connectionConfigTreatsOnlyBlankNodesAsMissing() {
        RedisConnectionConfig connection = RedisConnectionConfig.fromMap(Map.of(
                "nodes", List.of("", "  ")
        ));

        assertEquals(List.of(), connection.getNodes());
        assertFalse(connection.isClusterMode());
        assertThrows(IllegalArgumentException.class, connection::validate);
    }

    @Test
    void connectionConfigRejectsInvalidBooleanAndTimeoutValues() {
        assertThrows(IllegalArgumentException.class, () -> RedisConnectionConfig.fromMap(Map.of(
                "nodes", List.of("127.0.0.1:7000"),
                "ssl", "tru"
        )));

        assertThrows(IllegalArgumentException.class, () -> RedisConnectionConfig.fromMap(Map.of(
                "nodes", List.of("127.0.0.1:7000"),
                "timeout_millis", 1.5
        )));

        assertThrows(IllegalArgumentException.class, () -> RedisConnectionConfig.fromMap(Map.of(
                "nodes", List.of("127.0.0.1:7000"),
                "timeout_millis", 2147483648L
        )));
    }

    @Test
    void connectionConfigParsesBooleanStringsForSsl() {
        RedisConnectionConfig sslEnabled = RedisConnectionConfig.fromMap(Map.of(
                "nodes", List.of("127.0.0.1:7000"),
                "ssl", "true"
        ));
        RedisConnectionConfig sslDisabled = RedisConnectionConfig.fromMap(Map.of(
                "nodes", List.of("127.0.0.1:7000"),
                "ssl", "false"
        ));

        assertTrue(sslEnabled.isSsl());
        assertFalse(sslDisabled.isSsl());
    }

    @Test
    void invalidConfigRaisesHelpfulError() {
        RedisCheckpointer.Provider provider = new RedisCheckpointer.Provider();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> provider.create(Map.of()));

        assertTrue(error.getMessage().contains("connection"));
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getSuperclass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Map<String, Object> agentMetadata(String agentId) {
        return Map.of("id", agentId, "type", "agent", "invoke", "invoke");
    }

    private static AgentSession agentSession(String sessionId, Config config, Checkpointer checkpointer) {
        return new AgentSession(sessionId, config, checkpointer, null, null);
    }

    static class FakeRedisClient {
        private final Map<String, Object> values = new ConcurrentHashMap<>();
        private final Map<String, Long> expiryAt = new ConcurrentHashMap<>();

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
                expiryAt.put(key, System.currentTimeMillis() + expiry * 1000L);
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
            return java.util.Arrays.stream(keys).map(this::get).toList();
        }

        public List<String> scanIter(String pattern) {
            String prefix = pattern.endsWith("*") ? pattern.substring(0, pattern.length() - 1) : pattern;
            return values.keySet().stream()
                    .peek(this::cleanup)
                    .filter(values::containsKey)
                    .filter(key -> key.startsWith(prefix))
                    .sorted()
                    .toList();
        }

        public boolean expire(String key, int ttlSeconds) {
            cleanup(key);
            if (!values.containsKey(key)) {
                return false;
            }
            expiryAt.put(key, System.currentTimeMillis() + ttlSeconds * 1000L);
            return true;
        }

        public FakeRedisPipeline pipeline() {
            return new FakeRedisPipeline(this);
        }

        private void cleanup(String key) {
            Long expiresAt = expiryAt.get(key);
            if (expiresAt != null && expiresAt <= System.currentTimeMillis()) {
                values.remove(key);
                expiryAt.remove(key);
            }
        }
    }

    static class FakeRedisPipeline {
        private final FakeRedisClient client;
        private final List<Runnable> operations = new java.util.ArrayList<>();

        FakeRedisPipeline(FakeRedisClient client) {
            this.client = client;
        }

        public FakeRedisPipeline expire(String key, int ttlSeconds) {
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
