/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.core.foundation.store.base_reranker.Reranker;
import com.openjiuwen.core.foundation.store.query.QueryExpr;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's graph utility behavior in
 * {@code openjiuwen/core/foundation/store/graph/utils.py}.
 */
class GraphStoreUtilsTest {

    @Test
    void batchedMatchesPythonFallbackBehavior() {
        assertEquals(List.of(List.of(1, 2), List.of(3, 4), List.of(5)),
                GraphStoreUtils.batched(List.of(1, 2, 3, 4, 5), 2));
        assertEquals(List.of(List.of("a", "b")),
                GraphStoreUtils.batched(List.of("a", "b"), 2, true));

        assertThrows(IllegalArgumentException.class, () -> GraphStoreUtils.batched(List.of(1), 0));
        assertThrows(IllegalArgumentException.class, () -> GraphStoreUtils.batched(List.of(1, 2, 3), 2, true));
    }

    @Test
    void withMetadataReturnsResultAndMetadata() {
        GraphStoreUtils.MetadataResult<String, Integer> result =
                GraphStoreUtils.withMetadata(CompletableFuture.completedFuture("ok"), 7).join();

        assertEquals("ok", result.result());
        assertEquals(7, result.metadata());
    }

    @Test
    void ensureUniqueUuidsReplacesMissingAndBackendDuplicates() {
        FakeGraphStore backend = new FakeGraphStore(false, List.of(List.of("existing", "new-1"), List.of()));
        AtomicInteger counter = new AtomicInteger(1);

        List<Object> unique = GraphStoreUtils.ensureUniqueUuids(
                backend,
                List.of("existing", "", "fresh"),
                "entities",
                false,
                () -> "new-" + counter.getAndIncrement()).join();

        assertEquals(List.of("new-2", "new-3", "fresh"), unique);
        assertEquals(List.of(List.of("existing", "new-1", "fresh"), List.of("new-2", "new-3")),
                backend.queriedIds);
    }

    @Test
    void ensureUniqueUuidsSkipsBackendWhenRequested() {
        FakeGraphStore backend = new FakeGraphStore(false, List.of());

        List<Object> unique = GraphStoreUtils.ensureUniqueUuids(
                backend,
                Arrays.asList("a", null),
                "entities",
                true,
                () -> "generated").join();

        assertEquals(List.of("a", "generated"), unique);
        assertTrue(backend.queriedIds.isEmpty());
    }

    @Test
    void formatsMessageListsWithRoleReplacement() {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "assistant", "content", "hello"),
                Map.of("role", "user", "content", "question"));

        assertEquals("Writing Assistant: hello\nuser: question\n",
                GraphStoreUtils.formatListOfMessages(messages, Map.of("assistant", "Writing Assistant"),
                        "{role}: {content}\n"));
    }

    @Test
    void timestampHelpersPreservePythonEdgeCases() {
        OffsetDateTime dateTime = OffsetDateTime.of(2025, 9, 10, 15, 56, 53, 0, ZoneOffset.ofHours(8));
        GraphStoreUtils.TimestampOffset parsed = GraphStoreUtils.iso2timestamp("2025-09-10T15:56:53+08:00");

        assertEquals((long) GraphStoreUtils.safeTimestamp(dateTime), parsed.timestamp());
        assertEquals(32, parsed.offset());
        assertEquals("2025-09-10T15:56:53+08:00",
                GraphStoreUtils.formatTimestampIso(parsed.timestamp(), ZoneOffset.ofHours(8)));
        assertEquals("(Wed) 2025/Sep/10 15:56:53",
                GraphStoreUtils.formatTimestamp(parsed.timestamp(), ZoneOffset.ofHours(8),
                        "(%a) %Y/%b/%d %H:%M:%S"));
        assertEquals("Unknown Datetime", GraphStoreUtils.formatTimestamp(-1));
        assertEquals("Unknown Datetime", GraphStoreUtils.formatTimestampIso(-1));

        assertEquals(-1L, (long) GraphStoreUtils.safeTimestamp(LocalDateTime.of(1969, 12, 31, 23, 59, 59)));
        assertEquals(1, GraphStoreUtils.safeDatetimeFromTimestamp(-1, ZoneOffset.UTC).getSecond());
        assertEquals(ZoneOffset.ofHours(8), GraphStoreUtils.loadStoredTimeFromDb(parsed.timestamp(), 32)
                .orElseThrow().getOffset());
        assertTrue(GraphStoreUtils.loadStoredTimeFromDb(-1, 32).isEmpty());
    }

    @Test
    void iso2timestampReturnsSentinelForInvalidOrUnsupportedOffsets() {
        assertEquals(new GraphStoreUtils.TimestampOffset(-1, 0), GraphStoreUtils.iso2timestamp("invalid"));
        assertEquals(new GraphStoreUtils.TimestampOffset(-1, 0),
                GraphStoreUtils.iso2timestamp("2025-09-10T15:56:53-05:00"));
        assertEquals(0, GraphStoreUtils.iso2timestamp("2025-09-10T24:00:00+").offset());
    }

    private static final class FakeGraphStore implements GraphStore {

        private final boolean empty;
        private final List<List<String>> duplicateRounds;
        private final List<List<?>> queriedIds = new ArrayList<>();
        private int queryIndex;

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
        public CompletableFuture<Void> addRelation(Iterable<?> relations, boolean flush, boolean upsert, boolean noEmbed) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> addEpisode(Iterable<?> episodes, boolean flush, boolean upsert, boolean noEmbed) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean isEmpty(String collection) {
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
            List<Map<String, Object>> rows = duplicates.stream()
                    .map(uuid -> Map.<String, Object>of("uuid", uuid))
                    .toList();
            return CompletableFuture.completedFuture(rows);
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
