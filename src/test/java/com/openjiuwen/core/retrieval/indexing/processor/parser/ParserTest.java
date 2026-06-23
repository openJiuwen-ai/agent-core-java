/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code Parser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/base.py}.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.core.retrieval.indexing.processor.parser.test_base}
 * in {@code tests/unit_tests/core/retrieval/indexing/processor/parser/test_base.py}.</p>
 */
class ParserTest {

    @Test
    void parseSuccess() {
        Parser parser = new ConcreteParser();

        List<Document> documents = parser.parse("test.txt", "doc_1").join();

        assertEquals(1, documents.size());
        assertEquals("doc_1", documents.getFirst().getId_());
        assertTrue(documents.getFirst().getText().contains("Content from test.txt"));
    }

    @Test
    void parseEmptyContent() {
        Parser parser = new EmptyParser();

        List<Document> documents = parser.parse("test.txt").join();

        assertTrue(documents.isEmpty());
    }

    @Test
    void parseWithKwargs() {
        Parser parser = new ConcreteParser();

        List<Document> documents = parser.parse("test.txt", "doc_1", null, Map.of("file_name", "test")).join();

        assertEquals(1, documents.size());
        assertEquals("doc_1", documents.getFirst().getId_());
    }

    @Test
    void lazyParse() {
        Parser parser = new ConcreteParser();

        List<Document> documents = parser.lazyParse("test.txt", "doc_1", Map.of()).join();

        assertEquals(1, documents.size());
        assertEquals("doc_1", documents.getFirst().getId_());
    }

    @Test
    void processDelegatesToParse() {
        Parser parser = new ConcreteParser();

        List<Document> documents = parser.process("test.txt", "doc_1").join();

        assertEquals(1, documents.size());
        assertEquals("doc_1", documents.getFirst().getId_());
    }

    @Test
    void supportsUsesConcreteParserPredicate() {
        Parser parser = new ConcreteParser();

        assertTrue(parser.supports("file.test"));
        assertFalse(parser.supports("file.txt"));
    }

    /**
     * Mirrors Python's {@code ConcreteParser} in
     * {@code tests/unit_tests/core/retrieval/indexing/processor/parser/test_base.py}.
     */
    private static final class ConcreteParser extends Parser {
        @Override
        protected CompletableFuture<String> parseContent(
                String filePath,
                BaseModelClient llmClient,
                Map<String, Object> options
        ) {
            return CompletableFuture.completedFuture("Content from " + filePath);
        }

        @Override
        public boolean supports(String doc) {
            return doc.endsWith(".test");
        }
    }

    /**
     * Mirrors Python's inline {@code EmptyParser} in
     * {@code tests/unit_tests/core/retrieval/indexing/processor/parser/test_base.py}.
     */
    private static final class EmptyParser extends Parser {
        @Override
        protected CompletableFuture<String> parseContent(
                String filePath,
                BaseModelClient llmClient,
                Map<String, Object> options
        ) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
