/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.servers;

import com.openjiuwen.harness.lsp.core.SpawnHandle;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Mirrors Python's {@code ServerDefinition} in
 * {@code openjiuwen/harness/lsp/servers/types.py}.
 */
public class ServerDefinition {

    private String id;
    private List<String> extensions = List.of();
    private String languageId;
    private int priority = 100;
    private boolean globalServer;
    private Function<String, String> findRoot = value -> null;
    private Function<String, SpawnHandle> spawn = value -> null;

    public ServerDefinition() {
    }

    public ServerDefinition(
            String id,
            List<String> extensions,
            String languageId,
            int priority,
            boolean globalServer,
            Function<String, String> findRoot,
            Function<String, SpawnHandle> spawn
    ) {
        this.id = id;
        setExtensions(extensions);
        this.languageId = languageId;
        this.priority = priority;
        this.globalServer = globalServer;
        this.findRoot = Objects.requireNonNullElse(findRoot, value -> null);
        this.spawn = Objects.requireNonNullElse(spawn, value -> null);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<String> getExtensions() {
        return extensions;
    }

    public void setExtensions(List<String> extensions) {
        this.extensions = extensions == null ? List.of() : List.copyOf(extensions);
    }

    public String getLanguageId() {
        return languageId;
    }

    public void setLanguageId(String languageId) {
        this.languageId = languageId;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isGlobalServer() {
        return globalServer;
    }

    public void setGlobalServer(boolean globalServer) {
        this.globalServer = globalServer;
    }

    public Function<String, String> getFindRoot() {
        return findRoot;
    }

    public void setFindRoot(Function<String, String> findRoot) {
        this.findRoot = Objects.requireNonNullElse(findRoot, value -> null);
    }

    public Function<String, SpawnHandle> getSpawn() {
        return spawn;
    }

    public void setSpawn(Function<String, SpawnHandle> spawn) {
        this.spawn = Objects.requireNonNullElse(spawn, value -> null);
    }
}
