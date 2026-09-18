/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Dedicated session-memory updater backed by either an edit-capable agent port or direct model replacement.
 *
 * <p>Mirrors Python's {@code SessionMemoryUpdateAgent} in
 * {@code openjiuwen/core/context_engine/context/session_memory_manager.py}.</p>
 */
public class SessionMemoryUpdateAgent {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionMemoryUpdateAgent.class);

    private final SessionMemoryConfig config;
    private final String toolNamespace;
    private AgentFactory agentFactory;
    private SessionFactory sessionFactory;
    private AgentPort agent;
    private Model directModel;
    private String inheritedSystemPrompt = "";
    private String workspaceRoot;

    public SessionMemoryUpdateAgent(SessionMemoryConfig config) {
        this(config, null, null);
    }

    public SessionMemoryUpdateAgent(SessionMemoryConfig config, AgentFactory agentFactory, SessionFactory sessionFactory) {
        this.config = config == null ? new SessionMemoryConfig() : config;
        this.agentFactory = agentFactory;
        this.sessionFactory = sessionFactory;
        this.toolNamespace = "session_memory_update_" + UUID.randomUUID().toString().replace("-", "");
    }

    public void bindModelDefaults(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        if (config.getModel() == null) {
            config.setModel(modelConfig);
        }
        if (config.getModelClient() == null) {
            config.setModelClient(modelClientConfig);
        }
    }

    public void setInheritedSystemPrompt(String inheritedSystemPrompt) {
        this.inheritedSystemPrompt = inheritedSystemPrompt == null ? "" : inheritedSystemPrompt;
        refreshPromptTemplate();
    }

    public String getInheritedSystemPrompt() {
        return inheritedSystemPrompt;
    }

    public void setAgentFactory(AgentFactory agentFactory) {
        this.agentFactory = agentFactory;
        this.agent = null;
        this.workspaceRoot = null;
    }

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public void setDirectModel(Model directModel) {
        this.directModel = directModel;
    }

    public CompletionStage<Void> invoke(List<BaseMessage> fullContextMessages, Path notesPath, String currentNotes) {
        List<BaseMessage> safeMessages = fullContextMessages == null ? List.of() : List.copyOf(fullContextMessages);
        if (config.getUpdateMode() == SessionMemoryConfig.UpdateMode.DIRECT_REPLACE) {
            return invokeDirectReplace(safeMessages, notesPath, currentNotes);
        }

        ensureAgent(notesPath);
        primeNotesFileAsRead(notesPath, currentNotes);
        String query = SessionMemorySupport.buildSessionMemoryPrompt(String.valueOf(notesPath), currentNotes);
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", query);
        inputs.put("conversation_id", toolNamespace);

        UpdateAgentSessionPort session = createAgentSession();
        return session.preRun(inputs)
                .thenCompose(ignored -> primeInheritedContext(session, safeMessages))
                .thenCompose(ignored -> agent.invoke(inputs, session))
                .handle((response, failure) -> failure)
                .thenCompose(failure -> closeStreamAndCommit(session, failure));
    }

    private CompletionStage<Void> invokeDirectReplace(List<BaseMessage> fullContextMessages, Path notesPath,
                                                      String currentNotes) {
        Model model = ensureDirectModel();
        List<BaseMessage> promptMessages = new ArrayList<>();
        String systemPrompt = inheritedSystemPrompt == null ? "" : inheritedSystemPrompt.strip();
        if (!systemPrompt.isBlank()) {
            promptMessages.add(new SystemMessage(systemPrompt));
        }
        promptMessages.addAll(fullContextMessages);
        promptMessages.add(new UserMessage(
                SessionMemorySupport.buildDirectSessionMemoryPrompt(String.valueOf(notesPath), currentNotes)
        ));
        return invokeDirectModelWithRetry(model, promptMessages)
                .thenAccept(response -> {
                    String content = SessionMemorySupport.normalizeDirectResponseContent(response.getContent());
                    if (content.isBlank()) {
                        throw new CompletionException(
                                new IllegalStateException("Session memory direct replace returned empty content"));
                    }
                    try {
                        Files.writeString(notesPath, content, StandardCharsets.UTF_8);
                    } catch (IOException ex) {
                        throw new CompletionException(ex);
                    }
                });
    }

    private CompletionStage<Void> primeInheritedContext(UpdateAgentSessionPort session,
                                                        List<BaseMessage> fullContextMessages) {
        if (agent == null || fullContextMessages == null || fullContextMessages.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return agent.initContext(session).thenCompose(context -> {
            if (context == null) {
                throw new CompletionException(new IllegalStateException(
                        "Session memory update agent does not support context initialization"));
            }
            List<BaseMessage> existingMessages = context.getMessages();
            if (existingMessages != null && !existingMessages.isEmpty()) {
                LOGGER.warn("agent context is empty");
                return CompletableFuture.completedFuture(null);
            }
            return context.addMessages(fullContextMessages);
        });
    }

    private void ensureAgent(Path notesPath) {
        if (notesPath == null) {
            throw new IllegalArgumentException("notesPath must not be null");
        }
        String resolvedWorkspace = String.valueOf(notesPath.getParent().getParent());
        if (agent != null && resolvedWorkspace.equals(workspaceRoot)) {
            return;
        }
        if (agentFactory == null) {
            throw new IllegalStateException("Session memory update agent factory is not configured");
        }
        agent = agentFactory.createAgent(notesPath, config, toolNamespace, inheritedSystemPrompt);
        workspaceRoot = resolvedWorkspace;
    }

    private Model ensureDirectModel() {
        if (directModel != null) {
            return directModel;
        }
        if (config.getModel() == null || config.getModelClient() == null) {
            throw new IllegalStateException("Session memory direct replace requires model and model_client config");
        }
        directModel = new Model(config.getModelClient(), config.getModel());
        return directModel;
    }

    private CompletionStage<AssistantMessage> invokeDirectModelWithRetry(Model model, List<BaseMessage> promptMessages) {
        CompletableFuture<AssistantMessage> result = new CompletableFuture<>();
        invokeDirectModelAttempt(model, promptMessages, 1, config.getDirectReplaceMaxRetries() + 1, result);
        return result;
    }

    private void invokeDirectModelAttempt(Model model, List<BaseMessage> promptMessages, int attempt, int attempts,
                                          CompletableFuture<AssistantMessage> result) {
        model.invoke(promptMessages).whenComplete((response, failure) -> {
            if (failure == null) {
                result.complete(response);
                return;
            }
            Throwable cause = unwrap(failure);
            if (attempt >= attempts) {
                result.completeExceptionally(cause);
                return;
            }
            LOGGER.warn("[SessionMemory] direct_replace model invoke failed attempt={}/{}, retrying: {}",
                    attempt, attempts, cause.getMessage());
            invokeDirectModelAttempt(model, promptMessages, attempt + 1, attempts, result);
        });
    }

    private List<Map<String, String>> buildPromptTemplate(String systemPrompt) {
        String normalized = systemPrompt == null ? "" : systemPrompt.strip();
        if (normalized.isBlank()) {
            return List.of();
        }
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", "system");
        message.put("content", normalized);
        return List.of(message);
    }

    private void refreshPromptTemplate() {
        if (agent != null) {
            agent.refreshPromptTemplate(buildPromptTemplate(inheritedSystemPrompt));
        }
    }

    private UpdateAgentSessionPort createAgentSession() {
        if (sessionFactory == null) {
            throw new IllegalStateException("Session memory update agent session factory is not configured");
        }
        return sessionFactory.createSession(toolNamespace + "_" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8), agent);
    }

    private void primeNotesFileAsRead(Path notesPath, String currentNotes) {
        if (notesPath == null || currentNotes == null || !Files.exists(notesPath)) {
            return;
        }
        // Python primes the filesystem tool's read registry here. The Java port keeps this as a no-op because the
        // edit-capable agent is supplied through AgentPort rather than the Python filesystem tool registry.
    }

    private CompletionStage<Void> closeStreamAndCommit(UpdateAgentSessionPort session, Throwable originalFailure) {
        return session.closeStream()
                .thenCompose(ignored -> session.commit())
                .handle((ignored, lifecycleFailure) -> {
                    if (lifecycleFailure != null) {
                        throw new CompletionException(unwrap(lifecycleFailure));
                    }
                    if (originalFailure != null) {
                        throw new CompletionException(unwrap(originalFailure));
                    }
                    return null;
                });
    }

    private static Throwable unwrap(Throwable failure) {
        if (failure instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return failure;
    }

    /**
     * Minimal context port initialized by the session-memory update agent.
     *
     * <p>Mirrors Python's dynamically returned agent context in
     * {@code openjiuwen/core/context_engine/context/session_memory_manager.py}.</p>
     */
    public interface AgentContextPort {
        List<BaseMessage> getMessages();

        CompletionStage<Void> addMessages(List<BaseMessage> messages);
    }

    /**
     * Minimal edit-capable agent port used by the updater.
     *
     * <p>Mirrors Python's {@code ReActAgent} dependency in
     * {@code openjiuwen/core/context_engine/context/session_memory_manager.py}.</p>
     */
    public interface AgentPort {
        CompletionStage<AgentContextPort> initContext(UpdateAgentSessionPort session);

        CompletionStage<?> invoke(Map<String, Object> inputs, UpdateAgentSessionPort session);

        default void refreshPromptTemplate(List<Map<String, String>> promptTemplate) {
        }
    }

    /**
     * Session lifecycle port used by the update agent.
     *
     * <p>Mirrors Python's agent session object in
     * {@code openjiuwen/core/context_engine/context/session_memory_manager.py}.</p>
     */
    public interface UpdateAgentSessionPort {
        CompletionStage<Void> preRun(Map<String, Object> inputs);

        CompletionStage<Void> closeStream();

        CompletionStage<Void> commit();
    }

    /**
     * Factory for the edit-capable update agent.
     *
     * <p>Mirrors Python's lazy {@code ReActAgent(...).configure(...)} construction in
     * {@code openjiuwen/core/context_engine/context/session_memory_manager.py}.</p>
     */
    public interface AgentFactory {
        AgentPort createAgent(Path notesPath, SessionMemoryConfig config, String toolNamespace,
                              String inheritedSystemPrompt);
    }

    /**
     * Factory for updater sessions.
     *
     * <p>Mirrors Python's {@code create_agent_session(...)} call in
     * {@code openjiuwen/core/context_engine/context/session_memory_manager.py}.</p>
     */
    public interface SessionFactory {
        UpdateAgentSessionPort createSession(String sessionId, AgentPort agent);
    }
}
