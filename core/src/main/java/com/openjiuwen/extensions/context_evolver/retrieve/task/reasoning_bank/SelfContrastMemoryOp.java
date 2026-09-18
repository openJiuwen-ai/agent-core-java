/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reasoning_bank;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemoryItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Extracts ReasoningBank memories by contrasting successful and failed trajectories.
 *
 * <p>Mirrors Python's {@code SelfContrastMemoryOp} in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/reasoning_bank/matts.py}.</p>
 */
public class SelfContrastMemoryOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        List<Map<String, Object>> trajectories =
                MattsSupport.trajectoryMaps(context.get("parallel_trajectories"));
        if (trajectories.isEmpty()) {
            LOGGER.warning("No parallel trajectories for contrastive extraction");
            return CompletableFuture.completedFuture(null);
        }

        LOGGER.info("Extracting memories using self-contrast");
        String query = String.valueOf(MattsSupport.requireContext(context, "query"));
        MattsAsyncLlm llm;
        try {
            llm = MattsSupport.requireLlm(getLlm());
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }

        List<Map<String, Object>> successful = trajectories.stream()
                .filter(trajectory -> MattsSupport.truthy(trajectory.getOrDefault("success", false)))
                .toList();
        List<Map<String, Object>> failed = trajectories.stream()
                .filter(trajectory -> !MattsSupport.truthy(trajectory.getOrDefault("success", false)))
                .toList();
        LOGGER.info("Found %s successful and %s failed trajectories", successful.size(), failed.size());

        String prompt = buildExtractionPrompt(query, successful, failed);
        try {
            CompletableFuture<Void> result = new CompletableFuture<>();
            llm.asyncGenerate(prompt, 1.0D)
                    .whenComplete((response, error) -> {
                        if (error != null) {
                            LOGGER.error("Error in contrastive memory extraction: %s", error);
                            context.set("contrastive_memories", List.of());
                        } else {
                            List<ReasoningBankMemory> memories = parseMemories(response, context);
                            context.set("contrastive_memories", memories);
                            LOGGER.info("Extracted %s contrastive memories", memories.size());
                        }
                        result.complete(null);
                    });
            return result;
        } catch (RuntimeException exception) {
            LOGGER.error("Error in contrastive memory extraction: %s", exception);
            context.set("contrastive_memories", List.of());
            return CompletableFuture.completedFuture(null);
        }
    }

    private static String buildExtractionPrompt(String query,
                                                List<Map<String, Object>> successful,
                                                List<Map<String, Object>> failed) {
        return """
                You are an expert in extracting reasoning strategies. You will be given a user query and multiple trajectories showing how an agent attempted the task. Some trajectories may be successful, and others may have failed.

                ## Guidelines
                Your goal is to compare and contrast these trajectories to identify the most useful and generalizable strategies as memory items.

                Use self-contrast reasoning:
                - Identify patterns and strategies that consistently led to success
                - Identify mistakes or inefficiencies from failed trajectories and formulate preventative strategies
                - Prefer strategies that generalize beyond specific pages or exact wording

                ## Important notes
                - Think first: Why did some trajectories succeed while others failed?
                - You can extract at most 5 memory items from all trajectories combined
                - Do not repeat similar or overlapping items
                - Do not mention specific websites, queries, or string contents; focus on generalizable behaviors and reasoning patterns
                - Make sure each memory item captures actionable and transferable insights

                ## Output Format
                Your output must strictly follow the Markdown format shown below:
                ```
                # Memory Item 1
                ## Title <the title of the memory item>
                ## Description <one sentence summary of the memory item>
                ## Content <1-5 sentences describing the insights learned to successfully accomplishing the task>

                # Memory Item 2
                ...
                ```

                Query: %s

                Successful Trajectories (%s):
                %s

                Failed Trajectories (%s):
                %s
                """.formatted(
                query,
                successful.size(),
                describeTrajectories(successful),
                failed.size(),
                describeTrajectories(failed));
    }

    private static String describeTrajectories(List<Map<String, Object>> trajectories) {
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> trajectory : trajectories) {
            lines.add("Trajectory %s: %s...".formatted(
                    trajectory.get("index"),
                    MattsSupport.left(MattsSupport.stringValue(trajectory.get("answer")), 200)));
        }
        return String.join("\n", lines);
    }

    private static List<ReasoningBankMemory> parseMemories(String response, RuntimeContext context) {
        List<Map<String, String>> parsed = new ArrayList<>();
        Map<String, String> current = new LinkedHashMap<>();
        for (String rawLine : MattsSupport.stringValue(response).split("\\R")) {
            String line = rawLine.strip();
            if (line.startsWith("## Title")) {
                addIfComplete(parsed, current);
                current = new LinkedHashMap<>();
                current.put("title", line.replaceFirst("^## Title", "").strip());
            } else if (line.startsWith("## Description")) {
                current.put("description", line.replaceFirst("^## Description", "").strip());
            } else if (line.startsWith("## Content")) {
                current.put("content", line.replaceFirst("^## Content", "").strip());
            } else if (!line.isEmpty() && current.containsKey("title") && current.containsKey("content")) {
                current.put("content", current.get("content") + " " + line);
            }
        }
        addIfComplete(parsed, current);

        List<ReasoningBankMemory> memories = new ArrayList<>();
        String workspaceId = MattsSupport.stringValue(context.get("user_id", "default"));
        String query = MattsSupport.stringValue(context.get("query", ""));
        for (Map<String, String> item : parsed) {
            ReasoningBankMemory memory = new ReasoningBankMemory();
            memory.setWorkspaceId(workspaceId);
            memory.setQuery(query);
            memory.setMemory(List.of(new ReasoningBankMemoryItem(
                    item.get("title"),
                    item.get("description"),
                    item.get("content"))));
            memories.add(memory);
        }
        return memories;
    }

    private static void addIfComplete(List<Map<String, String>> target, Map<String, String> item) {
        if (item.containsKey("title") && item.containsKey("description") && item.containsKey("content")) {
            target.add(new LinkedHashMap<>(item));
        }
    }
}
