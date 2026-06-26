/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.security.CryptUtils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for memory config models.
 *
 * <p>Mirrors Python's {@code MemoryEngineConfig}, {@code MemoryScopeConfig}, and
 * {@code AgentMemoryConfig} in {@code openjiuwen/core/memory/config/config.py}.</p>
 */
class MemoryConfigModelsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void engineDefaultsAndCryptoValidationMirrorPython() {
        MemoryEngineConfig config = new MemoryEngineConfig();

        assertEquals("", config.getForbiddenVariables());
        assertEquals(8192, config.getInputMsgMaxLen());
        assertArrayEquals(new byte[0], config.getCryptoKey());
        assertEquals(128, config.getSingleTurnHistorySummaryMaxToken());

        config.setCryptoKey(new byte[CryptUtils.AES_KEY_LENGTH]);
        assertEquals(CryptUtils.AES_KEY_LENGTH, config.getCryptoKey().length);

        assertThrows(BaseError.class, () -> config.setCryptoKey(new byte[CryptUtils.AES_KEY_LENGTH - 1]));
        assertThrows(IllegalArgumentException.class, () -> config.setSingleTurnHistorySummaryMaxToken(0));
    }

    @Test
    void scopeDefaultsIncludeZeroOneFourDefinitions() {
        MemoryScopeConfig config = new MemoryScopeConfig();

        assertEquals(MemoryScopeConfig.DEFAULT_USER_PROFILE_DEFINITION, config.getUserProfileDefinition());
        assertEquals(MemoryScopeConfig.DEFAULT_SEMANTIC_MEMORY_DEFINITION, config.getSemanticMemoryDefinition());
        assertEquals(MemoryScopeConfig.DEFAULT_EPISODIC_MEMORY_DEFINITION, config.getEpisodicMemoryDefinition());
    }

    @Test
    void agentDefaultsMirrorPythonSwitchesAndListFactory() {
        AgentMemoryConfig config = new AgentMemoryConfig();

        assertTrue(config.getMemVariables().isEmpty());
        assertTrue(config.isEnableLongTermMem());
        assertTrue(config.isEnableUserProfile());
        assertTrue(config.isEnableSemanticMemory());
        assertTrue(config.isEnableEpisodicMemory());
        assertTrue(config.isEnableSummaryMemory());
        assertTrue(config.isEnableFragmentMemory());

        AgentMemoryConfig disabled = AgentMemoryConfig.builder()
                .enableUserProfile(false)
                .enableSemanticMemory(false)
                .enableEpisodicMemory(false)
                .build();
        assertFalse(disabled.isEnableFragmentMemory());
    }

    @Test
    @SuppressWarnings("unchecked")
    void snakeCaseJsonFieldsArePreserved() throws Exception {
        MemoryEngineConfig engineConfig = MemoryEngineConfig.builder()
                .forbiddenVariables("secret,token")
                .inputMsgMaxLen(42)
                .singleTurnHistorySummaryMaxToken(7)
                .build();

        Map<String, Object> engineJson = MAPPER.convertValue(engineConfig, Map.class);
        assertEquals("secret,token", engineJson.get("forbidden_variables"));
        assertEquals(42, engineJson.get("input_msg_max_len"));
        assertEquals(7, engineJson.get("single_turn_history_summary_max_token"));

        MemoryScopeConfig scopeConfig = new MemoryScopeConfig();
        Map<String, Object> scopeJson = MAPPER.convertValue(scopeConfig, Map.class);
        assertEquals(
                MemoryScopeConfig.DEFAULT_USER_PROFILE_DEFINITION,
                scopeJson.get("user_profile_definition")
        );
        assertEquals(
                MemoryScopeConfig.DEFAULT_SEMANTIC_MEMORY_DEFINITION,
                scopeJson.get("semantic_memory_definition")
        );
        assertEquals(
                MemoryScopeConfig.DEFAULT_EPISODIC_MEMORY_DEFINITION,
                scopeJson.get("episodic_memory_definition")
        );

        AgentMemoryConfig agentConfig = AgentMemoryConfig.builder()
                .memVariables(List.of())
                .enableSummaryMemory(false)
                .build();
        Map<String, Object> agentJson = MAPPER.convertValue(agentConfig, Map.class);
        assertEquals(false, agentJson.get("enable_summary_memory"));
        assertEquals(List.of(), agentJson.get("mem_variables"));
    }
}
