/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.messager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal peer metadata for transport bootstrap.
 *
 * <p>Mirrors Python's {@code MessagerPeerConfig} in
 * {@code openjiuwen.agent_teams.messager.base}.</p>
 */
public class MessagerPeerConfig {

    private String agentId;
    private String peerId;
    private List<String> addrs = new ArrayList<>();
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getPeerId() {
        return peerId;
    }

    public void setPeerId(String peerId) {
        this.peerId = peerId;
    }

    public List<String> getAddrs() {
        return new ArrayList<>(addrs);
    }

    public void setAddrs(List<String> addrs) {
        this.addrs = addrs != null ? new ArrayList<>(addrs) : new ArrayList<>();
    }

    public Map<String, Object> getMetadata() {
        return new LinkedHashMap<>(metadata);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
    }
}
