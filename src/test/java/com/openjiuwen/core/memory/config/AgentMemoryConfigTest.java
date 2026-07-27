package com.openjiuwen.core.memory.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMemoryConfigTest {

    @Test
    void defaultsEnableAllPythonFragmentMemoryTypes() {
        AgentMemoryConfig config = AgentMemoryConfig.builder().build();

        assertTrue(config.isEnableUserProfile());
        assertTrue(config.isEnableSemanticMemory());
        assertTrue(config.isEnableEpisodicMemory());
        assertTrue(config.isEnableFragmentMemory());
    }

    @Test
    void fragmentTypesCanBeDisabledIndependently() {
        AgentMemoryConfig config = AgentMemoryConfig.builder()
                .enableUserProfile(false)
                .enableSemanticMemory(true)
                .enableEpisodicMemory(false)
                .build();

        assertFalse(config.isEnableUserProfile());
        assertTrue(config.isEnableSemanticMemory());
        assertFalse(config.isEnableEpisodicMemory());
    }

    @Test
    void disablingAllFragmentTypesDisablesFragmentMemory() {
        AgentMemoryConfig config = AgentMemoryConfig.builder()
                .enableUserProfile(false)
                .enableSemanticMemory(false)
                .enableEpisodicMemory(false)
                .build();

        assertFalse(config.isEnableFragmentMemory());
    }
}
