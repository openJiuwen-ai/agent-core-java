/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantWorkspaceResolver;
import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.core.singleagent.skills.SkillManager;
import com.openjiuwen.core.sysop.BaseFsOperation;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.local.LocalFsOperation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * OverlaySkillManager.
 *
 * @since 0.1.7
 */
public class OverlaySkillManager {
    private final SkillManager tenantSkillManager;
    private final SkillManager publicSkillManager;
    private final Path overlayDir;
    private final TenantWorkspaceResolver workspaceResolver;

    // 按 tenantId 缓存的租户技能管理器，避免并发请求时共享 tenantSkillManager 互相覆盖
    private final ConcurrentHashMap<String, SkillManager> tenantSkillManagerCache = new ConcurrentHashMap<>();

    public OverlaySkillManager(SkillManager tenantSkillManager, SkillManager publicSkillManager,
                               Path overlayDir, TenantWorkspaceResolver workspaceResolver) {
        this.tenantSkillManager = tenantSkillManager;
        this.publicSkillManager = publicSkillManager;
        this.overlayDir = overlayDir;
        this.workspaceResolver = workspaceResolver;
    }

    /**
     * 获取（或按需创建并刷新）当前租户专属的 SkillManager。
     * <p>
     * 多租户并发场景下，不同租户的技能互不可见。使用按 tenantId 缓存的独立 SkillManager，
     * 避免共享 {@link #tenantSkillManager} 被并发刷新互相覆盖。刷新采用增量方式，
     * 文件未变更时为近乎无操作。
     *
     * @param ctx 当前租户上下文
     * @param tenantSkillRoot 当前租户的技能根目录
     * @return 当前租户专属的 SkillManager
     */
    private SkillManager getOrRefreshTenantSkillManager(TenantContext ctx, Path tenantSkillRoot) {
        String tenantId = ctx.getTenantId();
        // develop SkillManager registers via BaseFsOperation (730 used java.nio.Files).
        // Per-tenant cache managers must carry a local FS resolver.
        Function<String, BaseFsOperation> fsResolver = id ->
                new LocalFsOperation(id, OperationMode.LOCAL, "", new LocalWorkConfig());
        SkillManager manager = tenantSkillManagerCache.computeIfAbsent(tenantId,
                id -> new SkillManager("tenant." + id, fsResolver));
        try {
            manager.refreshIncrementally(List.of(tenantSkillRoot));
        } catch (RuntimeException e) {
            Loggers.TOOL.warn("Failed to refresh tenant skills for {}: {}", tenantId, e.getMessage());
        }
        return manager;
    }

    /**
     * resolveSkillFile.
     *
     * @param skillName skillName
     * @param relativeFilePath relativeFilePath
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
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

    /**
     * getAllVisibleSkills.
     *
     * @return the result
     * @since 0.1.7
     */
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

        SkillManager perTenant = getOrRefreshTenantSkillManager(ctx, tenantSkillRoot);
        for (Skill skill : perTenant.getAll()) {
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

    /**
     * getAllVisibleSkillNames.
     *
     * @return the result
     * @since 0.1.7
     */
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

        SkillManager perTenant = getOrRefreshTenantSkillManager(ctx, tenantSkillRoot);
        for (String name : perTenant.getNames()) {
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

    /**
     * overrideSkill.
     *
     * @param skillName skillName
     * @throws IOException IOException
     * @since 0.1.7
     */
    public void overrideSkill(String skillName) throws IOException {
        TenantContext ctx = TenantContextHolder.getCurrentTenant();
        if (ctx == null || !ctx.isTenantAware() || overlayDir == null) {
            throw new IllegalStateException("Cannot override skill without tenant context");
        }
        Path marker = overlayDir.resolve(skillName + ".override");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "");
    }

    /**
     * revokeOverride.
     *
     * @param skillName skillName
     * @throws IOException IOException
     * @since 0.1.7
     */
    public void revokeOverride(String skillName) throws IOException {
        if (overlayDir == null) {
            return;
        }
        Path marker = overlayDir.resolve(skillName + ".override");
        Files.deleteIfExists(marker);
    }

    /**
     * isOverridden.
     *
     * @param skillName skillName
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * migrateImplicitOverrides.
     *
     * @since 0.1.7
     */
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
