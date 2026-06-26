/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the legacy controller package export facade.
 *
 * <p>Mirrors Python's {@code openjiuwen/core/controller/legacy/__init__.py}.</p>
 */
class LegacyControllerPackageTest {

    @Test
    void allPreservesPythonExportOrder() {
        List<String> expected = new ArrayList<>();
        expected.addAll(LegacyControllerPackage.CONTROLLER_CLASSES);
        expected.addAll(LegacyControllerPackage.INTENT_CLASSES);
        expected.addAll(LegacyControllerPackage.TASK_CLASSES);
        expected.addAll(LegacyControllerPackage.REASONER_CLASSES);
        expected.addAll(LegacyControllerPackage.EVENT_CLASSES);
        expected.addAll(LegacyControllerPackage.CONFIG_CLASSES);

        assertThat(LegacyControllerPackage.ALL).containsExactlyElementsOf(expected);
    }

    @Test
    void packageMetadataPointsToPythonSource() {
        assertThat(LegacyControllerPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/core/controller/legacy/__init__.py");
        assertThat(LegacyControllerPackage.DESCRIPTION)
                .isEqualTo("Controller legacy module - Agent controllers");
    }
}
