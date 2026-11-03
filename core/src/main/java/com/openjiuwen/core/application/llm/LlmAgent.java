/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm;

import com.openjiuwen.core.application.llm_agent.LLMAgent;
import com.openjiuwen.core.common.async.FutureList;
import com.openjiuwen.core.common.async.FutureMap;
import com.openjiuwen.core.application.schema.LlmAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig;
import com.openjiuwen.core.singleagent.legacy.config.AgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.Workflow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterator;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Backward-compatible facade for the 0.1.12 LLM agent class.
 *
 * <p>Mirrors Python's {@code LLMAgent} in
 * {@code openjiuwen/core/application/llm_agent/llm_agent.py}.</p>
 */
public class LlmAgent extends LLMAgent {
    private final AbilityManager abilityManager = new AbilityManager();
    private final AgentCard card;
    private final LlmAgentConfig applicationAgentConfig;

    public LlmAgent(LegacyReActAgentConfig agentConfig) {
        super(agentConfig);
        this.applicationAgentConfig = toApplicationConfig(agentConfig);
        this.card = toAgentCard(agentConfig);
        this.abilityManager.setContextEngine(getContextEngine());
        syncAbilityManagerToController();
    }

    public LlmAgent(LlmAgentConfig agentConfig) {
        super(toLegacyConfig(agentConfig));
        this.applicationAgentConfig = Objects.requireNonNull(agentConfig, "agentConfig");
        this.card = toAgentCard(agentConfig);
        this.abilityManager.setContextEngine(getContextEngine());
        syncAbilityManagerToController();
    }

    @Override
    public LlmAgentConfig getAgentConfig() {
        return applicationAgentConfig;
    }

    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    public AbilityManager get_ability_manager() {
        return abilityManager;
    }

    public AgentCard getCard() {
        return card;
    }

    @Override
    public CompletionStage<Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
        try {
            Object result = super.invoke(inputs, session).toCompletableFuture().join();
            return toDirectInvokeStage(toControllerOutput(result));
        } catch (RuntimeException error) {
            java.util.concurrent.CompletableFuture<Object> failed = new java.util.concurrent.CompletableFuture<>();
            failed.completeExceptionally(error);
            return failed;
        }
    }

    @Override
    public com.openjiuwen.core.context.ContextEngine getContextEngine() {
        return (com.openjiuwen.core.context.ContextEngine) super.getContextEngine();
    }

    @Override
    protected com.openjiuwen.core.context.ContextEngine createContextEngine() {
        ContextEngineConfig config = new ContextEngineConfig();
        config.setMaxContextMessageNum(readReservedMaxChatRounds(getAgentConfig()) * 2);
        return new com.openjiuwen.core.context.ContextEngine(config);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addPrompt(List promptTemplate) {
        List<Map<String, Object>> objectPrompt = copyPromptTemplateObjects(promptTemplate);
        super.addPrompt(objectPrompt);
        applicationAgentConfig.getPromptTemplate().addAll(copyPromptTemplateStrings(promptTemplate));
    }

    @Override
    public void addTools(List<?> incomingTools) {
        super.addTools(incomingTools);
        if (incomingTools == null) {
            return;
        }
        for (Object tool : incomingTools) {
            if (tool instanceof Tool typedTool) {
                abilityManager.add(typedTool.getCard());
            } else {
                Object card = readProperty(tool, "getCard");
                if (card != null) {
                    abilityManager.add(card);
                }
            }
        }
        syncAbilityManagerToController();
    }

    @Override
    public void addWorkflows(List<?> incomingWorkflows) {
        super.addWorkflows(incomingWorkflows);
        if (incomingWorkflows == null) {
            return;
        }
        for (Object workflow : incomingWorkflows) {
            Object card = readProperty(workflow, "getCard");
            if (card != null) {
                abilityManager.add(card);
            }
        }
        syncAbilityManagerToController();
    }

    private void syncAbilityManagerToController() {
        getLlmController().getEventHandler().setAbilityManager(abilityManager);
    }

    public static LlmAgentConfig createLlmAgentConfig(String agentId,
                                                      String agentVersion,
                                                      String description,
                                                      List<?> workflows,
                                                      List<?> plugins,
                                                      ModelConfig model,
                                                      List<? extends Map<String, ?>> promptTemplate) {
        return createLlmAgentConfig(
                agentId,
                agentVersion,
                description,
                workflows,
                plugins,
                model,
                promptTemplate,
                null
        );
    }

    public static LlmAgentConfig createLlmAgentConfig(String agentId,
                                                      String agentVersion,
                                                      String description,
                                                      List<?> workflows,
                                                      List<?> plugins,
                                                      ModelConfig model,
                                                      List<? extends Map<String, ?>> promptTemplate,
                                                      List<String> tools) {
        LlmAgentConfig config = new LlmAgentConfig();
        config.setId(agentId);
        config.setVersion(agentVersion);
        config.setDescription(description);
        config.setWorkflows(copyWorkflowSchemas(workflows));
        config.setPlugins(copyPluginSchemas(plugins));
        config.setModel(model);
        config.setPromptTemplate(copyPromptTemplateStrings(promptTemplate));
        config.setTools(tools == null ? List.of() : tools);
        return config;
    }

    public static LlmAgent createLlmAgent(LegacyReActAgentConfig agentConfig) {
        return createLlmAgent(agentConfig, null, null);
    }

    public static LlmAgent createLlmAgent(LegacyReActAgentConfig agentConfig,
                                          List<Workflow> workflows,
                                          List<Tool> tools) {
        LlmAgent agent = new LlmAgent(agentConfig);
        agent.addWorkflows(workflows);
        agent.addTools(tools == null ? List.of() : tools);
        return agent;
    }

    public static LlmAgent createLlmAgent(LlmAgentConfig agentConfig) {
        return createLlmAgent(agentConfig, null, null);
    }

    public static LlmAgent createLlmAgent(LlmAgentConfig agentConfig,
                                          List<Workflow> workflows,
                                          List<Tool> tools) {
        return createLlmAgent(toLegacyConfig(agentConfig), workflows, tools);
    }

    public static LegacyReActAgentConfig toLegacyConfig(LlmAgentConfig source) {
        Objects.requireNonNull(source, "agentConfig");
        LegacyReActAgentConfig config = new LegacyReActAgentConfig();
        config.setId(source.getId());
        config.setVersion(source.getVersion());
        config.setDescription(source.getDescription());
        config.setControllerType(source.getControllerType());
        config.setWorkflows(source.getWorkflows());
        config.setPlugins(source.getPlugins());
        config.setModel(source.getModel());
        config.setPromptTemplate(copyPromptTemplate(source.getPromptTemplate()));
        config.setTools(source.getTools());
        config.setMemoryScopeId(source.getMemoryScopeId());
        config.setAgentMemoryConfig(source.getAgentMemoryConfig());
        if (source.getConstrain() != null) {
            com.openjiuwen.core.singleagent.legacy.config.ConstrainConfig constrain =
                    new com.openjiuwen.core.singleagent.legacy.config.ConstrainConfig();
            constrain.setReservedMaxChatRounds(source.getConstrain().getReservedMaxChatRounds());
            // Align with Python LlmAgentConfig.constrain.max_iteration wiring.
            constrain.setMaxIteration(source.getConstrain().getMaxIteration());
            config.setConstrain(constrain);
        }
        return config;
    }

    private static AgentCard toAgentCard(AgentConfig source) {
        Objects.requireNonNull(source, "agentConfig");
        return new AgentCard(valueOrEmpty(source.getId()), valueOrEmpty(source.getId()),
                valueOrEmpty(source.getDescription()));
    }

    private static AgentCard toAgentCard(LlmAgentConfig source) {
        Objects.requireNonNull(source, "agentConfig");
        return new AgentCard(valueOrEmpty(source.getId()), valueOrEmpty(source.getId()),
                valueOrEmpty(source.getDescription()));
    }

    private static LlmAgentConfig toApplicationConfig(LegacyReActAgentConfig source) {
        Objects.requireNonNull(source, "agentConfig");
        LlmAgentConfig target = new LlmAgentConfig();
        target.setId(source.getId());
        target.setVersion(source.getVersion());
        target.setDescription(source.getDescription());
        target.setControllerType(source.getControllerType());
        target.setModel(source.getModel());
        target.setPromptTemplateName(source.getPromptTemplateName());
        target.setPromptTemplate(copyPromptTemplateStrings(source.getPromptTemplate()));
        target.setTools(source.getTools());
        target.setMemoryScopeId(source.getMemoryScopeId());
        target.setAgentMemoryConfig(source.getAgentMemoryConfig());
        target.setWorkflows(copyWorkflowSchemas(source.getWorkflows()));
        return target;
    }

    private static List<WorkflowSchema> copyWorkflowSchemas(List<?> source) {
        List<WorkflowSchema> copy = new ArrayList<>();
        if (source != null) {
            for (Object item : source) {
                if (item instanceof WorkflowSchema workflowSchema) {
                    copy.add(workflowSchema);
                }
            }
        }
        return copy;
    }

    private static List<com.openjiuwen.core.application.schema.PluginSchema> copyPluginSchemas(List<?> source) {
        List<com.openjiuwen.core.application.schema.PluginSchema> copy = new ArrayList<>();
        if (source != null) {
            for (Object item : source) {
                if (item instanceof com.openjiuwen.core.application.schema.PluginSchema pluginSchema) {
                    copy.add(pluginSchema);
                }
            }
        }
        return copy;
    }

    private static int readReservedMaxChatRounds(Object config) {
        Object constrain = readProperty(config, "getConstrain");
        Object value = readProperty(constrain, "getReservedMaxChatRounds");
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 10;
    }

    private static Object readProperty(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return null;
        }
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static List<Map<String, Object>> copyPromptTemplate(List<? extends Map<String, ?>> source) {
        List<Map<String, Object>> copy = new ArrayList<>();
        if (source != null) {
            for (Map<String, ?> item : source) {
                Map<String, Object> prompt = new LinkedHashMap<>();
                if (item != null) {
                    prompt.putAll(item);
                }
                copy.add(prompt);
            }
        }
        return copy;
    }

    private static List<Map<String, Object>> copyPromptTemplateObjects(List<?> source) {
        List<Map<String, Object>> copy = new ArrayList<>();
        if (source != null) {
            for (Object item : source) {
                Map<String, Object> prompt = new LinkedHashMap<>();
                if (item instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        prompt.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                copy.add(prompt);
            }
        }
        return copy;
    }

    private static List<Map<String, String>> copyPromptTemplateStrings(List<?> source) {
        List<Map<String, String>> copy = new ArrayList<>();
        if (source != null) {
            for (Object item : source) {
                Map<String, String> prompt = new LinkedHashMap<>();
                if (item instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        prompt.put(String.valueOf(entry.getKey()),
                                entry.getValue() == null ? null : String.valueOf(entry.getValue()));
                    }
                }
                copy.add(prompt);
            }
        }
        return copy;
    }

    private static ControllerOutput toControllerOutput(Object result) {
        if (result instanceof ControllerOutput controllerOutput) {
            return controllerOutput;
        }
        if (result instanceof Iterable<?> iterable && !(result instanceof Map<?, ?>)) {
            List<Object> chunks = new ArrayList<>();
            iterable.forEach(chunks::add);
            return new ControllerOutput(EventType.TASK_COMPLETION.getValue(), chunks);
        }
        if (result instanceof OutputSchema) {
            return new ControllerOutput(EventType.TASK_COMPLETION.getValue(), List.of(result));
        }
        if (result instanceof Map<?, ?> map && map.get("interaction") instanceof List<?> interaction) {
            List<Object> chunks = new ArrayList<>(interaction);
            return new ControllerOutput(EventType.TASK_COMPLETION.getValue(), chunks);
        }
        if (result instanceof Map<?, ?> map
                && "answer".equals(map.get("result_type"))
                && map.get("output") != null) {
            return new ControllerOutput(EventType.TASK_COMPLETION.getValue(),
                    List.of(new OutputSchema("answer", 0, new LinkedHashMap<>(map))));
        }
        return new ControllerOutput(EventType.TASK_COMPLETION.getValue(), result);
    }

    private static CompletionStage<Object> toDirectInvokeStage(ControllerOutput output) {
        List<Object> chunks = outputChunks(output);
        if (chunks != null && !chunks.isEmpty()) {
            return new ControllerOutputListStage(output, chunks);
        }
        return java.util.concurrent.CompletableFuture.completedFuture(output);
    }

    private static List<Object> outputChunks(ControllerOutput output) {
        if (output.getDataAsChunks() != null) {
            return new ArrayList<>(output.getDataAsChunks());
        }
        Object data = output.getData();
        if (data instanceof Iterable<?> iterable && !(data instanceof Map<?, ?>)) {
            List<Object> chunks = new ArrayList<>();
            iterable.forEach(chunks::add);
            return chunks;
        }
        if (data instanceof OutputSchema outputSchema) {
            return List.of(outputSchema);
        }
        return null;
    }

    private static Map<String, Object> outputMap(ControllerOutput output) {
        if (output.getDataAsMap() != null) {
            return new LinkedHashMap<>(output.getDataAsMap());
        }
        Object data = output.getData();
        if (data instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static CompletionStage<Object> completedListStage(List<Object> chunks) {
        return (CompletionStage<Object>) (CompletionStage) FutureList.completed(chunks);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static CompletionStage<Object> completedMapStage(Map<String, Object> map) {
        return (CompletionStage<Object>) (CompletionStage) FutureMap.completed(map);
    }

    private static final class ControllerOutputListStage
            extends java.util.concurrent.CompletableFuture<Object>
            implements List<Object> {
        private final List<Object> delegate;

        private ControllerOutputListStage(ControllerOutput output, List<Object> chunks) {
            this.delegate = new ArrayList<>(chunks);
            complete(output);
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean contains(Object item) {
            return delegate.contains(item);
        }

        @Override
        public Iterator<Object> iterator() {
            return delegate.iterator();
        }

        @Override
        public Object[] toArray() {
            return delegate.toArray();
        }

        @Override
        public <T> T[] toArray(T[] array) {
            return delegate.toArray(array);
        }

        @Override
        public boolean add(Object item) {
            return delegate.add(item);
        }

        @Override
        public boolean remove(Object item) {
            return delegate.remove(item);
        }

        @Override
        public boolean containsAll(Collection<?> collection) {
            return delegate.containsAll(collection);
        }

        @Override
        public boolean addAll(Collection<?> collection) {
            return delegate.addAll(collection);
        }

        @Override
        public boolean addAll(int index, Collection<?> collection) {
            return delegate.addAll(index, collection);
        }

        @Override
        public boolean removeAll(Collection<?> collection) {
            return delegate.removeAll(collection);
        }

        @Override
        public boolean retainAll(Collection<?> collection) {
            return delegate.retainAll(collection);
        }

        @Override
        public void replaceAll(UnaryOperator<Object> operator) {
            delegate.replaceAll(operator);
        }

        @Override
        public void sort(Comparator<? super Object> comparator) {
            delegate.sort(comparator);
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public Object get(int index) {
            return delegate.get(index);
        }

        @Override
        public Object set(int index, Object element) {
            return delegate.set(index, element);
        }

        @Override
        public void add(int index, Object element) {
            delegate.add(index, element);
        }

        @Override
        public Object remove(int index) {
            return delegate.remove(index);
        }

        @Override
        public int indexOf(Object item) {
            return delegate.indexOf(item);
        }

        @Override
        public int lastIndexOf(Object item) {
            return delegate.lastIndexOf(item);
        }

        @Override
        public ListIterator<Object> listIterator() {
            return delegate.listIterator();
        }

        @Override
        public ListIterator<Object> listIterator(int index) {
            return delegate.listIterator(index);
        }

        @Override
        public List<Object> subList(int fromIndex, int toIndex) {
            return delegate.subList(fromIndex, toIndex);
        }

        @Override
        public Spliterator<Object> spliterator() {
            return delegate.spliterator();
        }

        @Override
        public boolean removeIf(Predicate<? super Object> filter) {
            return delegate.removeIf(filter);
        }

        @Override
        public Stream<Object> stream() {
            return delegate.stream();
        }

        @Override
        public Stream<Object> parallelStream() {
            return delegate.parallelStream();
        }

        @Override
        public void forEach(Consumer<? super Object> action) {
            delegate.forEach(action);
        }
    }
}
