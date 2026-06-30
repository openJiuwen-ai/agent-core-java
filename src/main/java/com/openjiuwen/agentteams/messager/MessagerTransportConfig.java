/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.messager;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Transport settings for team messager initialization and peer discovery.
 *
 * @since 1.0
 */
public class MessagerTransportConfig {
    @Builder.Default
    private String backend = "inprocess";
    @Builder.Default
    private String teamName = "default";
    @Builder.Default
    private String nodeId = "";
    private String directAddr;
    private String pubsubPublishAddr;
    private String pubsubSubscribeAddr;
    @Builder.Default
    private List<String> listenAddrs = new ArrayList<>();
    @Builder.Default
    private List<MessagerPeerConfig> bootstrapPeers = new ArrayList<>();
    @Builder.Default
    private List<MessagerPeerConfig> knownPeers = new ArrayList<>();
    @Builder.Default
    private double requestTimeout = 10.0;
    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
