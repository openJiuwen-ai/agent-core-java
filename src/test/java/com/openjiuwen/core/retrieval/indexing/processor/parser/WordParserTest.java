/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.retrieval.common.Document;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WordParser}.
 *
 * <p>Mirrors Python's {@code test_word_parser.py} in
 * {@code tests/unit_tests/core/retrieval/indexing/processor/parser}.</p>
 */
class WordParserTest {

    private static final byte[] PNG_1X1 = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC");

    @TempDir
    Path tempDir;

    @Test
    void testInit() {
        assertNotNull(new WordParser());
    }

    @Test
    void testParseDocxSuccess() throws IOException {
        Path file = writeDocx("success.docx", document -> {
            document.createParagraph().createRun().setText("Paragraph 1");
            document.createParagraph().createRun().setText("Paragraph 2");
        });

        List<Document> docs = new WordParser().parse(file.toString(), "doc_1", null, Map.of());

        assertEquals(1, docs.size());
        assertEquals("doc_1", docs.getFirst().getId());
        assertTrue(docs.getFirst().getText().contains("Paragraph 1"));
        assertTrue(docs.getFirst().getText().contains("Paragraph 2"));
    }

    @Test
    void testParseDocxE2e() throws IOException {
        Path file = writeDocx("e2e.docx", document -> {
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
        });

        List<Document> docs = new WordParser().parse(file.toString(), "e2e_doc", null, Map.of());

        assertEquals(1, docs.size());
        String text = docs.getFirst().getText();
        assertTrue(text.contains("## Hello"));
        assertTrue(text.contains("First paragraph."));
        assertTrue(text.contains("Second paragraph with more text."));
        assertTrue(text.contains("| A1 | B1 |"));
        assertTrue(text.contains("| A2 | B2 |"));
        assertTrue(text.contains("Paragraph after table."));
    }

    @Test
    void testParseDocxHeadings() throws IOException {
        Path file = writeDocx("headings.docx", document -> {
            XWPFParagraph title = document.createParagraph();
            title.setStyle("Title");
            title.createRun().setText("Document Title Here");
            XWPFParagraph h1 = document.createParagraph();
            h1.setStyle("Heading1");
            h1.createRun().setText("Section");
            XWPFParagraph h2 = document.createParagraph();
            h2.setStyle("Heading2");
            h2.createRun().setText("Subsection");
            document.createParagraph().createRun().setText("Normal paragraph.");
        });

        List<Document> docs = new WordParser().parse(file.toString(), "headings_doc", null, Map.of());
        String text = docs.getFirst().getText();

        assertTrue(text.contains("# Document Title Here"));
        assertTrue(text.contains("## Section"));
        assertTrue(text.contains("### Subsection"));
        assertTrue(text.contains("Normal paragraph."));
    }

    @Test
    void testParseDocxE2eWithImage() throws Exception {
        Path file = writeDocx("with_image.docx", document -> {
            document.createParagraph().createRun().setText("Before image.");
            addPictureParagraph(document);
            document.createParagraph().createRun().setText("Between image.");
            addPictureParagraph(document);
            document.createParagraph().createRun().setText("After image.");
        });
        BaseModelClient llmClient = mock(BaseModelClient.class);
        when(llmClient.invoke(any(), any(), nullable(Float.class), nullable(Float.class), nullable(String.class),
                nullable(Integer.class), nullable(String.class), any(), nullable(Float.class), any()))
                .thenReturn(new AssistantMessage("A red square test image."));

        List<Document> docs = new WordParser().parse(file.toString(), "e2e_image_doc", llmClient, Map.of());

        assertEquals(1, docs.size());
        assertEquals("e2e_image_doc", docs.getFirst().getId());
        assertTrue(docs.getFirst().getText().contains(
                "Before image.\nA red square test image.\nBetween image.\nA red square test image.\nAfter image."));
        verify(llmClient, times(2)).invoke(any(), any(), nullable(Float.class), nullable(Float.class),
                nullable(String.class), nullable(Integer.class), nullable(String.class), any(), nullable(Float.class), any());
    }

    @Test
    void testParseDocxEmptyDocument() throws IOException {
        Path file = writeDocx("empty.docx", document -> {
        });

        assertTrue(new WordParser().parse(file.toString(), "doc_1", null, Map.of()).isEmpty());
    }

    @Test
    void testParseDocxFileNotFound() {
        assertTrue(new WordParser().parse(tempDir.resolve("nonexistent.docx").toString(), "doc_1", null, Map.of()).isEmpty());
    }

    @Test
    void testParseDocxWithException() throws IOException {
        Path file = tempDir.resolve("broken.docx");
        Files.writeString(file, "not a real docx");

        assertTrue(new WordParser().parse(file.toString(), "doc_1", null, Map.of()).isEmpty());
    }

    private Path writeDocx(String name, Consumer<XWPFDocument> customizer) throws IOException {
        Path file = tempDir.resolve(name);
        try (XWPFDocument document = new XWPFDocument(); OutputStream output = Files.newOutputStream(file)) {
            customizer.accept(document);
            document.write(output);
        }
        return file;
    }

    private static void addPictureParagraph(XWPFDocument document) {
        try {
            XWPFRun run = document.createParagraph().createRun();
            run.addPicture(new ByteArrayInputStream(PNG_1X1),
                    org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG,
                    "test_image.png",
                    Units.toEMU(10),
                    Units.toEMU(10));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
