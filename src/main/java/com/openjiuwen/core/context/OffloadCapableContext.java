/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.util.List;

/**
 * Interface for ModelContext implementations that support message offloading.
 * <p>
 * Replaces the {@code hasattr(context, "offload_messages")} duck-typing pattern
 * in Python, allowing any ModelContext subclass to participate in the offload flow
 * without being tied to {@code SessionModelContext}.
 * 
 * @since 0.1.7
 */
public interface OffloadCapableContext {
    /**
     * offloadMessages.
     * 
     * @param offloadHandle offloadHandle
     * @param messages messages
     * @since 0.1.7
     */
    void offloadMessages(String offloadHandle, List<BaseMessage> messages);
}
