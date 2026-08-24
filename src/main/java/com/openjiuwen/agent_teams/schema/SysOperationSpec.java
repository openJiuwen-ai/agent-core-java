/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializable system operation specification.
 *
 * <p>Mirrors Python's {@code SysOperationSpec} in
 * {@code openjiuwen/agent_teams/schema/deep_agent_spec.py}.</p>
 */
public class SysOperationSpec {

    private String id;
    private String mode = "local";
    private LocalWorkConfig workConfig;
    private SandboxGatewayConfig gatewayConfig;

    public Map<String, Object> buildCard() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", id);
        card.put("mode", mode);
        card.put("work_config", workConfig);
        card.put("gateway_config", gatewayConfig);
        return card;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode == null ? "local" : mode;
    }

    public LocalWorkConfig getWorkConfig() {
        return workConfig;
    }

    public void setWorkConfig(LocalWorkConfig workConfig) {
        this.workConfig = workConfig;
    }

    public SandboxGatewayConfig getGatewayConfig() {
        return gatewayConfig;
    }

    public void setGatewayConfig(SandboxGatewayConfig gatewayConfig) {
        this.gatewayConfig = gatewayConfig;
    }
}
