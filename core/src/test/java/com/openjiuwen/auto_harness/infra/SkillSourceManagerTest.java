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
 * Supplemental parity tests for community skill source management.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/auto_harness/test_skill_source_manager.py}.
 */
class SkillSourceManagerTest {

    @TempDir
    private Path tempDir;

    @Test
    void testBaoyuUrl() {
        assertThat(SkillSourceManager.repoNameFromUrl("https://github.com/JimLiu/baoyu-skills.git"))
                .isEqualTo("JimLiu-baoyu-skills");
    }

    @Test
    void testGithubUrl() {
        assertThat(SkillSourceManager.repoNameFromUrl("https://github.com/anthropics/skills.git"))
                .isEqualTo("anthropics-skills");
    }

    @Test
    void testNoGitSuffix() {
        assertThat(SkillSourceManager.repoNameFromUrl("https://github.com/owner/repo"))
                .isEqualTo("owner-repo");
    }

    @Test
    void testSingleSegment() {
        assertThat(SkillSourceManager.repoNameFromUrl("repo")).isEqualTo("repo");
    }

    @Test
    void testMissingFile() {
        assertThat(SkillSourceManager.loadSkillDescription(tempDir.resolve("missing").resolve("SKILL.md")))
                .isEmpty();
    }

    @Test
    void testNoFrontmatter() throws Exception {
        Path skillMd = writeRawSkill("raw", "# Just markdown\nNo frontmatter.");

        assertThat(SkillSourceManager.loadSkillDescription(skillMd)).isEmpty();
    }

    @Test
    void testWithFrontmatter() throws Exception {
        Path skillMd = writeSkill("anthropics-skills", "pptx", """
                ---
                name: pptx
                description: Create PowerPoint presentations
                ---
                # PPTX Skill
                """);

        assertThat(SkillSourceManager.loadSkillDescription(skillMd))
                .isEqualTo("Create PowerPoint presentations");
    }

    @Test
    void testAddMissingFields() throws Exception {
        Path skillMd = writeRawSkill("pptx", """
                ---
                name: pptx
                ---
                # PPTX
                """);
        SkillSourceManager.patchSkillFrontmatter(skillMd, "pptx");

        assertThat(Files.readString(skillMd))
                .contains("description:")
                .contains("Community skill: pptx");
    }

    @Test
    void testCompleteFrontmatterNoPatch() throws Exception {
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
    void testNoFrontmatterAtAll() throws Exception {
        Path bare = writeRawSkill("bare", "# Bare skill\nContent.");
        SkillSourceManager.patchSkillFrontmatter(bare, "bare");

        assertThat(Files.readString(bare))
                .startsWith("---")
                .contains("name: bare")
                .contains("description:");
    }

    @Test
    void testScanEmptyCache() throws Exception {
        Files.createDirectories(cacheDir());

        Map<String, SkillSourceManager.SkillMatch> result = SkillSourceManager.scanSkills(config(
                List.of("https://github.com/nonexistent/repo.git")
        ));

        assertThat(result).isEmpty();
    }

    @Test
    void testScanFindsSkills() throws Exception {
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
    void testCopySkillNotFound() throws Exception {
        Path extensionRoot = Files.createDirectories(tempDir.resolve("ext_root2"));

        Optional<Path> result = SkillSourceManager.copySkillToExtension(
                "nonexistent",
                extensionRoot,
                config(List.of())
        );

        assertThat(result).isEmpty();
    }

    @Test
    void testCopySkillSuccess() throws Exception {
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
    }

    @Test
    void testEmptyWhenNoCache() {
        AutoHarnessConfig config = config(List.of("https://github.com/nonexistent/repo.git"));

        assertThat(SkillSourceManager.communitySkillCacheSkillDirs(config)).isEmpty();
    }

    @Test
    void testReturnsExistingDirs() throws Exception {
        Files.createDirectories(cacheDir().resolve("anthropics-skills"));

        AutoHarnessConfig config = config(List.of("https://github.com/anthropics/skills.git"));

        assertThat(SkillSourceManager.communitySkillCacheSkillDirs(config))
                .hasSize(1)
                .first()
                .asString()
                .contains("anthropics-skills");
    }

    @Test
    void testFormatEmptyCache() {
        assertThat(SkillSourceManager.formatCommunitySkillList(config(List.of())))
                .contains("\u65e0");
    }

    @Test
    void testFormatWithSkills() throws Exception {
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
    void testCommunitySource() {
        ExtensionDesign design = new ExtensionDesign();
        design.setExtensionName("pptx_gen");
        design.setSkillSource("community:pptx");

        assertThat(design.getSkillSource()).isEqualTo("community:pptx");
    }

    @Test
    void testDefaultRepos() {
        AutoHarnessConfig config = new AutoHarnessConfig();

        assertThat(config.getCommunitySkillRepos()).hasSize(2);
        assertThat(config.getCommunitySkillRepos().get(0)).contains("anthropics");
        assertThat(config.getCommunitySkillRepos().get(1)).contains("baoyu-skills");
    }

    @Test
    void testDefaultValues() {
        ExtensionDesign design = new ExtensionDesign();

        assertThat(design.getSkillSource()).isEqualTo("");
    }

    @Test
    void testFromDict() {
        AutoHarnessConfig config = AutoHarnessConfig.loadFromDict(Map.of(
                "data_dir", "/tmp/ah",
                "community_skill_repos", List.of("https://github.com/test/skills.git"),
                "community_skill_cache_dir", "/tmp/skills-cache"
        ));

        assertThat(config.getCommunitySkillRepos()).containsExactly("https://github.com/test/skills.git");
        assertThat(config.getCommunitySkillCacheDir()).isEqualTo("/tmp/skills-cache");
        assertThat(config.getResolvedCommunitySkillCacheDir()).isEqualTo("/tmp/skills-cache");
    }

    @Test
    void testValidateMissingDescription() throws Exception {
        Path skillMd = writeRawSkill("no_desc", """
                ---
                name: pptx
                ---
                # PPTX Skill
                """);

        List<String> errors = RuntimeExtensionStaticChecks.validateSkillFrontmatter(skillMd);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("description");
    }

    @Test
    void testValidateMissingName() throws Exception {
        Path skillMd = writeRawSkill("no_name", """
                ---
                description: Create PowerPoint presentations
                ---
                # PPTX Skill
                """);

        List<String> errors = RuntimeExtensionStaticChecks.validateSkillFrontmatter(skillMd);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("name");
    }

    @Test
    void testValidateNoFrontmatter() throws Exception {
        Path skillMd = writeRawSkill("bare", "# Just markdown");

        List<String> errors = RuntimeExtensionStaticChecks.validateSkillFrontmatter(skillMd);

        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).contains("frontmatter");
    }

    @Test
    void testValidateValidFrontmatter() throws Exception {
        Path skillMd = writeRawSkill("pptx", """
                ---
                name: pptx
                description: Create PowerPoint presentations
                ---
                # PPTX Skill
                """);

        assertThat(RuntimeExtensionStaticChecks.validateSkillFrontmatter(skillMd)).isEmpty();
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
