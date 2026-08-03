/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.store.BaseMessageStore;
import com.openjiuwen.core.foundation.store.MessageMetadata;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.memory.config.MemoryEngineConfig;
import com.openjiuwen.core.memory.manage.index.WriteManager;
import com.openjiuwen.core.memory.manage.mem_model.BaseMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.DataIdManager;
import com.openjiuwen.core.memory.manage.mem_model.FragmentMemoryUnit;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.manage.mem_model.MessageManager;
import com.openjiuwen.core.memory.manage.mem_model.OperationType;
import com.openjiuwen.core.memory.manage.mem_model.ScopeUserMappingManager;
import com.openjiuwen.core.memory.manage.mem_model.SummaryUnit;
import com.openjiuwen.core.memory.manage.mem_model.VariableUnit;
import com.openjiuwen.core.memory.process.extract.Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_add_messages_result_and_proactive_memory} in
 * {@code tests/unit_tests/core/memory/test_add_messages_result_and_proactive_memory.py}.
 */
class LongTermMemoryAddMessagesResultTest {

    private static final ZonedDateTime FIXED_TIME = ZonedDateTime.parse("2026-01-01T00:00:00Z");

    private LongTermMemory memory;
    private RecordingGenerator generator;
    private RecordingWriteManager writeManager;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        memory = new LongTermMemory();
        generator = new RecordingGenerator();
        writeManager = new RecordingWriteManager();
        setField("sysMemConfig", new MemoryEngineConfig());
        setField("kvStore", new InMemoryKVStore());
        setField("messageManager", new MessageManager(new InMemoryMessageStore()));
        setField("scopeUserMappingManager", new RecordingScopeUserMappingManager());
        setField("generator", generator);
        setField("writeManager", writeManager);
        setField("baseLlm", fakeModel());
    }

    @Test
    void normalFlowReturnsAddMemResult() {
        generator.nextMemory = memoryMap(
                variable("name", "Tom"),
                fragment(MemoryType.USER_PROFILE, "profile-1", "user is Tom", OperationType.ADD),
                summary("summary-1", "user introduced himself")
        );

        AddMemResult result = add(defaultMessages(), new AgentMemoryConfig(), true);

        assertEquals(1, result.getVariables().size());
        assertEquals("name", result.getVariables().get(0).getVariableName());
        assertEquals("Tom", result.getVariables().get(0).getVariableMem());
        assertEquals(1, result.getUserProfile().size());
        assertEquals("user is Tom", result.getUserProfile().get(0).getContent());
        assertEquals(1, result.getSummary().size());
    }

    @Test
    void multiRoundIndependentResults() {
        generator.nextMemory = memoryMap(variable("name", "Tom"));
        AddMemResult first = add(List.of(user("I am Tom"), assistant("hello")), new AgentMemoryConfig(), true);

        generator.nextMemory = memoryMap(variable("occupation", "engineer"));
        AddMemResult second = add(List.of(user("I am an engineer"), assistant("ok")), new AgentMemoryConfig(), true);

        assertEquals("name", first.getVariables().get(0).getVariableName());
        assertEquals("occupation", second.getVariables().get(0).getVariableName());
    }

    @Test
    void assistantOnlyReturnsEmpty() {
        AddMemResult result = add(List.of(assistant("hello")), new AgentMemoryConfig(), true);

        assertEmpty(result);
        assertEquals(0, generator.calls.size());
    }

    @Test
    void genMemFalseReturnsEmpty() {
        AddMemResult result = add(defaultMessages(), new AgentMemoryConfig(), false);

        assertEmpty(result);
        assertEquals(0, generator.calls.size());
    }

    @Test
    void emptyScopeIdRaisesError() {
        assertThrows(BaseError.class, () -> add(defaultMessages(), new AgentMemoryConfig(), true, ""));
    }

    @Test
    void scopeIdWithSlashRaisesError() {
        assertThrows(BaseError.class, () -> add(defaultMessages(), new AgentMemoryConfig(), true, "invalid/scope"));
    }

    @Test
    void scopeIdTooLongRaisesError() {
        assertThrows(BaseError.class, () -> add(defaultMessages(), new AgentMemoryConfig(), true, "a".repeat(129)));
    }

    @Test
    void llmNotInitializedRaisesError() throws ReflectiveOperationException {
        setField("baseLlm", null);

        assertThrows(BaseError.class, () -> add(defaultMessages(), new AgentMemoryConfig(), true));
    }

    @Test
    void disableLongTermMemNoFragments() {
        generator.nextMemory = memoryMap(
                variable("name", "Tom"),
                fragment(MemoryType.USER_PROFILE, "profile-1", "user is Tom", OperationType.ADD),
                fragment(MemoryType.SEMANTIC_MEMORY, "semantic-1", "semantic", OperationType.ADD)
        );

        AddMemResult result = add(defaultMessages(), AgentMemoryConfig.builder().enableLongTermMem(false).build(), true);

        assertEquals(1, result.getVariables().size());
        assertTrue(result.getUserProfile().isEmpty());
        assertTrue(result.getSemanticMemory().isEmpty());
    }

    @Test
    void disableSummaryMemory() {
        generator.nextMemory = memoryMap(
                fragment(MemoryType.USER_PROFILE, "profile-1", "user is Tom", OperationType.ADD),
                summary("summary-1", "ignored")
        );

        AddMemResult result = add(defaultMessages(), AgentMemoryConfig.builder().enableSummaryMemory(false).build(), true);

        assertEquals(1, result.getUserProfile().size());
        assertTrue(result.getSummary().isEmpty());
    }

    @Test
    void disableUserProfile() {
        generator.nextMemory = memoryMap(
                fragment(MemoryType.USER_PROFILE, "profile-1", "user is Tom", OperationType.ADD),
                fragment(MemoryType.SEMANTIC_MEMORY, "semantic-1", "semantic", OperationType.ADD)
        );

        AddMemResult result = add(defaultMessages(), AgentMemoryConfig.builder().enableUserProfile(false).build(), true);

        assertTrue(result.getUserProfile().isEmpty());
        assertEquals(1, result.getSemanticMemory().size());
    }

    @Test
    void allMemoryTypesDisabled() {
        generator.nextMemory = memoryMap(
                variable("name", "Tom"),
                fragment(MemoryType.USER_PROFILE, "profile-1", "user is Tom", OperationType.ADD),
                fragment(MemoryType.SEMANTIC_MEMORY, "semantic-1", "semantic", OperationType.ADD),
                fragment(MemoryType.EPISODIC_MEMORY, "episodic-1", "episodic", OperationType.ADD),
                summary("summary-1", "ignored")
        );
        AgentMemoryConfig config = AgentMemoryConfig.builder()
                .enableLongTermMem(false)
                .enableSummaryMemory(false)
                .build();

        AddMemResult result = add(defaultMessages(), config, true);

        assertEquals(1, result.getVariables().size());
        assertTrue(result.getUserProfile().isEmpty());
        assertTrue(result.getSemanticMemory().isEmpty());
        assertTrue(result.getEpisodicMemory().isEmpty());
        assertTrue(result.getSummary().isEmpty());
    }

    @Test
    void variableFieldsContent() {
        generator.nextMemory = memoryMap(variable("name", "Tom"));

        VariableUnit variable = add(defaultMessages(), new AgentMemoryConfig(), true).getVariables().get(0);

        assertEquals("name", variable.getVariableName());
        assertEquals("Tom", variable.getVariableMem());
        assertEquals(MemoryType.VARIABLE, variable.getMemType());
    }

    @Test
    void fragmentMemoryFieldsContent() {
        generator.nextMemory = memoryMap(fragment(MemoryType.USER_PROFILE, "profile-1", "user is Tom", OperationType.ADD));

        FragmentMemoryUnit fragment = add(defaultMessages(), new AgentMemoryConfig(), true).getUserProfile().get(0);

        assertEquals("user is Tom", fragment.getContent());
        assertEquals(MemoryType.USER_PROFILE, fragment.getMemType());
        assertEquals(OperationType.ADD, fragment.getOperationType());
        assertFalse(fragment.getTimestamp().isEmpty());
    }

    @Test
    void summaryFieldsContent() {
        generator.nextMemory = memoryMap(summary("summary-1", "summary text"));

        SummaryUnit summary = add(defaultMessages(), new AgentMemoryConfig(), true).getSummary().get(0);

        assertEquals("summary text", summary.getSummary());
        assertEquals(MemoryType.SUMMARY, summary.getMemType());
        assertFalse(summary.getMessageMemId().isEmpty());
    }

    @Test
    void updateInstructionE2E() {
        generator.nextMemory = memoryMap(fragment(MemoryType.USER_PROFILE, "profile-1", "user is engineer", OperationType.UPDATE));

        AddMemResult result = add(defaultMessages(), new AgentMemoryConfig(), true);

        assertEquals(OperationType.UPDATE, result.getUserProfile().get(0).getOperationType());
        assertTrue(result.getUserProfile().get(0).getContent().contains("engineer"));
    }

    @Test
    void deleteInstructionE2E() {
        generator.nextMemory = memoryMap(fragment(MemoryType.USER_PROFILE, "profile-1", "", OperationType.DELETE));

        AddMemResult result = add(defaultMessages(), new AgentMemoryConfig(), true);

        assertEquals(OperationType.DELETE, result.getUserProfile().get(0).getOperationType());
    }

    @Test
    void emptyMessageListReturnsEmpty() {
        AddMemResult result = add(List.of(), new AgentMemoryConfig(), true);

        assertEmpty(result);
        assertEquals(0, generator.calls.size());
    }

    @Test
    void genAllMemoryReturnsEmptyDict() {
        generator.nextMemory = Map.of();

        AddMemResult result = add(defaultMessages(), new AgentMemoryConfig(), true);

        assertInstanceOf(AddMemResult.class, result);
        assertEmpty(result);
        assertEquals(1, generator.calls.size());
    }

    @Test
    void longContentDoesNotCrash() {
        generator.nextMemory = memoryMap(variable("info", "long data"));

        AddMemResult result = assertDoesNotThrow(() -> add(List.of(user("x".repeat(10_000)), assistant("ok")),
                new AgentMemoryConfig(), true));

        assertEquals(1, result.getVariables().size());
    }

    @Test
    void configPassedToGenAllMemory() {
        AgentMemoryConfig config = AgentMemoryConfig.builder().enableLongTermMem(false).build();

        add(defaultMessages(), config, true);

        assertSame(config, generator.calls.get(generator.calls.size() - 1).get("config"));
    }

    @Test
    void instructMemoriesEmptyList() {
        generator.nextMemory = Map.of();

        AddMemResult result = add(defaultMessages(), new AgentMemoryConfig(), true);

        assertInstanceOf(AddMemResult.class, result);
    }

    @Test
    void updateSemanticValidationFails() {
        generator.nextMemory = memoryMap();

        AddMemResult result = add(defaultMessages(), new AgentMemoryConfig(), true);

        assertTrue(result.getUserProfile().stream()
                .noneMatch(unit -> unit.getOperationType() == OperationType.UPDATE));
    }

    private AddMemResult add(List<BaseMessage> messages, AgentMemoryConfig config, boolean genMem) {
        return add(messages, config, genMem, "scope1");
    }

    private AddMemResult add(List<BaseMessage> messages, AgentMemoryConfig config, boolean genMem, String scopeId) {
        return memory.addMessages(messages, config, "u1", scopeId, "session1", FIXED_TIME, genMem, 2).join();
    }

    private static List<BaseMessage> defaultMessages() {
        return List.of(user("I am Tom"), assistant("hello Tom"));
    }

    private static UserMessage user(String content) {
        return new UserMessage(content);
    }

    private static AssistantMessage assistant(String content) {
        return new AssistantMessage(content);
    }

    private static VariableUnit variable(String name, String value) {
        return new VariableUnit(name, value);
    }

    private static FragmentMemoryUnit fragment(MemoryType type, String id, String content, OperationType operation) {
        return new FragmentMemoryUnit(type, id, content, "msg-1", "2026-01-01 00:00:00", operation);
    }

    private static SummaryUnit summary(String id, String text) {
        return new SummaryUnit(id, text, "msg-1", "2026-01-01 00:00:00");
    }

    private static Map<String, List<BaseMemoryUnit>> memoryMap(BaseMemoryUnit... units) {
        Map<String, List<BaseMemoryUnit>> result = new LinkedHashMap<>();
        for (BaseMemoryUnit unit : units) {
            String key = unit.getMemType().getValue();
            result.computeIfAbsent(key, ignored -> new ArrayList<>()).add(unit);
        }
        return result;
    }

    private static void assertEmpty(AddMemResult result) {
        assertTrue(result.getVariables().isEmpty());
        assertTrue(result.getUserProfile().isEmpty());
        assertTrue(result.getSemanticMemory().isEmpty());
        assertTrue(result.getEpisodicMemory().isEmpty());
        assertTrue(result.getSummary().isEmpty());
    }

    private void setField(String name, Object value) throws ReflectiveOperationException {
        Field field = LongTermMemory.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(memory, value);
    }

    private static Model fakeModel() {
        return new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("[]")));
    }

    private static final class RecordingGenerator extends Generator {
        private Map<String, List<BaseMemoryUnit>> nextMemory = Map.of();
        private final List<Map<String, Object>> calls = new ArrayList<>();

        private RecordingGenerator() {
            super(new DataIdManager());
        }

        @Override
        public CompletionStage<Map<String, List<BaseMemoryUnit>>> genAllMemory(Map<String, Object> kwargs) {
            calls.add(new LinkedHashMap<>(kwargs));
            AgentMemoryConfig config = (AgentMemoryConfig) kwargs.get("config");
            return CompletableFuture.completedFuture(filterByConfig(nextMemory, config));
        }

        private static Map<String, List<BaseMemoryUnit>> filterByConfig(
                Map<String, List<BaseMemoryUnit>> source,
                AgentMemoryConfig config
        ) {
            Map<String, List<BaseMemoryUnit>> result = new LinkedHashMap<>();
            for (Map.Entry<String, List<BaseMemoryUnit>> entry : source.entrySet()) {
                String key = entry.getKey();
                if (MemoryType.SUMMARY.getValue().equals(key) && !config.isEnableSummaryMemory()) {
                    continue;
                }
                if (isFragmentKey(key) && !config.isEnableLongTermMem()) {
                    continue;
                }
                if (MemoryType.USER_PROFILE.getValue().equals(key) && !config.isEnableUserProfile()) {
                    continue;
                }
                if (MemoryType.SEMANTIC_MEMORY.getValue().equals(key) && !config.isEnableSemanticMemory()) {
                    continue;
                }
                if (MemoryType.EPISODIC_MEMORY.getValue().equals(key) && !config.isEnableEpisodicMemory()) {
                    continue;
                }
                result.put(key, List.copyOf(entry.getValue()));
            }
            return result;
        }

        private static boolean isFragmentKey(String key) {
            return MemoryType.USER_PROFILE.getValue().equals(key)
                    || MemoryType.SEMANTIC_MEMORY.getValue().equals(key)
                    || MemoryType.EPISODIC_MEMORY.getValue().equals(key);
        }
    }

    private static final class RecordingWriteManager extends WriteManager {
        private RecordingWriteManager() {
            super(Map.of(), null);
        }

        @Override
        public CompletionStage<List<BaseMemoryUnit>> addMemories(
                String userId,
                String scopeId,
                Map<String, List<BaseMemoryUnit>> memories,
                Model llm
        ) {
            List<BaseMemoryUnit> result = new ArrayList<>();
            for (List<BaseMemoryUnit> units : memories.values()) {
                result.addAll(units);
            }
            return CompletableFuture.completedFuture(result);
        }
    }

    private static final class RecordingScopeUserMappingManager extends ScopeUserMappingManager {
        private RecordingScopeUserMappingManager() {
            super(null);
        }

        @Override
        public CompletableFuture<Void> add(String userId, String scopeId) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class InMemoryMessageStore extends BaseMessageStore {
        private final List<Map.Entry<BaseMessage, MessageMetadata>> rows = new ArrayList<>();

        @Override
        public CompletableFuture<String> addMessage(Map<String, Object> messageAdd) {
            String id = "msg-" + (rows.size() + 1);
            BaseMessage message = (BaseMessage) messageAdd.get("message");
            MessageMetadata metadata = new MessageMetadata(
                    id,
                    (String) messageAdd.get("user_id"),
                    (String) messageAdd.get("scope_id"),
                    (String) messageAdd.get("session_id"),
                    (ZonedDateTime) messageAdd.get("timestamp"),
                    message.getRole()
            );
            rows.add(new AbstractMap.SimpleImmutableEntry<>(message, metadata));
            return CompletableFuture.completedFuture(id);
        }

        @Override
        public CompletableFuture<List<String>> addMessages(List<Map<String, Object>> messageAdds) {
            List<String> ids = new ArrayList<>();
            for (Map<String, Object> messageAdd : messageAdds) {
                ids.add(addMessage(messageAdd).join());
            }
            return CompletableFuture.completedFuture(ids);
        }

        @Override
        public CompletableFuture<Map.Entry<BaseMessage, MessageMetadata>> getMessageById(String messageId) {
            return rows.stream()
                    .filter(row -> messageId.equals(row.getValue().getMessageId()))
                    .findFirst()
                    .map(CompletableFuture::completedFuture)
                    .orElseGet(() -> CompletableFuture.completedFuture(null));
        }

        @Override
        public CompletableFuture<List<Map.Entry<BaseMessage, MessageMetadata>>> getMessages(
                Map<String, Object> messageFilter,
                int limit,
                String orderBy,
                String orderDirection
        ) {
            List<Map.Entry<BaseMessage, MessageMetadata>> filtered = rows.stream()
                    .filter(row -> matches(row.getValue(), messageFilter))
                    .toList();
            List<Map.Entry<BaseMessage, MessageMetadata>> descending = new ArrayList<>(filtered);
            java.util.Collections.reverse(descending);
            return CompletableFuture.completedFuture(descending.subList(0, Math.min(limit, descending.size())));
        }

        @Override
        public CompletableFuture<Boolean> updateMessage(String messageId, Object content) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletableFuture<Boolean> deleteMessageById(String messageId) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletableFuture<Integer> deleteMessages(Map<String, Object> messageFilter) {
            int before = rows.size();
            rows.removeIf(row -> matches(row.getValue(), messageFilter));
            return CompletableFuture.completedFuture(before - rows.size());
        }

        @Override
        public CompletableFuture<Integer> countMessages(Map<String, Object> messageFilter) {
            return CompletableFuture.completedFuture((int) rows.stream()
                    .filter(row -> matches(row.getValue(), messageFilter))
                    .count());
        }

        @Override
        public CompletableFuture<Integer> getSchemaVersion() {
            return CompletableFuture.completedFuture(1);
        }

        @Override
        public CompletableFuture<Void> setSchemaVersion(int version) {
            return CompletableFuture.completedFuture(null);
        }

        private static boolean matches(MessageMetadata metadata, Map<String, Object> filter) {
            if (filter == null || filter.isEmpty()) {
                return true;
            }
            return matchesValue(metadata.getUserId(), filter.get("user_id"))
                    && matchesValue(metadata.getScopeId(), filter.get("scope_id"))
                    && matchesValue(metadata.getSessionId(), filter.get("session_id"));
        }

        private static boolean matchesValue(String actual, Object expected) {
            return expected == null || expected.equals(actual);
        }
    }
}
