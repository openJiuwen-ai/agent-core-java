/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.EventInputs;
import com.openjiuwen.core.singleagent.rail.TaskIterationInputs;
import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Rail for context evolution.
 *
 * <p>Mirrors Python's {@code ContextEvolutionRail} in
 * {@code openjiuwen.harness.rails.evolution.context_evolution_rail}.</p>
 */
public class ContextEvolutionRail extends EvolutionRail {

    private static final Logger LOG = LoggerFactory.getLogger(ContextEvolutionRail.class);

    private final String userId;
    private final TaskMemoryService memoryService;
    private final boolean injectMemoriesInContext;
    private final boolean autoSummarize;
    private final String autoSummarizeMattsMode;
    private final List<Object> pendingTools = new ArrayList<>();

    private int memoriesUsed;
    private List<Map<String, String>> originalPromptTemplate;
    private String lastRetrievedQuery;
    private Map<String, Object> lastRetrievalResult;
    private Object boundAgent;
    private boolean toolsApplied;
    private String currentQuery;

    public ContextEvolutionRail() {
        this("default_user", null);
    }

    public ContextEvolutionRail(String userId) {
        this(userId, null);
    }

    public ContextEvolutionRail(String userId, TaskMemoryService memoryService) {
        this(userId, memoryService, true, true, "none");
    }

    public ContextEvolutionRail(
            String userId,
            TaskMemoryService memoryService,
            boolean injectMemoriesInContext,
            boolean autoSummarize,
            String autoSummarizeMattsMode
    ) {
        super();
        this.userId = userId != null ? userId : "";
        this.memoryService = memoryService != null ? memoryService : new TaskMemoryService();
        this.injectMemoriesInContext = injectMemoriesInContext;
        this.autoSummarize = autoSummarize;
        this.autoSummarizeMattsMode = autoSummarizeMattsMode != null ? autoSummarizeMattsMode : "none";
        this.currentQuery = "";
        this.memoryService.loadMemories(this.userId);
        LOG.info(
                "ContextEvolutionRail initialised for user={}, inject_in_context={}, auto_summarize={}",
                this.userId,
                this.injectMemoriesInContext,
                this.autoSummarize
        );
    }

    @Override
    public void init(Object agent) {
        super.init(agent);
        if (boundAgent == null) {
            boundAgent = agent;
        }
        LOG.info("[ContextEvolutionRail] Initialized");
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

    public int getMemoriesUsed() {
        return memoriesUsed;
    }

    public List<Map<String, String>> getOriginalPromptTemplate() {
        return originalPromptTemplate != null ? copyTemplate(originalPromptTemplate) : null;
    }

    public String getLastRetrievedQuery() {
        return lastRetrievedQuery;
    }

    public Map<String, Object> getLastRetrievalResult() {
        return lastRetrievalResult;
    }

    public List<Object> getPendingTools() {
        return pendingTools;
    }

    public boolean isToolsApplied() {
        return toolsApplied;
    }

    public Object getAgent() {
        return boundAgent;
    }

    public String getCurrentQuery() {
        return currentQuery;
    }

    @Override
    public void beforeTaskIteration(AgentCallbackContext ctx) {
        memoriesUsed = 0;
        originalPromptTemplate = null;

        if (ctx == null) {
            return;
        }
        if (boundAgent == null) {
            boundAgent = ctx.getAgent();
        }

        QueryInput queryInput = queryInput(ctx.getInputs());
        if (queryInput.query().isBlank()) {
            return;
        }
        currentQuery = queryInput.query();

        String memoryString;
        try {
            Map<String, Object> memoryResult;
            if (queryInput.retrievalQuery().equals(lastRetrievedQuery) && lastRetrievalResult != null) {
                memoryResult = lastRetrievalResult;
                LOG.info("Reusing cached memory retrieval result");
            } else {
                memoryResult = memoryService.retrieve(userId, queryInput.retrievalQuery()).join();
                lastRetrievedQuery = queryInput.retrievalQuery();
                lastRetrievalResult = memoryResult;
            }

            memoryString = stringValue(memoryResult.get("memory_string"));
            memoriesUsed = listSize(memoryResult.get("retrieved_memory"));
            LOG.info("Retrieved {} memories for query", memoriesUsed);
        } catch (Exception error) {
            LOG.error("Failed to retrieve memories: {}", error.getMessage());
            return;
        }

        if (memoriesUsed <= 0 || memoryString.isBlank() || !injectMemoriesInContext) {
            return;
        }

        Object innerAgent = resolveInnerAgent(ctx.getAgent());
        PromptTemplateHandle promptTemplate = promptTemplateHandle(innerAgent);
        if (promptTemplate == null) {
            LOG.warn("Agent has no config.prompt_template - skipping memory injection");
            return;
        }

        List<Map<String, String>> template = promptTemplate.get();
        originalPromptTemplate = copyTemplate(template);
        String memoryBlock = "Some Related Experience to help you complete the task:\n"
                + memoryString + "\n";

        List<Map<String, String>> updated = new ArrayList<>();
        for (Map<String, String> message : template) {
            Map<String, String> copy = new LinkedHashMap<>(message);
            if ("system".equals(copy.get("role"))) {
                String content = copy.getOrDefault("content", "");
                copy.put("content", (content + "\n\n" + memoryBlock).strip());
            }
            updated.add(copy);
        }
        promptTemplate.set(updated);
        LOG.debug("Injected memory context into agent system prompt");
    }

    @Override
    public void afterTaskIteration(AgentCallbackContext ctx) {
        if (ctx == null) {
            return;
        }

        if (originalPromptTemplate != null) {
            PromptTemplateHandle promptTemplate = promptTemplateHandle(resolveInnerAgent(ctx.getAgent()));
            if (promptTemplate != null) {
                promptTemplate.set(copyTemplate(originalPromptTemplate));
            }
            originalPromptTemplate = null;
            LOG.debug("Restored original agent system prompt");
        }

        Map<String, Object> result = resultMap(ctx.getInputs());
        if (result != null) {
            result.put("memories_used", memoriesUsed);
        }

        if (!autoSummarize || currentQuery == null || currentQuery.isBlank()) {
            return;
        }

        try {
            String trajectory = extractTrajectory(ctx);
            if (trajectory == null || trajectory.isBlank()) {
                return;
            }
            LOG.info("Running auto-summarize for current trajectory");
            memoryService.summarize(
                    userId,
                    "none",
                    currentQuery,
                    List.of(trajectory),
                    null,
                    List.of(1)
            ).join();
        } catch (Exception error) {
            LOG.error("Auto-summarize in afterTaskIteration failed: {}", error.getMessage());
        }
    }

    public String extractTrajectory(AgentCallbackContext ctx) {
        try {
            if (ctx == null || ctx.getSession() == null) {
                return null;
            }
            Object innerAgent = resolveInnerAgent(ctx.getAgent());
            ContextEngine contextEngine = contextEngine(innerAgent);
            if (contextEngine == null) {
                return null;
            }
            Session session = ctx.getSession();
            ModelContext context = contextEngine.getContext("default_context_id", session.getSessionId());
            if (context == null) {
                return null;
            }
            return formatTrajectory(context.getMessages());
        } catch (Exception error) {
            LOG.warn("Failed to extract trajectory: {}", error.getMessage());
            return null;
        }
    }

    public static String formatTrajectory(List<? extends BaseMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        List<String> transcript = new ArrayList<>();
        for (BaseMessage message : messages) {
            if (message instanceof UserMessage) {
                transcript.add("USER: " + cleanUserContent(message.getContentAsString()));
            } else if (message instanceof AssistantMessage assistantMessage) {
                String content = assistantMessage.getContentAsString();
                if (content != null && !content.isBlank()) {
                    transcript.add("THOUGHT: " + content);
                }
                if (assistantMessage.getToolCalls() != null) {
                    for (ToolCall toolCall : assistantMessage.getToolCalls()) {
                        transcript.add("ACTION: " + stringValue(toolCall.getName())
                                + "(" + stringValue(toolCall.getArguments()) + ")");
                    }
                }
            } else if (message instanceof ToolMessage) {
                transcript.add("OBSERVATION: " + message.getContentAsString());
            } else {
                String role = message.getRole() != null ? message.getRole() : "message";
                transcript.add(role.toUpperCase() + ": " + message.getContentAsString());
            }
        }
        return String.join("\n", transcript);
    }

    protected void runEvolution() {
        LOG.debug("[ContextEvolutionRail] Running context evolution");
    }

    private static String cleanUserContent(String content) {
        String result = content != null ? content : "";
        int reexamine = result.indexOf("Let's carefully re-examine the previous trajectory");
        if (reexamine >= 0) {
            result = result.substring(0, reexamine);
        }
        int related = result.indexOf("Some Related Experience to help you complete the task");
        if (related >= 0) {
            result = result.substring(0, related);
        }
        int question = result.lastIndexOf("Question: ");
        if (question >= 0) {
            result = result.substring(question + "Question: ".length());
        }
        if (result.startsWith("Task:\n")) {
            result = result.substring("Task:\n".length());
        }
        return result.strip();
    }

    private static QueryInput queryInput(EventInputs inputs) {
        String query = "";
        String retrievalQuery = "";
        if (inputs instanceof TaskIterationInputs taskInputs) {
            query = stringValue(taskInputs.getQuery());
            retrievalQuery = stringValue(readProperty(taskInputs, "retrievalQuery"));
        } else if (inputs != null) {
            query = stringValue(readProperty(inputs, "query"));
            retrievalQuery = stringValue(readProperty(inputs, "retrievalQuery"));
            if (retrievalQuery.isBlank()) {
                retrievalQuery = stringValue(readProperty(inputs, "retrieval_query"));
            }
        }
        if (retrievalQuery.isBlank()) {
            retrievalQuery = query;
        }
        return new QueryInput(query, retrievalQuery);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resultMap(EventInputs inputs) {
        if (inputs instanceof TaskIterationInputs taskInputs) {
            return taskInputs.getResult();
        }
        Object result = readProperty(inputs, "result");
        if (result instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private static Object resolveInnerAgent(Object agent) {
        Object reactAgent = readProperty(agent, "reactAgent");
        if (reactAgent != null) {
            return reactAgent;
        }
        Object delegate = readProperty(agent, "delegate");
        return delegate != null ? delegate : agent;
    }

    private static ContextEngine contextEngine(Object agent) {
        Object value = readProperty(agent, "contextEngine");
        return value instanceof ContextEngine contextEngine ? contextEngine : null;
    }

    @SuppressWarnings("unchecked")
    private static PromptTemplateHandle promptTemplateHandle(Object agent) {
        Object config = readProperty(agent, "config");
        if (config instanceof ReActAgentConfig reactConfig) {
            return new PromptTemplateHandle(
                    () -> copyTemplate(reactConfig.getPromptTemplate()),
                    value -> reactConfig.setPromptTemplate(copyTemplate(value))
            );
        }
        if (config == null) {
            return null;
        }

        Object rawTemplate = readProperty(config, "promptTemplate");
        if (!(rawTemplate instanceof List<?>)) {
            return null;
        }

        return new PromptTemplateHandle(
                () -> copyTemplate((List<Map<String, String>>) rawTemplate),
                value -> writeProperty(config, "promptTemplate", copyTemplate(value))
        );
    }

    private static List<Map<String, String>> copyTemplate(List<Map<String, String>> template) {
        List<Map<String, String>> copy = new ArrayList<>();
        if (template == null) {
            return copy;
        }
        for (Map<String, String> message : template) {
            Map<String, String> row = new LinkedHashMap<>();
            if (message != null) {
                message.forEach((key, value) -> row.put(key, value != null ? value : ""));
            }
            copy.add(row);
        }
        return copy;
    }

    private static Object readProperty(Object target, String name) {
        if (target == null || name == null || name.isBlank()) {
            return null;
        }

        String accessor = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        Method method = findMethod(target.getClass(), accessor);
        if (method == null && name.startsWith("is")) {
            method = findMethod(target.getClass(), name);
        }
        if (method != null) {
            try {
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Exception ignored) {
                return null;
            }
        }

        Field field = findField(target.getClass(), name);
        if (field != null) {
            try {
                field.setAccessible(true);
                return field.get(target);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static void writeProperty(Object target, String name, Object value) {
        if (target == null || name == null || name.isBlank()) {
            return;
        }

        String setter = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(setter) && method.getParameterCount() == 1) {
                try {
                    method.setAccessible(true);
                    method.invoke(target, value);
                    return;
                } catch (Exception ignored) {
                    return;
                }
            }
        }

        Field field = findField(target.getClass(), name);
        if (field != null) {
            try {
                field.setAccessible(true);
                field.set(target, value);
            } catch (Exception ignored) {
                // best-effort compatibility path
            }
        }
    }

    private static Method findMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                try {
                    return current.getDeclaredMethod(name);
                } catch (NoSuchMethodException ignoredAgain) {
                    current = current.getSuperclass();
                }
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static int listSize(Object value) {
        return value instanceof List<?> list ? list.size() : 0;
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private record QueryInput(String query, String retrievalQuery) {
    }

    private record PromptTemplateHandle(
            Supplier<List<Map<String, String>>> getter,
            Consumer<List<Map<String, String>>> setter
    ) {
        List<Map<String, String>> get() {
            return getter.get();
        }

        void set(List<Map<String, String>> value) {
            setter.accept(value);
        }
    }
}
