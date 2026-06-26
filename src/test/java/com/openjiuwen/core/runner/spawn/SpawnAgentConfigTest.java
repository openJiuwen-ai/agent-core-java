/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import com.openjiuwen.core.runner.RunnerConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for spawn agent config DTOs and module helpers.
 *
 * <p>Mirrors Python's {@code SpawnAgentConfig}, {@code ClassAgentSpawnConfig}, and helper functions in
 * {@code openjiuwen/core/runner/spawn/agent_config.py}.</p>
 */
class SpawnAgentConfigTest {

    @Test
    void classAgentConfigDefaultsMatchPythonPydanticModel() {
        ClassAgentSpawnConfig config = new ClassAgentSpawnConfig("tests.mock_agents", "MockSimpleAgent");

        assertEquals(SpawnAgentKind.CLASS_AGENT, config.getAgentKind());
        assertEquals("tests.mock_agents", config.getAgentModule());
        assertEquals("MockSimpleAgent", config.getAgentClass());
        assertEquals(Map.of(), config.getInitKwargs());
        assertEquals(Map.of(), config.getPayload());
    }

    @Test
    void parseClassAgentConfigSelectsSubclassAndPreservesExtraFields() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agent_kind", "class_agent");
        payload.put("agent_module", "tests.unit_tests.core.runner.mock_agents");
        payload.put("agent_class", "MockSimpleAgent");
        payload.put("init_kwargs", Map.of("sleep_time", 0.1d));
        payload.put("payload", Map.of("job", "spawn"));
        payload.put("custom_extra", "kept");

        SpawnAgentConfig parsed = SpawnAgentConfigs.parseSpawnAgentConfig(payload);

        ClassAgentSpawnConfig classConfig = assertInstanceOf(ClassAgentSpawnConfig.class, parsed);
        assertEquals(SpawnAgentKind.CLASS_AGENT, classConfig.getAgentKind());
        assertEquals(Map.of("sleep_time", 0.1d), classConfig.getInitKwargs());
        assertEquals(Map.of("job", "spawn"), classConfig.getPayload());
        assertEquals("kept", classConfig.getExtraFields().get("custom_extra"));
        assertEquals("class_agent", classConfig.toMap().get("agent_kind"));
        assertEquals("MockSimpleAgent", classConfig.toMap().get("agent_class"));
    }

    @Test
    void parseUnknownKindUsesBaseConfigLikePythonFallback() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agent_kind", "team_agent");
        payload.put("session_id", "session-1");
        payload.put("runner_config", Map.of("distributed_mode", false));

        SpawnAgentConfig parsed = SpawnAgentConfigs.parseSpawnAgentConfig(payload);

        assertEquals(SpawnAgentKind.TEAM_AGENT, parsed.getAgentKind());
        assertEquals("session-1", parsed.getSessionId());
        assertEquals(Map.of("distributed_mode", false), parsed.getRunnerConfig());
        assertNull(SpawnPackage.javaSymbolNameFor("SpawnConfig"));
    }

    @Test
    void runnerConfigSerializationRoundTripKeepsJsonFieldNames() {
        RunnerConfig config = RunnerConfig.DEFAULT_RUNNER_CONFIG.copy();
        config.setEnvPrefix("spawn-test");
        config.setEnableA2a(true);

        Map<String, Object> payload = SpawnAgentConfigs.serializeRunnerConfig(config);
        RunnerConfig restored = SpawnAgentConfigs.deserializeRunnerConfig(payload);

        assertEquals("spawn-test", payload.get("env_prefix"));
        assertEquals(true, payload.get("enable_a2a"));
        assertEquals("spawn-test", restored.getEnvPrefix());
        assertTrue(restored.isEnableA2a());
    }
}
