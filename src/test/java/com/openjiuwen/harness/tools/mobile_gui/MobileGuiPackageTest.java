/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link MobileGuiRailsFactory} helpers that used to be reached only
 * through the deleted {@code MobileGuiPackage} export bridge.
 */
class MobileGuiPackageTest {

    @Test
    void railsFactoryHelpers() {
        MobileGuiRuntimeSettings settings = MobileGuiRuntimeSettings.fromEnvironment(
                Map.of("MULTIMODAL_SKILL_CONSULT_MODE", "none")
        );

        assertFalse(MobileGuiRailsFactory.buildMobileGuiRails(settings).isEmpty());
        assertEquals("model-x", MobileGuiRailsFactory.inferModelDisplayName("model-x"));
        assertTrue(MobileGuiRailsFactory.resolveMobileSkillRoot("workspace").replace('\\', '/')
                .endsWith("workspace/.skills"));
    }
}
