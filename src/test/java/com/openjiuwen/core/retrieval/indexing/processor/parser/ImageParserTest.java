/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.TestModelClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_image_parser.py} in
 * {@code tests.unit_tests.core.retrieval.indexing.processor.parser}.
 */
class ImageParserTest {

    @TempDir
    Path tempDir;

    @Test
    void testInit() {
        ImageParser parser = new ImageParser();

        assertNotNull(parser);
    }

    @Test
    void testParseImageSuccess() throws IOException {
        Path image = tempDir.resolve("sample.png");
        Files.write(image, new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        ImageParser parser = new ImageParser();
        var documents = parser.parse(image.toString(), "doc_img_1", new TestModelClient("gpt-4o", "A cat sitting on a mat"), Map.of());

        assertEquals(1, documents.size());
        assertEquals("doc_img_1", documents.getFirst().getId());
        assertTrue(documents.getFirst().getText().contains("A cat sitting on a mat"));
    }

    @Test
    void testParseImageMultipleCaptions() throws IOException {
        Path image = tempDir.resolve("sample.jpg");
        Files.write(image, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});

        ImageParser parser = new TestableImageParser(new StubCaptioner(List.of("A dog", "Running in a park"), false));
        var documents = parser.parse(image.toString(), "doc_img_2", new TestModelClient("gpt-4o", "unused"), Map.of());

        assertEquals(1, documents.size());
        assertEquals("doc_img_2", documents.getFirst().getId());
        assertTrue(documents.getFirst().getText().contains("A dog"));
        assertTrue(documents.getFirst().getText().contains("Running in a park"));
    }

    @Test
    void testParseImageFileNotFound() {
        ImageParser parser = new ImageParser();

        var documents = parser.parse(tempDir.resolve("nonexistent_image.png").toString(), "doc_img_nf", new TestModelClient("gpt-4o", "caption"), Map.of());

        assertTrue(documents.isEmpty());
    }

    @Test
    void testParseImageWithException() throws IOException {
        Path image = tempDir.resolve("sample.png");
        Files.write(image, new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        ImageParser parser = new TestableImageParser(new StubCaptioner(List.of(), true));

        var documents = parser.parse(image.toString(), "doc_img_exc", new TestModelClient("gpt-4o", "unused"), Map.of());

        assertTrue(documents.isEmpty());
    }

    private static final class TestableImageParser extends ImageParser {
        private final ImageCaptioner captioner;

        private TestableImageParser(ImageCaptioner captioner) {
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
                throw new IllegalStateException("Captioning error");
            }
            return captions;
        }
    }
}
