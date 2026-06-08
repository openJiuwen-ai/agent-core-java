/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class for message storage, defining a unified message storage interface.
 * <p>
 * Mirrors Python's {@code BaseMessageStore} in
 * {@code openjiuwen/core/foundation/store/base_message_store.py}.
 */
public abstract class BaseMessageStore {

    public abstract CompletableFuture<String> addMessage(Map<String, Object> messageAdd);

    public abstract CompletableFuture<List<String>> addMessages(List<Map<String, Object>> messageAdds);

    public abstract CompletableFuture<Map.Entry<BaseMessage, MessageMetadata>> getMessageById(String messageId);

    public abstract CompletableFuture<List<Map.Entry<BaseMessage, MessageMetadata>>> getMessages(
            Map<String, Object> messageFilter,
            int limit,
            String orderBy,
            String orderDirection
    );

    public abstract CompletableFuture<Boolean> updateMessage(String messageId, Object content);

    public abstract CompletableFuture<Boolean> deleteMessageById(String messageId);

    public abstract CompletableFuture<Integer> deleteMessages(Map<String, Object> messageFilter);

    public abstract CompletableFuture<Integer> countMessages(Map<String, Object> messageFilter);

    public abstract CompletableFuture<Integer> getSchemaVersion();

    public abstract CompletableFuture<Void> setSchemaVersion(int version);
}
