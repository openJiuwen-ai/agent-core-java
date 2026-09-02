/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.messager;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Subscription descriptor used for tracking topic subscriptions in messager backends.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionHandle {
    private String subscriptionId;
    private String topic;
    private String agentId;
    @Builder.Default
    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> backendMetadata = new LinkedHashMap<>();
}
