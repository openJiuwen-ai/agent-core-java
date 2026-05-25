/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.indexing.processor.parser.Parser;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parser abstract base class test cases.
 * Mirrors Python's tests/unit_tests/core/retrieval/indexing/processor/parser/test_base.py
 */
class TestParser {

    /**
     * Concrete parser implementation for testing.
     */
    static class ConcreteParser extends Parser {

        @Override
        protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options) {
            return doc;
        }

        @Override
        public boolean supports(String doc) {
            return true;
        }
    }

    @Test
    void testParserClass() {
        // Test that Parser class exists
        assertNotNull(Parser.class);
    }

    @Test
    void testConcreteParser() {
        // Test concrete implementation
        ConcreteParser parser = new ConcreteParser();
        String input = "Test document content";
        List<Document> result = parser.parse(input, "test_doc", null, null);
        assertNotNull(result);
    }
}