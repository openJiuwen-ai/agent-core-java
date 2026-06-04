/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.external;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.store.db.DefaultDbStore;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.MemResult;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.memory.config.MemoryScopeConfig;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.embedding.HashEmbedding;
import com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import com.openjiuwen.spi.store.BaseDbStore;
import com.openjiuwen.spi.store.BaseKVStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * OpenJiuwen external memory provider based on LongTermMemory.
 * <p>
 * Mirrors Python's {@code OpenJiuwenMemoryProvider} from
 * {@code core/memory/external/openjiuwen_memory_provider.py}.
 * <p>
 * Java adaptation note: LongTermMemory is wrapped by a small client interface
 * so tests can verify the provider behavior without starting a full memory
 * engine.
 */
public class OpenJiuwenMemoryProvider extends MemoryProvider {

    static final int DEFAULT_RECALL_USER_MEM_NUM = 5;
    static final int DEFAULT_RECALL_HISTORY_MEM_NUM = 3;

    static final Map<String, Object> LTM_SEARCH_SCHEMA = Map.of(
            "name", "ltm_search",
            "description", "Search related information in long-term memory.",
            "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "query", Map.of("type", "string", "description", "Search query."),
                            "num", Map.of("type", "integer", "description", "Max results.",
                                    "default", DEFAULT_RECALL_USER_MEM_NUM),
                            "threshold", Map.of("type", "number", "description", "Minimum relevance.",
                                    "default", 0.3)
                    ),
                    "required", List.of("query")
            )
    );

    static final Map<String, Object> LTM_SEARCH_SUMMARY_SCHEMA = Map.of(
            "name", "ltm_search_summary",
            "description", "Search long-term conversation summaries.",
            "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "query", Map.of("type", "string", "description", "Search query."),
                            "num", Map.of("type", "integer", "description", "Max results.",
                                    "default", DEFAULT_RECALL_HISTORY_MEM_NUM)
                    ),
                    "required", List.of("query")
            )
    );

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_ID = "__default__";

    private final Map<String, Object> config;
    private BaseKVStore kvStore;
    private VectorStore vectorStore;
    private BaseDbStore<?> dbStore;
    private Embedding embeddingModel;
    private final MemoryScopeConfig scopeConfig;
    private final AgentMemoryConfig agentMemoryConfig;
    private final Supplier<LongTermMemoryClient> ltmSupplier;
    private LongTermMemoryClient ltm;
    private boolean initialized;
    private String userId = DEFAULT_ID;
    private String scopeId = DEFAULT_ID;
    private String sessionId = DEFAULT_ID;

    interface LongTermMemoryClient {
        BaseKVStore getKvStore();

        void registerStore(BaseKVStore kvStore, VectorStore vectorStore, BaseDbStore<?> dbStore, Embedding embedding);

        boolean setScopeConfig(String scopeId, MemoryScopeConfig scopeConfig);

        List<MemResult> searchUserMem(String query, int num, String userId, String scopeId, double threshold);

        List<MemResult> searchUserHistorySummary(String query, int num, String userId, String scopeId, double threshold);

        void addMessages(List<BaseMessage> messages, AgentMemoryConfig config,
                         String userId, String scopeId, String sessionId);
    }

    static final class RealLongTermMemoryClient implements LongTermMemoryClient {
        private final LongTermMemory delegate;

        private RealLongTermMemoryClient(LongTermMemory delegate) {
            this.delegate = delegate;
        }

        @Override
        public BaseKVStore getKvStore() {
            return delegate.getKvStore();
        }

        @Override
        public void registerStore(BaseKVStore kvStore, VectorStore vectorStore, BaseDbStore<?> dbStore, Embedding embedding) {
            delegate.registerStore(kvStore, vectorStore, dbStore, embedding);
        }

        @Override
        public boolean setScopeConfig(String scopeId, MemoryScopeConfig scopeConfig) {
            return delegate.setScopeConfig(scopeId, scopeConfig);
        }

        @Override
        public List<MemResult> searchUserMem(String query, int num, String userId, String scopeId, double threshold) {
            return delegate.searchUserMem(query, num, userId, scopeId, threshold);
        }

        @Override
        public List<MemResult> searchUserHistorySummary(String query, int num, String userId,
                                                        String scopeId, double threshold) {
            return delegate.searchUserHistorySummary(query, num, userId, scopeId, threshold);
        }

        @Override
        public void addMessages(List<BaseMessage> messages, AgentMemoryConfig config,
                                String userId, String scopeId, String sessionId) {
            delegate.addMessages(messages, config, userId, scopeId, sessionId);
        }
    }

    public OpenJiuwenMemoryProvider() {
        this(null);
    }

    public OpenJiuwenMemoryProvider(Map<String, Object> config) {
        this(config, null, null, null, null, null, new AgentMemoryConfig(),
                () -> new RealLongTermMemoryClient(LongTermMemory.getInstance()));
    }

    public OpenJiuwenMemoryProvider(Map<String, Object> config,
                                    BaseKVStore kvStore,
                                    VectorStore vectorStore,
                                    BaseDbStore<?> dbStore,
                                    Embedding embeddingModel) {
        this(config, kvStore, vectorStore, dbStore, embeddingModel, null, new AgentMemoryConfig(),
                () -> new RealLongTermMemoryClient(LongTermMemory.getInstance()));
    }

    OpenJiuwenMemoryProvider(Map<String, Object> config,
                             BaseKVStore kvStore,
                             VectorStore vectorStore,
                             BaseDbStore<?> dbStore,
                             Embedding embeddingModel,
                             MemoryScopeConfig scopeConfig,
                             AgentMemoryConfig agentMemoryConfig,
                             Supplier<LongTermMemoryClient> ltmSupplier) {
        this.config = config == null ? new LinkedHashMap<>() : new LinkedHashMap<>(config);
        this.kvStore = kvStore;
        this.vectorStore = vectorStore;
        this.dbStore = dbStore;
        this.embeddingModel = embeddingModel;
        this.scopeConfig = scopeConfig != null ? scopeConfig : parseScopeConfig(this.config);
        this.agentMemoryConfig = agentMemoryConfig == null ? new AgentMemoryConfig() : agentMemoryConfig;
        this.ltmSupplier = Objects.requireNonNull(ltmSupplier, "ltmSupplier");
    }

    @Override
    public String name() {
        return "openjiuwen";
    }

    @Override
    public boolean isAvailable() {
        if (kvStore != null && vectorStore != null && dbStore != null) {
            return true;
        }
        Object embedding = config.get("embedding");
        if (embedding instanceof Map<?, ?> embeddingMap) {
            Object modelName = embeddingMap.get("model_name");
            return modelName != null && !String.valueOf(modelName).isEmpty();
        }
        return false;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public CompletableFuture<Void> initialize(Map<String, Object> kwargs) {
        Map<String, Object> initKwargs = kwargs == null ? Map.of() : kwargs;
        userId = stringOrDefault(initKwargs.get("user_id"), userId);
        scopeId = stringOrDefault(initKwargs.get("scope_id"), scopeId);
        sessionId = stringOrDefault(initKwargs.get("session_id"), sessionId);

        return CompletableFuture.runAsync(() -> {
            if (kvStore == null) {
                kvStore = createKvStore();
            }
            if (vectorStore == null) {
                vectorStore = createVectorStore();
            }
            if (dbStore == null) {
                dbStore = createDbStore();
            }
            if (embeddingModel == null) {
                embeddingModel = createEmbedding();
            }

            if (kvStore == null || vectorStore == null || dbStore == null) {
                Loggers.MEMORY.error("[OpenJiuwenMemoryProvider] Store creation failed");
                initialized = false;
                return;
            }

            ltm = ltmSupplier.get();
            if (ltm.getKvStore() == null) {
                ltm.registerStore(kvStore, vectorStore, dbStore, embeddingModel);
            }
            if (scopeConfig != null) {
                ltm.setScopeConfig(scopeId, scopeConfig);
            }
            initialized = true;
        });
    }

    @Override
    public String systemPromptBlock() {
        return "# Long-Term Memory System\n\n"
                + "You have long-term memory capabilities and can remember user information across sessions.\n\n"
                + "## Memory Search\n\n"
                + "When you need to recall previous information, use the `ltm_search` tool to search long-term memory.";
    }

    @Override
    public List<Map<String, Object>> getToolSchemas() {
        return List.of(LTM_SEARCH_SCHEMA, LTM_SEARCH_SUMMARY_SCHEMA);
    }

    @Override
    public CompletableFuture<String> handleToolCall(String toolName, Map<String, Object> args) {
        if (ltm == null || !initialized) {
            return CompletableFuture.completedFuture(toJson(Map.of("error", "Memory provider not initialized")));
        }
        Map<String, Object> safeArgs = args == null ? Map.of() : args;
        return CompletableFuture.supplyAsync(() -> {
            try {
                if ("ltm_search".equals(toolName)) {
                    return handleSearch(safeArgs);
                }
                if ("ltm_search_summary".equals(toolName)) {
                    return handleSearchSummary(safeArgs);
                }
                return toJson(Map.of("error", "Unknown tool: " + toolName));
            } catch (Exception e) {
                return toJson(Map.of("error", e.getMessage(), "results", List.of()));
            }
        });
    }

    @Override
    public CompletableFuture<String> prefetch(String query, Map<String, Object> kwargs) {
        if (ltm == null || !initialized) {
            return CompletableFuture.completedFuture("");
        }
        Map<String, Object> safeKwargs = kwargs == null ? Map.of() : kwargs;
        return CompletableFuture.supplyAsync(() -> {
            String effectiveUserId = stringOrDefault(safeKwargs.get("user_id"), userId);
            String effectiveScopeId = stringOrDefault(safeKwargs.get("scope_id"), scopeId);
            List<String> parts = new ArrayList<>();
            try {
                List<MemResult> memResults = ltm.searchUserMem(
                        query, DEFAULT_RECALL_USER_MEM_NUM, effectiveUserId, effectiveScopeId, 0.3);
                if (memResults != null && !memResults.isEmpty()) {
                    parts.add("## Related Memories");
                    for (MemResult result : memResults) {
                        parts.add(String.format("- [%s] %s (score: %.2f)",
                                memoryTypeValue(result), memoryContent(result), result.getScore()));
                    }
                }
            } catch (Exception e) {
                Loggers.MEMORY.warn("prefetch search_user_mem failed: {}", e.getMessage());
            }
            try {
                List<MemResult> summaryResults = ltm.searchUserHistorySummary(
                        query, DEFAULT_RECALL_HISTORY_MEM_NUM, effectiveUserId, effectiveScopeId, 0.3);
                if (summaryResults != null && !summaryResults.isEmpty()) {
                    parts.add("\n## Related History Summaries");
                    for (MemResult result : summaryResults) {
                        parts.add(String.format("- %s (score: %.2f)", memoryContent(result), result.getScore()));
                    }
                }
            } catch (Exception e) {
                Loggers.MEMORY.warn("prefetch search_user_history_summary failed: {}", e.getMessage());
            }
            return parts.isEmpty() ? "" : String.join("\n", parts);
        });
    }

    @Override
    public CompletableFuture<Void> syncTurn(String userMsg, String assistantMsg, Map<String, Object> kwargs) {
        if (ltm == null || !initialized) {
            return CompletableFuture.completedFuture(null);
        }
        Map<String, Object> safeKwargs = kwargs == null ? Map.of() : kwargs;
        return CompletableFuture.runAsync(() -> {
            String effectiveUserId = stringOrDefault(safeKwargs.get("user_id"), userId);
            String effectiveScopeId = stringOrDefault(safeKwargs.get("scope_id"), scopeId);
            String effectiveSessionId = stringOrDefault(safeKwargs.get("session_id"), sessionId);
            List<BaseMessage> messages = new ArrayList<>();
            if (userMsg != null && !userMsg.isEmpty()) {
                messages.add(new UserMessage(userMsg));
            }
            if (assistantMsg != null && !assistantMsg.isEmpty()) {
                messages.add(new AssistantMessage(assistantMsg));
            }
            if (messages.isEmpty()) {
                return;
            }
            try {
                ltm.addMessages(messages, agentMemoryConfig, effectiveUserId, effectiveScopeId, effectiveSessionId);
            } catch (Exception e) {
                Loggers.MEMORY.warn("sync_turn add_messages failed: {}", e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        initialized = false;
        return CompletableFuture.completedFuture(null);
    }

    protected BaseKVStore createKvStore() {
        return new InMemoryKVStore();
    }

    protected VectorStore createVectorStore() {
        return new InMemoryVectorStore(new VectorStoreConfig("chroma", "openjiuwen_memory"), "hybrid");
    }

    protected BaseDbStore<?> createDbStore() {
        return new DefaultDbStore("jdbc:h2:mem:openjiuwen_memory;DB_CLOSE_DELAY=-1");
    }

    protected Embedding createEmbedding() {
        return new HashEmbedding();
    }

    private String handleSearch(Map<String, Object> args) {
        List<MemResult> results = ltm.searchUserMem(
                stringOrDefault(args.get("query"), ""),
                intValue(args, "num", DEFAULT_RECALL_USER_MEM_NUM),
                userId,
                scopeId,
                doubleValue(args, "threshold", 0.3)
        );
        List<Map<String, Object>> payload = new ArrayList<>();
        for (MemResult result : nullToEmpty(results)) {
            payload.add(Map.of(
                    "id", result.getMemInfo() == null ? "" : result.getMemInfo().getMemId(),
                    "content", memoryContent(result),
                    "type", memoryTypeValue(result),
                    "score", result.getScore()
            ));
        }
        return toJson(Map.of("results", payload, "count", payload.size()));
    }

    private String handleSearchSummary(Map<String, Object> args) {
        List<MemResult> results = ltm.searchUserHistorySummary(
                stringOrDefault(args.get("query"), ""),
                intValue(args, "num", DEFAULT_RECALL_HISTORY_MEM_NUM),
                userId,
                scopeId,
                0.3
        );
        List<Map<String, Object>> payload = new ArrayList<>();
        for (MemResult result : nullToEmpty(results)) {
            payload.add(Map.of(
                    "id", result.getMemInfo() == null ? "" : result.getMemInfo().getMemId(),
                    "content", memoryContent(result),
                    "score", result.getScore()
            ));
        }
        return toJson(Map.of("results", payload, "count", payload.size()));
    }

    private static MemoryScopeConfig parseScopeConfig(Map<String, Object> config) {
        Object scopeCfg = config.get("scope_config");
        if (!(scopeCfg instanceof Map<?, ?> scopeMap) || scopeMap.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.convertValue(scopeMap, MemoryScopeConfig.class);
        } catch (IllegalArgumentException e) {
            Loggers.MEMORY.warn("[OpenJiuwenMemoryProvider] Failed to parse scope_config: {}", e.getMessage());
            return null;
        }
    }

    private static List<MemResult> nullToEmpty(List<MemResult> results) {
        return results == null ? List.of() : results;
    }

    private static String memoryContent(MemResult result) {
        if (result == null || result.getMemInfo() == null || result.getMemInfo().getContent() == null) {
            return "";
        }
        return result.getMemInfo().getContent();
    }

    private static String memoryTypeValue(MemResult result) {
        if (result == null || result.getMemInfo() == null || result.getMemInfo().getType() == null) {
            return "unknown";
        }
        return result.getMemInfo().getType().getValue();
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String stringOrDefault(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue == null ? "" : defaultValue;
        }
        return String.valueOf(value);
    }

    private static int intValue(Map<String, Object> values, String key, int defaultValue) {
        if (values == null || !values.containsKey(key)) {
            return defaultValue;
        }
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static double doubleValue(Map<String, Object> values, String key, double defaultValue) {
        if (values == null || !values.containsKey(key)) {
            return defaultValue;
        }
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }
}
