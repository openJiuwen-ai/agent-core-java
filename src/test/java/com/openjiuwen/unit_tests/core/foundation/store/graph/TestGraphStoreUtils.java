package com.openjiuwen.unit_tests.core.foundation.store.graph;

import com.openjiuwen.core.foundation.store.base_embedding.Embedding;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStore;
import com.openjiuwen.core.foundation.store.graph.GraphUtils;
import com.openjiuwen.spi.store.query.QueryExpr;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestGraphStoreUtils {

    @Test
    void testYieldsBatchesOfSizeN() {
        List<List<Integer>> batches = materialize(GraphUtils.batched(List.of(0, 1, 2, 3, 4, 5, 6), 3));
        assertEquals(List.of(List.of(0, 1, 2), List.of(3, 4, 5), List.of(6)), batches);
    }

    @Test
    void testNLessThanOneRaises() {
        assertThrows(IllegalArgumentException.class, () -> GraphUtils.batched(List.of(1, 2, 3), 0));
    }

    @Test
    void testStrictIncompleteBatchRaises() {
        Iterator<List<Integer>> iterator = GraphUtils.batched(List.of(0, 1, 2, 3, 4), 3, true);
        iterator.next();
        assertThrows(IllegalArgumentException.class, iterator::next);
    }

    @Test
    void testStrictCompleteBatchesOk() {
        List<List<Integer>> batches = materialize(GraphUtils.batched(List.of(0, 1, 2, 3, 4, 5), 3, true));
        assertEquals(List.of(List.of(0, 1, 2), List.of(3, 4, 5)), batches);
    }

    @Test
    void testReturnsResultAndMetadata() throws Exception {
        Map.Entry<Integer, String> result = GraphUtils.withMetadata(CompletableFuture.completedFuture(42), "meta").get();
        assertEquals(42, result.getKey());
        assertEquals("meta", result.getValue());
    }

    @Test
    void testReturns32CharHex() {
        String uuid = GraphUtils.getUuid();
        assertEquals(32, uuid.length());
        assertTrue(uuid.matches("[0-9a-f]+"));
    }

    @Test
    void testReplacesNoneEmptyWithNewUuid() throws Exception {
        StubGraphStore backend = new StubGraphStore();
        backend.empty = true;
        List<Object> ids = GraphUtils.ensureUniqueUuids(
                backend,
                Arrays.asList(null, "", "existing_id"),
                "entities",
                false);
        assertEquals(32, ids.get(0).toString().length());
        assertEquals(32, ids.get(1).toString().length());
        assertEquals("existing_id", ids.get(2));
    }

    @Test
    void testSkipTrueReturnsAfterUuidFill() throws Exception {
        StubGraphStore backend = new StubGraphStore();
        List<Object> ids = GraphUtils.ensureUniqueUuids(backend, Arrays.asList(null, "id1"), "entities", true);
        assertEquals(2, ids.size());
        assertEquals("id1", ids.get(1));
    }

    @Test
    void testBackendEmptyReturnsUniqueIds() throws Exception {
        StubGraphStore backend = new StubGraphStore();
        backend.empty = true;
        List<Object> ids = GraphUtils.ensureUniqueUuids(backend, List.of("a", "b"), "entities", false);
        assertEquals(List.of("a", "b"), ids);
    }

    @Test
    void testBackendHasDuplicatesReplacesUntilUnique() throws Exception {
        StubGraphStore backend = new StubGraphStore();
        backend.empty = false;
        backend.duplicateResponses = new ArrayList<>(List.of(
                List.of(Map.of("uuid", "id1")),
                List.of()));
        List<Object> ids = GraphUtils.ensureUniqueUuids(backend, List.of("id1", "id2"), "entities", false);
        assertNotEquals("id1", ids.get(0));
        assertEquals("id2", ids.get(1));
    }

    @Test
    void testFormatsRoleAndContent() {
        String output = GraphUtils.formatListOfMessages(
                List.of(Map.of("role", "user", "content", "Hello"), Map.of("role", "assistant", "content", "Hi")),
                null,
                null);
        assertTrue(output.contains("user"));
        assertTrue(output.contains("Hello"));
        assertTrue(output.contains("assistant"));
        assertTrue(output.contains("Hi"));
    }

    @Test
    void testRoleReplace() {
        String output = GraphUtils.formatListOfMessages(
                List.of(Map.of("role", "assistant", "content", "Hi")),
                Map.of("assistant", "Agent"),
                null);
        assertTrue(output.contains("Agent"));
    }

    @Test
    void testTemplate() {
        String output = GraphUtils.formatListOfMessages(
                List.of(Map.of("role", "user", "content", "x")),
                null,
                "{role}|{content}\n");
        assertEquals("user|x\n", output);
    }

    @Test
    void testPre1970ReturnsOffsetSeconds() {
        ZonedDateTime dt = ZonedDateTime.of(1960, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        assertEquals(-315619200L, (long) GraphUtils.safeTimestamp(dt));
    }

    @Test
    void testPost1970ReturnsNormalTimestamp() {
        ZonedDateTime dt = ZonedDateTime.of(2025, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        assertEquals(dt.toEpochSecond(), (long) GraphUtils.safeTimestamp(dt));
    }

    @Test
    void testReturnsIntRoughlyCurrentTime() {
        long now = java.time.Instant.now().getEpochSecond();
        assertTrue(Math.abs(GraphUtils.getCurrentUtcTimestamp() - now) <= 2);
    }

    @Test
    void testValidTReturnsFormattedString() {
        String output = GraphUtils.formatTimestamp(1735743600L, ZoneOffset.UTC, null);
        assertTrue(output.contains("2025"));
        assertTrue(output.contains(":"));
    }

    @Test
    void testTMinusOneReturnsUnknownDatetime() {
        assertEquals("Unknown Datetime", GraphUtils.formatTimestamp(-1, ZoneOffset.UTC, null));
    }

    @Test
    void testValidTReturnsIsoString() {
        assertTrue(GraphUtils.formatTimestampIso(1735743600L, ZoneOffset.UTC).contains("2025"));
    }

    @Test
    void testInvalidIsoReturnsMinusOneZero() {
        GraphUtils.TimestampWithOffset converted = GraphUtils.iso2timestamp("not-a-date");
        assertEquals(-1, converted.timestamp());
        assertEquals(0, converted.offset());
    }

    @Test
    void testValidTimestampOffsetReturnsDatetime() {
        OffsetDateTime dateTime = GraphUtils.loadStoredTimeFromDb(1735743600L, 0);
        assertNotNull(dateTime);
        assertEquals(2025, dateTime.getYear());
    }

    @Test
    void testTimestampMinusOneReturnsNone() {
        assertNull(GraphUtils.loadStoredTimeFromDb(-1, 0));
    }

    @Test
    void testUtcPlus8Roundtrip() {
        int offset = GraphUtils.storeTzOffset("+08:00");
        assertEquals(8 * 3600, GraphUtils.loadTzOffset(offset).getTotalSeconds());
    }

    @Test
    void testLoadTzOffsetZero() {
        assertEquals(0, GraphUtils.loadTzOffset(0).getTotalSeconds());
    }

    private static <T> List<List<T>> materialize(Iterator<List<T>> iterator) {
        List<List<T>> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    static class StubGraphStore implements GraphStore {
        boolean empty = true;
        List<List<Map<String, Object>>> duplicateResponses = new ArrayList<>();
        int queryCalls = 0;

        @Override
        public GraphConfig getConfig() {
            return null;
        }

        @Override
        public ExecutorService getEmbedExecutor() {
            return null;
        }

        @Override
        public Embedding getEmbedder() {
            return null;
        }

        @Override
        public void refresh() {
        }

        @Override
        public void addData(String collection, Iterable<Map<String, Object>> data, boolean flush, boolean upsert) {
        }

        @Override
        public void addEntity(Iterable<?> entities, boolean flush, boolean upsert, boolean noEmbed) {
        }

        @Override
        public void addRelation(Iterable<?> relations, boolean flush, boolean upsert, boolean noEmbed) {
        }

        @Override
        public void addEpisode(Iterable<?> episodes, boolean flush, boolean upsert, boolean noEmbed) {
        }

        @Override
        public boolean isEmpty(String collection) {
            return empty;
        }

        @Override
        public List<Map<String, Object>> query(String collection, List<Object> ids, QueryExpr expr,
                                               boolean silenceErrors) {
            if (duplicateResponses.isEmpty()) {
                return List.of();
            }
            return duplicateResponses.get(Math.min(queryCalls++, duplicateResponses.size() - 1));
        }

        @Override
        public Map<String, Object> delete(String collection, List<Object> ids, QueryExpr expr) {
            return Map.of();
        }

        @Override
        public Map<String, List<Map<String, Object>>> search(String queryText, int k, String collection,
                                                             Object rankerConfig, int bfsDepth, int bfsK,
                                                             QueryExpr filterExpr, List<String> outputFields,
                                                             List<Float> queryEmbedding,
                                                             Map<String, Object> kwargs) {
            return Map.of();
        }

        @Override
        public void attachEmbedder(Embedding embedder) {
        }

        @Override
        public void close() {
        }
    }
}
