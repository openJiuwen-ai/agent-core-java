/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.migrator;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.store.BaseMessageStore;
import com.openjiuwen.core.foundation.store.MessageMetadata;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import com.openjiuwen.core.memory.migration.operation.UpdateMessageOperation;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Supplemental parity coverage for message migration.
 *
 * <p>Mirrors Python's {@code TestMessageMigrator} in
 * {@code tests/unit_tests/core/memory/migration/migrator/test_message_migrator.py}.</p>
 */
class MessageMigratorMissingTest {

    @Test
    void tryMigrateEmptyOperations() {
        Fixture fixture = newFixture();

        assertThat(fixture.migrator().tryMigrate(MessageMigrator.MESSAGE_ENTITY_KEY, List.of()).join()).isTrue();
        assertThat(fixture.store().getSchemaVersionCalls).isZero();
    }

    @Test
    void tryMigrateInvalidEntityKey() {
        Fixture fixture = newFixture();
        AtomicInteger calls = new AtomicInteger();

        assertThat(fixture.migrator().tryMigrate("invalid_key", List.of(updateOp(1, calls))).join()).isFalse();
        assertThat(calls).hasValue(0);
    }

    @Test
    void operationsNotAscendingOrder() {
        Fixture fixture = newFixture();

        assertThat(fixture.migrator().tryMigrate(MessageMigrator.MESSAGE_ENTITY_KEY,
                List.of(updateOp(2), updateOp(1))).join()).isFalse();
    }

    @Test
    void operationsWithEqualVersion() {
        Fixture fixture = newFixture();

        assertThat(fixture.migrator().tryMigrate(MessageMigrator.MESSAGE_ENTITY_KEY,
                List.of(updateOp(1), updateOp(1))).join()).isFalse();
    }

    @Test
    void freshStoreExecutesAll() {
        Fixture fixture = newFixture();
        AtomicInteger calls1 = new AtomicInteger();
        AtomicInteger calls2 = new AtomicInteger();

        boolean result = fixture.migrator().tryMigrate(MessageMigrator.MESSAGE_ENTITY_KEY,
                List.of(updateOp(1, calls1), updateOp(2, calls2))).join();

        assertThat(result).isTrue();
        assertThat(calls1).hasValue(1);
        assertThat(calls2).hasValue(1);
        assertThat(fixture.store().setSchemaVersions).containsExactly(2);
    }

    @Test
    void skipAlreadyAppliedOperations() {
        Fixture fixture = newFixture();
        fixture.store().schemaVersion = 2;
        AtomicInteger calls2 = new AtomicInteger();
        AtomicInteger calls3 = new AtomicInteger();

        boolean result = fixture.migrator().tryMigrate(MessageMigrator.MESSAGE_ENTITY_KEY,
                List.of(updateOp(2, calls2), updateOp(3, calls3))).join();

        assertThat(result).isTrue();
        assertThat(calls2).hasValue(0);
        assertThat(calls3).hasValue(1);
        assertThat(fixture.store().setSchemaVersions).containsExactly(3);
    }

    @Test
    void noPendingOperations() {
        Fixture fixture = newFixture();
        fixture.store().schemaVersion = 3;

        boolean result = fixture.migrator().tryMigrate(MessageMigrator.MESSAGE_ENTITY_KEY,
                List.of(updateOp(1), updateOp(2), updateOp(3))).join();

        assertThat(result).isTrue();
        assertThat(fixture.store().setSchemaVersionCalls).isZero();
        assertThat(fixture.store().getMessagesCalls).isZero();
    }

    @Test
    void versionStoredAsInt() {
        Fixture fixture = newFixture();
        fixture.store().schemaVersion = 1;
        AtomicInteger calls = new AtomicInteger();

        boolean result = fixture.migrator().tryMigrate(MessageMigrator.MESSAGE_ENTITY_KEY,
                List.of(updateOp(2, calls))).join();

        assertThat(result).isTrue();
        assertThat(calls).hasValue(1);
    }

    @Test
    void operationErrorReturnsFalse() {
        Fixture fixture = newFixture();

        boolean result = fixture.migrator().tryMigrate(MessageMigrator.MESSAGE_ENTITY_KEY,
                List.of(failingUpdateOp(1, "boom"))).join();

        assertThat(result).isFalse();
    }

    @Test
    void idempotentMigration() {
        Fixture fixture = newFixture();
        fixture.store().schemaVersion = 1;
        AtomicInteger calls = new AtomicInteger();

        boolean result = fixture.migrator().tryMigrate(MessageMigrator.MESSAGE_ENTITY_KEY,
                List.of(updateOp(1, calls))).join();

        assertThat(result).isTrue();
        assertThat(calls).hasValue(0);
    }

    @Test
    void versionUpdateFailureReturnsFalse() {
        Fixture fixture = newFixture();
        fixture.store().setSchemaVersionFailure = new RuntimeException("write failed");
        AtomicInteger calls = new AtomicInteger();

        boolean result = fixture.migrator().tryMigrate(MessageMigrator.MESSAGE_ENTITY_KEY,
                List.of(updateOp(1, calls))).join();

        assertThat(result).isFalse();
        assertThat(calls).hasValue(1);
    }

    @Test
    void multipleOperationsSequential() {
        Fixture fixture = newFixture();
        List<Integer> order = new ArrayList<>();

        boolean result = fixture.migrator().tryMigrate(MessageMigrator.MESSAGE_ENTITY_KEY,
                List.of(updateOp(1, order), updateOp(2, order), updateOp(3, order))).join();

        assertThat(result).isTrue();
        assertThat(order).containsExactly(1, 2, 3);
        assertThat(fixture.store().setSchemaVersions).containsExactly(3);
    }

    @Test
    void partialMigrationOnFailure() {
        Fixture fixture = newFixture();
        AtomicInteger calls1 = new AtomicInteger();
        AtomicInteger calls3 = new AtomicInteger();

        boolean result = fixture.migrator().tryMigrate(MessageMigrator.MESSAGE_ENTITY_KEY,
                List.of(updateOp(1, calls1), failingUpdateOp(2, "failure at v2"), updateOp(3, calls3))).join();

        assertThat(result).isFalse();
        assertThat(calls1).hasValue(1);
        assertThat(calls3).hasValue(0);
        assertThat(fixture.store().setSchemaVersionCalls).isZero();
    }

    @Test
    void unsupportedOperationTypeReturnsFalse() {
        Fixture fixture = newFixture();

        boolean result = fixture.migrator().tryMigrate(MessageMigrator.MESSAGE_ENTITY_KEY,
                List.of(new FakeOperation(1))).join();

        assertThat(result).isFalse();
    }

    @Test
    void singleOperation() {
        Fixture fixture = newFixture();
        AtomicInteger calls = new AtomicInteger();

        boolean result = fixture.migrator().tryMigrate(MessageMigrator.MESSAGE_ENTITY_KEY,
                List.of(updateOp(1, calls))).join();

        assertThat(result).isTrue();
        assertThat(calls).hasValue(1);
    }

    @Test
    void backupCreatedBeforeMigration() {
        Fixture fixture = newFixture();

        boolean result = fixture.migrator().tryMigrate(MessageMigrator.MESSAGE_ENTITY_KEY,
                List.of(updateOp(1))).join();

        assertThat(result).isTrue();
        assertThat(fixture.store().getMessagesCalls).isEqualTo(1);
    }

    @Test
    void restoreOnFailure() {
        Fixture fixture = newFixture();
        fixture.store().messages.add(messageEntry("msg_1", "hello"));

        boolean result = fixture.migrator().tryMigrate(MessageMigrator.MESSAGE_ENTITY_KEY,
                List.of(failingUpdateOp(1, "migration failed"))).join();

        assertThat(result).isFalse();
        assertThat(fixture.store().deleteMessagesCalls).isEqualTo(1);
        assertThat(fixture.store().addMessageCalls).isEqualTo(1);
        assertThat(fixture.store().messages).hasSize(1);
        assertThat(fixture.store().messages.get(0).getKey().getContent()).isEqualTo("hello");
    }

    @Test
    void noRestoreOnSuccess() {
        Fixture fixture = newFixture();

        boolean result = fixture.migrator().tryMigrate(MessageMigrator.MESSAGE_ENTITY_KEY,
                List.of(updateOp(1))).join();

        assertThat(result).isTrue();
        assertThat(fixture.store().deleteMessagesCalls).isZero();
    }

    @Test
    void restoreResetsVersion() {
        Fixture fixture = newFixture();
        fixture.store().schemaVersion = 2;

        boolean result = fixture.migrator().tryMigrate(MessageMigrator.MESSAGE_ENTITY_KEY,
                List.of(failingUpdateOp(3, "v3 failed"))).join();

        assertThat(result).isFalse();
        assertThat(fixture.store().setSchemaVersions).containsExactly(2);
    }

    @Test
    void restoreContinuesOnRestoreError() {
        Fixture fixture = newFixture();
        fixture.store().deleteMessagesFailure = new RuntimeException("delete failed");

        boolean result = fixture.migrator().tryMigrate(MessageMigrator.MESSAGE_ENTITY_KEY,
                List.of(failingUpdateOp(1, "migration failed"))).join();

        assertThat(result).isFalse();
        assertThat(fixture.store().deleteMessagesCalls).isEqualTo(1);
    }

    @Test
    void rollbackResetsVersionToPreMigrationValue() {
        Fixture fixture = newFixture();
        fixture.store().schemaVersion = 2;

        boolean result = fixture.migrator().tryMigrate(MessageMigrator.MESSAGE_ENTITY_KEY,
                List.of(failingUpdateOp(3, "v3 failed"))).join();

        assertThat(result).isFalse();
        assertThat(fixture.store().schemaVersion).isEqualTo(2);
        assertThat(fixture.store().setSchemaVersions).containsExactly(2);
    }

    @Test
    void noVersionResetOnFirstOpFailureNoBackupData() {
        Fixture fixture = newFixture();

        boolean result = fixture.migrator().tryMigrate(MessageMigrator.MESSAGE_ENTITY_KEY,
                List.of(failingUpdateOp(1, "v1 failed"))).join();

        assertThat(result).isFalse();
        assertThat(fixture.store().deleteMessagesCalls).isEqualTo(1);
        assertThat(fixture.store().setSchemaVersionCalls).isZero();
    }

    private static Fixture newFixture() {
        RecordingMessageStore store = new RecordingMessageStore();
        return new Fixture(store, new MessageMigrator(store));
    }

    private static OperationMetadata metadata(int version) {
        return new OperationMetadata(version, "v" + version);
    }

    private static UpdateMessageOperation updateOp(int version) {
        return new UpdateMessageOperation(metadata(version), store -> CompletableFuture.completedFuture(null));
    }

    private static UpdateMessageOperation updateOp(int version, AtomicInteger calls) {
        return new UpdateMessageOperation(metadata(version), store -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        });
    }

    private static UpdateMessageOperation updateOp(int version, List<Integer> order) {
        return new UpdateMessageOperation(metadata(version), store -> {
            order.add(version);
            return CompletableFuture.completedFuture(null);
        });
    }

    private static UpdateMessageOperation failingUpdateOp(int version, String message) {
        return new UpdateMessageOperation(metadata(version),
                store -> CompletableFuture.failedFuture(new RuntimeException(message)));
    }

    private static Map.Entry<BaseMessage, MessageMetadata> messageEntry(String messageId, String content) {
        return new AbstractMap.SimpleEntry<>(
                new BaseMessage("user", content),
                new MessageMetadata(
                        messageId,
                        "u1",
                        "s1",
                        "sess1",
                        ZonedDateTime.parse("2026-06-01T00:00:00Z"),
                        "user"));
    }

    /**
     * Unsupported operation fake.
     *
     * <p>Mirrors Python's {@code make_fake_op} helper in
     * {@code tests/unit_tests/core/memory/migration/migrator/test_message_migrator.py}.</p>
     */
    private static final class FakeOperation extends BaseOperation {
        private FakeOperation(int version) {
            super(metadata(version));
        }
    }

    /**
     * Recording in-memory message store fixture.
     *
     * <p>Mirrors Python's {@code mock_message_store} fixture in
     * {@code tests/unit_tests/core/memory/migration/migrator/test_message_migrator.py}.</p>
     */
    private static final class RecordingMessageStore extends BaseMessageStore {
        private final List<Map.Entry<BaseMessage, MessageMetadata>> messages = new ArrayList<>();
        private final List<Integer> setSchemaVersions = new ArrayList<>();
        private Integer schemaVersion;
        private int addMessageCalls;
        private int getMessagesCalls;
        private int deleteMessagesCalls;
        private int getSchemaVersionCalls;
        private int setSchemaVersionCalls;
        private RuntimeException setSchemaVersionFailure;
        private RuntimeException deleteMessagesFailure;

        @Override
        public CompletableFuture<String> addMessage(Map<String, Object> messageAdd) {
            addMessageCalls++;
            String messageId = "msg_" + addMessageCalls;
            BaseMessage message = (BaseMessage) messageAdd.get("message");
            messages.add(new AbstractMap.SimpleEntry<>(
                    message,
                    new MessageMetadata(
                            messageId,
                            stringValue(messageAdd.get("user_id")),
                            stringValue(messageAdd.get("scope_id")),
                            stringValue(messageAdd.get("session_id")),
                            (ZonedDateTime) messageAdd.get("timestamp"),
                            message == null ? "" : message.getRole())));
            return CompletableFuture.completedFuture(messageId);
        }

        @Override
        public CompletableFuture<List<String>> addMessages(List<Map<String, Object>> messageAdds) {
            CompletableFuture<List<String>> chain = CompletableFuture.completedFuture(new ArrayList<>());
            for (Map<String, Object> messageAdd : messageAdds == null ? List.<Map<String, Object>>of() : messageAdds) {
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
                String orderDirection) {
            getMessagesCalls++;
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
                    entry -> messageId.equals(entry.getValue().getMessageId())));
        }

        @Override
        public CompletableFuture<Integer> deleteMessages(Map<String, Object> messageFilter) {
            deleteMessagesCalls++;
            if (deleteMessagesFailure != null) {
                return CompletableFuture.failedFuture(deleteMessagesFailure);
            }
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
            getSchemaVersionCalls++;
            return CompletableFuture.completedFuture(schemaVersion);
        }

        @Override
        public CompletableFuture<Void> setSchemaVersion(int version) {
            setSchemaVersionCalls++;
            setSchemaVersions.add(version);
            if (setSchemaVersionFailure != null) {
                return CompletableFuture.failedFuture(setSchemaVersionFailure);
            }
            schemaVersion = version;
            return CompletableFuture.completedFuture(null);
        }

        private static String stringValue(Object value) {
            return value == null ? "" : String.valueOf(value);
        }
    }

    private record Fixture(RecordingMessageStore store, MessageMigrator migrator) {
    }
}
