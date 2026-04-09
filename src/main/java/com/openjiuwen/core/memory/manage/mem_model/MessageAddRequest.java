  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.memory.manage.mem_model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Request object for adding a message.
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
    private OffsetDateTime timestamp = OffsetDateTime.now(ZoneOffset.UTC);
}
