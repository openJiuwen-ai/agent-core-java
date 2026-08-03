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
 * Generate ACE playbook delta operations from parallel MaTTS reflection.
 * <p>
 * Mirrors Python's {@code ParallelCurateOp} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/ace/update.py}.
 * </p>
 */
public class ParallelCurateOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        String matts = String.valueOf(context.get("matts", "none"));
        if (!"parallel".equals(matts) && !"combined".equals(matts)) {
            LOGGER.info("Skipping ParallelCurateOp for matts mode: %s", matts);
            return CompletableFuture.completedFuture(null);
        }

        Object llmObject = getLlm();
        if (!(llmObject instanceof AceAsyncLlm llm)) {
            return CompletableFuture.failedFuture(new IllegalStateException("LLM not configured in ServiceContext"));
        }

        Map<String, Object> reflection = CurateOp.reflection(context.get("reflection", Map.of()));
        if (reflection.isEmpty()) {
            LOGGER.warning("No reflection to curate from");
            context.set("delta", new Playbook.DeltaBatch("", List.of()));
            return CompletableFuture.completedFuture(null);
        }

        List<String> trajectories = ReflectOp.stringList(context.get("trajectories", List.of()));
        if (trajectories.size() < 2) {
            LOGGER.warning("Expected at least 2 trajectories for parallel mode, got %s", trajectories.size());
            context.set("delta", new Playbook.DeltaBatch("", List.of()));
            return CompletableFuture.completedFuture(null);
        }

        String query = CurateOp.requiredString(context.get("query"), "query");
        Playbook playbook = ReflectOp.playbook(context.get("playbook", new Playbook()));
        String userPrompt = AcePrompts.ACE_CURATOR_SCALING_PROMPT
                .replace("{question_context}", query)
                .replace("{playbook}", playbook.asPrompt())
                .replace("{trajectories}", formatTrajectories(trajectories))
                .replace("{reflection}", CurateOp.jsonString(reflection));

        LOGGER.debug("Generating playbook operations from parallel reflection...");
        try {
            return llm.asyncGenerate(userPrompt)
                    .thenAccept(response -> {
                        try {
                            Playbook.DeltaBatch delta = Playbook.DeltaBatch.fromJson(AceUtils.safeJsonLoads(response));
                            context.set("delta", delta);
                            LOGGER.info("Generated %s playbook operations (parallel)", delta.getOperations().size());
                        } catch (RuntimeException exception) {
                            LOGGER.error("Failed to parse curation: %s", exception);
                            context.set("delta", new Playbook.DeltaBatch("", List.of()));
                        }
                    });
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private static String formatTrajectories(List<String> trajectories) {
        List<String> parts = new ArrayList<>();
        for (int index = 0; index < trajectories.size(); index++) {
            parts.add("<TRAJECTORY " + (index + 1) + ">");
            parts.add(trajectories.get(index));
            parts.add("");
        }
        return String.join("\n", parts);
    }
}
