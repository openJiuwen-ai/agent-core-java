package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantWorkspaceResolver;
import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.core.singleagent.skills.SkillManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class OverlaySkillManager {
    private final SkillManager tenantSkillManager;
    private final SkillManager publicSkillManager;
    private final Path overlayDir;
    private final TenantWorkspaceResolver workspaceResolver;

    public OverlaySkillManager(SkillManager tenantSkillManager, SkillManager publicSkillManager,
                               Path overlayDir, TenantWorkspaceResolver workspaceResolver) {
        this.tenantSkillManager = tenantSkillManager;
        this.publicSkillManager = publicSkillManager;
        this.overlayDir = overlayDir;
        this.workspaceResolver = workspaceResolver;
    }

    public Path resolveSkillFile(String skillName, String relativeFilePath) {
        String fileName = (relativeFilePath == null || relativeFilePath.isBlank()) ? "SKILL.md" : relativeFilePath;
        TenantContext ctx = TenantContextHolder.getCurrentTenant();
        if (ctx != null && ctx.isTenantAware() && workspaceResolver != null) {
            Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(ctx);
            if (tenantSkillRoot != null && isOverridden(skillName) || hasTenantSkill(skillName, tenantSkillRoot)) {
                Path tenantTarget = tenantSkillRoot.resolve(skillName).resolve(fileName).normalize();
                if (Files.exists(tenantTarget)) {
                    return tenantTarget;
                }
            }
        }
        return null;
    }

    public List<Skill> getAllVisibleSkills() {
        TenantContext ctx = TenantContextHolder.getCurrentTenant();
        if (ctx == null || !ctx.isTenantAware() || workspaceResolver == null) {
            return new ArrayList<>(publicSkillManager.getAll());
        }
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(ctx);
        if (tenantSkillRoot == null || !Files.isDirectory(tenantSkillRoot)) {
            return new ArrayList<>(publicSkillManager.getAll());
        }
        Set<String> overriddenNames = getOverriddenNames();
        List<Skill> result = new ArrayList<>();
        Set<String> seenNames = new LinkedHashSet<>();

        for (Skill skill : tenantSkillManager.getAll()) {
            result.add(skill);
            seenNames.add(skill.getName());
        }

        for (Skill skill : publicSkillManager.getAll()) {
            if (!seenNames.contains(skill.getName()) && !overriddenNames.contains(skill.getName())) {
                result.add(skill);
                seenNames.add(skill.getName());
            }
        }

        return result;
    }

    public List<String> getAllVisibleSkillNames() {
        TenantContext ctx = TenantContextHolder.getCurrentTenant();
        if (ctx == null || !ctx.isTenantAware() || workspaceResolver == null) {
            return publicSkillManager.getNames();
        }
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(ctx);
        if (tenantSkillRoot == null || !Files.isDirectory(tenantSkillRoot)) {
            return publicSkillManager.getNames();
        }
        Set<String> overriddenNames = getOverriddenNames();
        Set<String> seenNames = new LinkedHashSet<>();

        for (String name : tenantSkillManager.getNames()) {
            seenNames.add(name);
        }

        for (String name : publicSkillManager.getNames()) {
            if (!seenNames.contains(name) && !overriddenNames.contains(name)) {
                seenNames.add(name);
            }
        }

        List<String> result = new ArrayList<>(seenNames);
        result.sort(java.util.Comparator.naturalOrder());
        return result;
    }

    public void overrideSkill(String skillName) throws IOException {
        TenantContext ctx = TenantContextHolder.getCurrentTenant();
        if (ctx == null || !ctx.isTenantAware() || overlayDir == null) {
            throw new IllegalStateException("Cannot override skill without tenant context");
        }
        Path marker = overlayDir.resolve(skillName + ".override");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "");
    }

    public void revokeOverride(String skillName) throws IOException {
        if (overlayDir == null) {
            return;
        }
        Path marker = overlayDir.resolve(skillName + ".override");
        Files.deleteIfExists(marker);
    }

    public boolean isOverridden(String skillName) {
        if (overlayDir == null) {
            return false;
        }
        Path marker = overlayDir.resolve(skillName + ".override");
        return Files.exists(marker);
    }

    private boolean hasTenantSkill(String skillName, Path tenantSkillRoot) {
        Path skillDir = tenantSkillRoot.resolve(skillName);
        return Files.isDirectory(skillDir);
    }

    private Set<String> getOverriddenNames() {
        Set<String> names = new HashSet<>();
        if (overlayDir == null || !Files.isDirectory(overlayDir)) {
            return names;
        }
        try (var stream = Files.list(overlayDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".override"))
                  .forEach(p -> {
                      String name = p.getFileName().toString().replace(".override", "");
                      names.add(name);
                  });
        } catch (IOException e) {
            Loggers.TOOL.warn("Failed to list overlay skill directory: {}", overlayDir, e);
        }
        return names;
    }

    public void migrateImplicitOverrides() {
        TenantContext ctx = TenantContextHolder.getCurrentTenant();
        if (ctx == null || !ctx.isTenantAware() || workspaceResolver == null || overlayDir == null) {
            return;
        }
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(ctx);
        if (tenantSkillRoot == null || !Files.isDirectory(tenantSkillRoot)) {
            return;
        }
        for (String publicName : publicSkillManager.getNames()) {
            Path tenantSkillDir = tenantSkillRoot.resolve(publicName);
            if (Files.isDirectory(tenantSkillDir) && !isOverridden(publicName)) {
                try {
                    overrideSkill(publicName);
                } catch (IOException e) {
                    Loggers.TOOL.warn("Failed to migrate implicit override for skill: {}", publicName, e);
                }
            }
        }
    }
}
