package com.openjiuwen.extensions.store.kv;

import com.openjiuwen.spi.store.KVStorePipeline;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.params.SetParams;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JedisClusterRedisStoreTest {

    @Test
    void storesAndReadsJsonValuesWithOriginalTypes() {
        MockJedisCluster cluster = mockCluster();
        JedisClusterRedisStore store = new JedisClusterRedisStore(cluster.client());

        Map<String, Object> value = Map.of(
                "name", "Ada",
                "count", 3,
                "active", true,
                "tags", List.of("math", 42, false)
        );

        store.set("profile", value);

        String rawValue = cluster.rawValue("profile");
        assertTrue(rawValue.startsWith("{"));
        assertTrue(rawValue.contains("\"name\":\"Ada\""));
        assertFalse(rawValue.contains("name=Ada"));
        Object loaded = store.get("profile");
        Map<?, ?> loadedMap = assertInstanceOf(Map.class, loaded);
        assertEquals("Ada", loadedMap.get("name"));
        assertEquals(3, loadedMap.get("count"));
        assertEquals(true, loadedMap.get("active"));
        assertEquals(List.of("math", 42, false), loadedMap.get("tags"));
    }

    @Test
    void storesAndReadsByteArrayValuesForCheckpointerBlobs() {
        MockJedisCluster cluster = mockCluster();
        JedisClusterRedisStore store = new JedisClusterRedisStore(cluster.client());
        byte[] original = new byte[] {1, 2, 3, 4};

        store.set("blob", original);

        String rawValue = cluster.rawValue("blob");
        assertTrue(rawValue.startsWith("{"));
        assertTrue(rawValue.contains("\"__openjiuwen_envelope\":true"));
        assertTrue(rawValue.contains("\"kind\":\"bytes\""));
        assertTrue(rawValue.contains("\"value\":\"AQIDBA==\""));
        Object loaded = store.get("blob");
        assertInstanceOf(byte[].class, loaded);
        assertArrayEquals(original, (byte[]) loaded);
    }

    @Test
    void storesMapWithByteEnvelopeLikeFieldsAsMap() {
        MockJedisCluster cluster = mockCluster();
        JedisClusterRedisStore store = new JedisClusterRedisStore(cluster.client());
        Map<String, Object> value = Map.of(
                "__openjiuwen_envelope", true,
                "type", "bytes",
                "value", "AQIDBA==",
                "meaning", "user-data"
        );

        store.set("map", value);

        Object loaded = store.get("map");
        Map<?, ?> loadedMap = assertInstanceOf(Map.class, loaded);
        assertEquals("user-data", loadedMap.get("meaning"));
        assertEquals("AQIDBA==", loadedMap.get("value"));
        assertEquals("bytes", loadedMap.get("type"));
        assertEquals(true, loadedMap.get("__openjiuwen_envelope"));
    }

    @Test
    void mgetReturnsValuesInInputOrder() {
        MockJedisCluster cluster = mockCluster();
        JedisClusterRedisStore store = new JedisClusterRedisStore(cluster.client());
        store.set("second", List.of(2));
        store.set("first", Map.of("order", 1));

        List<Object> values = store.mget(List.of("first", "missing", "second"));

        assertEquals(Map.of("order", 1), values.get(0));
        assertNull(values.get(1));
        assertEquals(List.of(2), values.get(2));
    }

    @Test
    void batchDeleteReturnsDeletedCount() {
        MockJedisCluster cluster = mockCluster();
        JedisClusterRedisStore store = new JedisClusterRedisStore(cluster.client());
        store.set("a", "one");
        store.set("b", "two");

        int deleted = store.batchDelete(List.of("a", "missing", "b"), 2);

        assertEquals(2, deleted);
        assertFalse(store.exists("a"));
        assertFalse(store.exists("b"));
    }

    @Test
    void pipelineExecutesSetGetExistsInOrder() {
        MockJedisCluster cluster = mockCluster();
        JedisClusterRedisStore store = new JedisClusterRedisStore(cluster.client());

        KVStorePipeline pipeline = store.pipeline();
        pipeline.set("a", Map.of("step", 1));
        pipeline.get("a");
        pipeline.exists("a");
        pipeline.set("b", "text");
        pipeline.get("b");

        assertEquals(Arrays.asList(null, Map.of("step", 1), true, null, "text"), pipeline.execute());
        assertEquals(List.of("set:a", "get:a", "exists:a", "set:b", "get:b"), cluster.operations());
    }

    @Test
    void exclusiveSetUsesNxAndExpiry() {
        JedisCluster cluster = mock(JedisCluster.class);
        when(cluster.set(eq("lock"), anyString(), any(SetParams.class))).thenReturn("OK");
        JedisClusterRedisStore store = new JedisClusterRedisStore(cluster);

        assertTrue(store.exclusiveSet("lock", "value", 30));

        ArgumentCaptor<SetParams> paramsCaptor = ArgumentCaptor.forClass(SetParams.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(cluster).set(eq("lock"), valueCaptor.capture(), paramsCaptor.capture());
        assertTrue(valueCaptor.getValue().contains("\"kind\":\"json\""));
        assertTrue(valueCaptor.getValue().contains("\"value\":\"value\""));
        assertSetParams(paramsCaptor.getValue(), "NX", "EX", 30L);

        JedisCluster rejectingCluster = mock(JedisCluster.class);
        when(rejectingCluster.set(eq("lock"), anyString(), any(SetParams.class))).thenReturn(null);
        assertFalse(new JedisClusterRedisStore(rejectingCluster).exclusiveSet("lock", "value", 30));
    }

    @Test
    void pipelineSetWithExpiryUsesAtomicSetParams() {
        JedisCluster cluster = mock(JedisCluster.class);
        when(cluster.set(eq("ttl"), anyString(), any(SetParams.class))).thenReturn("OK");
        JedisClusterRedisStore store = new JedisClusterRedisStore(cluster);

        store.pipeline().set("ttl", "value", 30).execute();

        ArgumentCaptor<SetParams> paramsCaptor = ArgumentCaptor.forClass(SetParams.class);
        verify(cluster).set(eq("ttl"), anyString(), paramsCaptor.capture());
        assertSetParams(paramsCaptor.getValue(), null, "EX", 30L);
        verify(cluster, never()).expire(eq("ttl"), anyLong());
    }

    @Test
    void refreshTtlExpiresEachKey() {
        MockJedisCluster cluster = mockCluster();
        JedisClusterRedisStore store = new JedisClusterRedisStore(cluster.client());
        store.set("a", "one");
        store.set("b", "two");

        store.refreshTtl(List.of("a", "b"), 60);

        assertEquals(List.of("a=60", "b=60"), cluster.expirations());
    }

    @Test
    void isMasterReplicationInfoRequiresExactMasterRoleLine() {
        assertTrue(JedisClusterRedisStore.isMasterReplicationInfo(
                "# Replication\r\nrole:master\r\nconnected_slaves:1\r\n"));
        assertFalse(JedisClusterRedisStore.isMasterReplicationInfo(
                "# Replication\r\nrole:slave\r\nmaster_host:127.0.0.1\r\n"));
        assertFalse(JedisClusterRedisStore.isMasterReplicationInfo(
                "# Replication\r\nrole:replica\r\nmaster_host:127.0.0.1\r\n"));
    }

    @Test
    void isMasterReplicationInfoRejectsNullAndMissingRoleLine() {
        IllegalStateException nullInfoError = assertThrows(IllegalStateException.class,
                () -> JedisClusterRedisStore.isMasterReplicationInfo(null));
        assertTrue(nullInfoError.getMessage().contains("replication info is null"));

        IllegalStateException missingRoleError = assertThrows(IllegalStateException.class,
                () -> JedisClusterRedisStore.isMasterReplicationInfo("# Replication\r\nconnected_slaves:0\r\n"));
        assertTrue(missingRoleError.getMessage().contains("does not contain a role line"));

        IllegalStateException embeddedMasterError = assertThrows(IllegalStateException.class,
                () -> JedisClusterRedisStore.isMasterReplicationInfo(
                        "# Replication\r\nmaster_replid:role:master\r\nconnected_slaves:0\r\n"));
        assertTrue(embeddedMasterError.getMessage().contains("does not contain a role line"));
    }

    @Test
    void isMasterWrapsReplicationInfoFailuresWithContext() throws ReflectiveOperationException {
        Jedis jedis = mock(Jedis.class);
        RuntimeException failure = new RuntimeException("boom");
        when(jedis.info("replication")).thenThrow(failure);

        IllegalStateException error = invokeIsMaster(jedis);

        assertTrue(error.getMessage().contains("Failed to read Redis replication info"));
        assertEquals(failure, error.getCause());
    }

    private static MockJedisCluster mockCluster() {
        JedisCluster client = mock(JedisCluster.class);
        Map<String, String> values = new HashMap<>();
        List<String> operations = new ArrayList<>();
        List<String> expirations = new ArrayList<>();

        when(client.set(anyString(), anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            operations.add("set:" + key);
            values.put(key, value);
            return "OK";
        });

        when(client.set(anyString(), anyString(), any(SetParams.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            operations.add("set:" + key);
            if (values.containsKey(key)) {
                return null;
            }
            values.put(key, value);
            return "OK";
        });

        when(client.get(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            operations.add("get:" + key);
            return values.get(key);
        });

        when(client.exists(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            operations.add("exists:" + key);
            return values.containsKey(key);
        });

        when(client.del(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            operations.add("del:" + key);
            return values.remove(key) == null ? 0L : 1L;
        });

        when(client.expire(anyString(), anyLong())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Long seconds = invocation.getArgument(1);
            expirations.add(key + "=" + seconds);
            return values.containsKey(key) ? 1L : 0L;
        });

        return new MockJedisCluster(client, values, operations, expirations);
    }

    private static void assertSetParams(SetParams params, String existence, String expiration,
            long expirationValue) {
        // Jedis SetParams does not expose public accessors for these options.
        Object actualExistence = readField(params, "existance");
        Object actualExpiration = readField(params, "expiration");
        assertEquals(existence, actualExistence == null ? null : actualExistence.toString());
        assertEquals(expiration, actualExpiration == null ? null : actualExpiration.toString());
        assertEquals(expirationValue, readField(params, "expirationValue"));
    }

    private static Object readField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to inspect " + fieldName, e);
        }
    }

    private static IllegalStateException invokeIsMaster(Jedis jedis) throws ReflectiveOperationException {
        Method method = JedisClusterRedisStore.class.getDeclaredMethod("isMaster", Jedis.class);
        method.setAccessible(true);
        try {
            method.invoke(new JedisClusterRedisStore(mock(JedisCluster.class)), jedis);
            throw new AssertionError("Expected isMaster to fail");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalStateException error) {
                return error;
            }
            throw e;
        }
    }

    private record MockJedisCluster(JedisCluster client, Map<String, String> values, List<String> operations,
                                    List<String> expirations) {
        String rawValue(String key) {
            return values.get(key);
        }
    }
}
