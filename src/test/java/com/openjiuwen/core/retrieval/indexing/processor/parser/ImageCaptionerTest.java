/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.retrieval.TestModelClient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class ImageCaptionerTest {
    @TempDir
    Path tempDir;

    @Test
    void cpImageCopiesToTargetDirectory() throws IOException {
        Path image = tempDir.resolve("sample.png");
        Files.write(image, new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        String copied = ImageCaptioner.cpImage(image.toString(), "images", tempDir);

        assertTrue(Files.exists(Path.of(copied)));
        assertEquals(Files.readAllBytes(image).length, Files.readAllBytes(Path.of(copied)).length);
    }

    @Test
    void cpImageRejectsTargetsOutsideAllowedBaseDirectory() throws IOException {
        Path image = tempDir.resolve("sample.png");
        Files.write(image, new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        Path allowedBaseDir = Files.createDirectories(tempDir.resolve("allowed"));
        Path outsideDir = Files.createDirectories(tempDir.resolve("outside"));

        assertThrows(SecurityException.class,
                () -> ImageCaptioner.cpImage(image.toString(), "../outside", allowedBaseDir));
        assertThrows(SecurityException.class,
                () -> ImageCaptioner.cpImage(image.toString(), outsideDir.toString(), allowedBaseDir));

        Files.createSymbolicLink(allowedBaseDir.resolve("linked"), outsideDir);
        assertThrows(SecurityException.class,
                () -> ImageCaptioner.cpImage(image.toString(), "linked", allowedBaseDir));
    }

    @Test
    void cpImageRejectsInvalidSourcePath() {
        assertThrows(IllegalArgumentException.class,
                () -> ImageCaptioner.cpImage(tempDir.resolve("missing.png").toString(), "images", tempDir));
        assertThrows(IllegalArgumentException.class,
                () -> ImageCaptioner.cpImage(tempDir.toString(), "images", tempDir));
    }

    @Test
    void sourceResolverRequiresARealRegularFile() throws Exception {
        Path image = tempDir.resolve("source.png");
        Files.write(image, new byte[] {1, 2, 3});

        assertEquals(image.toRealPath(), ImageCaptioner.resolveSafeSourcePath(image.toString()));
        assertThrows(IllegalArgumentException.class, () -> ImageCaptioner.resolveSafeSourcePath(" "));
        assertThrows(IllegalArgumentException.class,
                () -> ImageCaptioner.resolveSafeSourcePath(tempDir.toString()));
    }

    @Test
    void captionImagesUsesLlmForExistingFiles() throws IOException {
        Path image = tempDir.resolve("sample.png");
        Files.write(image, new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        TestModelClient llmClient = new TestModelClient("gpt-4o", "caption text");
        ImageCaptioner captioner = new ImageCaptioner(llmClient);
        List<String> captions = captioner.captionImages(List.of(image.toString()));

        assertEquals(List.of("caption text"), captions);
        assertTrue(llmClient.getLastMessages() instanceof List<?>);
    }

    @Test
    void captionImagesReturnsEmptyStringWithoutLlm() throws IOException {
        Path image = tempDir.resolve("sample.png");
        Files.write(image, new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        ImageCaptioner captioner = new ImageCaptioner(null);

        assertEquals(List.of(""), captioner.captionImages(List.of(image.toString())));
    }
}
