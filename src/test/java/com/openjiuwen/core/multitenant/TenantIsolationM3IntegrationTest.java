/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.core.singleagent.skills.SkillManager;
import com.openjiuwen.harness.tools.OverlaySkillManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@DisplayName("ST-M3: OverlaySkillManager tenant isolation integration tests")
class TenantIsolationM3IntegrationTest {

    @TempDir
    Path baseDir;

    private Path createSkillDir(Path parent, String skillName) throws Exception {
        Path skillDir = parent.resolve(skillName);
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\ndescription: " + skillName + " desc\n---\n# " + skillName);
        return skillDir;
    }

    private OverlaySkillManager createOverlaySkillManager(TenantContext ctx) throws Exception {
        TenantWorkspaceResolver workspaceResolver = new TenantWorkspaceResolver(baseDir.toString());
        workspaceResolver.initializeTenantSpace(ctx);
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(ctx);
        Path overlayDir = workspaceResolver.resolveTenantRoot(ctx).resolve(".overlay");
        Files.createDirectories(overlayDir);
        SkillManager tenantSM = new SkillManager("tenant-" + ctx.getTenantId());
        tenantSM.refreshIncrementally(Collections.singletonList(tenantSkillRoot));
        SkillManager publicSM = new SkillManager("public");
        Path publicSkillRoot = baseDir.resolve("skills");
        Files.createDirectories(publicSkillRoot);
        publicSM.refreshIncrementally(Collections.singletonList(publicSkillRoot));
        return new OverlaySkillManager(tenantSM, publicSM, overlayDir, workspaceResolver);
    }

    @Test
    @DisplayName("Dual-layer lookup: tenant skill overrides same-name public skill in getAllVisibleSkills")
    void testDualLayerLookup_tenantWinsOverPublic() throws Exception {
        TenantContext ctx = TenantContext.builder().tenantId("m3_tenant_win").build();
        TenantWorkspaceResolver workspaceResolver = new TenantWorkspaceResolver(baseDir.toString());
        workspaceResolver.initializeTenantSpace(ctx);
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(ctx);
        Path publicSkillRoot = baseDir.resolve("skills");
        Files.createDirectories(publicSkillRoot);
        Path overlayDir = workspaceResolver.resolveTenantRoot(ctx).resolve(".overlay");
        Files.createDirectories(overlayDir);

        createSkillDir(publicSkillRoot, "weather");
        createSkillDir(tenantSkillRoot, "weather");

        SkillManager tenantSM = new SkillManager("tenant-m3_tenant_win");
        tenantSM.refreshIncrementally(Collections.singletonList(tenantSkillRoot));
        SkillManager publicSM = new SkillManager("public");
        publicSM.refreshIncrementally(Collections.singletonList(publicSkillRoot));

        OverlaySkillManager osm = new OverlaySkillManager(tenantSM, publicSM, overlayDir, workspaceResolver);

        try {
            TenantContextHolder.setCurrentTenant(ctx);
            osm.overrideSkill("weather");

            List<Skill> visible = osm.getAllVisibleSkills();
            assertThat(visible.stream().map(Skill::getName).toList()).contains("weather");
            assertThat(visible.stream().filter(s -> s.getName().equals("weather")).findFirst().orElse(null))
                    .isNotNull();
            assertThat(visible.stream().filter(s -> s.getName().equals("weather")).findFirst().get().getDirectory())
                    .contains("m3_tenant_win");
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }
    }

    @Test
    @DisplayName("Dual-layer lookup: tenant has no private skill, getAllVisibleSkillNames includes public skill name")
    void testDualLayerLookup_publicSkillVisibleWhenTenantHasNone() throws Exception {
        TenantContext ctx = TenantContext.builder().tenantId("m3_public_visible").build();
        TenantWorkspaceResolver workspaceResolver = new TenantWorkspaceResolver(baseDir.toString());
        workspaceResolver.initializeTenantSpace(ctx);
        Path publicSkillRoot = baseDir.resolve("skills");
        Files.createDirectories(publicSkillRoot);
        Path overlayDir = workspaceResolver.resolveTenantRoot(ctx).resolve(".overlay");
        Files.createDirectories(overlayDir);

        createSkillDir(publicSkillRoot, "forecast");

        SkillManager tenantSM = new SkillManager("tenant-m3_public_visible");
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(ctx);
        tenantSM.refreshIncrementally(Collections.singletonList(tenantSkillRoot));
        SkillManager publicSM = new SkillManager("public");
        publicSM.refreshIncrementally(Collections.singletonList(publicSkillRoot));

        OverlaySkillManager osm = new OverlaySkillManager(tenantSM, publicSM, overlayDir, workspaceResolver);

        try {
            TenantContextHolder.setCurrentTenant(ctx);
            List<String> names = osm.getAllVisibleSkillNames();
            assertThat(names).contains("forecast");
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }
    }

    @Test
    @DisplayName("Override public skill: overrideSkill creates .override marker and isOverridden returns true")
    void testOverrideSkill_createsMarkerAndIsOverridden() throws Exception {
        TenantContext ctx = TenantContext.builder().tenantId("m3_override").build();
        TenantWorkspaceResolver workspaceResolver = new TenantWorkspaceResolver(baseDir.toString());
        workspaceResolver.initializeTenantSpace(ctx);
        Path overlayDir = workspaceResolver.resolveTenantRoot(ctx).resolve(".overlay");
        Files.createDirectories(overlayDir);

        SkillManager tenantSM = new SkillManager("tenant-m3_override");
        SkillManager publicSM = new SkillManager("public");

        OverlaySkillManager osm = new OverlaySkillManager(tenantSM, publicSM, overlayDir, workspaceResolver);

        try {
            TenantContextHolder.setCurrentTenant(ctx);
            osm.overrideSkill("weather");

            Path marker = overlayDir.resolve("weather.override");
            assertThat(Files.exists(marker)).isTrue();
            assertThat(osm.isOverridden("weather")).isTrue();
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }
    }

    @Test
    @DisplayName("Revoke override: revokeOverride deletes marker and isOverridden returns false")
    void testRevokeOverride_deletesMarkerAndIsOverridden() throws Exception {
        TenantContext ctx = TenantContext.builder().tenantId("m3_revoke").build();
        TenantWorkspaceResolver workspaceResolver = new TenantWorkspaceResolver(baseDir.toString());
        workspaceResolver.initializeTenantSpace(ctx);
        Path overlayDir = workspaceResolver.resolveTenantRoot(ctx).resolve(".overlay");
        Files.createDirectories(overlayDir);

        SkillManager tenantSM = new SkillManager("tenant-m3_revoke");
        SkillManager publicSM = new SkillManager("public");

        OverlaySkillManager osm = new OverlaySkillManager(tenantSM, publicSM, overlayDir, workspaceResolver);

        try {
            TenantContextHolder.setCurrentTenant(ctx);
            osm.overrideSkill("weather");
            assertThat(osm.isOverridden("weather")).isTrue();
            assertThat(Files.exists(overlayDir.resolve("weather.override"))).isTrue();

            osm.revokeOverride("weather");
            assertThat(Files.exists(overlayDir.resolve("weather.override"))).isFalse();
            assertThat(osm.isOverridden("weather")).isFalse();
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }
    }

    @Test
    @DisplayName("Hot reload: new skill added to tenant dir after OverlaySkillManager creation, refresh picks it up")
    void testHotReload_newTenantSkillVisibleAfterRefresh() throws Exception {
        TenantContext ctx = TenantContext.builder().tenantId("m3_hotreload").build();
        TenantWorkspaceResolver workspaceResolver = new TenantWorkspaceResolver(baseDir.toString());
        workspaceResolver.initializeTenantSpace(ctx);
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(ctx);
        Path publicSkillRoot = baseDir.resolve("skills");
        Files.createDirectories(publicSkillRoot);
        Path overlayDir = workspaceResolver.resolveTenantRoot(ctx).resolve(".overlay");
        Files.createDirectories(overlayDir);

        createSkillDir(publicSkillRoot, "base_skill");
        createSkillDir(tenantSkillRoot, "initial_skill");

        SkillManager tenantSM = new SkillManager("tenant-m3_hotreload");
        tenantSM.refreshIncrementally(Collections.singletonList(tenantSkillRoot));
        SkillManager publicSM = new SkillManager("public");
        publicSM.refreshIncrementally(Collections.singletonList(publicSkillRoot));

        OverlaySkillManager osm = new OverlaySkillManager(tenantSM, publicSM, overlayDir, workspaceResolver);

        try {
            TenantContextHolder.setCurrentTenant(ctx);

            List<String> namesBefore = osm.getAllVisibleSkillNames();
            assertThat(namesBefore).contains("base_skill", "initial_skill");
            assertThat(namesBefore).doesNotContain("late_skill");

            createSkillDir(tenantSkillRoot, "late_skill");
            tenantSM.refreshIncrementally(Collections.singletonList(tenantSkillRoot));

            List<String> namesAfter = osm.getAllVisibleSkillNames();
            assertThat(namesAfter).contains("base_skill", "initial_skill", "late_skill");
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }
    }

    @Test
    @DisplayName("Migrate implicit overrides: tenant skill dir matching public name auto-creates .override marker")
    void testMigrateImplicitOverrides_autoCreatesMarker() throws Exception {
        TenantContext ctx = TenantContext.builder().tenantId("m3_migrate").build();
        TenantWorkspaceResolver workspaceResolver = new TenantWorkspaceResolver(baseDir.toString());
        workspaceResolver.initializeTenantSpace(ctx);
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(ctx);
        Path publicSkillRoot = baseDir.resolve("skills");
        Files.createDirectories(publicSkillRoot);
        Path overlayDir = workspaceResolver.resolveTenantRoot(ctx).resolve(".overlay");
        Files.createDirectories(overlayDir);

        createSkillDir(publicSkillRoot, "weather");
        createSkillDir(tenantSkillRoot, "weather");

        SkillManager tenantSM = new SkillManager("tenant-m3_migrate");
        tenantSM.refreshIncrementally(Collections.singletonList(tenantSkillRoot));
        SkillManager publicSM = new SkillManager("public");
        publicSM.refreshIncrementally(Collections.singletonList(publicSkillRoot));

        OverlaySkillManager osm = new OverlaySkillManager(tenantSM, publicSM, overlayDir, workspaceResolver);

        try {
            TenantContextHolder.setCurrentTenant(ctx);
            assertThat(osm.isOverridden("weather")).isFalse();
            assertThat(Files.exists(overlayDir.resolve("weather.override"))).isFalse();

            osm.migrateImplicitOverrides();

            assertThat(osm.isOverridden("weather")).isTrue();
            assertThat(Files.exists(overlayDir.resolve("weather.override"))).isTrue();
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }
    }

    @Test
    @DisplayName("No tenant context: getAllVisibleSkills returns only public skills, resolveSkillFile returns null")
    void testNoTenantContext_returnsPublicOnlyAndNullForTenantPath() throws Exception {
        TenantContext ctx = TenantContext.builder().tenantId("m3_notenant").build();
        TenantWorkspaceResolver workspaceResolver = new TenantWorkspaceResolver(baseDir.toString());
        workspaceResolver.initializeTenantSpace(ctx);
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(ctx);
        Path publicSkillRoot = baseDir.resolve("skills");
        Files.createDirectories(publicSkillRoot);
        Path overlayDir = workspaceResolver.resolveTenantRoot(ctx).resolve(".overlay");
        Files.createDirectories(overlayDir);

        createSkillDir(publicSkillRoot, "public_skill");
        createSkillDir(tenantSkillRoot, "private_skill");

        SkillManager tenantSM = new SkillManager("tenant-m3_notenant");
        tenantSM.refreshIncrementally(Collections.singletonList(tenantSkillRoot));
        SkillManager publicSM = new SkillManager("public");
        publicSM.refreshIncrementally(Collections.singletonList(publicSkillRoot));

        OverlaySkillManager osm = new OverlaySkillManager(tenantSM, publicSM, overlayDir, workspaceResolver);

        try {
            TenantContextHolder.clearCurrentTenant();

            List<Skill> visible = osm.getAllVisibleSkills();
            assertThat(visible).hasSize(1);
            assertThat(visible.get(0).getName()).isEqualTo("public_skill");

            Path resolved = osm.resolveSkillFile("private_skill", null);
            assertThat(resolved).isNull();
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }
    }
}
