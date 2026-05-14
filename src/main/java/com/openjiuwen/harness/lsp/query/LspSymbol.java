/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.query;

import java.util.List;

/**
 * Minimal LSP symbol DTO.
 *
 * <p>Java-side supporting type for symbol and workspace-symbol queries.
 */
public class LspSymbol {

    private final String name;
    private final String kind;
    private final LspLocation location;
    private final LspRange range;
    private final LspRange selectionRange;
    private final String containerName;
    private final String detail;
    private final List<LspSymbol> children;

    public LspSymbol(String name, String kind, LspLocation location) {
        this(name, kind, location, null, null, "", "");
    }

    public LspSymbol(String name, String kind, LspLocation location, String containerName, String detail) {
        this(name, kind, location, null, null, containerName, detail, List.of());
    }

    public LspSymbol(String name, String kind, LspLocation location, String containerName, String detail, List<LspSymbol> children) {
        this(name, kind, location, null, null, containerName, detail, children);
    }

    public LspSymbol(String name, String kind, LspLocation location, LspRange range, LspRange selectionRange,
                     String containerName, String detail) {
        this(name, kind, location, range, selectionRange, containerName, detail, List.of());
    }

    public LspSymbol(String name, String kind, LspLocation location, LspRange range, LspRange selectionRange,
                     String containerName, String detail, List<LspSymbol> children) {
        this.name = name;
        this.kind = kind;
        this.location = location;
        this.range = range;
        this.selectionRange = selectionRange;
        this.containerName = containerName == null ? "" : containerName;
        this.detail = detail == null ? "" : detail;
        this.children = children != null ? List.copyOf(children) : List.of();
    }

    public String getName() {
        return name;
    }

    public String getKind() {
        return kind;
    }

    public LspLocation getLocation() {
        return location;
    }

    public LspRange getRange() {
        return range;
    }

    public LspRange getSelectionRange() {
        return selectionRange;
    }

    public String getContainerName() {
        return containerName;
    }

    public String getDetail() {
        return detail;
    }

    public List<LspSymbol> getChildren() {
        return children;
    }
}
