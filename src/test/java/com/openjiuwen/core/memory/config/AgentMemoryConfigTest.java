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
        assertTrue(config.isMemoryTypeEnabled("user_profile"));
        assertTrue(config.isMemoryTypeEnabled("semantic_memory"));
        assertTrue(config.isMemoryTypeEnabled("episodic_memory"));
    }

    @Test
    void fragmentTypesCanBeDisabledIndependently() {
        AgentMemoryConfig config = AgentMemoryConfig.builder()
                .enableUserProfile(false)
                .enableSemanticMemory(true)
                .enableEpisodicMemory(false)
                .build();

        assertFalse(config.isMemoryTypeEnabled("user_profile"));
        assertTrue(config.isMemoryTypeEnabled("semantic_memory"));
        assertFalse(config.isMemoryTypeEnabled("episodic_memory"));
    }

    @Test
    void legacyFragmentSwitchStillDisablesAllFragmentTypes() {
        AgentMemoryConfig config = AgentMemoryConfig.builder()
                .enableFragmentMemory(false)
                .build();

        assertFalse(config.isMemoryTypeEnabled("user_profile"));
        assertFalse(config.isMemoryTypeEnabled("semantic_memory"));
        assertFalse(config.isMemoryTypeEnabled("episodic_memory"));
    }
}
