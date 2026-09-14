/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.ace;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Generate ACE reflection from a single trajectory.
 * <p>
 * Mirrors Python's {@code ReflectOp} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/ace/update.py}.
 * </p>
 */
public class ReflectOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    private final boolean useGroundTruth;

    public ReflectOp() {
        this(false);
    }

    public ReflectOp(boolean useGroundTruth) {
        super(Map.of("use_ground_truth", useGroundTruth));
        this.useGroundTruth = useGroundTruth;
    }

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        String matts = String.valueOf(context.get("matts", "none"));
        if (!"none".equals(matts) && !"sequential".equals(matts)) {
            LOGGER.info("Skipping ReflectOp for matts mode: %s", matts);
            return CompletableFuture.completedFuture(null);
        }

        Object llmObject = getLlm();
        if (!(llmObject instanceof AceAsyncLlm llm)) {
            return CompletableFuture.failedFuture(new IllegalStateException("LLM not configured in ServiceContext"));
        }

        Object trajectoriesObject = context.get("trajectories", List.of());
        if (isEmptyTrajectoryCollection(trajectoriesObject)) {
            LOGGER.warning("No trajectories to reflect on");
            context.set("reflection", Map.of());
            return CompletableFuture.completedFuture(null);
        }

        Playbook playbook = playbook(context.get("playbook", new Playbook()));
        String trajectory = firstTrajectory(trajectoriesObject);
        String groundTruth = stringValue(context.get("ground_truth", ""));
        List<String> feedback = stringList(context.get("feedback", List.of()));

        String userPrompt;
        if (useGroundTruth && !groundTruth.isEmpty() && !feedback.isEmpty()) {
            userPrompt = AcePrompts.ACE_REFLECTOR_PROMPT
                    .replace("{ground_truth}", groundTruth)
                    .replace("{feedback}", feedback.get(0))
                    .replace("{playbook}", playbook.asPrompt())
                    .replace("{trajectory}", trajectory);
        } else {
            userPrompt = AcePrompts.ACE_REFLECTOR_NOGT_PROMPT
                    .replace("{playbook}", playbook.asPrompt())
                    .replace("{trajectory}", trajectory);
        }

        LOGGER.debug("Generating reflection from trajectory...");
        try {
            return llm.asyncGenerate(userPrompt)
                    .thenAccept(response -> {
                        try {
                            context.set("reflection", AceUtils.safeJsonLoads(response));
                            LOGGER.info("Generated reflection successfully");
                        } catch (RuntimeException exception) {
                            LOGGER.error("Failed to parse reflection: %s", exception);
                            context.set("reflection", Map.of());
                        }
                    });
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    static boolean isEmptyTrajectoryCollection(Object value) {
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        return value == null || String.valueOf(value).isEmpty();
    }

    static String firstTrajectory(Object value) {
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                throw new IllegalStateException("Expected at least one trajectory");
            }
            return String.valueOf(list.get(0));
        }
        return String.valueOf(value);
    }

    static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    static Playbook playbook(Object value) {
        return value instanceof Playbook playbook ? playbook : new Playbook();
    }
}
