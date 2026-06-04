/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.retrieval.indexing.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.CharChunker;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.ChunkerRegistry;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.HybridChunker;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's tests/unit_tests/core/retrieval/indexing/processor/chunker/test_chunker_registry.py.
 */
class TestChunkerRegistry {

    @Test
    void testGetCharChunker() {
        Chunker chunker = ChunkerRegistry.getChunker("char", Map.of("chunk_size", 256, "chunk_overlap", 30));

        assertInstanceOf(CharChunker.class, chunker);
        assertEquals(256, chunker.getChunkSize());
        assertEquals(30, chunker.getChunkOverlap());
    }

    @Test
    void testGetCharChunkerDefaults() {
        Chunker chunker = ChunkerRegistry.getChunker("char");

        assertInstanceOf(CharChunker.class, chunker);
        assertEquals(512, chunker.getChunkSize());
    }

    @Test
    void testGetHybridChunker() {
        Chunker chunker = ChunkerRegistry.getChunker("hybrid", Map.of("chunk_size", 128, "chunk_overlap", 20));

        assertInstanceOf(HybridChunker.class, chunker);
        assertEquals(128, chunker.getChunkSize());
        assertEquals(20, chunker.getChunkOverlap());
    }

    @Test
    void testGetHybridChunkerDefaults() {
        Chunker chunker = ChunkerRegistry.getChunker("hybrid");

        assertInstanceOf(HybridChunker.class, chunker);
        assertEquals(512, chunker.getChunkSize());
    }

    @Test
    void testGetHybridWithCustomInner() {
        CharChunker inner = new CharChunker(64, 10);

        Chunker chunker = ChunkerRegistry.getChunker("hybrid", Map.of("inner_chunker", inner));

        assertInstanceOf(HybridChunker.class, chunker);
        assertEquals(64, chunker.getChunkSize());
    }

    @Test
    void testGetHybridWithNoSplitWhen() {
        Chunker chunker = ChunkerRegistry.getChunker(
                "hybrid",
                Map.of("no_split_when", (java.util.function.Predicate<Document>) doc ->
                        "special".equals((doc.getMetadata() == null ? Map.of() : doc.getMetadata()).get("type"))));

        assertInstanceOf(HybridChunker.class, chunker);
    }

    @Test
    void testGetUnknownChunker() {
        assertThrows(NoSuchElementException.class, () -> ChunkerRegistry.getChunker("nonexistent"));
    }

    @Test
    void testHybridUnknownKwargsWhenInnerProvided() {
        CharChunker inner = new CharChunker(64, 10);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ChunkerRegistry.getChunker("hybrid", Map.of("inner_chunker", inner, "bad_param", true)));

        assertTrue(error.getMessage().contains("Unknown kwargs"));
    }

    @Test
    void testHybridExtraKwargsPassedToInner() {
        Chunker chunker = ChunkerRegistry.getChunker("hybrid", Map.of("chunk_size", 256, "chunk_overlap", 10));

        assertEquals(256, chunker.getChunkSize());
        assertEquals(10, chunker.getChunkOverlap());
    }

    @Test
    void testHybridInnerChunkerNotChunker() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ChunkerRegistry.getChunker("hybrid", Map.of("inner_chunker", "not a chunker")));

        assertTrue(error.getMessage().contains("inner_chunker must be a Chunker instance"));
    }

    @Test
    void testReturnTypeValidation() {
        String name = uniqueName("_test_bad_return");
        ChunkerRegistry.registerChunker(name, kwargs -> "not a chunker");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ChunkerRegistry.getChunker(name));

        assertTrue(error.getMessage().contains("must return a Chunker instance"));
    }

    @Test
    void testRegisterAndGet() {
        String name = uniqueName("_test_my_chunker");
        ChunkerRegistry.registerChunker(name, kwargs -> new CharChunker());

        Chunker chunker = ChunkerRegistry.getChunker(name);

        assertInstanceOf(CharChunker.class, chunker);
    }

    @Test
    void testRegisterDuplicateRaises() {
        String name = uniqueName("_test_dup");
        ChunkerRegistry.registerChunker(name, kwargs -> new CharChunker());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ChunkerRegistry.registerChunker(name, kwargs -> new CharChunker()));

        assertTrue(error.getMessage().contains("already registered"));
    }

    @Test
    void testRegisterDuplicateWithOverwrite() {
        String name = uniqueName("_test_overwrite");
        ChunkerRegistry.registerChunker(name, kwargs -> new CharChunker());
        ChunkerRegistry.registerChunker(name, kwargs -> new CharChunker(200, 10), true);

        Chunker chunker = ChunkerRegistry.getChunker(name);

        assertEquals(200, chunker.getChunkSize());
        assertEquals(10, chunker.getChunkOverlap());
    }

    @Test
    void testRegisterEmptyName() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ChunkerRegistry.registerChunker("", kwargs -> new CharChunker()));

        assertTrue(error.getMessage().contains("non-empty string"));
    }

    @Test
    void testRegisterWhitespaceName() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ChunkerRegistry.registerChunker("   ", kwargs -> new CharChunker()));

        assertTrue(error.getMessage().contains("non-empty string"));
    }

    @Test
    void testBuiltinCharRegistered() {
        assertTrue(ChunkerRegistry.isRegistered("char"));
    }

    @Test
    void testBuiltinHybridRegistered() {
        assertTrue(ChunkerRegistry.isRegistered("hybrid"));
    }

    @Test
    void testRegisterFactoryCallable() {
        String name = uniqueName("_test_factory");
        ChunkerRegistry.registerChunker(name, kwargs -> new CharChunker((int) kwargs.getOrDefault("chunk_size", 100), 0));

        Chunker chunker = ChunkerRegistry.getChunker(name, Map.of("chunk_size", 200));

        assertInstanceOf(CharChunker.class, chunker);
        assertEquals(200, chunker.getChunkSize());
    }

    @Test
    void testOverwriteBuiltinBlocked() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ChunkerRegistry.registerChunker("char", kwargs -> new CharChunker()));

        assertTrue(error.getMessage().contains("already registered"));
    }

    private static String uniqueName(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
