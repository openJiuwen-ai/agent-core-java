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
 */
class ParserTest {

    @Test
    void defaultParseReturnsNoDocumentsWhenParseContentIsEmpty() {
        Parser parser = new Parser() {
        };

        assertTrue(parser.parse("missing.txt", "doc-1").join().isEmpty());
    }

    @Test
    void parseWrapsContentInDocumentWithProvidedId() {
        Parser parser = new ContentParser("parsed text");

        List<Document> documents = parser.parse("file.txt", "doc-1").join();

        assertEquals(1, documents.size());
        assertEquals("doc-1", documents.getFirst().getId_());
        assertEquals("parsed text", documents.getFirst().getText());
        assertTrue(documents.getFirst().getMetadata().isEmpty());
    }

    @Test
    void lazyParseAndProcessDelegateToParse() {
        Parser parser = new ContentParser("body");

        assertEquals("body", parser.lazyParse("file.txt", "doc-2", Map.of()).join().getFirst().getText());
        assertEquals("body", parser.process("file.txt", "doc-3").join().getFirst().getText());
    }

    @Test
    void supportsDefaultsToFalse() {
        Parser parser = new ContentParser("body");

        assertFalse(parser.supports("file.txt"));
    }

    private static final class ContentParser extends Parser {
        private final String content;

        private ContentParser(String content) {
            this.content = content;
        }

        @Override
        protected CompletableFuture<String> parseContent(
                String filePath,
                BaseModelClient llmClient,
                Map<String, Object> options
        ) {
            return CompletableFuture.completedFuture(content);
        }
    }
}
