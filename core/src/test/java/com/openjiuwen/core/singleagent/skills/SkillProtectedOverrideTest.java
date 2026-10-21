/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.singleagent.skills.repository.SkillContentValidator;
import com.openjiuwen.core.singleagent.skills.repository.SkillRepository;
import com.openjiuwen.core.singleagent.skills.repository.WorkspaceSkillRepository;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Tag("issue196-w7")
class SkillProtectedOverrideTest {
    @TempDir
    private Path tempDir;

    @Test
    @Tag("TC-SEC-04")
    void protectedClasspathSkill_notOverriddenWhenBlockingEnabled() {
        Skill protectedSkill = new Skill("demo", "Protected classpath version.", null);
        protectedSkill.setSource(SkillSource.CLASSPATH);
        protectedSkill.setId("demo");
        protectedSkill.setProtectedSkill(true);
        Skill lowerPriority = new Skill("demo", "Filesystem version.", null);
        lowerPriority.setSource(SkillSource.FILESYSTEM);
        SkillRepository lowerRepo = fixedRepository(SkillSource.FILESYSTEM, List.of(lowerPriority));
        SkillRepository protectedRepo =
                fixedRepository(SkillSource.CLASSPATH, List.of(protectedSkill));

        SkillManager manager = new SkillManager("test-op");
        manager.setProtectedOverrideBlocked(true);
        manager.setRepositories(List.of(protectedRepo, lowerRepo));
        manager.refreshIncrementally(List.of());

        assertThat(manager.get("demo").getDescription()).isEqualTo("Protected classpath version.");
        assertThat(manager.get("demo").isProtectedSkill()).isTrue();
    }

    @Test
    @Tag("TC-SEC-05")
    void protectedSkill_overriddenWhenBlockingDisabled() {
        Skill protectedSkill = new Skill("demo", "Protected classpath version.", null);
        protectedSkill.setSource(SkillSource.CLASSPATH);
        protectedSkill.setProtectedSkill(true);
        Skill higherPriority = new Skill("demo", "Workspace version.", null);
        higherPriority.setSource(SkillSource.WORKSPACE);
        SkillRepository protectedRepo =
                fixedRepository(SkillSource.CLASSPATH, List.of(protectedSkill));
        SkillRepository higherRepo =
                fixedRepository(SkillSource.WORKSPACE, List.of(higherPriority));

        SkillManager manager = new SkillManager("test-op");
        manager.setProtectedOverrideBlocked(false);
        manager.setRepositories(List.of(protectedRepo, higherRepo));
        manager.refreshIncrementally(List.of());

        assertThat(manager.get("demo").getDescription()).isEqualTo("Workspace version.");
        assertThat(manager.get("demo").getSource()).isEqualTo(SkillSource.WORKSPACE);
    }

    @Test
    @Tag("TC-SEC-02")
    void oversizedWorkspaceSkill_rejectedByValidatorWithoutBlockingOthers() throws IOException {
        Path wsRoot = tempDir.resolve("ws");
        Path oversizedDir = wsRoot.resolve("oversized_skill");
        Files.createDirectories(oversizedDir);
        byte[] oversizedContent = new byte[SkillContentValidator.DEFAULT_WORKSPACE_MAX_BYTES + 1];
        Files.write(oversizedDir.resolve("SKILL.md"), oversizedContent);
        writeSkill(wsRoot.resolve("normal_skill"), "normal_skill", "Normal skill.");

        SkillContentValidator validator = new SkillContentValidator();
        assertThat(validator.validate(oversizedDir.resolve("SKILL.md"), wsRoot).allowed())
                .isFalse();
        assertThat(
                        validator
                                .validate(
                                        wsRoot.resolve("normal_skill").resolve("SKILL.md"), wsRoot)
                                .allowed())
                .isTrue();
        SkillRepository wsRepo = new WorkspaceSkillRepository(wsRoot, "user1");
        SkillManager manager = new SkillManager("test-op");
        manager.setRepositories(List.of(wsRepo));
        manager.refreshIncrementally(List.of());

        assertThat(manager.has("normal_skill")).isTrue();
        assertThat(manager.has("oversized_skill")).isFalse();
    }

    @Test
    @Tag("TC-SEC-06")
    void workspacePathOutsideRoot_rejectedByValidator() throws IOException {
        Path wsRoot = tempDir.resolve("ws");
        Path outsideRoot = tempDir.resolve("outside");
        writeSkill(outsideRoot, "outside_skill", "Outside skill.");
        writeSkill(wsRoot.resolve("inside_skill"), "inside_skill", "Inside skill.");

        SkillContentValidator validator = new SkillContentValidator();
        assertThat(
                        validator
                                .validate(
                                        outsideRoot.resolve("outside_skill").resolve("SKILL.md"),
                                        wsRoot)
                                .allowed())
                .isFalse();
        assertThat(
                        validator
                                .validate(
                                        wsRoot.resolve("inside_skill").resolve("SKILL.md"), wsRoot)
                                .allowed())
                .isTrue();
    }

    @Test
    void validatorDefaults_matchPlanRecommendations() {
        SkillContentValidator validator = new SkillContentValidator();
        assertThat(validator.getWorkspaceMaxBytes()).isEqualTo(262144);
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
                        .formatted(name, description),
                StandardCharsets.UTF_8);
    }

    private static SkillRepository fixedRepository(SkillSource source, List<Skill> skills) {
        return new SkillRepository() {
            @Override
            public SkillSource source() {
                return source;
            }

            @Override
            public List<Skill> loadAll() {
                return skills;
            }
        };
    }
}
