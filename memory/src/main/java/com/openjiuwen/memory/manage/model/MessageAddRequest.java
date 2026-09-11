/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.memory.manage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Request object for adding a message.
 *
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageAddRequest {
    private String userId;
    private String scopeId;
    private String content;
    private String role;
    private String sessionId;
    @Builder.Default
    /**
     * OffsetDateTime.now.
     *
     * @since 0.1.7
     */
    private OffsetDateTime timestamp = OffsetDateTime.now();
}
