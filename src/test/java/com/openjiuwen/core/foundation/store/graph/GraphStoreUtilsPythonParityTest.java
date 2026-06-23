/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.core.foundation.store.base_reranker.Reranker;
import com.openjiuwen.core.foundation.store.query.QueryExpr;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.core.foundation.store.graph.test_graph_store_utils} in
 * {@code tests/unit_tests/core/foundation/store/graph/test_graph_store_utils.py}.
 */
class GraphStoreUtilsPythonParityTest {

    @Test
    void testYieldsBatchesOfSizeN() {
        List<List<Integer>> batches = GraphStoreUtils.batched(List.of(0, 1, 2, 3, 4, 5, 6), 3);

        assertEquals(List.of(List.of(0, 1, 2), List.of(3, 4, 5), List.of(6)), batches);
    }

    @Test
    void testNLessThanOneRaises() {
        IllegalArgumentException zero = assertThrows(IllegalArgumentException.class,
                () -> GraphStoreUtils.batched(List.of(1, 2, 3), 0));
        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                () -> GraphStoreUtils.batched(List.of(1, 2, 3), -1));

        assertTrue(zero.getMessage().contains("n must be at least one"));
        assertTrue(negative.getMessage().contains("n must be at least one"));
    }

    @Test
    @Disabled("Skipped in Python source: itertools.batched (3.12+) does not support strict=")
    void testStrictIncompleteBatchRaises() {
        assertThrows(IllegalArgumentException.class,
                () -> GraphStoreUtils.batched(List.of(0, 1, 2, 3, 4), 3, true));
    }

    @Test
    @Disabled("Skipped in Python source: itertools.batched (3.12+) does not support strict=")
    void testStrictCompleteBatchesOk() {
        List<List<Integer>> batches = GraphStoreUtils.batched(List.of(0, 1, 2, 3, 4, 5), 3, true);

        assertEquals(List.of(List.of(0, 1, 2), List.of(3, 4, 5)), batches);
    }

    @Test
    void testReturnsResultAndMetadata() {
        GraphStoreUtils.MetadataResult<Integer, String> result =
                GraphStoreUtils.withMetadata(CompletableFuture.completedFuture(42), "meta").join();

        assertEquals(42, result.result());
        assertEquals("meta", result.metadata());
    }

    @Test
    void testReturns32CharHex() {
        String uuid = GraphStoreUtils.getUuid();

        assertEquals(32, uuid.length());
        assertTrue(uuid.chars().allMatch(c -> c >= '0' && c <= '9' || c >= 'a' && c <= 'f'));
    }

    @Test
    void testReplacesNoneEmptyWithNewUuid() {
        FakeGraphStore backend = new FakeGraphStore(true, List.of());
        AtomicInteger counter = new AtomicInteger();
        List<Object> result = GraphStoreUtils.ensureUniqueUuids(
                backend,
                Arrays.asList(null, "", "existing_id"),
                "entities",
                false,
                () -> "0000000000000000000000000000000" + counter.incrementAndGet()).join();

        assertNotNull(result.get(0));
        assertNotEquals("", result.get(0));
        assertEquals(32, String.valueOf(result.get(0)).length());
        assertNotNull(result.get(1));
        assertNotEquals("", result.get(1));
        assertEquals(32, String.valueOf(result.get(1)).length());
        assertEquals("existing_id", result.get(2));
    }

    @Test
    void testSkipTrueReturnsAfterUuidFill() {
        FakeGraphStore backend = new FakeGraphStore(false, List.of());
        List<Object> result = GraphStoreUtils.ensureUniqueUuids(
                backend,
                Arrays.asList(null, "id1"),
                "entities",
                true,
                () -> "11111111111111111111111111111111").join();

        assertEquals(0, backend.isEmptyCalls);
        assertEquals(2, result.size());
        assertEquals("id1", result.get(1));
    }

    @Test
    void testBackendEmptyReturnsUniqueIds() {
        FakeGraphStore backend = new FakeGraphStore(true, List.of());

        List<Object> result = GraphStoreUtils.ensureUniqueUuids(backend, List.of("a", "b"), "entities").join();

        assertEquals(List.of("a", "b"), result);
        assertTrue(backend.queriedIds.isEmpty());
    }

    @Test
    void testBackendHasDuplicatesReplacesUntilUnique() {
        FakeGraphStore backend = new FakeGraphStore(false, List.of(List.of("id1"), List.of()));

        List<Object> result = GraphStoreUtils.ensureUniqueUuids(
                backend,
                List.of("id1", "id2"),
                "entities",
                false,
                () -> "22222222222222222222222222222222").join();

        assertEquals("id2", result.get(1));
        assertNotEquals("id1", result.get(0));
        assertEquals(32, String.valueOf(result.get(0)).length());
        assertFalse(backend.queriedIds.isEmpty());
    }

    @Test
    void testFormatsRoleAndContent() {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", "Hello"),
                Map.of("role", "assistant", "content", "Hi"));

        String result = GraphStoreUtils.formatListOfMessages(messages);

        assertTrue(result.contains("user"));
        assertTrue(result.contains("Hello"));
        assertTrue(result.contains("assistant"));
        assertTrue(result.contains("Hi"));
    }

    @Test
    void testRoleReplace() {
        String result = GraphStoreUtils.formatListOfMessages(
                List.of(Map.of("role", "assistant", "content", "Hi")),
                Map.of("assistant", "Agent"),
                "{role}: {content}\n");

        assertTrue(result.contains("Agent"));
    }

    @Test
    void testTemplate() {
        String result = GraphStoreUtils.formatListOfMessages(
                List.of(Map.of("role", "user", "content", "x")),
                Map.of(),
                "{role}|{content}\n");

        assertEquals("user|x\n", result);
    }

    @Test
    void testPre1970ReturnsOffsetSeconds() {
        OffsetDateTime dateTime = OffsetDateTime.of(1960, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        double expected = Duration.between(
                OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                dateTime).toNanos() / 1_000_000_000.0d;

        assertEquals(expected, GraphStoreUtils.safeTimestamp(dateTime), 0.0d);
    }

    @Test
    void testPost1970ReturnsNormalTimestamp() {
        OffsetDateTime dateTime = OffsetDateTime.of(2025, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);

        assertEquals(dateTime.toEpochSecond(), GraphStoreUtils.safeTimestamp(dateTime), 0.0d);
    }

    @Test
    void testReturnsIntRoughlyCurrentTime() {
        long before = Instant.now().getEpochSecond();
        long timestamp = GraphStoreUtils.getCurrentUtcTimestamp();
        long after = Instant.now().getEpochSecond();

        assertTrue(timestamp >= before - 1);
        assertTrue(timestamp <= after + 1);
    }

    @Test
    void testValidTimestampReturnsFormattedString() {
        String result = GraphStoreUtils.formatTimestamp(1735729200, ZoneOffset.UTC, "(%a) %Y/%b/%d %H:%M:%S");

        assertTrue(result.contains("2025"));
        assertTrue(result.contains(":"));
    }

    @Test
    void testTimestampMinusOneReturnsUnknownDatetime() {
        assertEquals("Unknown Datetime", GraphStoreUtils.formatTimestamp(-1));
    }

    @Test
    void testValidTimestampReturnsIsoString() {
        String result = GraphStoreUtils.formatTimestampIso(1735729200, ZoneOffset.UTC);

        assertTrue(result.contains("2025"));
        assertTrue(result.contains("01"));
    }

    @Test
    void testIsoTimestampMinusOneReturnsUnknownDatetime() {
        assertEquals("Unknown Datetime", GraphStoreUtils.formatTimestampIso(-1));
    }

    @Test
    void testValidIsoReturnsTimestampAndOffset() {
        GraphStoreUtils.TimestampOffset result = GraphStoreUtils.iso2timestamp("2025-09-10T15:56:53+08:00");

        assertNotEquals(-1, result.timestamp());
        assertEquals(32, result.offset());
    }

    @Test
    void testInvalidIsoReturnsMinusOneZero() {
        assertEquals(new GraphStoreUtils.TimestampOffset(-1, 0), GraphStoreUtils.iso2timestamp("not-a-date"));
    }

    @Test
    void testValidTimestampOffsetReturnsDatetime() {
        Optional<OffsetDateTime> result = GraphStoreUtils.loadStoredTimeFromDb(1735729200, 0);

        assertTrue(result.isPresent());
        assertEquals(2025, result.orElseThrow().getYear());
    }

    @Test
    void testTimestampMinusOneReturnsNone() {
        assertTrue(GraphStoreUtils.loadStoredTimeFromDb(-1, 0).isEmpty());
    }

    @Test
    void testUtcPlus8Roundtrip() {
        int offset = GraphStoreUtils.storeTzOffset("+08:00");
        ZoneOffset timezone = GraphStoreUtils.loadTzOffset(offset);

        assertEquals(8 * 3600, timezone.getTotalSeconds());
    }

    @Test
    void testLoadTzOffsetZero() {
        ZoneOffset timezone = GraphStoreUtils.loadTzOffset(0);

        assertEquals(0, timezone.getTotalSeconds());
    }

    private static final class FakeGraphStore implements GraphStore {

        private final boolean empty;
        private final List<List<String>> duplicateRounds;
        private final List<List<?>> queriedIds = new ArrayList<>();
        private int queryIndex;
        private int isEmptyCalls;

        private FakeGraphStore(boolean empty, List<List<String>> duplicateRounds) {
            this.empty = empty;
            this.duplicateRounds = duplicateRounds;
        }

        @Override
        public GraphConfig getConfig() {
            return GraphConfig.builder().backend("fake").build();
        }

        @Override
        public Optional<Semaphore> getSemophore() {
            return Optional.empty();
        }

        @Override
        public Optional<Embedding> getEmbedder() {
            return Optional.empty();
        }

        @Override
        public boolean isReturnSimilarityScore() {
            return false;
        }

        @Override
        public void rebuild() {
        }

        @Override
        public CompletableFuture<Void> refresh(boolean skipCompact, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> addData(String collection,
                                               Iterable<Map<String, Object>> data,
                                               boolean flush,
                                               boolean upsert,
                                               Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> addEntity(Iterable<?> entities, boolean flush, boolean upsert, boolean noEmbed) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> addRelation(Iterable<?> relations,
                                                   boolean flush,
                                                   boolean upsert,
                                                   boolean noEmbed) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> addEpisode(Iterable<?> episodes,
                                                  boolean flush,
                                                  boolean upsert,
                                                  boolean noEmbed) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean isEmpty(String collection) {
            isEmptyCalls++;
            return empty;
        }

        @Override
        public CompletableFuture<List<Map<String, Object>>> query(String collection,
                                                                  List<?> ids,
                                                                  QueryExpr expr,
                                                                  boolean silenceErrors,
                                                                  Map<String, Object> kwargs) {
            queriedIds.add(ids == null ? List.of() : List.copyOf(ids));
            List<String> duplicates = queryIndex < duplicateRounds.size()
                    ? duplicateRounds.get(queryIndex++)
                    : List.of();
            return CompletableFuture.completedFuture(duplicates.stream()
                    .map(uuid -> Map.<String, Object>of("uuid", uuid))
                    .toList());
        }

        @Override
        public CompletableFuture<Map<String, Object>> delete(String collection,
                                                             List<?> ids,
                                                             QueryExpr expr,
                                                             Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(Map.of());
        }

        @Override
        public CompletableFuture<Map<String, List<Map<String, Object>>>> search(String query,
                                                                                int k,
                                                                                String collection,
                                                                                BaseRankConfig rankerConfig,
                                                                                Reranker reranker,
                                                                                int bfsDepth,
                                                                                int bfsK,
                                                                                QueryExpr filterExpr,
                                                                                List<String> outputFields,
                                                                                List<Double> queryEmbedding,
                                                                                Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(Map.of());
        }

        @Override
        public void attachEmbedder(Embedding embedder) {
        }

        @Override
        public void close() {
        }
    }
}
