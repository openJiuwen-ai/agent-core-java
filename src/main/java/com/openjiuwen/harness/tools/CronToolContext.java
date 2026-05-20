/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class CronToolContext used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class CronToolContext {
    private String channelId;
    private String sessionId;
    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private String mode;

    /**
     * Auto-generated for codecheck compliance.
     */
    public String toolScope() {
        String channel = channelId != null && !channelId.isBlank() ? channelId : "unknown";
        String session = sessionId != null && !sessionId.isBlank() ? sessionId : "default";
        return channel + ":" + session;
    }
}
