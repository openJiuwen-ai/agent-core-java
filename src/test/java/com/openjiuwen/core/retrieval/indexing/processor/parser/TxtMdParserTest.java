/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class TxtMdParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parseStripsWhitespace() throws IOException {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "   \n  Content  \n   ", StandardCharsets.UTF_8);

        TxtMdParser parser = new TxtMdParser();
        List<Document> docs = parser.parse(file.toString(), "doc-1", null, Map.of());

        assertEquals(1, docs.size());
        assertEquals("Content", docs.getFirst().getText());
    }

    @Test
    void parseMissingFileReturnsEmpty() {
        TxtMdParser parser = new TxtMdParser();

        assertTrue(parser.parse(tempDir.resolve("missing.txt").toString(), "doc-1", null, Map.of()).isEmpty());
    }
}
