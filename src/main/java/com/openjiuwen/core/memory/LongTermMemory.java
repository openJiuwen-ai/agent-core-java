/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory;

import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.utils.Pair;
import com.openjiuwen.core.common.utils.Singleton;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.store.BaseDbStore;
import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.memory.common.DistributedLock;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.memory.config.MemoryEngineConfig;
import com.openjiuwen.core.memory.config.MemoryScopeConfig;
import com.openjiuwen.core.memory.manage.index.BaseMemoryManager;
import com.openjiuwen.core.memory.manage.index.UserProfileManager;
import com.openjiuwen.core.memory.manage.index.VariableManager;
import com.openjiuwen.core.memory.manage.index.WriteManager;
import com.openjiuwen.core.memory.manage.memmodel.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.memmodel.DataIdManager;
import com.openjiuwen.core.memory.manage.memmodel.MemoryType;
import com.openjiuwen.core.memory.manage.memmodel.MessageAddRequest;
import com.openjiuwen.core.memory.manage.memmodel.MessageManager;
import com.openjiuwen.core.memory.manage.memmodel.MessageTables;
import com.openjiuwen.core.memory.manage.memmodel.ScopeUserMappingManager;
import com.openjiuwen.core.memory.manage.memmodel.SemanticStore;
import com.openjiuwen.core.memory.manage.memmodel.SqlDbStore;
import com.openjiuwen.core.memory.manage.memmodel.UserMemStore;
import com.openjiuwen.core.memory.manage.search.SearchManager;
import com.openjiuwen.core.memory.manage.search.SearchParams;
import com.openjiuwen.core.memory.process.extract.Generator;
import com.openjiuwen.core.retrieval.embedding.APIEmbedding;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vectorstore.VectorStore;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for memory engine.
 * <p>
 * Defines the core interface for memory storage and retrieval operations.
 * Provides unified memory management functionality including conversation memory,
 * user variables, semantic search, and persistence.
 * <p>
 * Concrete implementations should handle memory operations across multiple storage
 * backends (KV store, semantic store, database store).
 * <p>
 * Corresponds to Python: long_term_memory.py - LongTermMemory
 */
@Singleton("LongTermMemory")
public class LongTermMemory {

    private static final LoggerProtocol logger = Loggers.MEMORY;

    public static final String DEFAULT_VALUE = "__default__";
    public static final String SCOPE_CONFIG_KEY = "memory_scope_config";

    private static volatile LongTermMemory instance;
    private static final Object LOCK = new Object();

    // Config
    private MemoryEngineConfig sysMemConfig;
    private final Map<String, MemoryScopeConfig> scopeConfig = new ConcurrentHashMap<>();

    // Store
    private BaseKVStore kvStore;
    private SemanticStore semanticStore;
    private BaseDbStore dbStore;

    // Managers
    private ScopeUserMappingManager scopeUserMappingManager;
    private MessageManager messageManager;
    private UserProfileManager userProfileManager;
    private VariableManager variableManager;
    private WriteManager writeManager;
    private SearchManager searchManager;
    private Generator generator;

    // LLM
    private Pair<String, Model> baseLlm;

    // Embedding model cache
    private final Map<String, Embedding> scopeEmbedding = new ConcurrentHashMap<>();

    /**
     * Private constructor for singleton pattern.
     */
    private LongTermMemory() {
        // Initialize with null values
    }

    /**
     * Get the singleton instance.
     *
     * @return The singleton LongTermMemory instance
     */
    public static LongTermMemory getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new LongTermMemory();
                }
            }
        }
        return instance;
    }

    /**
     * Reset the singleton instance. Used for testing.
     */
    public static void resetInstance() {
        synchronized (LOCK) {
            instance = null;
        }
    }

    /**
     * Register store instances.
     *
     * @param kvStore        Key-value store for fast structured data access
     * @param vectorStore    Vector storage for vector-based similarity search
     * @param dbStore        Database store for persistent data storage
     * @param embeddingModel Embedding model for semantic search
     * @return CompletableFuture that completes when registration is done
     */
    public CompletableFuture<Void> registerStore(BaseKVStore kvStore,
                                                  VectorStore vectorStore,
                                                  BaseDbStore dbStore,
                                                  Embedding embeddingModel) {
        if (kvStore == null) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_REGISTER_STORE_EXECUTION_ERROR,
                    "kv store is required, cannot be None"
            );
        }

        this.kvStore = kvStore;
        this.semanticStore = new SemanticStore(vectorStore, embeddingModel);
        this.dbStore = dbStore;

        if (this.semanticStore != null && embeddingModel != null) {
            // Only temporarily initialize the embedding model of the semantic_store during the register_store process
            this.semanticStore.initializeEmbeddingModel(embeddingModel);
        }

        if (this.dbStore != null) {
            return MessageTables.createTables(this.dbStore);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Set configuration.
     *
     * @param config Memory engine configuration parameters
     */
    public void setConfig(MemoryEngineConfig config) {
        if (kvStore == null || semanticStore == null || dbStore == null) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_SET_CONFIG_EXECUTION_ERROR,
                    "stores must be registered before setting config"
            );
        }

        this.sysMemConfig = config;
        DataIdManager dataIdGenerator = new DataIdManager();
        UserMemStore userMemStore = new UserMemStore(this.kvStore);

        if (this.dbStore != null) {
            SqlDbStore sqlDbStore = new SqlDbStore(this.dbStore);
            this.scopeUserMappingManager = new ScopeUserMappingManager(sqlDbStore);
            this.messageManager = new MessageManager(
                    sqlDbStore,
                    dataIdGenerator,
                    config.getCryptoKey()
            );
        }

        this.userProfileManager = new UserProfileManager(
                this.semanticStore,
                userMemStore,
                dataIdGenerator,
                config.getCryptoKey()
        );

        this.variableManager = new VariableManager(this.kvStore, config.getCryptoKey());

        Map<String, BaseMemoryManager> managers = new HashMap<>();
        managers.put(MemoryType.USER_PROFILE.getValue(), this.userProfileManager);
        managers.put(MemoryType.VARIABLE.getValue(), this.variableManager);

        this.writeManager = new WriteManager(managers, userMemStore);
        this.searchManager = new SearchManager(managers, userMemStore, config.getCryptoKey());
        this.generator = new Generator();

        // Set init LLM
        Model llm = getLlmFromConfig(config.getDefaultModelCfg(), config.getDefaultModelClientCfg());
        this.baseLlm = new Pair<>(config.getDefaultModelCfg().getModelName(), llm);
    }

    /**
     * Set the scope-specific memory configuration and store it in kv_store.
     *
     * @param scopeId           The scope identifier
     * @param memoryScopeConfig The scope-specific memory configuration
     * @return CompletableFuture containing true if the configuration was set successfully, false otherwise
     */
    public CompletableFuture<Boolean> setScopeConfig(String scopeId, MemoryScopeConfig memoryScopeConfig) {
        if (!validateId(scopeId)) {
            logger.error("Invalid scope_id format, scope_id={}", scopeId);
            return CompletableFuture.completedFuture(false);
        }

        // Create a copy of the config (Java records are immutable, so we'd need a builder for encryption)
        // For now, we'll store the config as-is and handle encryption at retrieval/storage layer
        MemoryScopeConfig encryptedConfig = encryptScopeConfig(memoryScopeConfig);
        this.scopeConfig.put(scopeId, encryptedConfig);

        String configKey = SCOPE_CONFIG_KEY + "/" + scopeId;
        String configJson = serializeScopeConfig(encryptedConfig);

        return kvStore.set(configKey, configJson)
                .thenApply(v -> {
                    // Clear cached embedding model for this scope since configuration changed
                    scopeEmbedding.remove(scopeId);
                    return true;
                });
    }

    /**
     * Get the scope-specific memory configuration from kv_store.
     *
     * @param scopeId Unique identifier for the scope
     * @return CompletableFuture containing the decrypted memory configuration for the scope, or null if not found
     */
    public CompletableFuture<MemoryScopeConfig> getScopeConfig(String scopeId) {
        if (!validateId(scopeId)) {
            logger.error("Invalid scope_id format, scope_id={}", scopeId);
            return CompletableFuture.completedFuture(null);
        }

        String configKey = SCOPE_CONFIG_KEY + "/" + scopeId;
        return kvStore.get(configKey)
                .thenApply(configJson -> {
                    if (configJson == null || configJson.isEmpty()) {
                        return null;
                    }
                    MemoryScopeConfig encryptedConfig = deserializeScopeConfig(configJson);
                    return decryptScopeConfig(encryptedConfig);
                });
    }

    /**
     * Delete the scope-specific memory configuration from kv_store.
     *
     * @param scopeId The scope identifier whose configuration should be deleted
     * @return CompletableFuture containing true if the configuration was deleted successfully, false otherwise
     */
    public CompletableFuture<Boolean> deleteScopeConfig(String scopeId) {
        if (!validateId(scopeId)) {
            logger.error("Invalid scope_id format, scope_id={}", scopeId);
            return CompletableFuture.completedFuture(false);
        }

        String configKey = SCOPE_CONFIG_KEY + "/" + scopeId;
        return kvStore.delete(configKey)
                .thenApply(v -> {
                    scopeConfig.remove(scopeId);
                    scopeEmbedding.remove(scopeId);
                    logger.debug("Successfully deleted configuration for scope {}", scopeId);
                    return true;
                })
                .exceptionally(e -> {
                    logger.error("Failed to delete configuration for scope {}", scopeId, e);
                    return false;
                });
    }

    /**
     * Delete all memories associated with a specific scope.
     *
     * @param scopeId The scope identifier whose memories should be deleted
     * @return CompletableFuture containing true if all memories were deleted successfully, false otherwise
     */
    public CompletableFuture<Boolean> deleteMemByScope(String scopeId) {
        if (!validateId(scopeId)) {
            logger.error("Invalid scope_id format, scope_id={}", scopeId);
            return CompletableFuture.completedFuture(false);
        }

        return scopeUserMappingManager.getByScopeId(scopeId)
                .thenCompose(scopeUserData -> {
                    List<String> userIds = new ArrayList<>();
                    for (Map<String, Object> scopeUser : scopeUserData) {
                        userIds.add((String) scopeUser.get("user_id"));
                    }

                    // Use write_manager to delete all memories associated with the scope
                    List<CompletableFuture<Void>> futures = new ArrayList<>();
                    if (writeManager != null) {
                        for (String userId : userIds) {
                            CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
                                DistributedLock lock = new DistributedLock(kvStore, "user/" + userId);
                                try {
                                    lock.acquire();
                                    writeManager.deleteMemByUserId(userId, scopeId).join();
                                } finally {
                                    lock.release();
                                }
                                return null;
                            });
                            futures.add(future);
                        }
                    }

                    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                            .thenCompose(v -> scopeUserMappingManager.deleteByScopeId(scopeId))
                            .thenApply(v -> {
                                logger.debug("Successfully deleted memories for scope {}", scopeId);
                                return true;
                            });
                });
    }

    /**
     * Add messages and optionally generate memories.
     *
     * @param messages               List of messages to add
     * @param agentConfig            Agent memory configuration
     * @param userId                 User identifier
     * @param scopeId                Scope identifier
     * @param sessionId              Session identifier
     * @param timestamp              Optional timestamp
     * @param genMem                 Whether to generate memories
     * @param genMemWithHistoryMsgNum Number of history messages for memory generation
     * @return CompletableFuture that completes when operation is done
     */
    public CompletableFuture<Void> addMessages(
            List<BaseMessage> messages,
            AgentMemoryConfig agentConfig,
            String userId,
            String scopeId,
            String sessionId,
            Instant timestamp,
            boolean genMem,
            int genMemWithHistoryMsgNum
    ) {
        if (!validateId(scopeId)) {
            logger.error("Invalid scope_id format, scope_id={}", scopeId);
            return CompletableFuture.completedFuture(null);
        }

        return getScopeLlm(scopeId)
                .thenCompose(llm -> setSemanticStoreEmbeddingModel(scopeId)
                        .thenCompose(v -> {
                            DistributedLock lock = new DistributedLock(kvStore, "user/" + userId);
                            return CompletableFuture.supplyAsync(() -> {
                                try {
                                    lock.acquire();
                                    return processAddMessages(messages, agentConfig, userId, scopeId,
                                            sessionId, timestamp, genMem, genMemWithHistoryMsgNum, llm);
                                } finally {
                                    lock.release();
                                }
                            }).thenCompose(f -> f);
                        }));
    }

    private CompletableFuture<Void> processAddMessages(
            List<BaseMessage> messages,
            AgentMemoryConfig agentConfig,
            String userId,
            String scopeId,
            String sessionId,
            Instant timestamp,
            boolean genMem,
            int genMemWithHistoryMsgNum,
            Pair<String, Model> llm
    ) {
        if (llm == null) {
            logger.error("llm is not initialized.");
            return CompletableFuture.completedFuture(null);
        }

        return getHistoryMessages(userId, scopeId, sessionId, genMemWithHistoryMsgNum)
                .thenCompose(historyMessages -> 
                    scopeUserMappingManager.add(userId, scopeId)
                        .thenCompose(v -> {
                            // If timestamp is null, use current time
                            Instant ts = timestamp != null ? timestamp : Instant.now();
                            String[] msgIdHolder = {"-1"};

                            // Add messages sequentially
                            CompletableFuture<Void> addFuture = CompletableFuture.completedFuture(null);
                            for (int i = 0; i < messages.size(); i++) {
                                final int index = i;
                                final BaseMessage msg = messages.get(i);
                                addFuture = addFuture.thenCompose(x -> {
                                    Instant msgTimestamp = ts.plus(index, ChronoUnit.MILLIS);
                                    MessageAddRequest addReq = MessageAddRequest.builder()
                                            .userId(userId)
                                            .scopeId(scopeId)
                                            .role(msg.getRole())
                                            .content((String) msg.getContent())
                                            .sessionId(sessionId)
                                            .timestamp(msgTimestamp)
                                            .build();
                                    return messageManager.add(addReq)
                                            .thenAccept(msgId -> msgIdHolder[0] = msgId);
                                });
                            }

                            if (!genMem) {
                                return addFuture;
                            }

                            return addFuture.thenCompose(x -> {
                                Pair<Boolean, List<BaseMessage>> checkResult = checkMessages(messages);
                                if (!checkResult.getKey()) {
                                    logger.debug("Memory engine no need to process messages.");
                                    return CompletableFuture.completedFuture(null);
                                }

                                return generator.genAllMemory(
                                        checkResult.getValue(),
                                        agentConfig,
                                        userId,
                                        scopeId,
                                        new Pair<>(llm.getKey(), llm.getValue()),
                                        historyMessages,
                                        msgIdHolder[0]
                                ).thenCompose(allMemory -> {
                                    try {
                                        return writeManager.addMem(allMemory, llm)
                                                .thenRun(() -> logger.debug("Successfully added memory units"));
                                    } catch (Exception e) {
                                        logger.error("Failed to add mem, error: {}", e.getMessage());
                                        throw ErrorBuilder.build(
                                                StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                                                e.getMessage()
                                        );
                                    }
                                });
                            });
                        }));
    }

    /**
     * Get recent messages.
     *
     * @param userId    User identifier
     * @param scopeId   Scope identifier
     * @param sessionId Session identifier
     * @param num       Number of messages to retrieve
     * @return CompletableFuture containing list of messages in order of writing
     */
    public CompletableFuture<List<BaseMessage>> getRecentMessages(
            String userId,
            String scopeId,
            String sessionId,
            int num
    ) {
        if (!validateId(scopeId)) {
            logger.error("Invalid scope_id format, scope_id={}", scopeId);
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        return messageManager.get(userId, scopeId, sessionId, num)
                .thenApply(recentMessagesTuple -> {
                    List<BaseMessage> recentMessages = new ArrayList<>();
                    for (MessageManager.MessageWithTimestamp mwt : recentMessagesTuple) {
                        recentMessages.add(mwt.message());
                    }
                    return recentMessages;
                });
    }

    /**
     * Retrieve a specific message by its unique identifier.
     *
     * @param msgId Unique identifier of the message to retrieve
     * @return CompletableFuture containing tuple of (message object, creation timestamp) or null
     */
    public CompletableFuture<MessageManager.MessageWithTimestamp> getMessageById(String msgId) {
        if (messageManager == null) {
            logger.warning("Message manager is not initialized.");
            return CompletableFuture.completedFuture(null);
        }
        return messageManager.getById(msgId)
                .thenApply(opt -> opt.orElse(null));
    }

    /**
     * Delete a specific memory by ID.
     *
     * @param memId   Memory ID
     * @param userId  User ID
     * @param scopeId Scope ID
     * @return CompletableFuture that completes when operation is done
     */
    public CompletableFuture<Void> deleteMemById(String memId, String userId, String scopeId) {
        if (!validateId(scopeId)) {
            logger.error("Invalid scope_id format, scope_id={}", scopeId);
            return CompletableFuture.completedFuture(null);
        }

        return setSemanticStoreEmbeddingModel(scopeId)
                .thenCompose(v -> {
                    DistributedLock lock = new DistributedLock(kvStore, "user/" + userId);
                    return CompletableFuture.supplyAsync(() -> {
                        try {
                            lock.acquire();
                            if (writeManager == null) {
                                throw ErrorBuilder.build(
                                        StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR,
                                        "write manager is not initialized"
                                );
                            }
                            writeManager.deleteMemById(userId, scopeId, memId).join();
                            return null;
                        } finally {
                            lock.release();
                        }
                    });
                });
    }

    /**
     * Delete all type memories for a user with scope id.
     *
     * @param userId  User identifier
     * @param scopeId Scope identifier
     * @return CompletableFuture that completes when operation is done
     */
    public CompletableFuture<Void> deleteMemByUserId(String userId, String scopeId) {
        if (!validateId(scopeId)) {
            logger.error("Invalid scope_id format, scope_id={}", scopeId);
            return CompletableFuture.completedFuture(null);
        }

        return setSemanticStoreEmbeddingModel(scopeId)
                .thenCompose(v -> {
                    DistributedLock lock = new DistributedLock(kvStore, "user/" + userId);
                    return CompletableFuture.supplyAsync(() -> {
                        try {
                            lock.acquire();
                            if (writeManager == null) {
                                throw ErrorBuilder.build(
                                        StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR,
                                        "write manager is not initialized"
                                );
                            }
                            writeManager.deleteMemByUserId(userId, scopeId).join();
                            return null;
                        } finally {
                            lock.release();
                        }
                    });
                });
    }

    /**
     * Update the content of an existing memory entry.
     *
     * @param memId   Memory ID
     * @param memory  New memory content
     * @param userId  User ID
     * @param scopeId Scope ID
     * @return CompletableFuture that completes when operation is done
     */
    public CompletableFuture<Void> updateMemById(String memId, String memory, String userId, String scopeId) {
        if (!validateId(scopeId)) {
            logger.error("Invalid scope_id format, scope_id={}", scopeId);
            return CompletableFuture.completedFuture(null);
        }

        return setSemanticStoreEmbeddingModel(scopeId)
                .thenCompose(v -> {
                    DistributedLock lock = new DistributedLock(kvStore, "user/" + userId);
                    return CompletableFuture.supplyAsync(() -> {
                        try {
                            lock.acquire();
                            if (writeManager == null) {
                                throw ErrorBuilder.build(
                                        StatusCode.MEMORY_UPDATE_MEMORY_EXECUTION_ERROR,
                                        "write manager is not initialized"
                                );
                            }
                            writeManager.updateMemById(userId, scopeId, memId, memory).join();
                            return null;
                        } finally {
                            lock.release();
                        }
                    });
                });
    }

    /**
     * Get user variable(s).
     *
     * @param names   Name of the variable(s) to get (null for all, String for one, List for multiple)
     * @param userId  User identifier
     * @param scopeId Scope identifier
     * @return CompletableFuture containing map of variable name to value
     */
    public CompletableFuture<Map<String, String>> getVariables(Object names, String userId, String scopeId) {
        if (!validateId(scopeId)) {
            logger.error("Invalid scope_id format, scope_id={}", scopeId);
            return CompletableFuture.completedFuture(new HashMap<>());
        }

        if (searchManager == null) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "search manager is not initialized"
            );
        }

        if (names == null) {
            return searchManager.getAllUserVariable(userId, scopeId)
                    .thenApply(objMap -> {
                        Map<String, String> result = new HashMap<>();
                        for (Map.Entry<String, Object> entry : objMap.entrySet()) {
                            result.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : null);
                        }
                        return result;
                    });
        }

        if (names instanceof String) {
            String name = (String) names;
            return searchManager.getUserVariable(userId, scopeId, name)
                    .thenApply(value -> {
                        Map<String, String> result = new HashMap<>();
                        result.put(name, value);
                        return result;
                    });
        }

        if (names instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> nameList = (List<String>) names;
            Map<String, String> ret = new HashMap<>();
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (String name : nameList) {
                chain = chain.thenCompose(v ->
                        searchManager.getUserVariable(userId, scopeId, name)
                                .thenAccept(value -> ret.put(name, value))
                );
            }
            return chain.thenApply(v -> ret);
        }

        throw ErrorBuilder.build(
                StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                "names must be str | list[str] | None"
        );
    }

    /**
     * Search user memories.
     *
     * @param query     Search query
     * @param num       Number of results
     * @param userId    User identifier
     * @param scopeId   Scope identifier
     * @param threshold Score threshold
     * @return CompletableFuture containing list of memory results
     */
    public CompletableFuture<List<MemResult>> searchUserMem(
            String query,
            int num,
            String userId,
            String scopeId,
            double threshold
    ) {
        if (!validateId(scopeId)) {
            logger.error("Invalid scope_id format, scope_id={}", scopeId);
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        return setSemanticStoreEmbeddingModel(scopeId)
                .thenCompose(v -> {
                    if (searchManager == null) {
                        throw ErrorBuilder.build(
                                StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                                "search manager is not initialized"
                        );
                    }

                    SearchParams params = SearchParams.builder()
                            .query(query)
                            .scopeId(scopeId)
                            .topK(num)
                            .userId(userId)
                            .threshold(threshold)
                            .build();

                    try {
                        return searchManager.search(params)
                                .thenApply(searchData -> {
                                    List<MemResult> memResults = new ArrayList<>();
                                    for (Map<String, Object> item : searchData) {
                                        Object memTypeObj = item.get("mem_type");
                                        MemoryType memType = memTypeObj instanceof MemoryType
                                                ? (MemoryType) memTypeObj
                                                : MemoryType.USER_PROFILE;
                                        MemInfo memInfo = MemInfo.builder()
                                                .memId((String) item.get("id"))
                                                .content((String) item.get("mem"))
                                                .type(memType)
                                                .build();
                                        MemResult memResult = MemResult.builder()
                                                .memInfo(memInfo)
                                                .score(((Number) item.getOrDefault("score", 0.0)).doubleValue())
                                                .build();
                                        memResults.add(memResult);
                                    }
                                    return memResults;
                                });
                    } catch (IllegalArgumentException e) {
                        logger.warning("Search user mem has value exception: {}", e.getMessage());
                        return CompletableFuture.completedFuture(new ArrayList<>());
                    } catch (Exception e) {
                        logger.warning("Search user mem has exception: {}", e.getMessage());
                        return CompletableFuture.completedFuture(new ArrayList<>());
                    }
                });
    }

    /**
     * Return total number of user memory.
     *
     * @param userId  User identifier
     * @param scopeId Scope identifier
     * @return CompletableFuture containing total count
     */
    public CompletableFuture<Integer> userMemTotalNum(String userId, String scopeId) {
        if (!validateId(scopeId)) {
            logger.error("Invalid scope_id format, scope_id={}", scopeId);
            return CompletableFuture.completedFuture(0);
        }

        return searchManager.listUserProfile(userId, scopeId, null)
                .thenApply(List::size);
    }

    /**
     * List user memories with pagination support.
     *
     * @param userId     User identifier
     * @param scopeId    Scope identifier
     * @param pageSize   Number of memories per page
     * @param pageIdx    Page index (0-based)
     * @param memoryType Memory type filter
     * @return CompletableFuture containing list of memory info
     */
    public CompletableFuture<List<MemInfo>> getUserMemByPage(
            String userId,
            String scopeId,
            int pageSize,
            int pageIdx,
            MemoryType memoryType
    ) {
        if (!validateId(scopeId)) {
            logger.error("Invalid scope_id format, scope_id={}", scopeId);
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        if (searchManager == null) {
            throw ErrorBuilder.build(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "search manager is not initialized"
            );
        }

        return searchManager.listUserMem(userId, scopeId, pageSize, pageIdx)
                .thenApply(searchData -> {
                    if (searchData == null || searchData.isEmpty()) {
                        return new ArrayList<MemInfo>();
                    }

                    List<MemInfo> memResults = new ArrayList<>();
                    for (Map<String, Object> item : searchData) {
                        String memTypeValue = (String) item.getOrDefault("mem_type", MemoryType.UNKNOWN.getValue());
                        // Apply filtering if type is not UNKNOWN
                        if (memoryType == MemoryType.UNKNOWN || memTypeValue.equals(memoryType.getValue())) {
                            MemInfo memInfo = MemInfo.builder()
                                    .memId((String) item.get("id"))
                                    .content((String) item.get("mem"))
                                    .type(MemoryType.fromValue(memTypeValue))
                                    .build();
                            memResults.add(memInfo);
                        }
                    }
                    return memResults;
                });
    }

    /**
     * Update user variables.
     *
     * @param variables Variable name to value pairs
     * @param userId    User identifier
     * @param scopeId   Scope identifier
     * @return CompletableFuture that completes when operation is done
     */
    public CompletableFuture<Void> updateVariables(Map<String, String> variables, String userId, String scopeId) {
        if (!validateId(scopeId)) {
            logger.error("Invalid scope_id format, scope_id={}", scopeId);
            return CompletableFuture.completedFuture(null);
        }

        DistributedLock lock = new DistributedLock(kvStore, "user/" + userId);
        return CompletableFuture.supplyAsync(() -> {
            try {
                lock.acquire();
                if (variableManager == null) {
                    throw ErrorBuilder.build(
                            StatusCode.MEMORY_UPDATE_MEMORY_EXECUTION_ERROR,
                            "variable manager is not initialized"
                    );
                }

                for (Map.Entry<String, String> entry : variables.entrySet()) {
                    variableManager.updateUserVariable(userId, scopeId, entry.getKey(), entry.getValue()).join();
                }
                return null;
            } finally {
                lock.release();
            }
        });
    }

    /**
     * Delete user variables.
     *
     * @param names   Names of the variables to delete
     * @param userId  User identifier
     * @param scopeId Scope identifier
     * @return CompletableFuture containing true if successful
     */
    public CompletableFuture<Boolean> deleteVariables(List<String> names, String userId, String scopeId) {
        if (!validateId(scopeId)) {
            logger.error("Invalid scope_id format, scope_id={}", scopeId);
            return CompletableFuture.completedFuture(false);
        }

        DistributedLock lock = new DistributedLock(kvStore, "user/" + userId);
        return CompletableFuture.supplyAsync(() -> {
            try {
                lock.acquire();
                if (variableManager == null) {
                    throw ErrorBuilder.build(
                            StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR,
                            "variable manager is not initialized"
                    );
                }

                for (String name : names) {
                    variableManager.deleteUserVariable(userId, scopeId, name).join();
                }
                return true;
            } finally {
                lock.release();
            }
        });
    }

    // ===== Private helper methods =====

    private static Model getLlmFromConfig(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        return new Model(modelClientConfig, modelConfig);
    }

    private CompletableFuture<MemoryScopeConfig> getInternalScopeConfig(String scopeId) {
        // First check if config is in memory cache
        if (scopeConfig.containsKey(scopeId)) {
            MemoryScopeConfig config = scopeConfig.get(scopeId);
            return CompletableFuture.completedFuture(decryptScopeConfig(config));
        }

        // If not in memory, get from kv_store
        return getScopeConfig(scopeId);
    }

    private CompletableFuture<Embedding> getScopeEmbeddingModel(String scopeId) {
        // Check if embedding model is already in cache
        if (scopeEmbedding.containsKey(scopeId)) {
            return CompletableFuture.completedFuture(scopeEmbedding.get(scopeId));
        }

        return getInternalScopeConfig(scopeId)
                .thenApply(config -> {
                    try {
                        if (config != null && config.getEmbeddingCfg() != null) {
                            // Use APIEmbedding to instantiate the embedding model
                            Embedding embeddingModel = new APIEmbedding(config.getEmbeddingCfg());
                            // Cache the embedding model
                            scopeEmbedding.put(scopeId, embeddingModel);
                            return embeddingModel;
                        }
                    } catch (Exception e) {
                        logger.error("Failed to get or instantiate embedding model for scope {}: {}", scopeId, e.getMessage());
                    }
                    logger.error("No embedding model available for scope {}", scopeId);
                    return null;
                });
    }

    private CompletableFuture<Pair<String, Model>> getScopeLlm(String scopeId) {
        return getInternalScopeConfig(scopeId)
                .thenApply(config -> {
                    try {
                        if (config != null && config.getModelCfg() != null && config.getModelClientCfg() != null) {
                            Model llm = getLlmFromConfig(config.getModelCfg(), config.getModelClientCfg());
                            return new Pair<>(config.getModelCfg().getModelName(), llm);
                        }

                        // If the LLM fails to be obtained, try to use the system default configuration
                        if (sysMemConfig == null) {
                            return baseLlm;
                        }
                        if (sysMemConfig.getDefaultModelClientCfg() == null) {
                            logger.debug("Default model client config is missing, cannot instantiate LLM");
                            return baseLlm;
                        }
                        if (sysMemConfig.getDefaultModelCfg() == null) {
                            logger.debug("Default model config is missing, cannot instantiate LLM");
                            return baseLlm;
                        }

                        Model llm = getLlmFromConfig(sysMemConfig.getDefaultModelCfg(), sysMemConfig.getDefaultModelClientCfg());
                        return new Pair<>(sysMemConfig.getDefaultModelCfg().getModelName(), llm);
                    } catch (Exception e) {
                        logger.error("Failed to get scope LLM for scope {}: {}", scopeId, e.getMessage());
                        return baseLlm;
                    }
                });
    }

    private CompletableFuture<Void> setSemanticStoreEmbeddingModel(String scopeId) {
        if (semanticStore == null) {
            return CompletableFuture.completedFuture(null);
        }

        return getScopeEmbeddingModel(scopeId)
                .thenAccept(embeddingModel -> {
                    if (embeddingModel != null) {
                        semanticStore.initializeEmbeddingModel(embeddingModel);
                    }
                });
    }

    private Pair<Boolean, List<BaseMessage>> checkMessages(List<BaseMessage> messages) {
        List<BaseMessage> outMessages = new ArrayList<>();
        boolean hasHumanMsg = false;
        UserMessage humanMessage = new UserMessage();

        for (BaseMessage msg : messages) {
            if (msg.getRole().equals(humanMessage.getRole())) {
                outMessages.add(msg);
                hasHumanMsg = true;
                continue;
            }
            // Truncate content if needed
            String content = (String) msg.getContent();
            if (content.length() > sysMemConfig.getInputMsgMaxLen()) {
                content = content.substring(0, sysMemConfig.getInputMsgMaxLen());
                // Create new message with truncated content
                msg = createTruncatedMessage(msg, content);
            }
            outMessages.add(msg);
        }

        return new Pair<>(hasHumanMsg, outMessages);
    }

    private BaseMessage createTruncatedMessage(BaseMessage original, String truncatedContent) {
        // Create a new message with the truncated content while preserving the role
        if (original instanceof UserMessage) {
            return new UserMessage(truncatedContent);
        }
        // For other message types, return as-is (they should implement similar truncation)
        return original;
    }

    private CompletableFuture<List<BaseMessage>> getHistoryMessages(
            String userId,
            String scopeId,
            String sessionId,
            int historyWindowSize
    ) {
        if (messageManager == null) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        return messageManager.get(userId, scopeId, sessionId, historyWindowSize)
                .thenApply(historyMessagesTuple -> {
                    List<BaseMessage> historyMessages = new ArrayList<>();
                    UserMessage humanMessage = new UserMessage();

                    for (MessageManager.MessageWithTimestamp mwt : historyMessagesTuple) {
                        BaseMessage msg = mwt.message();
                        if (msg.getRole().equals(humanMessage.getRole())) {
                            historyMessages.add(msg);
                            continue;
                        }
                        // Truncate content if needed
                        String content = (String) msg.getContent();
                        if (content.length() > sysMemConfig.getInputMsgMaxLen()) {
                            content = content.substring(0, sysMemConfig.getInputMsgMaxLen());
                            msg = createTruncatedMessage(msg, content);
                        }
                        historyMessages.add(msg);
                    }
                    return historyMessages;
                });
    }

    /**
     * Validate the scope_id format.
     *
     * @param scopeId Scope identifier
     * @return true if the scope_id is valid, false otherwise
     */
    public static boolean validateId(String scopeId) {
        if (scopeId == null || scopeId.isEmpty()) {
            logger.error("scope_id is invalid: {}", scopeId);
            return false;
        }
        if (scopeId.contains("/")) {
            logger.error("scope_id cannot contain separator '/', scope_id={}", scopeId);
            return false;
        }
        if (scopeId.length() > 128) {
            logger.error("scope_id length exceeds limit (128), scope_id={}", scopeId);
            return false;
        }
        return true;
    }

    // ===== Helper methods for config serialization/encryption =====

    private MemoryScopeConfig encryptScopeConfig(MemoryScopeConfig config) {
        // For now, return config as-is. Encryption would be implemented using BaseMemoryManager methods
        return config;
    }

    private MemoryScopeConfig decryptScopeConfig(MemoryScopeConfig config) {
        // For now, return config as-is. Decryption would be implemented using BaseMemoryManager methods
        return config;
    }

    private String serializeScopeConfig(MemoryScopeConfig config) {
        // Placeholder for JSON serialization
        return "{}";
    }

    private MemoryScopeConfig deserializeScopeConfig(String json) {
        // Placeholder for JSON deserialization
        return MemoryScopeConfig.builder().build();
    }

    // ===== Getters for testing =====

    public BaseKVStore getKvStore() {
        return kvStore;
    }

    public SemanticStore getSemanticStore() {
        return semanticStore;
    }

    public BaseDbStore getDbStore() {
        return dbStore;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public UserProfileManager getUserProfileManager() {
        return userProfileManager;
    }

    public VariableManager getVariableManager() {
        return variableManager;
    }

    public WriteManager getWriteManager() {
        return writeManager;
    }

    public SearchManager getSearchManager() {
        return searchManager;
    }

    public Generator getGenerator() {
        return generator;
    }

    // ===== Setters for testing =====

    public void setSearchManager(SearchManager searchManager) {
        this.searchManager = searchManager;
    }

}

