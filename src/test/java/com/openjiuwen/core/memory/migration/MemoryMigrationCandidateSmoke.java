/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.foundation.store.BaseMemoryIndex;
import com.openjiuwen.core.foundation.store.BaseMessageStore;
import com.openjiuwen.core.foundation.store.BasedKVStorePipeline;
import com.openjiuwen.core.foundation.store.MemoryDoc;
import com.openjiuwen.core.foundation.store.MessageMetadata;
import com.openjiuwen.core.foundation.store.StorageCodec;
import com.openjiuwen.core.memory.common.KvPrefixRegistry;
import com.openjiuwen.core.memory.migration.migrator.IndexVersionMigrator;
import com.openjiuwen.core.memory.migration.migrator.KvMigrator;
import com.openjiuwen.core.memory.migration.migrator.MessageMigrator;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.migration.operation.RenameMemoryDocFieldOperation;
import com.openjiuwen.core.memory.migration.operation.UpdateKVOperation;
import com.openjiuwen.core.memory.migration.operation.UpdateMessageOperation;

import java.time.ZonedDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * <p>Mirrors Python's focused memory migration smoke paths across
 * {@code openjiuwen/core/memory/migration/migrator/kv_migrator.py},
 * {@code openjiuwen/core/memory/migration/migrator/message_migrator.py},
 * {@code openjiuwen/core/memory/migration/migrator/index_version_migrator.py}, and
 * {@code openjiuwen/core/memory/migration/run_migrations.py}.</p>
 */
public final class MemoryMigrationCandidateSmoke {

    private MemoryMigrationCandidateSmoke() {
    }

    public static void main(String[] args) {
        smokeKvMigrator();
        smokeMessageMigrator();
        smokeIndexVersionMigrator();
        smokeNoopRunner();
        System.out.println("memory migration candidate smoke passed");
    }

    private static void smokeKvMigrator() {
        InMemoryKvStore store = new InMemoryKvStore();
        KvPrefixRegistry.getInstance().registerCurrent("MEM_");
        store.set("MEM_existing", "old").join();

        UpdateKVOperation operation = new UpdateKVOperation(
                new OperationMetadata(1, "kv smoke"),
                kvStore -> kvStore.set("MEM_new", "new")
        );
        boolean migrated = new KvMigrator(store)
                .tryMigrate(KvMigrator.KV_ENTITY_KEY, List.of(operation))
                .join();

        require(migrated, "KV migration should succeed");
        require("1".equals(store.get(KvMigrator.KV_SCHEMA_VERSION).join()), "KV schema version should update");
        require("new".equals(store.get("MEM_new").join()), "KV update operation should run");
        KvPrefixRegistry.getInstance().unregister("MEM_");
    }

    private static void smokeMessageMigrator() {
        InMemoryMessageStore store = new InMemoryMessageStore();
        store.addMessage(Map.of(
                "message", new BaseMessage("user", "hello"),
                "user_id", "u",
                "scope_id", "s",
                "session_id", "session",
                "timestamp", ZonedDateTime.parse("2026-06-01T00:00:00Z")
        )).join();

        UpdateMessageOperation operation = new UpdateMessageOperation(
                new OperationMetadata(1, "message smoke"),
                messageStore -> messageStore.updateMessage("msg-1", "updated").thenApply(ignored -> null)
        );
        boolean migrated = new MessageMigrator(store)
                .tryMigrate(MessageMigrator.MESSAGE_ENTITY_KEY, List.of(operation))
                .join();

        require(migrated, "Message migration should succeed");
        require(Integer.valueOf(1).equals(store.getSchemaVersion().join()), "Message schema version should update");
        require("updated".equals(store.messages.get(0).getKey().getContent()), "Message content should update");
    }

    private static void smokeIndexVersionMigrator() {
        InMemoryIndex index = new InMemoryIndex();
        MemoryDoc doc = new MemoryDoc(
                "doc-1",
                "text",
                "fragment",
                ZonedDateTime.parse("2026-06-01T00:00:00Z"),
                new LinkedHashMap<>(Map.of("old", "value"))
        );
        index.documents.add(doc);

        RenameMemoryDocFieldOperation operation = new RenameMemoryDocFieldOperation(
                new OperationMetadata(1, "rename"),
                "old",
                "new"
        );
        boolean migrated = new IndexVersionMigrator().tryMigrate(index, List.of(operation)).join();

        require(migrated, "Index migration should succeed");
        require(index.schemaVersion == 1, "Index schema version should update");
        require(index.documents.get(0).getFields().containsKey("new"), "Index document field should be renamed");
    }

    private static void smokeNoopRunner() {
        MigrationPlan.getKvRegistry().clear();
        RunMigrations.runKvMigrations(new InMemoryKvStore()).join();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class InMemoryKvStore extends BaseKVStore {
        private final Map<String, Object> values = new LinkedHashMap<>();

        @Override
        public CompletableFuture<Void> set(String key, Object value) {
            values.put(key, value);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> exclusiveSet(String key, Object value, Integer expiry) {
            if (values.containsKey(key)) {
                return CompletableFuture.completedFuture(false);
            }
            values.put(key, value);
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Object> get(String key) {
            return CompletableFuture.completedFuture(values.get(key));
        }

        @Override
        public CompletableFuture<Boolean> exists(String key) {
            return CompletableFuture.completedFuture(values.containsKey(key));
        }

        @Override
        public CompletableFuture<Void> delete(String key) {
            values.remove(key);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> getByPrefix(String prefix) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletableFuture<Void> deleteByPrefix(String prefix, Integer batchSize) {
            values.keySet().removeIf(key -> key.startsWith(prefix));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<Object>> mget(List<String> keys) {
            return CompletableFuture.completedFuture(keys.stream().map(values::get).toList());
        }

        @Override
        public CompletableFuture<Integer> batchDelete(List<String> keys, Integer batchSize) {
            int deleted = 0;
            for (String key : keys) {
                if (values.remove(key) != null) {
                    deleted++;
                }
            }
            return CompletableFuture.completedFuture(deleted);
        }

        @Override
        public BasedKVStorePipeline pipeline() {
            return new BasedKVStorePipeline(operations -> CompletableFuture.completedFuture(List.of()));
        }
    }

    private static final class InMemoryMessageStore extends BaseMessageStore {
        private final List<Map.Entry<BaseMessage, MessageMetadata>> messages = new ArrayList<>();
        private Integer schemaVersion;

        @Override
        public CompletableFuture<String> addMessage(Map<String, Object> messageAdd) {
            String messageId = "msg-" + (messages.size() + 1);
            BaseMessage message = (BaseMessage) messageAdd.get("message");
            messages.add(new AbstractMap.SimpleEntry<>(
                    message,
                    new MessageMetadata(
                            messageId,
                            stringValue(messageAdd.get("user_id")),
                            stringValue(messageAdd.get("scope_id")),
                            stringValue(messageAdd.get("session_id")),
                            (ZonedDateTime) messageAdd.get("timestamp"),
                            message == null ? "" : message.getRole()
                    )
            ));
            return CompletableFuture.completedFuture(messageId);
        }

        @Override
        public CompletableFuture<List<String>> addMessages(List<Map<String, Object>> messageAdds) {
            CompletableFuture<List<String>> chain = CompletableFuture.completedFuture(new ArrayList<>());
            for (Map<String, Object> messageAdd : messageAdds) {
                chain = chain.thenCompose(ids -> addMessage(messageAdd).thenApply(id -> {
                    ids.add(id);
                    return ids;
                }));
            }
            return chain.thenApply(List::copyOf);
        }

        @Override
        public CompletableFuture<Map.Entry<BaseMessage, MessageMetadata>> getMessageById(String messageId) {
            return CompletableFuture.completedFuture(messages.stream()
                    .filter(entry -> messageId.equals(entry.getValue().getMessageId()))
                    .findFirst()
                    .orElse(null));
        }

        @Override
        public CompletableFuture<List<Map.Entry<BaseMessage, MessageMetadata>>> getMessages(
                Map<String, Object> messageFilter,
                int limit,
                String orderBy,
                String orderDirection
        ) {
            return CompletableFuture.completedFuture(new ArrayList<>(messages));
        }

        @Override
        public CompletableFuture<Boolean> updateMessage(String messageId, Object content) {
            for (Map.Entry<BaseMessage, MessageMetadata> entry : messages) {
                if (messageId.equals(entry.getValue().getMessageId())) {
                    entry.getKey().setContent(content);
                    return CompletableFuture.completedFuture(true);
                }
            }
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletableFuture<Boolean> deleteMessageById(String messageId) {
            return CompletableFuture.completedFuture(messages.removeIf(
                    entry -> messageId.equals(entry.getValue().getMessageId())
            ));
        }

        @Override
        public CompletableFuture<Integer> deleteMessages(Map<String, Object> messageFilter) {
            int count = messages.size();
            messages.clear();
            return CompletableFuture.completedFuture(count);
        }

        @Override
        public CompletableFuture<Integer> countMessages(Map<String, Object> messageFilter) {
            return CompletableFuture.completedFuture(messages.size());
        }

        @Override
        public CompletableFuture<Integer> getSchemaVersion() {
            return CompletableFuture.completedFuture(schemaVersion);
        }

        @Override
        public CompletableFuture<Void> setSchemaVersion(int version) {
            schemaVersion = version;
            return CompletableFuture.completedFuture(null);
        }

        private static String stringValue(Object value) {
            return value == null ? "" : String.valueOf(value);
        }
    }

    private static final class InMemoryIndex extends BaseMemoryIndex {
        private final List<MemoryDoc> documents = new ArrayList<>();
        private int schemaVersion;
        private List<MemoryDoc> backup = List.of();

        @Override
        public void setStorageCodec(StorageCodec codec) {
        }

        @Override
        public CompletableFuture<Void> addMemories(String userId, String scopeId, List<MemoryDoc> memories) {
            documents.addAll(memories);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> updateMemories(String userId, String scopeId, List<MemoryDoc> memories) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteMemories(String userId, String scopeId, List<String> ids) {
            documents.removeIf(document -> ids.contains(document.getId()));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteByUser(String userId) {
            documents.clear();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteByScope(String scopeId) {
            documents.clear();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteByUserAndScope(String userId, String scopeId) {
            documents.clear();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<MemorySearchResult>> search(
                String userId,
                String scopeId,
                String query,
                List<String> memTypes,
                int topK
        ) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<MemoryDoc> getById(String userId, String scopeId, String memId) {
            return CompletableFuture.completedFuture(documents.stream()
                    .filter(document -> memId.equals(document.getId()))
                    .findFirst()
                    .orElse(null));
        }

        @Override
        public CompletableFuture<List<MemoryDoc>> listMemories(
                String userId,
                String scopeId,
                int offset,
                int limit,
                List<String> memTypes
        ) {
            if (offset >= documents.size()) {
                return CompletableFuture.completedFuture(List.of());
            }
            int end = Math.min(offset + limit, documents.size());
            return CompletableFuture.completedFuture(new ArrayList<>(documents.subList(offset, end)));
        }

        @Override
        public int getSchemaVersion() {
            return schemaVersion;
        }

        @Override
        public void updateSchemaVersion(int version) {
            schemaVersion = version;
        }

        @Override
        public CompletableFuture<String> createBackup() {
            backup = new ArrayList<>(documents);
            return CompletableFuture.completedFuture("backup");
        }

        @Override
        public CompletableFuture<Void> restoreBackup(String backupId) {
            documents.clear();
            documents.addAll(backup);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> cleanupBackup(String backupId) {
            backup = List.of();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<UserScopeKey>> listUserScopes() {
            return CompletableFuture.completedFuture(List.of(new UserScopeKey("u", "s")));
        }
    }
}
