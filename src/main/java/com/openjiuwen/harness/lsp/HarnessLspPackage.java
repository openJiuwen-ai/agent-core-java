/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp;

import com.openjiuwen.harness.lsp.core.LspDiagnosticFile;
import com.openjiuwen.harness.lsp.core.LspDiagnosticRegistry;
import com.openjiuwen.harness.lsp.core.LSPServerManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Module facade for harness LSP runtime helpers.
 *
 * <p>Mirrors Python's {@code openjiuwen/harness/lsp/__init__.py}.</p>
 */
public final class HarnessLspPackage {

    public static final String VERSION = "0.1.10";

    private HarnessLspPackage() {
    }

    public static List<LspDiagnosticFile> getPendingLspDiagnostics(int maxPerFile, int maxTotal) {
        return LspDiagnosticRegistry.getInstance().getAndClear(maxPerFile, maxTotal);
    }

    public static CompletableFuture<InitializeResult> initializeLsp(InitializeOptions options) {
        return CompletableFuture.completedFuture(LSPServerManager.initialize(options));
    }

    public static CompletableFuture<Void> shutdownLsp() {
        LSPServerManager.shutdown();
        return CompletableFuture.completedFuture(null);
    }

    public static Object getLspTool() {
        return null;
    }

    public static LspStatus getLspStatus() {
        return LSPServerManager.getGlobalStatus();
    }
}
