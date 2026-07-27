/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.skills;

import com.openjiuwen.core.sys_operation.Cwd;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.local.LocalFsOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SkillManager incremental refresh and snapshot signature.
 */
class SkillManagerIncrementalTest {

    @TempDir
    private Path tempDir;

    private SkillManager manager;

    @BeforeEach
    void setUp() {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        manager = new SkillManager("test-sysop", id -> new LocalFsOperation(
                "fs", OperationMode.LOCAL, "local fs operation", null));
    }

    @AfterEach
    void tearDown() {
        Cwd.clear();
    }

    @Test
    void testRegisterLoadsSkillFromDirectory() throws IOException {
        Path skill1Dir = tempDir.resolve("alpha_skill");
        Files.createDirectories(skill1Dir);
        Files.writeString(skill1Dir.resolve("SKILL.md"), "---\ndescription: Alpha skill\n---\n# Alpha");

        manager.register(List.of(skill1Dir), true);

        assertThat(manager.count()).isEqualTo(1);
        assertThat(manager.has("alpha_skill")).isTrue();

        Path skill2Dir = tempDir.resolve("beta_skill");
        Files.createDirectories(skill2Dir);
        Files.writeString(skill2Dir.resolve("SKILL.md"), "---\ndescription: Beta skill\n---\n# Beta");

        manager.register(List.of(skill2Dir), true);

        assertThat(manager.count()).isEqualTo(2);
        assertThat(manager.has("alpha_skill")).isTrue();
        assertThat(manager.has("beta_skill")).isTrue();
    }

    @Test
    void testRegisterReloadsUpdatedSkill() throws IOException {
        Path skillDir = tempDir.resolve("update_skill");
        Files.createDirectories(skillDir);
        Path skillMd = skillDir.resolve("SKILL.md");
        Files.writeString(skillMd, "---\ndescription: Original desc\n---\n# Original");

        manager.register(List.of(skillDir), true);
        assertThat(manager.get("update_skill").getDescription()).isEqualTo("Original desc");

        Files.writeString(skillMd, "---\ndescription: Updated desc\n---\n# Updated");

        forceMtimeChange(skillMd);

        manager.register(List.of(skillDir), true);
        assertThat(manager.get("update_skill").getDescription()).isEqualTo("Updated desc");
    }

    @Test
    void testUnregisterRemovesSkill() throws IOException {
        Path skillDir = tempDir.resolve("stale_skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "---\ndescription: Stale\n---\n# Stale");

        manager.register(List.of(skillDir), true);
        assertThat(manager.has("stale_skill")).isTrue();

        manager.unregister("stale_skill");
        assertThat(manager.has("stale_skill")).isFalse();
        assertThat(manager.count()).isZero();
    }

    @Test
    void testGetNamesReturnsRegisteredSkillNames() throws IOException {
        Path skill1Dir = tempDir.resolve("skill_a");
        Files.createDirectories(skill1Dir);
        Files.writeString(skill1Dir.resolve("SKILL.md"), "---\ndescription: A\n---");

        Path skill2Dir = tempDir.resolve("skill_b");
        Files.createDirectories(skill2Dir);
        Files.writeString(skill2Dir.resolve("SKILL.md"), "---\ndescription: B\n---");

        manager.register(List.of(skill1Dir, skill2Dir), true);

        List<String> names = manager.getNames();
        assertThat(names).hasSize(2);
        assertThat(names).contains("skill_a");
        assertThat(names).contains("skill_b");
    }

    @Test
    void testRegisterSkipsNonexistentRoot() throws IOException {
        List<Skill> result = manager.register(List.of(), true);
        assertThat(result).isEmpty();
    }

    @Test
    void testClearAll() throws IOException {
        Path skillDir = tempDir.resolve("clear_skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "---\ndescription: Clear\n---");

        manager.register(List.of(skillDir), true);
        assertThat(manager.count()).isEqualTo(1);

        manager.clear();
        assertThat(manager.count()).isZero();
        assertThat(manager.getAll()).isEmpty();
    }

    @Test
    void testGetAllInOrder() throws IOException {
        Path skillBDir = tempDir.resolve("b_skill");
        Files.createDirectories(skillBDir);
        Files.writeString(skillBDir.resolve("SKILL.md"), "---\ndescription: B\n---");

        Path skillADir = tempDir.resolve("a_skill");
        Files.createDirectories(skillADir);
        Files.writeString(skillADir.resolve("SKILL.md"), "---\ndescription: A\n---");

        manager.register(List.of(skillBDir, skillADir), true);

        List<Skill> inOrder = manager.getAll().stream()
                .sorted(Comparator.comparing(Skill::getName))
                .toList();
        assertThat(inOrder).hasSize(2);
        assertThat(inOrder.get(0).getName()).isEqualTo("a_skill");
        assertThat(inOrder.get(1).getName()).isEqualTo("b_skill");
    }

    @Test
    void testFindSkillByDirectory() throws IOException {
        Path skillDir = tempDir.resolve("find_skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "---\ndescription: Find\n---");

        manager.register(List.of(skillDir), true);

        String absPath = skillDir.toAbsolutePath().normalize().toString();
        Skill found = manager.getAll().stream()
                .filter(s -> s.getDirectory() != null && s.getDirectory().toAbsolutePath().normalize().toString().equals(absPath))
                .findFirst()
                .orElse(null);
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
