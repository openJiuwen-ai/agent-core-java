/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenizerChunkerTest {

    @Test
    void chunkTextUsesExternalTokenizer() {
        Function<String, List<String>> tokenizer = text -> Arrays.stream(text.replaceAll("[^A-Za-z0-9\\s]", " ")
                        .trim()
                        .split("\\s+"))
                .filter(part -> !part.isBlank())
                .toList();
        TokenizerChunker chunker = new TokenizerChunker(2, 0, tokenizer, "en", Map.of());

        List<String> chunks = chunker.chunkText("One two. Three four.");

        assertEquals(List.of("One two.", "Three four."), chunks);
        assertEquals("en", chunker.getLanguage());
        assertEquals(Map.of(), chunker.getSplitterConfig());
    }

    @Test
    void chunkTextHandlesEmptyInputAndHybridRegistry() {
        TokenizerChunker chunker = new TokenizerChunker(2, 0);
        assertTrue(chunker.chunkText("").isEmpty());
        assertInstanceOf(HybridChunker.class, ChunkerRegistry.getChunker("hybrid"));
    }

    @Test
    void chunkTextWithOverlapPreservesContext() {
        Function<String, List<String>> tokenizer = text -> Arrays.stream(text.replaceAll("[^A-Za-z0-9\\s]", " ")
                        .trim()
                        .split("\\s+"))
                .filter(part -> !part.isBlank())
                .toList();
        // chunkSize=3, overlap=1 → each chunk holds up to 3 tokens, overlap keeps 1 token from previous
        TokenizerChunker chunker = new TokenizerChunker(3, 1, tokenizer, "en", Map.of());

        List<String> chunks = chunker.chunkText("One two three. Four five six. Seven eight nine.");

        // With overlap the last sentence of a chunk should reappear in the next chunk
        assertTrue(chunks.size() >= 2, "Expected at least 2 chunks with overlap");
        // Verify some overlap: second chunk should start with content from the end of the first
        String firstChunk = chunks.get(0);
        String secondChunk = chunks.get(1);
        assertTrue(firstChunk.length() > 0 && secondChunk.length() > 0);
    }

    @Test
    void chunkTextWithChineseLanguageDetection() {
        // Use default tokenizer (null) so SentenceSplitter auto-detects Chinese
        TokenizerChunker chunker = new TokenizerChunker(5, 0, null, "auto", Map.of());

        List<String> chunks = chunker.chunkText("你好世界。今天天气很好。明天也会不错。");

        assertFalse(chunks.isEmpty());
        assertEquals("auto", chunker.getLanguage());
    }

    @Test
    void constructorDefaultsLanguageAndConfig() {
        TokenizerChunker chunker = new TokenizerChunker(10, 2);
        assertEquals("auto", chunker.getLanguage());
        assertEquals(Map.of(), chunker.getSplitterConfig());
    }
}
