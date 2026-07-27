/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.retrieval.common.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code JSONParser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/json_parser.py}.
 *
 * <p>Focused tests also mirror Python's {@code test_json_parser.py} in
 * {@code tests/unit_tests/core/retrieval/indexing/processor/parser/test_json_parser.py}.</p>
 */
class JSONParserTest {

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
        assertThat(new JsonParser()).isNotNull();
    }

    @Test
    void testParseJsonSuccess() throws Exception {
        Path file = Files.writeString(tempDir.resolve("sample.json"),
                "{\"name\":\"test\",\"value\":123,\"items\":[\"item1\",\"item2\"]}", StandardCharsets.UTF_8);

        List<Document> documents = new JsonParser().parse(file.toString(), "doc_1").join();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getId_()).isEqualTo("doc_1");
        assertThat(documents.get(0).getText()).contains("test", "123");
    }

    @Test
    void testParseJsonEmptyObject() throws Exception {
        Path file = Files.writeString(tempDir.resolve("empty.json"), "{}", StandardCharsets.UTF_8);

        List<Document> documents = new JsonParser().parse(file.toString(), "doc_1").join();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getText()).isEqualTo("{}");
    }

    @Test
    void testParseJsonArray() throws Exception {
        Path file = Files.writeString(tempDir.resolve("array.json"), "[1,2,3,\"test\"]", StandardCharsets.UTF_8);

        List<Document> documents = new JsonParser().parse(file.toString(), "doc_1").join();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getText()).contains("1", "test");
    }

    @Test
    void testParseJsonInvalidFormat() throws Exception {
        Path file = Files.writeString(tempDir.resolve("bad.json"), "{ invalid json }", StandardCharsets.UTF_8);

        List<Document> documents = new JsonParser().parse(file.toString(), "doc_1").join();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getText()).contains("invalid json");
    }

    @Test
    void testParseJsonFileNotFound() {
        List<Document> documents = new JsonParser().parse(tempDir.resolve("nonexistent.json").toString(), "doc_1")
                .join();

        assertThat(documents).isEmpty();
    }

    @Test
    void testParseJsonWithException() throws Exception {
        Path directory = tempDir.resolve("directory.json");
        Files.createDirectory(directory);

        List<Document> documents = new JsonParser().parse(directory.toString(), "doc_1").join();

        assertThat(documents).isEmpty();
    }

    @Test
    void testParseJsonWithUnicode() throws Exception {
        Path file = Files.writeString(tempDir.resolve("unicode.json"),
                "{\"name\":\"娴嬭瘯\",\"description\":\"杩欐槸涓€涓祴璇昞\"}", StandardCharsets.UTF_8);

        List<Document> documents = new JsonParser().parse(file.toString(), "doc_1").join();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getText()).contains("娴嬭瘯");
    }

    @Test
    void testParseJsonFormattedOutput() throws Exception {
        Path file = Files.writeString(tempDir.resolve("formatted.json"),
                "{\"key\":\"value\",\"nested\":{\"inner\":\"data\"}}", StandardCharsets.UTF_8);

        List<Document> documents = new JsonParser().parse(file.toString(), "doc_1").join();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getText()).contains("\n").contains("  ");
    }

    @Test
    void autoFileParserDispatchesJson() throws Exception {
        Path file = Files.writeString(tempDir.resolve("auto.json"), "{\"key\":\"value\"}", StandardCharsets.UTF_8);

        List<Document> documents = new AutoFileParser().parse(file.toString(), "auto-json").join();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getId_()).isEqualTo("auto-json");
        assertThat(documents.get(0).getText()).contains("value");
        assertThat(documents.get(0).getMetadata())
                .containsEntry("doc_id", "auto-json")
                .containsEntry("file_ext", ".json")
                .containsEntry("file_path", file.toString());
    }
}
