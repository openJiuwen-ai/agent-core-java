/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Predefined-member spec for a bridge agent.
 *
 * <p>Mirrors Python's {@code BridgeMemberSpec} in
 * {@code openjiuwen/agent_teams/schema/team.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BridgeMemberSpec extends TeamMemberSpec {

    @JsonProperty("mailbox_inject_mode")
    private BridgeMailboxInjectMode mailboxInjectMode = BridgeMailboxInjectMode.PASSTHROUGH;

    private String protocol = "";

    @JsonProperty("adapter_config")
    private Map<String, Object> adapterConfig = new LinkedHashMap<>();

    public BridgeMemberSpec() {
        setRoleType(TeamRole.BRIDGE_AGENT);
    }

    @Override
    protected TeamRole defaultRoleType() {
        return TeamRole.BRIDGE_AGENT;
    }

    @Override
    protected boolean isRoleAllowed(TeamRole role) {
        return role == TeamRole.BRIDGE_AGENT;
    }

    @Override
    public void setRoleType(TeamRole roleType) {
        TeamRole resolved = roleType == null ? TeamRole.BRIDGE_AGENT : roleType;
        if (resolved != TeamRole.BRIDGE_AGENT) {
            throw new IllegalArgumentException("BridgeMemberSpec.role_type must be bridge_agent");
        }
        super.setRoleType(resolved);
    }

    public BridgeMailboxInjectMode getMailboxInjectMode() {
        return mailboxInjectMode;
    }

    public void setMailboxInjectMode(BridgeMailboxInjectMode mailboxInjectMode) {
        this.mailboxInjectMode = mailboxInjectMode == null
                ? BridgeMailboxInjectMode.PASSTHROUGH
                : mailboxInjectMode;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol == null ? "" : protocol;
    }

    public Map<String, Object> getAdapterConfig() {
        return new LinkedHashMap<>(adapterConfig);
    }

    public void setAdapterConfig(Map<String, Object> adapterConfig) {
        this.adapterConfig = adapterConfig == null ? new LinkedHashMap<>() : new LinkedHashMap<>(adapterConfig);
    }
}
