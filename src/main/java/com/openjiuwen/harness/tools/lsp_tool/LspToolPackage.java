/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.lsp_tool;

import java.util.List;

/**
 * Package facade for the LSP tool module.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.tools.lsp_tool} in
 * {@code openjiuwen/harness/tools/lsp_tool/__init__.py}.</p>
 */
public final class LspToolPackage {

    private LspToolPackage() {
    }

    public static List<String> exportedSymbols() {
        return List.of("LspTool", "LspOperation", "LspToolOutput", "LspResultFormatter");
    }
}
