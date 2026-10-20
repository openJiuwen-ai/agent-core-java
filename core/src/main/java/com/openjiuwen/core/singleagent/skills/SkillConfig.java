/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Skill security and loading configuration.
 *
 * @since 0.1.17
 */
public final class SkillConfig {

    public static final boolean DEFAULT_PROTECTED_OVERRIDE_BLOCKED = true;
    public static final int DEFAULT_WORKSPACE_MAX_BYTES = 262144;
    public static final boolean DEFAULT_ON_DEMAND_LOADING = true;
    public static final String PROPERTY_PROTECTED_OVERRIDE_BLOCKED =
            "skill.protected-override-blocked";
    public static final String PROPERTY_WORKSPACE_MAX_BYTES = "skill.workspace-max-bytes";
    public static final String PROPERTY_ON_DEMAND_LOADING = "skill.on-demand-loading";

    private boolean protectedOverrideBlocked = DEFAULT_PROTECTED_OVERRIDE_BLOCKED;
    private int workspaceMaxBytes = DEFAULT_WORKSPACE_MAX_BYTES;
    private boolean onDemandLoading = DEFAULT_ON_DEMAND_LOADING;

    /** Returns whether protected classpath Skills block higher-layer overrides. */
    public boolean isProtectedOverrideBlocked() {
        return protectedOverrideBlocked;
    }

    /** Sets the protected-classpath override policy. */
    public void setProtectedOverrideBlocked(boolean blocked) {
        this.protectedOverrideBlocked = blocked;
    }

    /** Returns the workspace SKILL.md size limit in bytes. */
    public int getWorkspaceMaxBytes() {
        return workspaceMaxBytes;
    }

    /** Sets the workspace SKILL.md size limit in bytes. */
    public void setWorkspaceMaxBytes(int maxBytes) {
        this.workspaceMaxBytes = maxBytes;
    }

    /** Returns whether Skill bodies are loaded through {@code skill_tool}. */
    public boolean isOnDemandLoading() {
        return onDemandLoading;
    }

    /** Sets the on-demand Skill loading mode. */
    public void setOnDemandLoading(boolean onDemand) {
        this.onDemandLoading = onDemand;
    }

    /** Builds configuration from Java system properties and environment variables. */
    public static SkillConfig fromEnvironment() {
        return fromSources(System.getProperties(), System.getenv());
    }

    /** Builds configuration from explicit property and environment sources. */
    public static SkillConfig fromSources(Properties properties, Map<String, String> environment) {
        SkillConfig config = new SkillConfig();
        Optional<Boolean> protectedBlocked =
                booleanValue(
                        value(
                                properties,
                                environment,
                                PROPERTY_PROTECTED_OVERRIDE_BLOCKED,
                                "SKILL_PROTECTED_OVERRIDE_BLOCKED"));
        if (protectedBlocked.isPresent()) {
            config.setProtectedOverrideBlocked(protectedBlocked.get());
        }
        Optional<Integer> maxBytes =
                intValue(
                        value(
                                properties,
                                environment,
                                PROPERTY_WORKSPACE_MAX_BYTES,
                                "SKILL_WORKSPACE_MAX_BYTES"));
        if (maxBytes.isPresent()) {
            config.setWorkspaceMaxBytes(maxBytes.get());
        }
        Optional<Boolean> onDemand =
                booleanValue(
                        value(
                                properties,
                                environment,
                                PROPERTY_ON_DEMAND_LOADING,
                                "SKILL_ON_DEMAND_LOADING"));
        if (onDemand.isPresent()) {
            config.setOnDemandLoading(onDemand.get());
        }
        return config;
    }

    private static Optional<String> value(
            Properties properties, Map<String, String> environment, String property, String env) {
        String propertyValue = properties == null ? null : properties.getProperty(property);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return Optional.of(propertyValue);
        }
        return environment == null ? Optional.empty() : Optional.ofNullable(environment.get(env));
    }

    private static Optional<Boolean> booleanValue(Optional<String> value) {
        if (value.isEmpty()) {
            return Optional.empty();
        }
        String normalized = value.get().trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "true" -> Optional.of(Boolean.TRUE);
            case "false" -> Optional.of(Boolean.FALSE);
            default -> Optional.empty();
        };
    }

    private static Optional<Integer> intValue(Optional<String> value) {
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            int parsed = Integer.parseInt(value.get().trim());
            return parsed > 0 ? Optional.of(parsed) : Optional.empty();
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
