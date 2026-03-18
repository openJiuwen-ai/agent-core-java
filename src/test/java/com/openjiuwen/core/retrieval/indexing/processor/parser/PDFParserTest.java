/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.processor.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PDFParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsePdfExtractsText() throws IOException {
        Path file = tempDir.resolve("sample.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(100, 700);
                content.showText("Page 1 content");
                content.endText();
            }
            document.save(file.toFile());
        }

        PDFParser parser = new PDFParser();
        var docs = parser.parse(file.toString(), "pdf-1", null, Map.of());

        assertEquals(1, docs.size());
        assertTrue(docs.getFirst().getText().contains("Page 1 content"));
    }

    @Test
    void parseMissingPdfReturnsEmpty() {
        PDFParser parser = new PDFParser();

        assertTrue(parser.parse(tempDir.resolve("missing.pdf").toString(), "pdf-1", null, Map.of()).isEmpty());
    }
}
