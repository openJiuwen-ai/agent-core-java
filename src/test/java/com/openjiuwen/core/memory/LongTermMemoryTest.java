package com.openjiuwen.core.memory;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.memory.config.MemoryEngineConfig;
import com.openjiuwen.core.memory.config.MemoryScopeConfig;
import com.openjiuwen.core.memory.manage.index.WriteManager;
import com.openjiuwen.core.memory.manage.mem_model.MessageManager;
import com.openjiuwen.core.memory.manage.mem_model.ScopeUserMappingManager;
import com.openjiuwen.core.memory.process.extract.Generator;
import com.openjiuwen.core.memory.support.TestDbStore;
import com.openjiuwen.core.memory.support.TestInMemoryKVStore;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.embedding.HashEmbedding;
import com.openjiuwen.core.retrieval.vector_store.InMemoryVectorStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LongTermMemoryTest {

    private static final byte[] TEST_KEY = "1234567890abcdef1234567890123456".getBytes();

    @AfterEach
    void tearDown() {
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

        assertTrue(memory.setScopeConfig("test_scope_123", scopeConfig));

        MemoryScopeConfig loaded = memory.getScopeConfig("test_scope_123");
        assertNotNull(loaded);
        assertEquals("test_model", loaded.getModelCfg().getModelName());
        assertEquals("DashScope", loaded.getModelClientCfg().getClientProvider());
        assertEquals("test_api_key", loaded.getModelClientCfg().getApiKey());
        assertEquals("embedding_key", loaded.getEmbeddingCfg().getApiKey());

        TestInMemoryKVStore kvStore = (TestInMemoryKVStore) getField(memory, "kvStore");
        String rawJson = String.valueOf(kvStore.get("memory_scope_config/test_scope_123"));
        assertTrue(rawJson.contains("test_model"));
        assertTrue(!rawJson.contains("test_api_key"));
        assertTrue(!rawJson.contains("embedding_key"));
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
    void addMessagesUsesSystemDefaultTimezoneWhenTimestampIsMissing() throws Exception {
        LongTermMemory memory = registeredMemory();
        memory.setConfig(MemoryEngineConfig.builder().cryptoKey(TEST_KEY).build());

        Model model = mock(Model.class);
        setField(memory, "baseLlm", Map.entry("test_model", model));

        MessageManager messageManager = mock(MessageManager.class);
        doReturn(Collections.emptyList()).when(messageManager).get(any(), any(), any(), any(Integer.class));
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
                    "session",
                    null,
                    true,
                    2
            );
        } finally {
            TimeZone.setDefault(original);
        }

        assertNotNull(capturedTimestamp.get());
        LocalDateTime parsed = LocalDateTime.parse(capturedTimestamp.get(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        long seconds = Math.abs(java.time.Duration.between(parsed, now).getSeconds());
        assertTrue(seconds < 60, "timestamp should follow system default timezone");
    }

    private static LongTermMemory registeredMemory() {
        LongTermMemory.resetInstance();
        LongTermMemory memory = LongTermMemory.getInstance();
        memory.registerStore(
                new TestInMemoryKVStore(),
                new InMemoryVectorStore("memory_test_collection"),
                new TestDbStore(createDataSource()),
                new HashEmbedding()
        );
        return memory;
    }

    private static DataSource createDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");
        return dataSource;
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
