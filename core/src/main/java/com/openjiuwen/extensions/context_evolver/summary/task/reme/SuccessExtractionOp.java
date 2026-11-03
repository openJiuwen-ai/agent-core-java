/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reme;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.schema.ReMeMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReMeMemoryMetadata;
import com.openjiuwen.extensions.context_evolver.schema.SchemaUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Extract ReMe memories from successful trajectories.
 * <p>
 * Mirrors Python's {@code SuccessExtractionOp} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reme/update.py}.
 * </p>
 */
public class SuccessExtractionOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    private final boolean useExtraction;

    public SuccessExtractionOp() {
        this(true);
    }

    public SuccessExtractionOp(boolean useExtraction) {
        super(Map.of("use_extraction", useExtraction));
        this.useExtraction = useExtraction;
    }

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        if (!useExtraction) {
            context.set("success_memories", List.of());
            return CompletableFuture.completedFuture(null);
        }
        List<Object> successTrajectories = TrajectoryPreprocessOp.objectList(context.get("success_trajectories", List.of()));
        if (successTrajectories.isEmpty()) {
            LOGGER.info("No success trajectories to extract from");
            context.set("success_memories", List.of());
            return CompletableFuture.completedFuture(null);
        }
        Object llmObject = getLlm();
        if (!(llmObject instanceof ReMeSummaryAsyncLlm llm)) {
            return CompletableFuture.failedFuture(new IllegalStateException("LLM not configured in ServiceContext"));
        }
        String userId = String.valueOf(context.get("user_id", "default"));
        String query = String.valueOf(context.get("query", ""));
        List<ReMeMemory> memories = new ArrayList<>();
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (Object trajectory : successTrajectories) {
            String prompt = ReMePrompts.SUCCESS_MEMORY_PROMPT
                    .replace("{query}", query)
                    .replace("{step_sequence}", String.valueOf(trajectory))
                    .replace("{outcome}", "successful");
            future = future.thenCompose(ignored -> llm.asyncGenerate(prompt)
                    .thenAccept(response -> appendParsedMemories(response, userId, memories)));
        }
        return future.thenRun(() -> {
            context.set("success_memories", memories);
            LOGGER.info("Extracted %s insights from success trajectories", memories.size());
        });
    }

    static void appendParsedMemories(String response, String userId, List<ReMeMemory> memories) {
        for (Map<String, Object> experience : ReMeUtils.parseJsonExperienceResponse(response)) {
            try {
                memories.add(memoryFromExperience(experience, userId));
            } catch (RuntimeException exception) {
                LOGGER.warning("Failed to create ReMeMemory from parsed data: %s", exception);
            }
        }
    }

    static ReMeMemory memoryFromExperience(Map<String, Object> experience, String userId) {
        ReMeMemoryMetadata metadata = new ReMeMemoryMetadata();
        metadata.setTags(SchemaUtils.stringListValue(experience.get("tags")));
        metadata.setStepType(SchemaUtils.stringValue(experience.get("step_type"), ""));
        metadata.setToolsUsed(SchemaUtils.stringListValue(experience.get("tools_used")));
        metadata.setConfidence(SchemaUtils.doubleValue(experience.get("confidence"), 1.0d));
        metadata.setFreq(0);
        metadata.setUtility(0.0d);

        ReMeMemory memory = new ReMeMemory();
        memory.setWorkspaceId(userId);
        memory.setWhenToUse(SchemaUtils.stringValue(
                experience.containsKey("when_to_use") ? experience.get("when_to_use") : experience.get("condition"),
                ""
        ));
        memory.setContent(SchemaUtils.stringValue(experience.get("experience"), ""));
        memory.setScore(1.0d);
        Instant now = Instant.now();
        memory.setCreatedAt(now);
        memory.setUpdatedAt(now);
        memory.setMetadata(metadata);
        return memory;
    }
}
