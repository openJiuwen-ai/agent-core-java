/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.openjiuwen.core.session.Session;

/**
 * Drive message published to container topics by HandoffTeam.
 * <p>
 * Mirrors Python's {@code HandoffRequest} in 
 * {@code openjiuwen.core.multi_agent.teams.handoff.handoff_request}.
 * <p>
 * Attributes:
 * <ul>
 *     <li>inputMessage: User or intermediate input for the next agent hop</li>
 *     <li>history: Accumulated handoff history across hops</li>
 *     <li>sessionId: Session ID derived from attached session</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HandoffRequest {
    
    /** User or intermediate input for the next agent hop. */
    private Object inputMessage;
    
    /** Accumulated handoff history across hops. */
    private List<Map<String, Object>> history = new ArrayList<>();

    /** Team session for stream I/O. */
    private Session session;
    
    /** Explicit session ID for lightweight Java call paths. */
    private String sessionId;

    public HandoffRequest(Object inputMessage) {
        this(inputMessage, new ArrayList<>(), (String) null);
    }

    public HandoffRequest(Object inputMessage, List<Map<String, Object>> history) {
        this(inputMessage, history, (String) null);
    }

    public HandoffRequest(Object inputMessage, List<Map<String, Object>> history, String sessionId) {
        this.inputMessage = inputMessage;
        this.history = history != null ? history : new ArrayList<>();
        this.sessionId = sessionId;
        this.session = null;
    }

    public HandoffRequest(Object inputMessage, List<Map<String, Object>> history, Session session) {
        this.inputMessage = inputMessage;
        this.history = history != null ? history : new ArrayList<>();
        this.session = session;
        this.sessionId = null;
    }
    
    /**
     * Get session ID, returning empty string if not attached.
     * 
     * @return session ID or empty string
     */
    public String getSessionId() {
        if (session != null) {
            return session.getSessionId();
        }
        return sessionId != null ? sessionId : "";
    }
}
