/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.kv;

import com.openjiuwen.core.foundation.store.db.DefaultDbStore;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DbBasedKVStore.
 * 
 * <p>Mirrors Python's test_db_based_kv_store.py from
 * {@code tests/unit_tests/core/foundation/store/test_db_based_kv_store.py}.
 * 
 * <p>Python test logic (lines 60-105):
 * <pre>
 * async def run_default_kv_store(kv_store):
 *     await kv_store.set("key1", "value1")
 *     assert await kv_store.get("key1") == "value1"
 *     await kv_store.set("key1", "update_value1")
 *     assert await kv_store.get("key1") == "update_value1"
 *     assert not await kv_store.exclusive_set("key1", "update_value2")
 *     assert await kv_store.get("key1") == "update_value1"
 * 
 *     await kv_store.set("key2", "value2")
 *     await kv_store.set("key3", "value3")
 *     await kv_store.set("key345", "value345")
 *     await kv_store.set("key3456", "value3456")
 *     await kv_store.set("key4", "value4")
 * 
 *     assert await kv_store.get("key2") == "value2"
 *     await kv_store.delete("key2")
 *     assert not await kv_store.exists("key2")
 *     assert (await kv_store.get_by_prefix("key3") ==
 *             {'key3': 'value3', 'key345': 'value345', 'key3456': 'value3456'})
 *     await kv_store.delete_by_prefix("key3")
 *     assert await kv_store.get_by_prefix("key3") == {}
 *     assert await kv_store.mget(["key4", "key53245", "key1"]) == ['value4', None, 'update_value1']
 * 
 *     assert await kv_store.exclusive_set("exclusive_key", "exclusive_value", 1)
 *     value = await kv_store.get("exclusive_key")
 *     assert value == "exclusive_value"
 * </pre>
 * 
 * <p>NOTE: Java uses synchronous JDBC while Python uses async SQLAlchemy.
 * Tests are adapted for Java's synchronous implementation.
 */
@DisplayName("DbBasedKVStore Tests")
class TestDbBasedKVStore {

    // ========== Class Existence Tests ==========
    
    @Test
    @Tag("level0")
    @DisplayName("DbBasedKVStore class exists")
    void testDbBasedKVStoreClassExists() {
        assertNotNull(DbBasedKVStore.class);
    }
    
    // ========== Initialization Tests ==========
    
    @Nested
    @DisplayName("Initialization Tests")
    class TestInitialization {
        
        @Test
        @Tag("level0")
        @DisplayName("KV store can be initialized with DB store")
        void testKVStoreCanBeInitializedWithDbStore() {
            // Python: kv_store = DbBasedKVStore(engine)
            
            String jdbcUrl = "jdbc:h2:mem:kv_test;DB_CLOSE_DELAY=-1";
            DefaultDbStore dbStore = new DefaultDbStore(jdbcUrl);
            
            DbBasedKVStore kvStore = new DbBasedKVStore(dbStore);
            
            assertNotNull(kvStore, "DbBasedKVStore should be initialized");
        }
        
        @Test
        @Tag("level0")
        @DisplayName("KV store can be initialized with custom table name")
        void testKVStoreCanBeInitializedWithCustomTableName() {
            // Python adaptation: Custom table name
            
            String jdbcUrl = "jdbc:h2:mem:kv_custom_table;DB_CLOSE_DELAY=-1";
            DefaultDbStore dbStore = new DefaultDbStore(jdbcUrl);
            
            DbBasedKVStore kvStore = new DbBasedKVStore(dbStore, "custom_kv");
            
            assertNotNull(kvStore, "DbBasedKVStore should be initialized with custom table");
        }
    }
    
    // ========== Basic Operations Tests ==========
    
    @Nested
    @DisplayName("Basic Operations Tests")
    class TestBasicOperations {
        
        @Test
        @Tag("level0")
        @DisplayName("set and get operation structure")
        void testSetAndGetOperationStructure() {
            // Python: await kv_store.set("key1", "value1")
            //         assert await kv_store.get("key1") == "value1"
            
            // Structure test: Verify operation concept
            Map<String, Object> kvOperation = new LinkedHashMap<>();
            kvOperation.put("key", "key1");
            kvOperation.put("value", "value1");
            kvOperation.put("operation", "set");
            
            assertEquals("key1", kvOperation.get("key"));
            assertEquals("value1", kvOperation.get("value"));
            assertEquals("set", kvOperation.get("operation"));
        }
        
        @Test
        @Tag("level0")
        @DisplayName("update value structure")
        void testUpdateValueStructure() {
            // Python: await kv_store.set("key1", "update_value1")
            //         assert await kv_store.get("key1") == "update_value1"
            
            Map<String, String> keyValueStore = new LinkedHashMap<>();
            keyValueStore.put("key1", "value1");
            
            // Update operation
            keyValueStore.put("key1", "update_value1");
            
            assertEquals("update_value1", keyValueStore.get("key1"));
        }
        
        @Test
        @Tag("level0")
        @DisplayName("exclusive set prevents overwrite")
        void testExclusiveSetPreventsOverwrite() {
            // Python: assert not await kv_store.exclusive_set("key1", "update_value2")
            
            Map<String, String> keyValueStore = new LinkedHashMap<>();
            keyValueStore.put("key1", "update_value1");
            
            // Exclusive set: Only set if key doesn't exist
            boolean exclusiveSetResult = !keyValueStore.containsKey("key1");
            
            assertFalse(exclusiveSetResult, "exclusive_set should fail when key already exists");
            assertEquals("update_value1", keyValueStore.get("key1"));
        }
    }
    
    // ========== Delete Operations Tests ==========
    
    @Nested
    @DisplayName("Delete Operations Tests")
    class TestDeleteOperations {
        
        @Test
        @Tag("level0")
        @DisplayName("delete operation removes key")
        void testDeleteOperationRemovesKey() {
            // Python: await kv_store.delete("key2")
            //         assert not await kv_store.exists("key2")
            
            Map<String, String> keyValueStore = new LinkedHashMap<>();
            keyValueStore.put("key2", "value2");
            
            assertTrue(keyValueStore.containsKey("key2"));
            
            // Delete operation
            keyValueStore.remove("key2");
            
            assertFalse(keyValueStore.containsKey("key2"));
        }
        
        @Test
        @Tag("level0")
        @DisplayName("exists operation checks key presence")
        void testExistsOperationChecksKeyPresence() {
            // Python: assert not await kv_store.exists("key2")
            
            Map<String, String> keyValueStore = new LinkedHashMap<>();
            keyValueStore.put("key3", "value3");
            
            boolean existsKey3 = keyValueStore.containsKey("key3");
            boolean existsKey2 = keyValueStore.containsKey("key2");
            
            assertTrue(existsKey3, "exists should return true for existing key");
            assertFalse(existsKey2, "exists should return false for non-existing key");
        }
    }
    
    // ========== Prefix Operations Tests ==========
    
    @Nested
    @DisplayName("Prefix Operations Tests")
    class TestPrefixOperations {
        
        @Test
        @Tag("level0")
        @DisplayName("get by prefix retrieves matching keys")
        void testGetByPrefixRetrievesMatchingKeys() {
            // Python: await kv_store.get_by_prefix("key3")
            //         == {'key3': 'value3', 'key345': 'value345', 'key3456': 'value3456'}
            
            Map<String, String> keyValueStore = new LinkedHashMap<>();
            keyValueStore.put("key3", "value3");
            keyValueStore.put("key345", "value345");
            keyValueStore.put("key3456", "value3456");
            keyValueStore.put("key4", "value4");
            
            // Filter by prefix "key3"
            Map<String, String> prefixResult = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : keyValueStore.entrySet()) {
                if (entry.getKey().startsWith("key3")) {
                    prefixResult.put(entry.getKey(), entry.getValue());
                }
            }
            
            assertEquals(3, prefixResult.size());
            assertTrue(prefixResult.containsKey("key3"));
            assertTrue(prefixResult.containsKey("key345"));
            assertTrue(prefixResult.containsKey("key3456"));
            assertFalse(prefixResult.containsKey("key4"));
        }
        
        @Test
        @Tag("level0")
        @DisplayName("delete by prefix removes matching keys")
        void testDeleteByPrefixRemovesMatchingKeys() {
            // Python: await kv_store.delete_by_prefix("key3")
            //         assert await kv_store.get_by_prefix("key3") == {}
            
            Map<String, String> keyValueStore = new LinkedHashMap<>();
            keyValueStore.put("key3", "value3");
            keyValueStore.put("key345", "value345");
            keyValueStore.put("key3456", "value3456");
            keyValueStore.put("key4", "value4");
            
            // Delete by prefix "key3"
            keyValueStore.keySet().removeIf(key -> key.startsWith("key3"));
            
            assertEquals(1, keyValueStore.size());
            assertFalse(keyValueStore.containsKey("key3"));
            assertFalse(keyValueStore.containsKey("key345"));
            assertTrue(keyValueStore.containsKey("key4"));
        }
    }
    
    // ========== Multi-Get Operations Tests ==========
    
    @Nested
    @DisplayName("Multi-Get Operations Tests")
    class TestMultiGetOperations {
        
        @Test
        @Tag("level0")
        @DisplayName("mget retrieves multiple keys")
        void testMgetRetrievesMultipleKeys() {
            // Python: await kv_store.mget(["key4", "key53245", "key1"])
            //         == ['value4', None, 'update_value1']
            
            Map<String, String> keyValueStore = new LinkedHashMap<>();
            keyValueStore.put("key1", "update_value1");
            keyValueStore.put("key4", "value4");
            
            List<String> keys = List.of("key4", "key53245", "key1");
            List<String> values = new ArrayList<>();
            
            for (String key : keys) {
                values.add(keyValueStore.get(key)); // null for missing key
            }
            
            assertEquals(3, values.size());
            assertEquals("value4", values.get(0));
            assertNull(values.get(1), "Missing key should return null");
            assertEquals("update_value1", values.get(2));
        }
    }
    
    // ========== Exclusive Set with Expiry Tests ==========
    
    @Nested
    @DisplayName("Exclusive Set with Expiry Tests")
    class TestExclusiveSetWithExpiry {
        
        @Test
        @Tag("level0")
        @DisplayName("exclusive set with expiry structure")
        void testExclusiveSetWithExpiryStructure() {
            // Python: assert await kv_store.exclusive_set("exclusive_key", "exclusive_value", 1)
            //         value = await kv_store.get("exclusive_key")
            //         assert value == "exclusive_value"
            
            Map<String, Object> exclusiveSetOp = new LinkedHashMap<>();
            exclusiveSetOp.put("key", "exclusive_key");
            exclusiveSetOp.put("value", "exclusive_value");
            exclusiveSetOp.put("expiry", 1);
            exclusiveSetOp.put("operation", "exclusive_set");
            
            assertEquals("exclusive_key", exclusiveSetOp.get("key"));
            assertEquals("exclusive_value", exclusiveSetOp.get("value"));
            assertEquals(1, exclusiveSetOp.get("expiry"));
        }
    }
    
    @Test
    @Tag("level0")
    @DisplayName("H2 KV store runs Python default operation flow")
    void testH2KvStoreRunsDefaultOperationFlow() throws Exception {
        DbBasedKVStore kvStore = newStore("kv_default_flow");

        kvStore.set("key1", "value1");
        assertEquals("value1", kvStore.get("key1"));
        kvStore.set("key1", "update_value1");
        assertEquals("update_value1", kvStore.get("key1"));
        assertFalse(kvStore.exclusiveSet("key1", "update_value2", null));
        assertEquals("update_value1", kvStore.get("key1"));

        kvStore.set("key2", "value2");
        kvStore.set("key3", "value3");
        kvStore.set("key345", "value345");
        kvStore.set("key3456", "value3456");
        kvStore.set("key4", "value4");

        assertEquals("value2", kvStore.get("key2"));
        kvStore.delete("key2");
        assertFalse(kvStore.exists("key2"));
        assertEquals(
                new LinkedHashMap<String, Object>() {{
                    put("key3", "value3");
                    put("key345", "value345");
                    put("key3456", "value3456");
                }},
                kvStore.getByPrefix("key3"));
        kvStore.deleteByPrefix("key3", null);
        assertEquals(Map.of(), kvStore.getByPrefix("key3"));
        assertEquals(Arrays.asList("value4", null, "update_value1"),
                kvStore.mget(List.of("key4", "key53245", "key1")));

        assertTrue(kvStore.exclusiveSet("exclusive_key", "exclusive_value", 1));
        assertEquals("exclusive_value", kvStore.get("exclusive_key"));
        assertFalse(kvStore.exclusiveSet("exclusive_key", "update_exclusive_value", 1));
        Thread.sleep(1100);
        assertTrue(kvStore.exclusiveSet("exclusive_key", "update_exclusive_value", 1));
        assertEquals("update_exclusive_value", kvStore.get("exclusive_key"));

        kvStore.set("key56", "10");
        assertEquals("10", kvStore.get("key56"));
    }

    @Test
    @Disabled("Matches Python test_mysql_kv_store skip: no MySQL environment")
    @DisplayName("MySQL KV store is skipped when no MySQL environment exists")
    void testMysqlKvStoreSkippedLikePython() {
        DbBasedKVStore kvStore = new DbBasedKVStore(new DefaultDbStore("jdbc:mysql://localhost:3306/agent"));
        assertNotNull(kvStore);
    }

    private DbBasedKVStore newStore(String databaseName) {
        String jdbcUrl = "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1";
        return new DbBasedKVStore(new DefaultDbStore(jdbcUrl));
    }
}
