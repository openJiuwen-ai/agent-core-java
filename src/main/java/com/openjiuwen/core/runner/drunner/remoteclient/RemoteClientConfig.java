/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remoteclient;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remote client configuration.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemoteClientConfig {
    private String id;

    private String version;

    private String name;

    private String description;

    @Builder.Default
    private ProtocolEnum protocol = ProtocolEnum.MQ;

    private String type;

    private String topic;

    private String url;

    @Builder.Default
    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> kwargs = new LinkedHashMap<>();
}
