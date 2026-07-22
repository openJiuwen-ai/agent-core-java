package com.openjiuwen.core.multitenant.workspace;

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
