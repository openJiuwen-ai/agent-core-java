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
 */
class TokenizerChunkerTest {

    @Test
    void emptyInputReturnsNoChunks() {
        TokenizerChunker chunker = new TokenizerChunker(8, 2, new WhitespaceTokenizer());

        assertThat(chunker.chunkText(null)).isEmpty();
        assertThat(chunker.chunkText("")).isEmpty();
    }

    @Test
    void constructsSplitterFromTokenizerAndConfig() {
        Map<String, Object> splitterConfig = Map.of("separator", " ");
        TokenizerChunker chunker = new TokenizerChunker(8, 2, new WhitespaceTokenizer(), "en", splitterConfig);

        assertThat(chunker.getChunkSize()).isEqualTo(8);
        assertThat(chunker.getChunkOverlap()).isEqualTo(2);
        assertThat(chunker.getLanguage()).isEqualTo("en");
        assertThat(chunker.getSplitterConfig()).containsEntry("separator", " ");
        assertThat(chunker.getTokenizer()).isInstanceOf(WhitespaceTokenizer.class);
    }

    @Test
    void splitsTextWithTokenizerBackedSentenceSplitter() {
        TokenizerChunker chunker = new TokenizerChunker(4, 1, new WhitespaceTokenizer(), "en", null);

        List<String> chunks = chunker.chunkText("one two three four five six seven");

        assertThat(chunks).isNotEmpty();
        assertThat(String.join(" ", chunks)).contains("one").contains("seven");
    }

    private static final class WhitespaceTokenizer implements IndexSentenceSplitter.TokenCodec {

        @Override
        public List<String> encode(String text, int maxLength) {
            return List.of(text.split("\\s+"));
        }
    }
}
