/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import com.openjiuwen.harness.rails.DeepAgentRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base rail for interrupt-and-resume handling.
 * <p>
 * Provides:
 * <ul>
 *   <li>Tool name registration</li>
 *   <li>User input extraction from context extras</li>
 *   <li>Decision application (approve / reject / interrupt)</li>
 *   <li>Auto-confirm key checking</li>
 * </ul>
 * <p>
 * Subclasses must implement {@link #resolveInterrupt} to define
 * the specific interruption logic.
 * <p>
 * Mirrors Python's {@code BaseInterruptRail} in
 * {@code openjiuwen.harness.rails.interrupt.interrupt_base}.
 */
public abstract class BaseInterruptRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(BaseInterruptRail.class);

    /** Rail priority (higher = runs later). */
    public static final int PRIORITY = 90;

    /** Reserved keys used in interrupt state. */
    public static final String RESUME_USER_INPUT_KEY = "resume_user_input";
    public static final String INTERRUPT_AUTO_CONFIRM_KEY = "interrupt_auto_confirm";

    private final Set<String> toolNames = ConcurrentHashMap.newKeySet();

    protected BaseInterruptRail() {
        this(null);
    }

    protected BaseInterruptRail(Iterable<String> toolNames) {
        if (toolNames != null) {
            for (String name : toolNames) {
                this.toolNames.add(name);
            }
        }
    }

    /** Register a tool name this rail should intercept. */
    public void addTool(String toolName) {
        toolNames.add(toolName);
    }

    /** Remove a tool name from interception. */
    public void removeTool(String toolName) {
        toolNames.remove(toolName);
    }

    /** Get the set of tool names this rail intercepts. */
    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(toolNames);
    }

    /** Check if a tool name is registered. */
    public boolean hasTool(String toolName) {
        return toolNames.contains(toolName);
    }

    /**
     * Check if auto-confirm is enabled for the given key.
     *
     * @param autoConfirmConfig map from extra context
     * @param key               tool-specific key
     * @return true if auto-confirmed
     */
    protected boolean isAutoConfirmed(Map<String, Object> autoConfirmConfig, String key) {
        if (autoConfirmConfig == null) {
            return false;
        }
        Object val = autoConfirmConfig.get(key);
        if (val == null) {
            // fallback to wildcard
            val = autoConfirmConfig.get("*");
        }
        return Boolean.TRUE.equals(val);
    }

    /**
     * Resolve the interrupt decision for a pending tool call.
     *
     * @param ctx             callback context (carries extras, session, etc.)
     * @param toolCall        the pending tool call (may be null)
     * @param userInput       user input from resume (null on first call)
     * @param autoConfirmConfig auto-confirm configuration map
     * @return an InterruptDecision (approve / reject / interrupt)
     */
    public abstract InterruptDecision resolveInterrupt(
            Object ctx,
            Object toolCall,
            Object userInput,
            Map<String, Object> autoConfirmConfig
    );
}
