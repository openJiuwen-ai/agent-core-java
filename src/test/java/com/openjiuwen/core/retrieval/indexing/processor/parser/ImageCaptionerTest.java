/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/
package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.retrieval.TestModelClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageCaptionerTest {

    @TempDir
    Path tempDir;

    @Test
    void cpImageCopiesToTargetDirectory() throws IOException {
        Path image = tempDir.resolve("sample.png");
        Files.write(image, new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        String copied = ImageCaptioner.cpImage(image.toString(), tempDir.resolve("images").toString());

        assertTrue(Files.exists(Path.of(copied)));
        assertEquals(Files.readAllBytes(image).length, Files.readAllBytes(Path.of(copied)).length);
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
