/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;

/**
 * Helper utilities for session management.
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/session/internal/workflow.py 中的辅助函数
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public final class SessionHelper {
    
    private SessionHelper() {
        // Utility class
    }
    
    /**
     * Creates a parent ID from a session.
     * 
     * <p>If the session is a NodeSession, returns its executable ID.
     * Otherwise, returns an empty string.
     * 
     * @param session the session
     * @return the parent ID
     */
    public static String createParentId(BaseSession session) {
        if (session instanceof NodeSession nodeSession) {
            return nodeSession.getExecutableId();
        }
        return "";
    }
    
    /**
     * Creates an executable ID from a node ID and parent ID.
     * 
     * <p>If the parent ID is not empty, the executable ID is "parentId.nodeId".
     * Otherwise, the executable ID is just the node ID.
     * 
     * @param nodeId the node ID
     * @param parentId the parent ID
     * @return the executable ID
     */
    public static String createExecutableId(String nodeId, String parentId) {
        if (parentId != null && !parentId.isEmpty()) {
            return parentId + "." + nodeId;
        }
        return nodeId;
    }
}

