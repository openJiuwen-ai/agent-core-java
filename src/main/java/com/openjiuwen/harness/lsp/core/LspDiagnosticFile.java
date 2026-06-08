/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's {@code LspDiagnosticFile} in
 * {@code openjiuwen/harness/lsp/core/diagnostic_registry.py}.
 */
public class LspDiagnosticFile {

    private String uri;
    private List<LspDiagnosticItem> diagnostics = new ArrayList<>();

    @JsonProperty("server_name")
    private String serverName = "";

    @JsonProperty("local_path")
    private String localPath = "";

    public LspDiagnosticFile() {
    }

    public LspDiagnosticFile(String uri, List<LspDiagnosticItem> diagnostics, String serverName, String localPath) {
        this.uri = uri;
        setDiagnostics(diagnostics);
        this.serverName = serverName == null ? "" : serverName;
        this.localPath = localPath == null ? "" : localPath;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public List<LspDiagnosticItem> getDiagnostics() {
        return diagnostics;
    }

    public void setDiagnostics(List<LspDiagnosticItem> diagnostics) {
        this.diagnostics = diagnostics == null ? new ArrayList<>() : new ArrayList<>(diagnostics);
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName == null ? "" : serverName;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath == null ? "" : localPath;
    }
}
