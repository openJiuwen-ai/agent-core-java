package com.openjiuwen.harness.tools;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantWorkspaceResolver;
import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.core.singleagent.skills.SkillManager;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.local.LocalFsOperation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverlaySkillManagerTest {

    @TempDir
    Path baseDir;

    private SkillManager tenantSkillManager;
    private SkillManager publicSkillManager;
    private TenantWorkspaceResolver workspaceResolver;
    private Path overlayDir;
    private OverlaySkillManager overlaySkillManager;
    private TenantContext tenantA;

    @BeforeEach
    void setUp() throws Exception {
        TenantContextHolder.clearCurrentTenant();
        tenantA = TenantContext.builder().tenantId("tenantA").build();
        workspaceResolver = new TenantWorkspaceResolver(baseDir.toString());
        workspaceResolver.initializeTenantSpace(tenantA);
        overlayDir = workspaceResolver.resolveTenantRoot(tenantA).resolve(".overlay");
        Files.createDirectories(overlayDir);
        LocalFsOperation fs = new LocalFsOperation("overlay-test", OperationMode.LOCAL, "", new LocalWorkConfig());
        tenantSkillManager = new SkillManager("test-tenant", id -> fs);
        publicSkillManager = new SkillManager("test-public", id -> fs);
        overlaySkillManager = new OverlaySkillManager(tenantSkillManager, publicSkillManager, overlayDir, workspaceResolver);
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

    @Test
    void testResolveSkillFile_tenantSkillExists() throws Exception {
        TenantContextHolder.setCurrentTenant(tenantA);
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(tenantA);
        createSkillDir(tenantSkillRoot, "skillA");
        tenantSkillManager.register(tenantSkillRoot);

        Path result = overlaySkillManager.resolveSkillFile("skillA", null);
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(tenantSkillRoot.resolve("skillA").resolve("SKILL.md").normalize());
    }

    @Test
    void testResolveSkillFile_publicSkillOnly() throws Exception {
        TenantContextHolder.setCurrentTenant(tenantA);

        Path result = overlaySkillManager.resolveSkillFile("skillA", null);
        assertThat(result).isNull();
    }

    @Test
    void testResolveSkillFile_overriddenSkill() throws Exception {
        TenantContextHolder.setCurrentTenant(tenantA);
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(tenantA);
        createSkillDir(tenantSkillRoot, "skillA");
        tenantSkillManager.register(tenantSkillRoot);
        overlaySkillManager.overrideSkill("skillA");

        Path result = overlaySkillManager.resolveSkillFile("skillA", null);
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(tenantSkillRoot.resolve("skillA").resolve("SKILL.md").normalize());
    }

    @Test
    void testGetAllVisibleSkillNames_publicOnly() throws Exception {
        Path publicSkillRoot = baseDir.resolve("public-skills");
        Files.createDirectories(publicSkillRoot);
        createSkillDir(publicSkillRoot, "skillP1");
        createSkillDir(publicSkillRoot, "skillP2");
        publicSkillManager.register(publicSkillRoot);

        List<String> result = overlaySkillManager.getAllVisibleSkillNames();
        assertThat(result).containsExactlyInAnyOrder("skillP1", "skillP2");
    }

    @Test
    void testGetAllVisibleSkillNames_withTenant() throws Exception {
        Path publicSkillRoot = baseDir.resolve("public-skills");
        Files.createDirectories(publicSkillRoot);
        createSkillDir(publicSkillRoot, "skillP1");
        publicSkillManager.register(publicSkillRoot);

        TenantContextHolder.setCurrentTenant(tenantA);
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(tenantA);
        createSkillDir(tenantSkillRoot, "skillT1");
        tenantSkillManager.register(tenantSkillRoot);

        List<String> result = overlaySkillManager.getAllVisibleSkillNames();
        assertThat(result).containsExactlyInAnyOrder("skillP1", "skillT1");
        assertThat(result).isSorted();
    }

    @Test
    void testGetAllVisibleSkillNames_overridden() throws Exception {
        Path publicSkillRoot = baseDir.resolve("public-skills");
        Files.createDirectories(publicSkillRoot);
        createSkillDir(publicSkillRoot, "skillA");
        publicSkillManager.register(publicSkillRoot);

        TenantContextHolder.setCurrentTenant(tenantA);
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(tenantA);
        createSkillDir(tenantSkillRoot, "skillA");
        tenantSkillManager.register(tenantSkillRoot);
        overlaySkillManager.overrideSkill("skillA");

        List<String> result = overlaySkillManager.getAllVisibleSkillNames();
        assertThat(result).containsExactly("skillA");
    }

    @Test
    void testOverrideSkill_createsMarkerFile() throws Exception {
        TenantContextHolder.setCurrentTenant(tenantA);
        overlaySkillManager.overrideSkill("skillA");

        Path marker = overlayDir.resolve("skillA.override");
        assertThat(Files.exists(marker)).isTrue();
    }

    @Test
    void testRevokeOverride_deletesMarkerFile() throws Exception {
        TenantContextHolder.setCurrentTenant(tenantA);
        overlaySkillManager.overrideSkill("skillA");
        assertThat(Files.exists(overlayDir.resolve("skillA.override"))).isTrue();

        overlaySkillManager.revokeOverride("skillA");
        assertThat(Files.exists(overlayDir.resolve("skillA.override"))).isFalse();
    }

    @Test
    void testIsOverridden_true() throws Exception {
        TenantContextHolder.setCurrentTenant(tenantA);
        overlaySkillManager.overrideSkill("skillA");

        assertThat(overlaySkillManager.isOverridden("skillA")).isTrue();
    }

    @Test
    void testIsOverridden_false() {
        assertThat(overlaySkillManager.isOverridden("skillA")).isFalse();
    }

    @Test
    void testMigrateImplicitOverrides() throws Exception {
        Path publicSkillRoot = baseDir.resolve("public-skills");
        Files.createDirectories(publicSkillRoot);
        createSkillDir(publicSkillRoot, "skillA");
        publicSkillManager.register(publicSkillRoot);

        TenantContextHolder.setCurrentTenant(tenantA);
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(tenantA);
        createSkillDir(tenantSkillRoot, "skillA");
        tenantSkillManager.register(tenantSkillRoot);

        assertThat(overlaySkillManager.isOverridden("skillA")).isFalse();

        overlaySkillManager.migrateImplicitOverrides();

        assertThat(overlaySkillManager.isOverridden("skillA")).isTrue();
        assertThat(Files.exists(overlayDir.resolve("skillA.override"))).isTrue();
    }

    @Test
    void testMigrateImplicitOverrides_noTenantContext_noEffect() {
        Path publicSkillRoot = baseDir.resolve("public-skills");
        assertThatCode(() -> overlaySkillManager.migrateImplicitOverrides())
            .doesNotThrowAnyException();
    }

    @Test
    void testMigrateImplicitOverrides_alreadyOverridden_noDuplicate() throws Exception {
        Path publicSkillRoot = baseDir.resolve("public-skills");
        Files.createDirectories(publicSkillRoot);
        createSkillDir(publicSkillRoot, "skillA");
        publicSkillManager.register(publicSkillRoot);

        TenantContextHolder.setCurrentTenant(tenantA);
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(tenantA);
        createSkillDir(tenantSkillRoot, "skillA");
        tenantSkillManager.register(tenantSkillRoot);

        overlaySkillManager.overrideSkill("skillA");
        assertThat(overlaySkillManager.isOverridden("skillA")).isTrue();

        overlaySkillManager.migrateImplicitOverrides();
        assertThat(overlaySkillManager.isOverridden("skillA")).isTrue();
        assertThat(Files.exists(overlayDir.resolve("skillA.override"))).isTrue();
    }

    @Test
    void testMigrateImplicitOverrides_noOverlapPublicSkill_notMarked() throws Exception {
        Path publicSkillRoot = baseDir.resolve("public-skills");
        Files.createDirectories(publicSkillRoot);
        createSkillDir(publicSkillRoot, "skillP1");
        createSkillDir(publicSkillRoot, "skillP2");
        publicSkillManager.register(publicSkillRoot);

        TenantContextHolder.setCurrentTenant(tenantA);
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(tenantA);
        createSkillDir(tenantSkillRoot, "skillT1");
        tenantSkillManager.register(tenantSkillRoot);

        overlaySkillManager.migrateImplicitOverrides();

        assertThat(overlaySkillManager.isOverridden("skillP1")).isFalse();
        assertThat(overlaySkillManager.isOverridden("skillP2")).isFalse();
        assertThat(overlaySkillManager.isOverridden("skillT1")).isFalse();
    }

    @Test
    void testGetAllVisibleSkills_noTenant_returnsPublicOnly() throws Exception {
        Path publicSkillRoot = baseDir.resolve("public-skills");
        Files.createDirectories(publicSkillRoot);
        createSkillDir(publicSkillRoot, "skillP1");
        createSkillDir(publicSkillRoot, "skillP2");
        publicSkillManager.register(publicSkillRoot);

        List<Skill> result = overlaySkillManager.getAllVisibleSkills();
        assertThat(result).hasSize(2);
        assertThat(result.stream().map(Skill::getName).toList())
            .containsExactlyInAnyOrder("skillP1", "skillP2");
    }

    @Test
    void testGetAllVisibleSkills_withTenant_returnsBothLayers() throws Exception {
        Path publicSkillRoot = baseDir.resolve("public-skills");
        Files.createDirectories(publicSkillRoot);
        createSkillDir(publicSkillRoot, "skillP1");
        publicSkillManager.register(publicSkillRoot);

        TenantContextHolder.setCurrentTenant(tenantA);
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(tenantA);
        createSkillDir(tenantSkillRoot, "skillT1");
        tenantSkillManager.register(tenantSkillRoot);

        List<Skill> result = overlaySkillManager.getAllVisibleSkills();
        assertThat(result).hasSize(2);
        assertThat(result.stream().map(Skill::getName).toList())
            .containsExactlyInAnyOrder("skillP1", "skillT1");
    }

    @Test
    void testGetAllVisibleSkills_overriddenPublicSkill_hidden() throws Exception {
        Path publicSkillRoot = baseDir.resolve("public-skills");
        Files.createDirectories(publicSkillRoot);
        createSkillDir(publicSkillRoot, "skillA");
        publicSkillManager.register(publicSkillRoot);

        TenantContextHolder.setCurrentTenant(tenantA);
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(tenantA);
        createSkillDir(tenantSkillRoot, "skillA");
        tenantSkillManager.register(tenantSkillRoot);
        overlaySkillManager.overrideSkill("skillA");

        List<Skill> result = overlaySkillManager.getAllVisibleSkills();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("skillA");
    }

    @Test
    void testGetAllVisibleSkills_noOverlaps_returnsAll() throws Exception {
        Path publicSkillRoot = baseDir.resolve("public-skills");
        Files.createDirectories(publicSkillRoot);
        createSkillDir(publicSkillRoot, "skillP1");
        createSkillDir(publicSkillRoot, "skillP2");
        publicSkillManager.register(publicSkillRoot);

        TenantContextHolder.setCurrentTenant(tenantA);
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(tenantA);
        createSkillDir(tenantSkillRoot, "skillT1");
        tenantSkillManager.register(tenantSkillRoot);

        List<Skill> result = overlaySkillManager.getAllVisibleSkills();
        assertThat(result).hasSize(3);
        assertThat(result.stream().map(Skill::getName).toList())
            .containsExactlyInAnyOrder("skillP1", "skillP2", "skillT1");
    }

    @Test
    void testOverrideSkill_noTenantContext_throwsIllegalStateException() {
        assertThatThrownBy(() -> overlaySkillManager.overrideSkill("skillA"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot override skill without tenant context");
    }

    @Test
    void testOverrideSkill_sameNameTwice_idempotent() throws Exception {
        TenantContextHolder.setCurrentTenant(tenantA);
        overlaySkillManager.overrideSkill("skillA");
        overlaySkillManager.overrideSkill("skillA");

        assertThat(overlaySkillManager.isOverridden("skillA")).isTrue();
        assertThat(Files.exists(overlayDir.resolve("skillA.override"))).isTrue();
    }

    @Test
    void testRevokeOverride_nonExistent_noError() throws Exception {
        assertThatCode(() -> overlaySkillManager.revokeOverride("nonexistent"))
            .doesNotThrowAnyException();
    }

    @Test
    void testOverrideAndRevoke_cycle() throws Exception {
        TenantContextHolder.setCurrentTenant(tenantA);
        overlaySkillManager.overrideSkill("skillA");
        assertThat(overlaySkillManager.isOverridden("skillA")).isTrue();

        overlaySkillManager.revokeOverride("skillA");
        assertThat(overlaySkillManager.isOverridden("skillA")).isFalse();

        overlaySkillManager.overrideSkill("skillA");
        assertThat(overlaySkillManager.isOverridden("skillA")).isTrue();
    }

    @Test
    void testResolveSkillFile_noTenant_returnsNull() {
        assertThat(overlaySkillManager.resolveSkillFile("skillA", null)).isNull();
    }

    @Test
    void testResolveSkillFile_withRelativeFilePath() throws Exception {
        TenantContextHolder.setCurrentTenant(tenantA);
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(tenantA);
        createSkillDir(tenantSkillRoot, "skillA");
        Files.writeString(tenantSkillRoot.resolve("skillA").resolve("extra.md"), "extra content");
        tenantSkillManager.register(tenantSkillRoot);

        Path result = overlaySkillManager.resolveSkillFile("skillA", "extra.md");
        assertThat(result).isNotNull();
        assertThat(result.getFileName().toString()).isEqualTo("extra.md");
    }

    @Test
    void testResolveSkillFile_blankRelativeFilePath_defaultsToSkillMd() throws Exception {
        TenantContextHolder.setCurrentTenant(tenantA);
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(tenantA);
        createSkillDir(tenantSkillRoot, "skillA");
        tenantSkillManager.register(tenantSkillRoot);

        Path result = overlaySkillManager.resolveSkillFile("skillA", "");
        assertThat(result).isNotNull();
        assertThat(result.getFileName().toString()).isEqualTo("SKILL.md");
    }

    @Test
    void testGetAllVisibleSkillNames_noTenant_returnsPublicNames() throws Exception {
        Path publicSkillRoot = baseDir.resolve("public-skills");
        Files.createDirectories(publicSkillRoot);
        createSkillDir(publicSkillRoot, "skillP1");
        publicSkillManager.register(publicSkillRoot);

        TenantContextHolder.clearCurrentTenant();
        List<String> result = overlaySkillManager.getAllVisibleSkillNames();
        assertThat(result).containsExactly("skillP1");
    }

    @Test
    void testGetAllVisibleSkillNames_overriddenPublicSkillExcluded() throws Exception {
        Path publicSkillRoot = baseDir.resolve("public-skills");
        Files.createDirectories(publicSkillRoot);
        createSkillDir(publicSkillRoot, "skillA");
        createSkillDir(publicSkillRoot, "skillP2");
        publicSkillManager.register(publicSkillRoot);

        TenantContextHolder.setCurrentTenant(tenantA);
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(tenantA);
        createSkillDir(tenantSkillRoot, "skillA");
        tenantSkillManager.register(tenantSkillRoot);
        overlaySkillManager.overrideSkill("skillA");

        List<String> names = overlaySkillManager.getAllVisibleSkillNames();
        assertThat(names).contains("skillA");
        assertThat(names).contains("skillP2");
        assertThat(names).isSorted();
    }
}
