/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.workflow.component.ComponentConfig;

/**
 * Configuration for the long-term memory write workflow component.
 *
 * <p>Mirrors Python's {@code MemoryWriteCompConfig} in
 * {@code openjiuwen/core/workflow/components/resource/memory_write_comp.py}.</p>
 */
public class MemoryWriteCompConfig extends ComponentConfig {

    @JsonProperty("memory")
    private LongTermMemory memory;

    @JsonProperty("scope_id")
    private String scopeId = LongTermMemory.DEFAULT_VALUE;

    @JsonProperty("user_id")
    private String userId = LongTermMemory.DEFAULT_VALUE;

    @JsonProperty("session_id")
    private String sessionId = LongTermMemory.DEFAULT_VALUE;

    @JsonProperty("agent_config")
    private AgentMemoryConfig agentConfig = new AgentMemoryConfig();

    @JsonProperty("gen_mem")
    private boolean genMem = true;

    @JsonProperty("gen_mem_with_history_msg_num")
    private int genMemWithHistoryMsgNum = 2;

    public MemoryWriteCompConfig() {
    }

    public MemoryWriteCompConfig(LongTermMemory memory) {
        this.memory = memory;
    }

    public MemoryWriteCompConfig(LongTermMemory memory, String scopeId, String userId, String sessionId,
                                 AgentMemoryConfig agentConfig, boolean genMem, int genMemWithHistoryMsgNum) {
        this.memory = memory;
        setScopeId(scopeId);
        setUserId(userId);
        setSessionId(sessionId);
        setAgentConfig(agentConfig);
        this.genMem = genMem;
        this.genMemWithHistoryMsgNum = genMemWithHistoryMsgNum;
    }

    public MemoryWriteCompConfig(LongTermMemory memory, String scopeId, String userId, String sessionId,
                                 AgentMemoryConfig agentConfig, boolean genMem, Integer genMemWithHistoryMsgNum) {
        this(memory, scopeId, userId, sessionId, agentConfig, genMem,
                genMemWithHistoryMsgNum == null ? 2 : genMemWithHistoryMsgNum);
    }

    public static Builder builder() {
        return new Builder();
    }

    public LongTermMemory getMemory() {
        return memory;
    }

    public void setMemory(LongTermMemory memory) {
        this.memory = memory;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId == null ? LongTermMemory.DEFAULT_VALUE : scopeId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId == null ? LongTermMemory.DEFAULT_VALUE : userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId == null ? LongTermMemory.DEFAULT_VALUE : sessionId;
    }

    public AgentMemoryConfig getAgentConfig() {
        return agentConfig;
    }

    public void setAgentConfig(AgentMemoryConfig agentConfig) {
        this.agentConfig = agentConfig == null ? new AgentMemoryConfig() : agentConfig;
    }

    public boolean isGenMem() {
        return genMem;
    }

    public void setGenMem(boolean genMem) {
        this.genMem = genMem;
    }

    public int getGenMemWithHistoryMsgNum() {
        return genMemWithHistoryMsgNum;
    }

    public void setGenMemWithHistoryMsgNum(int genMemWithHistoryMsgNum) {
        this.genMemWithHistoryMsgNum = genMemWithHistoryMsgNum;
    }

    public void setGenMemWithHistoryMsgNum(Integer genMemWithHistoryMsgNum) {
        this.genMemWithHistoryMsgNum = genMemWithHistoryMsgNum == null ? 2 : genMemWithHistoryMsgNum;
    }

    /**
     * Builder mirroring Python keyword-only construction.
     */
    public static final class Builder {
        private LongTermMemory memory;
        private String scopeId = LongTermMemory.DEFAULT_VALUE;
        private String userId = LongTermMemory.DEFAULT_VALUE;
        private String sessionId = LongTermMemory.DEFAULT_VALUE;
        private AgentMemoryConfig agentConfig = new AgentMemoryConfig();
        private boolean genMem = true;
        private int genMemWithHistoryMsgNum = 2;

        private Builder() {
        }

        public Builder memory(LongTermMemory memory) {
            this.memory = memory;
            return this;
        }

        public Builder scopeId(String scopeId) {
            this.scopeId = scopeId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder agentConfig(AgentMemoryConfig agentConfig) {
            this.agentConfig = agentConfig;
            return this;
        }

        public Builder genMem(boolean genMem) {
            this.genMem = genMem;
            return this;
        }

        public Builder genMemWithHistoryMsgNum(int genMemWithHistoryMsgNum) {
            this.genMemWithHistoryMsgNum = genMemWithHistoryMsgNum;
            return this;
        }

        public Builder genMemWithHistoryMsgNum(Integer genMemWithHistoryMsgNum) {
            this.genMemWithHistoryMsgNum = genMemWithHistoryMsgNum == null ? 2 : genMemWithHistoryMsgNum;
            return this;
        }

        public MemoryWriteCompConfig build() {
            return new MemoryWriteCompConfig(memory, scopeId, userId, sessionId, agentConfig, genMem,
                    genMemWithHistoryMsgNum);
        }
    }
}
