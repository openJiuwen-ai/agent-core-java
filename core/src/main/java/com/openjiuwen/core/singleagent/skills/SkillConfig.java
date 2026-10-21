/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Properties;

/**
 * Skill security and loading configuration.
 *
 * @since 0.1.17
 */
public final class SkillConfig {
    /** Default value for blocking protected-classpath overrides. */
    public static final boolean DEFAULT_IS_PROTECTED_OVERRIDE_BLOCKED = true;
    /** Default workspace SKILL.md size limit in bytes. */
    public static final int DEFAULT_WORKSPACE_MAX_BYTES = 262144;
    /** Default value for on-demand Skill loading. */
    public static final boolean DEFAULT_IS_ON_DEMAND_LOADING = true;
    /** System property for the protected-classpath override policy. */
    public static final String PROPERTY_PROTECTED_OVERRIDE_BLOCKED =
            "skill.protected-override-blocked";
    /** System property for the workspace SKILL.md size limit. */
    public static final String PROPERTY_WORKSPACE_MAX_BYTES = "skill.workspace-max-bytes";
    /** System property for on-demand Skill loading. */
    public static final String PROPERTY_ON_DEMAND_LOADING = "skill.on-demand-loading";

    private boolean isProtectedOverrideBlocked = DEFAULT_IS_PROTECTED_OVERRIDE_BLOCKED;
    private int workspaceMaxBytes = DEFAULT_WORKSPACE_MAX_BYTES;
    private boolean isOnDemandLoading = DEFAULT_IS_ON_DEMAND_LOADING;

    /**
     * Returns whether protected classpath Skills block higher-layer overrides.
     *
     * @return true when protected Skills cannot be overridden
     */
    public boolean isProtectedOverrideBlocked() {
        return isProtectedOverrideBlocked;
    }

    /**
     * Sets the protected-classpath override policy.
     *
     * @param isBlocked whether protected Skills cannot be overridden
     */
    public void setProtectedOverrideBlocked(boolean isBlocked) {
        this.isProtectedOverrideBlocked = isBlocked;
    }

    /**
     * Returns the workspace SKILL.md size limit in bytes.
     *
     * @return maximum accepted SKILL.md size
     */
    public int getWorkspaceMaxBytes() {
        return workspaceMaxBytes;
    }

    /**
     * Sets the workspace SKILL.md size limit in bytes.
     *
     * @param maxBytes maximum accepted SKILL.md size
     */
    public void setWorkspaceMaxBytes(int maxBytes) {
        this.workspaceMaxBytes = maxBytes;
    }

    /**
     * Returns whether Skill bodies are loaded through {@code skill_tool}.
     *
     * @return true when Skill bodies are loaded on demand
     */
    public boolean isOnDemandLoading() {
        return isOnDemandLoading;
    }

    /**
     * Sets the on-demand Skill loading mode.
     *
     * @param isOnDemand whether Skill bodies are loaded on demand
     */
    public void setOnDemandLoading(boolean isOnDemand) {
        this.isOnDemandLoading = isOnDemand;
    }

    /**
     * Builds configuration from Java system properties and environment variables.
     *
     * @return resolved configuration with defaults for missing values
     */
    public static SkillConfig fromEnvironment() {
        return fromSources(System.getProperties(), System.getenv());
    }

    /**
     * Builds configuration from explicit property and environment sources.
     *
     * @param properties Java system-style property source
     * @param environment environment variable source
     * @return resolved configuration with defaults for missing values
     */
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
        OptionalInt maxBytes =
                intValue(
                        value(
                                properties,
                                environment,
                                PROPERTY_WORKSPACE_MAX_BYTES,
                                "SKILL_WORKSPACE_MAX_BYTES"));
        if (maxBytes.isPresent()) {
            config.setWorkspaceMaxBytes(maxBytes.getAsInt());
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

    private static OptionalInt intValue(Optional<String> value) {
        if (value.isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            int parsed = Integer.parseInt(value.get().trim());
            return parsed > 0 ? OptionalInt.of(parsed) : OptionalInt.empty();
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }
}
