/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.store.BaseMessageStore;
import com.openjiuwen.core.foundation.store.MessageMetadata;
import com.openjiuwen.core.memory.codec.AesStorageCodec;
import com.openjiuwen.core.memory.migration.migrator.MemoryMetaManager;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * SQL database message storage implementation.
 *
 * <p>Mirrors Python's {@code SqlMessageStore} in
 * {@code openjiuwen/core/memory/manage/mem_model/sql_message_store.py}.</p>
 */
public class SqlMessageStore extends BaseMessageStore {

    public static final String DEFAULT_TABLE_NAME = "user_message";
    public static final int COUNT_QUERY_LIMIT = 1_000_000;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final DateTimeFormatter PYTHON_TIMESTAMP_SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private byte[] cryptoKey;
    private final SqlDbStore sqlDbStore;
    private final String tableName;
    private AesStorageCodec codec;

    public SqlMessageStore(SqlDbStore sqlDbStore) {
        this(null, sqlDbStore, DEFAULT_TABLE_NAME);
    }

    public SqlMessageStore(byte[] cryptoKey, SqlDbStore sqlDbStore) {
        this(cryptoKey, sqlDbStore, DEFAULT_TABLE_NAME);
    }

    public SqlMessageStore(SqlDbStore sqlDbStore, String tableName) {
        this(null, sqlDbStore, tableName);
    }

    public SqlMessageStore(byte[] cryptoKey, SqlDbStore sqlDbStore, String tableName) {
        this.cryptoKey = cryptoKey == null ? null : cryptoKey.clone();
        this.sqlDbStore = sqlDbStore;
        this.tableName = isTruthy(tableName) ? tableName : DEFAULT_TABLE_NAME;
        this.codec = new AesStorageCodec(this.cryptoKey);
    }

    public byte[] getCryptoKey() {
        return cryptoKey == null ? null : cryptoKey.clone();
    }

    public void setCryptoKey(byte[] cryptoKey) {
        this.cryptoKey = cryptoKey == null ? null : cryptoKey.clone();
        this.codec = new AesStorageCodec(this.cryptoKey);
    }

    public SqlDbStore getSqlDbStore() {
        return sqlDbStore;
    }

    public String getTableName() {
        return tableName;
    }

    @Override
    public CompletableFuture<String> addMessage(Map<String, Object> messageAdd) {
        BaseMessage message = (BaseMessage) messageAdd.get("message");
        String userId = textOrEmpty(messageAdd.get("user_id"));
        String scopeId = textOrEmpty(messageAdd.get("scope_id"));
        String sessionId = textOrEmpty(messageAdd.get("session_id"));
        ZonedDateTime timestamp = toZonedDateTime(messageAdd.get("timestamp"));
        if (timestamp == null) {
            timestamp = ZonedDateTime.now();
        }

        String messageId = generateMessageId(message, timestamp);
        String content = encodeContent(message == null ? null : message.getContent());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message_id", messageId);
        data.put("user_id", userId);
        data.put("session_id", sessionId);
        data.put("scope_id", scopeId);
        data.put("role", textOrEmpty(message == null ? null : message.getRole()));
        data.put("content", content);
        data.put("timestamp", timestamp);

        return sqlDbStore.write(tableName, data).thenApply(ignored -> messageId);
    }

    @Override
    public CompletableFuture<List<String>> addMessages(List<Map<String, Object>> messageAdds) {
        CompletableFuture<List<String>> chain = CompletableFuture.completedFuture(new ArrayList<>());
        for (Map<String, Object> messageAdd : messageAdds) {
            chain = chain.thenCompose(ids -> addMessage(messageAdd).thenApply(messageId -> {
                ids.add(messageId);
                return ids;
            }));
        }
        return chain.thenApply(List::copyOf);
    }

    @Override
    public CompletableFuture<Map.Entry<BaseMessage, MessageMetadata>> getMessageById(String messageId) {
        Map<String, List<String>> filters = new LinkedHashMap<>();
        filters.put("message_id", List.of(messageId));
        return sqlDbStore.conditionGet(tableName, filters, null).thenApply(messages -> {
            if (messages == null || messages.isEmpty()) {
                throw ErrorHelper.buildError(
                        StatusCode.MEMORY_GET_MEMORY_EXECUTION_ERROR,
                        "memory_type", "message",
                        "error_msg", "Message with id " + messageId + " not found"
                );
            }
            return toMessageEntry(messages.get(0));
        });
    }

    @Override
    public CompletableFuture<List<Map.Entry<BaseMessage, MessageMetadata>>> getMessages(
            Map<String, Object> messageFilter,
            int limit,
            String orderBy,
            String orderDirection
    ) {
        Map<String, Object> filters = new LinkedHashMap<>();
        if (isTruthy(value(messageFilter, "user_id"))) {
            filters.put("user_id", value(messageFilter, "user_id"));
        }
        if (isTruthy(value(messageFilter, "scope_id"))) {
            filters.put("scope_id", value(messageFilter, "scope_id"));
        }
        if (messageFilter != null && messageFilter.containsKey("session_id")
                && value(messageFilter, "session_id") != null) {
            filters.put("session_id", value(messageFilter, "session_id"));
        }

        return sqlDbStore.getWithSort(tableName, filters, orderBy, orderDirection.toUpperCase(Locale.ROOT), limit)
                .thenApply(messages -> {
                    List<Map.Entry<BaseMessage, MessageMetadata>> result = new ArrayList<>();
                    for (Map<String, Object> messageData : messages) {
                        result.add(toMessageEntry(messageData));
                    }
                    return result;
                });
    }

    @Override
    public CompletableFuture<Boolean> updateMessage(String messageId, Object content) {
        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("message_id", messageId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("content", encodeContent(content));

        return sqlDbStore.update(tableName, conditions, data);
    }

    @Override
    public CompletableFuture<Boolean> deleteMessageById(String messageId) {
        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("message_id", messageId);
        return sqlDbStore.delete(tableName, conditions);
    }

    @Override
    public CompletableFuture<Integer> deleteMessages(Map<String, Object> messageFilter) {
        Map<String, Object> conditions = filterTruthy(messageFilter, false);
        return countMessages(messageFilter).thenCompose(count ->
                sqlDbStore.delete(tableName, conditions).thenApply(ignored -> count));
    }

    @Override
    public CompletableFuture<Integer> countMessages(Map<String, Object> messageFilter) {
        Map<String, Object> filters = filterTruthy(messageFilter, false);
        return sqlDbStore.getWithSort(tableName, filters, "timestamp", "ASC", COUNT_QUERY_LIMIT)
                .thenApply(List::size);
    }

    @Override
    public CompletableFuture<Integer> getSchemaVersion() {
        MemoryMetaManager metaManager = new MemoryMetaManager(sqlDbStore);
        return metaManager.getByTableName(tableName).thenApply(result -> {
            if (result == null || result.isEmpty()) {
                return null;
            }
            Object version = result.get(0).get("schema_version");
            if (!isTruthy(version)) {
                return null;
            }
            return Integer.parseInt(String.valueOf(version));
        });
    }

    @Override
    public CompletableFuture<Void> setSchemaVersion(int version) {
        MemoryMetaManager metaManager = new MemoryMetaManager(sqlDbStore);
        return metaManager.add(tableName, String.valueOf(version));
    }

    private String generateMessageId(BaseMessage message, ZonedDateTime timestamp) {
        Object content = message == null ? null : message.getContent();
        String contentString = toPythonJson(content);
        String messageHash = sha256Hex(contentString + pythonTimestamp(timestamp));
        return "msg_" + messageHash.substring(0, 16) + "_" + timestamp.toInstant().toEpochMilli();
    }

    private Map.Entry<BaseMessage, MessageMetadata> toMessageEntry(Map<String, Object> messageData) {
        Object rawContent = messageData.get("content");
        String decoded = codec.decode(rawContent == null ? null : String.valueOf(rawContent));

        BaseMessage baseMessage = new BaseMessage(
                textOrEmpty(messageData.get("role")),
                decodeContent(decoded)
        );

        MessageMetadata metadata = new MessageMetadata(
                textOrEmpty(messageData.get("message_id")),
                textOrEmpty(messageData.get("user_id")),
                textOrEmpty(messageData.get("scope_id")),
                textOrEmpty(messageData.get("session_id")),
                toZonedDateTime(messageData.get("timestamp")),
                textOrEmpty(messageData.get("role"))
        );

        return new AbstractMap.SimpleImmutableEntry<>(baseMessage, metadata);
    }

    private String encodeContent(Object content) {
        String text = content instanceof String value ? value : toPythonJson(content);
        return codec.encode(text);
    }

    private static Object decodeContent(String decoded) {
        if (decoded == null || decoded.isEmpty()) {
            return decoded;
        }
        String trimmed = decoded.stripLeading();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return decoded;
        }
        try {
            return OBJECT_MAPPER.readValue(decoded, Object.class);
        } catch (JsonProcessingException ignored) {
            return decoded;
        }
    }

    private static Map<String, Object> filterTruthy(Map<String, Object> messageFilter, boolean includeEmptySession) {
        Map<String, Object> filters = new LinkedHashMap<>();
        if (isTruthy(value(messageFilter, "user_id"))) {
            filters.put("user_id", value(messageFilter, "user_id"));
        }
        if (isTruthy(value(messageFilter, "scope_id"))) {
            filters.put("scope_id", value(messageFilter, "scope_id"));
        }
        Object sessionId = value(messageFilter, "session_id");
        if (includeEmptySession ? sessionId != null : isTruthy(sessionId)) {
            filters.put("session_id", sessionId);
        }
        return filters;
    }

    private static Object value(Map<String, Object> values, String key) {
        return values == null ? null : values.get(key);
    }

    private static String textOrEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof CharSequence text) {
            return !text.isEmpty();
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0D;
        }
        if (value instanceof Iterable<?> iterable) {
            return iterable.iterator().hasNext();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) > 0;
        }
        return true;
    }

    private static ZonedDateTime toZonedDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toZonedDateTime();
        }
        if (value instanceof Instant instant) {
            return instant.atZone(ZoneId.systemDefault());
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault());
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atZone(ZoneId.systemDefault());
        }
        if (value instanceof CharSequence text) {
            return parseTimestamp(text.toString());
        }
        throw new IllegalArgumentException("Unsupported timestamp value: " + value);
    }

    private static ZonedDateTime parseTimestamp(String value) {
        String timestamp = value.trim();
        if (timestamp.isEmpty()) {
            return null;
        }
        List<String> candidates = List.of(timestamp, timestamp.replace(' ', 'T'));
        for (String candidate : candidates) {
            try {
                return ZonedDateTime.parse(candidate);
            } catch (RuntimeException ignored) {
                // Try the next timestamp form.
            }
            try {
                return OffsetDateTime.parse(candidate).toZonedDateTime();
            } catch (RuntimeException ignored) {
                // Try the next timestamp form.
            }
            try {
                return LocalDateTime.parse(candidate).atZone(ZoneId.systemDefault());
            } catch (RuntimeException ignored) {
                // Try the next timestamp form.
            }
        }
        throw new IllegalArgumentException("Unsupported timestamp string: " + value);
    }

    private static String pythonTimestamp(ZonedDateTime timestamp) {
        StringBuilder builder = new StringBuilder();
        builder.append(timestamp.format(PYTHON_TIMESTAMP_SECONDS));
        int microseconds = timestamp.getNano() / 1_000;
        if (microseconds > 0) {
            builder.append('.').append(String.format(Locale.ROOT, "%06d", microseconds));
        }
        String offset = timestamp.getOffset().getId();
        builder.append("Z".equals(offset) ? "+00:00" : offset);
        return builder.toString();
    }

    private static String toPythonJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return quote(text);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            List<String> entries = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                entries.add(quote(String.valueOf(entry.getKey())) + ": " + toPythonJson(entry.getValue()));
            }
            return "{" + String.join(", ", entries) + "}";
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> entries = new ArrayList<>();
            for (Object item : iterable) {
                entries.add(toPythonJson(item));
            }
            return "[" + String.join(", ", entries) + "]";
        }
        if (value.getClass().isArray()) {
            List<String> entries = new ArrayList<>();
            for (int index = 0; index < Array.getLength(value); index++) {
                entries.add(toPythonJson(Array.get(value, index)));
            }
            return "[" + String.join(", ", entries) + "]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return quote(Objects.toString(value));
        }
    }

    private static String quote(String value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append(String.format(Locale.ROOT, "%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
