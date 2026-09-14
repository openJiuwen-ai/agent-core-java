/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.openjiuwen.harness.workspace.Workspace;

/**
 * Serializable workspace specification.
 *
 * <p>Mirrors Python's {@code WorkspaceSpec} in
 * {@code openjiuwen/agent_teams/schema/deep_agent_spec.py}.</p>
 */
public class WorkspaceSpec {

    private String rootPath = "./";
    private String language = "cn";
    private boolean stableBase;

    public Workspace build() {
        return new Workspace(rootPath, language);
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath == null ? "./" : rootPath;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language == null ? "cn" : language;
    }

    public boolean isStableBase() {
        return stableBase;
    }

    public void setStableBase(boolean stableBase) {
        this.stableBase = stableBase;
    }
}
