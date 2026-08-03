/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.controller;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the controller package export facade.
 *
 * <p>Mirrors Python's {@code openjiuwen/core/controller/__init__.py}.</p>
 */
class ControllerPackageTest {

    @Test
    void allPreservesPythonExportOrder() {
        List<String> expected = new ArrayList<>();
        expected.addAll(ControllerPackage.CONTROLLER_CLASSES);
        expected.addAll(ControllerPackage.INTENT_CLASSES);
        expected.addAll(ControllerPackage.TASK_CLASSES);
        expected.addAll(ControllerPackage.REASONER_CLASSES);
        expected.addAll(ControllerPackage.EVENT_CLASSES);
        expected.addAll(ControllerPackage.CONFIG_CLASSES);
        expected.addAll(ControllerPackage.NEW_CLASSES);

        assertThat(ControllerPackage.ALL).containsExactlyElementsOf(expected);
    }

    @Test
    void packageMetadataPointsToPythonSource() {
        assertThat(ControllerPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/core/controller/__init__.py");
        assertThat(ControllerPackage.DESCRIPTION)
                .isEqualTo("Controller module - Agent controllers");
    }
}
