/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.retrieval.indexing.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.CharSplitterText;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.IndexSentenceSplitter;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.TextSplitter;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's tests/unit_tests/core/retrieval/indexing/processor/chunker/test_text_splitter.py.
 */
class TestTextSplitter {

    private static final class ConcreteTextSplitter extends TextSplitter {
        @Override
        public List<TextChunk> split(Document doc) {
            String prefix = doc.getText().substring(0, Math.min(10, doc.getText().length()));
            return List.of(TextChunk.fromDocument(doc, prefix, "chunk-1"));
        }
    }

    @Test
    void testCannotInstantiateAbstractClass() {
        assertTrue(Modifier.isAbstract(TextSplitter.class.getModifiers()));
    }

    @Test
    void testConcreteSplitterWithDocument() {
        List<TextChunk> chunks = new ConcreteTextSplitter().split(new Document("doc_1", "This is a test"));

        assertEquals(1, chunks.size());
        assertEquals("This is a ", chunks.getFirst().getText());
        assertEquals("doc_1", chunks.getFirst().getDocId());
    }

    @Test
    void testInitWithDefaults() {
        CharSplitterText splitter = new CharSplitterText();

        assertEquals(200, splitter.getChunkSize());
        assertEquals(40, splitter.getChunkOverlap());
    }

    @Test
    void testInitWithCustomValues() {
        CharSplitterText splitter = new CharSplitterText(512, 50);

        assertEquals(512, splitter.getChunkSize());
        assertEquals(50, splitter.getChunkOverlap());
    }

    @Test
    void testInitOverlapAdjusted() {
        CharSplitterText splitter = new CharSplitterText(100, 150);

        assertTrue(splitter.getChunkOverlap() < splitter.getChunkSize());
    }

    @Test
    void testInitOverlapNegative() {
        CharSplitterText splitter = new CharSplitterText(100, -10);

        assertTrue(splitter.getChunkOverlap() >= 0);
    }

    @Test
    void testInitChunkSizeMinimum() {
        CharSplitterText splitter = new CharSplitterText(0, 0);

        assertTrue(splitter.getChunkSize() >= 1);
    }

    @Test
    void testSplitShortText() {
        List<TextChunk> chunks = new CharSplitterText(100, 10).split(new Document("doc_1", "Short text"));

        assertEquals(1, chunks.size());
        assertEquals("Short text", chunks.getFirst().getText());
        assertEquals("doc_1", chunks.getFirst().getDocId());
    }

    @Test
    void testSplitLongText() {
        List<TextChunk> chunks = new CharSplitterText(10, 2)
                .split(new Document("doc_1", "This is a longer text that needs to be split into multiple chunks"));

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> "doc_1".equals(chunk.getDocId())));
    }

    @Test
    void testSplitWithOverlap() {
        List<TextChunk> chunks = new CharSplitterText(10, 3)
                .split(new Document("doc_1", "This is a test text for splitting"));

        assertTrue(chunks.size() > 1);
        assertEquals(3, chunks.get(0).getText().substring(chunks.get(0).getText().length() - 3).length());
        assertEquals(3, chunks.get(1).getText().substring(0, 3).length());
    }

    @Test
    void testSplitPreservesMetadata() {
        List<TextChunk> chunks = new CharSplitterText(10, 2)
                .split(new Document("doc_1", "This is a test", Map.of("source", "test", "author", "test_author")));

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(chunk -> "test".equals(chunk.getMetadata().get("source"))));
    }

    @Test
    void testSplitWithTextChunk() {
        List<TextChunk> chunks = new ConcreteTextSplitter()
                .split(new TextChunk("1", "This is a test", "doc_1", Map.of("source", "test"), null));

        assertEquals(1, chunks.size());
        assertEquals("doc_1", chunks.getFirst().getDocId());
        assertEquals("test", chunks.getFirst().getMetadata().get("source"));
    }

    @Test
    void testIndexSentenceSplitterDefaultConfiguration() {
        Function<String, List<String>> tokenizer = text -> Arrays.asList(text.split("\\s+"));
        List<TextChunk> chunks = new IndexSentenceSplitter(tokenizer, null, null, null, "auto")
                .split(new Document("doc_1", "This is a test."));

        assertFalse(chunks.isEmpty());
    }

    @Test
    void testIndexSentenceSplitterWithDocument() {
        Function<String, List<String>> tokenizer = text -> Arrays.asList(text.split("\\s+"));
        List<TextChunk> chunks = new IndexSentenceSplitter(tokenizer, 8, 2, null, "en")
                .split(new Document("doc_1", "This is sentence one. This is sentence two."));

        assertTrue(chunks.size() >= 1);
        assertTrue(chunks.stream().allMatch(chunk -> "doc_1".equals(chunk.getDocId())));
    }

    @Test
    void testIndexSentenceSplitterWithTextChunk() {
        Function<String, List<String>> tokenizer = text -> Arrays.asList(text.split("\\s+"));
        List<TextChunk> chunks = new IndexSentenceSplitter(tokenizer, 8, 2, null, "en")
                .split(new TextChunk("1", "This is a test sentence.", "doc_1"));

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(item -> "doc_1".equals(item.getDocId())));
    }

    @Test
    void testSplitLongSingleSentenceMultipleChunksIntegration() {
        Function<String, List<String>> tokenizer =
                text -> Arrays.stream(text.split("\\s+")).collect(Collectors.toList());
        String longSentence = java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> "w" + i)
                .collect(Collectors.joining(" "));
        List<TextChunk> chunks = new IndexSentenceSplitter(tokenizer, 8, 2, null, "en")
                .split(new Document("doc_long", longSentence, Map.of("k", "v")));

        Set<String> covered = chunks.stream()
                .flatMap(chunk -> Arrays.stream(chunk.getText().split("\\s+")))
                .collect(Collectors.toSet());

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> "doc_long".equals(chunk.getDocId())));
        assertTrue(chunks.stream().allMatch(chunk -> "v".equals(chunk.getMetadata().get("k"))));
        assertEquals(Set.copyOf(Arrays.asList(longSentence.split("\\s+"))), covered);
    }
}
