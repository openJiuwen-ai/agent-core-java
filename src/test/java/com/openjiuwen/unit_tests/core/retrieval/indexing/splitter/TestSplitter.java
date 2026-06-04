/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.splitter;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.indexing.processor.splitter.Splitter;
import com.openjiuwen.core.retrieval.indexing.processor.splitter.Tokenizer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Splitter abstract base class test cases.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/core/retrieval/indexing/processor/splitter/test_base.py}
 * with Java constructor and API adaptations.</p>
 */
class TestSplitter {

    @Test
    void testInitWithDefaults() {
        ConcreteSplitter splitter = new ConcreteSplitter();

        assertEquals(512, splitter.chunkSizeValue());
        assertEquals(50, splitter.chunkOverlapValue());
        assertNull(splitter.getTokenizer());
        assertNull(splitter.tokenizerEncValue());
        assertNull(splitter.tokenizerDecValue());
    }

    @Test
    void testInitWithCustomValues() {
        ConcreteSplitter splitter = new ConcreteSplitter(1024, 100);

        assertEquals(1024, splitter.chunkSizeValue());
        assertEquals(100, splitter.chunkOverlapValue());
    }

    @Test
    void testInitWithTokenizer() {
        ConcreteSplitter splitter = new ConcreteSplitter(512, 50, new SimpleTokenizer());

        assertNotNull(splitter.getTokenizer());
        assertNotNull(splitter.tokenizerEncValue());
        assertNotNull(splitter.tokenizerDecValue());
    }

    @Test
    void testInitWithEncodeOnlyTokenizer() {
        ConcreteSplitter splitter = new ConcreteSplitter(512, 50, new EncodeOnlyTokenizer());

        assertNotNull(splitter.getTokenizer());
        assertNotNull(splitter.tokenizerEncValue());
        assertNull(splitter.tokenizerDecValue());
    }

    @Test
    void testCallEquivalentSplitTextBehavior() {
        ConcreteSplitter splitter = new ConcreteSplitter();

        List<String> chunks = splitter.splitText("This is a test text for splitting");

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> chunk instanceof String));
    }

    @Test
    void testGetNodesFromDocuments() {
        ConcreteSplitter splitter = new ConcreteSplitter();
        List<Document> documents = List.of(
                new Document("doc_1", "This is document 1"),
                new Document("doc_2", "This is document 2"));

        List<TextChunk> nodes = splitter.getNodesFromDocuments(documents);

        assertTrue(nodes.size() > 0);
        assertTrue(nodes.stream().allMatch(node -> node instanceof TextChunk));
        assertTrue(nodes.stream().allMatch(node -> List.of("doc_1", "doc_2").contains(node.getDocId())));
    }

    @Test
    void testGetNodesFromDocumentsEmptyDoc() {
        ConcreteSplitter splitter = new ConcreteSplitter();
        List<Document> documents = List.of(
                new Document("doc_1", ""),
                new Document("doc_2", "This is document 2"));

        List<TextChunk> nodes = splitter.getNodesFromDocuments(documents);

        assertTrue(nodes.size() > 0);
        assertTrue(nodes.stream().allMatch(node -> "doc_2".equals(node.getDocId())));
    }

    @Test
    void testGetNodesFromDocumentsNoneDoc() {
        ConcreteSplitter splitter = new ConcreteSplitter();
        List<Document> documents = new ArrayList<>();
        documents.add(null);
        documents.add(new Document("doc_2", "This is document 2"));

        List<TextChunk> nodes = splitter.getNodesFromDocuments(documents);

        assertTrue(nodes.size() > 0);
        assertTrue(nodes.stream().allMatch(node -> "doc_2".equals(node.getDocId())));
    }

    @Test
    void testSplitTextReturnsOnlyTextList() {
        ConcreteSplitter splitter = new ConcreteSplitter();

        List<String> chunks = splitter.splitText("This is a test text");

        assertTrue(chunks.size() > 0);
        assertTrue(chunks.stream().allMatch(chunk -> chunk instanceof String));
    }

    @Test
    void testCannotInstantiateAbstractClassDirectly() {
        assertTrue(Modifier.isAbstract(Splitter.class.getModifiers()));
    }

    private static final class ConcreteSplitter extends Splitter {
        private ConcreteSplitter() {
            super(512, 50);
        }

        private ConcreteSplitter(int chunkSize, int chunkOverlap) {
            super(chunkSize, chunkOverlap);
        }

        private ConcreteSplitter(int chunkSize, int chunkOverlap, Tokenizer tokenizer) {
            super(chunkSize, chunkOverlap, tokenizer);
        }

        @Override
        public List<String> splitText(String text) {
            if (text == null) {
                return List.of();
            }
            List<String> chunks = new ArrayList<>();
            for (int i = 0; i < text.length(); i += 10) {
                chunks.add(text.substring(i, Math.min(i + 10, text.length())));
            }
            return chunks;
        }

        private int chunkSizeValue() {
            return chunkSize;
        }

        private int chunkOverlapValue() {
            return chunkOverlap;
        }

        private Object tokenizerEncValue() {
            return tokenizerEnc;
        }

        private Object tokenizerDecValue() {
            return tokenizerDec;
        }
    }

    private static final class SimpleTokenizer implements Tokenizer {
        @Override
        public List<Integer> encode(String text) {
            String[] parts = text == null || text.isBlank() ? new String[0] : text.split("\\s+");
            List<Integer> tokens = new ArrayList<>();
            for (int i = 0; i < parts.length; i++) {
                tokens.add(i);
            }
            return tokens;
        }

        @Override
        public String decode(List<Integer> tokens) {
            return tokens == null ? "" : tokens.toString();
        }
    }

    private static final class EncodeOnlyTokenizer implements Tokenizer {
        @Override
        public List<Integer> encode(String text) {
            return List.of(1, 2, 3);
        }

        @Override
        public String decode(List<Integer> tokens) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean canDecode() {
            return false;
        }
    }
}
