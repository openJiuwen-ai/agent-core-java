/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_evaluator;

import com.openjiuwen.core.foundation.tool.ToolCard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code SkillEvaluator} in
 * {@code openjiuwen/dev_tools/skill_evaluator/skill_evaluator.py}.
 */
class SkillEvaluatorTest {
    @Test
    void createAgentRegistersSystemToolsSubagentAndPrompt(@TempDir Path tempDir) throws IOException {
        Path skillsRoot = tempDir.resolve("skills");
        Path skillDir = skillsRoot.resolve("sample_skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                description: Sample skill for evaluator tests.
                ---

                # Sample Skill
                """);
        Path filesBase = tempDir.resolve("files");
        Path outputDir = tempDir.resolve("outputs");

        Map<String, String> env = new LinkedHashMap<>();
        env.put("SKILLS_DIR", skillsRoot.toString());
        env.put("FILES_BASE_DIR", filesBase.toString());
        env.put("OUTPUT_DIR", outputDir.toString());
        env.put("MAX_ITERATIONS", "9");
        env.put("MODEL_PROVIDER", "openai");
        env.put("MODEL_NAME", "unit-model");
        env.put("API_KEY", "unit-key");
        env.put("API_BASE", "https://example.invalid");

        SkillEvaluator evaluator = new SkillEvaluator(env);
        evaluator.createAgent().toCompletableFuture().join();

        assertNotNull(evaluator.getAgent());
        Set<String> toolNames = evaluator.getTools().stream()
                .map(ToolCard::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("read_file", "execute_code", "execute_cmd", "write_file"), toolNames);
        assertTrue(evaluator.getAgent().getAbilityManager().get("create_subagent").isPresent());

        String renderedPrompt = evaluator.getAgent().buildRenderedSystemPrompt(Map.of(), Map.of());
        assertTrue(renderedPrompt.contains("All user-provided files are located at '" + filesBase + "'"));
        assertTrue(renderedPrompt.contains("Put all generated files into " + outputDir));
    }

    @Test
    void evaluateWithoutOutputPathPreservesPythonMissingAttributeSemantics() {
        SkillEvaluator evaluator = new SkillEvaluator(Map.of());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> evaluator.evaluate(Path.of("/mock/skills/my-skill"), "Run the full pipeline")
        );

        assertTrue(exception.getMessage().contains("_output_dir"));
    }

    @Test
    void evaluationQueryPreservesPythonTextAndRequirementConcatenation() {
        SkillEvaluator evaluator = new SkillEvaluator(Map.of());
        Path skillPath = Path.of("mock", "skills", "my-skill");
        Path outputPath = Path.of("mock", "outputs");

        String query = evaluator.buildEvaluationQuery(
                skillPath,
                "Focus on safety eval",
                outputPath
        );

        assertEquals("Help me evaluate the skill in the '" + skillPath + "'.\n"
                + "Save evaluation report to '" + outputPath + "' foler.Focus on safety eval", query);
    }
}
