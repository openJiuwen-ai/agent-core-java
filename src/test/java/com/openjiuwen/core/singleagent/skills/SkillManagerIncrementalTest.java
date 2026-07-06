/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.skills;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SkillManager incremental refresh and snapshot signature.
 */
class SkillManagerIncrementalTest {

    private SkillManager manager;

    @BeforeEach
    void setUp() {
        manager = new SkillManager("test-sysop");
    }

    @Test
    void testRefreshIncrementallyOnlyLoadsNewSkill(@TempDir Path tempDir) throws IOException {
        Path skill1Dir = tempDir.resolve("alpha_skill");
        Files.createDirectories(skill1Dir);
        Files.writeString(skill1Dir.resolve("SKILL.md"), "---\ndescription: Alpha skill\n---\n# Alpha");

        manager.refreshIncrementally(List.of(tempDir));

        assertThat(manager.count()).isEqualTo(1);
        assertThat(manager.has("alpha_skill")).isTrue();
        assertThat(manager.get("alpha_skill").getUpdateAt()).isGreaterThan(0);

        Path skill2Dir = tempDir.resolve("beta_skill");
        Files.createDirectories(skill2Dir);
        Files.writeString(skill2Dir.resolve("SKILL.md"), "---\ndescription: Beta skill\n---\n# Beta");

        manager.refreshIncrementally(List.of(tempDir));

        assertThat(manager.count()).isEqualTo(2);
        assertThat(manager.has("alpha_skill")).isTrue();
        assertThat(manager.has("beta_skill")).isTrue();
    }

    @Test
    void testRefreshIncrementallyReloadsUpdatedSkillByMtime(@TempDir Path tempDir) throws IOException {
        Path skillDir = tempDir.resolve("update_skill");
        Files.createDirectories(skillDir);
        Path skillMd = skillDir.resolve("SKILL.md");
        Files.writeString(skillMd, "---\ndescription: Original desc\n---\n# Original");

        manager.refreshIncrementally(List.of(tempDir));
        assertThat(manager.get("update_skill").getDescription()).isEqualTo("Original desc");

        long originalMtime = skillMd.toFile().lastModified();

        Files.writeString(skillMd, "---\ndescription: Updated desc\n---\n# Updated");

        forceMtimeChange(skillMd);

        manager.refreshIncrementally(List.of(tempDir));
        assertThat(manager.get("update_skill").getDescription()).isEqualTo("Updated desc");
        assertThat(manager.get("update_skill").getUpdateAt()).isNotEqualTo(originalMtime);
    }

    @Test
    void testRefreshIncrementallyRemovesStaleSkill(@TempDir Path tempDir) throws IOException {
        Path skillDir = tempDir.resolve("stale_skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "---\ndescription: Stale\n---\n# Stale");

        manager.refreshIncrementally(List.of(tempDir));
        assertThat(manager.has("stale_skill")).isTrue();

        Files.deleteIfExists(skillDir.resolve("SKILL.md"));
        Files.deleteIfExists(skillDir);

        manager.refreshIncrementally(List.of(tempDir));
        assertThat(manager.has("stale_skill")).isFalse();
        assertThat(manager.count()).isZero();
    }

    @Test
    void testBuildSnapshotSignature(@TempDir Path tempDir) throws IOException {
        Path skill1Dir = tempDir.resolve("skill_a");
        Files.createDirectories(skill1Dir);
        Files.writeString(skill1Dir.resolve("SKILL.md"), "---\ndescription: A\n---");

        Path skill2Dir = tempDir.resolve("skill_b");
        Files.createDirectories(skill2Dir);
        Files.writeString(skill2Dir.resolve("SKILL.md"), "---\ndescription: B\n---");

        List<Map.Entry<String, Long>> signature = manager.buildSnapshotSignature(List.of(tempDir));

        assertThat(signature).hasSize(2);
        assertThat(signature.get(0).getKey()).contains("skill_a");
        assertThat(signature.get(1).getKey()).contains("skill_b");
        assertThat(signature.get(0).getValue()).isGreaterThan(0);
        assertThat(signature.get(1).getValue()).isGreaterThan(0);
    }

    @Test
    void testBuildSnapshotSignatureSkipsNonexistentRoot(@TempDir Path tempDir) {
        Path nonexistent = tempDir.resolve("no_such_dir");
        List<Map.Entry<String, Long>> signature = manager.buildSnapshotSignature(List.of(nonexistent));
        assertThat(signature).isEmpty();
    }

    @Test
    void testClearAll(@TempDir Path tempDir) throws IOException {
        Path skillDir = tempDir.resolve("clear_skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "---\ndescription: Clear\n---");

        manager.refreshIncrementally(List.of(tempDir));
        assertThat(manager.count()).isEqualTo(1);

        manager.clearAll();
        assertThat(manager.count()).isZero();
        assertThat(manager.getAll()).isEmpty();
        assertThat(manager.getAllInOrder()).isEmpty();
    }

    @Test
    void testGetAllInOrder(@TempDir Path tempDir) throws IOException {
        Path skillBDir = tempDir.resolve("b_skill");
        Files.createDirectories(skillBDir);
        Files.writeString(skillBDir.resolve("SKILL.md"), "---\ndescription: B\n---");

        Path skillADir = tempDir.resolve("a_skill");
        Files.createDirectories(skillADir);
        Files.writeString(skillADir.resolve("SKILL.md"), "---\ndescription: A\n---");

        manager.refreshIncrementally(List.of(tempDir));

        List<Skill> inOrder = manager.getAllInOrder();
        assertThat(inOrder).hasSize(2);
        assertThat(inOrder.get(0).getName()).isEqualTo("a_skill");
        assertThat(inOrder.get(1).getName()).isEqualTo("b_skill");
    }

    @Test
    void testFindSkillByDirectory(@TempDir Path tempDir) throws IOException {
        Path skillDir = tempDir.resolve("find_skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "---\ndescription: Find\n---");

        manager.refreshIncrementally(List.of(tempDir));

        String absPath = skillDir.toAbsolutePath().normalize().toString();
        Skill found = manager.findSkillByDirectory(absPath);
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("find_skill");
    }

    private void forceMtimeChange(Path file) throws IOException {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        file.toFile().setLastModified(file.toFile().lastModified() + 2000);
    }
}