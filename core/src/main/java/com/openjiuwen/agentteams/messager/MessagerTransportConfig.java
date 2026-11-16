/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.messager;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transport settings for team messager initialization and peer discovery.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessagerTransportConfig {
    private static final ObjectMapper COPY_MAPPER = new ObjectMapper();

    @Builder.Default
    private String backend = "inprocess";
    @Builder.Default
    @JsonAlias("team_name")
    private String teamName = "default";
    @Builder.Default
    @JsonAlias("node_id")
    private String nodeId = "";
    @JsonAlias("direct_addr")
    private String directAddr;
    @JsonAlias("pubsub_publish_addr")
    private String pubsubPublishAddr;
    @JsonAlias("pubsub_subscribe_addr")
    private String pubsubSubscribeAddr;
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    @JsonAlias("listen_addrs")
    private List<String> listenAddrs = new ArrayList<>();
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    @JsonAlias("bootstrap_peers")
    private List<MessagerPeerConfig> bootstrapPeers = new ArrayList<>();
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    @JsonAlias("known_peers")
    private List<MessagerPeerConfig> knownPeers = new ArrayList<>();
    @Builder.Default
    @JsonAlias("request_timeout")
    private double requestTimeout = 10.0;
    @Builder.Default
    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> metadata = new LinkedHashMap<>();

    /**
     * Copy JSON-safe transport settings, including peer lists and nested metadata.
     * Runtime socket binding must not mutate a blueprint shared by multiple members.
     *
     * @return an independent configuration
     * @throws IllegalArgumentException if metadata cannot be represented as JSON
     * @since 2026-11-09
     */
    public MessagerTransportConfig copy() {
        return COPY_MAPPER.convertValue(this, MessagerTransportConfig.class);
    }
}
