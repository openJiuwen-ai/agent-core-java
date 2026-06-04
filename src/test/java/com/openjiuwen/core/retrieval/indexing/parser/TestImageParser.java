/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.parser;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.indexing.processor.parser.ImageCaptioner;
import com.openjiuwen.core.retrieval.indexing.processor.parser.ImageParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ImageParser.
 *
 * <p>Mirrors Python's {@code TestImageParser} in
 * {@code tests.unit_tests.core.retrieval.indexing.processor.parser.test_image_parser}.</p>
 */
class TestImageParser {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("ImageParser tests")
    class ImageParserTests {

        @Test
        @DisplayName("test_init")
        void testInit() {
            assertNotNull(new ImageParser());
        }

        @Test
        @DisplayName("test_parse_image_success")
        void testParseImageSuccess() throws Exception {
            Path image = tempDir.resolve("image.png");
            Files.write(image, new byte[] {1, 2, 3});
            String caption = "A small test image";

            try (MockedConstruction<ImageCaptioner> ignored = Mockito.mockConstruction(
                    ImageCaptioner.class,
                    (mock, context) -> {
                        when(mock.cpImage(anyString())).thenReturn(image.toString());
                        when(mock.captionImages(anyList())).thenReturn(List.of(caption));
                    })) {
                List<Document> documents = new ImageParser().parse(image.toString(), "doc_img_1", null, Map.of());

                assertEquals(1, documents.size());
                assertEquals("doc_img_1", documents.getFirst().getId());
                assertTrue(documents.getFirst().getText().contains(caption));
            }
        }

        @Test
        @DisplayName("test_parse_image_multiple_captions")
        void testParseImageMultipleCaptions() throws Exception {
            Path image = tempDir.resolve("image.jpg");
            Files.write(image, new byte[] {4, 5, 6});

            try (MockedConstruction<ImageCaptioner> ignored = Mockito.mockConstruction(
                    ImageCaptioner.class,
                    (mock, context) -> {
                        when(mock.cpImage(anyString())).thenReturn(image.toString());
                        when(mock.captionImages(anyList())).thenReturn(List.of("first caption", "second caption"));
                    })) {
                List<Document> documents = new ImageParser().parse(image.toString(), "doc_img_2", null, Map.of());

                assertEquals(1, documents.size());
                assertEquals("doc_img_2", documents.getFirst().getId());
                assertTrue(documents.getFirst().getText().contains("first caption"));
                assertTrue(documents.getFirst().getText().contains("second caption"));
            }
        }

        @Test
        @DisplayName("test_parse_image_file_not_found")
        void testParseImageFileNotFound() {
            List<Document> documents = new ImageParser().parse(
                    tempDir.resolve("missing.png").toString(), "missing", null, Map.of());

            assertTrue(documents.isEmpty());
        }

        @Test
        @DisplayName("test_parse_image_with_exception")
        void testParseImageWithException() throws Exception {
            Path image = tempDir.resolve("image.webp");
            Files.write(image, new byte[] {7, 8, 9});

            try (MockedConstruction<ImageCaptioner> ignored = Mockito.mockConstruction(
                    ImageCaptioner.class,
                    (mock, context) -> when(mock.cpImage(anyString())).thenThrow(new IllegalStateException("boom")))) {
                List<Document> documents = new ImageParser().parse(image.toString(), "doc_img_3", null, Map.of());

                assertTrue(documents.isEmpty());
            }
        }
    }
}
