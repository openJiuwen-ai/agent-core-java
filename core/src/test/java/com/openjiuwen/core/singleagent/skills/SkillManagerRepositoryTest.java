/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.singleagent.skills.repository.FileSystemSkillRepository;
import com.openjiuwen.core.singleagent.skills.repository.WorkspaceSkillRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tests Skill repository merging.
 *
 * @since 0.1.17
 */
class SkillManagerRepositoryTest {
    @TempDir
    private Path tempDir;

    @Test
    void setRepositories_getAllReturnsMergedSnapshot() throws IOException {
        Path fsRoot = tempDir.resolve("fs");
        writeSkill(fsRoot, "fs_skill", "Filesystem skill.");
        Path wsRoot = tempDir.resolve("ws");
        writeSkill(wsRoot, "ws_skill", "Workspace skill.");

        SkillManager manager = new SkillManager("test-op");
        manager.setRepositories(
                List.of(
                        new FileSystemSkillRepository(fsRoot),
                        new WorkspaceSkillRepository(wsRoot, "user1")));
        manager.refreshIncrementally(List.of());

        assertThat(manager.count()).isEqualTo(2);
        assertThat(manager.has("fs_skill")).isTrue();
        assertThat(manager.has("ws_skill")).isTrue();
        assertThat(manager.getAll().stream().map(Skill::getSource))
                .containsExactlyInAnyOrder(SkillSource.FILESYSTEM, SkillSource.WORKSPACE);
    }

    @Test
    void workspaceOverridesFilesystem_andStaleRemovedAfterRefresh() throws IOException {
        Path fsRoot = tempDir.resolve("fs");
        writeSkill(fsRoot, "shared_skill", "Filesystem version.");
        Path wsRoot = tempDir.resolve("ws");
        writeSkill(wsRoot, "shared_skill", "Workspace version.");
        writeSkill(wsRoot, "extra_skill", "Extra skill.");

        SkillManager manager = new SkillManager("test-op");
        manager.setRepositories(
                List.of(
                        new FileSystemSkillRepository(fsRoot),
                        new WorkspaceSkillRepository(wsRoot, "user1")));
        manager.refreshIncrementally(List.of());

        assertThat(manager.count()).isEqualTo(2);
        Skill sharedSkill = manager.get("shared_skill");
        assertThat(sharedSkill).isNotNull();
        assertThat(sharedSkill.getSource()).isEqualTo(SkillSource.WORKSPACE);
        assertThat(sharedSkill.getDescription()).isEqualTo("Workspace version.");

        manager.setRepositories(List.of(new FileSystemSkillRepository(fsRoot)));
        manager.refreshIncrementally(List.of());

        assertThat(manager.count()).isEqualTo(1);
        assertThat(manager.get("shared_skill").getDescription()).isEqualTo("Filesystem version.");
        assertThat(manager.has("extra_skill")).isFalse();
    }

    @Test
    void getAllReturnsImmutableCopy() throws IOException {
        Path fsRoot = tempDir.resolve("fs");
        writeSkill(fsRoot, "skill_a", "Skill A.");

        SkillManager manager = new SkillManager("test-op");
        manager.setRepositories(List.of(new FileSystemSkillRepository(fsRoot)));
        manager.refreshIncrementally(List.of());

        List<Skill> snapshot = manager.getAll();
        snapshot.clear();
        assertThat(manager.count()).isEqualTo(1);
    }

    @Test
    void skillConfigAppliesToRepositoriesSetBeforeAndAfterConfiguration() throws IOException {
        Path wsRoot = tempDir.resolve("ws");
        writeSkill(wsRoot, "normal_skill", "Normal skill.");
        Files.createDirectories(wsRoot.resolve("oversized_skill"));
        Files.write(wsRoot.resolve("oversized_skill").resolve("SKILL.md"), new byte[256]);
        SkillConfig config = new SkillConfig();
        config.setWorkspaceMaxBytes(128);

        SkillManager beforeRepositories = new SkillManager("test-op");
        beforeRepositories.setSkillConfig(config);
        beforeRepositories.setRepositories(List.of(new WorkspaceSkillRepository(wsRoot, "user1")));
        beforeRepositories.refreshIncrementally(List.of());

        SkillManager afterRepositories = new SkillManager("test-op");
        afterRepositories.setRepositories(List.of(new WorkspaceSkillRepository(wsRoot, "user1")));
        afterRepositories.setSkillConfig(config);
        afterRepositories.refreshIncrementally(List.of());

        assertThat(beforeRepositories.has("normal_skill")).isTrue();
        assertThat(beforeRepositories.has("oversized_skill")).isFalse();
        assertThat(afterRepositories.has("normal_skill")).isTrue();
        assertThat(afterRepositories.has("oversized_skill")).isFalse();
    }

    @Test
    void repeatedRefresh_producesStableOrder() throws IOException {
        Path fsRoot = tempDir.resolve("fs");
        writeSkill(fsRoot.resolve("b"), "skill_b", "Skill B.");
        writeSkill(fsRoot.resolve("a"), "skill_a", "Skill A.");

        SkillManager manager = new SkillManager("test-op");
        manager.setRepositories(List.of(new FileSystemSkillRepository(fsRoot)));
        manager.refreshIncrementally(List.of());
        List<String> firstOrder = manager.getAll().stream().map(Skill::getName).toList();
        manager.refreshIncrementally(List.of());
        List<String> secondOrder = manager.getAll().stream().map(Skill::getName).toList();

        assertThat(firstOrder).isEqualTo(secondOrder);
        Set<String> names = firstOrder.stream().collect(Collectors.toSet());
        assertThat(names).containsExactlyInAnyOrder("skill_a", "skill_b");
    }

    @Test
    void explicitPathSelectsLowerPrioritySameNameSkill() throws IOException {
        Path fsRoot = tempDir.resolve("fs");
        Path wsRoot = tempDir.resolve("ws");
        Path fsSkillDirectory = writeSkill(fsRoot, "shared_skill", "Filesystem version.");
        writeSkill(wsRoot, "shared_skill", "Workspace version.");

        SkillManager manager = new SkillManager("test-op");
        manager.setRepositories(
                List.of(
                        new FileSystemSkillRepository(fsRoot),
                        new WorkspaceSkillRepository(wsRoot, "user1")));
        manager.refreshIncrementally(List.of());

        Optional<Skill> selected = manager.getExplicitSkill(null, fsSkillDirectory.toString());
        assertThat(selected).isPresent();
        assertThat(selected.get().getSource()).isEqualTo(SkillSource.FILESYSTEM);
        assertThat(selected.get().getDescription()).isEqualTo("Filesystem version.");
    }

    @Test
    void explicitReferenceMatrixIsDeterministicAndProtectsLowerPrioritySkill()
            throws IOException {
        Path fsRoot = tempDir.resolve("fs");
        Path wsRoot = tempDir.resolve("ws");
        Path filesystemSkillDirectory =
                writeSkillWithId(fsRoot, "shared_skill", "Filesystem version.", "filesystem/shared_id");
        Path workspaceSkillDirectory =
                writeSkillWithId(wsRoot, "shared_skill", "Workspace version.", "workspace/shared_id");

        SkillManager manager = new SkillManager("test-op");
        manager.setRepositories(
                List.of(
                        new FileSystemSkillRepository(fsRoot),
                        new WorkspaceSkillRepository(wsRoot, "user1")));
        manager.refreshIncrementally(List.of());

        assertThat(manager.getExplicitSkill(null, null)).isEmpty();
        assertThat(manager.getExplicitSkill("unknown_id", null)).isEmpty();
        assertThat(manager.getExplicitSkill(null, wsRoot.resolve("missing_skill").toString()))
                .isEmpty();
        assertThat(manager.getExplicitSkill("filesystem/shared_id", workspaceSkillDirectory.toString()))
                .isEmpty();
        assertThat(manager.getExplicitSkill("workspace/shared_id", workspaceSkillDirectory.toString()))
                .isPresent()
                .get()
                .returns(SkillSource.WORKSPACE, Skill::getSource)
                .returns("Workspace version.", Skill::getDescription);
        assertThat(manager.getExplicitSkill(
                "workspace/shared_id", workspaceSkillDirectory.resolve("SKILL.md").toString()))
                .isPresent()
                .get()
                .returns(SkillSource.WORKSPACE, Skill::getSource);
        assertThat(manager.getExplicitSkill("filesystem/shared_id", filesystemSkillDirectory.toString()))
                .isPresent()
                .get()
                .returns(SkillSource.FILESYSTEM, Skill::getSource)
                .returns("Filesystem version.", Skill::getDescription);
    }

    @Test
    void explicitPathDoesNotBypassProtectedLowerPrioritySkill() throws IOException {
        Path fsRoot = tempDir.resolve("fs");
        Path wsRoot = tempDir.resolve("ws");
        Path protectedSkillDirectory =
                writeSkillWithId(
                        fsRoot, "protected_skill", "Protected filesystem version.", "filesystem/protected_id");
        Path workspaceSkillDirectory =
                writeSkillWithId(
                        wsRoot, "protected_skill", "Workspace version.", "workspace/protected_id");
        markProtected(protectedSkillDirectory.resolve("SKILL.md"));

        SkillManager manager = new SkillManager("test-op");
        manager.setRepositories(
                List.of(
                        new FileSystemSkillRepository(fsRoot),
                        new WorkspaceSkillRepository(wsRoot, "user1")));
        manager.refreshIncrementally(List.of());

        assertThat(manager.get("protected_skill").getDescription())
                .isEqualTo("Protected filesystem version.");
        assertThat(manager.getExplicitSkill(null, workspaceSkillDirectory.toString())).isEmpty();
        assertThat(manager.getExplicitSkill("workspace/protected_id", null)).isEmpty();
        assertThat(manager.getExplicitSkill(null, protectedSkillDirectory.toString())).isPresent();
        assertThat(manager.getExplicitSkill("filesystem/protected_id", null)).isPresent();
    }

    private static void markProtected(Path skillFile) throws IOException {
        String content = Files.readString(skillFile, StandardCharsets.UTF_8);
        content =
                content.replace(
                        "description: ",
                        "description: "
                                + System.lineSeparator()
                                + "protected: true"
                                + System.lineSeparator()
                                + "description: ");
        Files.writeString(skillFile, content, StandardCharsets.UTF_8);
    }

    private static Path writeSkill(Path directory, String name, String description)
            throws IOException {
        Path skillDirectory = directory.resolve(name);
        Files.createDirectories(skillDirectory);
        Files.writeString(
                skillDirectory.resolve("SKILL.md"),
                """
                ---
                name: %s
                description: %s
                ---
                Skill body.
                """
                        .formatted(name, description));
        return skillDirectory;
    }

    private static Path writeSkillWithId(
            Path directory, String name, String description, String id) throws IOException {
        Path skillDirectory = directory.resolve(name);
        Files.createDirectories(skillDirectory);
        String content = """
                ---
                name: %s
                description: %s
                ---
                Skill body.
                """
                .formatted(name, description);
        if (id != null) {
            content = content.replace(
                    "description: ", "id: " + id + System.lineSeparator() + "description: ");
        }
        Files.writeString(skillDirectory.resolve("SKILL.md"), content);
        return skillDirectory;
    }
}
