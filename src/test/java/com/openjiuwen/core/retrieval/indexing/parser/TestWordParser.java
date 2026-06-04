/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.TestModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.indexing.processor.parser.ImageCaptioner;
import com.openjiuwen.core.retrieval.indexing.processor.parser.WordParser;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_word_parser.py} in
 * {@code tests.unit_tests.core.retrieval.indexing.processor.parser}.
 */
class TestWordParser {

    @TempDir
    Path tempDir;

    @Test
    void testInit() {
        WordParser parser = new WordParser();

        assertNotNull(parser);
    }

    @Test
    void testParseDocxSuccess() throws IOException {
        Path file = tempDir.resolve("sample.docx");
        try (XWPFDocument document = new XWPFDocument(); OutputStream output = Files.newOutputStream(file)) {
            XWPFParagraph p1 = document.createParagraph();
            p1.createRun().setText("Paragraph 1");
            XWPFParagraph p2 = document.createParagraph();
            p2.createRun().setText("Paragraph 2");
            document.write(output);
        }

        WordParser parser = new WordParser();
        List<Document> documents = parser.parse(file.toString(), "doc_1", null, Map.of());

        assertEquals(1, documents.size());
        assertEquals("doc_1", documents.getFirst().getId());
        assertTrue(documents.getFirst().getText().contains("Paragraph 1"));
        assertTrue(documents.getFirst().getText().contains("Paragraph 2"));
    }

    @Test
    void testParseDocxE2e() throws IOException {
        Path file = tempDir.resolve("e2e.docx");
        try (XWPFDocument document = new XWPFDocument(); OutputStream output = Files.newOutputStream(file)) {
            XWPFParagraph heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("Hello");
            document.createParagraph().createRun().setText("First paragraph.");
            document.createParagraph().createRun().setText("Second paragraph with more text.");
            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("A1");
            table.getRow(0).getCell(1).setText("B1");
            table.getRow(1).getCell(0).setText("A2");
            table.getRow(1).getCell(1).setText("B2");
            document.createParagraph().createRun().setText("Paragraph after table.");
            document.write(output);
        }

        WordParser parser = new WordParser();
        List<Document> documents = parser.parse(file.toString(), "e2e_doc", null, Map.of());

        assertEquals(1, documents.size());
        assertEquals("e2e_doc", documents.getFirst().getId());
        String text = documents.getFirst().getText();
        assertTrue(text.contains("## Hello"));
        assertTrue(text.contains("First paragraph."));
        assertTrue(text.contains("Second paragraph with more text."));
        assertTrue(text.contains("A1"));
        assertTrue(text.contains("B1"));
        assertTrue(text.contains("A2"));
        assertTrue(text.contains("B2"));
        assertTrue(text.contains("Paragraph after table."));
    }

    @Test
    void testParseDocxHeadings() throws IOException {
        Path file = tempDir.resolve("headings.docx");
        try (XWPFDocument document = new XWPFDocument(); OutputStream output = Files.newOutputStream(file)) {
            XWPFParagraph title = document.createParagraph();
            title.setStyle("Title");
            title.createRun().setText("Document Title Here");
            XWPFParagraph heading1 = document.createParagraph();
            heading1.setStyle("Heading1");
            heading1.createRun().setText("Section");
            XWPFParagraph heading2 = document.createParagraph();
            heading2.setStyle("Heading2");
            heading2.createRun().setText("Subsection");
            document.createParagraph().createRun().setText("Normal paragraph.");
            document.write(output);
        }

        WordParser parser = new WordParser();
        List<Document> documents = parser.parse(file.toString(), "headings_doc", null, Map.of());

        assertEquals(1, documents.size());
        String text = documents.getFirst().getText();
        assertTrue(text.contains("# Document Title Here"));
        assertTrue(text.contains("## Section"));
        assertTrue(text.contains("### Subsection"));
        assertTrue(text.contains("Normal paragraph."));
    }

    @Test
    void testParseDocxE2eWithImage() throws IOException {
        Path file = tempDir.resolve("with_image.docx");
        byte[] png = createPngBytes(Color.RED);
        try (XWPFDocument document = new XWPFDocument(); OutputStream output = Files.newOutputStream(file)) {
            document.createParagraph().createRun().setText("Before image.");
            addPictureParagraph(document, png, "test_image.png");
            document.createParagraph().createRun().setText("Between image.");
            addPictureParagraph(document, createPngBytes(Color.BLUE), "test_image2.png");
            document.createParagraph().createRun().setText("After image.");
            document.write(output);
        }

        String fakeCaption = "A red square test image.";
        TestableWordParser parser = new TestableWordParser(new StubCaptioner(List.of(fakeCaption)));
        List<Document> documents = parser.parse(file.toString(), "e2e_image_doc", new TestModelClient("gpt-4o", "unused"), Map.of());

        assertEquals(1, documents.size());
        assertEquals("e2e_image_doc", documents.getFirst().getId());
        String text = documents.getFirst().getText();
        assertTrue(text.contains("Before image.\n" + fakeCaption + "\nBetween image.\n" + fakeCaption + "\nAfter image."));
        assertEquals(2, parser.captioner.callCount);
    }

    @Test
    void testParseDocxEmptyDocument() throws IOException {
        Path file = tempDir.resolve("empty.docx");
        try (XWPFDocument document = new XWPFDocument(); OutputStream output = Files.newOutputStream(file)) {
            document.write(output);
        }

        WordParser parser = new WordParser();
        List<Document> documents = parser.parse(file.toString(), "doc_1", null, Map.of());

        assertTrue(documents.isEmpty());
    }

    @Test
    void testParseDocxFileNotFound() {
        WordParser parser = new WordParser();

        List<Document> documents = parser.parse(tempDir.resolve("nonexistent.docx").toString(), "doc_1", null, Map.of());

        assertTrue(documents.isEmpty());
    }

    @Test
    void testParseDocxWithException() throws IOException {
        Path file = tempDir.resolve("bad.docx");
        Files.writeString(file, "not a docx");

        WordParser parser = new WordParser();
        List<Document> documents = parser.parse(file.toString(), "doc_1", null, Map.of());

        assertTrue(documents.isEmpty());
    }

    private static byte[] createPngBytes(Color color) throws IOException {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static void addPictureParagraph(XWPFDocument document, byte[] png, String fileName) throws IOException {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        try (ByteArrayInputStream input = new ByteArrayInputStream(png)) {
            run.addPicture(input, org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG, fileName, Units.toEMU(10), Units.toEMU(10));
        } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException ex) {
            throw new IOException(ex);
        }
    }

    private static final class TestableWordParser extends WordParser {
        private final StubCaptioner captioner;

        private TestableWordParser(StubCaptioner captioner) {
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
