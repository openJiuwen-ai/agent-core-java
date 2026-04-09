/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/
package com.openjiuwen.core.retrieval.indexing.processor.parser;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WordParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parseDocxExtractsParagraphText() throws IOException {
        Path file = tempDir.resolve("sample.docx");
        try (XWPFDocument document = new XWPFDocument(); OutputStream output = Files.newOutputStream(file)) {
            XWPFParagraph p1 = document.createParagraph();
            p1.createRun().setText("Paragraph 1");
            XWPFParagraph p2 = document.createParagraph();
            p2.createRun().setText("Paragraph 2");
            document.write(output);
        }

        WordParser parser = new WordParser();
        var docs = parser.parse(file.toString(), "docx-1", null, Map.of());

        assertEquals(1, docs.size());
        assertTrue(docs.getFirst().getText().contains("Paragraph 1"));
        assertTrue(docs.getFirst().getText().contains("Paragraph 2"));
    }

    @Test
    void parseMissingDocxReturnsEmpty() {
        WordParser parser = new WordParser();

        assertTrue(parser.parse(tempDir.resolve("missing.docx").toString(), "docx-1", null, Map.of()).isEmpty());
    }
}
