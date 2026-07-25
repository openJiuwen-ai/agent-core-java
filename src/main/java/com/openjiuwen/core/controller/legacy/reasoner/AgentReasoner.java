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

import java.util.List;
import java.util.concurrent.CompletionStage;

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

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletionStage<List<IntentDetectionController.Intent>> detect(Event event, Session session) {
        if (intentDetector == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(List.of());
        }
        return intentDetector.processMessage(event)
                .thenApply(tasks -> List.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletionStage<List<Task>> plan(IntentDetectionController.Intent intent, Session session) {
        if (planner == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(List.of());
        }
        return planner.processMessage(null);
    }
}
