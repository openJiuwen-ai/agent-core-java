/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * Mirrors Python's {@code MessageMetadata} in
 * {@code openjiuwen/core/foundation/store/base_message_store.py}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageMetadata {

    @JsonProperty("message_id")
    private String messageId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("scope_id")
    private String scopeId;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("timestamp")
    private ZonedDateTime timestamp;

    @JsonProperty("message_type")
    private String messageType;
}
