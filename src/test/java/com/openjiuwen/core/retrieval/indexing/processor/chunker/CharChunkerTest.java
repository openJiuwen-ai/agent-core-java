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
 *
 * <p>Mirrors Python's {@code TestCharChunker} in
 * {@code tests/unit_tests/core/retrieval/indexing/processor/chunker/test_char_chunker.py}.</p>
 */
class CharChunkerTest {

    @Test
    void defaultsMatchPythonConstructor() {
        CharChunker chunker = new CharChunker();

        assertThat(chunker.getChunkSize()).isEqualTo(512);
        assertThat(chunker.getChunkOverlap()).isEqualTo(50);
    }

    @Test
    void customConstructorStoresChunkSizeAndOverlap() {
        CharChunker chunker = new CharChunker(256, 25);

        assertThat(chunker.getChunkSize()).isEqualTo(256);
        assertThat(chunker.getChunkOverlap()).isEqualTo(25);
    }

    @Test
    void chunkTextSuccessUsesConfiguredCharSplitter() {
        CharChunker chunker = new CharChunker(5, 1);

        List<String> chunks = chunker.chunkText("abcdefghijk");

        assertThat(chunks).containsExactly("abcde", "efghi", "ijk");
    }

    @Test
    void emptyTextReturnsNoChunks() {
        CharChunker chunker = new CharChunker();

        assertThat(chunker.chunkText("")).isEmpty();
    }

    @Test
    void nullTextReturnsNoChunks() {
        CharChunker chunker = new CharChunker();

        assertThat(chunker.chunkText(null)).isEmpty();
    }
}
