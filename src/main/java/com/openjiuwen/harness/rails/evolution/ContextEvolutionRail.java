/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import com.openjiuwen.extensions.context_evolver.service.TrajectoryGenerator;
import com.openjiuwen.harness.rails.CallbackContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Context-memory evolution rail.
 *
 * <p>Mirrors Python's {@code ContextEvolutionRail} in
 * {@code openjiuwen/harness/rails/evolution/context_evolution_rail.py}.</p>
 */
public class ContextEvolutionRail extends EvolutionRail {

    private static final String MEMORY_BLOCK_PREFIX = "Some Related Experience to help you complete the task:";

    private final String userId;
    private final TaskMemoryService memoryService;
    private final boolean injectMemoriesInContext;
    private final boolean autoSummarize;
    private final String autoSummarizeMattsMode;
    private int memoriesUsed;
    private List<Map<String, Object>> originalPromptTemplate;
    private String lastRetrievedQuery;
    private Map<String, Object> lastRetrievalResult;
    private Object agent;
    private final List<Object> pendingTools = new ArrayList<>();
    private boolean toolsApplied;
    private String currentQuery = "";

    public ContextEvolutionRail() {
        this("", new TaskMemoryService(), true, true, "none");
    }

    public ContextEvolutionRail(String userId, boolean injectMemoriesInContext, boolean autoSummarize) {
        this(userId, new TaskMemoryService(), injectMemoriesInContext, autoSummarize, "none");
    }

    public ContextEvolutionRail(
            String userId,
            TaskMemoryService memoryService,
            boolean injectMemoriesInContext,
            boolean autoSummarize
    ) {
        this(userId, memoryService, injectMemoriesInContext, autoSummarize, "none");
    }

    public ContextEvolutionRail(
            String userId,
            TaskMemoryService memoryService,
            boolean injectMemoriesInContext,
            boolean autoSummarize,
            String autoSummarizeMattsMode
    ) {
        setPriority(50);
        this.userId = userId == null ? "" : userId;
        this.memoryService = memoryService == null ? new TaskMemoryService() : memoryService;
        this.injectMemoriesInContext = injectMemoriesInContext;
        this.autoSummarize = autoSummarize;
        this.autoSummarizeMattsMode = autoSummarizeMattsMode == null || autoSummarizeMattsMode.isBlank()
                ? "none"
                : autoSummarizeMattsMode;
        this.memoryService.loadMemories(this.userId);
    }

    @Override
    public void beforeTaskIteration(CallbackContext ctx) {
        memoriesUsed = 0;
        originalPromptTemplate = null;

        if (agent == null) {
            agent = ctx.getAgent() != null ? ctx.getAgent() : ctx.get("agent");
        }

        ctx.put("context_evolution_user_id", userId);
        ctx.put("inject_memories_in_context", injectMemoriesInContext);

        Object inputs = ctx.get("inputs");
        String query = stringValue(firstNonNull(ctx.get("query"), readAttribute(inputs, "query")));
        String retrievalQuery = stringValue(firstNonNull(ctx.get("retrieval_query"), readAttribute(inputs, "retrievalQuery")));
        if (retrievalQuery.isBlank()) {
            retrievalQuery = query;
        }
        if (query.isBlank()) {
            return;
        }
        currentQuery = query;

        Map<String, Object> memoryResult;
        try {
            if (retrievalQuery.equals(lastRetrievedQuery) && lastRetrievalResult != null) {
                memoryResult = new LinkedHashMap<>(lastRetrievalResult);
            } else {
                memoryResult = memoryService.retrieve(userId, retrievalQuery).toCompletableFuture().join();
                if (memoryResult == null) {
                    memoryResult = Map.of();
                }
                lastRetrievedQuery = retrievalQuery;
                lastRetrievalResult = new LinkedHashMap<>(memoryResult);
            }
        } catch (RuntimeException exception) {
            return;
        }

        String memoryString = stringValue(memoryResult.get("memory_string"));
        List<?> retrievedMemory = listValue(memoryResult.get("retrieved_memory"));
        memoriesUsed = retrievedMemory.size();
        if (memoriesUsed <= 0 || memoryString.isBlank() || !injectMemoriesInContext) {
            return;
        }

        List<Map<String, Object>> promptTemplate = readPromptTemplate(ctx);
        if (promptTemplate.isEmpty()) {
            return;
        }
        originalPromptTemplate = copyTemplate(promptTemplate);
        List<Map<String, Object>> newTemplate = new ArrayList<>();
        String memoryBlock = MEMORY_BLOCK_PREFIX + "\n" + memoryString + "\n";
        for (Map<String, Object> message : promptTemplate) {
            Map<String, Object> copy = new LinkedHashMap<>(message);
            if ("system".equals(copy.get("role"))) {
                String content = stringValue(copy.get("content"));
                copy.put("content", (content + "\n\n" + memoryBlock).trim());
            }
            newTemplate.add(copy);
        }
        ctx.put("prompt_template", newTemplate);
    }

    @Override
    public void afterTaskIteration(CallbackContext ctx) {
        if (originalPromptTemplate != null) {
            ctx.put("prompt_template", copyTemplate(originalPromptTemplate));
            originalPromptTemplate = null;
        }

        Object inputs = ctx.get("inputs");
        Object result = firstNonNull(ctx.get("result"), readAttribute(inputs, "result"));
        if (result instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> typed = (Map<Object, Object>) map;
            typed.put("memories_used", memoriesUsed);
        }

        if (autoSummarize && !currentQuery.isBlank()) {
            String trajectory = extractTrajectory(ctx);
            if (trajectory != null && !trajectory.isBlank()) {
                try {
                    memoryService.summarize(
                            userId,
                            autoSummarizeMattsMode,
                            currentQuery,
                            List.of(trajectory),
                            null,
                            List.of(1)
                    ).toCompletableFuture().join();
                } catch (RuntimeException ignored) {
                    // Python logs and suppresses auto-summarize failures.
                }
            }
        }

        super.afterTaskIteration(ctx);
        ctx.put("auto_summarize", autoSummarize);
    }

    public String extractTrajectory(CallbackContext ctx) {
        if (ctx == null || ctx.get("session") == null) {
            return null;
        }
        Object trajectory = ctx.get("trajectory");
        if (trajectory instanceof String text) {
            return text;
        }
        Object messages = ctx.get("messages");
        if (messages instanceof List<?> list) {
            List<Map<String, Object>> messageMaps = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    map.forEach((key, value) -> copy.put(String.valueOf(key), value));
                    messageMaps.add(copy);
                }
            }
            if (!messageMaps.isEmpty()) {
                return TrajectoryGenerator.formatTrajectory(messageMaps);
            }
        }
        return null;
    }

    public int getMemoriesUsed() {
        return memoriesUsed;
    }

    public String getUserId() {
        return userId;
    }

    public TaskMemoryService getMemoryService() {
        return memoryService;
    }

    public boolean isInjectMemoriesInContext() {
        return injectMemoriesInContext;
    }

    public boolean isAutoSummarize() {
        return autoSummarize;
    }

    public String getAutoSummarizeMattsMode() {
        return autoSummarizeMattsMode;
    }

    public List<Map<String, Object>> getOriginalPromptTemplate() {
        return originalPromptTemplate == null ? null : copyTemplate(originalPromptTemplate);
    }

    public String getLastRetrievedQuery() {
        return lastRetrievedQuery;
    }

    public Map<String, Object> getLastRetrievalResult() {
        return lastRetrievalResult == null ? null : new LinkedHashMap<>(lastRetrievalResult);
    }

    public Object getAgent() {
        return agent;
    }

    public List<Object> getPendingTools() {
        return new ArrayList<>(pendingTools);
    }

    public boolean isToolsApplied() {
        return toolsApplied;
    }

    public String getCurrentQuery() {
        return currentQuery;
    }

    private List<Map<String, Object>> readPromptTemplate(CallbackContext ctx) {
        Object value = firstNonNull(ctx.get("prompt_template"), ctx.get("promptTemplate"));
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((key, val) -> copy.put(String.valueOf(key), val));
                result.add(copy);
            }
        }
        return result;
    }

    private static List<Map<String, Object>> copyTemplate(List<Map<String, Object>> template) {
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> message : template) {
            copy.add(new LinkedHashMap<>(message));
        }
        return copy;
    }

    private static List<?> listValue(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    private static Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Object readAttribute(Object source, String name) {
        if (source == null || name == null || name.isBlank()) {
            return null;
        }
        if (source instanceof Map<?, ?> map) {
            Object direct = map.get(name);
            if (direct != null) {
                return direct;
            }
            String snake = name.replaceAll("([A-Z])", "_$1").toLowerCase();
            return map.get(snake);
        }
        String methodName = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            return source.getClass().getMethod(methodName).invoke(source);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
