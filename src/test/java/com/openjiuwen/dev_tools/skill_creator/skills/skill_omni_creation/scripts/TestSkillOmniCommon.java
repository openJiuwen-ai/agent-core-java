/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_creator.skills.skill_omni_creation.scripts;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code tests/unit_tests/dev_tools/agent_builder/skill_omni_creation/test_common.py}.
 */
class TestSkillOmniCommon {

    @Test
    void slugifyLowercasesInput() {
        assertEquals("hello_world", SkillOmniCommon.slugify("Hello World"));
    }

    @Test
    void slugifyReplacesSpacesWithUnderscore() {
        assertEquals("foo_bar_baz", SkillOmniCommon.slugify("foo bar baz"));
    }

    @Test
    void slugifyStripsSpecialCharacters() {
        assertEquals("hello_world", SkillOmniCommon.slugify("hello! world?"));
    }

    @Test
    void slugifyCollapsesMultipleSeparators() {
        assertEquals("foo_bar_baz", SkillOmniCommon.slugify("foo--bar__baz"));
    }

    @Test
    void slugifyTruncatesTo80Characters() {
        assertEquals(80, SkillOmniCommon.slugify("a".repeat(100)).length());
    }

    @Test
    void slugifyStripsWhitespace() {
        assertEquals("hello", SkillOmniCommon.slugify("  hello  "));
    }

    @Test
    void stripJsonFenceHandlesJsonFence() {
        assertEquals("{\"key\": \"value\"}", SkillOmniCommon.stripJsonFence("```json\n{\"key\": \"value\"}\n```"));
    }

    @Test
    void stripJsonFenceHandlesPlainFence() {
        assertEquals("{\"key\": \"value\"}", SkillOmniCommon.stripJsonFence("```\n{\"key\": \"value\"}\n```"));
    }

    @Test
    void stripJsonFenceLeavesUnfencedText() {
        assertEquals("{\"key\": \"value\"}", SkillOmniCommon.stripJsonFence("{\"key\": \"value\"}"));
    }

    @Test
    void stripJsonFenceStripsSurroundingWhitespace() {
        assertEquals("{\"key\": \"value\"}", SkillOmniCommon.stripJsonFence("  ```json\n{\"key\": \"value\"}\n```  "));
    }

    @Test
    void imageExtExtractsExtensionFromUrl() {
        assertEquals(".jpg", SkillOmniCommon.imageExt("https://example.com/img/photo.jpg", "image/png"));
    }

    @Test
    void imageExtFallsBackToMime() {
        assertEquals(".png", SkillOmniCommon.imageExt("https://example.com/img/photo.bmp", "image/png"));
    }

    @Test
    void imageExtFallsBackToPng() {
        assertEquals(".png", SkillOmniCommon.imageExt("https://example.com/img/photo.bmp", "image/tiff"));
    }

    @Test
    void imageExtKeepsWebp() {
        assertEquals(".webp", SkillOmniCommon.imageExt("https://example.com/img/photo.webp", "image/jpeg"));
    }

    @Test
    void blocksWithPathsAsStringConvertsPathToString() {
        List<Map<String, Object>> result = SkillOmniCommon.blocksWithPathsAsString(List.of(
                Map.of("type", "image", "path", Path.of("/some/path.png"), "alt", "x")
        ));
        assertEquals("/some/path.png", result.get(0).get("path"));
        assertTrue(result.get(0).get("path") instanceof String);
    }

    @Test
    void blocksWithPathsAsPathConvertsStringToPath() {
        List<Map<String, Object>> result = SkillOmniCommon.blocksWithPathsAsPath(List.of(
                Map.of("type", "image", "path", "/some/path.png", "alt", "x")
        ));
        assertTrue(result.get(0).get("path") instanceof Path);
    }

    @Test
    void blocksWithPathsAsStringLeavesNonImageBlocksUntouched() {
        Map<String, Object> block = Map.of("type", "text", "text", "hello");
        assertEquals(block, SkillOmniCommon.blocksWithPathsAsString(List.of(block)).get(0));
    }

    @Test
    void blocksWithPathsAsStringDoesNotMutateOriginal() {
        List<Map<String, Object>> original = List.of(Map.of("type", "image", "path", Path.of("/some/path.png")));
        SkillOmniCommon.blocksWithPathsAsString(original);
        assertTrue(original.get(0).get("path") instanceof Path);
    }

    @Test
    void stripHallucinatedImagesKeepsValidReference() {
        String markdown = "![screenshot](references/img_00.jpg)";
        assertEquals(markdown, SkillOmniCommon.stripHallucinatedImages(markdown, Set.of("references/img_00.jpg")));
    }

    @Test
    void stripHallucinatedImagesRemovesInvalidReference() {
        String result = SkillOmniCommon.stripHallucinatedImages(
                "step one\n\n![fake](references/nonexistent.png)\n\nstep two",
                Set.of()
        );
        assertFalse(result.contains("nonexistent"));
        assertTrue(result.contains("step one"));
        assertTrue(result.contains("step two"));
    }

    @Test
    void stripHallucinatedImagesRemovesButtonNameHallucination() {
        String result = SkillOmniCommon.stripHallucinatedImages("Click ![Plus](Plus) to add an item.", Set.of());
        assertFalse(result.contains("![Plus](Plus)"));
    }

    @Test
    void stripHallucinatedImagesLeavesTextOnlyMarkdown() {
        assertEquals("just text, no images", SkillOmniCommon.stripHallucinatedImages("just text, no images", Set.of()));
    }
}
