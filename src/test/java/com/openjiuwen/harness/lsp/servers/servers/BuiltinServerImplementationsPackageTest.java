/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.servers.servers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's package-export coverage for
 * {@code openjiuwen/harness/lsp/servers/servers/__init__.py}.
 */
class BuiltinServerImplementationsPackageTest {

    @Test
    void exposesBuiltinImplementationModulesInPythonExportOrder() {
        assertThat(BuiltinServerImplementationsPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen.harness.lsp.servers.servers");
        assertThat(BuiltinServerImplementationsPackage.GO).isEqualTo(GoBuiltinServer.class);
        assertThat(BuiltinServerImplementationsPackage.JAVA).isEqualTo(JavaBuiltinServer.class);
        assertThat(BuiltinServerImplementationsPackage.PYTHON).isEqualTo(PythonBuiltinServer.class);
        assertThat(BuiltinServerImplementationsPackage.RUST).isEqualTo(RustBuiltinServer.class);
        assertThat(BuiltinServerImplementationsPackage.TYPESCRIPT).isEqualTo(TypeScriptBuiltinServer.class);
        assertThat(BuiltinServerImplementationsPackage.exportedModules()).containsExactly(
                GoBuiltinServer.class,
                JavaBuiltinServer.class,
                PythonBuiltinServer.class,
                RustBuiltinServer.class,
                TypeScriptBuiltinServer.class
        );
    }
}
