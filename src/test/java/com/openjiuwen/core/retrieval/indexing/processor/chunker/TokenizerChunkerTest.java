/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TokenizerChunker} behavior in
 * {@code openjiuwen/core/retrieval/indexing/processor/chunker/tokenizer_chunker.py}.
 *
 * <p>Mirrors Python's {@code TestTokenizerChunker} in
 * {@code tests/unit_tests/core/retrieval/indexing/processor/chunker/test_tokenizer_chunker.py}.</p>
 */
class TokenizerChunkerTest {

    @Test
    void initStoresChunkerConfiguration() {
        TokenizerChunker chunker = new TokenizerChunker(512, 50, new WhitespaceTokenizer());

        assertThat(chunker.getChunkSize()).isEqualTo(512);
        assertThat(chunker.getChunkOverlap()).isEqualTo(50);
        assertThat(chunker.getTokenizer()).isInstanceOf(WhitespaceTokenizer.class);
    }

    @Test
    void chunkTextSuccessReturnsChunkTexts() {
        TokenizerChunker chunker = new TokenizerChunker(4, 1, new WhitespaceTokenizer(), "en", null);

        List<String> chunks = chunker.chunkText("one two three four five six seven");

        assertThat(chunks).containsExactly("one two three four", "four five six seven");
    }

    @Test
    void chunkTextEmptyReturnsNoChunks() {
        TokenizerChunker chunker = new TokenizerChunker(512, 50, new WhitespaceTokenizer());

        assertThat(chunker.chunkText("")).isEmpty();
    }

    @Test
    void chunkTextNullReturnsNoChunks() {
        TokenizerChunker chunker = new TokenizerChunker(512, 50, new WhitespaceTokenizer());

        assertThat(chunker.chunkText(null)).isEmpty();
    }

    private static final class WhitespaceTokenizer implements IndexSentenceSplitter.TokenCodec {

        @Override
        public List<String> encode(String text, int maxLength) {
            return List.of(text.split("\\s+"));
        }
    }
}
