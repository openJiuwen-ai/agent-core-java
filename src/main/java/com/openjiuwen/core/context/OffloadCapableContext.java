/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.context;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.util.List;

/**
 * Interface for ModelContext implementations that support message offloading.
 * <p>
 * Replaces the {@code hasattr(context, "offload_messages")} duck-typing pattern
 * in Python, allowing any ModelContext subclass to participate in the offload flow
 * without being tied to {@code SessionModelContext}.
 */
public interface OffloadCapableContext {

    /**
     * Offload messages to the in-memory buffer.
     *
     * @param offloadHandle unique identifier for the offloaded messages
     * @param messages      the messages to offload
     */
    void offloadMessages(String offloadHandle, List<BaseMessage> messages);
}
