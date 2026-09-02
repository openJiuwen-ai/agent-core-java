package com.openjiuwen.harness.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantWorkspaceResolver;

class TenantSkillIsolationTest {

    @TempDir
    Path baseDir;

    private Path publicSkillsRoot;
    private TenantWorkspaceResolver resolver;

    @BeforeEach
    void setUp() throws IOException {
        publicSkillsRoot = baseDir.resolve("skills");
        Files.createDirectories(publicSkillsRoot);
        resolver = new TenantWorkspaceResolver(baseDir.toAbsolutePath().toString());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearCurrentTenant();
    }

    private void createSkill(Path skillsRoot, String skillName, String content) throws IOException {
        Path dir = skillsRoot.resolve(skillName);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("SKILL.md"), content);
    }

    private TenantContext tenant(String tenantId) {
        return TenantContext.builder().tenantId(tenantId).build();
    }

    @Test
    void publicSkillVisibleToAllTenants() throws IOException {
        createSkill(publicSkillsRoot, "common-skill", "public content");

        TenantContextHolder.setCurrentTenant(tenant("tenant-a"));
        SkillTool skillTool = new SkillTool(publicSkillsRoot.toString(), resolver);

        ToolOutput output = skillTool.readSkill("common-skill", null);
        assertThat(output.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) output.getData();
        assertThat(data.get("skill_content")).isEqualTo("public content");
    }

    @Test
    void tenantPrivateSkillNotVisibleToOtherTenant() throws IOException {
        TenantContext tenantA = tenant("tenant-a");
        TenantContext tenantB = tenant("tenant-b");
        Path tenantASkillRoot = resolver.resolveSkillRoot(tenantA);
        Files.createDirectories(tenantASkillRoot);
        createSkill(tenantASkillRoot, "private-a", "tenant-a private");

        TenantContextHolder.setCurrentTenant(tenantB);
        SkillTool skillTool = new SkillTool(publicSkillsRoot.toString(), resolver);

        ToolOutput output = skillTool.readSkill("private-a", null);
        assertThat(output.isSuccess()).isFalse();
    }

    @Test
    void tenantPrivateSkillPriorityOverPublic() throws IOException {
        TenantContext tenantA = tenant("tenant-a");
        createSkill(publicSkillsRoot, "shared-skill", "public version");
        Path tenantASkillRoot = resolver.resolveSkillRoot(tenantA);
        Files.createDirectories(tenantASkillRoot);
        createSkill(tenantASkillRoot, "shared-skill", "tenant override");

        TenantContextHolder.setCurrentTenant(tenantA);
        SkillTool skillTool = new SkillTool(publicSkillsRoot.toString(), resolver);

        ToolOutput output = skillTool.readSkill("shared-skill", null);
        assertThat(output.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) output.getData();
        assertThat(data.get("skill_content")).isEqualTo("tenant override");
    }

    @Test
    void tenantFallBackToPublicWhenNoPrivate() throws IOException {
        TenantContext tenantA = tenant("tenant-a");
        createSkill(publicSkillsRoot, "only-public", "public only");

        TenantContextHolder.setCurrentTenant(tenantA);
        SkillTool skillTool = new SkillTool(publicSkillsRoot.toString(), resolver);

        ToolOutput output = skillTool.readSkill("only-public", null);
        assertThat(output.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) output.getData();
        assertThat(data.get("skill_content")).isEqualTo("public only");
    }

    @Test
    void listSkillsMergesPublicAndTenant() throws IOException {
        TenantContext tenantA = tenant("tenant-a");
        createSkill(publicSkillsRoot, "alpha", "public alpha");
        createSkill(publicSkillsRoot, "beta", "public beta");
        Path tenantASkillRoot = resolver.resolveSkillRoot(tenantA);
        Files.createDirectories(tenantASkillRoot);
        createSkill(tenantASkillRoot, "gamma", "tenant gamma");

        TenantContextHolder.setCurrentTenant(tenantA);
        ListSkillTool listTool = new ListSkillTool(publicSkillsRoot.toString(), resolver);

        ToolOutput output = listTool.listSkills();
        assertThat(output.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<String> skills = (List<String>) output.getData();
        assertThat(skills).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void listSkillsDeduplicatesSameNameAcrossPublicAndTenant() throws IOException {
        TenantContext tenantA = tenant("tenant-a");
        createSkill(publicSkillsRoot, "common", "public common");
        Path tenantASkillRoot = resolver.resolveSkillRoot(tenantA);
        Files.createDirectories(tenantASkillRoot);
        createSkill(tenantASkillRoot, "common", "tenant common override");

        TenantContextHolder.setCurrentTenant(tenantA);
        ListSkillTool listTool = new ListSkillTool(publicSkillsRoot.toString(), resolver);

        ToolOutput output = listTool.listSkills();
        assertThat(output.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<String> skills = (List<String>) output.getData();
        assertThat(skills).containsExactly("common");
    }

    @Test
    void noTenantBehaviorMatchesOriginalSkillTool() throws IOException {
        createSkill(publicSkillsRoot, "solo-skill", "solo content");

        SkillTool skillTool = new SkillTool(publicSkillsRoot.toString(), resolver);

        ToolOutput output = skillTool.readSkill("solo-skill", null);
        assertThat(output.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) output.getData();
        assertThat(data.get("skill_content")).isEqualTo("solo content");
    }

    @Test
    void noTenantBehaviorMatchesOriginalListSkillTool() throws IOException {
        createSkill(publicSkillsRoot, "skill-x", "x");
        createSkill(publicSkillsRoot, "skill-y", "y");

        ListSkillTool listTool = new ListSkillTool(publicSkillsRoot.toString(), resolver);

        ToolOutput output = listTool.listSkills();
        assertThat(output.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<String> skills = (List<String>) output.getData();
        assertThat(skills).containsExactly("skill-x", "skill-y");
    }

    @Test
    void skillToolWithoutWorkspaceResolverFallsBackToPublic() throws IOException {
        TenantContext tenantA = tenant("tenant-a");
        createSkill(publicSkillsRoot, "base-skill", "base");

        TenantContextHolder.setCurrentTenant(tenantA);
        SkillTool skillTool = new SkillTool(publicSkillsRoot.toString());

        ToolOutput output = skillTool.readSkill("base-skill", null);
        assertThat(output.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) output.getData();
        assertThat(data.get("skill_content")).isEqualTo("base");
    }

    @Test
    void listSkillToolWithoutWorkspaceResolverReturnsPublicOnly() throws IOException {
        TenantContext tenantA = tenant("tenant-a");
        createSkill(publicSkillsRoot, "pub-skill", "pub");

        TenantContextHolder.setCurrentTenant(tenantA);
        ListSkillTool listTool = new ListSkillTool(publicSkillsRoot.toString());

        ToolOutput output = listTool.listSkills();
        assertThat(output.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<String> skills = (List<String>) output.getData();
        assertThat(skills).containsExactly("pub-skill");
    }
}
