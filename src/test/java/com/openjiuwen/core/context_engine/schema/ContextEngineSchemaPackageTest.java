/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.schema;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's package facade in
 * {@code openjiuwen/core/context_engine/schema/__init__.py}.
 */
class ContextEngineSchemaPackageTest {

    @Test
    void exportedSymbolsMatchPythonAllOrder() {
        assertThat(ContextEngineSchemaPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/core/context_engine/schema/__init__.py");

        assertThat(ContextEngineSchemaPackage.all()).containsExactly(
                "CONTEXT_COMPRESSION_STATE_TYPE",
                "ContextCompressionMetric",
                "ContextCompressionSaved",
                "ContextCompressionState",
                "ContextCompressionUsage"
        );
    }

    @Test
    void keyExportsMapToTranslatedJavaTypes() {
        assertThat(ContextEngineSchemaPackage.sourceFor("CONTEXT_COMPRESSION_STATE_TYPE"))
                .isEqualTo("openjiuwen.core.context_engine.schema.context_state.CONTEXT_COMPRESSION_STATE_TYPE");
        assertThat(ContextEngineSchemaPackage.javaTypeNameFor("CONTEXT_COMPRESSION_STATE_TYPE"))
                .isEqualTo(
                        "com.openjiuwen.core.context_engine.schema.ContextCompressionState"
                                + "#CONTEXT_COMPRESSION_STATE_TYPE"
                );
        assertThat(ContextEngineSchemaPackage.sourceFor("ContextCompressionState"))
                .isEqualTo("openjiuwen.core.context_engine.schema.context_state.ContextCompressionState");
        assertThat(ContextEngineSchemaPackage.javaTypeNameFor("ContextCompressionUsage"))
                .isEqualTo("com.openjiuwen.core.context_engine.schema.ContextCompressionUsage");
    }

    @Test
    void unknownSymbolIsNotExposed() {
        assertThat(ContextEngineSchemaPackage.exports("ContextEngineConfig")).isFalse();
        assertThat(ContextEngineSchemaPackage.sourceFor("missing")).isNull();
        assertThat(ContextEngineSchemaPackage.javaTypeNameFor("missing")).isNull();
    }

    @Test
    void exportedSymbolsAreImmutable() {
        assertThatThrownBy(() -> ContextEngineSchemaPackage.all().add("unexpected"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
