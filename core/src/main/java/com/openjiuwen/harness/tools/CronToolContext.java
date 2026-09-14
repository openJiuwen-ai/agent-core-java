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

/**
 * Public class CronToolContext used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CronToolContext {
    private String channelId;
    private String sessionId;
    @Builder.Default
    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private String mode;

    /**
     * toolScope.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String toolScope() {
        String channel = channelId != null && !channelId.isBlank() ? channelId : "unknown";
        String session = sessionId != null && !sessionId.isBlank() ? sessionId : "default";
        return channel + ":" + session;
    }
}
