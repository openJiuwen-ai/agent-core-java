/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Public class Message used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message {
    private MessageType type;
    private Object payload;

    @Builder.Default
    /**
     * Instant.now.
     * 
     * @since 0.1.7
     */
    private String timestamp = Instant.now().toString();

    @JsonProperty("message_id")
    @Builder.Default
    /**
     * UUID.randomUUID.
     * 
     * @since 0.1.7
     */
    private String messageId = UUID.randomUUID().toString();
}
