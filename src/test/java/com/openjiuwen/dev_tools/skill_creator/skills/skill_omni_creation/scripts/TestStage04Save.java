/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_creator.skills.skill_omni_creation.scripts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code tests/unit_tests/dev_tools/agent_builder/skill_omni_creation/test_stage04_save.py}.
 */
class TestStage04Save {

    @TempDir
    Path tempDir;

    @Test
    void writesImageToDisk() throws Exception {
        List<Map<String, Object>> result = Stage04Save.saveImageBlocks(
                List.of(image("https://example.com/img.png")),
                Map.of("https://example.com/img.png", new SkillOmniCommon.AssetPayload(new byte[]{1, 2, 3}, "image/png")),
                tempDir
        );
        assertEquals(1, tempDir.toFile().listFiles().length);
        assertNotNull(result.get(0).get("path"));
    }

    @Test
    void setsPathFieldInBlock() throws Exception {
        List<Map<String, Object>> result = Stage04Save.saveImageBlocks(
                List.of(image("https://example.com/img.jpg")),
                Map.of("https://example.com/img.jpg", new SkillOmniCommon.AssetPayload("jpegdata".getBytes(), "image/jpeg")),
                tempDir
        );

        Object path = result.get(0).get("path");
        assertInstanceOf(Path.class, path);
        assertEquals(tempDir, ((Path) path).getParent());
    }

    @Test
    void sequentialFilenameNumbering() throws Exception {
        Stage04Save.saveImageBlocks(
                List.of(
                        image("https://example.com/img0.png"),
                        image("https://example.com/img1.png"),
                        image("https://example.com/img2.png")
                ),
                Map.of(
                        "https://example.com/img0.png", new SkillOmniCommon.AssetPayload("data0".getBytes(), "image/png"),
                        "https://example.com/img1.png", new SkillOmniCommon.AssetPayload("data1".getBytes(), "image/png"),
                        "https://example.com/img2.png", new SkillOmniCommon.AssetPayload("data2".getBytes(), "image/png")
                ),
                tempDir
        );

        String[] names = tempDir.toFile().list();
        Arrays.sort(names);
        assertTrue(names[0].startsWith("img_00"));
        assertTrue(names[1].startsWith("img_01"));
        assertTrue(names[2].startsWith("img_02"));
    }

    @Test
    void usesCorrectExtensionFromUrl() throws Exception {
        Stage04Save.saveImageBlocks(
                List.of(image("https://example.com/photo.jpg")),
                Map.of("https://example.com/photo.jpg", new SkillOmniCommon.AssetPayload(new byte[]{1}, "image/jpeg")),
                tempDir
        );
        assertEquals(".jpg", tempDir.toFile().listFiles()[0].toPath().getFileName().toString().substring(6));
    }

    @Test
    void fallsBackToMimeExtensionForUnknownExt() throws Exception {
        Stage04Save.saveImageBlocks(
                List.of(image("https://example.com/photo.bmp")),
                Map.of("https://example.com/photo.bmp", new SkillOmniCommon.AssetPayload(new byte[]{1}, "image/png")),
                tempDir
        );

        assertEquals(".png", tempDir.toFile().listFiles()[0].toPath().getFileName().toString().substring(6));
    }

    @Test
    void preservesNonImageBlocks() throws Exception {
        List<Map<String, Object>> result = Stage04Save.saveImageBlocks(
                List.of(
                        Map.of("type", "heading", "level", 2, "text", "H", "source", "main"),
                        image("https://example.com/img.png"),
                        Map.of("type", "text", "text", "T", "source", "main")
                ),
                Map.of("https://example.com/img.png", new SkillOmniCommon.AssetPayload(new byte[]{1}, "image/png")),
                tempDir
        );
        assertEquals(List.of("heading", "image", "text"), result.stream().map(b -> String.valueOf(b.get("type"))).toList());
    }

    @Test
    void skipsImageBlockNotInFetched() throws Exception {
        List<Map<String, Object>> result = Stage04Save.saveImageBlocks(List.of(image("https://example.com/missing.png")), Map.of(), tempDir);
        assertTrue(result.isEmpty());
    }

    @Test
    void createsImageDirIfNotExists() throws Exception {
        Path nestedDir = tempDir.resolve("deep").resolve("nested").resolve("references");

        Stage04Save.saveImageBlocks(
                List.of(image("https://example.com/img.png")),
                Map.of("https://example.com/img.png", new SkillOmniCommon.AssetPayload(new byte[]{1}, "image/png")),
                nestedDir
        );

        assertTrue(Files.exists(nestedDir));
    }

    @Test
    void handlesEmptyBlocks() throws Exception {
        assertTrue(Stage04Save.saveImageBlocks(List.of(), Map.of(), tempDir).isEmpty());
    }

    @Test
    void preservesOriginalFieldsInOutput() throws Exception {
        Map<String, Object> block = image("https://example.com/img.png");
        block.put("alt", "custom alt");
        block.put("source", "subpage");

        List<Map<String, Object>> result = Stage04Save.saveImageBlocks(
                List.of(block),
                Map.of("https://example.com/img.png", new SkillOmniCommon.AssetPayload(new byte[]{1}, "image/png")),
                tempDir
        );

        assertEquals("custom alt", result.get(0).get("alt"));
        assertEquals("subpage", result.get(0).get("source"));
        assertEquals("https://example.com/img.png", result.get(0).get("url"));
    }

    private static Map<String, Object> image(String url) {
        java.util.LinkedHashMap<String, Object> block = new java.util.LinkedHashMap<>();
        block.put("type", "image");
        block.put("url", url);
        block.put("alt", "screenshot");
        block.put("source", "main");
        block.put("path", null);
        return block;
    }
}
