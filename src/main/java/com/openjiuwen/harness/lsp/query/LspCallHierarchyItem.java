/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.query;

import java.util.Objects;

/**
 * Minimal call hierarchy item DTO for Java harness LSP.
 *
 * <p>Mirrors the Python-side call hierarchy item shape consumed by
 * {@code openjiuwen.harness.tools.lsp_tool._formatter}.
 */
public class LspCallHierarchyItem {

    private final String name;
    private final String kind;
    private final String detail;
    private final String uri;
    private final LspRange range;
    private final LspRange selectionRange;

    public LspCallHierarchyItem(String name, LspLocation location) {
        this(name, location, location);
    }

    public LspCallHierarchyItem(String name, LspLocation location, LspLocation selectionLocation) {
        this(name, "", "", location != null ? location.getFilePath() : "",
                new LspRange(location, location),
                new LspRange(selectionLocation, selectionLocation));
    }

    public LspCallHierarchyItem(String name, String detail, LspLocation location, LspLocation selectionLocation,
                                LspRange range, LspRange selectionRange) {
        this(name, "", detail, location != null ? location.getFilePath() : "", range, selectionRange);
    }

    public LspCallHierarchyItem(String name, String kind, String detail, String uri, LspRange range, LspRange selectionRange) {
        this.name = name;
        this.kind = kind == null ? "" : kind;
        this.detail = detail == null ? "" : detail;
        this.uri = uri == null ? "" : uri;
        this.range = range;
        this.selectionRange = selectionRange;
    }

    public String getName() {
        return name;
    }

    public String getDetail() {
        return detail;
    }

    public String getKind() {
        return kind;
    }

    public String getUri() {
        return uri;
    }

    public LspLocation getLocation() {
        return range != null ? range.getStart() : null;
    }

    public LspLocation getSelectionLocation() {
        return selectionRange != null ? selectionRange.getStart() : getLocation();
    }

    public LspRange getRange() {
        return range;
    }

    public LspRange getSelectionRange() {
        return selectionRange;
    }

    public String getSelectionUri() {
        LspLocation selectionLocation = getSelectionLocation();
        return selectionLocation != null ? selectionLocation.getFilePath() : getUri();
    }
}
