/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantWorkspaceResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class SkillTool used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class SkillTool {
    private final Path skillsRoot;
    private final TenantWorkspaceResolver workspaceResolver;
    private final OverlaySkillManager overlaySkillManager;

    public SkillTool(String skillsRoot) {
        this(skillsRoot, null, null);
    }

    public SkillTool(String skillsRoot, TenantWorkspaceResolver workspaceResolver) {
        this(skillsRoot, workspaceResolver, null);
    }

    public SkillTool(String skillsRoot, TenantWorkspaceResolver workspaceResolver,
            OverlaySkillManager overlaySkillManager) {
        this.skillsRoot = Path.of(skillsRoot).toAbsolutePath().normalize();
        this.workspaceResolver = workspaceResolver;
        this.overlaySkillManager = overlaySkillManager;
    }

    /**
     * readSkill.
     *
     * @param skillName skillName
     * @param relativeFilePath relativeFilePath
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput readSkill(String skillName, String relativeFilePath) {
        if (overlaySkillManager != null) {
            Path tenantTarget = overlaySkillManager.resolveSkillFile(skillName, relativeFilePath);
            if (tenantTarget != null) {
                return readSkillFile(tenantTarget.getParent(), tenantTarget);
            }
        } else {
            ToolOutput tenantResult = readTenantSkill(skillName, relativeFilePath);
            if (tenantResult != null) {
                return tenantResult;
            }
        }
        String fileName = (relativeFilePath == null || relativeFilePath.isBlank()) ? "SKILL.md" : relativeFilePath;
        Path skillDir = skillsRoot.resolve(skillName).normalize();
        Path target = skillDir.resolve(fileName).normalize();
        return readSkillFile(skillDir, target);
    }

    private ToolOutput readTenantSkill(String skillName, String relativeFilePath) {
        TenantContext tenantCtx = TenantContextHolder.getCurrentTenant();
        if (tenantCtx == null || !tenantCtx.isTenantAware() || workspaceResolver == null) {
            return null;
        }
        Path tenantSkillRoot = workspaceResolver.resolveSkillRoot(tenantCtx);
        if (tenantSkillRoot == null) {
            return null;
        }
        Path tenantSkillDir = tenantSkillRoot.resolve(skillName).normalize();
        Path tenantTarget = tenantSkillDir.resolve(
            (relativeFilePath == null || relativeFilePath.isBlank())
                ? "SKILL.md" : relativeFilePath).normalize();
        if (!Files.exists(tenantTarget)) {
            return null;
        }
        return readSkillFile(tenantSkillDir, tenantTarget);
    }

    private ToolOutput readSkillFile(Path skillDir, Path target) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("skill_directory", skillDir.toString());
            payload.put("skill_content", Files.readString(target, StandardCharsets.UTF_8));
            return ToolOutput.builder().success(true).data(payload).build();
        } catch (IOException ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }
}
