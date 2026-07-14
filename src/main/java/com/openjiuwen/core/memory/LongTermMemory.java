/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.async.FutureList;
import com.openjiuwen.core.common.async.FutureMap;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.store.BaseDbStore;
import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.foundation.store.BaseMemoryIndex;
import com.openjiuwen.core.foundation.store.BaseMessageStore;
import com.openjiuwen.core.foundation.store.BaseVectorStore;
import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.core.foundation.store.MemoryDoc;
import com.openjiuwen.core.foundation.store.index.SimpleMemoryIndex;
import com.openjiuwen.core.memory.codec.AesStorageCodec;
import com.openjiuwen.core.memory.common.DistributedLock;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.memory.config.FutureMemoryScopeConfig;
import com.openjiuwen.core.memory.config.MemoryEngineConfig;
import com.openjiuwen.core.memory.config.MemoryScopeConfig;
import com.openjiuwen.core.memory.manage.index.BaseMemoryManager;
import com.openjiuwen.core.memory.manage.index.FragmentMemoryManager;
import com.openjiuwen.core.memory.manage.index.SummaryManager;
import com.openjiuwen.core.memory.manage.index.VariableManager;
import com.openjiuwen.core.memory.manage.index.WriteManager;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.DataIdManager;
import com.openjiuwen.core.memory.manage.mem_model.DbModelSupport;
import com.openjiuwen.core.memory.manage.mem_model.FragmentMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.manage.mem_model.MessageAddRequest;
import com.openjiuwen.core.memory.manage.mem_model.MessageManager;
import com.openjiuwen.core.memory.manage.mem_model.ScopeUserMappingManager;
import com.openjiuwen.core.memory.manage.mem_model.SqlDbStore;
import com.openjiuwen.core.memory.manage.mem_model.SqlMessageStore;
import com.openjiuwen.core.memory.manage.mem_model.SummaryUnit;
import com.openjiuwen.core.memory.manage.mem_model.VariableUnit;
import com.openjiuwen.core.memory.manage.search.SearchManager;
import com.openjiuwen.core.memory.manage.search.SearchParams;
import com.openjiuwen.core.memory.migration.RunMigrations;
import com.openjiuwen.core.memory.process.extract.Generator;
import com.openjiuwen.core.retrieval.embedding.APIEmbedding;
import com.openjiuwen.core.runner.callback.MemoryEvents;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Main long-term memory engine.
 *
 * <p>Mirrors Python's {@code LongTermMemory} in
 * {@code openjiuwen/core/memory/long_term_memory.py}.</p>
 */
public class LongTermMemory {
    private static final LoggerProtocol MEMORY_LOGGER = Loggers.MEMORY;
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static final String DEFAULT_VALUE = "__default__";
    public static final String SCOPE_CONFIG_KEY = "memory_scope_config";

    private static volatile LongTermMemory instance;

    private MemoryEngineConfig sysMemConfig;
    private final ConcurrentHashMap<String, MemoryScopeConfig> scopeConfig = new ConcurrentHashMap<>();

    private BaseKVStore kvStore;
    private BaseVectorStore vectorStore;
    private BaseDbStore<?> dbStore;
    private BaseMessageStore messageStore;

    private BaseMemoryIndex memoryIndex;
    private AesStorageCodec storageCodec;

    private ScopeUserMappingManager scopeUserMappingManager;
    private MessageManager messageManager;
    private FragmentMemoryManager fragmentMemoryManager;
    private VariableManager variableManager;
    private WriteManager writeManager;
    private SummaryManager summaryManager;
    private SearchManager searchManager;
    private Generator generator;
    private List<String> fragmentType;

    private Object baseLlm;
    private Embedding baseEmbed;
    private final ConcurrentHashMap<String, Embedding> scopeEmbedding = new ConcurrentHashMap<>();

    public LongTermMemory() {
    }

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

    public static void resetInstance() {
        synchronized (LongTermMemory.class) {
            instance = null;
        }
    }

    public BaseKVStore getKvStore() {
        return kvStore;
    }

    public BaseMemoryIndex getMemoryIndex() {
        return memoryIndex;
    }

    public CompletableFuture<Void> registerPlugin(String name,
                                                  Class<? extends BaseMemoryIndex> pluginClass,
                                                  Map<String, Object> params) {
        BaseMemoryIndex pluginInstance = instantiatePlugin(pluginClass, params == null ? Map.of() : params);
        if (memoryIndex == null) {
            memoryIndex = pluginInstance;
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> registerStore(BaseKVStore kvStore,
                                                 BaseVectorStore vectorStore,
                                                 BaseDbStore<?> dbStore,
                                                 Embedding embeddingModel,
                                                 BaseMessageStore messageStore) {
        if (kvStore == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_REGISTER_STORE_EXECUTION_ERROR,
                    "store_type", "kv store",
                    "error_msg", "kv store is required, cannot be None"
            );
        }
        if (vectorStore != null && !(vectorStore instanceof BaseVectorStore)) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_REGISTER_STORE_EXECUTION_ERROR,
                    "store_type", "vector store",
                    "error_msg", "vector store must be instance of BaseVectorStore"
            );
        }
        if (dbStore != null && !(dbStore instanceof BaseDbStore<?>)) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_REGISTER_STORE_EXECUTION_ERROR,
                    "store_type", "db store",
                    "error_msg", "db store must be instance of BaseDbStore"
            );
        }
        if (messageStore != null && !(messageStore instanceof BaseMessageStore)) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_REGISTER_STORE_EXECUTION_ERROR,
                    "store_type", "message store",
                    "error_msg", "message store must be instance of BaseMessageStore"
            );
        }

        this.kvStore = kvStore;
        this.vectorStore = vectorStore;
        this.dbStore = dbStore;
        this.baseEmbed = embeddingModel;
        this.messageStore = messageStore;

        if (this.vectorStore != null && this.kvStore != null) {
            registerPlugin(
                    "semantic_index",
                    SimpleMemoryIndex.class,
                    Map.of(
                            "kv_store", this.kvStore,
                            "vector_store", this.vectorStore,
                            "embedding_model", this.baseEmbed
                    )
            ).join();
        }

        if (this.dbStore != null) {
            join(DbModelSupport.createTables(this.dbStore));
        }
        if (this.messageStore == null && this.dbStore != null) {
            this.messageStore = new SqlMessageStore(new SqlDbStore(this.dbStore));
        }

        setConfig(new MemoryEngineConfig());

        runMigration(store -> RunMigrations.runKvMigrations((BaseKVStore) store), this.kvStore, "kv store").join();
        if (this.vectorStore != null) {
            runMigration(
                    store -> RunMigrations.runVectorMigrations((BaseVectorStore) store),
                    this.vectorStore,
                    "vector store"
            ).join();
        }
        if (this.dbStore != null) {
            SqlDbStore sqlDbStore = new SqlDbStore(this.dbStore);
            runMigration(store -> RunMigrations.runSqlMigrations((SqlDbStore) store), sqlDbStore, "db store").join();
        }
        if (this.messageStore != null) {
            runMigration(
                    store -> RunMigrations.runMessageMigrations((BaseMessageStore) store),
                    this.messageStore,
                    "message store"
            ).join();
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> registerStore(BaseKVStore kvStore,
                                                 BaseVectorStore vectorStore,
                                                 BaseDbStore<?> dbStore,
                                                 Embedding embeddingModel) {
        return registerStore(kvStore, vectorStore, dbStore, embeddingModel, null);
    }

    public static CompletableFuture<Void> migrateBetweenIndices(BaseMemoryIndex sourceIndex,
                                                                BaseMemoryIndex targetIndex) {
        List<BaseMemoryIndex.UserScopeKey> scopes = join(sourceIndex.listUserScopes());
        for (BaseMemoryIndex.UserScopeKey scope : scopes) {
            int offset = 0;
            int batchSize = 100;
            while (true) {
                List<MemoryDoc> documents = join(
                        sourceIndex.listMemories(scope.userId(), scope.scopeId(), offset, batchSize, null)
                );
                if (documents == null || documents.isEmpty()) {
                    break;
                }
                List<MemoryDoc> targetDocuments = new ArrayList<>(documents.size());
                for (MemoryDoc doc : documents) {
                    targetDocuments.add(new MemoryDoc(
                            doc.getId(),
                            doc.getText(),
                            doc.getType(),
                            doc.getTimestamp(),
                            new LinkedHashMap<>(doc.getFields())
                    ));
                }
                join(targetIndex.addMemories(scope.userId(), scope.scopeId(), targetDocuments));
                offset += batchSize;
            }
        }
        MEMORY_LOGGER.info(
                "Cross-index migration completed. event_type={}, scope_count={}",
                LogEventType.MEMORY_INIT.getValue(),
                scopes.size()
        );
        return CompletableFuture.completedFuture(null);
    }

    public void setConfig(MemoryEngineConfig config) {
        if (kvStore == null || dbStore == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_SET_CONFIG_EXECUTION_ERROR,
                    "config_type", "system",
                    "error_msg", "kv store and db store must be registered before setting config"
            );
        }
        if (memoryIndex == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_SET_CONFIG_EXECUTION_ERROR,
                    "config_type", "system",
                    "error_msg", "memory_index must be provided (via register_plugin or register_store)"
            );
        }

        sysMemConfig = config;
        AesStorageCodec codec = new AesStorageCodec(config.getCryptoKey());
        memoryIndex.setStorageCodec(codec);
        storageCodec = codec;

        DataIdManager dataIdGenerator = new DataIdManager();
        SqlDbStore sqlDbStore = new SqlDbStore(dbStore);
        scopeUserMappingManager = new ScopeUserMappingManager(sqlDbStore);

        if (messageStore != null) {
            if (messageStore instanceof SqlMessageStore sqlMessageStore && sqlMessageStore.getCryptoKey() == null) {
                sqlMessageStore.setCryptoKey(config.getCryptoKey());
            }
            messageManager = new MessageManager(messageStore);
        }

        fragmentMemoryManager = new FragmentMemoryManager(memoryIndex, config.getCryptoKey());
        summaryManager = new SummaryManager(memoryIndex, sysMemConfig.getCryptoKey());
        variableManager = new VariableManager(kvStore, config.getCryptoKey());

        Map<String, BaseMemoryManager> managers = new LinkedHashMap<>();
        managers.put(MemoryType.USER_PROFILE.getValue(), fragmentMemoryManager);
        managers.put(MemoryType.EPISODIC_MEMORY.getValue(), fragmentMemoryManager);
        managers.put(MemoryType.SEMANTIC_MEMORY.getValue(), fragmentMemoryManager);
        managers.put(MemoryType.VARIABLE.getValue(), variableManager);
        managers.put(MemoryType.SUMMARY.getValue(), summaryManager);

        fragmentType = List.of(
                MemoryType.USER_PROFILE.getValue(),
                MemoryType.EPISODIC_MEMORY.getValue(),
                MemoryType.SEMANTIC_MEMORY.getValue()
        );
        writeManager = new WriteManager(managers, memoryIndex);
        searchManager = new SearchManager(managers, config.getCryptoKey(), memoryIndex);
        generator = new Generator(dataIdGenerator, searchManager);

        if (config.getDefaultModelCfg() != null && config.getDefaultModelClientCfg() != null) {
            baseLlm = getLlmFromConfig(config.getDefaultModelCfg(), config.getDefaultModelClientCfg());
        }
    }

    public CompletableFuture<Boolean> setScopeConfig(String scopeId, MemoryScopeConfig memoryScopeConfig) {
        if (!validateId(LogEventType.MEMORY_STORE, scopeId)) {
            MEMORY_LOGGER.error("Invalid scope_id format. event_type={}, scope_id={}",
                    LogEventType.MEMORY_STORE.getValue(), scopeId);
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_SET_CONFIG_EXECUTION_ERROR,
                    "config_type", "scope",
                    "error_msg", "invalid scope_id format"
            );
        }

        MemoryScopeConfig encryptedConfig = copyConfig(memoryScopeConfig);
        encodeApiKeys(encryptedConfig);
        scopeConfig.put(scopeId, encryptedConfig);

        String configKey = SCOPE_CONFIG_KEY + "/" + scopeId;
        join(kvStore.set(configKey, writeJson(encryptedConfig)));
        scopeEmbedding.remove(scopeId);
        return CompletableFuture.completedFuture(true);
    }

    public FutureMemoryScopeConfig getScopeConfig(String scopeId) {
        if (!validateId(LogEventType.MEMORY_RETRIEVE, scopeId)) {
            MEMORY_LOGGER.error("Invalid scope_id format. event_type={}, scope_id={}",
                    LogEventType.MEMORY_RETRIEVE.getValue(), scopeId);
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", "scope_config",
                    "error_msg", "invalid scope_id format"
            );
        }

        String configKey = SCOPE_CONFIG_KEY + "/" + scopeId;
        Object rawConfig = join(kvStore.get(configKey));
        String configJson = readStoreValue(rawConfig);
        if (configJson == null || configJson.isEmpty()) {
            return null;
        }

        MemoryScopeConfig encryptedConfig = readConfig(configJson);
        decodeApiKeys(encryptedConfig);
        return new FutureMemoryScopeConfig(encryptedConfig);
    }

    public boolean deleteScopeConfig(String scopeId) {
        if (!validateId(LogEventType.MEMORY_DELETE, scopeId)) {
            MEMORY_LOGGER.error("Invalid scope_id format. event_type={}, scope_id={}",
                    LogEventType.MEMORY_DELETE.getValue(), scopeId);
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR,
                    "memory_type", "scope_config",
                    "error_msg", "invalid scope_id format"
            );
        }
        try {
            join(kvStore.delete(SCOPE_CONFIG_KEY + "/" + scopeId));
            scopeConfig.remove(scopeId);
            scopeEmbedding.remove(scopeId);
            MEMORY_LOGGER.debug("Successfully deleted configuration. event_type={}, scope_id={}",
                    LogEventType.MEMORY_DELETE.getValue(), scopeId);
            return true;
        } catch (RuntimeException exception) {
            MEMORY_LOGGER.error("Failed to delete configuration. event_type={}, exception={}, scope_id={}",
                    LogEventType.MEMORY_DELETE.getValue(), exception.getMessage(), scopeId);
            throw memoryError(
                    StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR,
                    exception,
                    "memory_type", "scope_config",
                    "error_msg", "failed to delete scope config: " + exception.getMessage()
            );
        }
    }

    public CompletableFuture<Boolean> deleteMemByScope(String scopeId) {
        if (!validateId(LogEventType.MEMORY_DELETE, scopeId)) {
            MEMORY_LOGGER.error("Invalid scope_id format. event_type={}, scope_id={}",
                    LogEventType.MEMORY_DELETE.getValue(), scopeId);
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR,
                    "memory_type", "all",
                    "error_msg", "invalid scope_id format"
            );
        }

        List<Map<String, Object>> scopeUserData = join(scopeUserMappingManager.getByScopeId(scopeId));
        List<String> userIds = new ArrayList<>();
        if (scopeUserData != null) {
            for (Map<String, Object> scopeUser : scopeUserData) {
                Object userId = scopeUser.get("user_id");
                if (userId != null) {
                    userIds.add(String.valueOf(userId));
                }
            }
        }

        if (writeManager != null) {
            for (String userId : userIds) {
                withUserLock(userId, () -> writeManager.deleteMemByUserId(userId, scopeId));
            }
        }
        join(scopeUserMappingManager.deleteByScopeId(scopeId));
        MEMORY_LOGGER.debug("Successfully deleted memories. event_type={}, scope_id={}",
                LogEventType.MEMORY_DELETE.getValue(), scopeId);
        return CompletableFuture.completedFuture(true);
    }

    public CompletableFuture<AddMemResult> addMessages(List<BaseMessage> messages,
                                                       AgentMemoryConfig agentConfig,
                                                       String userId,
                                                       String scopeId,
                                                       String sessionId,
                                                       ZonedDateTime timestamp,
                                                       boolean genMem,
                                                       int genMemWithHistoryMsgNum) {
        if (!validateId(LogEventType.MEMORY_STORE, scopeId)) {
            MEMORY_LOGGER.error("Invalid scope_id format. event_type={}, scope_id={}, user_id={}",
                    LogEventType.MEMORY_STORE.getValue(), scopeId, userId);
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                    "memory_type", "all",
                    "error_msg", "invalid scope_id format"
            );
        }

        final ZonedDateTime baseTimestamp = timestamp == null ? ZonedDateTime.now() : timestamp;
        Model llm = getScopeLlm(scopeId);
        MemoryScopeConfig effectiveScopeConfig = internalScopeConfig(scopeId);
        applyScopeEmbedding(scopeId);

        AddMemResult result = withUserLock(userId, () -> {
            if (messageManager == null) {
                throw ErrorHelper.buildError(
                        StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                        "memory_type", "message",
                        "error_msg", "message manager is not initialized"
                );
            }
            if (generator == null || writeManager == null) {
                throw ErrorHelper.buildError(
                        StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                        "memory_type", "all",
                        "error_msg", "memory managers are not initialized"
                );
            }

            List<BaseMessage> historyMessages = getHistoryMessages(
                    userId,
                    scopeId,
                    sessionId,
                    genMemWithHistoryMsgNum
            );
            join(scopeUserMappingManager.add(userId, scopeId));
            String timestampText = TIMESTAMP_FORMAT.format(baseTimestamp);
            String msgId = "-1";
            List<BaseMessage> safeMessages = messages == null ? List.of() : messages;
            for (int index = 0; index < safeMessages.size(); index++) {
                BaseMessage message = safeMessages.get(index);
                MessageAddRequest addRequest = MessageAddRequest.builder()
                        .userId(userId)
                        .scopeId(scopeId)
                        .role(message.getRole())
                        .content(message.getContentAsString())
                        .sessionId(sessionId)
                        .timestamp(baseTimestamp.plusNanos(index * 1_000_000L))
                        .build();
                msgId = join(messageManager.add(addRequest));
            }

            if (!genMem) {
                return new AddMemResult();
            }
            if (llm == null) {
                if (canPersistMessagesWithoutGeneration()) {
                    MEMORY_LOGGER.warning("LLM is not initialized; long-term memory generation is skipped. event_type={}, user_id={}, scope_id={}",
                            LogEventType.MEMORY_STORE.getValue(), userId, scopeId);
                    return new AddMemResult();
                }
                MEMORY_LOGGER.error("LLM is not initialized. event_type={}, user_id={}, scope_id={}",
                        LogEventType.MEMORY_STORE.getValue(), userId, scopeId);
                throw ErrorHelper.buildError(
                        StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                        "memory_type", "all",
                        "error_msg", "LLM is not initialized"
                );
            }

            CheckMessagesResult checkResult = checkMessages(safeMessages);
            if (!checkResult.hasHumanMessage()) {
                MEMORY_LOGGER.debug("Memory engine no need to process messages. event_type={}, user_id={}, scope_id={}",
                        LogEventType.MEMORY_STORE.getValue(), userId, scopeId);
                return new AddMemResult();
            }

            Map<String, Object> generatorKwargs = new LinkedHashMap<>();
            generatorKwargs.put("scope_id", scopeId);
            generatorKwargs.put("user_id", userId);
            generatorKwargs.put("messages", checkResult.messages());
            generatorKwargs.put("history_messages", historyMessages);
            generatorKwargs.put("session_id", sessionId);
            generatorKwargs.put("config", agentConfig);
            generatorKwargs.put("base_chat_model", llm);
            generatorKwargs.put("message_mem_id", msgId);
            generatorKwargs.put("timestamp", timestampText);
            generatorKwargs.put("forbidden_variables", sysMemConfig.getForbiddenVariables());
            generatorKwargs.put("summary_max_token", sysMemConfig.getSingleTurnHistorySummaryMaxToken());
            generatorKwargs.put("scope_config", effectiveScopeConfig);
            generatorKwargs.put("semantic_store", memoryIndex);
            Map<String, List<BaseMemoryUnit>> allMemory = join(generator.genAllMemory(generatorKwargs));

            try {
                List<BaseMemoryUnit> writeResult = join(writeManager.addMemories(userId, scopeId, allMemory, llm));
                MEMORY_LOGGER.debug("Successfully added memory units. event_type={}, memory_count={}, user_id={}, scope_id={}",
                        LogEventType.MEMORY_STORE.getValue(), allMemory == null ? 0 : allMemory.size(), userId, scopeId);
                return buildAddMemResult(writeResult);
            } catch (RuntimeException exception) {
                MEMORY_LOGGER.error("Failed to add mem. event_type={}, exception={}, user_id={}, scope_id={}",
                        LogEventType.MEMORY_STORE.getValue(), exception.getMessage(), userId, scopeId);
                throw memoryError(
                        StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                        exception,
                        "memory_type", "unknown",
                        "error_msg", String.valueOf(exception.getMessage())
                );
            }
        });
        return CompletableFuture.completedFuture(result);
    }

    public CompletableFuture<AddMemResult> addMessages(List<BaseMessage> messages,
                                                       AgentMemoryConfig agentConfig,
                                                       String userId,
                                                       String scopeId,
                                                       String sessionId,
                                                       OffsetDateTime timestamp,
                                                       boolean genMem,
                                                       int genMemWithHistoryMsgNum) {
        ZonedDateTime zonedTimestamp = timestamp == null ? null : timestamp.toZonedDateTime();
        return addMessages(messages, agentConfig, userId, scopeId, sessionId, zonedTimestamp, genMem, genMemWithHistoryMsgNum);
    }

    public CompletableFuture<AddMemResult> addMessages(List<BaseMessage> messages,
                                                       AgentMemoryConfig agentConfig,
                                                       String userId,
                                                       String scopeId,
                                                       String sessionId) {
        return addMessages(messages, agentConfig, userId, scopeId, sessionId, (ZonedDateTime) null, true, 2);
    }

    public FutureList<BaseMessage> getRecentMessages(String userId,
                                                     String scopeId,
                                                     String sessionId,
                                                     int num) {
        if (!validateId(LogEventType.MEMORY_RETRIEVE, scopeId)) {
            MEMORY_LOGGER.error("Invalid scope_id format. event_type={}, user_id={}, scope_id={}",
                    LogEventType.MEMORY_RETRIEVE.getValue(), userId, scopeId);
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", "message",
                    "error_msg", "invalid scope_id format"
            );
        }
        List<Map.Entry<BaseMessage, ZonedDateTime>> rows = join(messageManager.get(userId, scopeId, sessionId, num));
        List<BaseMessage> recentMessages = new ArrayList<>();
        for (Map.Entry<BaseMessage, ZonedDateTime> row : rows) {
            recentMessages.add(row.getKey());
        }
        return FutureList.completed(recentMessages);
    }

    public CompletableFuture<Map.Entry<BaseMessage, ZonedDateTime>> getMessageById(String msgId) {
        if (messageManager == null) {
            MEMORY_LOGGER.warning("Message manager is not initialized. event_type={}, memory_id={}",
                    LogEventType.MEMORY_RETRIEVE.getValue(), msgId);
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", "message",
                    "error_msg", "message manager is not initialized"
            );
        }
        return messageManager.getById(msgId);
    }

    public CompletableFuture<Void> deleteMessagesByUserAndScope(String userId, String scopeId) {
        if (!validateId(LogEventType.MEMORY_RETRIEVE, scopeId)) {
            MEMORY_LOGGER.error("Invalid scope_id format. event_type={}, user_id={}, scope_id={}",
                    LogEventType.MEMORY_RETRIEVE.getValue(), userId, scopeId);
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR,
                    "memory_type", "message",
                    "error_msg", "invalid scope_id format"
            );
        }
        join(messageManager.deleteByUserAndScope(userId, scopeId));
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> deleteMemById(String memId, String userId, String scopeId) {
        if (!validateId(LogEventType.MEMORY_DELETE, scopeId)) {
            MEMORY_LOGGER.error("Invalid scope_id format. event_type={}, user_id={}, scope_id={}, memory_id={}",
                    LogEventType.MEMORY_DELETE.getValue(), userId, scopeId, memId);
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR,
                    "memory_type", "all",
                    "error_msg", "invalid scope_id format"
            );
        }
        withUserLock(userId, () -> {
            if (writeManager == null) {
                throw ErrorHelper.buildError(
                        StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR,
                        "memory_type", "all",
                        "error_msg", "write manager is not initialized"
                );
            }
            join(writeManager.deleteMemById(userId, scopeId, memId));
            return null;
        });
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> deleteMemByUserId(String userId, String scopeId) {
        if (!validateId(LogEventType.MEMORY_DELETE, scopeId)) {
            MEMORY_LOGGER.error("Invalid scope_id format. event_type={}, user_id={}, scope_id={}",
                    LogEventType.MEMORY_DELETE.getValue(), userId, scopeId);
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR,
                    "memory_type", "all",
                    "error_msg", "invalid scope_id format"
            );
        }
        withUserLock(userId, () -> {
            if (writeManager == null) {
                throw ErrorHelper.buildError(
                        StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR,
                        "memory_type", "all",
                        "error_msg", "write manager is not initialized"
                );
            }
            join(writeManager.deleteMemByUserId(userId, scopeId));
            return null;
        });
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> updateMemById(String memId, String memory, String userId, String scopeId) {
        if (!validateId(LogEventType.MEMORY_UPDATE, scopeId)) {
            MEMORY_LOGGER.error("Invalid scope_id format. event_type={}, user_id={}, scope_id={}, memory_id={}",
                    LogEventType.MEMORY_UPDATE.getValue(), userId, scopeId, memId);
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_UPDATE_MEMORY_EXECUTION_ERROR,
                    "memory_type", "all",
                    "error_msg", "invalid scope_id format"
            );
        }
        withUserLock(userId, () -> {
            if (writeManager == null) {
                throw ErrorHelper.buildError(
                        StatusCode.MEMORY_UPDATE_MEMORY_EXECUTION_ERROR,
                        "memory_type", "all",
                        "error_msg", "write manager is not initialized"
                );
            }
            applyScopeEmbedding(scopeId);
            join(writeManager.updateMemById(userId, scopeId, memId, memory));
            return null;
        });
        return CompletableFuture.completedFuture(null);
    }

    public FutureMap<String, String> getVariables(Object names, String userId, String scopeId) {
        if (!validateId(LogEventType.MEMORY_RETRIEVE, scopeId)) {
            MEMORY_LOGGER.error("Invalid scope_id format. event_type={}, user_id={}, scope_id={}",
                    LogEventType.MEMORY_RETRIEVE.getValue(), userId, scopeId);
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", MemoryType.VARIABLE.getValue(),
                    "error_msg", "invalid scope_id format"
            );
        }
        if (searchManager == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", "all",
                    "error_msg", "search manager is not initialized"
            );
        }
        Map<String, String> ret = new LinkedHashMap<>();
        if (names == null) {
            return FutureMap.fromFuture(searchManager.getAllUserVariable(userId, scopeId).toCompletableFuture());
        }
        if (names instanceof String name) {
            ret.put(name, join(searchManager.getUserVariable(userId, scopeId, name)));
            return FutureMap.completed(ret);
        }
        if (names instanceof List<?> nameList) {
            for (Object nameObj : nameList) {
                String name = String.valueOf(nameObj);
                ret.put(name, join(searchManager.getUserVariable(userId, scopeId, name)));
            }
            return FutureMap.completed(ret);
        }
        throw ErrorHelper.buildError(
                StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                "memory_type", "all",
                "error_msg", "names must be str | list[str] | None"
        );
    }

    public FutureList<MemResult> searchUserMem(String query,
                                               int num,
                                               String userId,
                                               String scopeId,
                                               double threshold) {
        if (num <= 0) {
            return FutureList.completed(List.of());
        }
        if (!validateId(LogEventType.MEMORY_RETRIEVE, scopeId)) {
            MEMORY_LOGGER.error("Invalid scope_id format. event_type={}, query={}, user_id={}, scope_id={}",
                    LogEventType.MEMORY_RETRIEVE.getValue(), query, userId, scopeId);
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", "user_mem",
                    "error_msg", "invalid scope_id format"
            );
        }
        if (searchManager == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", "all",
                    "error_msg", "search manager is not initialized"
            );
        }
        applyScopeEmbedding(scopeId);
        SearchParams params = SearchParams.builder()
                .userId(userId)
                .scopeId(scopeId)
                .query(query)
                .topK(num)
                .threshold(threshold)
                .searchType(fragmentType)
                .build();
        try {
            List<Map<String, Object>> searchData = join(searchManager.search(params));
            List<Map<String, Object>> sorted = sortedByScore(searchData, num);
            List<MemResult> results = toMemResults(sorted, null);
            emitSearchFinished(scopeId, userId, query, results.size(), "user_mem");
            return FutureList.completed(results);
        } catch (RuntimeException exception) {
            MEMORY_LOGGER.warning("Search user mem has exception. event_type={}, user_id={}, scope_id={}, query={}, exception={}",
                    LogEventType.MEMORY_RETRIEVE.getValue(), userId, scopeId, query, exception.getMessage());
            throw memoryError(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    exception,
                    "memory_type", "user_mem",
                    "error_msg", String.valueOf(exception.getMessage())
            );
        }
    }

    public FutureList<MemResult> searchUserHistorySummary(String query,
                                                          int num,
                                                          String userId,
                                                          String scopeId,
                                                          double threshold) {
        if (num <= 0) {
            return FutureList.completed(List.of());
        }
        if (!validateId(LogEventType.MEMORY_RETRIEVE, scopeId)) {
            MEMORY_LOGGER.error("Invalid scope_id format. event_type={}, query={}, user_id={}, scope_id={}",
                    LogEventType.MEMORY_RETRIEVE.getValue(), query, userId, scopeId);
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", "history_summary",
                    "error_msg", "invalid scope_id format"
            );
        }
        if (searchManager == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", "all",
                    "error_msg", "search manager is not initialized"
            );
        }
        applyScopeEmbedding(scopeId);
        SearchParams params = SearchParams.builder()
                .userId(userId)
                .scopeId(scopeId)
                .query(query)
                .topK(num)
                .threshold(threshold)
                .searchType(List.of(MemoryType.SUMMARY.getValue()))
                .build();
        try {
            List<Map<String, Object>> searchData = join(searchManager.search(params));
            List<MemResult> results = toMemResults(searchData, MemoryType.SUMMARY);
            emitSearchFinished(scopeId, userId, query, results.size(), "history_summary");
            return FutureList.completed(results);
        } catch (RuntimeException exception) {
            MEMORY_LOGGER.warning("Search user history summary has exception. event_type={}, user_id={}, scope_id={}, query={}, exception={}",
                    LogEventType.MEMORY_RETRIEVE.getValue(), userId, scopeId, query, exception.getMessage());
            throw memoryError(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    exception,
                    "memory_type", "history_summary",
                    "error_msg", String.valueOf(exception.getMessage())
            );
        }
    }

    public Integer userMemTotalNum(String userId, String scopeId) {
        if (!validateId(LogEventType.MEMORY_RETRIEVE, scopeId)) {
            MEMORY_LOGGER.error("Invalid scope_id format. event_type={}, user_id={}, scope_id={}",
                    LogEventType.MEMORY_RETRIEVE.getValue(), userId, scopeId);
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", "all",
                    "error_msg", "invalid scope_id format"
            );
        }
        List<Map<String, Object>> searchData = join(searchManager.listUserProfile(userId, scopeId));
        return searchData == null ? 0 : searchData.size();
    }

    public FutureList<MemInfo> getUserMemByPage(String userId,
                                                String scopeId,
                                                int pageSize,
                                                int pageIdx,
                                                MemoryType memoryType) {
        if (!validateId(LogEventType.MEMORY_RETRIEVE, scopeId)) {
            MEMORY_LOGGER.error("Invalid scope_id format. event_type={}, user_id={}, scope_id={}",
                    LogEventType.MEMORY_RETRIEVE.getValue(), userId, scopeId);
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", "all",
                    "error_msg", "invalid scope_id format"
            );
        }
        if (searchManager == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                    "memory_type", "all",
                    "error_msg", "search manager is not initialized"
            );
        }

        String searchMemoryType = memoryType == null || memoryType == MemoryType.UNKNOWN
                ? null
                : memoryType.getValue();
        List<Map<String, Object>> searchData = join(
                searchManager.listUserMem(userId, scopeId, pageSize, pageIdx, searchMemoryType)
        );
        if (searchData == null || searchData.isEmpty()) {
            return FutureList.completed(List.of());
        }
        List<MemInfo> results = new ArrayList<>();
        for (Map<String, Object> item : searchData) {
            results.add(new MemInfo(
                    Objects.toString(item.get("id"), ""),
                    Objects.toString(item.get("mem"), ""),
                    memoryTypeFrom(item.getOrDefault("mem_type", MemoryType.UNKNOWN.getValue()), MemoryType.UNKNOWN),
                    timestampFrom(item.get("timestamp"))
            ));
        }
        return FutureList.completed(results);
    }

    public CompletableFuture<Void> updateVariables(Map<String, String> variables, String userId, String scopeId) {
        if (!validateId(LogEventType.MEMORY_UPDATE, scopeId)) {
            MEMORY_LOGGER.error("Invalid scope_id format. event_type={}, user_id={}, scope_id={}",
                    LogEventType.MEMORY_UPDATE.getValue(), userId, scopeId);
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_UPDATE_MEMORY_EXECUTION_ERROR,
                    "memory_type", "variable",
                    "error_msg", "invalid scope_id format"
            );
        }
        withUserLock(userId, () -> {
            if (variableManager == null) {
                throw ErrorHelper.buildError(
                        StatusCode.MEMORY_UPDATE_MEMORY_EXECUTION_ERROR,
                        "memory_type", "variable",
                        "error_msg", "variable manager is not initialized"
                );
            }
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                join(variableManager.updateUserVariable(userId, scopeId, entry.getKey(), entry.getValue()));
            }
            return null;
        });
        return CompletableFuture.completedFuture(null);
    }

    public boolean deleteVariables(List<String> names, String userId, String scopeId) {
        if (!validateId(LogEventType.MEMORY_DELETE, scopeId)) {
            MEMORY_LOGGER.error("Invalid scope_id format. event_type={}, user_id={}, scope_id={}",
                    LogEventType.MEMORY_DELETE.getValue(), userId, scopeId);
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR,
                    "memory_type", "variable",
                    "error_msg", "invalid scope_id format"
            );
        }
        withUserLock(userId, () -> {
            if (variableManager == null) {
                throw ErrorHelper.buildError(
                        StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR,
                        "memory_type", "variable",
                        "error_msg", "variable manager is not initialized"
                );
            }
            for (String name : names) {
                join(variableManager.deleteUserVariable(userId, scopeId, name));
            }
            return null;
        });
        return true;
    }

    public static Model getLlmFromConfig(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        return new Model(modelClientConfig, modelConfig);
    }

    private MemoryScopeConfig internalScopeConfig(String scopeId) {
        MemoryScopeConfig cached = scopeConfig.get(scopeId);
        if (cached != null) {
            MemoryScopeConfig decryptedConfig = copyConfig(cached);
            decodeApiKeys(decryptedConfig);
            return decryptedConfig;
        }
        FutureMemoryScopeConfig config = getScopeConfig(scopeId);
        return config == null ? null : config.join();
    }

    private void applyScopeEmbedding(String scopeId) {
        if (memoryIndex == null) {
            return;
        }
        Embedding scopeEmbed = getScopeEmbeddingModel(scopeId);
        Embedding nextEmbed = scopeEmbed != null ? scopeEmbed : baseEmbed;
        try {
            Method setter = memoryIndex.getClass().getMethod("setEmbeddingModel", Embedding.class);
            setter.invoke(memoryIndex, nextEmbed);
        } catch (ReflectiveOperationException ignored) {
            // Python uses hasattr; missing set_embedding_model is intentionally a no-op.
        }
    }

    private Embedding getScopeEmbeddingModel(String scopeId) {
        Embedding cached = scopeEmbedding.get(scopeId);
        if (cached != null) {
            return cached;
        }
        try {
            MemoryScopeConfig config = internalScopeConfig(scopeId);
            if (config != null && config.getEmbeddingCfg() != null) {
                Embedding embeddingModel = new APIEmbedding(config.getEmbeddingCfg());
                scopeEmbedding.put(scopeId, embeddingModel);
                return embeddingModel;
            }
        } catch (RuntimeException exception) {
            MEMORY_LOGGER.error("Failed to get or instantiate embedding model. event_type={}, scope_id={}, exception={}",
                    LogEventType.MEMORY_RETRIEVE.getValue(), scopeId, exception.getMessage());
        }
        MEMORY_LOGGER.error("No embedding model available. event_type={}, scope_id={}",
                LogEventType.MEMORY_RETRIEVE.getValue(), scopeId);
        return null;
    }

    private Model getScopeLlm(String scopeId) {
        try {
            MemoryScopeConfig config = internalScopeConfig(scopeId);
            if (config != null && config.getModelCfg() != null && config.getModelClientCfg() != null) {
                return getLlmFromConfig(config.getModelCfg(), config.getModelClientCfg());
            }
            if (sysMemConfig == null) {
                return normalizeLlm(baseLlm);
            }
            if (sysMemConfig.getDefaultModelClientCfg() == null) {
                MEMORY_LOGGER.debug("Default model client config is missing, cannot instantiate LLM. event_type={}, scope_id={}",
                        LogEventType.MEMORY_RETRIEVE.getValue(), scopeId);
            } else if (sysMemConfig.getDefaultModelCfg() == null) {
                MEMORY_LOGGER.debug("Default model config is missing, cannot instantiate LLM. event_type={}, scope_id={}",
                        LogEventType.MEMORY_RETRIEVE.getValue(), scopeId);
            } else {
                return getLlmFromConfig(sysMemConfig.getDefaultModelCfg(), sysMemConfig.getDefaultModelClientCfg());
            }
            return normalizeLlm(baseLlm);
        } catch (RuntimeException exception) {
            MEMORY_LOGGER.error("Failed to get scope LLM. event_type={}, scope_id={}, exception={}",
                    LogEventType.MEMORY_RETRIEVE.getValue(), scopeId, exception.getMessage());
            return normalizeLlm(baseLlm);
        }
    }

    private static Model normalizeLlm(Object llmCandidate) {
        if (llmCandidate instanceof Model model) {
            return model;
        }
        if (llmCandidate instanceof Map.Entry<?, ?> entry && entry.getValue() instanceof Model model) {
            return tupleBackedModel(entry.getKey(), model);
        }
        if (llmCandidate == null) {
            return null;
        }
        throw ErrorHelper.buildError(
                StatusCode.MEMORY_SET_CONFIG_EXECUTION_ERROR,
                "config_type", "model",
                "error_msg", "base llm must be Model or tuple-like (name, Model)"
        );
    }

    private static Model tupleBackedModel(Object modelName, Model delegate) {
        return new Model((messages, modelConfig, modelClientConfig, options) -> {
            try {
                AssistantMessage response = delegate.invoke(
                        messages,
                        options == null ? null : options.getTools(),
                        options == null ? null : options.getTemperature(),
                        options == null ? null : options.getTopP(),
                        options == null ? null : options.getMaxTokens(),
                        options == null ? null : options.getStop(),
                        modelName(options, modelName),
                        options == null ? null : options.getOutputParser(),
                        options == null ? null : options.getTimeout(),
                        options == null ? Map.of() : options.getExtraFields()
                );
                return CompletableFuture.completedFuture(response);
            } catch (RuntimeException exception) {
                return CompletableFuture.failedFuture(exception);
            }
        });
    }

    private static String modelName(com.openjiuwen.core.foundation.llm.ModelInvokeOptions options, Object modelName) {
        if (options != null && options.getModel() != null) {
            return options.getModel();
        }
        return modelName == null ? null : String.valueOf(modelName);
    }

    private CheckMessagesResult checkMessages(List<BaseMessage> messages) {
        List<BaseMessage> outMessages = new ArrayList<>();
        boolean hasHumanMessage = false;
        String humanRole = new UserMessage("").getRole();
        for (BaseMessage message : messages) {
            if (humanRole.equals(message.getRole())) {
                outMessages.add(message);
                hasHumanMessage = true;
                continue;
            }
            String content = message.getContentAsString();
            int maxLen = sysMemConfig.getInputMsgMaxLen();
            if (content.length() > maxLen) {
                message.setContent(content.substring(0, maxLen));
            }
            outMessages.add(message);
        }
        return new CheckMessagesResult(hasHumanMessage, outMessages);
    }

    private List<BaseMessage> getHistoryMessages(String userId,
                                                 String scopeId,
                                                 String sessionId,
                                                 int historyWindowSize) {
        if (messageManager == null) {
            return List.of();
        }
        List<Map.Entry<BaseMessage, ZonedDateTime>> rows = join(
                messageManager.get(userId, scopeId, sessionId, historyWindowSize)
        );
        List<BaseMessage> historyMessages = new ArrayList<>();
        String humanRole = new UserMessage("").getRole();
        for (Map.Entry<BaseMessage, ZonedDateTime> row : rows) {
            BaseMessage message = row.getKey();
            if (humanRole.equals(message.getRole())) {
                historyMessages.add(message);
                continue;
            }
            String content = message.getContentAsString();
            int maxLen = sysMemConfig.getInputMsgMaxLen();
            if (content.length() > maxLen) {
                message.setContent(content.substring(0, maxLen));
            }
            historyMessages.add(message);
        }
        return historyMessages;
    }

    private static boolean validateId(LogEventType eventType, String scopeId) {
        if (scopeId == null || scopeId.isEmpty()) {
            MEMORY_LOGGER.error("Scope_id is invalid. event_type={}, scope_id={}", eventType.getValue(), scopeId);
            return false;
        }
        if (scopeId.contains("/")) {
            MEMORY_LOGGER.error("Scope_id cannot contain separator '/'. event_type={}, scope_id={}",
                    eventType.getValue(), scopeId);
            return false;
        }
        if (scopeId.length() > 128) {
            MEMORY_LOGGER.error("Scope_id length exceeds limit (128). event_type={}, scope_id={}",
                    eventType.getValue(), scopeId);
            return false;
        }
        return true;
    }

    private CompletableFuture<Void> runMigration(Function<Object, CompletableFuture<Void>> migrateFunc,
                                                 Object store,
                                                 String storeType) {
        try {
            MEMORY_LOGGER.info("Starting {} migration. event_type={}",
                    storeType, LogEventType.MEMORY_INIT.getValue());
            join(migrateFunc.apply(store));
            MEMORY_LOGGER.info("{} migration completed successfully. event_type={}",
                    storeType, LogEventType.MEMORY_INIT.getValue());
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException exception) {
            MEMORY_LOGGER.error("{} migration failed. event_type={}, exception={}",
                    storeType, LogEventType.MEMORY_INIT.getValue(), exception.getMessage());
            throw memoryError(
                    StatusCode.MEMORY_REGISTER_STORE_EXECUTION_ERROR,
                    exception,
                    "store_type", storeType,
                    "error_msg", storeType + " migration failed: " + exception.getMessage()
            );
        }
    }

    private BaseMemoryIndex instantiatePlugin(Class<? extends BaseMemoryIndex> pluginClass,
                                              Map<String, Object> params) {
        try {
            if (SimpleMemoryIndex.class.equals(pluginClass)) {
                return new SimpleMemoryIndex(
                        (BaseKVStore) params.get("kv_store"),
                        (BaseVectorStore) params.get("vector_store"),
                        (Embedding) params.get("embedding_model")
                );
            }
            Constructor<? extends BaseMemoryIndex> mapConstructor = findMapConstructor(pluginClass);
            if (mapConstructor != null) {
                return mapConstructor.newInstance(params);
            }
            Constructor<? extends BaseMemoryIndex> constructor = pluginClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException | ClassCastException exception) {
            throw memoryError(
                    StatusCode.MEMORY_REGISTER_STORE_EXECUTION_ERROR,
                    exception,
                    "store_type", "memory_index",
                    "error_msg", "failed to instantiate memory index plugin: " + exception.getMessage()
            );
        }
    }

    private Constructor<? extends BaseMemoryIndex> findMapConstructor(Class<? extends BaseMemoryIndex> pluginClass) {
        try {
            return pluginClass.getDeclaredConstructor(Map.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private void encodeApiKeys(MemoryScopeConfig config) {
        if (config == null || storageCodec == null) {
            return;
        }
        if (config.getModelClientCfg() != null && hasText(config.getModelClientCfg().getApiKey())) {
            config.getModelClientCfg().setApiKey(storageCodec.encode(config.getModelClientCfg().getApiKey()));
        }
        if (config.getEmbeddingCfg() != null && hasText(config.getEmbeddingCfg().getApiKey())) {
            config.getEmbeddingCfg().setApiKey(storageCodec.encode(config.getEmbeddingCfg().getApiKey()));
        }
    }

    private void decodeApiKeys(MemoryScopeConfig config) {
        if (config == null || storageCodec == null) {
            return;
        }
        if (config.getModelClientCfg() != null && hasText(config.getModelClientCfg().getApiKey())) {
            config.getModelClientCfg().setApiKey(storageCodec.decode(config.getModelClientCfg().getApiKey()));
        }
        if (config.getEmbeddingCfg() != null && hasText(config.getEmbeddingCfg().getApiKey())) {
            config.getEmbeddingCfg().setApiKey(storageCodec.decode(config.getEmbeddingCfg().getApiKey()));
        }
    }

    private MemoryScopeConfig copyConfig(MemoryScopeConfig config) {
        if (config == null) {
            return null;
        }
        return readConfig(writeJson(config));
    }

    private static MemoryScopeConfig readConfig(String json) {
        try {
            return MAPPER.readValue(json, MemoryScopeConfig.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse MemoryScopeConfig", exception);
        }
    }

    private static String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize value", exception);
        }
    }

    private static String readStoreValue(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof byte[] bytes) {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        return String.valueOf(rawValue);
    }

    private <T> T withUserLock(String userId, LockBody<T> body) {
        DistributedLock lock = new DistributedLock(kvStore, "user/" + userId);
        join(lock.enter());
        try {
            return body.execute();
        } finally {
            join(lock.exit());
        }
    }

    private AddMemResult buildAddMemResult(List<BaseMemoryUnit> writeResult) {
        List<VariableUnit> variables = new ArrayList<>();
        List<FragmentMemoryUnit> userProfile = new ArrayList<>();
        List<FragmentMemoryUnit> semanticMemory = new ArrayList<>();
        List<FragmentMemoryUnit> episodicMemory = new ArrayList<>();
        List<SummaryUnit> summary = new ArrayList<>();

        if (writeResult != null) {
            for (BaseMemoryUnit unit : writeResult) {
                MemoryType type = unit.getMemType();
                if (type == MemoryType.VARIABLE && unit instanceof VariableUnit variableUnit) {
                    variables.add(variableUnit);
                } else if (type == MemoryType.USER_PROFILE && unit instanceof FragmentMemoryUnit fragmentUnit) {
                    userProfile.add(fragmentUnit);
                } else if (type == MemoryType.SEMANTIC_MEMORY && unit instanceof FragmentMemoryUnit fragmentUnit) {
                    semanticMemory.add(fragmentUnit);
                } else if (type == MemoryType.EPISODIC_MEMORY && unit instanceof FragmentMemoryUnit fragmentUnit) {
                    episodicMemory.add(fragmentUnit);
                } else if (type == MemoryType.SUMMARY && unit instanceof SummaryUnit summaryUnit) {
                    summary.add(summaryUnit);
                }
            }
        }

        return new AddMemResult(variables, userProfile, semanticMemory, episodicMemory, summary);
    }

    private static List<Map<String, Object>> sortedByScore(List<Map<String, Object>> data, int limit) {
        if (limit <= 0 || data == null || data.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> sorted = new ArrayList<>(data);
        sorted.sort(Comparator.comparingDouble((Map<String, Object> item) -> numberValue(item.get("score"), 0.0d))
                .reversed());
        return sorted.size() <= limit ? sorted : new ArrayList<>(sorted.subList(0, limit));
    }

    private boolean canPersistMessagesWithoutGeneration() {
        return memoryIndex != null && messageManager != null && scopeUserMappingManager != null;
    }

    private static List<MemResult> toMemResults(List<Map<String, Object>> searchData, MemoryType defaultType) {
        if (searchData == null || searchData.isEmpty()) {
            return List.of();
        }
        List<MemResult> results = new ArrayList<>();
        for (Map<String, Object> item : searchData) {
            results.add(new MemResult(
                    new MemInfo(
                            Objects.toString(item.get("id"), ""),
                            Objects.toString(item.get("mem"), ""),
                            memoryTypeFrom(item.get("mem_type"), defaultType),
                            timestampFrom(item.get("timestamp"))
                    ),
                    numberValue(item.get("score"), 0.0d)
            ));
        }
        return results;
    }

    private static MemoryType memoryTypeFrom(Object rawValue, MemoryType fallback) {
        if (rawValue instanceof MemoryType memoryType) {
            return memoryType;
        }
        if (rawValue instanceof String text && !text.isEmpty()) {
            return MemoryType.fromValue(text);
        }
        return fallback == null ? MemoryType.UNKNOWN : fallback;
    }

    private static ZonedDateTime timestampFrom(Object rawValue) {
        if (rawValue instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime;
        }
        if (rawValue instanceof String text && !text.isEmpty()) {
            try {
                return ZonedDateTime.parse(text);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static double numberValue(Object rawValue, double fallback) {
        return rawValue instanceof Number number ? number.doubleValue() : fallback;
    }

    private void emitSearchFinished(String scopeId, String userId, String query, int resultCount, String searchType) {
        MEMORY_LOGGER.debug(
                "Memory search finished. event={}, scope_id={}, user_id={}, query={}, result_count={}, search_type={}",
                MemoryEvents.MEMORY_SEARCH_FINISHED,
                scopeId,
                userId,
                query,
                resultCount,
                searchType
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    private static RuntimeException memoryError(StatusCode status, Throwable cause, String... pairs) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (pairs != null) {
            for (int index = 0; index + 1 < pairs.length; index += 2) {
                params.put(pairs[index], pairs[index + 1]);
            }
        }
        return ErrorHelper.buildError(status, null, null, cause, params);
    }

    private static <T> T join(CompletionStage<T> stage) {
        try {
            return stage.toCompletableFuture().join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    @FunctionalInterface
    private interface LockBody<T> {
        T execute();
    }

    private record CheckMessagesResult(boolean hasHumanMessage, List<BaseMessage> messages) {
    }
}
