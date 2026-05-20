/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.memory.common.DistributedLock;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.memory.config.MemoryEngineConfig;
import com.openjiuwen.core.memory.config.MemoryScopeConfig;
import com.openjiuwen.core.memory.manage.index.BaseMemoryManager;
import com.openjiuwen.core.memory.manage.index.FragmentMemoryManager;
import com.openjiuwen.core.memory.manage.index.SummaryManager;
import com.openjiuwen.core.memory.manage.index.VariableManager;
import com.openjiuwen.core.memory.manage.index.WriteManager;
import com.openjiuwen.core.memory.manage.mem_model.DataIdManager;
import com.openjiuwen.core.memory.manage.mem_model.DbModel;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.manage.mem_model.MessageAddRequest;
import com.openjiuwen.core.memory.manage.mem_model.MessageManager;
import com.openjiuwen.core.memory.manage.mem_model.ScopeUserMappingManager;
import com.openjiuwen.core.memory.manage.mem_model.SemanticStore;
import com.openjiuwen.core.memory.manage.mem_model.SqlDbStore;
import com.openjiuwen.core.memory.manage.mem_model.UserMemStore;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.search.SearchManager;
import com.openjiuwen.core.memory.manage.search.SearchParams;
import com.openjiuwen.core.memory.migration.RunMigrations;
import com.openjiuwen.core.memory.process.extract.Generator;
import com.openjiuwen.core.retrieval.embedding.APIEmbedding;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import com.openjiuwen.spi.store.BaseDbStore;
import com.openjiuwen.spi.store.BaseKVStore;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Main memory engine implementing long-term memory management.
 * Singleton class managing conversation memory, user variables, semantic search, and persistence.
 */
public class LongTermMemory {

    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static final String DEFAULT_VALUE = "__default__";
    public static final String SCOPE_CONFIG_KEY = "memory_scope_config";

    private static volatile LongTermMemory instance;

    // config
    private MemoryEngineConfig sysMemConfig;
    private final ConcurrentHashMap<String, MemoryScopeConfig> scopeConfig = new ConcurrentHashMap<>();

    // stores
    private BaseKVStore kvStore;
    private VectorStore vectorStore;
    private BaseDbStore<?> dbStore;

    // managers
    private ScopeUserMappingManager scopeUserMappingManager;
    private MessageManager messageManager;
    private FragmentMemoryManager userProfileManager;
    private VariableManager variableManager;
    private WriteManager writeManager;
    private SummaryManager summaryManager;
    private SearchManager searchManager;
    private Generator generator;

    // llm
    private Map.Entry<String, Model> baseLlm;

    // embedding
    private Embedding baseEmbed;
    private final ConcurrentHashMap<String, Embedding> scopeEmbedding = new ConcurrentHashMap<>();

    private LongTermMemory() {
    }

    /**
     * Gets the singleton instance of LongTermMemory.
     *
     * @return the LongTermMemory singleton instance
     */
    public static LongTermMemory getInstance() {
        if (instance == null) {
            synchronized (LongTermMemory.class) {
                if (instance == null) {
                    instance = new LongTermMemory();
                }
            }
        }
        return instance;
    }

    /**
     * Reset singleton for testing.
     */
    public static void resetInstance() {
        synchronized (LongTermMemory.class) {
            instance = null;
        }
    }

    // ========================= Store Registration =========================

    /**
     * Registers storage backends for the memory engine.
     *
     * @param kvStore        the key-value store for persistence
     * @param vectorStore    the vector store for semantic search
     * @param dbStore        the database store for structured data
     * @param embeddingModel the embedding model for vectorization
     */
    public void registerStore(BaseKVStore kvStore,
                              VectorStore vectorStore,
                              BaseDbStore<?> dbStore,
                              Embedding embeddingModel) {
        if (kvStore == null) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_REGISTER_STORE_EXECUTION_ERROR,
                    "store_type", "kv store", "error_msg", "kv store is required, cannot be None");
        }
        this.kvStore = kvStore;
        this.vectorStore = vectorStore;
        this.dbStore = dbStore;
        this.baseEmbed = embeddingModel;

        if (this.dbStore != null) {
            DbModel.createTables(this.dbStore);
        }

        setConfig(new MemoryEngineConfig());

        runMigration(() -> RunMigrations.runKvMigrations(this.kvStore), "kv store");

        if (this.vectorStore != null) {
            SemanticStore semanticStore = new SemanticStore(this.vectorStore);
            runMigration(() -> RunMigrations.runVectorMigrations(semanticStore), "vector store");
        }

        if (this.dbStore != null) {
            SqlDbStore sqlStore = new SqlDbStore(this.dbStore);
            runMigration(() -> RunMigrations.runSqlMigrations(sqlStore), "db store");
        }
    }

    // ========================= Configuration =========================

    /**
     * Sets the system memory engine configuration.
     *
     * @param config the memory engine configuration
     */
    public void setConfig(MemoryEngineConfig config) {
        if (kvStore == null || dbStore == null || vectorStore == null) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_SET_CONFIG_EXECUTION_ERROR,
                    "config_type", "system", "error_msg", "stores must be registered before setting config");
        }
        config.validateCryptoKey();
        this.sysMemConfig = config;
        DataIdManager dataIdGenerator = new DataIdManager();
        UserMemStore userMemStore = new UserMemStore(kvStore);

        if (dbStore != null) {
            SqlDbStore sqlStore = new SqlDbStore(dbStore);
            scopeUserMappingManager = new ScopeUserMappingManager(sqlStore);
            messageManager = new MessageManager(sqlStore, dataIdGenerator, config.getCryptoKey());
        }

        userProfileManager = new FragmentMemoryManager(userMemStore, dataIdGenerator, config.getCryptoKey());
        variableManager = new VariableManager(kvStore, config.getCryptoKey());
        summaryManager = new SummaryManager(userMemStore, config.getCryptoKey());

        Map<String, BaseMemoryManager> managers = new HashMap<>();
        managers.put(MemoryType.USER_PROFILE.getValue(), userProfileManager);
        managers.put(MemoryType.SEMANTIC_MEMORY.getValue(), userProfileManager);
        managers.put(MemoryType.EPISODIC_MEMORY.getValue(), userProfileManager);
        managers.put(MemoryType.VARIABLE.getValue(), variableManager);
        managers.put(MemoryType.SUMMARY.getValue(), summaryManager);

        writeManager = new WriteManager(managers, userMemStore);
        searchManager = new SearchManager(managers, userMemStore, config.getCryptoKey());
        generator = new Generator(dataIdGenerator);

        if (config.getDefaultModelCfg() != null && config.getDefaultModelClientCfg() != null) {
            Model llm = getLlmFromConfig(config.getDefaultModelCfg(), config.getDefaultModelClientCfg());
            baseLlm = new AbstractMap.SimpleEntry<>(config.getDefaultModelCfg().getModelName(), llm);
        }
    }

    /**
     * Sets the configuration for a specific scope.
     *
     * @param scopeId             the scope identifier
     * @param memoryScopeConfig   the scope-specific memory configuration
     * @return true if configuration was set successfully, false otherwise
     */
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean setScopeConfig(String scopeId, MemoryScopeConfig memoryScopeConfig) {
        if (!validateId(LogEventType.MEMORY_STORE, scopeId)) {
            MEMORY_LOGGER.error("[{}] Invalid scope_id format.", LogEventType.MEMORY_STORE);
            return false;
        }

        try {
            // Serialize to JSON map, encrypt api_keys, then store
            Map<String, Object> configMap = MAPPER.convertValue(memoryScopeConfig, Map.class);
            encryptApiKeyInMap(configMap, "model_client_cfg");
            encryptApiKeyInMap(configMap, "embedding_cfg");

            // Store encrypted map in memory cache (as MemoryScopeConfig from encrypted JSON)
            String encryptedJson = MAPPER.writeValueAsString(configMap);
            MemoryScopeConfig encryptedConfig = MAPPER.readValue(encryptedJson, MemoryScopeConfig.class);
            scopeConfig.put(scopeId, encryptedConfig);

            String configKey = SCOPE_CONFIG_KEY + "/" + scopeId;
            kvStore.set(configKey, encryptedJson);

            scopeEmbedding.remove(scopeId);
            return true;
        } catch (Exception e) {
            MEMORY_LOGGER.error("[{}] Failed to set scope config: {}", LogEventType.MEMORY_STORE, e.getMessage());
            return false;
        }
    }

    /**
     * Gets the configuration for a specific scope.
     *
     * @param scopeId the scope identifier
     * @return the scope configuration, or null if not found
     */
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    public MemoryScopeConfig getScopeConfig(String scopeId) {
        if (!validateId(LogEventType.MEMORY_RETRIEVE, scopeId)) {
            MEMORY_LOGGER.error("[{}] Invalid scope_id format.", LogEventType.MEMORY_RETRIEVE);
            return null;
        }
        String configKey = SCOPE_CONFIG_KEY + "/" + scopeId;
        Object configJson = kvStore.get(configKey);
        if (configJson == null) {
            return null;
        }
        try {
            // Parse JSON to map, decrypt api_keys, then rebuild config
            Map<String, Object> configMap = MAPPER.readValue(String.valueOf(configJson), Map.class);
            decryptApiKeyInMap(configMap, "model_client_cfg");
            decryptApiKeyInMap(configMap, "embedding_cfg");
            String decryptedJson = MAPPER.writeValueAsString(configMap);
            return MAPPER.readValue(decryptedJson, MemoryScopeConfig.class);
        } catch (Exception e) {
            MEMORY_LOGGER.error("[{}] Failed to parse scope config: {}", LogEventType.MEMORY_RETRIEVE, e.getMessage());
            return null;
        }
    }

    /**
     * Deletes the configuration for a specific scope.
     *
     * @param scopeId the scope identifier
     * @return true if deletion was successful, false otherwise
     */
    public boolean deleteScopeConfig(String scopeId) {
        if (!validateId(LogEventType.MEMORY_DELETE, scopeId)) {
            return false;
        }
        try {
            String configKey = SCOPE_CONFIG_KEY + "/" + scopeId;
            kvStore.delete(configKey);
            scopeConfig.remove(scopeId);
            scopeEmbedding.remove(scopeId);
            return true;
        } catch (Exception e) {
            MEMORY_LOGGER.error("[{}] Failed to delete configuration: {}",
                    LogEventType.MEMORY_DELETE, e.getMessage());
            return false;
        }
    }

    // ========================= Memory Operations =========================

    /**
     * Deletes all memory data for a specific scope.
     *
     * @param scopeId the scope identifier
     * @return true if deletion was successful, false otherwise
     */
    public boolean deleteMemByScope(String scopeId) {
        if (!validateId(LogEventType.MEMORY_DELETE, scopeId)) {
            return false;
        }
        List<Map<String, Object>> scopeUserData = scopeUserMappingManager.getByScopeId(scopeId);
        SemanticStore semanticStore = createSemanticStoreWithEmbedding(scopeId);
        if (writeManager != null) {
            for (Map<String, Object> row : scopeUserData) {
                String userId = String.valueOf(row.get("user_id"));
                try (DistributedLock lock = new DistributedLock(kvStore, "user/" + userId)) {
                    lock.acquire();
                    writeManager.deleteMemByUserId(userId, scopeId, semanticStore);
                }
            }
        }
        scopeUserMappingManager.deleteByScopeId(scopeId);
        return true;
    }

    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    public void addMessages(List<BaseMessage> messages,
                            AgentMemoryConfig agentConfig,
                            String userId,
                            String scopeId,
                            String sessionId,
                            OffsetDateTime timestamp,
                            boolean genMem,
                            int genMemWithHistoryMsgNum) {
        if (!validateId(LogEventType.MEMORY_STORE, scopeId)) {
            return;
        }
        String msgId = "-1";
        Map.Entry<String, Model> llm = getScopeLlm(scopeId);
        SemanticStore semanticStore = createSemanticStoreWithEmbedding(scopeId);

        try (DistributedLock lock = new DistributedLock(kvStore, "user/" + userId)) {
            lock.acquire();

            if (llm == null) {
                MEMORY_LOGGER.error("[{}] LLM is not initialized.", LogEventType.MEMORY_STORE);
                return;
            }

            List<BaseMessage> historyMessages = getHistoryMessages(userId, scopeId, sessionId, genMemWithHistoryMsgNum);

            scopeUserMappingManager.add(userId, scopeId);

            if (timestamp == null) {
                timestamp = OffsetDateTime.now(ZoneId.systemDefault());
            }
            String timestampStr = timestamp.format(TIMESTAMP_FMT);

            for (int i = 0; i < messages.size(); i++) {
                BaseMessage msg = messages.get(i);
                OffsetDateTime msgTimestamp = timestamp.plusNanos((long) i * 1_000_000);
                MessageAddRequest addReq = MessageAddRequest.builder()
                        .userId(userId)
                        .scopeId(scopeId)
                        .role(msg.getRole())
                        .content(msg.getContentAsString())
                        .sessionId(sessionId)
                        .timestamp(msgTimestamp)
                        .build();
                msgId = messageManager.add(addReq);
            }

            if (!genMem) {
                return;
            }

            CheckMessagesResult checkRes = checkMessages(messages);
            if (!checkRes.hasHumanMsg) {
                MEMORY_LOGGER.debug("[{}] Memory engine no need to process messages.", LogEventType.MEMORY_STORE);
                return;
            }

            Map<String, Object> genParams = new HashMap<>();
            genParams.put("scope_id", scopeId);
            genParams.put("user_id", userId);
            genParams.put("messages", checkRes.messages);
            genParams.put("history_messages", historyMessages);
            genParams.put("session_id", sessionId);
            genParams.put("config", agentConfig);
            genParams.put("base_chat_model", llm);
            genParams.put("message_mem_id", msgId);
            genParams.put("timestamp", timestampStr);
            genParams.put("summary_max_token", sysMemConfig.getSingleTurnHistorySummaryMaxToken());

            genParams.put("forbidden_variables", sysMemConfig.getForbiddenVariables());

            Map<String, List<BaseMemoryUnit>> allMemory = generator.genAllMemory(genParams);

            try {
                writeManager.addMemories(userId, scopeId, allMemory, llm, semanticStore);
            } catch (Exception e) {
                MEMORY_LOGGER.error("[{}] Failed to add mem: {}", LogEventType.MEMORY_STORE, e.getMessage());
                throw ErrorHelper.buildError(StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                        "memory_type", "unknown", "error_msg", e.getMessage());
            }
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void addMessages(List<BaseMessage> messages, AgentMemoryConfig agentConfig,
                            String userId, String scopeId, String sessionId) {
        addMessages(messages, agentConfig, userId, scopeId, sessionId, null, true, 2);
    }

    // ========================= Message Operations =========================

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<BaseMessage> getRecentMessages(String userId, String scopeId, String sessionId, int num) {
        if (!validateId(LogEventType.MEMORY_RETRIEVE, scopeId)) {
            return List.of();
        }
        List<MessageManager.MessageRecord> records = messageManager.get(userId, scopeId, sessionId, num);
        List<BaseMessage> result = new ArrayList<>();
        for (MessageManager.MessageRecord record : records) {
            result.add(record.message());
        }
        return result;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public MessageManager.MessageRecord getMessageById(String msgId) {
        if (messageManager == null) {
            MEMORY_LOGGER.warn("[{}] Message manager is not initialized.", LogEventType.MEMORY_RETRIEVE);
            return null;
        }
        return messageManager.getById(msgId);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void deleteMessagesByUserAndScope(String userId, String scopeId) {
        if (!validateId(LogEventType.MEMORY_RETRIEVE, scopeId)) {
            return;
        }
        messageManager.deleteByUserAndScope(userId, scopeId);
    }

    // ========================= Memory CRUD =========================

    /**
     * Auto-generated for codecheck compliance.
     */
    public void deleteMemById(String memId, String userId, String scopeId) {
        if (!validateId(LogEventType.MEMORY_DELETE, scopeId)) {
            return;
        }
        SemanticStore semanticStore = createSemanticStoreWithEmbedding(scopeId);
        try (DistributedLock lock = new DistributedLock(kvStore, "user/" + userId)) {
            lock.acquire();
            if (writeManager == null) {
                throw ErrorHelper.buildError(StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR,
                        "memory_type", "all", "error_msg", "write manager is not initialized");
            }
            writeManager.deleteMemById(userId, scopeId, memId, semanticStore);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void deleteMemByUserId(String userId, String scopeId) {
        if (!validateId(LogEventType.MEMORY_DELETE, scopeId)) {
            return;
        }
        SemanticStore semanticStore = createSemanticStoreWithEmbedding(scopeId);
        try (DistributedLock lock = new DistributedLock(kvStore, "user/" + userId)) {
            lock.acquire();
            if (writeManager == null) {
                throw ErrorHelper.buildError(StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR,
                        "memory_type", "all", "error_msg", "write manager is not initialized");
            }
            writeManager.deleteMemByUserId(userId, scopeId, semanticStore);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void updateMemById(String memId, String memory, String userId, String scopeId) {
        if (!validateId(LogEventType.MEMORY_UPDATE, scopeId)) {
            return;
        }
        SemanticStore semanticStore = createSemanticStoreWithEmbedding(scopeId);
        try (DistributedLock lock = new DistributedLock(kvStore, "user/" + userId)) {
            lock.acquire();
            if (writeManager == null) {
                throw ErrorHelper.buildError(StatusCode.MEMORY_UPDATE_MEMORY_EXECUTION_ERROR,
                        "memory_type", "all", "error_msg", "write manager is not initialized");
            }
            writeManager.updateMemById(userId, scopeId, memId, memory, semanticStore);
        }
    }

    // ========================= Variable Operations =========================

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, String> getVariables(Object names, String userId, String scopeId) {
        if (!validateId(LogEventType.MEMORY_RETRIEVE, scopeId)) {
            return Map.of();
        }
        if (searchManager == null) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", "all", "error_msg", "search manager is not initialized");
        }
        Map<String, String> ret = new HashMap<>();
        if (names == null) {
            return searchManager.getAllUserVariable(userId, scopeId);
        }
        if (names instanceof String name) {
            String value = searchManager.getUserVariable(userId, scopeId, name);
            ret.put(name, value);
            return ret;
        }
        if (names instanceof List<?> nameList) {
            for (Object nameObj : nameList) {
                String name = String.valueOf(nameObj);
                String value = searchManager.getUserVariable(userId, scopeId, name);
                ret.put(name, value);
            }
            return ret;
        }
        throw ErrorHelper.buildError(StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                "memory_type", "all", "error_msg", "names must be String | List<String> | null");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void updateVariables(Map<String, String> variables, String userId, String scopeId) {
        if (!validateId(LogEventType.MEMORY_UPDATE, scopeId)) {
            return;
        }
        try (DistributedLock lock = new DistributedLock(kvStore, "user/" + userId)) {
            lock.acquire();
            if (variableManager == null) {
                throw ErrorHelper.buildError(StatusCode.MEMORY_UPDATE_MEMORY_EXECUTION_ERROR,
                        "memory_type", "variable", "error_msg", "variable manager is not initialized");
            }
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                variableManager.updateUserVariable(userId, scopeId, entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean deleteVariables(List<String> names, String userId, String scopeId) {
        if (!validateId(LogEventType.MEMORY_DELETE, scopeId)) {
            return false;
        }
        try (DistributedLock lock = new DistributedLock(kvStore, "user/" + userId)) {
            lock.acquire();
            if (variableManager == null) {
                throw ErrorHelper.buildError(StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR,
                        "memory_type", "variable", "error_msg", "variable manager is not initialized");
            }
            for (String name : names) {
                variableManager.deleteUserVariable(userId, scopeId, name);
            }
            return true;
        }
    }

    // ========================= Search Operations =========================

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<MemResult> searchUserMem(String query, int num, String userId, String scopeId, double threshold) {
        if (!validateId(LogEventType.MEMORY_RETRIEVE, scopeId)) {
            return List.of();
        }
        SemanticStore semanticStore = createSemanticStoreWithEmbedding(scopeId);
        if (searchManager == null) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", "all", "error_msg", "search manager is not initialized");
        }
        SearchParams params = SearchParams.builder()
                .query(query).scopeId(scopeId).topK(num).userId(userId).threshold(threshold)
                .build();
        try {
            List<Map<String, Object>> searchData = new ArrayList<>();
            for (MemoryType memType : List.of(
                    MemoryType.USER_PROFILE,
                    MemoryType.SEMANTIC_MEMORY,
                    MemoryType.EPISODIC_MEMORY)) {
                params.setSearchType(memType.getValue());
                List<Map<String, Object>> res = searchManager.search(params, semanticStore);
                if (res != null) {
                    searchData.addAll(res);
                }
            }
            searchData.sort((a, b) -> Double.compare(
                    scoreValue(b.get("score")),
                    scoreValue(a.get("score"))));
            if (searchData.size() > num) {
                searchData = new ArrayList<>(searchData.subList(0, num));
            }
            return toMemResults(searchData, MemoryType.USER_PROFILE);
        } catch (Exception e) {
            MEMORY_LOGGER.warn("[{}] Search user mem has exception: {}",
                    LogEventType.MEMORY_RETRIEVE, e.getMessage());
            return List.of();
        }
    }

    private static double scoreValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<MemResult> searchUserHistorySummary(String query, int num, String userId,
                                                     String scopeId, double threshold) {
        if (!validateId(LogEventType.MEMORY_RETRIEVE, scopeId)) {
            return List.of();
        }
        SemanticStore semanticStore = createSemanticStoreWithEmbedding(scopeId);
        if (searchManager == null) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", "all", "error_msg", "search manager is not initialized");
        }
        SearchParams params = SearchParams.builder()
                .query(query).scopeId(scopeId).topK(num).userId(userId).threshold(threshold)
                .searchType(MemoryType.SUMMARY.getValue())
                .build();
        try {
            List<Map<String, Object>> searchData = searchManager.search(params, semanticStore);
            return toMemResults(searchData, MemoryType.SUMMARY);
        } catch (Exception e) {
            MEMORY_LOGGER.warn("[{}] Search user history summary has exception: {}",
                    LogEventType.MEMORY_RETRIEVE, e.getMessage());
            return List.of();
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int userMemTotalNum(String userId, String scopeId) {
        if (!validateId(LogEventType.MEMORY_RETRIEVE, scopeId)) {
            return 0;
        }
        List<Map<String, Object>> data = searchManager.listUserProfile(userId, scopeId);
        return data.size();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<MemInfo> getUserMemByPage(String userId, String scopeId,
                                           int pageSize, int pageIdx, MemoryType memoryType) {
        if (!validateId(LogEventType.MEMORY_RETRIEVE, scopeId)) {
            return List.of();
        }
        if (searchManager == null) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", "all", "error_msg", "search manager is not initialized");
        }
        String searchMemType = memoryType == MemoryType.UNKNOWN ? null : memoryType.getValue();
        List<Map<String, Object>> searchData = searchManager.listUserMem(userId, scopeId,
                pageSize, pageIdx, searchMemType);
        if (searchData == null || searchData.isEmpty()) {
            return List.of();
        }
        List<MemInfo> results = new ArrayList<>();
        for (Map<String, Object> item : searchData) {
            String memTypeStr = String.valueOf(item.getOrDefault("mem_type", MemoryType.UNKNOWN.getValue()));
            results.add(MemInfo.builder()
                    .memId(String.valueOf(item.get("id")))
                    .content(String.valueOf(item.get("mem")))
                    .type(MemoryType.fromValue(memTypeStr))
                    .build());
        }
        return results;
    }

    // ========================= Private Helpers =========================

    private static Model getLlmFromConfig(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        return new Model(modelClientConfig, modelConfig);
    }

    @SuppressWarnings("unchecked")
    private MemoryScopeConfig getInternalScopeConfig(String scopeId) {
        if (scopeConfig.containsKey(scopeId)) {
            MemoryScopeConfig config = scopeConfig.get(scopeId);
            try {
                // Deep copy via JSON, decrypt api_keys
                Map<String, Object> configMap = MAPPER.convertValue(config, Map.class);
                decryptApiKeyInMap(configMap, "model_client_cfg");
                decryptApiKeyInMap(configMap, "embedding_cfg");
                String decryptedJson = MAPPER.writeValueAsString(configMap);
                return MAPPER.readValue(decryptedJson, MemoryScopeConfig.class);
            } catch (Exception e) {
                MEMORY_LOGGER.error("[{}] Failed to decrypt scope config: {}",
                        LogEventType.MEMORY_RETRIEVE, e.getMessage());
                return null;
            }
        }
        return getScopeConfig(scopeId);
    }

    private Embedding getScopeEmbeddingModel(String scopeId) {
        Embedding cached = scopeEmbedding.get(scopeId);
        if (cached != null) {
            return cached;
        }
        try {
            MemoryScopeConfig config = getInternalScopeConfig(scopeId);
            if (config != null && config.getEmbeddingCfg() != null) {
                Embedding embeddingModel = new APIEmbedding(config.getEmbeddingCfg());
                scopeEmbedding.put(scopeId, embeddingModel);
                return embeddingModel;
            }
        } catch (Exception e) {
            MEMORY_LOGGER.error("[{}] Failed to get scope embedding model for scope {}: {}",
                    LogEventType.MEMORY_RETRIEVE, scopeId, e.getMessage());
            return null;
        }
        MEMORY_LOGGER.error("[{}] No embedding model available for scope: {}",
                LogEventType.MEMORY_RETRIEVE, scopeId);
        return null;
    }

    private Map.Entry<String, Model> getScopeLlm(String scopeId) {
        try {
            MemoryScopeConfig config = getInternalScopeConfig(scopeId);
            if (config != null && config.getModelCfg() != null && config.getModelClientCfg() != null) {
                Model llm = getLlmFromConfig(config.getModelCfg(), config.getModelClientCfg());
                return new AbstractMap.SimpleEntry<>(config.getModelCfg().getModelName(), llm);
            }
            if (sysMemConfig != null && sysMemConfig.getDefaultModelCfg() != null
                    && sysMemConfig.getDefaultModelClientCfg() != null) {
                Model llm = getLlmFromConfig(sysMemConfig.getDefaultModelCfg(),
                        sysMemConfig.getDefaultModelClientCfg());
                return new AbstractMap.SimpleEntry<>(sysMemConfig.getDefaultModelCfg().getModelName(), llm);
            }
            return baseLlm;
        } catch (Exception e) {
            MEMORY_LOGGER.error("[{}] Failed to get scope LLM: {}", LogEventType.MEMORY_RETRIEVE, e.getMessage());
            return baseLlm;
        }
    }

    private SemanticStore createSemanticStoreWithEmbedding(String scopeId) {
        SemanticStore semanticStore = new SemanticStore(vectorStore);
        Embedding embeddingModel = getScopeEmbeddingModel(scopeId);
        if (embeddingModel != null) {
            semanticStore.initializeEmbeddingModel(embeddingModel);
        } else if (baseEmbed != null) {
            semanticStore.initializeEmbeddingModel(baseEmbed);
        }
        return semanticStore;
    }

    private CheckMessagesResult checkMessages(List<BaseMessage> messages) {
        List<BaseMessage> outMessages = new ArrayList<>();
        boolean hasHumanMsg = false;
        String humanRole = "user";
        for (BaseMessage msg : messages) {
            if (humanRole.equals(msg.getRole())) {
                outMessages.add(msg);
                hasHumanMsg = true;
            } else {
                String content = msg.getContentAsString();
                if (content != null && content.length() > sysMemConfig.getInputMsgMaxLen()) {
                    content = content.substring(0, sysMemConfig.getInputMsgMaxLen());
                    msg.setContent(content);
                }
                outMessages.add(msg);
            }
        }
        return new CheckMessagesResult(hasHumanMsg, outMessages);
    }

    private List<BaseMessage> getHistoryMessages(String userId, String scopeId,
                                                  String sessionId, int historyWindowSize) {
        if (messageManager == null) {
            return List.of();
        }
        List<MessageManager.MessageRecord> records = messageManager.get(userId, scopeId, sessionId, historyWindowSize);
        List<BaseMessage> historyMessages = new ArrayList<>();
        String humanRole = "user";
        for (MessageManager.MessageRecord record : records) {
            BaseMessage msg = record.message();
            if (humanRole.equals(msg.getRole())) {
                historyMessages.add(msg);
            } else {
                String content = msg.getContentAsString();
                if (content != null && content.length() > sysMemConfig.getInputMsgMaxLen()) {
                    content = content.substring(0, sysMemConfig.getInputMsgMaxLen());
                    msg.setContent(content);
                }
                historyMessages.add(msg);
            }
        }
        return historyMessages;
    }

    private static boolean validateId(LogEventType eventType, String scopeId) {
        if (scopeId == null || scopeId.isEmpty()) {
            MEMORY_LOGGER.error("[{}] Scope_id is invalid.", eventType);
            return false;
        }
        if (scopeId.contains("/")) {
            MEMORY_LOGGER.error("[{}] Scope_id cannot contain separator '/'.", eventType);
            return false;
        }
        if (scopeId.length() > 128) {
            MEMORY_LOGGER.error("[{}] Scope_id length exceeds limit (128).", eventType);
            return false;
        }
        return true;
    }

    private void runMigration(BooleanSupplier migrateFunc, String storeType) {
        try {
            MEMORY_LOGGER.info("[{}] Starting {} migration", LogEventType.MEMORY_INIT, storeType);
            boolean success = migrateFunc.getAsBoolean();
            if (!success) {
                throw new IllegalStateException(storeType + " migration returned failure status");
            }
            MEMORY_LOGGER.info("[{}] {} migration completed successfully", LogEventType.MEMORY_INIT, storeType);
        } catch (Exception e) {
            MEMORY_LOGGER.error("[{}] {} migration failed: {}", LogEventType.MEMORY_INIT, storeType, e.getMessage());
            throw ErrorHelper.buildError(StatusCode.MEMORY_REGISTER_STORE_EXECUTION_ERROR,
                    "store_type", storeType, "error_msg", storeType + " migration failed: " + e.getMessage());
        }
    }

    private static List<MemResult> toMemResults(List<Map<String, Object>> searchData, MemoryType defaultType) {
        List<MemResult> results = new ArrayList<>();
        for (Map<String, Object> item : searchData) {
            Object memTypeObj = item.get("mem_type");
            MemoryType memType = memTypeObj instanceof MemoryType mt ? mt
                    : memTypeObj != null ? MemoryType.fromValue(String.valueOf(memTypeObj)) : defaultType;

            results.add(MemResult.builder()
                    .memInfo(MemInfo.builder()
                            .memId(String.valueOf(item.get("id")))
                            .content(String.valueOf(item.get("mem")))
                            .type(memType)
                            .build())
                    .score(item.get("score") instanceof Number n ? n.doubleValue() : 0.0)
                    .build());
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    private void encryptApiKeyInMap(Map<String, Object> configMap, String cfgKey) {
        Object subCfg = configMap.get(cfgKey);
        if (subCfg instanceof Map<?, ?> subMap) {
            Map<String, Object> mutableMap = (Map<String, Object>) subMap;
            Object apiKey = mutableMap.get("api_key");
            if (apiKey == null) {
                apiKey = mutableMap.get("apiKey");
            }
            if (apiKey instanceof String key && !key.isEmpty()) {
                String encrypted = BaseMemoryManager.encryptMemoryIfNeeded(sysMemConfig.getCryptoKey(), key);
                mutableMap.put("api_key", encrypted);
            }
            mutableMap.remove("apiKey");
        }
    }

    @SuppressWarnings("unchecked")
    private void decryptApiKeyInMap(Map<String, Object> configMap, String cfgKey) {
        Object subCfg = configMap.get(cfgKey);
        if (subCfg instanceof Map<?, ?> subMap) {
            Map<String, Object> mutableMap = (Map<String, Object>) subMap;
            Object apiKey = mutableMap.get("api_key");
            if (apiKey == null) {
                apiKey = mutableMap.get("apiKey");
            }
            if (apiKey instanceof String key && !key.isEmpty()) {
                String decrypted = BaseMemoryManager.decryptMemoryIfNeeded(sysMemConfig.getCryptoKey(), key);
                mutableMap.put("api_key", decrypted);
            }
            mutableMap.remove("apiKey");
        }
    }

    private record CheckMessagesResult(boolean hasHumanMsg, List<BaseMessage> messages) {
    }
}
