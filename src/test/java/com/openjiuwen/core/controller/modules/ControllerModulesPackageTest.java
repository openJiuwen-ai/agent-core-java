/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for the controller modules package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.controller.modules} in
 * {@code openjiuwen/core/controller/modules/__init__.py}.</p>
 */
class ControllerModulesPackageTest {

    @Test
    void exportedSymbolsMatchPythonAll() {
        assertThat(ControllerModulesPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/core/controller/modules/__init__.py");
        assertThat(ControllerModulesPackage.EXPORTED_SYMBOLS).containsExactlyElementsOf(List.of(
                "EventHandlerInput",
                "EventHandler",
                "EventQueue",
                "TaskManagerState",
                "TaskManager",
                "TaskFilter",
                "TaskExecutor",
                "TaskExecutorDependencies",
                "TaskExecutorRegistry",
                "TaskScheduler",
                "IntentRecognizer",
                "EventHandlerWithIntentRecognition"
        ));
    }

    @Test
    void exportedSymbolsDoNotIncludeNonExportedHelperClasses() {
        assertThat(ControllerModulesPackage.EXPORTED_SYMBOLS)
                .doesNotContain("IntentToolkits");
    }

    @Test
    void exportedSymbolsAreImmutable() {
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> ControllerModulesPackage.EXPORTED_SYMBOLS.add("IntentToolkits"));
    }

    @Test
    void packageFacadeCannotBeInstantiated() throws Exception {
        Constructor<ControllerModulesPackage> constructor =
                ControllerModulesPackage.class.getDeclaredConstructor();

        assertThat(Modifier.isFinal(ControllerModulesPackage.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
