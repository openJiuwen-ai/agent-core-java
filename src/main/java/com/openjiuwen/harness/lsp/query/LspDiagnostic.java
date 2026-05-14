/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.query;

/**
 * Minimal LSP diagnostic DTO.
 *
 * <p>Java-side supporting type for diagnostics mirrored from Python harness
 * LSP tool outputs.
 */
public class LspDiagnostic {

    private final String severity;
    private final String message;
    private final LspLocation location;

    public LspDiagnostic(String severity, String message, LspLocation location) {
        this.severity = severity;
        this.message = message;
        this.location = location;
    }

    public String getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public LspLocation getLocation() {
        return location;
    }
}
