/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.servers;

import com.openjiuwen.harness.lsp.servers.servers.GoBuiltinServer;
import com.openjiuwen.harness.lsp.servers.servers.JavaBuiltinServer;
import com.openjiuwen.harness.lsp.servers.servers.PythonBuiltinServer;
import com.openjiuwen.harness.lsp.servers.servers.RustBuiltinServer;
import com.openjiuwen.harness.lsp.servers.servers.TypeScriptBuiltinServer;

import java.util.Map;

/**
 * Package bridge for builtin LSP server definitions.
 *
 * <p>Mirrors Python's package exports in
 * {@code openjiuwen/harness/lsp/servers/__init__.py}.</p>
 */
public final class LspServersPackage {

    public static final String PYTHON_MODULE = "openjiuwen.harness.lsp.servers";
    public static final Class<?> RUST = RustBuiltinServer.class;
    public static final Class<?> TYPESCRIPT = TypeScriptBuiltinServer.class;
    public static final Class<?> JAVA = JavaBuiltinServer.class;
    public static final Class<?> PYTHON = PythonBuiltinServer.class;
    public static final Class<?> GO = GoBuiltinServer.class;

    private LspServersPackage() {
    }

    public static Map<String, ServerDefinition> builtinServers() {
        BuiltinServerRegistry.ensureBuiltinServersLoaded();
        return BuiltinServerRegistry.BUILTIN_SERVERS;
    }
}
