/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.evaluator_pipeline.adapters.agents;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental parity tests for the JiuWenSwarm evaluator adapter.
 *
 * <p>Mirrors Python's
 * {@code tests.unit_tests.agent_evolving.evaluator.evaluator_pipeline.adapters.agents.test_jiuwenswarm} in
 * {@code tests/unit_tests/agent_evolving/evaluator/evaluator_pipeline/adapters/agents/test_jiuwenswarm.py}.</p>
 */
class JiuWenSwarmAgentPythonParityTest {

    private static final String SOURCE =
            "tests/unit_tests/agent_evolving/evaluator/evaluator_pipeline/adapters/agents/test_jiuwenswarm.py";

    @TestFactory
    Collection<DynamicTest> pythonJiuWenSwarmCases() {
        return List.of(
                caseOf("TestJiuWenSwarmAgentInit::test_default_init",
                        JiuWenSwarmAgentPythonParityTest::defaultInit),
                caseOf("TestJiuWenSwarmAgentInit::test_init_with_config",
                        JiuWenSwarmAgentPythonParityTest::initWithConfig),
                caseOf("TestJiuWenSwarmAgentInit::test_name_staticmethod",
                        JiuWenSwarmAgentPythonParityTest::nameStaticMethod),
                caseOf("TestJiuWenSwarmAgentInit::test_supported_skills_modes",
                        JiuWenSwarmAgentPythonParityTest::supportedSkillsModes),
                caseOf("TestJiuWenSwarmAgentInit::test_default_model",
                        JiuWenSwarmAgentPythonParityTest::defaultModel),
                caseOf("TestJiuWenSwarmAgentValidateConfig::test_validate_config_empty",
                        JiuWenSwarmAgentPythonParityTest::validateConfigEmpty),
                caseOf("TestJiuWenSwarmAgentValidateConfig::test_validate_config_complete",
                        JiuWenSwarmAgentPythonParityTest::validateConfigComplete),
                caseOf("TestJiuWenSwarmAgentGetSourceFiles::test_get_source_files_git_mode",
                        JiuWenSwarmAgentPythonParityTest::getSourceFilesGitMode),
                caseOf("TestJiuWenSwarmAgentGetSourceFiles::test_get_source_files_pypi_mode",
                        JiuWenSwarmAgentPythonParityTest::getSourceFilesPypiMode),
                caseOf("TestJiuWenSwarmAgentGetSourceFiles::test_get_source_files_local_mode_not_found",
                        JiuWenSwarmAgentPythonParityTest::getSourceFilesLocalModeNotFound),
                caseOf("TestJiuWenSwarmAgentGetSourceFiles::test_get_source_files_auto_mode",
                        JiuWenSwarmAgentPythonParityTest::getSourceFilesAutoMode),
                caseOf("TestJiuWenSwarmAgentSkillContext::test_set_skill_context",
                        JiuWenSwarmAgentPythonParityTest::setSkillContext),
                caseOf("TestJiuWenSwarmAgentConstants::test_class_constants",
                        JiuWenSwarmAgentPythonParityTest::classConstants)
        );
    }

    private static DynamicTest caseOf(String pythonNode, Executable executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, executable);
    }

    private static void defaultInit() {
        JiuWenSwarmAgent agent = new JiuWenSwarmAgent();

        assertThat(agent.getConfig()).isEmpty();
        assertThat(privateField(agent, "resolvedSkillName")).isEqualTo("");
        assertThat(privateField(agent, "allSkillNames")).isEqualTo(List.of());
    }

    private static void initWithConfig() {
        Map<String, Object> config = Map.of("model_name", "gpt-4", "api_key", "test-key");
        JiuWenSwarmAgent agent = new JiuWenSwarmAgent(config);

        assertThat(agent.getConfig()).isEqualTo(config);
    }

    private static void nameStaticMethod() {
        assertThat(new JiuWenSwarmAgent().name()).isEqualTo("jiuwenswarm");
    }

    private static void supportedSkillsModes() {
        List<String> modes = new JiuWenSwarmAgent().supportedSkillsModes();

        assertThat(modes).contains("create", "read", "evolve");
    }

    private static void defaultModel() {
        assertThat(new JiuWenSwarmAgent().defaultModel()).isEqualTo("glm-5");
        assertThat(new JiuWenSwarmAgent(Map.of("model_name", "gpt-4")).defaultModel()).isEqualTo("gpt-4");
    }

    private static void validateConfigEmpty() {
        List<String> errors = new JiuWenSwarmAgent().validateConfig();

        assertThat(errors).hasSize(2);
        assertThat(errors.get(0)).contains("api_key");
        assertThat(errors.get(1)).contains("api_base");
    }

    private static void validateConfigComplete() {
        JiuWenSwarmAgent agent = new JiuWenSwarmAgent(Map.of(
                "api_key", "test-key",
                "api_base", "https://api.example.com"
        ));

        assertThat(agent.validateConfig()).isEmpty();
    }

    private static void getSourceFilesGitMode() {
        Map<String, Object> result = new JiuWenSwarmAgent(Map.of("install_mode", "git")).getSourceFiles();

        assertThat(result).containsEntry("mode", "git").containsEntry("requires_git", true);
        assertThat((List<?>) result.get("packages")).hasSize(1);
        assertThat(String.valueOf(((List<?>) result.get("packages")).get(0))).contains("git+");
    }

    private static void getSourceFilesPypiMode() {
        Map<String, Object> result = new JiuWenSwarmAgent(Map.of("install_mode", "pypi")).getSourceFiles();

        assertThat(result).containsEntry("mode", "pypi");
        assertThat(result.get("packages")).isEqualTo(List.of("jiuwenswarm"));
    }

    private static void getSourceFilesLocalModeNotFound() {
        Map<String, Object> result = new JiuWenSwarmAgent(Map.of("install_mode", "local")).getSourceFiles();

        assertThat(result).containsEntry("mode", "git");
    }

    private static void getSourceFilesAutoMode() {
        Map<String, Object> result = new JiuWenSwarmAgent(Map.of("install_mode", "auto")).getSourceFiles();

        assertThat(result.get("mode")).isIn("local", "git");
    }

    private static void setSkillContext() {
        JiuWenSwarmAgent agent = new JiuWenSwarmAgent();
        agent.setSkillContext("skill1", List.of("skill1", "skill2"));

        assertThat(privateField(agent, "resolvedSkillName")).isEqualTo("skill1");
        assertThat(privateField(agent, "allSkillNames")).isEqualTo(List.of("skill1", "skill2"));
    }

    private static void classConstants() {
        assertThat(JiuWenSwarmAgent.SKILL_DIR).isEqualTo("/root/.jiuwenswarm/agent/workspace/skills");
        assertThat(JiuWenSwarmAgent.CONFIG_DIR).isEqualTo("/root/.jiuwenswarm/config");
        assertThat(JiuWenSwarmAgent.WORKSPACE_DIR).isEqualTo("/workspace");
    }

    private static Object privateField(JiuWenSwarmAgent agent, String name) {
        try {
            Field field = JiuWenSwarmAgent.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(agent);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to read field " + name, exception);
        }
    }
}
