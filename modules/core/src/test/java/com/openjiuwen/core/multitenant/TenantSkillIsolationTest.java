/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.core.singleagent.skills.SkillManager;
import com.openjiuwen.harness.tools.OverlaySkillManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Focuses on inter-tenant skill isolation semantics and the overlay lifecycle
 * across distinct tenants sharing a single public skill directory.
 *
 * <p>Single-tenant overlay mechanics (marker file shape, idempotency, blank
 * relative paths, etc.) are already covered by {@code OverlaySkillManagerTest};
 * this suite does not duplicate those cases.</p>
 */
@DisplayName("TenantSkillIsolation: inter-tenant skill isolation & overlay lifecycle")
class TenantSkillIsolationTest {

    @TempDir
    Path baseDir;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clearCurrentTenant();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearCurrentTenant();
    }

    private Path createSkillDir(Path parent, String skillName) throws Exception {
        Path skillDir = parent.resolve(skillName);
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\ndescription: " + skillName + " desc\n---\n# " + skillName);
        return skillDir;
    }

    private TestFixture createFixture(String tenantId) throws Exception {
        TenantContext ctx = TenantContext.builder().tenantId(tenantId).build();
        TenantWorkspaceResolver workspaceResolver = new TenantWorkspaceResolver(baseDir.toString());
        workspaceResolver.initializeTenantSpace(ctx);
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(ctx);
        Path overlayDir = workspaceResolver.resolveTenantRoot(ctx).resolve(".overlay");
        Files.createDirectories(overlayDir);
        SkillManager tenantSM = new SkillManager("tenant-" + tenantId);
        SkillManager publicSM = new SkillManager("public");
        Path publicSkillRoot = baseDir.resolve("skills");
        Files.createDirectories(publicSkillRoot);
        OverlaySkillManager overlayManager =
                new OverlaySkillManager(tenantSM, publicSM, overlayDir, workspaceResolver);
        return new TestFixture(ctx, workspaceResolver, overlayManager, tenantSM, publicSM,
                tenantSkillRoot, publicSkillRoot, overlayDir);
    }

    private static final class TestFixture {
        final TenantContext ctx;
        final TenantWorkspaceResolver workspaceResolver;
        final OverlaySkillManager overlayManager;
        final SkillManager tenantSkillManager;
        final SkillManager publicSkillManager;
        final Path tenantSkillRoot;
        final Path publicSkillRoot;
        final Path overlayDir;

        TestFixture(TenantContext ctx, TenantWorkspaceResolver workspaceResolver,
                    OverlaySkillManager overlayManager, SkillManager tenantSkillManager,
                    SkillManager publicSkillManager, Path tenantSkillRoot,
                    Path publicSkillRoot, Path overlayDir) {
            this.ctx = ctx;
            this.workspaceResolver = workspaceResolver;
            this.overlayManager = overlayManager;
            this.tenantSkillManager = tenantSkillManager;
            this.publicSkillManager = publicSkillManager;
            this.tenantSkillRoot = tenantSkillRoot;
            this.publicSkillRoot = publicSkillRoot;
            this.overlayDir = overlayDir;
        }

        void refresh() {
            tenantSkillManager.refreshIncrementally(Collections.singletonList(tenantSkillRoot));
            publicSkillManager.refreshIncrementally(Collections.singletonList(publicSkillRoot));
        }
    }

    @Test
    @DisplayName("1. Different tenants cannot see each other's private skills")
    void testTenantSkillIsolation_differentTenantsCannotSeeEachOtherSkills() throws Exception {
        TestFixture fa = createFixture("tenant_a");
        TestFixture fb = createFixture("tenant_b");
        createSkillDir(fa.tenantSkillRoot, "skill_a_private");
        createSkillDir(fb.tenantSkillRoot, "skill_b_private");
        fa.refresh();
        fb.refresh();

        TenantContextHolder.setCurrentTenant(fa.ctx);
        List<String> aNames = fa.overlayManager.getAllVisibleSkillNames();
        assertThat(aNames).contains("skill_a_private");
        assertThat(aNames).doesNotContain("skill_b_private");

        TenantContextHolder.setCurrentTenant(fb.ctx);
        List<String> bNames = fb.overlayManager.getAllVisibleSkillNames();
        assertThat(bNames).contains("skill_b_private");
        assertThat(bNames).doesNotContain("skill_a_private");
    }

    @Test
    @DisplayName("2. Tenant can override a public skill")
    void testTenantSkillIsolation_tenantCanOverridePublicSkill() throws Exception {
        TestFixture fixture = createFixture("tenant_override");
        createSkillDir(fixture.publicSkillRoot, "weather");
        fixture.refresh();

        TenantContextHolder.setCurrentTenant(fixture.ctx);
        assertThat(fixture.overlayManager.isOverridden("weather")).isFalse();

        fixture.overlayManager.overrideSkill("weather");

        assertThat(fixture.overlayManager.isOverridden("weather")).isTrue();
    }

    @Test
    @DisplayName("3. Overridden public skill (no tenant copy) is not visible to that tenant")
    void testTenantSkillIsolation_overriddenPublicSkillNotVisible() throws Exception {
        TestFixture fixture = createFixture("tenant_hidden");
        createSkillDir(fixture.publicSkillRoot, "weather");
        createSkillDir(fixture.publicSkillRoot, "forecast");
        fixture.refresh();

        TenantContextHolder.setCurrentTenant(fixture.ctx);
        fixture.overlayManager.overrideSkill("weather");

        List<String> names = fixture.overlayManager.getAllVisibleSkillNames();
        assertThat(names).doesNotContain("weather");
        assertThat(names).contains("forecast");
    }

    @Test
    @DisplayName("4. Revoking an override restores visibility of the public skill")
    void testTenantSkillIsolation_revokeOverrideRestoresPublicSkill() throws Exception {
        TestFixture fixture = createFixture("tenant_revoke");
        createSkillDir(fixture.publicSkillRoot, "weather");
        fixture.refresh();

        TenantContextHolder.setCurrentTenant(fixture.ctx);
        fixture.overlayManager.overrideSkill("weather");
        assertThat(fixture.overlayManager.getAllVisibleSkillNames()).doesNotContain("weather");

        fixture.overlayManager.revokeOverride("weather");

        assertThat(fixture.overlayManager.getAllVisibleSkillNames()).contains("weather");
    }

    @Test
    @DisplayName("5. Without tenant context, only public skills are visible")
    void testTenantSkillIsolation_noTenantOnlySeesPublicSkills() throws Exception {
        TestFixture fixture = createFixture("tenant_no_ctx");
        createSkillDir(fixture.publicSkillRoot, "public_one");
        createSkillDir(fixture.publicSkillRoot, "public_two");
        createSkillDir(fixture.tenantSkillRoot, "tenant_only");
        fixture.refresh();

        TenantContextHolder.clearCurrentTenant();
        List<Skill> visible = fixture.overlayManager.getAllVisibleSkills();

        List<String> names = visible.stream().map(Skill::getName).toList();
        assertThat(visible).hasSize(2);
        assertThat(names).containsExactlyInAnyOrder("public_one", "public_two");
        assertThat(names).doesNotContain("tenant_only");
    }

    @Test
    @DisplayName("6. overrideSkill creates the .override marker file in tenant overlay dir")
    void testTenantSkillIsolation_overlayMarkerCreatedOnOverride() throws Exception {
        TestFixture fixture = createFixture("tenant_marker_create");
        createSkillDir(fixture.publicSkillRoot, "weather");
        fixture.refresh();

        TenantContextHolder.setCurrentTenant(fixture.ctx);
        assertThat(Files.exists(fixture.overlayDir.resolve("weather.override"))).isFalse();

        fixture.overlayManager.overrideSkill("weather");

        assertThat(Files.exists(fixture.overlayDir.resolve("weather.override"))).isTrue();
    }

    @Test
    @DisplayName("7. revokeOverride deletes the .override marker file")
    void testTenantSkillIsolation_overlayMarkerDeletedOnRevoke() throws Exception {
        TestFixture fixture = createFixture("tenant_marker_delete");
        createSkillDir(fixture.publicSkillRoot, "weather");
        fixture.refresh();

        TenantContextHolder.setCurrentTenant(fixture.ctx);
        fixture.overlayManager.overrideSkill("weather");
        assertThat(Files.exists(fixture.overlayDir.resolve("weather.override"))).isTrue();

        fixture.overlayManager.revokeOverride("weather");

        assertThat(Files.exists(fixture.overlayDir.resolve("weather.override"))).isFalse();
    }

    @Test
    @DisplayName("8. migrateImplicitOverrides creates .override markers for same-name tenant skills")
    void testTenantSkillIsolation_migrateImplicitOverrides_createsMarkers() throws Exception {
        TestFixture fixture = createFixture("tenant_migrate");
        createSkillDir(fixture.publicSkillRoot, "weather");
        createSkillDir(fixture.tenantSkillRoot, "weather");
        fixture.refresh();

        TenantContextHolder.setCurrentTenant(fixture.ctx);
        assertThat(fixture.overlayManager.isOverridden("weather")).isFalse();
        assertThat(Files.exists(fixture.overlayDir.resolve("weather.override"))).isFalse();

        fixture.overlayManager.migrateImplicitOverrides();

        assertThat(fixture.overlayManager.isOverridden("weather")).isTrue();
        assertThat(Files.exists(fixture.overlayDir.resolve("weather.override"))).isTrue();
    }

    @Test
    @DisplayName("9. findSkill returns tenant private skill first when same name exists in public")
    void testTenantSkillIsolation_findSkill_tenantPrivateFirst() throws Exception {
        TestFixture fixture = createFixture("tenant_first");
        createSkillDir(fixture.publicSkillRoot, "common");
        createSkillDir(fixture.tenantSkillRoot, "common");
        fixture.refresh();

        TenantContextHolder.setCurrentTenant(fixture.ctx);
        List<Skill> visible = fixture.overlayManager.getAllVisibleSkills();

        long commonCount = visible.stream().filter(s -> s.getName().equals("common")).count();
        assertThat(commonCount).isEqualTo(1);

        Skill found = visible.stream()
                .filter(s -> s.getName().equals("common"))
                .findFirst()
                .orElse(null);
        assertThat(found).isNotNull();

        Path actualDir = Path.of(found.getDirectory()).toAbsolutePath().normalize();
        Path tenantDir = fixture.tenantSkillRoot.resolve("common").toAbsolutePath().normalize();
        Path publicDir = fixture.publicSkillRoot.resolve("common").toAbsolutePath().normalize();
        assertThat(actualDir).isEqualTo(tenantDir);
        assertThat(actualDir).isNotEqualTo(publicDir);
    }

    @Test
    @DisplayName("10. getAllVisibleSkills merges tenant and public skills correctly")
    void testTenantSkillIsolation_getAllVisibleSkills_mergesCorrectly() throws Exception {
        TestFixture fixture = createFixture("tenant_merge");
        createSkillDir(fixture.publicSkillRoot, "p_one");
        createSkillDir(fixture.publicSkillRoot, "p_two");
        createSkillDir(fixture.tenantSkillRoot, "t_one");
        createSkillDir(fixture.tenantSkillRoot, "t_two");
        fixture.refresh();

        TenantContextHolder.setCurrentTenant(fixture.ctx);
        List<Skill> visible = fixture.overlayManager.getAllVisibleSkills();

        assertThat(visible).hasSize(4);
        assertThat(visible.stream().map(Skill::getName).toList())
                .containsExactlyInAnyOrder("p_one", "p_two", "t_one", "t_two");
    }
}
