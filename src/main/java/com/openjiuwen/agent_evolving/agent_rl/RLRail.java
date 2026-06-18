/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl;

import com.openjiuwen.agent_evolving.trajectory.InMemoryTrajectoryStore;
import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.StepKind;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryBuilder;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStore;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.evolution.EvolutionRail;
import com.openjiuwen.harness.rails.evolution.EvolutionTriggerPoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collects RL training trajectory metadata on top of the generic evolution rail.
 *
 * <p>Mirrors Python's {@code RLRail} in
 * {@code openjiuwen/agent_evolving/agent_rl/rl_rail.py}.</p>
 */
public class RLRail extends EvolutionRail {

    public static final int PRIORITY = 100;

    private final String sessionId;
    private final String source;
    private final String caseId;
    private final TrajectoryStore trajectoryStore;
    private TrajectoryBuilder trajectoryBuilder;
    private int llmStepCount;

    public RLRail() {
        this("", "rl_offline", null);
    }

    public RLRail(String sessionId, String source, String caseId) {
        this(sessionId, source, caseId, null);
    }

    public RLRail(String sessionId, String source, String caseId, TrajectoryStore trajectoryStore) {
        super(null, EvolutionTriggerPoint.AFTER_INVOKE, true, Set.of());
        setPriority(PRIORITY);
        this.sessionId = sessionId == null ? "" : sessionId;
        this.source = source == null ? "rl_offline" : source;
        this.caseId = caseId;
        this.trajectoryStore = trajectoryStore == null ? new InMemoryTrajectoryStore() : trajectoryStore;
        this.llmStepCount = 0;
    }

    @Override
    public void beforeInvoke(CallbackContext ctx) {
        super.beforeInvoke(ctx);
        trajectoryBuilder = new TrajectoryBuilder(resolveSessionId(ctx), source, caseId, null, null, null);
        llmStepCount = 0;
    }

    @Override
    public void afterInvoke(CallbackContext ctx) {
        if (trajectoryBuilder != null) {
            trajectoryStore.save(trajectoryBuilder.build(), null);
            trajectoryBuilder = null;
        }
        super.afterInvoke(ctx);
        resetTrajectoryBuilder();
    }

    @Override
    public void afterModelCall(CallbackContext ctx) {
        super.afterModelCall(ctx);
        if (trajectoryBuilder == null) {
            return;
        }

        LLMCallDetail detail = LLMCallDetail.builder()
                .model(stringValue(ctx.get("model"), "unknown"))
                .messages(objectList(ctx.get("messages")))
                .response(ctx.get("response"))
                .tools(mapList(ctx.get("tools")))
                .build();
        llmStepCount += 1;
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("turn_id", llmStepCount - 1);
        meta.put("source", source);
        meta.put("case_id", caseId);
        trajectoryBuilder.recordStep(TrajectoryStep.builder()
                .kind(StepKind.LLM)
                .detail(detail)
                .meta(meta)
                .build());

        Map<String, Object> lastStep = lastTrajectoryStep();
        if (lastStep == null || !"llm".equals(lastStep.get("kind"))) {
            return;
        }

        Map<String, Object> mapMeta = mutableMeta(lastStep);
        mapMeta.put("turn_id", llmStepCount - 1);
        mapMeta.put("source", source);
        mapMeta.put("case_id", caseId);
        lastStep.put("meta", mapMeta);
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getSource() {
        return source;
    }

    public String getCaseId() {
        return caseId;
    }

    public int getLlmStepCount() {
        return llmStepCount;
    }

    public TrajectoryStore getTrajectoryStore() {
        return trajectoryStore;
    }

    private static Map<String, Object> mutableMeta(Map<String, Object> step) {
        Object rawMeta = step.get("meta");
        if (rawMeta instanceof Map<?, ?> meta) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : meta.entrySet()) {
                if (entry.getKey() != null) {
                    copy.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return copy;
        }
        return new LinkedHashMap<>();
    }

    private String resolveSessionId(CallbackContext ctx) {
        Object conversationId = ctx == null ? null : ctx.get("conversation_id");
        if (conversationId != null && !String.valueOf(conversationId).isBlank()) {
            return String.valueOf(conversationId);
        }
        return sessionId;
    }

    private static String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static List<Object> objectList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value == null) {
            return List.of();
        }
        return List.of(value);
    }

    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        copy.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                result.add(copy);
            }
        }
        return result;
    }
}
