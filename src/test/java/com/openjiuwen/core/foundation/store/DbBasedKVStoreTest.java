// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.store;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for DbBasedKVStore.
 * Converted from: agent-core/tests/unit_tests/core/foundation/store/test_db_based_kv_store.py
 */
class DbBasedKVStoreTest {
    private Path resourceDir;
    private Path kvPath;
    private Connection connection;
    private DbBasedKVStore kvStore;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        // Create resource directory
        Path projectRoot = Paths.get(System.getProperty("user.dir"));
        resourceDir = projectRoot.resolve("target").resolve("test-resources").resolve("store");
        Files.createDirectories(resourceDir);
        
        kvPath = resourceDir.resolve("kv_store.db");
        
        // Create SQLite connection
        String jdbcUrl = "jdbc:sqlite:" + kvPath.toAbsolutePath();
        connection = DriverManager.getConnection(jdbcUrl);
        
        // Create KV store
        kvStore = new DbBasedKVStore(connection);
    }

    @AfterEach
    void tearDown() throws IOException, SQLException {
        // Close connection
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        
        // Clean up resource directory
        if (Files.exists(resourceDir)) {
            Files.walk(resourceDir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Ignore
                    }
                });
        }
    }

    @Test
    void testDefaultKVStore() throws Exception {
        // Test set and get
        kvStore.set("key1", "value1").get();
        assertEquals("value1", kvStore.get("key1").get());
        
        // Test update
        kvStore.set("key1", "update_value1").get();
        assertEquals("update_value1", kvStore.get("key1").get());
        
        // Test exclusive_set (should fail on existing key)
        assertFalse(kvStore.exclusiveSet("key1", "update_value2", null).get());
        assertEquals("update_value1", kvStore.get("key1").get());
        
        // Set multiple keys
        kvStore.set("key2", "value2").get();
        kvStore.set("key3", "value3").get();
        kvStore.set("key345", "value345").get();
        kvStore.set("key3456", "value3456").get();
        kvStore.set("key4", "value4").get();
        
        // Test get and delete
        assertEquals("value2", kvStore.get("key2").get());
        kvStore.delete("key2").get();
        assertFalse(kvStore.exists("key2").get());
        
        // Test get_by_prefix
        Map<String, String> prefixResult = kvStore.getByPrefix("key3").get();
        assertEquals(3, prefixResult.size());
        assertEquals("value3", prefixResult.get("key3"));
        assertEquals("value345", prefixResult.get("key345"));
        assertEquals("value3456", prefixResult.get("key3456"));
        
        // Test delete_by_prefix
        kvStore.deleteByPrefix("key3").get();
        assertTrue(kvStore.getByPrefix("key3").get().isEmpty());
        
        // Test mget
        List<String> mgetResult = kvStore.mget(List.of("key4", "key53245", "key1")).get();
        assertEquals(3, mgetResult.size());
        assertEquals("value4", mgetResult.get(0));
        assertNull(mgetResult.get(1));
        assertEquals("update_value1", mgetResult.get(2));
    }
}

