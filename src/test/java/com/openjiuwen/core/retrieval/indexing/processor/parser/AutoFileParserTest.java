/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.Document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class AutoFileParserTest {
    @TempDir
    Path tempDir;

    @Test
    void parseJsonFileAddsFileExtensionMetadata() throws IOException {
        Path file = tempDir.resolve("sample.json");
        Files.writeString(file, "{\"k\":\"v\"}", StandardCharsets.UTF_8);

        AutoFileParser parser = new AutoFileParser();
        List<Document> docs = parser.parse(file.toString(), "doc-1", null, Map.of());

        assertEquals(1, docs.size());
        assertEquals(".json", docs.get(0).getMetadata().get("file_ext"));
    }

    @Test
    void supportsRegisteredFormatsOnlyWhenFileExists() throws IOException {
        Path csv = tempDir.resolve("table.csv");
        Files.writeString(csv, "a,b\n1,2\n", StandardCharsets.UTF_8);
        Path html = tempDir.resolve("page.html");
        Files.writeString(html, "<html><body><article>" + "w".repeat(120) + "</article></body></html>",
                StandardCharsets.UTF_8);

        AutoFileParser parser = new AutoFileParser();

        assertTrue(parser.supports(csv.toString()));
        assertTrue(parser.supports(html.toString()));
        assertTrue(AutoFileParser.getSupportedFormats().contains(".csv"));
        assertTrue(AutoFileParser.getSupportedFormats().contains(".html"));
        assertTrue(AutoFileParser.getSupportedFormats().contains(".htm"));
    }

    @Test
    void parseMissingOrUnsupportedFileThrows() throws IOException {
        AutoFileParser parser = new AutoFileParser();
        Path unsupported = tempDir.resolve("sample.xyz");
        Files.writeString(unsupported, "data", StandardCharsets.UTF_8);

        assertThrows(BaseError.class,
                () -> parser.parse(tempDir.resolve("missing.txt").toString(), "doc-1", null, Map.of()));
        assertThrows(BaseError.class, () -> parser.parse(unsupported.toString(), "doc-1", null, Map.of()));
    }
}
