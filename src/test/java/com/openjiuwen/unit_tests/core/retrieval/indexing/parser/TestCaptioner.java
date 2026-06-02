/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.parser;

import com.openjiuwen.core.retrieval.TestModelClient;
import com.openjiuwen.core.retrieval.indexing.processor.parser.ImageCaptioner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Captioner test cases.
 *
 * <p>Mirrors Python's {@code test_captioner.py} with Java adaptations for the
 * synchronous captioning helper.</p>
 */
class TestCaptioner {

    @TempDir
    Path tempDir;

    @Test
    void testDumpImageSavesAndReturnsCopiedPath() throws Exception {
        byte[] imageBytes = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        Path source = tempDir.resolve("source.png");
        Path targetDir = tempDir.resolve("saved");
        Files.write(source, imageBytes);

        String copiedPath = ImageCaptioner.cpImage(source.toString(), targetDir.toString());
        Path copied = Path.of(copiedPath);

        assertTrue(Files.exists(copied));
        assertEquals("source.png", copied.getFileName().toString());
        assertArrayEquals(imageBytes, Files.readAllBytes(copied));
    }

    @Test
    void testLlmCallReturnsEmptyWhenNoLlmClient() throws Exception {
        Path image = tempDir.resolve("image.png");
        Files.write(image, new byte[] {1, 2, 3});

        InspectableCaptioner captioner = new InspectableCaptioner(null);

        assertEquals("", captioner.invokeLlm(image.toString()));
    }

    @Test
    void testCaptionImagesUsesLlmCallForEachExistingImage() throws Exception {
        Path first = tempDir.resolve("img1.png");
        Path second = tempDir.resolve("img2.jpg");
        Files.write(first, new byte[] {1});
        Files.write(second, new byte[] {2});

        CountingCaptioner captioner = new CountingCaptioner(Map.of(
                "img1.png", "caption for img1.png",
                "img2.jpg", "caption for img2.jpg"));

        List<String> captions = captioner.captionImages(List.of(first.toString(), second.toString()));

        assertEquals(2, captions.size());
        assertEquals("caption for img1.png", captions.get(0));
        assertEquals("caption for img2.jpg", captions.get(1));
        assertEquals(2, captioner.callCount);
    }

    @Test
    void testCaptionImagesHandlesPerImageFailuresWithoutInterruptingLaterImages() throws Exception {
        Path good = tempDir.resolve("good.png");
        Path bad = tempDir.resolve("bad.png");
        Path good2 = tempDir.resolve("good2.png");
        Files.write(good, new byte[] {1});
        Files.write(bad, new byte[] {2});
        Files.write(good2, new byte[] {3});

        CountingCaptioner captioner = new CountingCaptioner(Map.of(
                "good.png", "ok:good.png",
                "bad.png", "",
                "good2.png", "ok:good2.png"));

        List<String> captions = captioner.captionImages(List.of(
                good.toString(),
                bad.toString(),
                good2.toString()));

        assertEquals(3, captions.size());
        assertEquals("ok:good.png", captions.get(0));
        assertEquals("", captions.get(1));
        assertEquals("ok:good2.png", captions.get(2));
        assertEquals(3, captioner.callCount);
    }

    private static final class InspectableCaptioner extends ImageCaptioner {
        private InspectableCaptioner(TestModelClient llmClient) {
            super(llmClient);
        }

        private String invokeLlm(String imageLoc) {
            return llmCall(imageLoc);
        }
    }

    private static final class CountingCaptioner extends ImageCaptioner {
        private final Map<String, String> responses;
        private int callCount;

        private CountingCaptioner(Map<String, String> responses) {
            super(new TestModelClient("gpt-4o", "unused"));
            this.responses = responses;
        }

        @Override
        protected String llmCall(String imageLoc) {
            callCount++;
            return responses.getOrDefault(Path.of(imageLoc).getFileName().toString(), "");
        }
    }
}
