/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.splitter;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's base splitter behavior in
 * {@code openjiuwen/core/retrieval/indexing/processor/splitter/base.py}.
 */
class SplitterTest {

    @Test
    void constructorValidatesChunkArguments() {
        BaseError chunkSizeError = assertThrows(
                BaseError.class,
                () -> new StubSplitter(null, 0, 1)
        );
        BaseError overlapError = assertThrows(
                BaseError.class,
                () -> new StubSplitter(null, 10, 10)
        );

        assertEquals(StatusCode.RETRIEVAL_INDEXING_CHUNK_SIZE_INVALID, chunkSizeError.getStatus());
        assertEquals(StatusCode.RETRIEVAL_INDEXING_CHUNK_OVERLAP_INVALID, overlapError.getStatus());
    }

    @Test
    void splitTextReturnsOnlyChunkTexts() {
        StubSplitter splitter = new StubSplitter(null, 10, 1);

        assertEquals(List.of("alpha", "beta"), splitter.splitText("ignored"));
    }

    @Test
    void getNodesFromDocumentsSkipsEmptyDocumentsAndBuildsTextChunks() {
        StubSplitter splitter = new StubSplitter((Splitter.TokenizerAdapter) text -> List.of(1, 2, 3), 10, 1);

        List<TextChunk> chunks = splitter.getNodesFromDocuments(List.of(
                new Document("doc-1", "hello world"),
                new Document("doc-2", " "),
                new Document("doc-3", "second")
        ));

        assertEquals(4, chunks.size());
        assertEquals("alpha", chunks.get(0).getText());
        assertEquals("doc-1", chunks.get(0).getDocId());
        assertEquals(3, splitter.exposedTokenCount("abc"));
    }

    private static final class StubSplitter extends Splitter {

        StubSplitter(Object tokenizer, int chunkSize, int chunkOverlap) {
            super(tokenizer, chunkSize, chunkOverlap);
        }

        @Override
        public List<SplitChunk> split(String doc) {
            return List.of(
                    new SplitChunk("alpha", 0, 5),
                    new SplitChunk("beta", 6, 10)
            );
        }

        int exposedTokenCount(String text) {
            return getTokenCount(text);
        }
    }
}
