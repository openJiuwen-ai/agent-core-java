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
    void blocksForLlmPreservesOrder() {
        List<Map<String, Object>> result = Stage05Generate.blocksForLlm(List.of(
                Map.of("type", "heading", "level", 2, "text", "Step", "source", "main"),
                Map.of("type", "text", "text", "Do this.", "source", "main"),
                Map.of("type", "image", "path", SKILL_DIR.resolve("references").resolve("img_00.png"), "alt", "x", "source", "main")
        ), SKILL_DIR);
        assertEquals(List.of("heading", "text", "image"), result.stream().map(b -> String.valueOf(b.get("type"))).toList());
    }

    @Test
    void callSkillAgentReturnsTrimmedContent() throws Exception {
        SkillOmniCommon.ChatClient client = (systemPrompt, userContent, temperature, maxTokens, extraBody) -> "  ## My Skill\n1. Step one  ";
        assertEquals("## My Skill\n1. Step one", Stage05Generate.callSkillAgent(client, "My Skill", List.of()));
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
    void appendReferenceFilesDoesNothingWithoutImages() {
        String markdown = "# Skill\n\n1. Step one.";
        assertEquals(markdown, Stage05Generate.appendReferenceFiles(markdown, List.of(
                Map.of("type", "text", "text", "hello", "source", "main")
        )));
    }
}
