/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's tests in
 * {@code tests/unit_tests/core/retrieval/indexing/processor/chunker/test_text_splitter.py}.
 */
class TextSplitterTest {

    @Test
    void textSplitterIsAbstract() {
        assertTrue(Modifier.isAbstract(TextSplitter.class.getModifiers()));
    }

    @Test
    void charSplitterInitializesWithDefaultsAndCustomValues() {
        CharSplitter defaults = new CharSplitter();
        CharSplitter custom = new CharSplitter(512, 50);

        assertEquals(200, defaults.getChunkSize());
        assertEquals(40, defaults.getChunkOverlap());
        assertEquals(512, custom.getChunkSize());
        assertEquals(50, custom.getChunkOverlap());
    }

    @Test
    void charSplitterInitializesWithDefaults() {
        CharSplitter splitter = new CharSplitter();

        assertEquals(200, splitter.getChunkSize());
        assertEquals(40, splitter.getChunkOverlap());
    }

    @Test
    void charSplitterInitializesWithCustomValues() {
        CharSplitter splitter = new CharSplitter(512, 50);

        assertEquals(512, splitter.getChunkSize());
        assertEquals(50, splitter.getChunkOverlap());
    }

    @Test
    void charSplitterAdjustsInvalidSizesAndOverlap() {
        assertTrue(new CharSplitter(100, 150).getChunkOverlap() < 100);
        assertTrue(new CharSplitter(100, -10).getChunkOverlap() >= 0);
        assertTrue(new CharSplitter(0, null).getChunkSize() >= 1);
    }

    @Test
    void charSplitterAdjustsOverlapLargerThanChunkSize() {
        CharSplitter splitter = new CharSplitter(100, 150);

        assertTrue(splitter.getChunkOverlap() < splitter.getChunkSize());
    }

    @Test
    void charSplitterClampsNegativeOverlap() {
        CharSplitter splitter = new CharSplitter(100, -10);

        assertTrue(splitter.getChunkOverlap() >= 0);
    }

    @Test
    void charSplitterUsesMinimumChunkSize() {
        CharSplitter splitter = new CharSplitter(0, null);

        assertTrue(splitter.getChunkSize() >= 1);
    }

    @Test
    void charSplitterSplitsShortAndLongText() {
        CharSplitter shortSplitter = new CharSplitter(100, 10);
        List<TextChunk> shortChunks = shortSplitter.split(new Document("doc_1", "Short text"));

        assertEquals(1, shortChunks.size());
        assertEquals("Short text", shortChunks.getFirst().getText());
        assertEquals("doc_1", shortChunks.getFirst().getDocId());

        CharSplitter longSplitter = new CharSplitter(10, 2);
        List<TextChunk> longChunks = longSplitter.split(new Document(
                "doc_1",
                "This is a longer text that needs to be split into multiple chunks"
        ));

        assertTrue(longChunks.size() > 1);
        assertTrue(longChunks.stream().allMatch(chunk -> "doc_1".equals(chunk.getDocId())));
    }

    @Test
    void charSplitterSplitsShortText() {
        CharSplitter splitter = new CharSplitter(100, 10);
        List<TextChunk> chunks = splitter.split(new Document("doc_1", "Short text"));

        assertEquals(1, chunks.size());
        assertEquals("Short text", chunks.getFirst().getText());
        assertEquals("doc_1", chunks.getFirst().getDocId());
    }

    @Test
    void charSplitterSplitsLongText() {
        CharSplitter splitter = new CharSplitter(10, 2);
        List<TextChunk> chunks = splitter.split(new Document(
                "doc_1",
                "This is a longer text that needs to be split into multiple chunks"
        ));

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> "doc_1".equals(chunk.getDocId())));
    }

    @Test
    void charSplitterKeepsOverlapAndMetadata() {
        CharSplitter splitter = new CharSplitter(10, 3);
        List<TextChunk> chunks = splitter.split(new Document(
                "doc_1",
                "This is a test text for splitting",
                Map.of("source", "test", "author", "test_author")
        ));

        assertTrue(chunks.size() > 1);
        assertEquals(chunks.get(0).getText().substring(chunks.get(0).getText().length() - 3),
                chunks.get(1).getText().substring(0, 3));
        assertTrue(chunks.stream().allMatch(chunk -> "test".equals(chunk.getMetadata().get("source"))));
    }

    @Test
    void charSplitterPreservesMetadata() {
        CharSplitter splitter = new CharSplitter(10, 2);
        List<TextChunk> chunks = splitter.split(new Document(
                "doc_1",
                "This is a test",
                Map.of("source", "test", "author", "test_author")
        ));

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(chunk -> "test".equals(chunk.getMetadata().get("source"))));
    }

    @Test
    void indexSentenceSplitterUsesTokenizerMaxLengthAndDefaultOverlap() {
        IndexSentenceSplitter splitter = new IndexSentenceSplitter(new TokenizeOnly(), null, null, null, "en");

        assertEquals(512, splitter.getChunkSize());
        assertEquals(102, splitter.getChunkOverlap());
    }

    @Test
    void indexSentenceSplitterResolvesChunkSizeAndSplitsDocument() {
        IndexSentenceSplitter splitter = new IndexSentenceSplitter(new TokenizeOnly(), 64, 8, null, "en");
        List<TextChunk> chunks = splitter.split(new Document("doc_1", "This is a test. This is another test."));

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(chunk -> "doc_1".equals(chunk.getDocId())));
    }

    @Test
    void indexSentenceSplitterConvertsTextChunkToDocument() {
        IndexSentenceSplitter splitter = new IndexSentenceSplitter(new TokenizeOnly(), 64, 8, null, "en");
        TextChunk chunk = new TextChunk("chunk_1", "This is a test.", "doc_1", Map.of("k", "v"));

        List<TextChunk> chunks = splitter.split(chunk);

        assertEquals(1, chunks.size());
        assertEquals("doc_1", chunks.getFirst().getDocId());
        assertEquals("v", chunks.getFirst().getMetadata().get("k"));
    }

    @Test
    void indexSentenceSplitterLongSingleSentenceUsesDecodeAndPreservesCoverage() {
        String longSentence = String.join(" ", java.util.stream.IntStream.range(0, 30)
                .mapToObj(index -> "w" + index)
                .toList());
        IndexSentenceSplitter splitter = new IndexSentenceSplitter(new DecodeTokenizer(), 8, 2, null, "en");

        List<TextChunk> chunks = splitter.split(new Document("doc_long", longSentence, Map.of("k", "v")));

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> "doc_long".equals(chunk.getDocId())));
        assertTrue(chunks.stream().allMatch(chunk -> "v".equals(chunk.getMetadata().get("k"))));
        Set<String> covered = new HashSet<>();
        for (TextChunk chunk : chunks) {
            covered.addAll(List.of(chunk.getText().split("\\s+")));
        }
        assertEquals(new HashSet<>(List.of(longSentence.split("\\s+"))), covered);
    }

    private static final class TokenizeOnly implements IndexSentenceSplitter.TokenCodec {

        @Override
        public List<String> encode(String text, int maxLength) {
            return List.of(text.split("\\s+"));
        }

        @Override
        public boolean canDecode() {
            return false;
        }

        @Override
        public Integer maxTokenLength() {
            return 512;
        }
    }

    private static final class DecodeTokenizer implements IndexSentenceSplitter.TokenCodec {

        @Override
        public List<String> encode(String text, int maxLength) {
            return List.of(text.split("\\s+"));
        }

        @Override
        public String decode(List<String> tokens) {
            return String.join(" ", tokens);
        }

        @Override
        public Integer maxTokenLength() {
            return 1024;
        }
    }
}
