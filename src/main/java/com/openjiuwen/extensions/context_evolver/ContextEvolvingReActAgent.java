// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.extensions.context_evolver;

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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * ReActAgent with integrated memory retrieval capabilities.
 *
 * <p>This agent automatically retrieves relevant memories before invoking
 * the base ReActAgent, augmenting the input with contextual knowledge.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.context_evolver.context_evolving_react_agent.ContextEvolvingReActAgent}.
 */
public class ContextEvolvingReActAgent extends ReActAgent {

    private static final Logger logger = LoggerFactory.getLogger(ContextEvolvingReActAgent.class);

    private final String userId;
    private final TaskMemoryService memoryService;
    private final boolean injectMemoriesInContext;
    private final JSONFileConnector fileConnector;
    private final String memoryDir;

    // Cache for memory retrieval
    private String lastRetrievedQuery = null;
    private Map<String, Object> lastRetrievalResult = null;

    /**
     * Initialize ContextEvolvingReActAgent.
     *
     * @param card                     Agent card (required)
     * @param userId                   User identifier for memory retrieval
     * @param memoryService            Optional pre-configured TaskMemoryService
     * @param injectMemoriesInContext  If True, inject retrieved memories into system context
     * @param memoryDir                Directory for memory persistence files
     */
    public ContextEvolvingReActAgent(
            AgentCard card,
            String userId,
            TaskMemoryService memoryService,
            boolean injectMemoriesInContext,
            String memoryDir) {
        super(card);

        this.userId = userId;
        this.memoryService = memoryService != null ? memoryService : new TaskMemoryService();
        this.injectMemoriesInContext = injectMemoriesInContext;
        this.fileConnector = new JSONFileConnector();
        this.memoryDir = memoryDir != null ? memoryDir : "memory_files";

        // Ensure directories exist
        try {
            Files.createDirectories(Paths.get(this.memoryDir));
        } catch (Exception e) {
            logger.warn("Failed to create memory directory: {}", e.getMessage());
        }

        // Attempt to load existing memories
        loadExistingMemories();

        logger.info("ContextEvolvingReActAgent initialized for user={}, inject_in_context={}",
                userId, injectMemoriesInContext);
    }

    public ContextEvolvingReActAgent(AgentCard card, String userId) {
        this(card, userId, null, true, "memory_files");
    }

    public ContextEvolvingReActAgent(AgentCard card, String userId, TaskMemoryService memoryService) {
        this(card, userId, memoryService, true, "memory_files");
    }

    public ContextEvolvingReActAgent(
            AgentCard card,
            String userId,
            TaskMemoryService memoryService,
            boolean injectMemoriesInContext) {
        this(card, userId, memoryService, injectMemoriesInContext, "memory_files");
    }

    public ContextEvolvingReActAgent(AgentCard card, String userId, boolean injectMemoriesInContext) {
        this(card, userId, null, injectMemoriesInContext, "memory_files");
    }

    private void loadExistingMemories() {
        try {
            String summaryAlgo = getConfig("SUMMARY_ALGO", "RB");
            String filename = "memory_" + summaryAlgo + "_" + userId + ".json";
            Path filePath = Paths.get(memoryDir, filename);

            if (Files.exists(filePath)) {
                logger.info("Found existing memory file: {}", filePath);
                Map<String, Object> data = fileConnector.loadFromFile(filePath.toString());

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

            String retrievalQuery = inputMap.containsKey("retrieval_query")
                    ? String.valueOf(inputMap.get("retrieval_query"))
                    : query;

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
                if (injectMemoriesInContext) {
                    String memoryContext = "Some Related Experience to help you complete the task:\n"
                            + memoryString + "\n";
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
            }
        }

        return String.join("\n", transcript);
    }

    /**
     * Add a tool to this agent and register it with the resource manager.
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
     */
    public void addTools(List<? extends Tool> tools) {
        if (tools == null) {
            return;
        }
        for (Tool tool : tools) {
            addTool(tool);
        }
    }

    private void tryRegisterTool(Tool tool) {
        try {
            Class<?> runnerClass = Class.forName("com.openjiuwen.core.runner.Runner");
            Object resourceMgr = runnerClass.getMethod("resourceMgr").invoke(null);
            resourceMgr.getClass()
                .getMethod("addTool", Tool.class, Object.class)
                .invoke(resourceMgr, tool, getCard().getId());
        } catch (ClassNotFoundException | LinkageError e) {
            logger.debug("Runner runtime is unavailable; skipping resource-manager registration for {}",
                tool.getCard().getId());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to register tool " + tool.getCard().getId(), e);
        }
    }

    /**
     * Summarize trajectory based on feedback and save to JSON.
     */
    public CompletableFuture<Map<String, Object>> summarizeTrajectories(SummarizeTrajectoriesInput params) {
        return CompletableFuture.supplyAsync(() -> {
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

                Map<String, Object> summaryResult = memoryService.summarize(
                        userId,
                        params.getMattsMode(),
                        params.getQuery(),
                        trajectories,
                        labels,
                        scores
                ).join();

                // Save memories to file
                saveMemoriesToFile(summaryResult);

                return summaryResult;

            } catch (Exception e) {
                logger.error("Failed to learn from feedback: {}", e.getMessage());
                return null;
            }
        });
    }

    private void saveMemoriesToFile(Map<String, Object> summaryResult) {
        try {
            String summaryAlgo = getConfig("SUMMARY_ALGO", "RB");
            String filename = "memory_" + summaryAlgo + "_" + userId + ".json";
            Path filePath = Paths.get(memoryDir, filename);

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

                fileConnector.saveToFile(filePath.toString(), allMemoriesData);
                logger.info("Persisted {} total memories to {}", allMemoriesData.size(), filePath);
            }
        } catch (Exception e) {
            logger.error("Failed to save full memory store: {}", e.getMessage());
        }
    }

    private boolean convertToBoolean(Object feedback) {
        if (feedback instanceof Boolean) {
            return (Boolean) feedback;
        }
        String fLower = feedback.toString().toLowerCase();
        return fLower.contains("success") || fLower.contains("helpful")
                || fLower.contains("positive") || fLower.contains("good");
    }

    private String getConfig(String key, String defaultValue) {
        return Config.getString(key, defaultValue);
    }

    // Getters
    public String getUserId() {
        return userId;
    }

    public TaskMemoryService getMemoryService() {
        return memoryService;
    }

    public boolean isInjectMemoriesInContext() {
        return injectMemoriesInContext;
    }

    public String getMemoryDir() {
        return memoryDir;
    }

    /**
     * Mirrors Python's {@code create_memory_agent_config()} helper.
     */
    public static ReActAgentConfig createMemoryAgentConfig(MemoryAgentConfigInput params) {
        String defaultSystemPrompt = "You are a helpful assistant with access to a memory system. "
                + "When relevant memories are provided in your context, use them to inform "
                + "your responses. Always provide accurate, helpful answers based on both "
                + "your knowledge and any retrieved memories.";

        List<Map<String, String>> promptTemplate = List.of(Map.of(
                "role", "system",
                "content", params.getSystemPrompt() != null ? params.getSystemPrompt() : defaultSystemPrompt
        ));

        return ReActAgentConfig.builder().build()
                .configureModelClient(
                        params.getModelProvider(),
                        params.getApiKey(),
                        params.getApiBase(),
                        params.getModelName(),
                        false
                )
                .configurePromptTemplate(promptTemplate)
                .configureMaxIterations(params.getMaxIterations());
    }
}
