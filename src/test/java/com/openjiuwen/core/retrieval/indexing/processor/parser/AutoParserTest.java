/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.retrieval.common.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoParserTest {

    @TempDir
    Path tempDir;

    @Test
    void supportsUrlsAndExistingFiles() throws IOException {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "content", StandardCharsets.UTF_8);

        AutoParser parser = new AutoParser();

        assertTrue(parser.supports("https://example.com/page"));
        assertTrue(parser.supports(file.toString()));
        assertFalse(parser.supports("not-a-url"));
    }

    @Test
    void parseDelegatesToConfiguredParser() {
        RecordingParser linkParser = new RecordingParser(true, List.of(new Document("link-doc", "from link", Map.of())));
        RecordingParser fileParser = new RecordingParser(false, List.of(new Document("file-doc", "from file", Map.of())));
        AutoParser parser = new AutoParser(linkParser, fileParser);

        List<Document> docs = parser.parse("https://example.com/page", "id-1", null, Map.of());

        assertEquals(1, docs.size());
        assertEquals("https://example.com/page", linkParser.lastDoc);
        assertEquals(0, fileParser.parseCount);
    }

    private static final class RecordingParser extends Parser {

        private final boolean supported;
        private final List<Document> result;
        private String lastDoc;
        private int parseCount;

        private RecordingParser(boolean supported, List<Document> result) {
            this.supported = supported;
            this.result = result;
        }

        @Override
        public boolean supports(String doc) {
            return supported;
        }

        @Override
        public List<Document> parse(String doc, String docId, com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient llmClient, Map<String, Object> options) {
            this.lastDoc = doc;
            this.parseCount++;
            return result;
        }

        @Override
        protected String parseContent(String doc, com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient llmClient, Map<String, Object> options) {
            return null;
        }
    }
}
