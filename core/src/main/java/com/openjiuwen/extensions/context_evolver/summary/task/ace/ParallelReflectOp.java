/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.ace;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Generate ACE reflection from multiple MaTTS trajectories.
 * <p>
 * Mirrors Python's {@code ParallelReflectOp} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/ace/update.py}.
 * </p>
 */
public class ParallelReflectOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    private final boolean useGroundTruth;

    public ParallelReflectOp() {
        this(false);
    }

    public ParallelReflectOp(boolean useGroundTruth) {
        super(Map.of("use_ground_truth", useGroundTruth));
        this.useGroundTruth = useGroundTruth;
    }

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        String matts = String.valueOf(context.get("matts", "none"));
        if (!"parallel".equals(matts) && !"combined".equals(matts)) {
            LOGGER.info("Skipping ParallelReflectOp for matts mode: %s", matts);
            return CompletableFuture.completedFuture(null);
        }

        Object llmObject = getLlm();
        if (!(llmObject instanceof AceAsyncLlm llm)) {
            return CompletableFuture.failedFuture(new IllegalStateException("LLM not configured in ServiceContext"));
        }

        List<String> trajectories = ReflectOp.stringList(context.get("trajectories", List.of()));
        if (trajectories.size() < 2) {
            LOGGER.warning("Expected at least 2 trajectories for parallel mode, got %s", trajectories.size());
            context.set("reflection", Map.of());
            return CompletableFuture.completedFuture(null);
        }

        Playbook playbook = ReflectOp.playbook(context.get("playbook", new Playbook()));
        String groundTruth = ReflectOp.stringValue(context.get("ground_truth", ""));
        List<String> feedback = ReflectOp.stringList(context.get("feedback", List.of()));
        String trajectoriesText = formatTrajectories(trajectories, feedback);

        String userPrompt;
        if (useGroundTruth && !groundTruth.isEmpty()) {
            userPrompt = AcePrompts.ACE_REFLECTOR_SCALING_PROMPT
                    .replace("{ground_truth}", groundTruth)
                    .replace("{playbook}", playbook.asPrompt())
                    .replace("{trajectories}", trajectoriesText);
        } else {
            userPrompt = AcePrompts.ACE_REFLECTOR_SCALING_NOGT_PROMPT
                    .replace("{playbook}", playbook.asPrompt())
                    .replace("{trajectories}", trajectoriesText);
        }

        LOGGER.debug("Generating parallel reflection from %s trajectories...", trajectories.size());
        try {
            return llm.asyncGenerate(userPrompt)
                    .thenAccept(response -> {
                        try {
                            context.set("reflection", AceUtils.safeJsonLoads(response));
                            LOGGER.info("Generated parallel reflection successfully");
                        } catch (RuntimeException exception) {
                            LOGGER.error("Failed to parse reflection: %s", exception);
                            context.set("reflection", Map.of());
                        }
                    });
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private static String formatTrajectories(List<String> trajectories, List<String> feedback) {
        List<String> parts = new ArrayList<>();
        for (int index = 0; index < trajectories.size(); index++) {
            parts.add("<TRAJECTORY " + (index + 1) + ">");
            if (feedback.size() > index && !feedback.get(index).isEmpty()) {
                parts.add("TEST_REPORT_START");
                parts.add(feedback.get(index));
                parts.add("TEST_REPORT_END");
            }
            parts.add(trajectories.get(index));
            parts.add("");
        }
        return String.join("\n", parts);
    }
}
