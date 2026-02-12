// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DataFrame schema models.
 * Tests BaseDataFrame, TextDataFrame, FileDataFrame, JsonDataFrame.
 */
@DisplayName("DataFrame Schema Tests")
class DataFrameTest {

    // ---- BaseDataFrame ----

    @Nested
    @DisplayName("BaseDataFrame Tests")
    class BaseDataFrameTests {

        @Test
        @DisplayName("should accept valid types: text, file, json")
        void testBaseDataFrameAcceptsValidTypes() {
            TextDataFrame text = new TextDataFrame("hello");
            assertEquals("text", text.getType());

            FileDataFrame file = new FileDataFrame("report.pdf", "application/pdf");
            assertEquals("file", file.getType());

            JsonDataFrame json = new JsonDataFrame(Map.of("key", "value"));
            assertEquals("json", json.getType());
        }

        @Test
        @DisplayName("should reject invalid type via compile-time safety (enum/subclass only)")
        void testBaseDataFrameRejectsInvalidType() {
            // In Java, type safety is enforced by the class hierarchy.
            // BaseDataFrame is abstract, so only valid subtypes can be instantiated.
            // This test verifies that all subtypes have correct types.
            BaseDataFrame[] frames = {
                new TextDataFrame("test"),
                new FileDataFrame("f.txt", "text/plain"),
                new JsonDataFrame(Map.of())
            };
            for (BaseDataFrame df : frames) {
                assertTrue(
                    df.getType().equals("text") ||
                    df.getType().equals("file") ||
                    df.getType().equals("json")
                );
            }
        }
    }

    // ---- TextDataFrame ----

    @Nested
    @DisplayName("TextDataFrame Tests")
    class TextDataFrameTests {

        @Test
        @DisplayName("should default type to 'text' and require text field")
        void testTextDataFrameDefaultsAndRequiredFields() {
            TextDataFrame tdf = new TextDataFrame("hello world");
            assertEquals("text", tdf.getType());
            assertEquals("hello world", tdf.getText());
        }

        @Test
        @DisplayName("should reject null text field")
        void testTextDataFrameMissingTextFieldRaises() {
            assertThrows(NullPointerException.class, () -> new TextDataFrame(null));
        }

        @Test
        @DisplayName("empty string text is valid")
        void testTextDataFrameEmptyStringTextIsValid() {
            TextDataFrame tdf = new TextDataFrame("");
            assertEquals("", tdf.getText());
        }
    }

    // ---- FileDataFrame ----

    @Nested
    @DisplayName("FileDataFrame Tests")
    class FileDataFrameTests {

        @Test
        @DisplayName("should default type to 'file', require name and mimeType")
        void testFileDataFrameDefaultsAndRequiredFields() {
            FileDataFrame fdf = new FileDataFrame("report.pdf", "application/pdf");
            assertEquals("file", fdf.getType());
            assertEquals("report.pdf", fdf.getName());
            assertEquals("application/pdf", fdf.getMimeType());
            assertNull(fdf.getBytes());
            assertNull(fdf.getUri());
        }

        @Test
        @DisplayName("bytes defaults to null")
        void testFileDataFrameBytesDefaultsToNull() {
            FileDataFrame fdf = new FileDataFrame("img.png", "image/png");
            assertNull(fdf.getBytes());
        }

        @Test
        @DisplayName("can carry content via URI")
        void testFileDataFrameWithUri() {
            FileDataFrame fdf = new FileDataFrame("doc.txt", "text/plain", null, "file:///tmp/doc.txt");
            assertEquals("file:///tmp/doc.txt", fdf.getUri());
        }

        @Test
        @DisplayName("should reject missing required fields")
        void testFileDataFrameMissingRequiredFieldsRaises() {
            assertThrows(NullPointerException.class, () -> new FileDataFrame(null, "text/plain"));
            assertThrows(NullPointerException.class, () -> new FileDataFrame("file.txt", null));
        }
    }

    // ---- JsonDataFrame ----

    @Nested
    @DisplayName("JsonDataFrame Tests")
    class JsonDataFrameTests {

        @Test
        @DisplayName("should default type to 'json' and require data dict")
        void testJsonDataFrameDefaultsAndRequiredFields() {
            JsonDataFrame jdf = new JsonDataFrame(Map.of("key", "value", "nested", Map.of("a", 1)));
            assertEquals("json", jdf.getType());
            assertEquals("value", jdf.getData().get("key"));
        }

        @Test
        @DisplayName("empty dict is valid")
        void testJsonDataFrameEmptyDictIsValid() {
            JsonDataFrame jdf = new JsonDataFrame(Map.of());
            assertTrue(jdf.getData().isEmpty());
        }

        @Test
        @DisplayName("should reject null data field")
        void testJsonDataFrameMissingDataRaises() {
            assertThrows(NullPointerException.class, () -> new JsonDataFrame(null));
        }
    }

    // ---- DataFrame type discrimination ----

    @Nested
    @DisplayName("DataFrame Type Discrimination Tests")
    class DataFrameTypeTests {

        @Test
        @DisplayName("should correctly identify TextDataFrame via instanceof")
        void testUnionTypeTextDeserialization() {
            BaseDataFrame df = new TextDataFrame("hello");
            assertInstanceOf(TextDataFrame.class, df);
            assertEquals("hello", ((TextDataFrame) df).getText());
        }

        @Test
        @DisplayName("should correctly identify FileDataFrame via instanceof")
        void testUnionTypeFileDeserialization() {
            BaseDataFrame df = new FileDataFrame("test.pdf", "application/pdf");
            assertInstanceOf(FileDataFrame.class, df);
            assertEquals("test.pdf", ((FileDataFrame) df).getName());
        }

        @Test
        @DisplayName("should correctly identify JsonDataFrame via instanceof")
        void testUnionTypeJsonDeserialization() {
            BaseDataFrame df = new JsonDataFrame(Map.of("k", 1));
            assertInstanceOf(JsonDataFrame.class, df);
            assertEquals(1, ((JsonDataFrame) df).getData().get("k"));
        }

        @Test
        @DisplayName("all subtypes should support equals/hashCode")
        void testModelDumpRoundtrip() {
            TextDataFrame text1 = new TextDataFrame("test");
            TextDataFrame text2 = new TextDataFrame("test");
            assertEquals(text1, text2);
            assertEquals(text1.hashCode(), text2.hashCode());

            FileDataFrame file1 = new FileDataFrame("f.txt", "text/plain", null, "http://x");
            FileDataFrame file2 = new FileDataFrame("f.txt", "text/plain", null, "http://x");
            assertEquals(file1, file2);

            JsonDataFrame json1 = new JsonDataFrame(Map.of("x", 1));
            JsonDataFrame json2 = new JsonDataFrame(Map.of("x", 1));
            assertEquals(json1, json2);
        }
    }
}

