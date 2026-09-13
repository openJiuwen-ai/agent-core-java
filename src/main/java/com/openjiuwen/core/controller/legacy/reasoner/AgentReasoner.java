/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.reasoner;

import com.openjiuwen.core.controller.legacy.IntentDetectionController;
import com.openjiuwen.core.controller.legacy.config.ReasonerConfig;
import com.openjiuwen.core.controller.legacy.event.Event;
import com.openjiuwen.core.controller.legacy.task.Task;
import com.openjiuwen.core.session.Session;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Minimal legacy reasoner composed of an intent detector and a planner.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentReasoner {
    private IntentDetector intentDetector;

    private Planner planner;

    /**
     * ReasonerConfig.
     * 
     * @since 0.1.7
     */
    private ReasonerConfig config = new ReasonerConfig();

    /**
     * detect.
     * 
     * @param event event
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    public IntentDetectionController.Intent detect(Event event, Session session) {
        return intentDetector != null ? intentDetector.detect(event, session, config) : null;
    }

    /**
     * plan.
     * 
     * @param intent intent
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    public Task plan(IntentDetectionController.Intent intent, Session session) {
        return planner != null ? planner.plan(intent, session) : null;
    }
}
