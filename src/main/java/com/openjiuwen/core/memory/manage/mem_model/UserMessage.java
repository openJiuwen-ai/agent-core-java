/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.memory.manage.mem_model;

/**
 * Public row model matching the memory user_message table.
 */
public record UserMessage(
        String messageId,
        String userId,
        String scopeId,
        String content,
        String sessionId,
        String role,
        String timestamp) {
}