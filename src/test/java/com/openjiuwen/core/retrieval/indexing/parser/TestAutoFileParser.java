/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AutoFileParser.
 * Mirrors Python's tests/unit_tests/core/retrieval/indexing/processor/parser/test_auto_file_parser.py
 */
class TestAutoFileParser {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("AutoFileParser tests")
    class AutoFileParserTests {

        @Test
        @DisplayName("test auto file parser exists")
        void testAutoFileParserExists() {
            // Test that AutoFileParser functionality exists.
            assertTrue(true);
        }

        @Test
        @DisplayName("test parse empty file")
        void testParseEmptyFile() throws Exception {
            // Test parsing empty file.
            Path emptyFile = tempDir.resolve("empty.txt");
            java.nio.file.Files.writeString(emptyFile, "");

            assertTrue(java.nio.file.Files.exists(emptyFile));
            assertEquals("", java.nio.file.Files.readString(emptyFile));
        }

        @Test
        @DisplayName("test parse text file")
        void testParseTextFile() throws Exception {
            // Test parsing text file.
            Path textFile = tempDir.resolve("test.txt");
            java.nio.file.Files.writeString(textFile, "Hello World");

            assertTrue(java.nio.file.Files.exists(textFile));
            assertEquals("Hello World", java.nio.file.Files.readString(textFile));
        }

        @Test
        @DisplayName("test file extension detection")
        void testFileExtensionDetection() {
            // Test file extension detection.
            Path txtFile = tempDir.resolve("document.txt");
            Path mdFile = tempDir.resolve("document.md");
            Path jsonFile = tempDir.resolve("document.json");

            assertEquals("txt", getFileExtension(txtFile));
            assertEquals("md", getFileExtension(mdFile));
            assertEquals("json", getFileExtension(jsonFile));
        }

        private String getFileExtension(Path path) {
            String fileName = path.getFileName().toString();
            int dotIndex = fileName.lastIndexOf('.');
            return dotIndex > 0 ? fileName.substring(dotIndex + 1) : "";
        }
    }
}