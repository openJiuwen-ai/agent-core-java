/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.messager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON-safe transport config for messager backends.
 *
 * <p>Mirrors Python's {@code MessagerTransportConfig} in
 * {@code openjiuwen.agent_teams.messager.base}.</p>
 */
public class MessagerTransportConfig {

    private String backend = "inprocess";
    private String teamName = "default";
    private String nodeId;
    private String directAddr;
    private String pubsubPublishAddr;
    private String pubsubSubscribeAddr;
    private List<String> listenAddrs = new ArrayList<>();
    private List<MessagerPeerConfig> bootstrapPeers = new ArrayList<>();
    private List<MessagerPeerConfig> knownPeers = new ArrayList<>();
    private double requestTimeout = 10.0;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getDirectAddr() {
        return directAddr;
    }

    public void setDirectAddr(String directAddr) {
        this.directAddr = directAddr;
    }

    public String getPubsubPublishAddr() {
        return pubsubPublishAddr;
    }

    public void setPubsubPublishAddr(String pubsubPublishAddr) {
        this.pubsubPublishAddr = pubsubPublishAddr;
    }

    public String getPubsubSubscribeAddr() {
        return pubsubSubscribeAddr;
    }

    public void setPubsubSubscribeAddr(String pubsubSubscribeAddr) {
        this.pubsubSubscribeAddr = pubsubSubscribeAddr;
    }

    public List<String> getListenAddrs() {
        return new ArrayList<>(listenAddrs);
    }

    public void setListenAddrs(List<String> listenAddrs) {
        this.listenAddrs = listenAddrs != null ? new ArrayList<>(listenAddrs) : new ArrayList<>();
    }

    public List<MessagerPeerConfig> getBootstrapPeers() {
        return new ArrayList<>(bootstrapPeers);
    }

    public void setBootstrapPeers(List<MessagerPeerConfig> bootstrapPeers) {
        this.bootstrapPeers = bootstrapPeers != null ? new ArrayList<>(bootstrapPeers) : new ArrayList<>();
    }

    public List<MessagerPeerConfig> getKnownPeers() {
        return new ArrayList<>(knownPeers);
    }

    public void setKnownPeers(List<MessagerPeerConfig> knownPeers) {
        this.knownPeers = knownPeers != null ? new ArrayList<>(knownPeers) : new ArrayList<>();
    }

    public double getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(double requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public Map<String, Object> getMetadata() {
        return new LinkedHashMap<>(metadata);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
    }

    public String broadcastTopic() {
        return "team:" + teamName + ":broadcast";
    }
}
