/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_creator.skills.skill_omni_creation.scripts;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code tests/unit_tests/dev_tools/agent_builder/skill_omni_creation/test_stage03_filter.py}.
 */
class TestStage03Filter {

    @Test
    void getImageContextFindsHeadingBeforeImage() {
        Stage03Filter.ImageContext context = Stage03Filter.getImageContext(List.of(
                heading("Quick access"),
                image("https://example.com/img.png", "main")
        ), 1);
        assertEquals("Quick access", context.heading());
    }

    @Test
    void getImageContextFindsTextBeforeImage() {
        Stage03Filter.ImageContext context = Stage03Filter.getImageContext(List.of(
                text("Click the Save button to proceed."),
                image("https://example.com/img.png", "main")
        ), 1);
        assertTrue(context.textBefore().contains("Save"));
    }

    @Test
    void getImageContextFindsTextAfterImage() {
        Stage03Filter.ImageContext context = Stage03Filter.getImageContext(List.of(
                image("https://example.com/img.png", "main"),
                text("The dialog will close.")
        ), 0);
        assertTrue(context.textAfter().contains("dialog"));
    }

    @Test
    void getImageContextReturnsEmptyStringsWhenNoContext() {
        Stage03Filter.ImageContext context = Stage03Filter.getImageContext(List.of(
                image("https://example.com/img.png", "main")
        ), 0);
        assertEquals("", context.heading());
        assertEquals("", context.textBefore());
        assertEquals("", context.textAfter());
    }

    @Test
    void getImageContextHeadingSearchStopsAtFirstFound() {
        Stage03Filter.ImageContext context = Stage03Filter.getImageContext(List.of(
                heading("Far Heading"),
                text("Some text in between that is long enough."),
                heading("Close Heading"),
                image("https://example.com/img.png", "main")
        ), 3);
        assertEquals("Close Heading", context.heading());
    }

    @Test
    void getImageContextTextAfterSkipsNonTextBlocks() {
        Stage03Filter.ImageContext context = Stage03Filter.getImageContext(List.of(
                image("https://example.com/img.png", "main"),
                heading("Next Section"),
                text("First real text after image.")
        ), 0);
        assertTrue(context.textAfter().contains("First real text"));
    }

    @Test
    void getImageContextFindsHeadingAndTextBefore() {
        Stage03Filter.ImageContext context = Stage03Filter.getImageContext(List.of(
                heading("Windows 11"),
                text("Right-click the folder to proceed."),
                image("https://example.com/img.png", "main")
        ), 2);
        assertEquals("Windows 11", context.heading());
        assertTrue(context.textBefore().contains("Right-click"));
    }

    @Test
    void filterBatchReturnsKeepFlags() {
        SkillOmniCommon.ChatClient client = (systemPrompt, userContent, temperature, maxTokens, extraBody) -> "[\"KEEP\", \"SKIP\", \"KEEP\"]";
        List<Boolean> result = Stage03Filter.filterBatch(
                client,
                List.of(
                        new Stage03Filter.FilterItem(image("https://example.com/1.png", "main"), "heading", "before", "after"),
                        new Stage03Filter.FilterItem(image("https://example.com/2.png", "main"), "heading", "before", "after"),
                        new Stage03Filter.FilterItem(image("https://example.com/3.png", "main"), "heading", "before", "after")
                ),
                List.of(
                        new SkillOmniCommon.AssetPayload(new byte[]{1}, "image/png"),
                        new SkillOmniCommon.AssetPayload(new byte[]{2}, "image/png"),
                        new SkillOmniCommon.AssetPayload(new byte[]{3}, "image/png")
                ),
                "How to use Chrome"
        );
        assertEquals(List.of(true, false, true), result);
    }

    @Test
    void filterBatchTreatsKeepCaseInsensitively() {
        SkillOmniCommon.ChatClient client = (systemPrompt, userContent, temperature, maxTokens, extraBody) -> "[\"keep\", \"KEEP\"]";
        List<Boolean> result = Stage03Filter.filterBatch(
                client,
                List.of(
                        new Stage03Filter.FilterItem(image("https://example.com/1.png", "main"), "", "", ""),
                        new Stage03Filter.FilterItem(image("https://example.com/2.png", "main"), "", "", "")
                ),
                List.of(
                        new SkillOmniCommon.AssetPayload(new byte[]{1}, "image/png"),
                        new SkillOmniCommon.AssetPayload(new byte[]{2}, "image/png")
                ),
                "Tutorial"
        );
        assertEquals(List.of(true, true), result);
    }

    @Test
    void filterBatchFallsBackToKeepAll() {
        SkillOmniCommon.ChatClient client = (systemPrompt, userContent, temperature, maxTokens, extraBody) -> {
            throw new RuntimeException("API error");
        };
        List<Boolean> result = Stage03Filter.filterBatch(
                client,
                List.of(
                        new Stage03Filter.FilterItem(image("https://example.com/1.png", "main"), "", "", ""),
                        new Stage03Filter.FilterItem(image("https://example.com/2.png", "main"), "", "", "")
                ),
                List.of(
                        new SkillOmniCommon.AssetPayload(new byte[]{1}, "image/png"),
                        new SkillOmniCommon.AssetPayload(new byte[]{2}, "image/png")
                ),
                "Tutorial"
        );
        assertEquals(List.of(true, true), result);
    }

    @Test
    void filterBatchFallsBackToKeepAllOnInvalidJson() {
        SkillOmniCommon.ChatClient client = (systemPrompt, userContent, temperature, maxTokens, extraBody) -> "not valid json";
        List<Boolean> result = Stage03Filter.filterBatch(
                client,
                List.of(new Stage03Filter.FilterItem(image("https://example.com/1.png", "main"), "", "", "")),
                List.of(new SkillOmniCommon.AssetPayload(new byte[]{1}, "image/png")),
                "Tutorial"
        );
        assertEquals(List.of(true), result);
    }

    @Test
    void filterBatchIncludesSubpageContextLabel() {
        Object[] capturedContent = new Object[1];
        SkillOmniCommon.ChatClient client = (systemPrompt, userContent, temperature, maxTokens, extraBody) -> {
            capturedContent[0] = userContent;
            return "[\"KEEP\"]";
        };
        Stage03Filter.filterBatch(
                client,
                List.of(new Stage03Filter.FilterItem(image("https://example.com/1.png", "subpage"), "", "", "")),
                List.of(new SkillOmniCommon.AssetPayload(new byte[]{1}, "image/png")),
                "Tutorial"
        );
        assertTrue(contentContainsText(capturedContent[0], "subpage"));
    }

    @Test
    void filterBatchIncludesHeadingContext() {
        Object[] capturedContent = new Object[1];
        SkillOmniCommon.ChatClient client = (systemPrompt, userContent, temperature, maxTokens, extraBody) -> {
            capturedContent[0] = userContent;
            return "[\"KEEP\"]";
        };
        Stage03Filter.filterBatch(
                client,
                List.of(new Stage03Filter.FilterItem(
                        image("https://example.com/1.png", "main"),
                        "Quick access",
                        "Click the folder",
                        ""
                )),
                List.of(new SkillOmniCommon.AssetPayload(new byte[]{1}, "image/png")),
                "File Explorer"
        );
        assertTrue(contentContainsText(capturedContent[0], "Quick access"));
    }

    @Test
    void filterImageBlocksDropsSkippedIndices() throws Exception {
        SkillOmniCommon.ChatClient client = (systemPrompt, userContent, temperature, maxTokens, extraBody) -> "[\"KEEP\", \"SKIP\"]";
        List<Map<String, Object>> blocks = List.of(
                heading("Windows 11"),
                image("https://example.com/1.png", "main"),
                image("https://example.com/2.png", "subpage"),
                text("Done")
        );
        Map<String, SkillOmniCommon.AssetPayload> fetched = Map.of(
                "https://example.com/1.png", new SkillOmniCommon.AssetPayload(new byte[]{1}, "image/png"),
                "https://example.com/2.png", new SkillOmniCommon.AssetPayload(new byte[]{2}, "image/png")
        );
        List<Map<String, Object>> result = Stage03Filter.filterImageBlocks(client, blocks, fetched, "Tutorial");
        assertEquals(1, result.stream().filter(b -> "image".equals(b.get("type"))).count());
    }

    private static Map<String, Object> image(String url, String source) {
        java.util.LinkedHashMap<String, Object> block = new java.util.LinkedHashMap<>();
        block.put("type", "image");
        block.put("url", url);
        block.put("alt", "");
        block.put("source", source);
        block.put("path", null);
        return block;
    }

    private static Map<String, Object> text(String value) {
        return Map.of("type", "text", "text", value, "source", "main");
    }

    private static Map<String, Object> heading(String value) {
        return Map.of("type", "heading", "level", 2, "text", value, "source", "main");
    }

    private static boolean contentContainsText(Object userContent, String expected) {
        if (!(userContent instanceof List<?> content)) {
            return false;
        }
        return content.stream().anyMatch(item -> {
            if (!(item instanceof Map<?, ?> part)) {
                return false;
            }
            Object value = part.get("text");
            return value != null && String.valueOf(value).contains(expected);
        });
    }
}
