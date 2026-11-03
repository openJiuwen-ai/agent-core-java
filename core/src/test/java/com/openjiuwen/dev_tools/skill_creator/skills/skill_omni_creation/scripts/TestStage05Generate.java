/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_creator.skills.skill_omni_creation.scripts;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code tests/unit_tests/dev_tools/agent_builder/skill_omni_creation/test_stage05_generate.py}.
 */
class TestStage05Generate {

    private static final Path SKILL_DIR = Path.of("/skills/001_my_skill");

    @Test
    void blocksForLlmConvertsImagePathToRelative() {
        List<Map<String, Object>> result = Stage05Generate.blocksForLlm(List.of(
                Map.of("type", "image", "path", SKILL_DIR.resolve("references").resolve("img_00.png"), "alt", "x", "source", "main")
        ), SKILL_DIR);
        assertEquals("references/img_00.png", result.get(0).get("path"));
    }

    @Test
    void blocksForLlmSkipsImageWithoutPath() {
        java.util.LinkedHashMap<String, Object> block = new java.util.LinkedHashMap<>();
        block.put("type", "image");
        block.put("path", null);
        block.put("alt", "x");
        block.put("source", "main");
        assertTrue(Stage05Generate.blocksForLlm(List.of(block), SKILL_DIR).isEmpty());
    }

    @Test
    void blocksForLlmStripsUrlFieldFromImage() {
        Map<String, Object> block = new java.util.LinkedHashMap<>();
        block.put("type", "image");
        block.put("url", "https://example.com/img.png");
        block.put("path", SKILL_DIR.resolve("references").resolve("img_00.png"));
        block.put("alt", "x");
        block.put("source", "main");

        List<Map<String, Object>> result = Stage05Generate.blocksForLlm(List.of(block), SKILL_DIR);

        assertFalse(result.get(0).containsKey("url"));
        assertEquals("references/img_00.png", result.get(0).get("path"));
    }

    @Test
    void blocksForLlmPassesTextBlockThrough() {
        Map<String, Object> block = Map.of("type", "text", "text", "Click the button.", "source", "main");

        List<Map<String, Object>> result = Stage05Generate.blocksForLlm(List.of(block), SKILL_DIR);

        assertEquals("Click the button.", result.get(0).get("text"));
    }

    @Test
    void blocksForLlmStripsPathFieldFromTextBlock() {
        Map<String, Object> block = new java.util.LinkedHashMap<>();
        block.put("type", "text");
        block.put("text", "hello");
        block.put("source", "main");
        block.put("path", null);

        List<Map<String, Object>> result = Stage05Generate.blocksForLlm(List.of(block), SKILL_DIR);

        assertFalse(result.get(0).containsKey("path"));
    }

    @Test
    void blocksForLlmPassesHeadingBlockThrough() {
        List<Map<String, Object>> result = Stage05Generate.blocksForLlm(List.of(
                Map.of("type", "heading", "level", 2, "text", "Windows 11", "source", "main")
        ), SKILL_DIR);

        assertEquals("heading", result.get(0).get("type"));
        assertEquals("Windows 11", result.get(0).get("text"));
        assertEquals(2, result.get(0).get("level"));
    }

    @Test
    void blocksForLlmPreservesOrder() {
        List<Map<String, Object>> result = Stage05Generate.blocksForLlm(List.of(
                Map.of("type", "heading", "level", 2, "text", "Step", "source", "main"),
                Map.of("type", "text", "text", "Do this.", "source", "main"),
                Map.of("type", "image", "path", SKILL_DIR.resolve("references").resolve("img_00.png"), "alt", "x", "source", "main")
        ), SKILL_DIR);
        assertEquals(List.of("heading", "text", "image"), result.stream().map(b -> String.valueOf(b.get("type"))).toList());
    }

    @Test
    void blocksForLlmFallsBackToFilenameWhenPathIsNotRelative() {
        List<Map<String, Object>> result = Stage05Generate.blocksForLlm(List.of(
                Map.of("type", "image", "path", Path.of("/other/dir/img_00.png"), "alt", "x", "source", "main")
        ), SKILL_DIR);

        assertEquals("img_00.png", result.get(0).get("path"));
    }

    @Test
    void callSkillAgentReturnsTrimmedContent() throws Exception {
        SkillOmniCommon.ChatClient client = (systemPrompt, userContent, temperature, maxTokens, extraBody) -> "  ## My Skill\n1. Step one  ";
        assertEquals("## My Skill\n1. Step one", Stage05Generate.callSkillAgent(client, "My Skill", List.of()));
    }

    @Test
    void callSkillAgentPassesTitleAndBlocksInUserMessage() throws Exception {
        RecordingChatClient client = new RecordingChatClient("output");
        List<Map<String, Object>> blocks = List.of(
                Map.of("type", "heading", "level", 2, "text", "Windows 11", "source", "main")
        );

        Stage05Generate.callSkillAgent(client, "File Explorer in Windows", blocks);

        assertTrue(String.valueOf(client.userContent).contains("File Explorer in Windows"));
        assertTrue(String.valueOf(client.userContent).contains("Windows 11"));
        assertTrue(String.valueOf(client.userContent).contains("BLOCKS"));
    }

    @Test
    void callSkillAgentUsesSkillPromptAsSystemMessage() throws Exception {
        RecordingChatClient client = new RecordingChatClient("output");

        Stage05Generate.callSkillAgent(client, "Title", List.of());

        assertEquals(SkillOmniCommon.SKILL_PROMPT, client.systemPrompt);
        assertEquals(0.2d, client.temperature);
        assertEquals(8192, client.maxTokens);
    }

    @Test
    void appendReferenceFilesAppendsWhenImagesPresent() {
        String result = Stage05Generate.appendReferenceFiles(
                "# Skill\n\n1. Step one.",
                List.of(Map.of("type", "image", "path", "references/img_00.jpg", "alt", "screenshot", "source", "main"))
        );
        assertTrue(result.contains("## Reference Files"));
        assertTrue(result.contains("references/img_00.jpg"));
    }

    @Test
    void appendReferenceFilesUsesAltAsDescription() {
        String result = Stage05Generate.appendReferenceFiles("# Skill", List.of(
                Map.of("type", "image", "path", "references/img_00.jpg", "alt", "Click save button", "source", "main")
        ));

        assertTrue(result.contains("Click save button"));
    }

    @Test
    void appendReferenceFilesUsesScreenshotFallbackWhenAltBlank() {
        String result = Stage05Generate.appendReferenceFiles("# Skill", List.of(
                Map.of("type", "image", "path", "references/img_00.jpg", "alt", "", "source", "main")
        ));

        assertTrue(result.contains("screenshot"));
    }

    @Test
    void appendReferenceFilesDoesNothingWithoutImages() {
        String markdown = "# Skill\n\n1. Step one.";
        assertEquals(markdown, Stage05Generate.appendReferenceFiles(markdown, List.of(
                Map.of("type", "text", "text", "hello", "source", "main")
        )));
    }

    @Test
    void appendReferenceFilesDoesNotAppendWhenImagesHaveNoPath() {
        Map<String, Object> block = new java.util.LinkedHashMap<>();
        block.put("type", "image");
        block.put("path", null);
        block.put("alt", "x");
        block.put("source", "main");

        String result = Stage05Generate.appendReferenceFiles("# Skill", List.of(block));

        assertFalse(result.contains("## Reference Files"));
        assertEquals("# Skill", result);
    }

    @Test
    void appendReferenceFilesListsAllImages() {
        String result = Stage05Generate.appendReferenceFiles("# Skill", List.of(
                Map.of("type", "image", "path", "references/img_00.jpg", "alt", "first", "source", "main"),
                Map.of("type", "image", "path", "references/img_01.jpg", "alt", "second", "source", "main")
        ));

        assertTrue(result.contains("img_00.jpg"));
        assertTrue(result.contains("img_01.jpg"));
    }

    @Test
    void appendReferenceFilesSectionComesAfterSkillContent() {
        String result = Stage05Generate.appendReferenceFiles("# Skill\n\n1. Step one.", List.of(
                Map.of("type", "image", "path", "references/img_00.jpg", "alt", "x", "source", "main")
        ));

        assertTrue(result.indexOf("Step one") < result.indexOf("Reference Files"));
    }

    private static final class RecordingChatClient implements SkillOmniCommon.ChatClient {
        private final String response;
        private String systemPrompt;
        private Object userContent;
        private double temperature;
        private int maxTokens;

        private RecordingChatClient(String response) {
            this.response = response;
        }

        @Override
        public String chat(String systemPrompt, Object userContent, double temperature, int maxTokens,
                           Map<String, Object> extraBody) {
            this.systemPrompt = systemPrompt;
            this.userContent = userContent;
            this.temperature = temperature;
            this.maxTokens = maxTokens;
            return response;
        }
    }
}
