/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.core.singleagent.skills.SkillSource;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Tag("issue196-w5")
class SkillRepositoryTest {

    @TempDir private Path tempDir;

    @Test
    @Tag("TC-SKL-01")
    void threeLevelRepositories_loadIndependently() throws IOException {
        Path fsRoot = tempDir.resolve("fs");
        writeSkill(fsRoot, "fs_skill", "Filesystem skill.");
        Path wsRoot = tempDir.resolve("ws");
        writeSkill(wsRoot, "ws_skill", "Workspace skill.");

        FileSystemSkillRepository fsRepo = new FileSystemSkillRepository(fsRoot);
        WorkspaceSkillRepository wsRepo = new WorkspaceSkillRepository(wsRoot, "user1");

        assertThat(fsRepo.source()).isEqualTo(SkillSource.FILESYSTEM);
        assertThat(wsRepo.source()).isEqualTo(SkillSource.WORKSPACE);
        assertThat(fsRepo.loadAll()).hasSize(1);
        assertThat(fsRepo.loadAll().get(0).getSource()).isEqualTo(SkillSource.FILESYSTEM);
        assertThat(wsRepo.loadAll()).hasSize(1);
        assertThat(wsRepo.loadAll().get(0).getSource()).isEqualTo(SkillSource.WORKSPACE);
    }

    @Test
    @Tag("TC-SKL-02")
    void compositeRepository_workspaceOverridesFilesystem_thenFallsBack() throws IOException {
        Path fsRoot = tempDir.resolve("fs");
        writeSkill(fsRoot, "shared_skill", "Filesystem version.");
        Path wsRoot = tempDir.resolve("ws");
        writeSkill(wsRoot, "shared_skill", "Workspace version.");
        CompositeSkillRepository composite =
                new CompositeSkillRepository(
                        List.of(
                                new FileSystemSkillRepository(fsRoot),
                                new WorkspaceSkillRepository(wsRoot, "user1")));

        List<Skill> loaded = composite.loadAll();
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getDescription()).isEqualTo("Workspace version.");
        assertThat(loaded.get(0).getSource()).isEqualTo(SkillSource.WORKSPACE);

        Path emptyWsRoot = tempDir.resolve("ws_empty");
        Files.createDirectories(emptyWsRoot);
        CompositeSkillRepository afterRemoval =
                new CompositeSkillRepository(
                        List.of(
                                new FileSystemSkillRepository(fsRoot),
                                new WorkspaceSkillRepository(emptyWsRoot, "user1")));
        List<Skill> afterDelete = afterRemoval.loadAll();
        assertThat(afterDelete).hasSize(1);
        assertThat(afterDelete.get(0).getDescription()).isEqualTo("Filesystem version.");
        assertThat(afterDelete.get(0).getSource()).isEqualTo(SkillSource.FILESYSTEM);
    }

    @Test
    @Tag("TC-SKL-03")
    void filesystemRepository_discoversRecursivelyWithStableOrder() throws IOException {
        Path root = tempDir.resolve("multi");
        writeSkill(root.resolve("a"), "skill_a", "Skill A.");
        writeSkill(root.resolve("b").resolve("nested"), "skill_b", "Skill B.");
        writeSkill(root.resolve("c").resolve("deep").resolve("deeper"), "skill_c", "Skill C.");

        FileSystemSkillRepository repository = new FileSystemSkillRepository(root);
        List<Skill> first = repository.loadAll();
        List<Skill> second = repository.loadAll();

        assertThat(first).hasSize(3);
        assertThat(first)
                .extracting(Skill::getName)
                .containsExactly("skill_a", "skill_b", "skill_c");
        assertThat(second).hasSameSizeAs(first);
        for (int i = 0; i < first.size(); i++) {
            assertThat(second.get(i).getName()).isEqualTo(first.get(i).getName());
            assertThat(second.get(i).getDescription()).isEqualTo(first.get(i).getDescription());
        }
    }

    @Test
    void filesystemRepository_derivesStableRelativeSkillId() throws IOException {
        Path root = tempDir.resolve("ids");
        writeSkill(root.resolve("top"), "skill_top", "Top skill.");
        writeSkill(root.resolve("nested").resolve("deep"), "skill_deep", "Deep skill.");

        List<Skill> loaded = new FileSystemSkillRepository(root).loadAll();

        assertThat(loaded)
                .extracting(Skill::getId)
                .containsExactlyInAnyOrder("filesystem/top", "filesystem/nested/deep");
    }

    @Test
    void filesystemRepository_rejectsSymlinkOutsideRoot() throws IOException {
        Path root = tempDir.resolve("fs-symlink-root");
        Path outsideRoot = tempDir.resolve("fs-symlink-outside");
        writeSkill(root.resolve("inside_skill"), "inside_skill", "Inside skill.");
        writeSkill(outsideRoot, "outside_skill", "Outside skill.");

        try {
            Files.createSymbolicLink(root.resolve("linked_skill"), outsideRoot);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            assumeTrue(false, "Symbolic link creation is unavailable without platform privilege");
            return;
        }

        List<Skill> loaded = new FileSystemSkillRepository(root).loadAll();
        assertThat(loaded)
                .extracting(Skill::getName)
                .contains("inside_skill")
                .doesNotContain("outside_skill");
    }

    @Test
    void workspaceRepository_rejectsPathOutsideRoot(@TempDir Path outsideRoot) throws IOException {
        Path insideRoot = tempDir.resolve("ws");
        writeSkill(insideRoot, "inside_skill", "Inside skill.");
        writeSkill(outsideRoot, "outside_skill", "Outside skill.");

        WorkspaceSkillRepository repository = new WorkspaceSkillRepository(insideRoot, "user1");
        List<Skill> loaded = repository.loadAll();

        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getName()).isEqualTo("inside_skill");
    }

    @Test
    void skillSource_outputsCorrectLowercaseValues() {
        assertThat(SkillSource.CLASSPATH.value()).isEqualTo("classpath");
        assertThat(SkillSource.FILESYSTEM.value()).isEqualTo("filesystem");
        assertThat(SkillSource.WORKSPACE.value()).isEqualTo("workspace");
    }

    @Test
    void classpathRepository_loadsDirectoryResourcesAndKeepsSkillDirectory() {
        ClasspathSkillRepository repository = new ClasspathSkillRepository("issue196/skills");
        List<Skill> loaded = repository.loadAll();

        assertThat(repository.source()).isEqualTo(SkillSource.CLASSPATH);
        assertThat(loaded).hasSize(2);
        assertThat(loaded)
                .extracting(Skill::getName)
                .containsExactlyInAnyOrder("builtin_demo", "protected_builtin_demo");
        assertThat(loaded)
                .allSatisfy(
                        skill -> {
                            assertThat(skill.getDirectory()).isNotNull();
                            assertThat(skill.getDirectory().toString()).doesNotContain("SKILL.md");
                            assertThat(skill.getId()).isNotBlank();
                        });
    }

    private static void writeSkill(Path directory, String name, String description)
            throws IOException {
        Files.createDirectories(directory);
        Files.writeString(
                directory.resolve("SKILL.md"),
                """
                ---
                name: %s
                description: %s
                ---
                Skill body.
                """
                        .formatted(name, description));
    }
}
