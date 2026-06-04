/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.TestModelClient;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
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
 * Mirrors Python's {@code test_pdf_parser.py} in
 * {@code tests.unit_tests.core.retrieval.indexing.processor.parser}.
 */
class PDFParserTest {

    @TempDir
    Path tempDir;

    @Test
    void testInit() {
        PDFParser parser = new PDFParser();

        assertNotNull(parser);
    }

    @Test
    void testParsePdfSuccess() throws IOException {
        Path file = tempDir.resolve("sample.pdf");
        createPdf(file, List.of("Page 1 content", "Page 2 content"));

        PDFParser parser = new PDFParser();
        List<com.openjiuwen.core.retrieval.common.Document> documents = parser.parse(file.toString(), "doc_1", null, Map.of());

        assertEquals(1, documents.size());
        assertEquals("doc_1", documents.getFirst().getId());
        assertTrue(documents.getFirst().getText().contains("Page 1 content"));
        assertTrue(documents.getFirst().getText().contains("Page 2 content"));
    }

    @Test
    void testParsePdfEmptyPages() throws IOException {
        Path file = tempDir.resolve("empty.pdf");
        createPdf(file, List.of());

        PDFParser parser = new PDFParser();
        List<com.openjiuwen.core.retrieval.common.Document> documents = parser.parse(file.toString(), "doc_1", null, Map.of());

        assertTrue(documents.isEmpty());
    }

    @Test
    void testParsePdfWithNoneText() throws IOException {
        Path file = tempDir.resolve("none_text.pdf");
        createPdf(file, List.of(""));

        PDFParser parser = new PDFParser();
        List<com.openjiuwen.core.retrieval.common.Document> documents = parser.parse(file.toString(), "doc_1", null, Map.of());

        assertTrue(documents.isEmpty());
    }

    @Test
    void testParsePdfFileNotFound() {
        PDFParser parser = new PDFParser();

        List<com.openjiuwen.core.retrieval.common.Document> documents = parser.parse("nonexistent.pdf", "doc_1", null, Map.of());

        assertTrue(documents.isEmpty());
    }

    @Test
    void testParsePdfWithException() throws IOException {
        Path file = tempDir.resolve("bad.pdf");
        Files.writeString(file, "not a pdf");

        PDFParser parser = new PDFParser();
        List<com.openjiuwen.core.retrieval.common.Document> documents = parser.parse(file.toString(), "doc_1", null, Map.of());

        assertTrue(documents.isEmpty());
    }

    @Test
    void testParsePdfMultiplePages() throws IOException {
        Path file = tempDir.resolve("multi.pdf");
        List<String> pages = List.of("Page 1 content", "Page 2 content", "Page 3 content", "Page 4 content", "Page 5 content");
        createPdf(file, pages);

        PDFParser parser = new PDFParser();
        List<com.openjiuwen.core.retrieval.common.Document> documents = parser.parse(file.toString(), "doc_1", null, Map.of());

        assertEquals(1, documents.size());
        for (String pageText : pages) {
            assertTrue(documents.getFirst().getText().contains(pageText));
        }
    }

    @Test
    void testParsePdfWithImageCaptions() throws IOException {
        Path file = tempDir.resolve("captions.pdf");
        createPdfWithImage(file);

        PDFParser parser = new TestablePDFParser(new StubCaptioner(List.of("An example caption", "Second caption"), false));
        List<com.openjiuwen.core.retrieval.common.Document> documents = parser.parse(file.toString(), "doc_capt", new TestModelClient("gpt-4o", "unused"), Map.of());

        assertEquals(1, documents.size());
        assertTrue(documents.getFirst().getText().contains("An example caption"));
        assertTrue(documents.getFirst().getText().contains("Second caption"));
    }

    private static void createPdf(Path file, List<String> pageTexts) throws IOException {
        try (PDDocument pdfDocument = new PDDocument()) {
            if (pageTexts.isEmpty()) {
                pdfDocument.addPage(new PDPage());
            } else {
                for (String pageText : pageTexts) {
                    PDPage pdfPage = new PDPage();
                    pdfDocument.addPage(pdfPage);
                    if (pageText != null && !pageText.isBlank()) {
                        try (PDPageContentStream pdfStream = new PDPageContentStream(pdfDocument, pdfPage)) {
                            pdfStream.beginText();
                            pdfStream.setFont(PDType1Font.HELVETICA, 12);
                            pdfStream.newLineAtOffset(100, 700);
                            pdfStream.showText(pageText);
                            pdfStream.endText();
                        }
                    }
                }
            }
            pdfDocument.save(file.toFile());
        }
    }

    private static void createPdfWithImage(Path file) throws IOException {
        try (PDDocument pdfDocument = new PDDocument()) {
            PDPage page = new PDPage();
            pdfDocument.addPage(page);
            BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < 10; x++) {
                for (int y = 0; y < 10; y++) {
                    image.setRGB(x, y, Color.RED.getRGB());
                }
            }
            var pdImage = LosslessFactory.createFromImage(pdfDocument, image);
            try (PDPageContentStream stream = new PDPageContentStream(pdfDocument, page)) {
                stream.drawImage(pdImage, 100, 600, 50, 50);
            }
            pdfDocument.save(file.toFile());
        }
    }

    private static final class TestablePDFParser extends PDFParser {
        private final ImageCaptioner captioner;

        private TestablePDFParser(ImageCaptioner captioner) {
            this.captioner = captioner;
        }

        @Override
        protected ImageCaptioner createImageCaptioner(BaseModelClient llmClient) {
            return captioner;
        }
    }

    private static final class StubCaptioner extends ImageCaptioner {
        private final List<String> captions;
        private final boolean throwOnCaption;

        private StubCaptioner(List<String> captions, boolean throwOnCaption) {
            super(null);
            this.captions = captions;
            this.throwOnCaption = throwOnCaption;
        }

        @Override
        public List<String> captionImages(List<String> imageLocs) {
            if (throwOnCaption) {
                throw new IllegalStateException("PDF parsing error");
            }
            return captions;
        }
    }
}
