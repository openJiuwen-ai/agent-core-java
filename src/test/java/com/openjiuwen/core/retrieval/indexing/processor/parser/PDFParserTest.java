/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code PDFParser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/pdf_parser.py}.
 *
 * <p>Focused tests also mirror Python's {@code test_pdf_parser.py} in
 * {@code tests/unit_tests/core/retrieval/indexing/processor/parser/test_pdf_parser.py}.</p>
 */
class PDFParserTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        AutoFileParser.clearRegisteredParsersForTest();
    }

    @AfterEach
    void tearDown() {
        AutoFileParser.clearRegisteredParsersForTest();
    }

    @Test
    void testInit() {
        assertThat(new PDFParser()).isNotNull();
    }

    @Test
    void testParsePdfSuccess() throws IOException {
        Path file = tempDir.resolve("sample.pdf");
        createPdf(file, List.of("Page 1 content", "Page 2 content"));

        List<Document> documents = new PDFParser().parse(file.toString(), "doc_1").join();

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().getId_()).isEqualTo("doc_1");
        assertThat(documents.getFirst().getText()).contains("Page 1 content", "Page 2 content");
    }

    @Test
    void testParsePdfEmptyPages() throws IOException {
        Path file = tempDir.resolve("empty.pdf");
        createPdf(file, List.of());

        List<Document> documents = new PDFParser().parse(file.toString(), "doc_1").join();

        assertThat(documents).isEmpty();
    }

    @Test
    void testParsePdfWithNoneText() throws IOException {
        Path file = tempDir.resolve("none-text.pdf");
        createPdf(file, List.of(""));

        List<Document> documents = new PDFParser().parse(file.toString(), "doc_1").join();

        assertThat(documents).isEmpty();
    }

    @Test
    void testParsePdfFileNotFound() {
        List<Document> documents = new PDFParser().parse(tempDir.resolve("nonexistent.pdf").toString(), "doc_1")
                .join();

        assertThat(documents).isEmpty();
    }

    @Test
    void testParsePdfWithException() throws IOException {
        Path file = tempDir.resolve("bad.pdf");
        Files.writeString(file, "not a pdf");

        List<Document> documents = new PDFParser().parse(file.toString(), "doc_1").join();

        assertThat(documents).isEmpty();
    }

    @Test
    void testParsePdfMultiplePages() throws IOException {
        Path file = tempDir.resolve("multi.pdf");
        List<String> pages = List.of(
                "Page 1 content",
                "Page 2 content",
                "Page 3 content",
                "Page 4 content",
                "Page 5 content"
        );
        createPdf(file, pages);

        List<Document> documents = new PDFParser().parse(file.toString(), "doc_1").join();

        assertThat(documents).hasSize(1);
        for (String pageText : pages) {
            assertThat(documents.getFirst().getText()).contains(pageText);
        }
    }

    @Test
    void testParsePdfWithImageCaptions() throws IOException {
        Path file = tempDir.resolve("captions.pdf");
        createPdfWithImage(file);
        PDFParser parser = new TestablePDFParser(new StubCaptioner(List.of("An example caption", "Second caption")));

        List<Document> documents = parser.parse(file.toString(), "doc_capt", null, Map.of()).join();

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().getText()).contains("An example caption", "Second caption");
    }

    @Test
    void supportsPdfCaseInsensitively() {
        PDFParser parser = new PDFParser();

        assertThat(parser.supports("sample.pdf")).isTrue();
        assertThat(parser.supports("sample.PDF")).isTrue();
        assertThat(parser.supports("sample.txt")).isFalse();
        assertThat(parser.supports(null)).isFalse();
    }

    @Test
    void autoFileParserDispatchesPdfAndEnrichesMetadata() throws IOException {
        Path file = tempDir.resolve("auto.pdf");
        createPdf(file, List.of("Auto parser content"));

        List<Document> documents = new AutoFileParser().parse(file.toString(), "auto-pdf").join();

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().getText()).contains("Auto parser content");
        assertThat(documents.getFirst().getMetadata())
                .containsEntry("doc_id", "auto-pdf")
                .containsEntry("file_ext", ".pdf")
                .containsEntry("file_path", file.toString());
    }

    private static void createPdf(Path file, List<String> pageTexts) throws IOException {
        try (PDDocument document = new PDDocument()) {
            if (pageTexts.isEmpty()) {
                document.addPage(new PDPage());
            } else {
                for (String pageText : pageTexts) {
                    PDPage page = new PDPage();
                    document.addPage(page);
                    if (pageText != null && !pageText.isBlank()) {
                        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                            stream.beginText();
                            stream.setFont(PDType1Font.HELVETICA, 12);
                            stream.newLineAtOffset(100, 700);
                            stream.showText(pageText);
                            stream.endText();
                        }
                    }
                }
            }
            document.save(file.toFile());
        }
    }

    private static void createPdfWithImage(Path file) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < 10; x++) {
                for (int y = 0; y < 10; y++) {
                    image.setRGB(x, y, Color.RED.getRGB());
                }
            }
            var pdImage = LosslessFactory.createFromImage(document, image);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.drawImage(pdImage, 100, 600, 50, 50);
            }
            document.save(file.toFile());
        }
    }

    /**
     * Mirrors Python's patched {@code ImageCaptioner} collaborator for
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/pdf_parser.py}.
     */
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

    /**
     * Mirrors Python's patched {@code ImageCaptioner.caption_images} for
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/pdf_parser.py}.
     */
    private static final class StubCaptioner extends ImageCaptioner {
        private final List<String> captions;

        private StubCaptioner(List<String> captions) {
            super((BaseModelClient) null);
            this.captions = captions;
        }

        @Override
        public CompletableFuture<List<String>> captionImages(List<String> imageLocs) {
            return CompletableFuture.completedFuture(captions);
        }
    }
}
