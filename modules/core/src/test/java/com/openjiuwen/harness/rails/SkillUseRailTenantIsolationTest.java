/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantWorkspaceResolver;
import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SkillUseRail tenant isolation tests")
class SkillUseRailTenantIsolationTest {

    @TempDir
    Path tempDir;

    private DeepAgent agent;
    private SkillUseRail rail;
    private Path publicSkillsRoot;
    private TenantWorkspaceResolver resolver;

    @BeforeEach
    void setUp() throws Exception {
        TenantContextHolder.clearCurrentTenant();

        DeepAgentConfig config = DeepAgentConfig.builder()
                .enableTenantIsolation(true)
                .tenantDataRoot(tempDir.toString())
                .workspacePath(tempDir.toString())
                .language("en")
                .build();

        Workspace workspace = Workspace.builder()
                .rootPath(tempDir.toString())
                .language("en")
                .build();

        agent = new DeepAgent(null, config, workspace);
        resolver = agent.getWorkspaceResolver();
        assertThat(resolver).isNotNull();

        publicSkillsRoot = tempDir.resolve("skills");
        Files.createDirectories(publicSkillsRoot);

        rail = new SkillUseRail();
        rail.init(agent);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearCurrentTenant();
        if (rail != null) {
            rail.uninit(agent);
        }
    }

    private Path createSkill(Path skillsRoot, String skillName, String description) throws Exception {
        Path dir = skillsRoot.resolve(skillName);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("SKILL.md"),
                "---\ndescription: " + description + "\n---\n# " + skillName);
        return dir;
    }

    @Test
    @DisplayName("OverlaySkillManager is created when tenant isolation is enabled")
    void overlaySkillManagerCreated_whenTenantIsolationEnabled() {
        assertThat(rail.overlaySkillManager).isNotNull();
        assertThat(rail.tenantSkillManager).isNotNull();
        assertThat(rail.railWorkspaceResolver).isNotNull();
    }

    @Test
    @DisplayName("Public skills are visible to all tenants")
    void publicSkillsVisibleToAllTenants() throws Exception {
        createSkill(publicSkillsRoot, "common-skill", "public description");

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant_a").build());
        resolver.initializeTenantSpace(TenantContextHolder.getCurrentTenant());

        rail.reloadSkills();
        List<Skill> skills = rail.filterSkills(null);
        assertThat(skills.stream().anyMatch(s -> s.getName().equals("common-skill"))).isTrue();
    }

    @Test
    @DisplayName("Tenant private skills are visible only to that tenant")
    void tenantPrivateSkillsVisibleOnlyToThatTenant() throws Exception {
        createSkill(publicSkillsRoot, "common-skill", "public description");

        TenantContext tenantA = TenantContext.builder().tenantId("tenant_a").build();
        TenantContext tenantB = TenantContext.builder().tenantId("tenant_b").build();
        resolver.initializeTenantSpace(tenantA);
        resolver.initializeTenantSpace(tenantB);
        Path tenantASkillRoot = resolver.resolveSkillRoot(tenantA);
        Path tenantBSkillRoot = resolver.resolveSkillRoot(tenantB);
        createSkill(tenantASkillRoot, "private-a", "tenant A private");
        createSkill(tenantBSkillRoot, "private-b", "tenant B private");

        // Tenant A sees private-a but not private-b
        TenantContextHolder.setCurrentTenant(tenantA);
        rail.reloadSkills();
        List<Skill> skillsA = rail.filterSkills(null);
        assertThat(skillsA.stream().anyMatch(s -> s.getName().equals("private-a"))).isTrue();
        assertThat(skillsA.stream().anyMatch(s -> s.getName().equals("private-b"))).isFalse();

        // Tenant B sees private-b but not private-a
        TenantContextHolder.setCurrentTenant(tenantB);
        rail.reloadSkills();
        List<Skill> skillsB = rail.filterSkills(null);
        assertThat(skillsB.stream().anyMatch(s -> s.getName().equals("private-b"))).isTrue();
        assertThat(skillsB.stream().anyMatch(s -> s.getName().equals("private-a"))).isFalse();
    }

    @Test
    @DisplayName("Tenant skill overrides public skill with same name")
    void tenantSkillOverridesPublicSkill() throws Exception {
        createSkill(publicSkillsRoot, "shared-skill", "public version");
        TenantContext tenantA = TenantContext.builder().tenantId("tenant_a").build();
        resolver.initializeTenantSpace(tenantA);
        Path tenantASkillRoot = resolver.resolveSkillRoot(tenantA);
        createSkill(tenantASkillRoot, "shared-skill", "tenant override");

        TenantContextHolder.setCurrentTenant(tenantA);
        rail.reloadSkills();
        List<Skill> skills = rail.filterSkills(null);
        assertThat(skills.stream().filter(s -> s.getName().equals("shared-skill")).count()).isEqualTo(1);
        assertThat(skills.stream().filter(s -> s.getName().equals("shared-skill"))
                .findFirst().get().getDescription()).isEqualTo("tenant override");
    }

    @Test
    @DisplayName("No tenant context shows only public skills")
    void noTenantContextShowsOnlyPublicSkills() throws Exception {
        TenantContext tenantA = TenantContext.builder().tenantId("tenant_a").build();
        resolver.initializeTenantSpace(tenantA);
        Path tenantASkillRoot = resolver.resolveSkillRoot(tenantA);
        createSkill(publicSkillsRoot, "public-skill", "public desc");
        createSkill(tenantASkillRoot, "tenant-skill", "tenant desc");

        TenantContextHolder.clearCurrentTenant();
        rail.reloadSkills();
        List<Skill> skills = rail.filterSkills(null);
        assertThat(skills.stream().anyMatch(s -> s.getName().equals("public-skill"))).isTrue();
        assertThat(skills.stream().anyMatch(s -> s.getName().equals("tenant-skill"))).isFalse();
    }

    @Test
    @DisplayName("Hot reload: newly added tenant skill becomes visible after refresh")
    void hotReload_newTenantSkillVisible() throws Exception {
        TenantContext tenantA = TenantContext.builder().tenantId("tenant_a").build();
        resolver.initializeTenantSpace(tenantA);
        Path tenantASkillRoot = resolver.resolveSkillRoot(tenantA);
        Files.createDirectories(tenantASkillRoot);

        TenantContextHolder.setCurrentTenant(tenantA);
        rail.reloadSkills();
        assertThat(rail.filterSkills(null).stream().anyMatch(s -> s.getName().equals("hot-skill"))).isFalse();

        createSkill(tenantASkillRoot, "hot-skill", "hot loaded");
        rail.reloadSkills();
        assertThat(rail.filterSkills(null).stream().anyMatch(s -> s.getName().equals("hot-skill"))).isTrue();
    }

    @Test
    @DisplayName("Hot reload: updated tenant skill reflects new description")
    void hotReload_updatedTenantSkillReflectsChanges() throws Exception {
        TenantContext tenantA = TenantContext.builder().tenantId("tenant_a").build();
        resolver.initializeTenantSpace(tenantA);
        Path tenantASkillRoot = resolver.resolveSkillRoot(tenantA);
        Path skillDir = createSkill(tenantASkillRoot, "update-skill", "original desc");

        TenantContextHolder.setCurrentTenant(tenantA);
        rail.reloadSkills();
        assertThat(rail.filterSkills(null).stream().filter(s -> s.getName().equals("update-skill"))
                .findFirst().get().getDescription()).isEqualTo("original desc");

        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\ndescription: updated desc\n---\n# update-skill");
        skillDir.resolve("SKILL.md").toFile().setLastModified(System.currentTimeMillis() + 1000);
        rail.reloadSkills();
        assertThat(rail.filterSkills(null).stream().filter(s -> s.getName().equals("update-skill"))
                .findFirst().get().getDescription()).isEqualTo("updated desc");
    }
}
