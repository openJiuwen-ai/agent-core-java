/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_creator.skills.skill_omni_creation.scripts;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 5 generates SKILL.md from normalized blocks.
 * <p>
 * Mirrors Python's {@code openjiuwen/dev_tools/skill_creator/skills/skill_omni_creation/scripts/stage_05_generate.py}.
 */
public final class Stage05Generate {

    private Stage05Generate() {
    }

    public static List<Map<String, Object>> blocksForLlm(List<Map<String, Object>> blocks, Path skillDir) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> block : blocks) {
            if ("image".equals(block.get("type"))) {
                Object pathValue = block.get("path");
                if (pathValue == null) {
                    continue;
                }
                Path path = pathValue instanceof Path actualPath ? actualPath : Path.of(String.valueOf(pathValue));
                Path normalizedSkillDir = skillDir.normalize();
                Path normalizedPath = path.normalize();
                String relative = normalizedPath.startsWith(normalizedSkillDir)
                        ? normalizedSkillDir.relativize(normalizedPath).toString().replace('\\', '/')
                        : normalizedPath.getFileName().toString();
                Map<String, Object> image = new LinkedHashMap<>();
                image.put("type", "image");
                image.put("path", relative);
                image.put("alt", block.getOrDefault("alt", ""));
                image.put("source", block.getOrDefault("source", "main"));
                result.add(image);
            } else {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : block.entrySet()) {
                    if (!"path".equals(entry.getKey()) && !"url".equals(entry.getKey())) {
                        copy.put(entry.getKey(), entry.getValue());
                    }
                }
                result.add(copy);
            }
        }
        return result;
    }

    public static String callSkillAgent(
            SkillOmniCommon.ChatClient client,
            String title,
            List<Map<String, Object>> blocksLlm
    ) throws IOException, InterruptedException {
        String blocksJson;
        try {
            blocksJson = SkillOmniCommon.toJson(blocksLlm);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IOException("Failed to serialize blocks", exception);
        }
        String userMessage = "Title: " + title + "\n\n=== BLOCKS ===\n" + blocksJson;
        return client.chat(SkillOmniCommon.SKILL_PROMPT, userMessage, 0.2, 8192, Map.of()).trim();
    }

    public static String appendReferenceFiles(String markdown, List<Map<String, Object>> blocksLlm) {
        List<Map<String, Object>> images = blocksLlm.stream()
                .filter(block -> "image".equals(block.get("type")) && block.get("path") != null)
                .toList();
        if (images.isEmpty()) {
            return markdown;
        }
        List<String> lines = new ArrayList<>();
        lines.add("## Reference Files");
        lines.add("");
        lines.add("For visual reference, the following screenshots are available:");
        lines.add("");
        for (Map<String, Object> image : images) {
            String description = String.valueOf(image.getOrDefault("alt", "")).isBlank()
                    ? "screenshot"
                    : String.valueOf(image.get("alt"));
            lines.add("- `" + image.get("path") + "` - " + description);
        }
        return markdown.stripTrailing() + System.lineSeparator() + System.lineSeparator()
                + String.join(System.lineSeparator(), lines) + System.lineSeparator();
    }
}
