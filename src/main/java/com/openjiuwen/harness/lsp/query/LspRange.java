/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.query;

/**
 * Minimal range DTO for Java harness LSP.
 *
 * <p>Mirrors the Python-side range semantics consumed by
 * {@code openjiuwen.harness.tools.lsp_tool._formatter}.
 */
public class LspRange {

    private final LspLocation start;
    private final LspLocation end;

    public LspRange(LspLocation start, LspLocation end) {
        this.start = start;
        this.end = end;
    }

    public LspLocation getStart() {
        return start;
    }

    public LspLocation getEnd() {
        return end;
    }
}
