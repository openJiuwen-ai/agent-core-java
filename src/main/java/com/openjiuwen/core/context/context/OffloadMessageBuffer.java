/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Buffer for messages that have been offloaded from the context window.
 * Supports in-memory storage.
 * <p>
 * Mirrors Python's {@code OffloadMessageBuffer} from {@code context_engine/context/message_buffer.py}.
 */
public class OffloadMessageBuffer {

    private Map<String, List<BaseMessage>> inMemoryOffloadMessages;

    public OffloadMessageBuffer() {
        this.inMemoryOffloadMessages = new HashMap<>();
    }

    public OffloadMessageBuffer(Map<String, List<BaseMessage>> initMessages) {
        this.inMemoryOffloadMessages = initMessages != null ? new HashMap<>(initMessages) : new HashMap<>();
    }

    /**
     * Offload messages to the specified storage.
     *
     * @param offloadHandle unique identifier for the offloaded messages
     * @param offloadType   storage type (currently only "in_memory")
     * @param messages      the messages to offload
     */
    public void offload(String offloadHandle, String offloadType, List<BaseMessage> messages) {
        if ("in_memory".equals(offloadType)) {
            inMemoryOffloadMessages.put(offloadHandle, messages);
        }
    }

    /**
     * Reload offloaded messages from storage.
     *
     * @param offloadHandle the handle of the messages to reload
     * @param offloadType   the storage type
     * @return the reloaded messages, or empty list if not found
     */
    public List<BaseMessage> reload(String offloadHandle, String offloadType) {
        if ("in_memory".equals(offloadType)) {
            return inMemoryOffloadMessages.getOrDefault(offloadHandle, new ArrayList<>());
        }
        return new ArrayList<>();
    }

    /**
     * Clear a specific offloaded message set.
     */
    public void clear(String offloadHandle, String offloadType) {
        if ("in_memory".equals(offloadType)) {
            inMemoryOffloadMessages.remove(offloadHandle);
        }
    }

    /**
     * Get all offloaded messages.
     */
    public Map<String, List<BaseMessage>> getAll() {
        return inMemoryOffloadMessages;
    }
}
