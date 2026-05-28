/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.workflow.component.ComponentConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Configuration for the Memory Write workflow component.
 * <p>
 * Mirrors Python's {@code MemoryWriteCompConfig}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MemoryWriteCompConfig extends ComponentConfig {

    private LongTermMemory memory;
    private String scopeId = LongTermMemory.DEFAULT_VALUE;
    private String userId = LongTermMemory.DEFAULT_VALUE;
    private String sessionId = LongTermMemory.DEFAULT_VALUE;
    private AgentMemoryConfig agentConfig = new AgentMemoryConfig();
    private Boolean genMem = true;
    private Integer genMemWithHistoryMsgNum = 2;

    public static MemoryWriteCompConfigBuilder builder() {
        return new MemoryWriteCompConfigBuilder();
    }

    public static class MemoryWriteCompConfigBuilder {
        private LongTermMemory memory;
        private String scopeId = LongTermMemory.DEFAULT_VALUE;
        private String userId = LongTermMemory.DEFAULT_VALUE;
        private String sessionId = LongTermMemory.DEFAULT_VALUE;
        private AgentMemoryConfig agentConfig = new AgentMemoryConfig();
        private Boolean genMem = true;
        private Integer genMemWithHistoryMsgNum = 2;

        public MemoryWriteCompConfigBuilder memory(LongTermMemory memory) { this.memory = memory; return this; }
        public MemoryWriteCompConfigBuilder scopeId(String scopeId) { this.scopeId = scopeId; return this; }
        public MemoryWriteCompConfigBuilder userId(String userId) { this.userId = userId; return this; }
        public MemoryWriteCompConfigBuilder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public MemoryWriteCompConfigBuilder agentConfig(AgentMemoryConfig agentConfig) { this.agentConfig = agentConfig; return this; }
        public MemoryWriteCompConfigBuilder genMem(Boolean genMem) { this.genMem = genMem; return this; }
        public MemoryWriteCompConfigBuilder genMemWithHistoryMsgNum(Integer num) { this.genMemWithHistoryMsgNum = num; return this; }

        public MemoryWriteCompConfig build() {
            MemoryWriteCompConfig config = new MemoryWriteCompConfig();
            config.setMemory(memory);
            config.setScopeId(scopeId);
            config.setUserId(userId);
            config.setSessionId(sessionId);
            config.setAgentConfig(agentConfig);
            config.setGenMem(genMem);
            config.setGenMemWithHistoryMsgNum(genMemWithHistoryMsgNum);
            return config;
        }
    }
}