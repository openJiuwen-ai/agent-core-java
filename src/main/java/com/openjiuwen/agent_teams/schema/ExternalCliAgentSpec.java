/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static launch config for an external CLI agent kind.
 *
 * <p>Mirrors Python's {@code ExternalCliAgentSpec} in
 * {@code openjiuwen/agent_teams/schema/team.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExternalCliAgentSpec {

    @JsonProperty("cli_agent")
    private String cliAgent;

    private List<String> command;
    private String cwd;

    @JsonProperty("inject_mcp")
    private boolean injectMcp = true;

    @JsonProperty("mcp_server_command")
    private List<String> mcpServerCommand = new ArrayList<>(List.of("openjiuwen-team-mcp"));

    private Map<String, String> env = new LinkedHashMap<>();

    public String getCliAgent() {
        return cliAgent;
    }

    public void setCliAgent(String cliAgent) {
        this.cliAgent = cliAgent;
    }

    public List<String> getCommand() {
        return command == null ? null : new ArrayList<>(command);
    }

    public void setCommand(List<String> command) {
        this.command = command == null ? null : new ArrayList<>(command);
    }

    public String getCwd() {
        return cwd;
    }

    public void setCwd(String cwd) {
        this.cwd = cwd;
    }

    public boolean isInjectMcp() {
        return injectMcp;
    }

    public void setInjectMcp(boolean injectMcp) {
        this.injectMcp = injectMcp;
    }

    public List<String> getMcpServerCommand() {
        return new ArrayList<>(mcpServerCommand);
    }

    public void setMcpServerCommand(List<String> mcpServerCommand) {
        this.mcpServerCommand = mcpServerCommand == null
                ? new ArrayList<>(List.of("openjiuwen-team-mcp"))
                : new ArrayList<>(mcpServerCommand);
    }

    public Map<String, String> getEnv() {
        return new LinkedHashMap<>(env);
    }

    public void setEnv(Map<String, String> env) {
        this.env = env == null ? new LinkedHashMap<>() : new LinkedHashMap<>(env);
    }
}
