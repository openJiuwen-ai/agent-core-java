/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import com.openjiuwen.auto_harness.schema.RuntimeExtensionArtifact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeExtensionStaticChecksTest {

    @TempDir
    private Path tempDir;

    @Test
    void resultDefaultsUseEmptyErrorsList() {
        ExtStaticCheckResult result = new ExtStaticCheckResult();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getRailsCount()).isZero();
        assertThat(result.getToolsCount()).isZero();
    }

    @Test
    void validateSkillFrontmatterAcceptsNameAndDescription() throws Exception {
        Path skill = writeSkill("valid", """
                ---
                name: pptx
                description: Create PowerPoint presentations
                ---
                # PPTX Skill
                """);

        assertThat(RuntimeExtensionStaticChecks.validateSkillFrontmatter(skill)).isEmpty();
    }

    @Test
    void validateSkillFrontmatterReportsMissingName() throws Exception {
        Path skill = writeSkill("no_name", """
                ---
                description: Create PowerPoint presentations
                ---
                # PPTX Skill
                """);

        List<String> errors = RuntimeExtensionStaticChecks.validateSkillFrontmatter(skill);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("name");
    }

    @Test
    void validateSkillFrontmatterReportsNoFrontmatter() throws Exception {
        Path skill = writeSkill("bare", "# Just markdown");

        List<String> errors = RuntimeExtensionStaticChecks.validateSkillFrontmatter(skill);

        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).contains("frontmatter");
    }

    @Test
    void runStaticChecksRequiresManifestFile() {
        RuntimeExtensionArtifact artifact = RuntimeExtensionArtifact.builder()
                .extensionName("demo")
                .runtimePath(tempDir.resolve("runtime").toString())
                .configPath(tempDir.resolve("missing.yaml").toString())
                .build();

        assertThatThrownBy(() -> RuntimeExtensionStaticChecks.runStaticChecksAgainstRuntime(artifact, "session-1"))
                .isInstanceOf(NoSuchFileException.class)
                .hasMessageContaining("Missing extension manifest");
    }

    private Path writeSkill(String name, String content) throws Exception {
        Path skill = tempDir.resolve(name).resolve("SKILL.md");
        Files.createDirectories(skill.getParent());
        Files.writeString(skill, content);
        return skill;
    }
}
