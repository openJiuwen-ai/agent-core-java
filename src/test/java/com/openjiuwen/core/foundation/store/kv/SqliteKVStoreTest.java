/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.kv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.KVStoreFactory;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Behavior tests for the SQLite KV provider.
 */
class SqliteKVStoreTest {
    @Test
    @Tag("integration")
    void persistsStringsAndBytesAcrossInstances(@TempDir Path tempDir) {
        Map<String, Object> config = Map.of("db_path", tempDir.resolve("values.db").toString());
        byte[] payload = {0, 1, -1, 127};

        try (BaseKVStore writer = KVStoreFactory.create("sqlite", config)) {
            writer.pipeline()
                    .set("checkpoint%:type", "java")
                    .set("checkpoint%:payload", payload)
                    .set("checkpoint-other:type", "unrelated")
                    .execute();
        }

        try (BaseKVStore reader = KVStoreFactory.create("sqlite", config)) {
            assertEquals("java", reader.get("checkpoint%:type"));
            assertArrayEquals(payload, (byte[]) reader.get("checkpoint%:payload"));
            assertEquals(Set.of("checkpoint%:type", "checkpoint%:payload"),
                    reader.getByPrefix("checkpoint%").keySet());

            reader.deleteByPrefix("checkpoint%", null);
            assertFalse(reader.isExists("checkpoint%:type"));
            assertFalse(reader.isExists("checkpoint%:payload"));
            assertEquals("unrelated", reader.get("checkpoint-other:type"));
        }
    }

    @Test
    @Tag("integration")
    void pipelineRollsBackAllWritesWhenAnOperationFails(@TempDir Path tempDir) {
        Map<String, Object> config = Map.of("db_path", tempDir.resolve("rollback.db").toString());

        try (BaseKVStore store = KVStoreFactory.create("sqlite", config)) {
            assertThrows(IllegalArgumentException.class, () -> store.pipeline()
                    .set("first", "value")
                    .set("invalid", Map.of("unsupported", "value"))
                    .execute());
            assertFalse(store.isExists("first"));
        }
    }

    @Test
    @Tag("integration")
    void exclusiveSetAllowsOnlyOneOwnerAcrossInstances(@TempDir Path tempDir) {
        Map<String, Object> config = Map.of("db_path", tempDir.resolve("lock.db").toString());

        try (BaseKVStore first = KVStoreFactory.create("sqlite", config);
                BaseKVStore second = KVStoreFactory.create("sqlite", config)) {
            assertTrue(first.exclusiveSet("lock", "owner-a", null));
            assertFalse(second.exclusiveSet("lock", "owner-b", null));
            assertEquals("owner-a", second.get("lock"));
        }
    }
}
