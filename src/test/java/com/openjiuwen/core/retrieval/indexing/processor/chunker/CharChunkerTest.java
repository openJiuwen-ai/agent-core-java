/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.common.exception.BaseError;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharChunkerTest {

    @Test
    void fixedSizeChunking() {
        CharChunker chunker = new CharChunker(10, 0);
        List<String> chunks = chunker.chunkText("abcdefghijklmnopqrstuvwxyz");
        assertEquals(3, chunks.size());
        assertEquals("abcdefghij", chunks.get(0));
        assertEquals("klmnopqrst", chunks.get(1));
        assertEquals("uvwxyz", chunks.get(2));
    }

    @Test
    void overlapFeature() {
        CharChunker chunker = new CharChunker(10, 3);
        List<String> chunks = chunker.chunkText("abcdefghijklmnop");
        assertEquals(2, chunks.size());
        assertEquals("abcdefghij", chunks.get(0));
        assertTrue(chunks.get(1).startsWith("hijklmnop"));
    }

    @Test
    void emptyAndNullInput() {
        CharChunker chunker = new CharChunker(10, 0);
        assertTrue(chunker.chunkText("").isEmpty());
        assertTrue(chunker.chunkText(null).isEmpty());
    }

    @Test
    void shortTextReturnsSingleChunk() {
        CharChunker chunker = new CharChunker(100, 0);
        List<String> chunks = chunker.chunkText("hello");
        assertEquals(1, chunks.size());
        assertEquals("hello", chunks.get(0));
    }

    @Test
    void constructorRejectsInvalidChunkSize() {
        assertThrows(BaseError.class, () -> new CharChunker(0, 0));
        assertThrows(BaseError.class, () -> new CharChunker(-1, 0));
    }

    @Test
    void constructorRejectsOverlapGreaterOrEqualChunkSize() {
        assertThrows(BaseError.class, () -> new CharChunker(10, 10));
        assertThrows(BaseError.class, () -> new CharChunker(5, 6));
    }
}
