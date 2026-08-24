/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy.agent;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.WorkflowCard;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Legacy base agent compatibility surface.
 *
 * <p>Mirrors Python's {@code BaseAgent} in
 * {@code openjiuwen/core/single_agent/legacy/agent.py}.</p>
 */
public abstract class BaseAgent {
    private final Config configWrapper;
    private final List<Object> tools = new ArrayList<>();
    private final List<Object> workflows = new ArrayList<>();
    private Object agentConfig;
    private Config config;
    private ContextEngine contextEngine;

    protected BaseAgent(Object agentConfig) {
        this.configWrapper = new Config();
        this.configWrapper.setAgentConfig(agentConfig);
        this.agentConfig = agentConfig;
        this.config = configWrapper;
        this.contextEngine = createContextEngine();
    }

    public Config config() {
        return configWrapper;
    }

    public Config getConfigWrapper() {
        return configWrapper;
    }

    public Object getAgentConfig() {
        return agentConfig;
    }

    public Config getConfig() {
        return config;
    }

    public List<Object> getTools() {
        return List.copyOf(tools);
    }

    public List<Object> getWorkflows() {
        return List.copyOf(workflows);
    }

    public ContextEngine getContextEngine() {
        return contextEngine;
    }

    protected void setAgentConfig(Object agentConfig) {
        this.agentConfig = agentConfig;
        this.configWrapper.setAgentConfig(agentConfig);
    }

    protected void setContextEngine(ContextEngine contextEngine) {
        this.contextEngine = Objects.requireNonNull(contextEngine, "contextEngine");
    }

    protected ContextEngine createContextEngine() {
        int maxRounds = readInt(readProperty(agentConfig, "constrain", "getConstrain")
                .orElse(null), "reserved_max_chat_rounds", "getReservedMaxChatRounds", 10);
        ContextEngineConfig engineConfig = new ContextEngineConfig();
        engineConfig.setMaxContextMessageNum(maxRounds * 2);
        return new ContextEngine(engineConfig);
    }

    public void addPrompt(List<Map<String, Object>> promptTemplate) {
        Object current = readProperty(agentConfig, "prompt_template", "getPromptTemplate").orElse(null);
        if (current instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> target = (List<Map<String, Object>>) list;
            target.addAll(promptTemplate == null ? List.of() : promptTemplate);
        }
    }

    public void add_prompt(List<Map<String, Object>> promptTemplate) {
        addPrompt(promptTemplate);
    }

    public void addTools(List<?> incomingTools) {
        if (incomingTools == null) {
            return;
        }
        Set<String> existingToolNames = toolNames(tools);
        for (Object tool : incomingTools) {
            String toolName = readToolName(tool);
            appendToStringList(agentConfig, "tools", "getTools", toolName);
            appendPluginIfAvailable(tool, toolName);
            if (toolName == null || existingToolNames.add(toolName)) {
                tools.add(tool);
            }
            registerTool(tool, agentId());
        }
    }

    public void add_tools(List<?> incomingTools) {
        addTools(incomingTools);
    }

    public void addWorkflows(List<?> incomingWorkflows) {
        if (incomingWorkflows == null) {
            return;
        }
        Set<String> existingWorkflowKeys = workflowKeys(workflows);
        for (Object workflow : incomingWorkflows) {
            WorkflowCard workflowCard = resolveWorkflowCard(workflow);
            String key = workflowKey(workflowCard);
            if (key != null && existingWorkflowKeys.add(key)) {
                appendObjectToList(agentConfig, "workflows", "getWorkflows", workflowCard);
            }
            workflows.add(workflow);
            registerWorkflow(scopedResourceCard(workflowCard), providerFor(workflow), agentId());
        }
    }

    public void add_workflows(List<?> incomingWorkflows) {
        addWorkflows(incomingWorkflows);
    }

    public void removeWorkflows(List<WorkflowReference> workflowReferences) {
        if (workflowReferences == null || workflowReferences.isEmpty()) {
            return;
        }
        Set<String> keysToRemove = new LinkedHashSet<>();
        for (WorkflowReference reference : workflowReferences) {
            keysToRemove.add(reference.workflowId() + "_" + reference.workflowVersion());
        }
        workflows.removeIf(workflow -> keysToRemove.contains(workflowKey(resolveWorkflowCard(workflow))));
        Object configured = readProperty(agentConfig, "workflows", "getWorkflows").orElse(null);
        if (configured instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> mutable = (List<Object>) list;
            mutable.removeIf(item -> keysToRemove.contains(workflowKey(resolveWorkflowCard(item))));
        }
    }

    public void remove_workflows(List<WorkflowReference> workflowReferences) {
        removeWorkflows(workflowReferences);
    }

    public void bindWorkflows(List<?> incomingWorkflows) {
        addWorkflows(incomingWorkflows);
    }

    public void bind_workflows(List<?> incomingWorkflows) {
        bindWorkflows(incomingWorkflows);
    }

    public void addPlugins(List<?> plugins) {
        if (plugins == null) {
            return;
        }
        Object configured = readProperty(agentConfig, "plugins", "getPlugins").orElse(null);
        if (configured instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> mutable = (List<Object>) list;
            Set<String> existingNames = pluginNames(mutable);
            for (Object plugin : plugins) {
                String name = readString(plugin, "name", "getName");
                if (name == null || existingNames.add(name)) {
                    mutable.add(plugin);
                }
            }
        }
    }

    public void add_plugins(List<?> plugins) {
        addPlugins(plugins);
    }

    public Map<String, Object> toolToPluginSchema(Object tool) {
        Map<String, Object> schema = new LinkedHashMap<>();
        Object card = readProperty(tool, "card", "getCard").orElse(null);
        schema.put("id", readString(card, "id", "getId"));
        schema.put("name", readString(card, "name", "getName"));
        schema.put("description", readString(card, "description", "getDescription"));
        schema.put("inputs", readProperty(tool, "params", "getParams").orElse(Map.of(
                "type", "object",
                "properties", new LinkedHashMap<>(),
                "required", new ArrayList<>())));
        return schema;
    }

    public Map<String, Object> _tool_to_plugin_schema(Object tool) {
        return toolToPluginSchema(tool);
    }

    public CompletionStage<Void> clearSession(String sessionId) {
        return invokeRunnerRelease(sessionId)
                .thenRun(() -> {
                    if (contextEngine != null) {
                        contextEngine.clearContext(null, sessionId);
                    }
                });
    }

    public CompletionStage<Void> clearSession() {
        return clearSession("default_session");
    }

    public CompletionStage<Void> clear_session(String sessionId) {
        return clearSession(sessionId);
    }

    public CompletionStage<Void> clear_session() {
        return clearSession();
    }

    public abstract CompletionStage<Object> invoke(Map<String, Object> inputs, AgentSessionApi session);

    public abstract Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session,
                                            List<StreamMode> streamModes);

    protected static Object invokeCompatible(Object target, String methodName, Object... args) {
        Method method = findCompatibleMethod(target, methodName, args);
        if (method == null) {
            return NoMethod.INSTANCE;
        }
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(methodName + " is not accessible", error);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(methodName + " failed", cause);
        }
    }

    protected static boolean hasCompatibleMethod(Object target, String methodName, Object... args) {
        return findCompatibleMethod(target, methodName, args) != null;
    }

    protected static <T> CompletionStage<T> failed(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }

    protected enum NoMethod {
        INSTANCE
    }

    private static Method findCompatibleMethod(Object target, String methodName, Object... args) {
        if (target == null || methodName == null) {
            return null;
        }
        Method selected = null;
        int selectedScore = -1;
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                continue;
            }
            int score = compatibilityScore(method.getParameterTypes(), args);
            if (score > selectedScore) {
                selected = method;
                selectedScore = score;
            }
        }
        return selected;
    }

    private static int compatibilityScore(Class<?>[] parameterTypes, Object[] args) {
        int score = 0;
        for (int i = 0; i < parameterTypes.length; i++) {
            int itemScore = compatibilityScore(parameterTypes[i], args[i]);
            if (itemScore < 0) {
                return -1;
            }
            score += itemScore;
        }
        return score;
    }

    private static int compatibilityScore(Class<?> parameterType, Object arg) {
        if (arg == null) {
            return parameterType.isPrimitive() ? -1 : 1;
        }
        Class<?> boxed = box(parameterType);
        Class<?> actual = arg.getClass();
        if (boxed.equals(actual)) {
            return 4;
        }
        if (boxed.isAssignableFrom(actual)) {
            return boxed == Object.class ? 1 : 3;
        }
        return -1;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return Void.class;
    }

    private static java.util.Optional<Object> readProperty(Object target, String fieldName, String getterName) {
        if (target == null) {
            return java.util.Optional.empty();
        }
        try {
            Method method = target.getClass().getMethod(getterName);
            return java.util.Optional.ofNullable(method.invoke(target));
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return java.util.Optional.ofNullable(field.get(target));
        } catch (ReflectiveOperationException ignored) {
            return java.util.Optional.empty();
        }
    }

    private static String readString(Object target, String fieldName, String getterName) {
        return readProperty(target, fieldName, getterName).map(String::valueOf).orElse(null);
    }

    private static int readInt(Object target, String fieldName, String getterName, int defaultValue) {
        Object value = readProperty(target, fieldName, getterName).orElse(null);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static void appendToStringList(Object target, String fieldName, String getterName, String value) {
        if (value == null) {
            return;
        }
        Object list = readProperty(target, fieldName, getterName).orElse(null);
        if (list instanceof List<?> values && !values.contains(value)) {
            @SuppressWarnings("unchecked")
            List<String> mutable = (List<String>) values;
            mutable.add(value);
        }
    }

    private static void appendObjectToList(Object target, String fieldName, String getterName, Object value) {
        if (value == null) {
            return;
        }
        Object list = readProperty(target, fieldName, getterName).orElse(null);
        if (list instanceof List<?> values && !values.contains(value)) {
            @SuppressWarnings("unchecked")
            List<Object> mutable = (List<Object>) values;
            mutable.add(value);
        }
    }

    private static Set<String> toolNames(List<Object> values) {
        Set<String> names = new LinkedHashSet<>();
        for (Object value : values) {
            String name = readToolName(value);
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    private static String readToolName(Object tool) {
        Object card = readProperty(tool, "card", "getCard").orElse(null);
        return readString(card, "name", "getName");
    }

    private void appendPluginIfAvailable(Object tool, String toolName) {
        Object plugins = readProperty(agentConfig, "plugins", "getPlugins").orElse(null);
        if (!(plugins instanceof List<?> list)) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<Object> mutable = (List<Object>) list;
        Set<String> existing = pluginNames(mutable);
        if (toolName == null || existing.contains(toolName)) {
            return;
        }
        mutable.add(toolToPluginSchema(tool));
    }

    private static Set<String> pluginNames(List<Object> plugins) {
        Set<String> names = new LinkedHashSet<>();
        for (Object plugin : plugins) {
            String name = readString(plugin, "name", "getName");
            if (plugin instanceof Map<?, ?> map && map.get("name") != null) {
                name = String.valueOf(map.get("name"));
            }
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    private static WorkflowCard resolveWorkflowCard(Object workflow) {
        if (workflow instanceof WorkflowCard workflowCard) {
            return workflowCard;
        }
        if (workflow instanceof WorkflowFactory factory) {
            return factory.card();
        }
        Object card = readProperty(workflow, "card", "getCard").orElse(null);
        return card instanceof WorkflowCard workflowCard ? workflowCard : null;
    }

    private static Supplier<?> providerFor(Object workflow) {
        if (workflow instanceof Supplier<?> supplier) {
            return supplier;
        }
        return () -> workflow;
    }

    private static String workflowKey(WorkflowCard card) {
        if (card == null) {
            return null;
        }
        return card.getId() + "_" + card.getVersion();
    }

    private static Set<String> workflowKeys(List<Object> values) {
        Set<String> keys = new LinkedHashSet<>();
        for (Object value : values) {
            String key = workflowKey(resolveWorkflowCard(value));
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static void registerTool(Object tool, String tag) {
        try {
            Object resourceManager = Class.forName("com.openjiuwen.core.runner.Runner")
                    .getMethod("getResourceMgr")
                    .invoke(null);
            invokeCompatible(resourceManager, "addTool", tool, tag);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private static void registerWorkflow(WorkflowCard card, Supplier<?> provider, String tag) {
        if (card == null || provider == null) {
            return;
        }
        try {
            Object resourceManager = Class.forName("com.openjiuwen.core.runner.Runner")
                    .getMethod("getResourceMgr")
                    .invoke(null);
            invokeCompatible(resourceManager, "addWorkflow", card, provider, tag);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private WorkflowCard scopedResourceCard(WorkflowCard card) {
        if (card == null) {
            return null;
        }
        return new WorkflowCard(
                workflowKey(card),
                card.getName(),
                card.getDescription(),
                card.getVersion(),
                card.getInputParams()
        );
    }

    private String agentId() {
        return readString(agentConfig, "id", "getId");
    }

    private static CompletionStage<Void> invokeRunnerRelease(String sessionId) {
        try {
            Object result = Class.forName("com.openjiuwen.core.runner.Runner")
                    .getMethod("release", String.class)
                    .invoke(null, sessionId);
            if (result instanceof CompletionStage<?> stage) {
                return stage.thenApply(ignored -> null);
            }
            return CompletableFuture.completedFuture(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
