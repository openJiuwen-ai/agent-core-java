/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reme;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.schema.ReMeMemory;
import com.openjiuwen.extensions.context_evolver.schema.SchemaUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validate extracted ReMe memories.
 * <p>
 * Mirrors Python's {@code MemoryValidationOp} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reme/update.py}.
 * </p>
 */
public class MemoryValidationOp extends BaseOp {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final Pattern JSON_CODE_BLOCK = Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```");
    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;
    private static final double VALIDATION_THRESHOLD = 0.5d;

    private final boolean useValidation;

    public MemoryValidationOp() {
        this(true);
    }

    public MemoryValidationOp(boolean useValidation) {
        super(Map.of("use_validation", useValidation));
        this.useValidation = useValidation;
    }

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        List<ReMeMemory> allMemories = new ArrayList<>();
        allMemories.addAll(memories(context.get("success_memories", List.of())));
        allMemories.addAll(memories(context.get("failure_memories", List.of())));
        allMemories.addAll(memories(context.get("comparative_memories", List.of())));
        if (allMemories.isEmpty()) {
            LOGGER.info("No memories to validate");
            context.set("validated_memories", List.of());
            return CompletableFuture.completedFuture(null);
        }
        if (!useValidation) {
            context.set("validated_memories", allMemories);
            return CompletableFuture.completedFuture(null);
        }
        Object llmObject = getLlm();
        if (!(llmObject instanceof ReMeSummaryAsyncLlm llm)) {
            return CompletableFuture.failedFuture(new IllegalStateException("LLM not configured in ServiceContext"));
        }
        LOGGER.info("Validating %s memories", allMemories.size());
        List<ReMeMemory> validated = new ArrayList<>();
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (ReMeMemory memory : allMemories) {
            future = future.thenCompose(ignored -> validateMemory(llm, memory)
                    .thenAccept(result -> {
                        if (Boolean.TRUE.equals(result.isValid())) {
                            memory.setScore(result.score());
                            validated.add(memory);
                        } else {
                            LOGGER.warning("Memory validation failed: %s", result.reason());
                        }
                    }));
        }
        return future.thenRun(() -> {
            context.set("validated_memories", validated);
            LOGGER.info("Validated %s out of %s memories", validated.size(), allMemories.size());
        });
    }

    static List<ReMeMemory> memories(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<ReMeMemory> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof ReMeMemory memory) {
                result.add(memory);
            } else if (item instanceof Map<?, ?>) {
                result.add(ReMeMemory.fromMap(SchemaUtils.mapValue(item)));
            }
        }
        return result;
    }

    private CompletableFuture<ValidationResult> validateMemory(ReMeSummaryAsyncLlm llm, ReMeMemory memory) {
        String prompt = ReMePrompts.MEMORY_VALIDATION_PROMPT
                .replace("{condition}", ReasoningValue.safe(memory.getWhenToUse()))
                .replace("{task_memory_content}", ReasoningValue.safe(memory.getContent()));
        return llm.asyncGenerate(prompt)
                .thenApply(response -> parseValidation(response)
                        .orElseGet(() -> new ValidationResult(false, 0.0d, "LLM validation error: invalid JSON")));
    }

    private java.util.Optional<ValidationResult> parseValidation(String response) {
        try {
            Map<String, Object> parsed;
            Matcher matcher = JSON_CODE_BLOCK.matcher(response);
            if (matcher.find()) {
                parsed = OBJECT_MAPPER.readValue(matcher.group(1), MAP_TYPE);
            } else {
                parsed = OBJECT_MAPPER.readValue(response, MAP_TYPE);
            }
            boolean isValid = SchemaUtils.booleanValue(parsed.get("is_valid")) == null
                    || Boolean.TRUE.equals(SchemaUtils.booleanValue(parsed.get("is_valid")));
            double score = SchemaUtils.doubleValue(parsed.get("score"), 0.5d);
            boolean accepted = isValid && score >= VALIDATION_THRESHOLD;
            String reason = accepted ? "" : "Low validation score (" + String.format("%.2f", score) + ") or marked as invalid";
            return java.util.Optional.of(new ValidationResult(accepted, score, reason));
        } catch (JsonProcessingException exception) {
            LOGGER.error("LLM validation failed for memory: %s", exception);
            return java.util.Optional.empty();
        }
    }

    private record ValidationResult(boolean isValid, double score, String reason) {
    }

    private static final class ReasoningValue {
        private static String safe(String value) {
            return value != null ? value : "";
        }
    }
}
