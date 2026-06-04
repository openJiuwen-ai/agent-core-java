/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.indexing.processor.parser.Parser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parser abstract base class test cases.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/core/retrieval/indexing/processor/parser/test_base.py}
 * with Java API adaptations.</p>
 */
class TestParser {

    @Test
    void testParseSuccess() {
        ConcreteParser parser = new ConcreteParser();

        List<Document> documents = parser.parse("test.txt", "doc_1", null, Map.of());

        assertEquals(1, documents.size());
        assertEquals("doc_1", documents.getFirst().getId());
        assertTrue(documents.getFirst().getText().contains("Content from test.txt"));
    }

    @Test
    void testParseEmptyContent() {
        EmptyParser parser = new EmptyParser();

        List<Document> documents = parser.parse("test.txt", "doc_1", null, Map.of());

        assertTrue(documents.isEmpty());
    }

    @Test
    void testParseWithOptions() {
        ConcreteParser parser = new ConcreteParser();

        List<Document> documents = parser.parse("test.txt", "doc_1", null, Map.of("file_name", "test"));

        assertEquals(1, documents.size());
        assertEquals("doc_1", documents.getFirst().getId());
    }

    @Test
    void testProcessDelegatesToParse() {
        ConcreteParser parser = new ConcreteParser();

        List<Document> documents = parser.process("test.txt", Map.of("doc_id", "doc_1"));

        assertEquals(1, documents.size());
        assertTrue(documents.getFirst().getText().contains("Content from test.txt"));
    }

    @Test
    void testSupports() {
        ConcreteParser parser = new ConcreteParser();

        assertTrue(parser.supports("file.test"));
        assertFalse(parser.supports("file.txt"));
    }

    @Test
    void testParserIsAbstractBaseClass() {
        assertTrue(Modifier.isAbstract(Parser.class.getModifiers()));
    }

    private static final class ConcreteParser extends Parser {
        @Override
        protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options) {
            return "Content from " + doc;
        }

        @Override
        public boolean supports(String doc) {
            return doc != null && doc.endsWith(".test");
        }
    }

    private static final class EmptyParser extends Parser {
        @Override
        protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options) {
            return null;
        }

        @Override
        public boolean supports(String doc) {
            return true;
        }
    }
}
