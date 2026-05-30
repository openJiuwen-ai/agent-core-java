// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl;

import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import com.openjiuwen.harness.rails.evolution.EvolutionRail;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RL Rail for trajectory collection during training.
 * <p>
 * Mirrors Python's {@code rl_rail.py} from
 * {@code openjiuwen.agent_evolving.agent_rl.rl_rail}.
 */
public class RlRail extends EvolutionRail {
    
    /**
     * Rail priority.
     */
    public static final int PRIORITY = 100;

    private final String sessionId;
    private final String source;
    private final String caseId;
    private int llmStepCount;

    /**
     * Initialize RL Rail.
     */
    public RlRail() {
        this("", "rl_offline", null);
    }

    public RlRail(String sessionId, String source, String caseId) {
        super();
        this.sessionId = sessionId != null ? sessionId : "";
        this.source = source != null ? source : "rl_offline";
        this.caseId = caseId;
        this.llmStepCount = 0;
    }

    @Override
    protected void onBeforeInvoke(Object ctx) {
        llmStepCount = 0;
    }

    @Override
    protected void onAfterModelCall(Object ctx, Object response) {
        if (response instanceof TrajectoryStep step) {
            processStep(step);
        } else {
            llmStepCount += 1;
        }
    }

    /**
     * Process trajectory step.
     */
    public void processStep(Object step) {
        if (!(step instanceof TrajectoryStep trajectoryStep)) {
            return;
        }
        if (!"llm".equals(trajectoryStep.getKind())) {
            return;
        }
        int turnId = llmStepCount;
        llmStepCount += 1;

        Map<String, Object> meta = trajectoryStep.getMeta() != null
            ? new LinkedHashMap<>(trajectoryStep.getMeta())
            : new LinkedHashMap<>();
        meta.put("turn_id", turnId);
        meta.put("source", source);
        meta.put("case_id", caseId);
        if (!sessionId.isBlank()) {
            meta.put("session_id", sessionId);
        }
        trajectoryStep.setMeta(meta);
    }

    public String getSessionId() { return sessionId; }
    public String getSource() { return source; }
    public String getCaseId() { return caseId; }
    public int getLlmStepCount() { return llmStepCount; }
}
