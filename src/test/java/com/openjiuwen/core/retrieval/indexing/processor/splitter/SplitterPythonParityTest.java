/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.splitter;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestSplitter} tests in
 * {@code tests/unit_tests/core/retrieval/indexing/processor/splitter/test_base.py}.
 */
class SplitterPythonParityTest {

    @Test
    void initWithDefaults() {
        ConcreteSplitter splitter = new ConcreteSplitter();

        assertThat(splitter.chunkSize).isEqualTo(512);
        assertThat(splitter.chunkOverlap).isEqualTo(50);
        assertThat(splitter.tokenizer).isNull();
        assertThat(splitter.tokenizerEnc).isNull();
        assertThat(splitter.tokenizerDec).isNull();
    }

    @Test
    void initWithCustomValues() {
        ConcreteSplitter splitter = new ConcreteSplitter(null, 1024, 100);

        assertThat(splitter.chunkSize).isEqualTo(1024);
        assertThat(splitter.chunkOverlap).isEqualTo(100);
    }

    @Test
    void initWithTokenizerAdapter() {
        Splitter.TokenizerAdapter tokenizer = new Splitter.TokenizerAdapter() {
            @Override
            public Object encode(String text) {
                return List.of(text.split("\\s+"));
            }

            @Override
            public String decode(Object tokens) {
                return String.join(" ", ((List<?>) tokens).stream().map(String::valueOf).toList());
            }
        };

        ConcreteSplitter splitter = new ConcreteSplitter(tokenizer, 512, 50);

        assertThat(splitter.tokenizer).isSameAs(tokenizer);
        assertThat(splitter.tokenizerEnc).isNotNull();
        assertThat(splitter.tokenizerDec).isNotNull();
    }

    @Test
    void initWithCallableTokenizer() {
        Function<String, Object> tokenizer = text -> List.of(text.split("\\s+"));

        ConcreteSplitter splitter = new ConcreteSplitter(tokenizer, 512, 50);

        assertThat(splitter.tokenizer).isSameAs(tokenizer);
        assertThat(splitter.tokenizerEnc).isNotNull();
        assertThat(splitter.tokenizerDec).isNull();
    }

    @Test
    void callReturnsTextStartEndTuples() {
        ConcreteSplitter splitter = new ConcreteSplitter();

        List<Splitter.SplitChunk> chunks = splitter.split("This is a test text for splitting");

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.text()).isInstanceOf(String.class);
            assertThat(chunk.startIdx()).isInstanceOf(Integer.class);
            assertThat(chunk.endIdx()).isInstanceOf(Integer.class);
        });
    }

    @Test
    void getNodesFromDocuments() {
        ConcreteSplitter splitter = new ConcreteSplitter();
        List<Document> documents = List.of(
                new Document("doc_1", "This is document 1"),
                new Document("doc_2", "This is document 2")
        );

        List<TextChunk> nodes = splitter.getNodesFromDocuments(documents);

        assertThat(nodes).isNotEmpty();
        assertThat(nodes).allMatch(node -> List.of("doc_1", "doc_2").contains(node.getDocId()));
    }

    @Test
    void getNodesFromDocumentsSkipsEmptyDocument() {
        ConcreteSplitter splitter = new ConcreteSplitter();
        List<Document> documents = List.of(
                new Document("doc_1", ""),
                new Document("doc_2", "This is document 2")
        );

        List<TextChunk> nodes = splitter.getNodesFromDocuments(documents);

        assertThat(nodes).isNotEmpty();
        assertThat(nodes).allMatch(node -> "doc_2".equals(node.getDocId()));
    }

    @Test
    void getNodesFromDocumentsSkipsNullDocument() {
        ConcreteSplitter splitter = new ConcreteSplitter();
        List<Document> documents = java.util.Arrays.asList(null, new Document("doc_2", "This is document 2"));

        List<TextChunk> nodes = splitter.getNodesFromDocuments(documents);

        assertThat(nodes).isNotEmpty();
        assertThat(nodes).allMatch(node -> "doc_2".equals(node.getDocId()));
    }

    @Test
    void splitTextReturnsOnlyTexts() {
        ConcreteSplitter splitter = new ConcreteSplitter();

        List<String> chunks = splitter.splitText("This is a test text");

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allMatch(String.class::isInstance);
    }

    @Test
    void cannotInstantiateAbstractClass() {
        assertThat(Modifier.isAbstract(Splitter.class.getModifiers())).isTrue();
    }

    private static final class ConcreteSplitter extends Splitter {

        private ConcreteSplitter() {
            super();
        }

        private ConcreteSplitter(Object tokenizer, int chunkSize, int chunkOverlap) {
            super(tokenizer, chunkSize, chunkOverlap);
        }

        @Override
        public List<SplitChunk> split(String doc) {
            List<SplitChunk> chunks = new java.util.ArrayList<>();
            for (int i = 0; i < doc.length(); i += 10) {
                int end = Math.min(i + 10, doc.length());
                chunks.add(new SplitChunk(doc.substring(i, end), i, end));
            }
            return chunks;
        }
    }
}
