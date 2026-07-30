package com.openjiuwen.core.memory;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.memory.config.MemoryEngineConfig;
import com.openjiuwen.core.memory.config.MemoryScopeConfig;
import com.openjiuwen.core.memory.manage.index.WriteManager;
import com.openjiuwen.core.memory.manage.mem_model.MessageManager;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.manage.mem_model.ScopeUserMappingManager;
import com.openjiuwen.core.memory.manage.search.SearchManager;
import com.openjiuwen.core.memory.common.KvPrefixRegistry;
import com.openjiuwen.core.memory.migration.MigrationPlan;
import com.openjiuwen.core.memory.migration.operation.AddColumnOperation;
import com.openjiuwen.core.memory.migration.operation.AddScalarFieldOperation;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.migration.operation.UpdateKVOperation;
import com.openjiuwen.core.memory.migration.migrator.KvMigrator;
import com.openjiuwen.core.memory.process.extract.Generator;
import com.openjiuwen.core.foundation.store.BaseDbStore;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.embedding.HashEmbedding;
import com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LongTermMemoryTest {

    private static final byte[] TEST_KEY = "1234567890abcdef1234567890123456".getBytes();
    private Map<String, List<BaseOperation>> sqlBackup;
    private Map<String, List<BaseOperation>> vectorBackup;
    private Map<String, List<BaseOperation>> kvBackup;

    @org.junit.jupiter.api.BeforeEach
    void backupMigrationRegistries() {
        sqlBackup = copyOperations(MigrationPlan.getSqlRegistry().getAllOperations());
        vectorBackup = copyOperations(MigrationPlan.getVectorRegistry().getAllOperations());
        kvBackup = copyOperations(MigrationPlan.getKvRegistry().getAllOperations());
        MigrationPlan.getSqlRegistry().clear();
        MigrationPlan.getVectorRegistry().clear();
        MigrationPlan.getKvRegistry().clear();
    }

    @AfterEach
    void tearDown() {
        MigrationPlan.getSqlRegistry().clear();
        MigrationPlan.getSqlRegistry().setOperations(sqlBackup);
        MigrationPlan.getVectorRegistry().clear();
        MigrationPlan.getVectorRegistry().setOperations(vectorBackup);
        MigrationPlan.getKvRegistry().clear();
        MigrationPlan.getKvRegistry().setOperations(kvBackup);
        LongTermMemory.resetInstance();
    }

    @Test
    void setScopeConfigPersistsAndDecryptsConfiguration() {
        LongTermMemory memory = registeredMemory();
        memory.setConfig(MemoryEngineConfig.builder().cryptoKey(TEST_KEY).build());

        MemoryScopeConfig scopeConfig = MemoryScopeConfig.builder()
                .modelCfg(ModelRequestConfig.builder().modelName("test_model").build())
                .modelClientCfg(ModelClientConfig.builder()
                        .clientProvider("DashScope")
                        .apiKey("test_api_key")
                        .apiBase("https://dashscope.aliyuncs.com/api/v1")
                        .build())
                .embeddingCfg(new EmbeddingConfig(
                        "test_embedding_model",
                        "https://dashscope.aliyuncs.com/api/v1",
                        "embedding_key"
                ))
                .build();

        assertTrue(memory.setScopeConfig("test_scope_123", scopeConfig).join());

        MemoryScopeConfig loaded = memory.getScopeConfig("test_scope_123");
        assertNotNull(loaded);
        assertEquals("test_model", loaded.getModelCfg().getModelName());
        assertEquals("DashScope", loaded.getModelClientCfg().getClientProvider());
        assertEquals("test_api_key", loaded.getModelClientCfg().getApiKey());
        assertEquals("embedding_key", loaded.getEmbeddingCfg().getApiKey());

        InMemoryKVStore kvStore =
                new InMemoryKVStore();
        String rawJson = String.valueOf(kvStore.get("memory_scope_config/test_scope_123").join());
        assertTrue(rawJson.contains("test_model"));
        assertTrue(!rawJson.contains("test_api_key"));
        assertTrue(!rawJson.contains("embedding_key"));
    }

    @Test
    void setConfigRejectsInvalidNonEmptyCryptoKeyLength() {
        LongTermMemory memory = registeredMemory();

        BaseError error = assertThrows(BaseError.class,
                () -> memory.setConfig(MemoryEngineConfig.builder().cryptoKey("short-key".getBytes()).build()));

        assertEquals(StatusCode.MEMORY_SET_CONFIG_EXECUTION_ERROR, error.getStatus());
        assertEquals("crypto_key", error.getParams().get("config_type"));
        assertTrue(error.getMessage().contains("32 bytes length"));
    }

    @Test
    void setConfigAcceptsEmptyAnd32ByteCryptoKeys() {
        LongTermMemory memory = registeredMemory();

        assertDoesNotThrow(() -> memory.setConfig(MemoryEngineConfig.builder().cryptoKey(new byte[0]).build()));
        assertDoesNotThrow(() -> memory.setConfig(MemoryEngineConfig.builder().cryptoKey(TEST_KEY).build()));
    }

    @Test
    void getVariablesWithEmptyStringNameReturnsNullMapping() {
        LongTermMemory memory = registeredMemory();

        Map<String, String> result = memory.getVariables("", "user", "scope");

        assertEquals(1, result.size());
        assertTrue(result.containsKey(""));
        assertNull(result.get(""));
    }

    @Test
    @Tag("system-test")
    void addMessagesPassesForbiddenVariablesToGenerator() throws Exception {
        LongTermMemory memory = registeredMemory();
        memory.setConfig(MemoryEngineConfig.builder()
                .cryptoKey(TEST_KEY)
                .forbiddenVariables("手机号,证件号")
                .build());

        Model model = mock(Model.class);
        setField(memory, "baseLlm", Map.entry("test_model", model));

        MessageManager messageManager = mock(MessageManager.class);
        doReturn(CompletableFuture.completedFuture(Collections.emptyList())).when(messageManager).get(any(), any(), any(), any(Integer.class));
        doReturn("m1").when(messageManager).add(any());
        setField(memory, "messageManager", messageManager);

        ScopeUserMappingManager scopeUserMappingManager = mock(ScopeUserMappingManager.class);
        doNothing().when(scopeUserMappingManager).add(any(), any());
        setField(memory, "scopeUserMappingManager", scopeUserMappingManager);

        WriteManager writeManager = mock(WriteManager.class);
        doNothing().when(writeManager).addMemories(any(), any(), any(), any(), any());
        setField(memory, "writeManager", writeManager);

        Generator generator = mock(Generator.class);
        doReturn(Collections.emptyMap()).when(generator).genAllMemory(any());
        setField(memory, "generator", generator);

        memory.addMessages(
                java.util.List.of(new BaseMessage("user", "hello")),
                AgentMemoryConfig.builder().enableLongTermMem(true).build(),
                "user",
                "scope",
                "session"
        ).join();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(generator).genAllMemory(paramsCaptor.capture());
        assertEquals("手机号,证件号", paramsCaptor.getValue().get("forbidden_variables"));
    }

    @Test
    @Tag("system-test")
    void addMessagesUsesSystemDefaultTimezoneWhenTimestampIsMissing() throws Exception {
        LongTermMemory memory = registeredMemory();
        memory.setConfig(MemoryEngineConfig.builder().cryptoKey(TEST_KEY).build());

        Model model = mock(Model.class);
        setField(memory, "baseLlm", Map.entry("test_model", model));

        MessageManager messageManager = mock(MessageManager.class);
        doReturn(CompletableFuture.completedFuture(Collections.emptyList())).when(messageManager).get(any(), any(), any(), any(Integer.class));
        doReturn("m1").when(messageManager).add(any());
        setField(memory, "messageManager", messageManager);

        ScopeUserMappingManager scopeUserMappingManager = mock(ScopeUserMappingManager.class);
        doNothing().when(scopeUserMappingManager).add(any(), any());
        setField(memory, "scopeUserMappingManager", scopeUserMappingManager);

        WriteManager writeManager = mock(WriteManager.class);
        doNothing().when(writeManager).addMemories(any(), any(), any(), any(), any());
        setField(memory, "writeManager", writeManager);

        AtomicReference<String> capturedTimestamp = new AtomicReference<>();
        Generator generator = mock(Generator.class);
        doReturn(Collections.emptyMap()).when(generator).genAllMemory(any());
        org.mockito.Mockito.doAnswer(invocation -> {
            Map<String, Object> params = invocation.getArgument(0);
            capturedTimestamp.set(String.valueOf(params.get("timestamp")));
            return Collections.emptyMap();
        }).when(generator).genAllMemory(any());
        setField(memory, "generator", generator);

        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        try {
            memory.addMessages(
                    java.util.List.of(new BaseMessage("user", "hello")),
                    AgentMemoryConfig.builder().enableLongTermMem(true).build(),
                    "user",
                    "scope",
                    "session"
            ).join();
        } finally {
            TimeZone.setDefault(original);
        }

        assertNotNull(capturedTimestamp.get());
        LocalDateTime parsed = LocalDateTime.parse(capturedTimestamp.get(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        long seconds = Math.abs(java.time.Duration.between(parsed, now).getSeconds());
        assertTrue(seconds < 60, "timestamp should follow system default timezone");
    }

    @Test
    void searchUserMemSearchesOnlyThreeFragmentTypesAndKeepsResultTypes() throws Exception {
        LongTermMemory memory = registeredMemory();
        memory.setConfig(MemoryEngineConfig.builder().cryptoKey(TEST_KEY).build());

        SearchManager searchManager = mock(SearchManager.class);
        doReturn(CompletableFuture.completedFuture(List.of(Map.of(
                "id", "profile-id",
                "mem", "profile",
                "mem_type", MemoryType.USER_PROFILE.getValue(),
                "score", 0.3d
        )))).when(searchManager).search(
                org.mockito.ArgumentMatchers.argThat(params -> MemoryType.USER_PROFILE.getValue().equals(params.getSearchType())),
                any());
        doReturn(CompletableFuture.completedFuture(List.of(Map.of(
                "id", "semantic-id",
                "mem", "semantic",
                "mem_type", MemoryType.SEMANTIC_MEMORY.getValue(),
                "score", 0.9d
        )))).when(searchManager).search(
                org.mockito.ArgumentMatchers.argThat(params -> MemoryType.SEMANTIC_MEMORY.getValue().equals(params.getSearchType())),
                any());
        doReturn(CompletableFuture.completedFuture(List.of(Map.of(
                "id", "episodic-id",
                "mem", "episodic",
                "mem_type", MemoryType.EPISODIC_MEMORY.getValue(),
                "score", 0.6d
        )))).when(searchManager).search(
                org.mockito.ArgumentMatchers.argThat(params -> MemoryType.EPISODIC_MEMORY.getValue().equals(params.getSearchType())),
                any());
        setField(memory, "searchManager", searchManager);

        List<MemResult> results = memory.searchUserMem("query", 2, "user", "scope", 0.0d);

        assertEquals(List.of("semantic-id", "episodic-id"), results.stream()
                .map(result -> result.getMemInfo().getMemId())
                .toList());
        assertEquals(List.of(MemoryType.SEMANTIC_MEMORY, MemoryType.EPISODIC_MEMORY), results.stream()
                .map(result -> result.getMemInfo().getType())
                .toList());
        org.mockito.Mockito.verify(searchManager, org.mockito.Mockito.times(3)).search(any(), any());
    }

    @Test
    void registerStoreRunsSqlMigrations() throws Exception {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        BaseDbStore<DataSource> dbStore = createDbStore(createDataSource());
        MigrationPlan.getSqlRegistry().register("user_message", new AddColumnOperation(
                new OperationMetadata(2, "register store sql migration"),
                "user_message",
                "register_source",
                "STRING",
                true,
                null));

        LongTermMemory.resetInstance();
        LongTermMemory memory = LongTermMemory.getInstance();
        memory.registerStore(
                kvStore,
                new InMemoryVectorStore("memory_test_collection"),
                dbStore,
                new HashEmbedding()
        ).join();

        assertTrue(columnExists(dbStore.getAsyncEngine(), "user_message", "register_source"));
        assertEquals("2", readSchemaVersion(dbStore.getAsyncEngine(), "user_message"));
    }

    @Test
    void registerStoreRunsKvVectorAndSqlMigrationsTogether() throws Exception {
        KvPrefixRegistry.getInstance().registerCurrent("user_message");
        try {
            InMemoryKVStore kvStore = new InMemoryKVStore();
            kvStore.set("user_message:1", "old").join();

            InMemoryVectorStore vectorStore = new InMemoryVectorStore("vector_user_profile");
            String collectionName = "register_scope_user_profile";
            vectorStore.withCollection(collectionName).add(List.of(Map.of(
                    "id", "vec-1",
                    "text", "hello",
                    "vector", List.of(1.0f, 2.0f, 3.0f)
            )), null, Map.of());

            BaseDbStore<DataSource> dbStore = createDbStore(createDataSource());

            MigrationPlan.getKvRegistry().register(KvMigrator.KV_ENTITY_KEY, new UpdateKVOperation(
                    new OperationMetadata(2, "register store kv migration"),
                    store -> store.set("user_message:1", "new")));
            MigrationPlan.getVectorRegistry().register("vector_user_profile", new AddScalarFieldOperation(
                    new OperationMetadata(2, "register store vector migration"),
                    "user_profile",
                    "register_field",
                    "string",
                    "vector_value"));
            MigrationPlan.getSqlRegistry().register("user_message", new AddColumnOperation(
                    new OperationMetadata(2, "register store sql migration"),
                    "user_message",
                    "register_source",
                    "STRING",
                    true,
                    null));

            LongTermMemory.resetInstance();
            LongTermMemory memory = LongTermMemory.getInstance();
            memory.registerStore(
                    kvStore,
                    vectorStore,
                    dbStore,
                    new HashEmbedding()
            ).join();

            assertEquals("new", kvStore.get("user_message:1").join());
            assertEquals("2", kvStore.get(KvMigrator.KV_SCHEMA_VERSION).join());
            assertEquals("vector_value", vectorStore.withCollection(collectionName)
                    .queryByFilters(Map.of("register_field", "vector_value"), 10)
                    .get(0)
                    .getMetadata()
                    .get("register_field"));
            assertTrue(columnExists(dbStore.getAsyncEngine(), "user_message", "register_source"));
            assertEquals("2", readSchemaVersion(dbStore.getAsyncEngine(), "user_message"));
        } finally {
            KvPrefixRegistry.getInstance().unregister("user_message");
        }
    }

    private static LongTermMemory registeredMemory() {
        LongTermMemory.resetInstance();
        LongTermMemory memory = LongTermMemory.getInstance();
        memory.registerStore(
                new InMemoryKVStore(),
                new InMemoryVectorStore("memory_test_collection"),
                createDbStore(createDataSource()),
                new HashEmbedding()
        ).join();
        return memory;
    }

    private static DataSource createDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");
        return dataSource;
    }

    private static BaseDbStore<DataSource> createDbStore(DataSource ds) {
        return new BaseDbStore<>() {
            @Override
            public DataSource getAsyncEngine() {
                return ds;
            }
        };
    }

    private static String readSchemaVersion(DataSource dataSource, String tableName) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT schema_version FROM memory_meta WHERE table_name = '" + tableName + "'")) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private static boolean columnExists(DataSource dataSource, String tableName, String columnName) throws Exception {
        try (Connection connection = dataSource.getConnection();
             ResultSet resultSet = connection.getMetaData().getColumns(
                     null,
                     null,
                     tableName.toUpperCase(),
                     columnName.toUpperCase())) {
            return resultSet.next();
        }
    }

    private static Map<String, List<BaseOperation>> copyOperations(Map<String, List<BaseOperation>> source) {
        Map<String, List<BaseOperation>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, new ArrayList<>(value)));
        return copy;
    }

    private static Object getField(Object target, String fieldName) {
        try {
            Field field = LongTermMemory.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = LongTermMemory.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
