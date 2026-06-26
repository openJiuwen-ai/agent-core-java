/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.servers.servers;

import java.util.List;

/**
 * Package bridge for builtin LSP server implementations.
 *
 * <p>Mirrors Python's package exports in
 * {@code openjiuwen/harness/lsp/servers/servers/__init__.py}.</p>
 */
public final class BuiltinServerImplementationsPackage {

    public static final String PYTHON_MODULE = "openjiuwen.harness.lsp.servers.servers";
    public static final Class<?> GO = GoBuiltinServer.class;
    public static final Class<?> JAVA = JavaBuiltinServer.class;
    public static final Class<?> PYTHON = PythonBuiltinServer.class;
    public static final Class<?> RUST = RustBuiltinServer.class;
    public static final Class<?> TYPESCRIPT = TypeScriptBuiltinServer.class;

    private BuiltinServerImplementationsPackage() {
    }

    public static List<Class<?>> exportedModules() {
        return List.of(GO, JAVA, PYTHON, RUST, TYPESCRIPT);
    }
}
