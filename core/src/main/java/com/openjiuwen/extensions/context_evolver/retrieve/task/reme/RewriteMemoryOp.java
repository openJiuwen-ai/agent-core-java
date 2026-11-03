/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reme;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.schema.ReMeRetrievedMemory;
import com.openjiuwen.extensions.context_evolver.schema.SchemaUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Rewrite retrieved ReMe memories into a memory string.
 * <p>
 * Mirrors Python's {@code RewriteMemoryOp} in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/reme/run.py}.
 * </p>
 */
public class RewriteMemoryOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    private final boolean llmRewrite;

    public RewriteMemoryOp() {
        this(true);
    }

    public RewriteMemoryOp(boolean llmRewrite) {
        super(Map.of("llm_rewrite", llmRewrite));
        this.llmRewrite = llmRewrite;
    }

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        List<ReMeRetrievedMemory> retrievedMemories = retrievedMemories(context.get("retrieved_memories", List.of()));

        Object llmObject = getLlm();
        if (!(llmObject instanceof ReMeAsyncLlm llm)) {
            return CompletableFuture.failedFuture(new IllegalStateException("LLM not configured in ServiceContext"));
        }

        if (retrievedMemories.isEmpty()) {
            LOGGER.info("No memories retrieved, skipping rewrite");
            context.set("memory_string", "");
            return CompletableFuture.completedFuture(null);
        }

        String originalMemories = formatMemoriesForContext(retrievedMemories);
        if (!llmRewrite) {
            context.set("memory_string", originalMemories);
            return CompletableFuture.completedFuture(null);
        }

        Object queryObject = context.get("query");
        if (queryObject == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Context has no attribute 'query'"));
        }
        String userPrompt = ReMePrompt.DEFAULT_INSTANCE.getRewritePrompt()
                .replace("{current_query}", String.valueOf(queryObject))
                .replace("{original_context}", originalMemories);
        try {
            return llm.asyncGenerate(userPrompt)
                    .thenAccept(response -> {
                        String rewrittenContext = ReMeRetrieveUtils.parseJsonFieldOrNull(response, "rewritten_context");
                        if (rewrittenContext != null) {
                            context.set("memory_string", rewrittenContext);
                        } else {
                            LOGGER.warning("Failed to parse rewritten context, using formatted original memories");
                            context.set("memory_string", originalMemories);
                        }
                    });
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public static String formatMemoriesForContext(List<ReMeRetrievedMemory> memories) {
        List<String> formattedMemories = new ArrayList<>();
        for (int index = 0; index < memories.size(); index++) {
            ReMeRetrievedMemory memory = memories.get(index);
            formattedMemories.add("Memory " + (index + 1) + ":\n"
                    + "  When to use: " + memory.getWhenToUse() + "\n"
                    + "  Content: " + memory.getContent() + "\n");
        }
        return String.join("\n", formattedMemories);
    }

    private static List<ReMeRetrievedMemory> retrievedMemories(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<ReMeRetrievedMemory> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof ReMeRetrievedMemory memory) {
                result.add(memory);
            } else if (item instanceof Map<?, ?>) {
                result.add(ReMeRetrievedMemory.fromMap(SchemaUtils.mapValue(item)));
            }
        }
        return result;
    }
}
