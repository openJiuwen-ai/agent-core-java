/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.servers;

import com.openjiuwen.harness.lsp.servers.servers.GoBuiltinServer;
import com.openjiuwen.harness.lsp.servers.servers.JavaBuiltinServer;
import com.openjiuwen.harness.lsp.servers.servers.PythonBuiltinServer;
import com.openjiuwen.harness.lsp.servers.servers.RustBuiltinServer;
import com.openjiuwen.harness.lsp.servers.servers.TypeScriptBuiltinServer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's package-export coverage for
 * {@code openjiuwen/harness/lsp/servers/__init__.py}.
 */
class LspServersPackageTest {

    @Test
    void exposesBuiltinServersRegistryAndBuiltinServerModules() {
        Map<String, ServerDefinition> builtinServers = LspServersPackage.builtinServers();

        assertThat(LspServersPackage.PYTHON_MODULE).isEqualTo("openjiuwen.harness.lsp.servers");
        assertThat(LspServersPackage.RUST).isEqualTo(RustBuiltinServer.class);
        assertThat(LspServersPackage.TYPESCRIPT).isEqualTo(TypeScriptBuiltinServer.class);
        assertThat(LspServersPackage.JAVA).isEqualTo(JavaBuiltinServer.class);
        assertThat(LspServersPackage.PYTHON).isEqualTo(PythonBuiltinServer.class);
        assertThat(LspServersPackage.GO).isEqualTo(GoBuiltinServer.class);
        assertThat(builtinServers).containsKeys("gopls", "jdtls", "pyright", "rust", "typescript");
    }
}
