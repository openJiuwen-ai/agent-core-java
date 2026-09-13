
package com.openjiuwen.dev_tools.skill_evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class SkillEvaluatorCompatibilityTest {
    @Test
    void buildEvaluationQueryCarriesSkillPathOutputDirAndRequirement() {
        Path skillPath = Path.of("/tmp/demo-skill");
        Path outputDir = Path.of("/tmp/evals");

        String query =
            SkillEvaluator.buildEvaluationQuery(skillPath, outputDir, "Focus on trigger clarity and safety.");

        assertThat(query).contains(skillPath.toString());
        assertThat(query).contains(outputDir.toString());
        assertThat(query).contains("Focus on trigger clarity and safety.");
    }

    @Test
    void resolveReportDirFallsBackToConfiguredOutputDir() {
        Path fallback = Path.of("/tmp/fallback-evals");

        Path resolved = SkillEvaluator.resolveReportDir(null, fallback);

        assertThat(resolved).isEqualTo(fallback.toAbsolutePath().normalize());
    }

    @Test
    void defaultSkillsDirResolvesBundledEvaluatorSkills() {
        Path resolved = SkillEvaluator.resolveDefaultSkillsDir();

        assertThat(resolved).isDirectory();
        assertThat(resolved.resolve("skill_evaluation").resolve("SKILL.md")).isRegularFile();
    }

    @Test
    void configCopyRetainsPromptTemplateHeadersAndLimits() {
        ReActAgentConfig source = ReActAgentConfig.builder().modelName("demo-model").modelProvider("openai")
                .apiKey("demo-key").apiBase("https://example.com/v1")
                .promptTemplate(List.of(Map.of("role", "system", "content", "hello")))
                .customHeaders(Map.of("x-test", "1")).maxIterations(17).build();

        ReActAgentConfig copy = SkillEvaluator.copyConfig(source);

        assertThat(copy.getModelName()).isEqualTo("demo-model");
        assertThat(copy.getPromptTemplate()).containsExactly(Map.of("role", "system", "content", "hello"));
        assertThat(copy.getCustomHeaders()).containsEntry("x-test", "1");
        assertThat(copy.getMaxIterations()).isEqualTo(17);
        assertThat(copy).isNotSameAs(source);
        assertThat(copy.getPromptTemplate()).isNotSameAs(source.getPromptTemplate());
    }

    @Test
    @DisplayName("目标技能必须使用 SKILLS_DIR 内的相对路径")
    void targetSkillMustBeRelativeAndWithinConfiguredSkillsRoot(@TempDir Path tempDir) throws IOException {
        Path skillsRoot = Files.createDirectories(tempDir.resolve("skills"));
        Path safeSkill = Files.createDirectories(skillsRoot.resolve("safe-skill"));
        Files.writeString(safeSkill.resolve("SKILL.md"), "---\ndescription: Safe skill\n---");

        assertThat(SkillEvaluator.resolveTargetSkillPath("safe-skill", skillsRoot))
                .isEqualTo(safeSkill.toRealPath());
        assertThatThrownBy(() -> SkillEvaluator.resolveTargetSkillPath(safeSkill.toString(), skillsRoot))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> SkillEvaluator.resolveTargetSkillPath("../outside", skillsRoot))
                .isInstanceOf(SecurityException.class);

        Path outsideSkill = Files.createDirectories(tempDir.resolve("outside-skill"));
        Files.writeString(outsideSkill.resolve("SKILL.md"), "---\ndescription: Outside skill\n---");
        Files.createSymbolicLink(skillsRoot.resolve("linked-skill"), outsideSkill);
        assertThatThrownBy(() -> SkillEvaluator.resolveTargetSkillPath("linked-skill", skillsRoot))
                .isInstanceOf(SecurityException.class);
    }
}
