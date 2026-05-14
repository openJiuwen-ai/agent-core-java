/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.query;

import java.util.List;

/**
 * Minimal outgoing call hierarchy edge DTO for Java harness LSP.
 *
 * <p>Mirrors the Python-side outgoing call shape consumed by
 * {@code openjiuwen.harness.tools.lsp_tool._formatter}.
 */
public class LspOutgoingCall {

    private final LspCallHierarchyItem to;
    private final List<LspRange> fromRanges;

    public LspOutgoingCall(LspCallHierarchyItem to, List<LspRange> fromRanges) {
        this.to = to;
        this.fromRanges = fromRanges != null ? List.copyOf(fromRanges) : List.of();
    }

    public LspCallHierarchyItem getTo() {
        return to;
    }

    public List<LspRange> getFromRanges() {
        return fromRanges;
    }
}
