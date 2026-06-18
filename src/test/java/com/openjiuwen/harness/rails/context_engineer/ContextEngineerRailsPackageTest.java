/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.context_engineer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for context engineer package exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.rails.context_engineer} package facade in
 * {@code openjiuwen/harness/rails/context_engineer/__init__.py}.</p>
 */
class ContextEngineerRailsPackageTest {

    @Test
    void exposesPythonAllInOrder() {
        assertThat(ContextEngineerRailsPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/harness/rails/context_engineer/__init__.py");
        assertThat(ContextEngineerRailsPackage.all()).containsExactlyElementsOf(List.of(
                "ContextProcessorRail",
                "ContextAssembleRail"
        ));
        assertThat(ContextEngineerRailsPackage.all()).isSameAs(ContextEngineerRailsPackage.EXPORTED_SYMBOLS);
    }

    @Test
    void exposesRailTypesInPythonAllOrder() {
        assertThat(ContextEngineerRailsPackage.exports()).containsExactly(
                ContextProcessorRail.class,
                ContextAssembleRail.class
        );
        assertThat(ContextEngineerRailsPackage.typeFor("ContextProcessorRail")).isEqualTo(ContextProcessorRail.class);
        assertThat(ContextEngineerRailsPackage.typeFor("ContextAssembleRail")).isEqualTo(ContextAssembleRail.class);
    }

    @Test
    void missingSymbolIsNotExported() {
        assertThat(ContextEngineerRailsPackage.exports("missing")).isFalse();
        assertThat(ContextEngineerRailsPackage.typeFor("missing")).isNull();
    }
}
