/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantWorkspaceResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Public class ListSkillTool used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class ListSkillTool {
    private final Path skillsRoot;
    private final TenantWorkspaceResolver workspaceResolver;
    private final OverlaySkillManager overlaySkillManager;

    public ListSkillTool(String skillsRoot) {
        this(skillsRoot, null, null);
    }

    public ListSkillTool(String skillsRoot, TenantWorkspaceResolver workspaceResolver) {
        this(skillsRoot, workspaceResolver, null);
    }

    public ListSkillTool(String skillsRoot, TenantWorkspaceResolver workspaceResolver,
            OverlaySkillManager overlaySkillManager) {
        this.skillsRoot = Path.of(skillsRoot).toAbsolutePath().normalize();
        this.workspaceResolver = workspaceResolver;
        this.overlaySkillManager = overlaySkillManager;
    }

    /**
     * listSkills.
     *
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput listSkills() {
        List<String> skills;
        if (overlaySkillManager != null) {
            skills = overlaySkillManager.getAllVisibleSkillNames();
        } else {
            skills = collectDefaultSkills();
        }
        return ToolOutput.builder().success(true).data(skills).build();
    }

    private List<String> collectDefaultSkills() {
        List<String> skills = listDirectoryNames(skillsRoot);
        TenantContext tenantCtx = TenantContextHolder.getCurrentTenant();
        if (tenantCtx == null || !tenantCtx.isTenantAware() || workspaceResolver == null) {
            return skills;
        }
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(tenantCtx);
        if (tenantSkillRoot == null || !Files.isDirectory(tenantSkillRoot)) {
            return skills;
        }
        List<String> tenantSkills = listDirectoryNames(tenantSkillRoot);
        List<String> merged = new ArrayList<>(skills);
        for (String ts : tenantSkills) {
            if (!merged.contains(ts)) {
                merged.add(ts);
            }
        }
        merged.sort(Comparator.naturalOrder());
        return merged;
    }

    private List<String> listDirectoryNames(Path root) {
        try (Stream<Path> stream = Files.list(root)) {
            return stream.filter(Files::isDirectory)
                .sorted(Comparator.comparing(Path::getFileName))
                .map(path -> path.getFileName().toString())
                .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }
}
