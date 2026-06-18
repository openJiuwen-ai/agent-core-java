/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesign;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for community skill source management.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/auto_harness/test_skill_source_manager.py}
 * for {@code openjiuwen/auto_harness/infra/skill_source_manager.py}.
 */
class SkillSourceManagerTest {

    @TempDir
    private Path tempDir;

    @Test
    void repoNameFromUrlMatchesPythonCases() {
        assertThat(SkillSourceManager.repoNameFromUrl("https://github.com/anthropics/skills.git"))
                .isEqualTo("anthropics-skills");
        assertThat(SkillSourceManager.repoNameFromUrl("https://github.com/JimLiu/baoyu-skills.git"))
                .isEqualTo("JimLiu-baoyu-skills");
        assertThat(SkillSourceManager.repoNameFromUrl("https://github.com/owner/repo"))
                .isEqualTo("owner-repo");
        assertThat(SkillSourceManager.repoNameFromUrl("repo")).isEqualTo("repo");
    }

    @Test
    void loadSkillDescriptionReadsYamlFrontmatter() throws Exception {
        Path skillMd = writeSkill("anthropics-skills", "pptx", """
                ---
                name: pptx
                description: Create PowerPoint presentations
                ---
                # PPTX Skill
                """);

        assertThat(SkillSourceManager.loadSkillDescription(skillMd))
                .isEqualTo("Create PowerPoint presentations");
        assertThat(SkillSourceManager.loadSkillDescription(tempDir.resolve("missing").resolve("SKILL.md")))
                .isEmpty();
    }

    @Test
    void patchSkillFrontmatterAddsMissingFieldsAndKeepsCompleteFileUnchanged() throws Exception {
        Path missingDescription = writeRawSkill("pptx", """
                ---
                name: pptx
                ---
                # PPTX
                """);
        SkillSourceManager.patchSkillFrontmatter(missingDescription, "pptx");
        assertThat(Files.readString(missingDescription))
                .contains("description:")
                .contains("Community skill: pptx");

        Path bare = writeRawSkill("bare", "# Bare skill\nContent.");
        SkillSourceManager.patchSkillFrontmatter(bare, "bare");
        assertThat(Files.readString(bare))
                .startsWith("---")
                .contains("name: bare")
                .contains("description:");

        String complete = """
                ---
                name: pdf
                description: Generate PDF files
                ---
                # PDF Skill
                """;
        Path completePath = writeRawSkill("pdf", complete);
        SkillSourceManager.patchSkillFrontmatter(completePath, "pdf");
        assertThat(Files.readString(completePath)).isEqualTo(complete);
    }

    @Test
    void scanSkillsFindsSkillsAndSkipsNonSkillDirs() throws Exception {
        writeSkill("anthropics-skills", "pptx", """
                ---
                name: pptx
                description: Create PowerPoint presentations
                ---
                # PPTX Skill
                """);
        Files.createDirectories(cacheDir().resolve("anthropics-skills").resolve("not_a_skill"));

        Map<String, SkillSourceManager.SkillMatch> result = SkillSourceManager.scanSkills(config(
                List.of("https://github.com/anthropics/skills.git")
        ));

        assertThat(result).containsKey("pptx");
        assertThat(result.get("pptx").description()).isEqualTo("Create PowerPoint presentations");
        assertThat(result).doesNotContainKey("not_a_skill");
    }

    @Test
    void copySkillToExtensionCopiesAndPatchesSkill() throws Exception {
        writeSkill("anthropics-skills", "pptx", """
                ---
                name: pptx
                description: Create PowerPoint presentations
                ---
                # PPTX Skill
                """);
        Path extensionRoot = Files.createDirectories(tempDir.resolve("ext_root"));

        Optional<Path> result = SkillSourceManager.copySkillToExtension(
                "pptx",
                extensionRoot,
                config(List.of("https://github.com/anthropics/skills.git"))
        );

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getFileName().toString()).isEqualTo("pptx");
        assertThat(extensionRoot.resolve("skills").resolve("pptx").resolve("SKILL.md")).isRegularFile();
        assertThat(SkillSourceManager.copySkillToExtension("missing", extensionRoot, config(List.of())))
                .isEmpty();
    }

    @Test
    void communitySkillCacheSkillDirsHandlesSkillsSubdirAndFlatRepos() throws Exception {
        Files.createDirectories(cacheDir().resolve("anthropics-skills"));
        Files.createDirectories(cacheDir().resolve("owner-repo").resolve("skills"));

        AutoHarnessConfig config = config(List.of(
                "https://github.com/anthropics/skills.git",
                "https://github.com/owner/repo.git"
        ));

        assertThat(SkillSourceManager.communitySkillCacheSkillDirs(config))
                .containsExactly(
                        cacheDir().resolve("anthropics-skills").toString(),
                        cacheDir().resolve("owner-repo").resolve("skills").toString()
                );
    }

    @Test
    void formatCommunitySkillListMatchesPythonEmptyAndNonEmptyText() throws Exception {
        assertThat(SkillSourceManager.formatCommunitySkillList(config(List.of()))).contains("无");

        writeSkill("anthropics-skills", "pptx", """
                ---
                name: pptx
                description: Create PowerPoint presentations
                ---
                # PPTX Skill
                """);

        assertThat(SkillSourceManager.formatCommunitySkillList(config(List.of("https://github.com/anthropics/skills.git"))))
                .contains("pptx")
                .contains("Create PowerPoint presentations");
    }

    @Test
    void schemaSkillSourceFieldsMirrorPythonDefaults() {
        ExtensionDesign design = new ExtensionDesign();
        assertThat(design.getSkillSource()).isEqualTo("");

        AutoHarnessConfig config = AutoHarnessConfig.loadFromDict(Map.of(
                "data_dir", "/tmp/ah",
                "community_skill_repos", List.of("https://github.com/test/skills.git"),
                "community_skill_cache_dir", "/tmp/skills-cache"
        ));
        assertThat(config.getCommunitySkillRepos()).containsExactly("https://github.com/test/skills.git");
        assertThat(config.getCommunitySkillCacheDir()).isEqualTo("/tmp/skills-cache");
        assertThat(config.getResolvedCommunitySkillCacheDir()).isEqualTo("/tmp/skills-cache");
        assertThat(new AutoHarnessConfig().getCommunitySkillRepos()).hasSize(2);
    }

    private Path writeSkill(String repoName, String skillName, String content) throws Exception {
        Path skillDir = Files.createDirectories(cacheDir().resolve(repoName).resolve(skillName));
        Path skillMd = skillDir.resolve("SKILL.md");
        Files.writeString(skillMd, content);
        return skillMd;
    }

    private Path writeRawSkill(String skillName, String content) throws Exception {
        Path skillDir = Files.createDirectories(tempDir.resolve("raw").resolve(skillName));
        Path skillMd = skillDir.resolve("SKILL.md");
        Files.writeString(skillMd, content);
        return skillMd;
    }

    private Path cacheDir() throws Exception {
        return Files.createDirectories(tempDir.resolve("skills-cache"));
    }

    private AutoHarnessConfig config(List<String> repos) {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir(tempDir.toString());
        config.setCommunitySkillRepos(repos);
        return config;
    }
}
