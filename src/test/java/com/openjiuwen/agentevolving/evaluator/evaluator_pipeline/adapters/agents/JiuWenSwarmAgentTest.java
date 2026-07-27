/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.adapters.agents;

import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.AgentContext;
import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.EvalResult;
import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.IterationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JiuWenSwarmAgentTest {

    @Test
    void validateConfigAndDefaultModelMirrorPythonDefaults() {
        JiuWenSwarmAgent empty = new JiuWenSwarmAgent(Map.of());
        JiuWenSwarmAgent configured = new JiuWenSwarmAgent(Map.of(
                "api_key", "k",
                "api_base", "https://dashscope.example",
                "model_name", "glm-5-air"));

        assertEquals(List.of(
                "api_key is required (set DASHSCOPE_API_KEY or OPENAI_API_KEY)",
                "api_base is required"), empty.validateConfig());
        assertEquals("glm-5", empty.defaultModel());
        assertEquals("glm-5-air", configured.defaultModel());
        assertTrue(configured.validateConfig().isEmpty());
    }

    @Test
    void getSourceFilesSupportsGitAndPypiModes() {
        JiuWenSwarmAgent git = new JiuWenSwarmAgent(Map.of(
                "install_mode", "git",
                "jiuwenswarm_git_url", "https://gitcode.com/example/jiuwenswarm.git@develop"));
        JiuWenSwarmAgent pypi = new JiuWenSwarmAgent(Map.of("install_mode", "pypi"));

        Map<String, Object> gitSource = git.getSourceFiles();
        Map<String, Object> pypiSource = pypi.getSourceFiles();

        assertEquals("git", gitSource.get("mode"));
        assertEquals(List.of("git+https://gitcode.com/example/jiuwenswarm.git@develop"), gitSource.get("packages"));
        assertEquals(true, gitSource.get("requires_git"));
        assertEquals("pypi", pypiSource.get("mode"));
        assertEquals(List.of("jiuwenswarm"), pypiSource.get("packages"));
    }

    @Test
    void parseOutputReturnsPayloadBetweenMarkers() {
        String raw = "noise\n===JIUWENSWARM_OUTPUT_START===\n"
                + "{\"final_response\":\"done\",\"messages\":[{\"content\":\"abcd\"}],\"evolution_events\":[]}\n"
                + "===JIUWENSWARM_OUTPUT_END===\nmore";

        Map<String, Object> parsed = JiuWenSwarmAgent.parseOutput(raw);

        assertNotNull(parsed);
        assertEquals("done", parsed.get("final_response"));
        assertNull(JiuWenSwarmAgent.parseOutput("missing markers"));
    }

    @Test
    void estimateTokensAndBuildSystemMessageIncludeFeedback() {
        JiuWenSwarmAgent agent = new JiuWenSwarmAgent(Map.of("evolution_enabled", true));
        agent.setSkillContext("skill-a", List.of("skill-a", "skill-b"));

        EvalResult evalResult = new EvalResult();
        evalResult.setPassed(false);
        evalResult.setPassRate(0.5);
        evalResult.setFailedTests(List.of("test_a"));
        evalResult.setTestOutput("FAILED test_a - AssertionError: boom");
        IterationResult previous = new IterationResult();
        previous.setEvalResult(evalResult);

        String message = agent.buildSystemMessage(2, true, "Improve timeout handling", previous);
        int tokens = JiuWenSwarmAgent.estimateTokens(List.of(
                Map.of("content", "abcd"),
                Map.of("content", List.of(Map.of("text", "abcdefgh")))));

        assertTrue(message.contains("Evolution Suggestions from Previous Iteration"));
        assertTrue(message.contains("Read ALL Skills Before Solving"));
        assertTrue(message.contains("Improve timeout handling"));
        assertTrue(message.contains("Failed Tests"));
        assertEquals(3, tokens);
    }

    @Test
    void runnerScriptContainsOutputMarkers() {
        String script = JiuWenSwarmAgent.getRunnerScript();
        assertTrue(script.contains("===JIUWENSWARM_OUTPUT_START==="));
        assertTrue(script.contains("===JIUWENSWARM_OUTPUT_END==="));
        assertTrue(script.contains("asyncio.run(_run_agent_async())"));
    }
}
