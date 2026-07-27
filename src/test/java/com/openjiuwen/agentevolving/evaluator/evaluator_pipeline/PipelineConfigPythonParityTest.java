/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code TestResolveEnvVars} and {@code TestPipelineConfig} in
 * {@code tests/unit_tests/agent_evolving/evaluator/evaluator_pipeline/test_config.py}.
 */
class PipelineConfigPythonParityTest {

    @TempDir
    Path tempDir;

    @Nested
    class TestResolveEnvVars {

        @Test
        void testResolveEnvVarsReplacesValue() throws ReflectiveOperationException {
            Map.Entry<String, String> env = existingEnv();
            Map<String, Object> cfg = mutableMap("key", "${" + env.getKey() + "}");

            resolveEnvVars(cfg);

            assertEquals(env.getValue(), cfg.get("key"));
        }

        @Test
        void testResolveEnvVarsKeepsNonEnvValues() throws ReflectiveOperationException {
            Map<String, Object> cfg = mutableMap("key", "normal_value", "num", 123);

            resolveEnvVars(cfg);

            assertEquals("normal_value", cfg.get("key"));
            assertEquals(123, cfg.get("num"));
        }

        @Test
        void testResolveEnvVarsHandlesMissingEnv() throws ReflectiveOperationException {
            Map<String, Object> cfg = mutableMap("key", "${NON_EXISTENT_VAR_FOR_PIPELINE_CONFIG_TEST}");

            resolveEnvVars(cfg);

            assertEquals("${NON_EXISTENT_VAR_FOR_PIPELINE_CONFIG_TEST}", cfg.get("key"));
        }

        @Test
        void testResolveEnvVarsNestedDict() throws ReflectiveOperationException {
            Map.Entry<String, String> env = existingEnv();
            Map<String, Object> nested = mutableMap("inner", "${" + env.getKey() + "}");
            Map<String, Object> cfg = mutableMap("outer", nested);

            resolveEnvVars(cfg);

            assertEquals(nested, cfg.get("outer"));
        }
    }

    @Nested
    class TestPipelineConfig {

        @Test
        void testDefaultValues() {
            PipelineConfig config = new PipelineConfig();

            assertEquals("jiuwenswarm", config.getAgent());
            assertEquals("skillsbench", config.getBenchmark());
            assertFalse(config.isEvolutionMode());
            assertEquals(1, config.getMaxIterations());
            assertTrue(config.isConvergenceCheck());
            assertEquals(2, config.getConvergenceThreshold());
            assertEquals(3, config.getStagnationPatience());
            assertEquals(Path.of("./evolution_results"), config.getResultsDir());
            assertTrue(config.isSaveTrajectory());
            assertTrue(config.isSaveSkillHistory());
            assertEquals(Map.of(), config.getAgentConfig());
            assertEquals(Map.of(), config.getBenchConfig());
            assertEquals(List.of(), config.getTaskIds());
            assertEquals("", config.getTasksFilter());
        }

        @Test
        void testCustomValues() {
            PipelineConfig config = new PipelineConfig();
            config.setAgent("custom_agent");
            config.setMaxIterations(5);
            config.setEvolutionMode(true);
            config.setConvergenceThreshold(3);

            assertEquals("custom_agent", config.getAgent());
            assertEquals(5, config.getMaxIterations());
            assertTrue(config.isEvolutionMode());
            assertEquals(3, config.getConvergenceThreshold());
        }

        @Test
        void testFromArgs() {
            PipelineConfig config = PipelineConfig.fromArgs(mutableMap(
                    "agent", "test_agent",
                    "benchmark", "test_bench",
                    "max_iterations", 10));

            assertEquals("test_agent", config.getAgent());
            assertEquals("test_bench", config.getBenchmark());
            assertEquals(10, config.getMaxIterations());
        }

        @Test
        void testFromDict() {
            PipelineConfig config = PipelineConfig.fromDict(mutableMap(
                    "agent", "dict_agent",
                    "max_iterations", 7,
                    "evolution_mode", true,
                    "extra_key", "should_be_ignored"));

            assertEquals("dict_agent", config.getAgent());
            assertEquals(7, config.getMaxIterations());
            assertTrue(config.isEvolutionMode());
            assertThrows(NoSuchFieldException.class, () -> PipelineConfig.class.getDeclaredField("extra_key"));
        }

        @Test
        void testFromYaml() throws Exception {
            String yamlContent = """
                    pipeline:
                      agent: yaml_agent
                      benchmark: yaml_bench
                      max_iterations: 3
                      evolution_mode: true
                      convergence_threshold: 4

                    agent_config:
                      api_key: test_key

                    bench_config:
                      data_path: ./data
                    """;
            Path configFile = tempDir.resolve("test_config.yaml");
            Files.writeString(configFile, yamlContent);

            PipelineConfig config = PipelineConfig.fromYaml(configFile);

            assertEquals("yaml_agent", config.getAgent());
            assertEquals("yaml_bench", config.getBenchmark());
            assertEquals(3, config.getMaxIterations());
            assertTrue(config.isEvolutionMode());
            assertEquals(4, config.getConvergenceThreshold());
            assertEquals(Map.of("api_key", "test_key"), config.getAgentConfig());
            assertEquals(Map.of("data_path", "./data"), config.getBenchConfig());
        }

        @Test
        void testFromYamlWithEnvVar() throws Exception {
            Map.Entry<String, String> env = existingEnv();
            String yamlContent = """
                    pipeline:
                      agent: jiuwenswarm

                    agent_config:
                      api_key: ${%s}
                    """.formatted(env.getKey());
            Path configFile = tempDir.resolve("test_config_env.yaml");
            Files.writeString(configFile, yamlContent);

            PipelineConfig config = PipelineConfig.fromYaml(configFile);

            assertEquals(env.getValue(), config.getAgentConfig().get("api_key"));
        }
    }

    private static void resolveEnvVars(Map<String, Object> cfg) throws ReflectiveOperationException {
        Method method = PipelineConfig.class.getDeclaredMethod("resolveEnvVars", Map.class);
        method.setAccessible(true);
        method.invoke(null, cfg);
    }

    private static Map.Entry<String, String> existingEnv() {
        return System.getenv().entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No non-empty environment variables available"));
    }

    private static Map<String, Object> mutableMap(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < kv.length; index += 2) {
            map.put(String.valueOf(kv[index]), kv[index + 1]);
        }
        return map;
    }
}
