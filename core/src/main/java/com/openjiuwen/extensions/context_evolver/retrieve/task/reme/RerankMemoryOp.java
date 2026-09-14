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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Rerank retrieved ReMe memories by relevance.
 * <p>
 * Mirrors Python's {@code RerankMemoryOp} in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/reme/run.py}.
 * </p>
 */
public class RerankMemoryOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    private final boolean llmRerank;
    private final int topkRerank;

    public RerankMemoryOp() {
        this(true, 5);
    }

    public RerankMemoryOp(boolean llmRerank, int topkRerank) {
        super(Map.of("llm_rerank", llmRerank, "topk_rerank", topkRerank));
        this.llmRerank = llmRerank;
        this.topkRerank = topkRerank;
    }

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        if (!llmRerank) {
            return CompletableFuture.completedFuture(null);
        }

        Object queryObject = context.get("query");
        if (queryObject == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Context has no attribute 'query'"));
        }
        String query = String.valueOf(queryObject);
        List<ReMeRetrievedMemory> retrievedMemories = retrievedMemories(context.get("retrieved_memories", List.of()));

        Object llmObject = getLlm();
        if (!(llmObject instanceof ReMeAsyncLlm llm)) {
            return CompletableFuture.failedFuture(new IllegalStateException("LLM not configured in ServiceContext"));
        }

        if (retrievedMemories.isEmpty()) {
            LOGGER.info("No memories retrieved, skipping rerank");
            return CompletableFuture.completedFuture(null);
        }

        String userPrompt = ReMePrompt.DEFAULT_INSTANCE.getRerankPrompt()
                .replace("{query}", query)
                .replace("{num_candidates}", String.valueOf(retrievedMemories.size()))
                .replace("{candidates}", formatCandidatesForRerank(retrievedMemories));
        try {
            return llm.asyncGenerate(userPrompt)
                    .thenAccept(response -> context.set("retrieved_memories", rerankedMemories(response, retrievedMemories)));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public static String formatCandidatesForRerank(List<ReMeRetrievedMemory> candidates) {
        List<String> formattedCandidates = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            ReMeRetrievedMemory candidate = candidates.get(index);
            formattedCandidates.add("Candidate " + index + ":\n"
                    + "Condition: " + candidate.getWhenToUse() + "\n"
                    + "Experience: " + candidate.getContent() + "\n");
        }
        return String.join("\n---\n", formattedCandidates);
    }

    private List<ReMeRetrievedMemory> rerankedMemories(String response, List<ReMeRetrievedMemory> retrievedMemories) {
        List<Integer> rerankedIndices = ReMeRetrieveUtils.parseJsonListResponse(response, "ranked_indices");
        List<ReMeRetrievedMemory> reranked;
        if (!rerankedIndices.isEmpty()) {
            reranked = new ArrayList<>();
            Set<Integer> rankedIndexSet = new LinkedHashSet<>(rerankedIndices);
            for (Integer index : rerankedIndices) {
                if (index != null && index >= 0 && index < retrievedMemories.size()) {
                    reranked.add(retrievedMemories.get(index));
                }
            }
            for (int index = 0; index < retrievedMemories.size(); index++) {
                if (!rankedIndexSet.contains(index)) {
                    reranked.add(retrievedMemories.get(index));
                }
            }
        } else {
            reranked = new ArrayList<>(retrievedMemories);
            LOGGER.warning("Failed to parse rerank response, using original order");
        }
        int limit = Math.min(topkRerank, reranked.size());
        return new ArrayList<>(reranked.subList(0, limit));
    }

    @SuppressWarnings("unchecked")
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
