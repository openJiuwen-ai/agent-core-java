/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import java.util.Optional;

/**
 * Immutable handoff directive.
 * <p>
 * Mirrors Python's {@code HandoffSignal} in 
 * {@code openjiuwen.core.multi_agent.teams.handoff.handoff_signal}.
 * <p>
 * Produced by extract_handoff_signal function.
 * <p>
 * Attributes:
 * <ul>
 *     <li>target: ID of the target agent</li>
 *     <li>message: Optional context message forwarded to the target agent</li>
 *     <li>reason: Optional human-readable reason for the handoff</li>
 * </ul>
 */
public final class HandoffSignal {
    
    public static final String HANDOFF_TARGET_KEY = "__handoff_to__";
    public static final String HANDOFF_MESSAGE_KEY = "__handoff_message__";
    public static final String HANDOFF_REASON_KEY = "__handoff_reason__";
    
    private final String target;
    private final String message;
    private final String reason;
    
    public HandoffSignal(String target) {
        this(target, null, null);
    }
    
    public HandoffSignal(String target, String message, String reason) {
        this.target = target;
        this.message = message;
        this.reason = reason;
    }
    
    public String getTarget() { return target; }
    public Optional<String> getMessage() { return Optional.ofNullable(message); }
    public Optional<String> getReason() { return Optional.ofNullable(reason); }
    
    @Override
    public String toString() {
        return String.format("HandoffSignal(target=%s, message=%s, reason=%s)", 
                             target, message, reason);
    }
}