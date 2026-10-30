/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.subagent;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.kvcache.KVCacheSubagentLifecycle;
import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single-shot task spawning tool.
 *
 * <p>Mirrors Python's {@code TaskTool} and {@code create_task_tool} in
 * {@code openjiuwen/harness/tools/subagent/task_tool.py}.</p>
 */
public class TaskTool extends AbstractHarnessTool {

    /** Declared timeout for subagent tasks (matches the Python task_tool default). */
    public static final double DEFAULT_TASK_TIMEOUT_SECONDS = 720.0D;

    private final DeepAgent parentAgent;
    private final String language;
    private final TaskRunner taskRunner;
    private final Map<PendingTaskKey, PendingTask> pendingTasks = new ConcurrentHashMap<>();

    public TaskTool(TaskRunner taskRunner) {
        super(withTaskTimeout(toolCard("task_tool", "task_tool", "Run a subagent task.")));
        this.parentAgent = null;
        this.language = "cn";
        this.taskRunner = taskRunner;
    }

    public TaskTool(ToolCard card, DeepAgent parentAgent) {
        this(card, parentAgent, "cn");
    }

    public TaskTool(ToolCard card, DeepAgent parentAgent, String language) {
        super(withTaskTimeout(card == null ? toolCard("task_tool", "task_tool", "Run a subagent task.") : card));
        this.parentAgent = parentAgent;
        this.language = language == null || language.isBlank() ? "cn" : language;
        this.taskRunner = null;
    }

    /**
     * Declares the subagent timeout on the card so the ability manager honors it instead of
     * falling back to the shorter global default; existing declarations are preserved.
     *
     * @param card the tool card to annotate; must not be {@code null}
     * @return the same card, with the resilience timeout declared
     */
    private static ToolCard withTaskTimeout(ToolCard card) {
        Map<String, Object> properties = card.getProperties() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(card.getProperties());
        Object resilience = properties.get("resilience");
        Map<String, Object> resilienceMap = resilience instanceof Map<?, ?> existing
                ? new LinkedHashMap<>((Map<String, Object>) existing)
                : new LinkedHashMap<>();
        resilienceMap.putIfAbsent("timeout_s", DEFAULT_TASK_TIMEOUT_SECONDS);
        properties.put("resilience", resilienceMap);
        card.setProperties(properties);
        return card;
    }

    public static String buildSubSessionId(String parentSessionId, String subagentType) {
        return buildSubSessionId(parentSessionId, subagentType,
                KVCacheSubagentLifecycle.DEFAULT_STABLE_SUB_SESSION_TYPES);
    }

    /**
     * Build the sub-session id, honoring the configured stable sub-session
     * types: a type listed there reuses {@code parentSessionId + "_sub_" +
     * type} across calls so its cache identity stays stable, while every
     * other type gets a UUID suffix.
     *
     * @param parentSessionId parent product session id
     * @param subagentType requested subagent type
     * @param stableSubSessionTypes types configured for stable sub-session ids
     * @return sub-session id
     * @since 0.1.17
     */
    public static String buildSubSessionId(String parentSessionId, String subagentType,
            java.util.Collection<String> stableSubSessionTypes) {
        String normalizedType = stringValue(subagentType).trim();
        boolean isStableType = stableSubSessionTypes != null && stableSubSessionTypes.stream()
                .anyMatch(type -> type != null && type.strip().equals(normalizedType));
        if (isStableType) {
            return parentSessionId + "_sub_" + normalizedType;
        }
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return parentSessionId + "_sub_" + normalizedType + "_" + suffix;
    }

    /**
     * Build the provider-facing sub-session id with a parent scope digest.
     *
     * <p>Mirrors Python's {@code scope_sub_session_id} in
     * {@code kv_cache_subagent_lifecycle.py}: when the parent cache id differs
     * from the runtime parent session id (Team branch), append a short digest
     * so same-named children of different branches cannot collide on one
     * cache key.</p>
     *
     * @param subSessionId runtime sub-session id
     * @param runtimeParentSessionId runtime parent session id
     * @param parentCacheId provider-facing parent cache id
     * @return scoped sub-session id
     * @since 0.1.16
     */
    public static String scopeSubSessionId(String subSessionId, String runtimeParentSessionId,
            String parentCacheId) {
        return KVCacheSubagentLifecycle.scopeSubSessionId(
                subSessionId, runtimeParentSessionId, parentCacheId);
    }

    public static List<Tool> createTaskTool(DeepAgent parentAgent, String availableAgents, String language) {
        return createTaskTool(parentAgent, availableAgents, language, null);
    }

    public static List<Tool> createTaskTool(DeepAgent parentAgent, String availableAgents, String language,
                                            String agentId) {
        String resolvedId = agentId == null || agentId.isBlank() ? "task_tool" : agentId + "_task_tool";
        ToolCard card = toolCard("task_tool", "task_tool", "Run a subagent task.");
        card.setId(resolvedId);
        return new ArrayList<>(List.of(new TaskTool(card, parentAgent, language)));
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        if (parentAgent == null) {
            return invokeLegacyRunner(inputs, kwargs);
        }
        Object session = kwargs == null ? null : kwargs.get("session");
        if (!(session instanceof AgentSessionApi parentSession)) {
            throw new IllegalArgumentException("TaskTool requires a valid session in kwargs");
        }
        String subagentType = requiredString(inputs, "subagent_type");
        String taskDescription = requiredString(inputs, "task_description");
        PendingTaskKey taskKey = new PendingTaskKey(parentSession.getSessionId(), subagentType, taskDescription);
        if (inputs.containsKey("query")) {
            return resumeSubagent(taskKey, subagentType, inputs.get("query"));
        }
        String subSessionId = deriveSubSessionId(parentSession, subagentType);
        PendingTask pendingTask = new PendingTask(subSessionId);
        if (pendingTasks.putIfAbsent(taskKey, pendingTask) != null) {
            throw new IllegalStateException("An identical subagent task is already pending");
        }
        return spawnSubagent(parentSession, subagentType, taskDescription, taskKey, pendingTask);
    }

    private String deriveSubSessionId(AgentSessionApi parentSession, String subagentType) {
        boolean isAffinityEnabled = KVCacheSubagentLifecycle.affinityEnabled(parentAgent);
        java.util.Collection<String> stableTypes = KVCacheSubagentLifecycle.stableSubSessionTypes(parentAgent);
        String subSessionId = buildSubSessionId(parentSession.getSessionId(), subagentType, stableTypes);
        if (!isAffinityEnabled) {
            return subSessionId;
        }
        // Scope the child id by the parent's cache identity so same-named
        // children of different Team branches cannot collide on one cache key.
        return KVCacheSubagentLifecycle.scopeSubSessionId(
                subSessionId,
                parentSession.getSessionId(),
                KVCacheSubagentLifecycle.resolveSubagentParentCacheId(parentSession));
    }

    private Object resumeSubagent(PendingTaskKey taskKey, String subagentType, Object query) {
        PendingTask pendingTask = pendingTasks.get(taskKey);
        if (pendingTask == null) {
            throw new IllegalStateException("No interrupted subagent task matches the resume arguments");
        }
        InteractiveInput interactiveInput = interactiveInput(query);
        if (!pendingTask.beginResume()) {
            throw new IllegalStateException("Subagent task is not awaiting resume");
        }
        return invokeSubagent(taskKey, pendingTask, subagentType, interactiveInput);
    }

    private static InteractiveInput interactiveInput(Object query) {
        if (query instanceof InteractiveInput input) {
            return input;
        }
        if (query instanceof Map<?, ?> map) {
            InteractiveInput input = new InteractiveInput();
            if (map.get("userInputs") instanceof Map<?, ?> userInputs) {
                input.setUserInputs(stringObjectMap(userInputs));
            }
            Object rawInputs = map.get("rawInputs");
            if (rawInputs != null) {
                input.setRawInputs(rawInputs);
            }
            return input;
        }
        throw new IllegalArgumentException("TaskTool resume query must be an InteractiveInput");
    }

    private Object spawnSubagent(AgentSessionApi parentSession, String subagentType, String taskDescription,
            PendingTaskKey taskKey, PendingTask pendingTask) {
        boolean isSetupComplete = false;
        try {
            DeepAgent subagent = createSubagent(subagentType, pendingTask.subSessionId);
            AgentSessionApi subSession = buildSubSession(parentSession, pendingTask.subSessionId, subagent.getCard());
            pendingTask.retain(subagent, subSession);
            KVCacheSubagentLifecycle.prepareSubagent(parentAgent, subSession, subagentType).join();
            isSetupComplete = true;
        } finally {
            if (!isSetupComplete) {
                try {
                    if (pendingTask.subSession != null) {
                        KVCacheSubagentLifecycle.finishSubagent(
                                parentAgent, pendingTask.subSession, subagentType, false).join();
                    }
                } finally {
                    pendingTasks.remove(taskKey, pendingTask);
                }
            }
        }
        return invokeSubagent(taskKey, pendingTask, subagentType, taskDescription);
    }

    private DeepAgent createSubagent(String subagentType, String subSessionId) {
        try {
            return parentAgent.createSubagent(subagentType, subSessionId);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new IllegalStateException(
                    "Subagent " + subagentType + " creation failed: " + exception.getMessage(), exception);
        }
    }

    private Object invokeSubagent(PendingTaskKey taskKey, PendingTask pendingTask, String subagentType, Object query) {
        boolean isSucceeded = false;
        boolean isInterrupted = false;
        try {
            Map<String, Object> result = pendingTask.subagent.invoke(linkedMap(
                    "query", query,
                    "conversation_id", pendingTask.subSessionId
            ), pendingTask.subSession);
            if (isInterruptResult(result)) {
                Map<String, Object> interruptResult = interruptResult(result, pendingTask.subagent);
                isInterrupted = true;
                pendingTask.awaitResume();
                return interruptResult;
            }
            isSucceeded = true;
            return ToolOutput.success(resultData(result, pendingTask.subagent));
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException(
                    "Subagent " + subagentType + " execution failed: " + cause.getMessage(), cause);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new IllegalStateException(
                    "Subagent " + subagentType + " execution failed: " + exception.getMessage(), exception);
        } finally {
            if (!isInterrupted) {
                try {
                    KVCacheSubagentLifecycle.finishSubagent(
                            parentAgent, pendingTask.subSession, subagentType, isSucceeded).join();
                } finally {
                    pendingTasks.remove(taskKey, pendingTask);
                }
            }
        }
    }

    private static boolean isInterruptResult(Map<String, Object> result) {
        return result != null && "interrupt".equals(result.get("result_type"));
    }

    private static Map<String, Object> resultData(Map<String, Object> result, DeepAgent subagent) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("output", stringValue(result == null ? null : result.getOrDefault("output", "")));
        data.put("agent_id", subagent.getCard().getId());
        return data;
    }

    private static Map<String, Object> interruptResult(Map<String, Object> result, DeepAgent subagent) {
        Map<String, Object> data = resultData(result, subagent);
        data.put("result_type", result.get("result_type"));
        data.put("state", result.get("state"));
        data.put("interrupt_ids", result.get("interrupt_ids"));
        return data;
    }

    /**
     * Build the child session for one TaskTool call with KVC inheritance.
     *
     * @param parentSession parent product session
     * @param subSessionId runtime sub-session id
     * @param card child agent card
     * @return child session (never {@code null}); without affinity it is a
     *         plain self-keyed session
     */
    private static AgentSessionApi buildSubSession(AgentSessionApi parentSession, String subSessionId, Object card) {
        java.util.Optional<com.openjiuwen.core.singleagent.kvcache.KVCacheChildSession.ChildSessionKwargs> inheritance =
                com.openjiuwen.core.singleagent.kvcache.KVCacheChildSession.buildChildSessionKwargs(parentSession);
        if (inheritance.isEmpty()) {
            AgentSession child = AgentSession.createAgentSession(subSessionId, parentSession.getEnvs(), card);
            child.withTenantContext(parentSession.getTenantContext());
            return child;
        }
        return KVCacheSubagentLifecycle.createSubagentSession(
                parentSession,
                subSessionId,
                inheritance.get().parentSessionId(),
                null
        );
    }

    private Object invokeLegacyRunner(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        String prompt = requiredString(inputs, "prompt");
        String description = stringValue(inputs == null ? null : inputs.get("description"));
        if (taskRunner == null) {
            return ToolOutput.failure("task runner is not configured");
        }
        return ToolOutput.success(taskRunner.run(description, prompt, inputs == null ? Map.of() : inputs,
                kwargs == null ? Map.of() : kwargs));
    }

    /**
     * Java boundary for Python's subagent task execution in
     * {@code openjiuwen/harness/tools/subagent/task_tool.py}.
     */
    @FunctionalInterface
    public interface TaskRunner {
        Map<String, Object> run(String description, String prompt, Map<String, Object> inputs,
                                Map<String, Object> kwargs) throws Exception;
    }

    private record PendingTaskKey(String parentSessionId, String subagentType, String taskDescription) {
    }

    private static final class PendingTask {
        private final String subSessionId;
        private final AtomicBoolean awaitingResume = new AtomicBoolean();
        private DeepAgent subagent;
        private AgentSessionApi subSession;

        private PendingTask(String subSessionId) {
            this.subSessionId = subSessionId;
        }

        private void retain(DeepAgent subagent, AgentSessionApi subSession) {
            this.subagent = subagent;
            this.subSession = subSession;
        }

        private void awaitResume() {
            awaitingResume.set(true);
        }

        private boolean beginResume() {
            return awaitingResume.compareAndSet(true, false);
        }
    }
}
