/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused tests for {@link WordParser}.
 *
 * <p>Mirrors Python's {@code WordParser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/word_parser.py}.</p>
 *
 * <p>Scenarios also mirror Python's {@code TestWordParser} in
 * {@code tests/unit_tests/core/retrieval/indexing/processor/parser/test_word_parser.py}.</p>
 */
class WordParserTest {

    private static final byte[] PNG_1X1 = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC");

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
        assertThat(new WordParser()).isNotNull();
    }

    @Test
    void testParseDocxSuccess() throws IOException {
        Path file = writeDocx("success.docx", document -> {
            document.createParagraph().createRun().setText("Paragraph 1");
            document.createParagraph().createRun().setText("Paragraph 2");
        });

        List<Document> documents = new WordParser().parse(file.toString(), "doc_1").join();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getId_()).isEqualTo("doc_1");
        assertThat(documents.get(0).getText()).contains("Paragraph 1", "Paragraph 2");
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

        List<Document> documents = new WordParser().parse(file.toString(), "e2e_doc").join();

        assertThat(documents).hasSize(1);
        String text = documents.get(0).getText();
        assertThat(text).contains("## Hello");
        assertThat(text).contains("First paragraph.");
        assertThat(text).contains("Second paragraph with more text.");
        assertThat(text).contains("| A1 | B1 |", "| --- | --- |", "| A2 | B2 |");
        assertThat(text).contains("Paragraph after table.");
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

        List<Document> documents = new WordParser().parse(file.toString(), "headings_doc").join();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getText())
                .contains("# Document Title Here")
                .contains("## Section")
                .contains("### Subsection")
                .contains("Normal paragraph.");
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
        ImageCaptioner captioner = mock(ImageCaptioner.class);
        List<List<String>> calls = new ArrayList<>();
        when(captioner.captionImages(anyList())).thenAnswer(invocation -> {
            List<String> images = List.copyOf(invocation.getArgument(0));
            calls.add(images);
            return CompletableFuture.completedFuture(List.of("A red square test image."));
        });
        WordParser parser = new WordParser() {
            @Override
            protected ImageCaptioner createImageCaptioner(BaseModelClient llmClient) {
                return captioner;
            }

            @Override
            protected String savedImageDir() {
                return tempDir.resolve("images").toString();
            }
        };

        List<Document> documents = parser.parse(file.toString(), "e2e_image_doc", null, Map.of()).join();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getId_()).isEqualTo("e2e_image_doc");
        assertThat(documents.get(0).getText()).contains(
                "Before image.\nA red square test image.\nBetween image.\nA red square test image.\nAfter image.");
        verify(captioner, times(2)).captionImages(anyList());
        assertThat(calls).hasSize(2);
        assertThat(calls).allSatisfy(images -> {
            assertThat(images).hasSize(1);
            assertThat(images.get(0)).endsWith(".png");
            assertThat(Files.exists(Path.of(images.get(0)))).isTrue();
        });
    }

    @Test
    void testParseDocxEmptyDocument() throws IOException {
        Path file = writeDocx("empty.docx", document -> {
        });

        assertThat(new WordParser().parse(file.toString(), "doc_1").join()).isEmpty();
    }

    @Test
    void testParseDocxFileNotFound() {
        List<Document> documents = new WordParser()
                .parse(tempDir.resolve("nonexistent.docx").toString(), "doc_1")
                .join();

        assertThat(documents).isEmpty();
    }

    @Test
    void testParseDocxWithException() throws IOException {
        Path file = tempDir.resolve("broken.docx");
        Files.writeString(file, "not a real docx");

        assertThat(new WordParser().parse(file.toString(), "doc_1").join()).isEmpty();
    }

    @Test
    void supportsDocxAndDocCaseInsensitively() {
        WordParser parser = new WordParser();

        assertThat(parser.supports("sample.docx")).isTrue();
        assertThat(parser.supports("sample.DOCX")).isTrue();
        assertThat(parser.supports("legacy.doc")).isTrue();
        assertThat(parser.supports("legacy.DOC")).isTrue();
        assertThat(parser.supports("sample.pdf")).isFalse();
        assertThat(parser.supports(null)).isFalse();
    }

    @Test
    void autoFileParserDispatchesWordParserAndEnrichesMetadata() throws IOException {
        Path file = writeDocx("auto.docx", document -> document.createParagraph().createRun().setText("Auto content"));

        List<Document> documents = new AutoFileParser().parse(file.toString(), "auto-word").join();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getText()).contains("Auto content");
        assertThat(documents.get(0).getMetadata())
                .containsEntry("doc_id", "auto-word")
                .containsEntry("file_ext", ".docx")
                .containsEntry("file_path", file.toString());
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
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
