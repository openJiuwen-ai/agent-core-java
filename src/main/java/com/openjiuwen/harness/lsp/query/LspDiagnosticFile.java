/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.query;

import java.util.List;

/**
 * Pending diagnostics grouped by file.
 *
 * <p>Mirrors Python's {@code LspDiagnosticFile} in
 * {@code openjiuwen.harness.lsp.core.diagnostic_registry}.
 */
public class LspDiagnosticFile {

    private final String filePath;
    private final String fileUri;
    private final String serverName;
    private final List<LspDiagnostic> diagnostics;

    public LspDiagnosticFile(String filePath, String fileUri, String serverName, List<LspDiagnostic> diagnostics) {
        this.filePath = filePath;
        this.fileUri = fileUri;
        this.serverName = serverName;
        this.diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
    }

    public String getFilePath() {
        return filePath;
    }

    public String getFileUri() {
        return fileUri;
    }

    public String getServerName() {
        return serverName;
    }

    public List<LspDiagnostic> getDiagnostics() {
        return diagnostics;
    }
}
