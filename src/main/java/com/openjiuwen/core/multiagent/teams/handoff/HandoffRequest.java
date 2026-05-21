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
import java.util.Optional;

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
    
    /** Session ID for stream I/O. */
    private String sessionId;
    
    /**
     * Get session ID, returning empty string if not attached.
     * 
     * @return session ID or empty string
     */
    public String getSessionId() {
        return sessionId != null ? sessionId : "";
    }
}