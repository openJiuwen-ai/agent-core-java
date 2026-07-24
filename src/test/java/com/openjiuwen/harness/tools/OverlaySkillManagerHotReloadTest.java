/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantWorkspaceResolver;
import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.core.singleagent.skills.SkillManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OverlaySkillManagerHotReloadTest {

    @TempDir
    Path baseDir;

    private Path publicRoot;
    private Path tenantRoot;
    private SkillManager publicSkillManager;
    private SkillManager tenantSkillManager;
    private OverlaySkillManager overlaySkillManager;
    private TenantContext tenantA;
    private TenantWorkspaceResolver workspaceResolver;
    private Path overlayDir;

    @BeforeEach
    void setUp() throws Exception {
        TenantContextHolder.clearCurrentTenant();
        publicRoot = Files.createDirectories(baseDir.resolve("public_skills"));
        tenantA = TenantContext.builder().tenantId("tenantA").build();
        workspaceResolver = new TenantWorkspaceResolver(baseDir.toString());
        workspaceResolver.initializeTenantSpace(tenantA);
        tenantRoot = workspaceResolver.resolveSkillRoot(tenantA);
        Files.createDirectories(tenantRoot);
        overlayDir = workspaceResolver.resolveTenantRoot(tenantA).resolve(".overlay");
        Files.createDirectories(overlayDir);
        publicSkillManager = new SkillManager("test-public");
        tenantSkillManager = new SkillManager("test-tenant");
        overlaySkillManager = new OverlaySkillManager(tenantSkillManager, publicSkillManager, overlayDir, workspaceResolver);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearCurrentTenant();
    }

    private Path createSkillDir(Path parent, String skillName, String description) throws Exception {
        Path skillDir = parent.resolve(skillName);
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\ndescription: " + description + "\n---\n# " + skillName);
        return skillDir;
    }

    @Test
    void testPublicSkillUpdate_visibleAfterRefresh() throws Exception {
        Path skillDir = createSkillDir(publicRoot, "publicSkill", "original desc");
        publicSkillManager.register(publicRoot.toString());

        TenantContextHolder.setCurrentTenant(tenantA);
        List<Skill> skillsBefore = overlaySkillManager.getAllVisibleSkills();
        assertThat(skillsBefore.stream().anyMatch(s -> s.getName().equals("publicSkill"))).isTrue();
        assertThat(skillsBefore.stream().filter(s -> s.getName().equals("publicSkill")).findFirst().get().getDescription())
                .isEqualTo("original desc");

        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\ndescription: updated desc\n---\n# publicSkill");
        skillDir.resolve("SKILL.md").toFile().setLastModified(System.currentTimeMillis() + 1000);
        publicSkillManager.refreshIncrementally(List.of(publicRoot));

        List<Skill> skillsAfter = overlaySkillManager.getAllVisibleSkills();
        assertThat(skillsAfter.stream().anyMatch(s -> s.getName().equals("publicSkill"))).isTrue();
        assertThat(skillsAfter.stream().filter(s -> s.getName().equals("publicSkill")).findFirst().get().getDescription())
                .isEqualTo("updated desc");
    }

    @Test
    void testTenantSkillUpdate_visibleAfterRefresh() throws Exception {
        Path skillDir = createSkillDir(tenantRoot, "tenantSkill", "original tenant desc");
        tenantSkillManager.register(tenantRoot.toString());

        TenantContextHolder.setCurrentTenant(tenantA);
        List<Skill> skillsBefore = overlaySkillManager.getAllVisibleSkills();
        assertThat(skillsBefore.stream().anyMatch(s -> s.getName().equals("tenantSkill"))).isTrue();
        assertThat(skillsBefore.stream().filter(s -> s.getName().equals("tenantSkill")).findFirst().get().getDescription())
                .isEqualTo("original tenant desc");

        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\ndescription: updated tenant desc\n---\n# tenantSkill");
        skillDir.resolve("SKILL.md").toFile().setLastModified(System.currentTimeMillis() + 1000);
        tenantSkillManager.refreshIncrementally(List.of(tenantRoot));

        List<Skill> skillsAfter = overlaySkillManager.getAllVisibleSkills();
        assertThat(skillsAfter.stream().anyMatch(s -> s.getName().equals("tenantSkill"))).isTrue();
        assertThat(skillsAfter.stream().filter(s -> s.getName().equals("tenantSkill")).findFirst().get().getDescription())
                .isEqualTo("updated tenant desc");
    }

    @Test
    void testNewPublicSkill_visibleAfterRefresh() throws Exception {
        publicSkillManager.register(publicRoot.toString());

        TenantContextHolder.setCurrentTenant(tenantA);
        assertThat(overlaySkillManager.getAllVisibleSkills()).isEmpty();

        createSkillDir(publicRoot, "newPublicSkill", "new public desc");
        publicSkillManager.refreshIncrementally(List.of(publicRoot));

        List<Skill> skillsAfter = overlaySkillManager.getAllVisibleSkills();
        assertThat(skillsAfter.stream().anyMatch(s -> s.getName().equals("newPublicSkill"))).isTrue();
    }

    @Test
    void testDeletedPublicSkill_removedAfterRefresh() throws Exception {
        Path skillDir = createSkillDir(publicRoot, "removableSkill", "will be removed");
        publicSkillManager.register(publicRoot.toString());

        TenantContextHolder.setCurrentTenant(tenantA);
        assertThat(overlaySkillManager.getAllVisibleSkills().stream()
                .anyMatch(s -> s.getName().equals("removableSkill"))).isTrue();

        Files.deleteIfExists(skillDir.resolve("SKILL.md"));
        Files.deleteIfExists(skillDir);
        publicSkillManager.refreshIncrementally(List.of(publicRoot));

        assertThat(overlaySkillManager.getAllVisibleSkills().stream()
                .anyMatch(s -> s.getName().equals("removableSkill"))).isFalse();
    }

    @Test
    void testNewTenantSkill_visibleAfterRefresh() throws Exception {
        tenantSkillManager.register(tenantRoot.toString());

        TenantContextHolder.setCurrentTenant(tenantA);
        assertThat(overlaySkillManager.getAllVisibleSkills()).isEmpty();

        createSkillDir(tenantRoot, "newTenantSkill", "new tenant desc");
        tenantSkillManager.refreshIncrementally(List.of(tenantRoot));

        List<Skill> skillsAfter = overlaySkillManager.getAllVisibleSkills();
        assertThat(skillsAfter.stream().anyMatch(s -> s.getName().equals("newTenantSkill"))).isTrue();
    }

    @Test
    void testDeletedTenantSkill_removedAfterRefresh() throws Exception {
        Path skillDir = createSkillDir(tenantRoot, "removableTenantSkill", "will be removed");
        tenantSkillManager.register(tenantRoot.toString());

        TenantContextHolder.setCurrentTenant(tenantA);
        assertThat(overlaySkillManager.getAllVisibleSkills().stream()
                .anyMatch(s -> s.getName().equals("removableTenantSkill"))).isTrue();

        Files.deleteIfExists(skillDir.resolve("SKILL.md"));
        Files.deleteIfExists(skillDir);
        tenantSkillManager.refreshIncrementally(List.of(tenantRoot));

        assertThat(overlaySkillManager.getAllVisibleSkills().stream()
                .anyMatch(s -> s.getName().equals("removableTenantSkill"))).isFalse();
    }
}
