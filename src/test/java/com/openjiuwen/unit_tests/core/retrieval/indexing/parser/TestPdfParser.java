/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.TestModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.indexing.processor.parser.ImageCaptioner;
import com.openjiuwen.core.retrieval.indexing.processor.parser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PDF parser test cases.
 *
 * <p>Mirrors Python's {@code test_pdf_parser.py} for the Java PDF parser.</p>
 */
class TestPdfParser {

    @TempDir
    Path tempDir;

    @Test
    void testInit() {
        assertNotNull(new PDFParser());
    }

    @Test
    void testParsePdfSuccess() throws Exception {
        Path pdf = tempDir.resolve("simple.pdf");
        createPdfWithText(pdf, List.of("Page 1 content", "Page 2 content"));

        PDFParser parser = new PDFParser();
        List<Document> documents = parser.parse(pdf.toString(), "doc_1", null, Map.of());

        assertEquals(1, documents.size());
        assertEquals("doc_1", documents.getFirst().getId());
        assertTrue(documents.getFirst().getText().contains("Page 1 content"));
        assertTrue(documents.getFirst().getText().contains("Page 2 content"));
    }

    @Test
    void testParsePdfEmptyPages() throws Exception {
        Path pdf = tempDir.resolve("empty.pdf");
        createPdfWithText(pdf, List.of(" "));

        PDFParser parser = new PDFParser();
        List<Document> documents = parser.parse(pdf.toString(), "doc_1", null, Map.of());

        assertTrue(documents.isEmpty());
    }

    @Test
    void testParsePdfFileNotFound() {
        PDFParser parser = new PDFParser();

        List<Document> documents = parser.parse(tempDir.resolve("missing.pdf").toString(), "doc_1", null, Map.of());

        assertTrue(documents.isEmpty());
    }

    @Test
    void testParsePdfWithException() throws Exception {
        Path file = tempDir.resolve("bad.pdf");
        Files.writeString(file, "not a real pdf");

        PDFParser parser = new PDFParser();
        List<Document> documents = parser.parse(file.toString(), "doc_1", null, Map.of());

        assertTrue(documents.isEmpty());
    }

    @Test
    void testParsePdfMultiplePages() throws Exception {
        Path pdf = tempDir.resolve("multi.pdf");
        createPdfWithText(pdf, List.of(
                "Page 1 content",
                "Page 2 content",
                "Page 3 content",
                "Page 4 content",
                "Page 5 content"));

        PDFParser parser = new PDFParser();
        List<Document> documents = parser.parse(pdf.toString(), "doc_1", null, Map.of());

        assertEquals(1, documents.size());
        for (int i = 1; i <= 5; i++) {
            assertTrue(documents.getFirst().getText().contains("Page " + i + " content"));
        }
    }

    @Test
    void testParsePdfWithImageCaptions() throws Exception {
        Path pdf = tempDir.resolve("with-image.pdf");
        createPdfWithImage(pdf);

        StubCaptioner captioner = new StubCaptioner(List.of("An example caption"));
        PDFParser parser = new TestablePdfParser(captioner);
        BaseModelClient llmClient = new TestModelClient("gpt-4o", "unused");

        List<Document> documents = parser.parse(pdf.toString(), "doc_capt", llmClient, Map.of());

        assertEquals(1, documents.size());
        assertTrue(documents.getFirst().getText().contains("An example caption"));
        assertEquals(1, captioner.callCount);
    }

    @Test
    void testSupportsPdfExtension() {
        PDFParser parser = new PDFParser();

        assertTrue(parser.supports("a.pdf"));
        assertTrue(parser.supports("a.PDF"));
    }

    private static void createPdfWithText(Path target, List<String> pageTexts) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (String pageText : pageTexts) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    if (pageText != null && !pageText.isBlank()) {
                        content.beginText();
                        content.setFont(PDType1Font.HELVETICA, 12);
                        content.newLineAtOffset(50, 700);
                        content.showText(pageText);
                        content.endText();
                    }
                }
            }
            document.save(target.toFile());
        }
    }

    private static void createPdfWithImage(Path target) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < 20; x++) {
                for (int y = 0; y < 20; y++) {
                    image.setRGB(x, y, Color.RED.getRGB());
                }
            }
            PDImageXObject pdfImage = LosslessFactory.createFromImage(document, image);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(pdfImage, 50, 600);
            }
            document.save(target.toFile());
        }
    }

    private static final class TestablePdfParser extends PDFParser {
        private final StubCaptioner captioner;

        private TestablePdfParser(StubCaptioner captioner) {
            this.captioner = captioner;
        }

        @Override
        protected ImageCaptioner createImageCaptioner(BaseModelClient llmClient) {
            return captioner;
        }
    }

    private static final class StubCaptioner extends ImageCaptioner {
        private final List<String> captions;
        private int callCount;

        private StubCaptioner(List<String> captions) {
            super(null);
            this.captions = captions;
        }

        @Override
        public List<String> captionImages(List<String> imageLocs) {
            callCount++;
            return captions;
        }
    }
}
