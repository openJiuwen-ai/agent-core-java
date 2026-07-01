/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.retrieval.common.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TxtMdParser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/txt_md_parser.py}.
 *
 * <p>Focused tests also mirror Python's {@code test_txt_md_parser.py} in
 * {@code tests/unit_tests/core/retrieval/indexing/processor/parser/test_txt_md_parser.py}.</p>
 */
class TxtMdParserTest {

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
        assertThat(new TxtMdParser()).isNotNull();
    }

    @Test
    void testParseEmptyFile() throws Exception {
        Path file = Files.writeString(tempDir.resolve("empty.txt"), "", StandardCharsets.UTF_8);

        List<Document> documents = new TxtMdParser().parse(file.toString(), "doc_1").join();

        assertThat(documents).isEmpty();
    }

    @Test
    void testParseFileNotFound() {
        List<Document> documents = new TxtMdParser()
                .parse(tempDir.resolve("nonexistent.txt").toString(), "doc_1")
                .join();

        assertThat(documents).isEmpty();
    }

    @Test
    void testParseStripsContent() throws Exception {
        Path file = Files.writeString(tempDir.resolve("sample.txt"), "   \n  Content  \n   ",
                StandardCharsets.UTF_8);

        List<Document> documents = new TxtMdParser().parse(file.toString(), "doc_1").join();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getId_()).isEqualTo("doc_1");
        assertThat(documents.get(0).getText()).isEqualTo("Content");
        assertThat(documents.get(0).getMetadata()).isEmpty();
    }

    @Test
    void supportsTxtMdAndMarkdownCaseInsensitively() {
        TxtMdParser parser = new TxtMdParser();

        assertThat(parser.supports("a.txt")).isTrue();
        assertThat(parser.supports("a.TXT")).isTrue();
        assertThat(parser.supports("a.md")).isTrue();
        assertThat(parser.supports("a.MD")).isTrue();
        assertThat(parser.supports("a.markdown")).isTrue();
        assertThat(parser.supports("a.MARKDOWN")).isTrue();
        assertThat(parser.supports("a.json")).isFalse();
        assertThat(parser.supports(null)).isFalse();
    }

    @Test
    void parseReadsUtf16BomTextAndStripsContent() throws Exception {
        Path file = tempDir.resolve("utf16.MD");
        byte[] bom = new byte[] {(byte) 0xFF, (byte) 0xFE};
        byte[] body = "  Markdown body  ".getBytes(StandardCharsets.UTF_16LE);
        byte[] content = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(body, 0, content, bom.length, body.length);
        Files.write(file, content);

        List<Document> documents = new TxtMdParser().parse(file.toString(), "doc-utf16").join();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getText()).isEqualTo("Markdown body");
    }

    @Test
    void parseFallsBackForNonUtf8Text() throws Exception {
        Path file = tempDir.resolve("gb.markdown");
        String expected = "\u4e2d\u6587\u5185\u5bb9";
        Files.write(file, ("  " + expected + "  ").getBytes(Charset.forName("GB18030")));

        List<Document> documents = new TxtMdParser().parse(file.toString(), "doc-gb").join();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getText()).isEqualTo(expected);
    }

    @Test
    void parseIgnoresMalformedUtf8WhenDetectionFallsBackToUtf8() throws Exception {
        Path file = tempDir.resolve("invalid.txt");
        Files.write(file, new byte[] {' ', 'a', (byte) 0xC3, (byte) 0x28, 'b', ' '});

        List<Document> documents = new TxtMdParser().parse(file.toString(), "doc-invalid").join();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getText()).contains("a").contains("b");
    }

    @Test
    void autoFileParserDispatchesTxtMdAndEnrichesMetadata() throws Exception {
        Path file = Files.writeString(tempDir.resolve("note.MARKDOWN"), " note body ", StandardCharsets.UTF_8);

        List<Document> documents = new AutoFileParser()
                .parse(file.toString(), "auto-md", null, Map.of("file_name", "Named note"))
                .join();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getText()).isEqualTo("note body");
        assertThat(documents.get(0).getMetadata())
                .containsEntry("doc_id", "auto-md")
                .containsEntry("title", "Named note")
                .containsEntry("file_path", file.toString())
                .containsEntry("file_ext", ".markdown");
    }
}
