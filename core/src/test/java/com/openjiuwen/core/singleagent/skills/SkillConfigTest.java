/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

@Tag("issue196-w7")
class SkillConfigTest {
    @Test
    void fromSources_readsSystemPropertiesAndEnvironmentOverrides() {
        Properties properties = new Properties();
        properties.setProperty(SkillConfig.PROPERTY_PROTECTED_OVERRIDE_BLOCKED, "false");
        properties.setProperty(SkillConfig.PROPERTY_WORKSPACE_MAX_BYTES, "4096");
        properties.setProperty(SkillConfig.PROPERTY_ON_DEMAND_LOADING, "false");

        SkillConfig config = SkillConfig.fromSources(properties, Map.of());

        assertThat(config.isProtectedOverrideBlocked()).isFalse();
        assertThat(config.getWorkspaceMaxBytes()).isEqualTo(4096);
        assertThat(config.isOnDemandLoading()).isFalse();
    }

    @Test
    void fromSources_ignoresInvalidValuesAndKeepsSafeDefaults() {
        Properties properties = new Properties();
        properties.setProperty(SkillConfig.PROPERTY_PROTECTED_OVERRIDE_BLOCKED, "not-boolean");
        properties.setProperty(SkillConfig.PROPERTY_WORKSPACE_MAX_BYTES, "-1");
        properties.setProperty(SkillConfig.PROPERTY_ON_DEMAND_LOADING, "not-boolean");

        SkillConfig config = SkillConfig.fromSources(properties, Map.of());

        assertThat(config.isProtectedOverrideBlocked()).isTrue();
        assertThat(config.getWorkspaceMaxBytes())
                .isEqualTo(SkillConfig.DEFAULT_WORKSPACE_MAX_BYTES);
        assertThat(config.isOnDemandLoading()).isTrue();
    }
}
