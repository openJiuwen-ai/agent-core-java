/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant.workspace;

/**
 * Classifies workspace sub-directories under a tenant namespace.
 *
 * @since 0.1.7
 */
public enum WorkspaceType {
    WORKSPACE(""),
    SKILLS("skills"),
    TMP("tmp"),
    CHECKPOINTS("checkpoints"),
    TEAM_MEMORY("team_memory"),
    TODO("todo");

    private final String subDirectory;

    WorkspaceType(String subDirectory) {
        this.subDirectory = subDirectory;
    }

    public String subDirectory() {
        return subDirectory;
    }
}
