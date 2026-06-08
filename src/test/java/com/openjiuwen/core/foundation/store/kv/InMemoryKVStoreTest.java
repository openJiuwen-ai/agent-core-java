/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.kv;

import com.openjiuwen.core.foundation.store.BasedKVStorePipeline;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryKVStoreTest {

    @Nested
    class BasicOperations {

        @Test
        void testSetAndGet() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1").join();

            assertThat(store.get("key1").join()).isEqualTo("value1");
        }

        @Test
        void testSetOverwritesExistingValue() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1").join();
            store.set("key1", "value2").join();

            assertThat(store.get("key1").join()).isEqualTo("value2");
        }

        @Test
        void testGetNonexistentKeyReturnsNone() {
            InMemoryKVStore store = new InMemoryKVStore();

            assertThat(store.get("nonexistent").join()).isNull();
        }

        @Test
        void testSetAndGetBytes() {
            InMemoryKVStore store = new InMemoryKVStore();
            byte[] value = "bytes_value".getBytes(StandardCharsets.UTF_8);
            store.set("key1", value).join();

            assertThat((byte[]) store.get("key1").join()).containsExactly(value);
        }

        @Test
        void testDeleteExistingKey() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1").join();
            store.delete("key1").join();

            assertThat(store.get("key1").join()).isNull();
        }

        @Test
        void testDeleteNonexistentKeyNoError() {
            InMemoryKVStore store = new InMemoryKVStore();

            store.delete("nonexistent").join();

            assertThat(store.exists("nonexistent").join()).isFalse();
        }

        @Test
        void testExistsTrue() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1").join();

            assertThat(store.exists("key1").join()).isTrue();
        }

        @Test
        void testExistsFalse() {
            InMemoryKVStore store = new InMemoryKVStore();

            assertThat(store.exists("nonexistent").join()).isFalse();
        }
    }

    @Nested
    class ExclusiveSet {

        @Test
        void testExclusiveSetNewKey() {
            InMemoryKVStore store = new InMemoryKVStore();

            assertThat(store.exclusiveSet("key1", "value1", null).join()).isTrue();
            assertThat(store.get("key1").join()).isEqualTo("value1");
        }

        @Test
        void testExclusiveSetExistingKeyFails() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1").join();

            assertThat(store.exclusiveSet("key1", "value2", null).join()).isFalse();
            assertThat(store.get("key1").join()).isEqualTo("value1");
        }

        @Test
        void testExclusiveSetWithExpiry() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.exclusiveSet("key1", "value1", 1).join();

            assertThat(store.exists("key1").join()).isTrue();
            sleep();
            assertThat(store.exists("key1").join()).isFalse();
        }

        @Test
        void testExclusiveSetAllowsSettingAfterExpiry() {
            InMemoryKVStore store = new InMemoryKVStore();
            assertThat(store.exclusiveSet("key1", "value1", 1).join()).isTrue();

            sleep();

            assertThat(store.exclusiveSet("key1", "value2", 1).join()).isTrue();
        }
    }

    @Nested
    class PrefixOperations {

        @Test
        void testGetByPrefix() {
            InMemoryKVStore store = seededPrefixStore();

            assertThat(store.getByPrefix("user:1").join()).containsExactly(
                    Map.entry("user:1:name", "Alice"),
                    Map.entry("user:1:email", "alice@example.com"));
        }

        @Test
        void testGetByPrefixEmptyResult() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1").join();

            assertThat(store.getByPrefix("nonexistent:").join()).isEmpty();
        }

        @Test
        void testDeleteByPrefix() {
            InMemoryKVStore store = seededPrefixStore();

            store.deleteByPrefix("user:1", null).join();

            assertThat(store.exists("user:1:name").join()).isFalse();
            assertThat(store.exists("user:1:email").join()).isFalse();
            assertThat(store.exists("user:2:name").join()).isTrue();
            assertThat(store.exists("admin:settings").join()).isTrue();
        }

        @Test
        void testDeleteByPrefixWithBatchSize() {
            InMemoryKVStore store = new InMemoryKVStore();
            for (int i = 0; i < 10; i++) {
                store.set("prefix:key" + i, "value" + i).join();
            }

            store.deleteByPrefix("prefix:", 3).join();

            assertThat(store.getByPrefix("prefix:").join()).isEmpty();
        }

        @Test
        void testDeleteByPrefixNoMatches() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1").join();

            store.deleteByPrefix("nonexistent:", null).join();

            assertThat(store.exists("key1").join()).isTrue();
        }
    }

    @Nested
    class BatchOperations {

        @Test
        void testMget() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1").join();
            store.set("key2", "value2").join();
            store.set("key3", "value3").join();

            assertThat(store.mget(List.of("key1", "key2", "key4")).join())
                    .containsExactly("value1", "value2", null);
        }

        @Test
        void testMgetEmptyList() {
            InMemoryKVStore store = new InMemoryKVStore();

            assertThat(store.mget(List.of()).join()).isEmpty();
        }

        @Test
        void testBatchDelete() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1").join();
            store.set("key2", "value2").join();
            store.set("key3", "value3").join();

            assertThat(store.batchDelete(List.of("key1", "key2", "key4"), null).join()).isEqualTo(2);
            assertThat(store.exists("key1").join()).isFalse();
            assertThat(store.exists("key2").join()).isFalse();
            assertThat(store.exists("key3").join()).isTrue();
        }

        @Test
        void testBatchDeleteEmptyList() {
            InMemoryKVStore store = new InMemoryKVStore();

            assertThat(store.batchDelete(List.of(), null).join()).isZero();
        }

        @Test
        void testBatchDeleteWithBatchSize() {
            InMemoryKVStore store = new InMemoryKVStore();
            for (int i = 0; i < 10; i++) {
                store.set("key" + i, "value" + i).join();
            }

            assertThat(store.batchDelete(IntStream.range(0, 10).mapToObj(i -> "key" + i).toList(), 3).join())
                    .isEqualTo(10);
        }
    }

    @Nested
    class PipelineTests {

        @Test
        void testPipelineSetAndGet() {
            InMemoryKVStore store = new InMemoryKVStore();
            BasedKVStorePipeline pipeline = store.pipeline();

            pipeline.set("key1", "value1", null).join();
            pipeline.set("key2", "value2", null).join();
            pipeline.get("key1").join();
            pipeline.get("key2").join();

            assertThat(pipeline.execute().join()).containsExactly(null, null, "value1", "value2");
        }

        @Test
        void testPipelineSetGetExists() {
            InMemoryKVStore store = new InMemoryKVStore();
            BasedKVStorePipeline pipeline = store.pipeline();

            pipeline.set("key1", "value1", null).join();
            pipeline.get("key1").join();
            pipeline.exists("key1").join();
            pipeline.get("nonexistent").join();
            pipeline.exists("nonexistent").join();

            assertThat(pipeline.execute().join()).containsExactly(null, "value1", true, null, false);
        }

        @Test
        void testPipelineMultipleExecutes() {
            InMemoryKVStore store = new InMemoryKVStore();
            BasedKVStorePipeline pipeline = store.pipeline();

            pipeline.set("key1", "value1", null).join();
            pipeline.get("key1").join();
            List<Object> results1 = pipeline.execute().join();

            pipeline.set("key2", "value2", null).join();
            pipeline.get("key2").join();
            List<Object> results2 = pipeline.execute().join();

            assertThat(results1).containsExactly(null, "value1");
            assertThat(results2).containsExactly(null, "value2");
        }

        @Test
        void testPipelineEmptyOperations() {
            InMemoryKVStore store = new InMemoryKVStore();
            BasedKVStorePipeline pipeline = store.pipeline();

            assertThat(pipeline.execute().join()).isEmpty();
        }

        @Test
        void testPipelineWithBytes() {
            InMemoryKVStore store = new InMemoryKVStore();
            BasedKVStorePipeline pipeline = store.pipeline();
            byte[] value = "bytes_value".getBytes(StandardCharsets.UTF_8);

            pipeline.set("key1", value, null).join();
            pipeline.get("key1").join();

            List<Object> results = pipeline.execute().join();
            assertThat(results).hasSize(2);
            assertThat(results.get(0)).isNull();
            assertThat((byte[]) results.get(1)).containsExactly(value);
        }
    }

    @Nested
    class ExpiryTests {

        @Test
        void testKeyExpiry() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.exclusiveSet("key1", "value1", 1).join();

            assertThat(store.exists("key1").join()).isTrue();
            sleep();
            assertThat(store.exists("key1").join()).isFalse();
        }

        @Test
        void testExpiredKeyReturnsNone() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.exclusiveSet("key1", "value1", 1).join();

            sleep();

            assertThat(store.get("key1").join()).isNull();
        }

        @Test
        void testExpiredKeyInGetByPrefixReturnsNone() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("prefix:key1", "value1").join();
            store.exclusiveSet("prefix:key2", "value2", 1).join();

            sleep();

            Map<String, Object> expected = new LinkedHashMap<>();
            expected.put("prefix:key1", "value1");
            expected.put("prefix:key2", null);
            assertThat(store.getByPrefix("prefix:").join()).isEqualTo(expected);
        }

        @Test
        void testCanSetAfterKeyExpires() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.exclusiveSet("key1", "value1", 1).join();

            sleep();
            store.set("key1", "value2").join();

            assertThat(store.get("key1").join()).isEqualTo("value2");
        }
    }

    @Nested
    class ConcurrencyTests {

        @Test
        void testConcurrentSets() {
            InMemoryKVStore store = new InMemoryKVStore();

            CompletableFuture<?>[] tasks = IntStream.range(0, 100)
                    .mapToObj(i -> CompletableFuture.runAsync(() -> store.set("key" + i, "value" + i).join()))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(tasks).join();

            for (int i = 0; i < 100; i++) {
                assertThat(store.get("key" + i).join()).isEqualTo("value" + i);
            }
        }

        @Test
        void testConsecutivePipelineOperations() {
            InMemoryKVStore store = new InMemoryKVStore();

            for (int i = 0; i < 10; i++) {
                BasedKVStorePipeline pipeline = store.pipeline();
                pipeline.set("key" + i, "value" + i, null).join();
                pipeline.get("key" + i).join();

                assertThat(pipeline.execute().join()).containsExactly(null, "value" + i);
            }
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void testEmptyStringKey() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("", "value").join();

            assertThat(store.get("").join()).isEqualTo("value");
        }

        @Test
        void testEmptyStringValue() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "").join();

            assertThat(store.get("key1").join()).isEqualTo("");
        }

        @Test
        void testSpecialCharactersInKey() {
            InMemoryKVStore store = new InMemoryKVStore();
            List<String> specialKeys = List.of(
                    "key:with:colons",
                    "key/with/slashes",
                    "key-with-dashes",
                    "key_with_underscores",
                    "key.with.dots",
                    "key with spaces");

            for (String key : specialKeys) {
                store.set(key, "value_" + key).join();
            }

            for (String key : specialKeys) {
                assertThat(store.get(key).join()).isEqualTo("value_" + key);
            }
        }

        @Test
        void testLargeValue() {
            InMemoryKVStore store = new InMemoryKVStore();
            String largeValue = "x".repeat(100000);

            store.set("key1", largeValue).join();

            assertThat(store.get("key1").join()).isEqualTo(largeValue);
        }

        @Test
        void testGetByPrefixEmptyPrefix() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1").join();
            store.set("key2", "value2").join();

            assertThat(store.getByPrefix("").join()).containsExactly(
                    Map.entry("key1", "value1"),
                    Map.entry("key2", "value2"));
        }

        @Test
        void testDeleteByPrefixEmptyPrefix() {
            InMemoryKVStore store = new InMemoryKVStore();
            store.set("key1", "value1").join();
            store.set("key2", "value2").join();

            store.deleteByPrefix("", null).join();

            assertThat(store.exists("key1").join()).isFalse();
            assertThat(store.exists("key2").join()).isFalse();
        }
    }

    private InMemoryKVStore seededPrefixStore() {
        InMemoryKVStore store = new InMemoryKVStore();
        store.set("user:1:name", "Alice").join();
        store.set("user:1:email", "alice@example.com").join();
        store.set("user:2:name", "Bob").join();
        store.set("admin:settings", "value").join();
        return store;
    }

    private void sleep() {
        try {
            Thread.sleep(1100L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for expiry", exception);
        }
    }
}
