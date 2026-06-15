/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.deep_agent;

import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.rails.SkillUseRail;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for DeepAgent dynamic skill directory add/remove.
 */
class DeepAgentSkillDirectoryTest {

    @Test
    void testAddSkillDirectoriesAppendsToExistingRail(@TempDir Path tempDir) throws IOException {
        Path skillDir = tempDir.resolve("skills");
        Files.createDirectories(skillDir.resolve("initial_skill"));
        Files.writeString(skillDir.resolve("initial_skill").resolve("SKILL.md"),
                "---\ndescription: Initial skill\n---");

        SkillUseRail rail = new SkillUseRail(List.of(skillDir.toString()), "all");
        DeepAgentConfig config = DeepAgentConfig.builder()
                .workspacePath(tempDir.toString())
                .language("en")
                .rails(List.of(rail))
                .build();
        DeepAgent agent = HarnessFactory.createDeepAgent(config);
        agent.ensureInitialized();

        assertThat(agent.findSkillUseRail()).isNotNull();
        assertThat(agent.findSkillUseRail().registeredSkillNames()).contains("initial_skill");
    }

    @Test
    void testRemoveSkillDirectoriesRemovesFromExistingRail(@TempDir Path tempDir) throws IOException {
        Path skillDir = tempDir.resolve("skills");
        Files.createDirectories(skillDir.resolve("keep_skill"));
        Files.writeString(skillDir.resolve("keep_skill").resolve("SKILL.md"),
                "---\ndescription: Keep skill\n---");

        SkillUseRail rail = new SkillUseRail(List.of(skillDir.toString()), "all");
        DeepAgentConfig config = DeepAgentConfig.builder()
                .workspacePath(tempDir.toString())
                .language("en")
                .rails(List.of(rail))
                .build();
        DeepAgent agent = HarnessFactory.createDeepAgent(config);
        agent.ensureInitialized();

        assertThat(agent.findSkillUseRail()).isNotNull();
        assertThat(agent.findSkillUseRail().registeredSkillNames()).contains("keep_skill");

        agent.removeSkillDirectories(List.of("/nonexistent/path"));
        assertThat(agent.findSkillUseRail().registeredSkillNames()).contains("keep_skill");
    }

    @Test
    void testRemoveAllSkillDirectoriesClearsSkills(@TempDir Path tempDir) throws IOException {
        Path skillDir = tempDir.resolve("skills");
        Files.createDirectories(skillDir.resolve("all_skill"));
        Files.writeString(skillDir.resolve("all_skill").resolve("SKILL.md"),
                "---\ndescription: All skill\n---");

        SkillUseRail rail = new SkillUseRail(List.of(skillDir.toString()), "all");
        DeepAgentConfig config = DeepAgentConfig.builder()
                .workspacePath(tempDir.toString())
                .language("en")
                .rails(List.of(rail))
                .build();
        DeepAgent agent = HarnessFactory.createDeepAgent(config);
        agent.ensureInitialized();

        SkillUseRail found = agent.findSkillUseRail();
        assertThat(found).isNotNull();
        assertThat(found.registeredSkillNames()).contains("all_skill");

        found.clearSkills();
        assertThat(found.registeredSkillNames()).isEmpty();
    }

    @Test
    void testFindSkillUseRailReturnsNullWhenNoRailRegistered(@TempDir Path tempDir) {
        DeepAgentConfig config = DeepAgentConfig.builder()
                .workspacePath(tempDir.toString())
                .language("en")
                .build();
        DeepAgent agent = HarnessFactory.createDeepAgent(config);
        agent.ensureInitialized();

        assertThat(agent.findSkillUseRail()).isNull();
    }

    @Test
    void testAddSkillDirectoriesWhenNoExistingRail(@TempDir Path tempDir) {
        DeepAgentConfig config = DeepAgentConfig.builder()
                .workspacePath(tempDir.toString())
                .language("en")
                .build();
        DeepAgent agent = HarnessFactory.createDeepAgent(config);
        agent.ensureInitialized();

        assertThat(agent.findSkillUseRail()).isNull();

        agent.addSkillDirectories(List.of("/some/path"));
        assertThat(agent.findSkillUseRail()).isNull();
    }
}