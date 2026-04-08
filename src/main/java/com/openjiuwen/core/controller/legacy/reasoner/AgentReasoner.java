/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
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
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentReasoner {

    private IntentDetector intentDetector;

    private Planner planner;

    private ReasonerConfig config = new ReasonerConfig();

    public IntentDetectionController.Intent detect(Event event, Session session) {
        return intentDetector != null ? intentDetector.detect(event, session, config) : null;
    }

    public Task plan(IntentDetectionController.Intent intent, Session session) {
        return planner != null ? planner.plan(intent, session) : null;
    }
}
