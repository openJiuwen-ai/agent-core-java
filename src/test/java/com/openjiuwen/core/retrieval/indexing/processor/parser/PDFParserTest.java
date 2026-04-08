/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.processor.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
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
        try (PDDocument pdfDocument = new PDDocument()) {
            PDPage pdfPage = new PDPage();
            pdfDocument.addPage(pdfPage);
            try (PDPageContentStream pdfStream = new PDPageContentStream(pdfDocument, pdfPage)) {
                pdfStream.beginText();
                pdfStream.setFont(PDType1Font.HELVETICA, 12);
                pdfStream.newLineAtOffset(100, 700);
                pdfStream.showText("Page 1 content");
                pdfStream.endText();
            }
            pdfDocument.save(file.toFile());
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
