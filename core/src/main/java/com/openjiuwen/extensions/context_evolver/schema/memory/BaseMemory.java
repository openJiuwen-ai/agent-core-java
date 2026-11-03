/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema.memory;

import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;

/**
 * Mirrors Python's {@code BaseMemory} in
 * {@code openjiuwen/extensions/context_evolver/schema/memory.py}.
 */
public abstract class BaseMemory {

    private String content;
    private String workspaceId = "default";

    protected BaseMemory() {
    }

    protected BaseMemory(String content, String workspaceId) {
        this.content = content;
        setWorkspaceId(workspaceId);
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId != null ? workspaceId : "default";
    }

    public abstract VectorNode toVectorNode();

    public static BaseMemory fromVectorNode(VectorNode node) {
        throw new UnsupportedOperationException("Subclasses must implement fromVectorNode");
    }
}
