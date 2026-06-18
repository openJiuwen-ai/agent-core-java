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
import com.openjiuwen.core.foundation.store.BaseDbStore;
import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.foundation.store.BaseVectorStore;
import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import com.openjiuwen.core.foundation.store.FoundationStorePackage;
import com.openjiuwen.core.foundation.store.db.DefaultDbStore;
import com.openjiuwen.core.foundation.store.kv.DbBasedKVStore;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.MemInfo;
import com.openjiuwen.core.memory.MemResult;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.memory.config.MemoryEngineConfig;
import com.openjiuwen.core.memory.config.MemoryScopeConfig;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.retrieval.embedding.APIEmbedding;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

/**
 * OpenJiuwen memory provider backed by the translated long-term memory engine.
 *
 * <p>Mirrors Python's {@code OpenJiuwenMemoryProvider} in
 * {@code openjiuwen/core/memory/external/openjiuwen_memory_provider.py}.</p>
 */
public class OpenJiuwenMemoryProvider extends MemoryProvider {

    static final int DEFAULT_RECALL_USER_MEM_NUM = 5;
    static final int DEFAULT_RECALL_HISTORY_MEM_NUM = 3;
    static final String DEFAULT_KV_BACKEND = "memory";
    static final String DEFAULT_VECTOR_BACKEND = "chroma";
    static final String DEFAULT_DB_BACKEND = "sqlite";
    static final Map<String, Object> LTM_SEARCH_SCHEMA = createLtmSearchSchema();
    static final Map<String, Object> LTM_SEARCH_SUMMARY_SCHEMA = createLtmSearchSummarySchema();

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final String DEFAULT_ID = "__default__";

    private final Map<String, Object> config;
    private BaseKVStore kvStore;
    private BaseVectorStore vectorStore;
    private BaseDbStore<?> dbStore;
    private Embedding embeddingModel;
    private final MemoryEngineConfig engineConfig;
    private MemoryScopeConfig scopeConfig;
    private final AgentMemoryConfig agentMemoryConfig;
    private LongTermMemory longTermMemory;
    private boolean initialized;
    private String userId = DEFAULT_ID;
    private String scopeId = DEFAULT_ID;
    private String sessionId = DEFAULT_ID;

    public OpenJiuwenMemoryProvider() {
        this(Map.of());
    }

    public OpenJiuwenMemoryProvider(Map<String, Object> config) {
        this(config, null, null, null, null, null, null, null, null);
    }

    OpenJiuwenMemoryProvider(
            Map<String, Object> config,
            BaseKVStore kvStore,
            BaseVectorStore vectorStore,
            BaseDbStore<?> dbStore,
            Embedding embeddingModel,
            MemoryEngineConfig engineConfig,
            MemoryScopeConfig scopeConfig,
            AgentMemoryConfig agentMemoryConfig,
            LongTermMemory longTermMemory) {
        this.config = config == null ? Map.of() : new LinkedHashMap<>(config);
        this.kvStore = kvStore;
        this.vectorStore = vectorStore;
        this.dbStore = dbStore;
        this.embeddingModel = embeddingModel;
        this.engineConfig = engineConfig;
        this.scopeConfig = scopeConfig == null ? parseScopeConfig() : scopeConfig;
        this.agentMemoryConfig = agentMemoryConfig == null ? new AgentMemoryConfig() : agentMemoryConfig;
        this.longTermMemory = longTermMemory;
    }

    @Override
    public String getName() {
        return "openjiuwen";
    }

    @Override
    public boolean isAvailable() {
        if (kvStore != null && vectorStore != null && dbStore != null) {
            return true;
        }
        Map<String, Object> embeddingConfig = mapConfig("embedding");
        return !stringOrDefault(firstValue(embeddingConfig, "model_name", "modelName"), "").isEmpty();
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public CompletableFuture<Void> initialize(Map<String, Object> kwargs) {
        Map<String, Object> safeKwargs = kwargs == null ? Map.of() : kwargs;
        return CompletableFuture.runAsync(() -> {
            userId = stringOrDefault(safeKwargs.get("user_id"), userId);
            scopeId = stringOrDefault(safeKwargs.get("scope_id"), scopeId);
            sessionId = stringOrDefault(safeKwargs.get("session_id"), sessionId);

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
                return;
            }

            if (longTermMemory == null) {
                longTermMemory = newLongTermMemory();
            }
            if (longTermMemory.getKvStore() == null) {
                longTermMemory.registerStore(kvStore, vectorStore, dbStore, embeddingModel).join();
            }
            if (scopeConfig != null) {
                longTermMemory.setScopeConfig(scopeId, scopeConfig).join();
            }
            initialized = true;
        });
    }

    @Override
    public String systemPromptBlock() {
        return "# Long-Term Memory System\n\n"
                + "You have long-term memory capabilities and can remember user information across sessions. "
                + "The system automatically extracts valuable information from conversations and stores it in memory.\n\n"
                + "## Memory Search\n\n"
                + "When you need to recall previous information, use the `ltm_search` tool to search long-term memory.\n"
                + "- Search queries should contain key information (names, dates, event keywords)\n"
                + "- If results are insufficient, try searching again with different keywords\n\n"
                + "## Automatic Memory\n\n"
                + "The system automatically extracts from each conversation:\n"
                + "- User profile (identity, preferences, habits)\n"
                + "- Episodic memory (specific events, decisions)\n"
                + "- Semantic memory (background knowledge, technical details)\n"
                + "- Conversation summaries (key conclusions, main points)";
    }

    @Override
    public List<Map<String, Object>> getToolSchemas() {
        return List.of(LTM_SEARCH_SCHEMA, LTM_SEARCH_SUMMARY_SCHEMA);
    }

    @Override
    public CompletableFuture<String> handleToolCall(String toolName, Map<String, Object> args) {
        if (longTermMemory == null || !initialized) {
            return CompletableFuture.completedFuture(toJson(Map.of("error", "Memory provider not initialized")));
        }
        Map<String, Object> safeArgs = args == null ? Map.of() : args;
        return CompletableFuture.supplyAsync(() -> {
            try {
                return switch (toolName) {
                    case "ltm_search" -> handleSearch(safeArgs);
                    case "ltm_search_summary" -> handleSearchSummary(safeArgs);
                    default -> toJson(Map.of("error", "Unknown tool: " + toolName));
                };
            } catch (Exception exception) {
                return toJson(Map.of(
                        "error", errorMessage(exception),
                        "results", List.of()));
            }
        });
    }

    @Override
    public CompletableFuture<String> prefetch(String query, Map<String, Object> kwargs) {
        if (longTermMemory == null || !initialized) {
            return CompletableFuture.completedFuture("");
        }
        Map<String, Object> safeKwargs = kwargs == null ? Map.of() : kwargs;
        return CompletableFuture.supplyAsync(() -> {
            String runtimeUserId = stringOrDefault(safeKwargs.get("user_id"), userId);
            String runtimeScopeId = stringOrDefault(safeKwargs.get("scope_id"), scopeId);
            List<String> parts = new ArrayList<>();
            try {
                List<MemResult> memResults = longTermMemory.searchUserMem(
                        query, DEFAULT_RECALL_USER_MEM_NUM, runtimeUserId, runtimeScopeId, 0.3d).join();
                if (memResults != null && !memResults.isEmpty()) {
                    parts.add("## Related Memories");
                    for (MemResult result : memResults) {
                        MemInfo info = result.getMemInfo();
                        parts.add("- [" + typeValue(info) + "] " + contentValue(info)
                                + " (score: " + formatScore(result.getScore()) + ")");
                    }
                }
            } catch (Exception exception) {
                Loggers.MEMORY.warning("prefetch search_user_mem failed: {}", errorMessage(exception));
            }
            try {
                List<MemResult> summaryResults = longTermMemory.searchUserHistorySummary(
                        query, DEFAULT_RECALL_HISTORY_MEM_NUM, runtimeUserId, runtimeScopeId, 0.3d).join();
                if (summaryResults != null && !summaryResults.isEmpty()) {
                    parts.add("\n## Related History Summaries");
                    for (MemResult result : summaryResults) {
                        parts.add("- " + contentValue(result.getMemInfo())
                                + " (score: " + formatScore(result.getScore()) + ")");
                    }
                }
            } catch (Exception exception) {
                Loggers.MEMORY.warning("prefetch search_user_history_summary failed: {}", errorMessage(exception));
            }
            return parts.isEmpty() ? "" : String.join("\n", parts);
        });
    }

    @Override
    public CompletableFuture<Void> syncTurn(String userMsg, String assistantMsg, Map<String, Object> kwargs) {
        if (longTermMemory == null || !initialized) {
            return CompletableFuture.completedFuture(null);
        }
        Map<String, Object> safeKwargs = kwargs == null ? Map.of() : kwargs;
        return CompletableFuture.runAsync(() -> {
            String runtimeUserId = stringOrDefault(safeKwargs.get("user_id"), userId);
            String runtimeScopeId = stringOrDefault(safeKwargs.get("scope_id"), scopeId);
            String runtimeSessionId = stringOrDefault(safeKwargs.get("session_id"), sessionId);
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
                longTermMemory.addMessages(
                        messages, agentMemoryConfig, runtimeUserId, runtimeScopeId, runtimeSessionId).join();
            } catch (Exception exception) {
                Loggers.MEMORY.warning("sync_turn add_messages failed: {}", errorMessage(exception));
            }
        });
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        initialized = false;
        return CompletableFuture.completedFuture(null);
    }

    LongTermMemory rawLongTermMemoryForTest() {
        return longTermMemory;
    }

    String userIdValue() {
        return userId;
    }

    String scopeIdValue() {
        return scopeId;
    }

    String sessionIdValue() {
        return sessionId;
    }

    protected LongTermMemory newLongTermMemory() {
        return new LongTermMemory();
    }

    protected BaseKVStore createKvStore() {
        Map<String, Object> kvConfig = mapConfig("kv");
        String backend = stringOrDefault(kvConfig.get("backend"), DEFAULT_KV_BACKEND);
        try {
            if (DEFAULT_KV_BACKEND.equals(backend)) {
                return new InMemoryKVStore();
            }
            if (DEFAULT_DB_BACKEND.equals(backend)) {
                String path = stringOrDefault(kvConfig.get("path"), "memory_kv.db");
                return new DbBasedKVStore(new DriverManagerDataSource(toJdbcSqliteUrl(path)));
            }
        } catch (Exception exception) {
            Loggers.MEMORY.error("[OpenJiuwenMemoryProvider] KV store creation failed ({}): {}",
                    backend, exception.getMessage());
        }
        return null;
    }

    protected BaseVectorStore createVectorStore() {
        Map<String, Object> vectorConfig = mapConfig("vector");
        String backend = stringOrDefault(vectorConfig.get("backend"), DEFAULT_VECTOR_BACKEND);
        try {
            Map<String, Object> kwargs = new LinkedHashMap<>(vectorConfig);
            kwargs.remove("backend");
            return FoundationStorePackage.createVectorStore(backend, kwargs);
        } catch (Exception exception) {
            Loggers.MEMORY.error("[OpenJiuwenMemoryProvider] Vector store creation failed ({}): {}",
                    backend, exception.getMessage());
            return null;
        }
    }

    protected BaseDbStore<?> createDbStore() {
        Map<String, Object> dbConfig = mapConfig("db");
        String backend = stringOrDefault(dbConfig.get("backend"), DEFAULT_DB_BACKEND);
        try {
            if (DEFAULT_DB_BACKEND.equals(backend)) {
                String path = stringOrDefault(dbConfig.get("path"), "memory.db");
                return new DefaultDbStore<>(new DriverManagerDataSource(toJdbcSqliteUrl(path)));
            }
        } catch (Exception exception) {
            Loggers.MEMORY.error("[OpenJiuwenMemoryProvider] DB store creation failed ({}): {}",
                    backend, exception.getMessage());
        }
        return null;
    }

    protected Embedding createEmbedding() {
        Map<String, Object> embeddingConfig = mapConfig("embedding");
        String modelName = stringOrDefault(firstValue(embeddingConfig, "model_name", "modelName"), "");
        if (modelName.isEmpty()) {
            return null;
        }
        try {
            EmbeddingConfig config = EmbeddingConfig.builder()
                    .modelName(modelName)
                    .baseUrl(stringOrDefault(firstValue(embeddingConfig, "base_url", "baseUrl"), ""))
                    .apiKey(stringOrDefault(firstValue(embeddingConfig, "api_key", "apiKey"), null))
                    .build();
            return new APIEmbedding(config);
        } catch (Exception exception) {
            Loggers.MEMORY.error("[OpenJiuwenMemoryProvider] Embedding creation failed: {}", exception.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapConfig(String key) {
        Object value = config.get(key);
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            rawMap.forEach((mapKey, mapValue) -> result.put(String.valueOf(mapKey), mapValue));
            return result;
        }
        return Map.of();
    }

    private MemoryScopeConfig parseScopeConfig() {
        Object rawScopeConfig = config.get("scope_config");
        if (!(rawScopeConfig instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return null;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> values.put(String.valueOf(key), value));
        try {
            return MAPPER.convertValue(values, MemoryScopeConfig.class);
        } catch (IllegalArgumentException exception) {
            Loggers.MEMORY.warning(
                    "[OpenJiuwenMemoryProvider] Failed to parse scope_config: {}", exception.getMessage());
            return null;
        }
    }

    private String handleSearch(Map<String, Object> args) {
        List<MemResult> results = longTermMemory.searchUserMem(
                stringOrDefault(args.get("query"), ""),
                intValue(args.get("num"), DEFAULT_RECALL_USER_MEM_NUM),
                userId,
                scopeId,
                doubleValue(args.get("threshold"), 0.3d)
        ).join();
        List<Map<String, Object>> payload = new ArrayList<>();
        for (MemResult result : results == null ? List.<MemResult>of() : results) {
            MemInfo info = result.getMemInfo();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", info == null ? "" : info.getMemId());
            item.put("content", contentValue(info));
            item.put("type", typeValue(info));
            item.put("score", result.getScore());
            payload.add(item);
        }
        return toJson(Map.of("results", payload, "count", payload.size()));
    }

    private String handleSearchSummary(Map<String, Object> args) {
        List<MemResult> results = longTermMemory.searchUserHistorySummary(
                stringOrDefault(args.get("query"), ""),
                intValue(args.get("num"), DEFAULT_RECALL_HISTORY_MEM_NUM),
                userId,
                scopeId,
                0.3d
        ).join();
        List<Map<String, Object>> payload = new ArrayList<>();
        for (MemResult result : results == null ? List.<MemResult>of() : results) {
            MemInfo info = result.getMemInfo();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", info == null ? "" : info.getMemId());
            item.put("content", contentValue(info));
            item.put("score", result.getScore());
            payload.add(item);
        }
        return toJson(Map.of("results", payload, "count", payload.size()));
    }

    private static String typeValue(MemInfo info) {
        MemoryType type = info == null ? null : info.getType();
        return type == null ? "unknown" : type.getValue();
    }

    private static String contentValue(MemInfo info) {
        return info == null ? "" : stringOrDefault(info.getContent(), "");
    }

    private static String formatScore(double score) {
        return String.format(Locale.ROOT, "%.2f", score);
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static int intValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static double doubleValue(Object value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static Object firstValue(Map<String, Object> values, String firstKey, String secondKey) {
        Object first = values.get(firstKey);
        return first == null ? values.get(secondKey) : first;
    }

    private static String stringOrDefault(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue == null ? "" : defaultValue;
        }
        return String.valueOf(value);
    }

    private static String errorMessage(Throwable throwable) {
        Throwable actual = throwable;
        if (actual instanceof CompletionException && actual.getCause() != null) {
            actual = actual.getCause();
        }
        String message = actual.getMessage();
        return message == null || message.isEmpty() ? actual.toString() : message;
    }

    private static String toJdbcSqliteUrl(String path) {
        String actualPath = stringOrDefault(path, "");
        if (actualPath.startsWith("jdbc:")) {
            return actualPath;
        }
        return "jdbc:sqlite:" + actualPath;
    }

    private static Map<String, Object> createLtmSearchSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", Map.of(
                "type", "string",
                "description", "\u641c\u7d22\u67e5\u8be2\u5185\u5bb9"));
        properties.put("num", Map.of(
                "type", "integer",
                "description", "\u6700\u5927\u8fd4\u56de\u7ed3\u679c\u6570\u91cf",
                "default", DEFAULT_RECALL_USER_MEM_NUM));
        properties.put("threshold", Map.of(
                "type", "number",
                "description", "\u6700\u5c0f\u76f8\u5173\u6027\u9608\u503c (0-1)",
                "default", 0.3d));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("query"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("name", "ltm_search");
        schema.put("description", "\u5728\u957f\u671f\u8bb0\u5fc6\u4e2d\u641c\u7d22\u76f8\u5173"
                + "\u4fe1\u606f\u3002\u641c\u7d22\u8303\u56f4\u5305\u62ec\u7528\u6237\u7528"
                + "\u6237\u753b\u50cf\u3001\u60c5\u666f\u8bb0\u5fc6\u548c\u8bed\u4e49"
                + "\u8bb0\u5fc6\u3002");
        schema.put("parameters", parameters);
        return schema;
    }

    private static Map<String, Object> createLtmSearchSummarySchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", Map.of(
                "type", "string",
                "description", "\u641c\u7d22\u67e5\u8be2\u5185\u5bb9"));
        properties.put("num", Map.of(
                "type", "integer",
                "description", "\u6700\u5927\u8fd4\u56de\u7ed3\u679c\u6570\u91cf",
                "default", DEFAULT_RECALL_HISTORY_MEM_NUM));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("query"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("name", "ltm_search_summary");
        schema.put("description", "\u5728\u957f\u671f\u8bb0\u5fc6\u4e2d\u641c\u7d22\u5386\u53f2"
                + "\u4f1a\u8bdd\u6458\u8981\u3002\u7528\u4e8e\u56de\u5fc6\u4e4b\u524d"
                + "\u8ba8\u8bba\u7684\u8bdd\u9898\u548c\u8fbe\u6210\u7684\u7ed3\u8bba"
                + "\u3002");
        schema.put("parameters", parameters);
        return schema;
    }

    /**
     * Mirrors Python's sqlite backend creation in
     * {@code openjiuwen/core/memory/external/openjiuwen_memory_provider.py}.
     */
    private static final class DriverManagerDataSource implements DataSource {
        private final String jdbcUrl;
        private PrintWriter logWriter;
        private int loginTimeout;

        private DriverManagerDataSource(String jdbcUrl) {
            this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(jdbcUrl);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(jdbcUrl, username, password);
        }

        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            logWriter = out;
        }

        @Override
        public void setLoginTimeout(int seconds) {
            loginTimeout = seconds;
        }

        @Override
        public int getLoginTimeout() {
            return loginTimeout;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException("No parent logger");
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("Not a wrapper for " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
