/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.retrieval.common.Document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class JsonParserTest {
    @TempDir
    Path tempDir;

    @Test
    void parseFormatsValidJson() throws IOException {
        Path file = tempDir.resolve("sample.json");
        Files.writeString(file, "{\"name\":\"test\",\"value\":123}", StandardCharsets.UTF_8);

        JsonParser parser = new JsonParser();
        List<Document> docs = parser.parse(file.toString(), "doc-1", null, Map.of());

        assertEquals(1, docs.size());
        assertTrue(docs.get(0).getText().contains("\"name\""));
        assertTrue(docs.get(0).getText().contains("\n"));
    }

    @Test
    void parseReturnsRawTextForInvalidJson() throws IOException {
        Path file = tempDir.resolve("bad.json");
        Files.writeString(file, "{ invalid json }", StandardCharsets.UTF_8);

        JsonParser parser = new JsonParser();
        List<Document> docs = parser.parse(file.toString(), "doc-1", null, Map.of());

        assertEquals(1, docs.size());
        assertEquals("{ invalid json }", docs.get(0).getText());
    }

    @Test
    void parseMissingFileReturnsEmpty() {
        JsonParser parser = new JsonParser();

        assertTrue(parser.parse(tempDir.resolve("missing.json").toString(), "doc-1", null, Map.of()).isEmpty());
    }
}
