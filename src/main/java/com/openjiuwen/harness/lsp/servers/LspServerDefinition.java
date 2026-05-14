/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.servers;

import java.util.List;

/**
 * Definition for one builtin Java harness LSP server.
 *
 * <p>Mirrors Python's {@code ServerDefinition} in {@code openjiuwen.harness.lsp.servers.types}.
 */
public class LspServerDefinition {

    private final String id;
    private final List<String> extensions;
    private final String languageId;
    private final int priority;
    private final boolean globalServer;
    private final LspRootResolver rootResolver;

    public LspServerDefinition(String id,
                               List<String> extensions,
                               String languageId,
                               int priority,
                               boolean globalServer,
                               LspRootResolver rootResolver) {
        this.id = id;
        this.extensions = extensions != null ? List.copyOf(extensions) : List.of();
        this.languageId = languageId;
        this.priority = priority;
        this.globalServer = globalServer;
        this.rootResolver = rootResolver;
    }

    public String getId() {
        return id;
    }

    public List<String> getExtensions() {
        return extensions;
    }

    public String getLanguageId() {
        return languageId;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isGlobalServer() {
        return globalServer;
    }

    public LspRootResolver getRootResolver() {
        return rootResolver;
    }
}
