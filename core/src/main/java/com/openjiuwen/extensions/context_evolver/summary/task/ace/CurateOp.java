/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.ace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Generate ACE playbook delta operations from a reflection.
 * <p>
 * Mirrors Python's {@code CurateOp} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/ace/update.py}.
 * </p>
 */
public class CurateOp extends BaseOp {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        String matts = String.valueOf(context.get("matts", "none"));
        if (!"none".equals(matts) && !"sequential".equals(matts)) {
            LOGGER.info("Skipping CurateOp for matts mode: %s", matts);
            return CompletableFuture.completedFuture(null);
        }

        Object llmObject = getLlm();
        if (!(llmObject instanceof AceAsyncLlm llm)) {
            return CompletableFuture.failedFuture(new IllegalStateException("LLM not configured in ServiceContext"));
        }

        Map<String, Object> reflection = reflection(context.get("reflection", Map.of()));
        if (reflection.isEmpty()) {
            LOGGER.warning("No reflection to curate from");
            context.set("delta", new Playbook.DeltaBatch("", List.of()));
            return CompletableFuture.completedFuture(null);
        }

        String query = requiredString(context.get("query"), "query");
        Playbook playbook = ReflectOp.playbook(context.get("playbook", new Playbook()));
        String trajectory = ReflectOp.firstTrajectory(context.get("trajectories", List.of()));
        String userPrompt = AcePrompts.ACE_CURATOR_PROMPT
                .replace("{question_context}", query)
                .replace("{playbook}", playbook.asPrompt())
                .replace("{trajectory}", trajectory)
                .replace("{reflection}", jsonString(reflection));

        LOGGER.debug("Generating playbook operations from reflection...");
        try {
            return llm.asyncGenerate(userPrompt)
                    .thenAccept(response -> {
                        try {
                            Playbook.DeltaBatch delta = Playbook.DeltaBatch.fromJson(AceUtils.safeJsonLoads(response));
                            context.set("delta", delta);
                            LOGGER.info("Generated %s playbook operations", delta.getOperations().size());
                        } catch (RuntimeException exception) {
                            LOGGER.error("Failed to parse curation: %s", exception);
                            context.set("delta", new Playbook.DeltaBatch("", List.of()));
                        }
                    });
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    static Map<String, Object> reflection(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        return OBJECT_MAPPER.convertValue(rawMap, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
        });
    }

    static String requiredString(Object value, String key) {
        if (value == null) {
            throw new IllegalStateException("Context has no attribute '" + key + "'");
        }
        return String.valueOf(value);
    }

    static String jsonString(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize reflection.", exception);
        }
    }
}
