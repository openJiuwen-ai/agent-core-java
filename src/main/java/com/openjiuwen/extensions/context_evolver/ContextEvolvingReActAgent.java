/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.extensions.context_evolver.core.config.Config;
import com.openjiuwen.extensions.context_evolver.core.file_connector.JSONFileConnector;
import com.openjiuwen.extensions.context_evolver.core.file_connector.SafeModelDump;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * ReActAgent with integrated memory retrieval capabilities.
 * <p>
 * This agent automatically retrieves relevant memories before invoking
 * the base ReActAgent, augmenting the input with contextual knowledge.
 * <p>
 * Mirrors Python's
 * {@code openjiuwen.extensions.context_evolver.context_evolving_react_agent.ContextEvolvingReActAgent}.
 * 
 * @since 0.1.7
 */
public class ContextEvolvingReActAgent extends ReActAgent {
    private static final Logger logger = LoggerFactory.getLogger(ContextEvolvingReActAgent.class);

    private final String userId;
    private final TaskMemoryService memoryService;
    private final boolean shouldInjectMemoriesInContext;
    private final JSONFileConnector fileConnector;
    private final Path memoryDir;

    // Cache for memory retrieval
    private String lastRetrievedQuery = null;
    private Map<String, Object> lastRetrievalResult = null;

    /**
     * Initialize ContextEvolvingReActAgent.
     * 
     * @param card Agent card (required)
     * @param userId User identifier for memory retrieval; must not contain path separators or {@code ..}
     * @param memoryService Optional pre-configured TaskMemoryService
     * @param shouldInjectMemoriesInContext If True, inject retrieved memories into system context
     * @param memoryDir Directory for memory persistence files; must resolve within the application data directory
     * @since 0.1.7
     */
    public ContextEvolvingReActAgent(AgentCard card, String userId, TaskMemoryService memoryService,
            boolean shouldInjectMemoriesInContext, String memoryDir) {
        this(card, userId, memoryService, shouldInjectMemoriesInContext, memoryDir, Path.of(""));
    }

    /**
     * Initialize with an explicit trusted application data root.
     *
     * @param card Agent card (required)
     * @param userId User identifier for memory retrieval
     * @param memoryService Optional pre-configured TaskMemoryService
     * @param shouldInjectMemoriesInContext whether to inject retrieved memories into context
     * @param memoryDir memory directory relative to {@code applicationDataRoot}
     * @param applicationDataRoot trusted root for memory persistence
     * @since 0.1.13
     */
    public ContextEvolvingReActAgent(AgentCard card, String userId, TaskMemoryService memoryService,
            boolean shouldInjectMemoriesInContext, String memoryDir, Path applicationDataRoot) {
        super(card);

        this.userId = userId;
        validateFileNameComponent(userId, "User ID");
        this.memoryService = memoryService != null ? memoryService : new TaskMemoryService();
        this.shouldInjectMemoriesInContext = shouldInjectMemoriesInContext;
        this.memoryDir = resolveMemoryDirectory(applicationDataRoot, memoryDir);
        this.fileConnector = new JSONFileConnector(this.memoryDir);

        // Attempt to load existing memories
        loadExistingMemories();

        logger.info("ContextEvolvingReActAgent initialized for user={}, inject_in_context={}", userId,
                shouldInjectMemoriesInContext);
    }

    /**
     * ContextEvolvingReActAgent.
     * 
     * @param card card
     * @param userId userId
     * @since 0.1.7
     */
    public ContextEvolvingReActAgent(AgentCard card, String userId) {
        this(card, userId, null, true, "memory_files");
    }

    /**
     * ContextEvolvingReActAgent.
     * 
     * @param card card
     * @param userId userId
     * @param memoryService memoryService
     * @since 0.1.7
     */
    public ContextEvolvingReActAgent(AgentCard card, String userId, TaskMemoryService memoryService) {
        this(card, userId, memoryService, true, "memory_files");
    }

    /**
     * ContextEvolvingReActAgent.
     * 
     * @param card card
     * @param userId userId
     * @param memoryService memoryService
     * @param shouldInjectMemoriesInContext shouldInjectMemoriesInContext
     * @since 0.1.7
     */
    public ContextEvolvingReActAgent(AgentCard card, String userId, TaskMemoryService memoryService,
            boolean shouldInjectMemoriesInContext) {
        this(card, userId, memoryService, shouldInjectMemoriesInContext, "memory_files");
    }

    /**
     * ContextEvolvingReActAgent.
     * 
     * @param card card
     * @param userId userId
     * @param shouldInjectMemoriesInContext shouldInjectMemoriesInContext
     * @since 0.1.7
     */
    public ContextEvolvingReActAgent(AgentCard card, String userId, boolean shouldInjectMemoriesInContext) {
        this(card, userId, null, shouldInjectMemoriesInContext, "memory_files");
    }

    static Path resolveMemoryDirectory(Path applicationDataRoot, String memoryDir) {
        if (applicationDataRoot == null) {
            throw new IllegalArgumentException("Application data root must not be null.");
        }
        String configuredDirectory = memoryDir == null || memoryDir.isBlank() ? "memory_files" : memoryDir;
        try {
            Files.createDirectories(applicationDataRoot);
            Path normalizedRoot = applicationDataRoot.toAbsolutePath().normalize();
            Path requestedPath = Paths.get(configuredDirectory);
            Path targetPath = requestedPath.isAbsolute()
                    ? requestedPath.toAbsolutePath().normalize()
                    : normalizedRoot.resolve(requestedPath).normalize();
            if (!targetPath.startsWith(normalizedRoot)) {
                throw new SecurityException("Memory directory is outside the application data directory.");
            }

            Path existingAncestor = targetPath;
            while (existingAncestor != null && !Files.exists(existingAncestor, LinkOption.NOFOLLOW_LINKS)) {
                existingAncestor = existingAncestor.getParent();
            }
            Path realRoot = normalizedRoot.toRealPath();
            if (existingAncestor == null || !existingAncestor.toRealPath().startsWith(realRoot)) {
                throw new SecurityException("Memory directory is outside the application data directory.");
            }

            Files.createDirectories(targetPath);
            Path realMemoryDirectory = targetPath.toRealPath();
            if (!realMemoryDirectory.startsWith(realRoot)) {
                throw new SecurityException("Memory directory is outside the application data directory.");
            }
            return realMemoryDirectory;
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to resolve memory directory.", e);
        }
    }

    /**
     * loadExistingMemories.
     * 
     * @since 0.1.7
     */
    private void loadExistingMemories() {
        try {
            String summaryAlgo = getConfig("SUMMARY_ALGO", "RB");
            String filename = buildMemoryFileName(summaryAlgo, userId);

            if (fileConnector.existsWithinRoot(filename)) {
                logger.info("Found existing memory file: {}/{}", memoryDir, filename);
                Map<String, Object> data = fileConnector.loadFromFile(filename);

                if (memoryService.getVectorStore() != null) {
                    int count = 0;
                    for (Map.Entry<String, Object> entry : data.entrySet()) {
                        try {
                            String nodeId = entry.getKey();
                            @SuppressWarnings("unchecked")
                            Map<String, Object> nodeData = (Map<String, Object>) entry.getValue();
                            VectorNode node = VectorNode.fromDict(nodeData);
                            memoryService.getVectorStore().loadNode(nodeId, node);
                            count++;
                        } catch (Exception e) {
                            logger.warn("Failed to load node: {}", e.getMessage());
                        }
                    }
                    logger.info("Loaded {} memories into vector store from {}", count, filename);
                } else {
                    logger.warn("Memory service does not expose vector_store, cannot load memories.");
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load existing memories: {}", e.getMessage());
        }
    }

    /**
     * invoke.
     * 
     * @param inputs inputs
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object invoke(Object inputs, Session session) {
        try {
            Map<String, Object> inputMap;
            String query;

            if (inputs instanceof Map<?, ?> map) {
                inputMap = new HashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        inputMap.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                Object queryValue = inputMap.get("query");
                query = queryValue != null ? String.valueOf(queryValue) : "";
            } else if (inputs instanceof String text) {
                query = text;
                inputMap = new HashMap<>();
                inputMap.put("query", query);
            } else {
                query = "";
                inputMap = new HashMap<>();
            }

            if (query == null || query.isEmpty()) {
                logger.warn("No query provided in inputs");
                return super.invoke(inputs, session);
            }

            String retrievalQuery =
                inputMap.containsKey("retrieval_query") ? String.valueOf(inputMap.get("retrieval_query")) : query;

            logger.debug("Retrieving memories for query: {}", retrievalQuery);

            int memoriesUsed = 0;
            String memoryString = "";
            try {
                Map<String, Object> memoryResult;
                if (retrievalQuery.equals(lastRetrievedQuery) && lastRetrievalResult != null) {
                    memoryResult = lastRetrievalResult;
                    logger.info("Reusing cached memory retrieval result");
                } else {
                    memoryResult = memoryService.retrieve(userId, retrievalQuery).join();
                    lastRetrievedQuery = retrievalQuery;
                    lastRetrievalResult = memoryResult;
                    @SuppressWarnings("unchecked")
                    List<?> retrievedMemory = (List<?>) memoryResult.get("retrieved_memory");
                    logger.info("Retrieved {} memories for query",
                            retrievedMemory != null ? retrievedMemory.size() : 0);
                }

                memoryString = (String) memoryResult.getOrDefault("memory_string", "");
                @SuppressWarnings("unchecked")
                List<?> retrievedMemory = (List<?>) memoryResult.get("retrieved_memory");
                memoriesUsed = retrievedMemory != null ? retrievedMemory.size() : 0;
            } catch (Exception e) {
                logger.error("Failed to retrieve memories: {}", e.getMessage());
            }

            Map<String, Object> augmentedInput = new HashMap<>(inputMap);
            if (memoriesUsed > 0 && memoryString != null && !memoryString.isEmpty()) {
                if (shouldInjectMemoriesInContext) {
                    String memoryContext =
                        "Some Related Experience to help you complete the task:\n" + memoryString + "\n";
                    augmentedInput.put("query", "Task:\n" + query + "\n\n" + memoryContext);
                } else {
                    augmentedInput.put("memory_context", memoryString);
                    augmentedInput.put("memories_used", memoriesUsed);
                }
            }

            Object rawResult = super.invoke(augmentedInput, session);
            if (rawResult instanceof Map<?, ?> mapResult) {
                Map<String, Object> result = new HashMap<>();
                for (Map.Entry<?, ?> entry : mapResult.entrySet()) {
                    if (entry.getKey() != null) {
                        result.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                result.put("memories_used", memoriesUsed);
                return result;
            }
            return rawResult;
        } catch (Exception e) {
            logger.error("Failed to invoke agent: {}", e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }

    /**
     * Format a list of messages into a clean trajectory string.
     * 
     * @param messages messages
     * @return the result
     * @since 0.1.7
     */
    public String formatTrajectory(List<Object> messages) {
        List<String> transcript = new ArrayList<>();

        for (Object msg : messages) {
            if (msg instanceof UserMessage) {
                String content = ((UserMessage) msg).getContentAsString();

                // Remove injected prompts
                if (content.contains("Let's carefully re-examine the previous trajectory")) {
                    content = content.split("Let's carefully re-examine the previous trajectory")[0];
                }
                if (content.contains("Some Related Experience to help you complete the task")) {
                    content = content.split("Some Related Experience to help you complete the task")[0];
                }
                if (content.startsWith("Task:\n")) {
                    content = content.substring(6);
                }

                transcript.add("USER: " + content.trim());
            } else if (msg instanceof AssistantMessage) {
                AssistantMessage am = (AssistantMessage) msg;
                if (am.getContentAsString() != null && !am.getContentAsString().isEmpty()) {
                    transcript.add("THOUGHT: " + am.getContentAsString());
                }
                if (am.getToolCalls() != null) {
                    for (ToolCall toolCall : am.getToolCalls()) {
                        transcript.add("ACTION: " + toolCall.getName() + "(" + toolCall.getArguments() + ")");
                    }
                }
            } else if (msg instanceof ToolMessage) {
                transcript.add("OBSERVATION: " + ((ToolMessage) msg).getContentAsString());
            } else {
                // no-op
            }
        }

        return String.join("\n", transcript);
    }

    /**
     * Add a tool to this agent and register it with the resource manager.
     * 
     * @param tool tool
     * @since 0.1.7
     */
    public void addTool(Tool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("tool is required");
        }
        getAbilityManager().add(tool.getCard());
        tryRegisterTool(tool);
    }

    /**
     * Add multiple tools to this agent.
     * 
     * @param tools tools
     * @since 0.1.7
     */
    public void addTools(List<? extends Tool> tools) {
        if (tools == null) {
            return;
        }
        for (Tool tool : tools) {
            addTool(tool);
        }
    }

    /**
     * tryRegisterTool.
     * 
     * @param tool tool
     * @since 0.1.7
     */
    private void tryRegisterTool(Tool tool) {
        try {
            Class<?> runnerClass = Class.forName("com.openjiuwen.core.runner.Runner");
            Object resourceMgr = runnerClass.getMethod("resourceMgr").invoke(null);
            resourceMgr.getClass().getMethod("addTool", Tool.class, Object.class).invoke(resourceMgr, tool,
                    getCard().getId());
        } catch (ClassNotFoundException | LinkageError e) {
            logger.debug("Runner runtime is unavailable; skipping resource-manager registration for {}",
                    tool.getCard().getId());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to register tool " + tool.getCard().getId(), e);
        }
    }

    /**
     * Summarize trajectory based on feedback and save to JSON.
     * 
     * @param params params
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Map<String, Object>> summarizeTrajectories(SummarizeTrajectoriesInput params) {
        return OpenJiuwenExecutors.supplyBackgroundAsync(() -> {
            try {
                // Handle trajectory(ies)
                List<String> trajectories = new ArrayList<>();
                if (params.getTrajectory() instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> trajList = (List<String>) params.getTrajectory();
                    trajectories.addAll(trajList);
                } else if (params.getTrajectory() instanceof String) {
                    trajectories.add((String) params.getTrajectory());
                }

                // Handle feedback/labels
                List<Boolean> labels = new ArrayList<>();
                if (params.getFeedback() != null) {
                    if (params.getFeedback() instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> feedbackList = (List<Object>) params.getFeedback();
                        for (Object f : feedbackList) {
                            labels.add(convertToBoolean(f));
                        }
                    } else {
                        labels.add(convertToBoolean(params.getFeedback()));
                    }
                }

                // Handle scores
                List<Integer> scores = params.getScores() != null ? params.getScores() : new ArrayList<>();
                if (scores.isEmpty() && !labels.isEmpty()) {
                    for (Boolean label : labels) {
                        scores.add(label ? 1 : 0);
                    }
                }

                // For sequential mode, use only the last trajectory
                if ("sequential".equals(params.getMattsMode())) {
                    if (!trajectories.isEmpty()) {
                        trajectories = trajectories.subList(trajectories.size() - 1, trajectories.size());
                    }
                    if (!labels.isEmpty()) {
                        labels = labels.subList(labels.size() - 1, labels.size());
                    }
                    if (!scores.isEmpty()) {
                        scores = scores.subList(scores.size() - 1, scores.size());
                    }
                }

                Map<String, Object> summaryResult = memoryService
                        .summarize(userId, params.getMattsMode(), params.getQuery(), trajectories, labels, scores)
                        .join();

                // Save memories to file
                saveMemoriesToFile(summaryResult);

                return summaryResult;
            } catch (Exception e) {
                logger.error("Failed to learn from feedback: {}", e.getMessage());
                return null;
            }
        });
    }

    /**
     * saveMemoriesToFile.
     * 
     * @param summaryResult summaryResult
     * @since 0.1.7
     */
    private void saveMemoriesToFile(Map<String, Object> summaryResult) {
        try {
            String summaryAlgo = getConfig("SUMMARY_ALGO", "RB");
            String filename = buildMemoryFileName(summaryAlgo, userId);

            if (memoryService.getVectorStore() != null) {
                List<VectorNode> allNodes = memoryService.getVectorStore().getAll();
                Map<String, Object> allMemoriesData = new HashMap<>();

                for (VectorNode node : allNodes) {
                    try {
                        allMemoriesData.put(node.getId(), SafeModelDump.safeModelDump(node));
                    } catch (Exception e) {
                        logger.warn("Skipping node {} serialization: {}", node.getId(), e.getMessage());
                    }
                }

                fileConnector.saveToFile(filename, allMemoriesData);
                logger.info("Persisted {} total memories to {}/{}", allMemoriesData.size(), memoryDir, filename);
            }
        } catch (Exception e) {
            logger.error("Failed to save full memory store: {}", e.getMessage());
        }
    }

    static String buildMemoryFileName(String summaryAlgo, String userId) {
        validateFileNameComponent(summaryAlgo, "Summary algorithm");
        validateFileNameComponent(userId, "User ID");
        return "memory_" + summaryAlgo + "_" + userId + ".json";
    }

    private static void validateFileNameComponent(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
        if (".".equals(value) || value.contains("..") || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException(label + " contains an invalid path sequence.");
        }
        Path component = Path.of(value);
        if (component.isAbsolute() || component.getNameCount() != 1) {
            throw new IllegalArgumentException(label + " must be a single file-name component.");
        }
    }

    /**
     * convertToBoolean.
     * 
     * @param feedback feedback
     * @return the result
     * @since 0.1.7
     */
    private boolean convertToBoolean(Object feedback) {
        if (feedback instanceof Boolean) {
            return (Boolean) feedback;
        }
        String fLower = feedback.toString().toLowerCase(java.util.Locale.ROOT);
        return fLower.contains("success") || fLower.contains("helpful") || fLower.contains("positive")
                || fLower.contains("good");
    }

    /**
     * getConfig.
     * 
     * @param key key
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    private String getConfig(String key, String defaultValue) {
        return Config.getString(key, defaultValue);
    }

    // Getters
    /**
     * getUserId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getUserId() {
        return userId;
    }

    /**
     * getMemoryService.
     * 
     * @return the result
     * @since 0.1.7
     */
    public TaskMemoryService getMemoryService() {
        return memoryService;
    }

    /**
     * isInjectMemoriesInContext.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isInjectMemoriesInContext() {
        return shouldInjectMemoriesInContext;
    }

    /**
     * getMemoryDir.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getMemoryDir() {
        return memoryDir.toString();
    }

    /**
     * Mirrors Python's {@code create_memory_agent_config()} helper.
     * 
     * @param params params
     * @return the result
     * @since 0.1.7
     */
    public static ReActAgentConfig createMemoryAgentConfig(MemoryAgentConfigInput params) {
        String defaultSystemPrompt = "You are a helpful assistant with access to a memory system. "
                + "When relevant memories are provided in your context, use them to inform "
                + "your responses. Always provide accurate, helpful answers based on both "
                + "your knowledge and any retrieved memories.";

        List<Map<String, String>> promptTemplate = List.of(Map.of("role", "system", "content",
                params.getSystemPrompt() != null ? params.getSystemPrompt() : defaultSystemPrompt));

        return ReActAgentConfig.builder().build()
                .configureModelClient(params.getModelProvider(), params.getApiKey(), params.getApiBase(),
                        params.getModelName(), false)
                .configurePromptTemplate(promptTemplate).configureMaxIterations(params.getMaxIterations());
    }
}
