/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.query;

import java.util.List;

/**
 * Minimal incoming call hierarchy edge DTO for Java harness LSP.
 *
 * <p>Mirrors the Python-side incoming call shape consumed by
 * {@code openjiuwen.harness.tools.lsp_tool._formatter}.
 */
public class LspIncomingCall {

    private final LspCallHierarchyItem from;
    private final List<LspRange> fromRanges;

    public LspIncomingCall(LspCallHierarchyItem from, List<LspRange> fromRanges) {
        this.from = from;
        this.fromRanges = fromRanges != null ? List.copyOf(fromRanges) : List.of();
    }

    public LspCallHierarchyItem getFrom() {
        return from;
    }

    public List<LspRange> getFromRanges() {
        return fromRanges;
    }
}
