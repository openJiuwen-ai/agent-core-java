/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code CharChunker} behavior in
 * {@code openjiuwen/core/retrieval/indexing/processor/chunker/char_chunker.py}.
 */
class CharChunkerTest {

    @Test
    void emptyInputReturnsNoChunks() {
        CharChunker chunker = new CharChunker();

        assertThat(chunker.chunkText(null)).isEmpty();
        assertThat(chunker.chunkText("")).isEmpty();
    }

    @Test
    void splitsTextUsingConfiguredCharSplitter() {
        CharChunker chunker = new CharChunker(5, 1);

        List<String> chunks = chunker.chunkText("abcdefghijk");

        assertThat(chunks).containsExactly("abcde", "efghi", "ijk");
    }

    @Test
    void defaultsMatchPythonConstructor() {
        CharChunker chunker = new CharChunker();

        assertThat(chunker.getChunkSize()).isEqualTo(512);
        assertThat(chunker.getChunkOverlap()).isEqualTo(50);
    }
}
