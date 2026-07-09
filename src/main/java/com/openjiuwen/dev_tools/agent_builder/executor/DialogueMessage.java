/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.executor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Public class DialogueMessage used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DialogueMessage {
    private String content;
    private String role;
    private Instant timestamp;

    /**
     * toMap.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, String> toMap() {
        return Map.of("role", role, "content", content);
    }
}
