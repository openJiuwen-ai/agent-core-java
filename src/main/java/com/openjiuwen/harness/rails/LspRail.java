/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.harness.lsp.HarnessLspPackage;

/**
 * Injects pending LSP diagnostics into model context.
 *
 * <p>Mirrors Python's {@code LspRail} in
 * {@code openjiuwen/harness/rails/lsp_rail.py}.</p>
 */
public class LspRail extends DeepAgentRail {

    private final int maxPerFile;
    private final int maxTotal;

    public LspRail() {
        this(10, 30);
    }

    public LspRail(int maxPerFile, int maxTotal) {
        setPriority(35);
        this.maxPerFile = maxPerFile;
        this.maxTotal = maxTotal;
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        ctx.put("lsp_diagnostics", HarnessLspPackage.getPendingLspDiagnostics(maxPerFile, maxTotal));
    }
}
