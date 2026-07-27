/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.executor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
/**
 * Public class DialogueMessage used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class DialogueMessage {
    private String content;
    private String role;
    private Instant timestamp;

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, String> toMap() {
        return Map.of("role", role, "content", content);
    }
}
