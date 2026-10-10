/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.runbudget;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.defaults.DefaultLogger;
import com.openjiuwen.core.common.logging.events.LogEventType;

/**
 * Static facade for publishing FEAT-057 run-budget events.
 *
 * <p>{@code LoggerProtocol} does not expose {@code logEvent}, so the facade resolves the
 * "agent" logger from {@link LogManager} and uses the structured-event entry point when the
 * logger is a {@link DefaultLogger}; with a host-registered custom logger it degrades to a
 * structured {@code info} line instead of failing.</p>
 *
 * @since 2026-10-08
 */
public final class RunBudgetEvents {
    private static final String AGENT_LOG_TYPE = "agent";

    private RunBudgetEvents() {
    }

    /**
     * Publishes a run-budget event through the agent logger.
     *
     * @param message human-readable summary (ASCII)
     * @param eventType event type
     * @param event event payload
     */
    public static void emit(String message, LogEventType eventType, RunBudgetEvent event) {
        LoggerProtocol logger = LogManager.getLogger(AGENT_LOG_TYPE);
        if (logger instanceof DefaultLogger defaultLogger) {
            defaultLogger.logEvent(message, eventType, event);
            return;
        }
        logger.info("{}: {}", eventType.getValue(), event.toMap());
    }
}
