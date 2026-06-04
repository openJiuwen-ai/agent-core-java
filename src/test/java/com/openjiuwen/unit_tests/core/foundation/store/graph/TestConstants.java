package com.openjiuwen.unit_tests.core.foundation.store.graph;

import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.milvus.GenerateMilvusSchema;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TestConstants {

    @Test
    void testEntityCollectionValue() {
        assertEquals("entity", GenerateMilvusSchema.ENTITY_COLLECTION);
    }

    @Test
    void testRelationCollectionValue() {
        assertEquals("relation", GenerateMilvusSchema.RELATION_COLLECTION);
    }

    @Test
    void testEpisodeCollectionValue() {
        assertEquals("episode", GenerateMilvusSchema.EPISODE_COLLECTION);
    }

    @Test
    void testCollectionConstantsAreDistinct() {
        assertNotEquals(GenerateMilvusSchema.ENTITY_COLLECTION, GenerateMilvusSchema.RELATION_COLLECTION);
    }

    @Test
    void testDefaultWorkerNumValue() throws Exception {
        Path tempDir = Files.createTempDirectory("graph-constants-");
        GraphConfig config = GraphConfig.builder().uri(tempDir.resolve("graph.db").toString()).build();
        assertEquals(10, config.getWorkerThreads());
    }

    @Test
    void testDefaultEmbedBatchSizeValue() throws Exception {
        Path tempDir = Files.createTempDirectory("graph-constants-batch-");
        GraphConfig config = GraphConfig.builder().uri(tempDir.resolve("graph.db").toString()).build();
        assertEquals(10, config.getEmbedBatchSize());
    }
}
