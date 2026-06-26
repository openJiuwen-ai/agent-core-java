/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune;

import com.openjiuwen.core.single_agent.legacy.config.LlmCallConfig;
import com.openjiuwen.dev_tools.tune.chat_agent.ChatAgent;
import com.openjiuwen.dev_tools.tune.chat_agent.ChatAgentConfig;
import com.openjiuwen.dev_tools.tune.dataset.CaseLoader;
import com.openjiuwen.dev_tools.tune.evaluator.DefaultEvaluator;
import com.openjiuwen.dev_tools.tune.optimizer.ExampleOptimizer;
import com.openjiuwen.dev_tools.tune.optimizer.InstructionOptimizer;
import com.openjiuwen.dev_tools.tune.optimizer.JointOptimizer;
import com.openjiuwen.dev_tools.tune.trainer.Trainer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Mirrors Python's {@code openjiuwen.dev_tools.tune} module in
 * {@code openjiuwen/dev_tools/tune/__init__.py}.
 */
class TunePackageTest {

    @Test
    void exposesPythonModuleAndGroupedAllExportsInPythonOrder() {
        assertEquals("openjiuwen/dev_tools/tune/__init__.py", TunePackage.PYTHON_MODULE);
        assertEquals(List.of("Case", "EvaluatedCase", "CaseLoader"), TunePackage.CASE_LOADER_CLASSES);
        assertEquals(List.of("InstructionOptimizer", "ExampleOptimizer", "JointOptimizer"),
                TunePackage.OPTIMIZER_CLASSES);
        assertEquals(List.of("ChatAgent", "ChatAgentConfig", "create_chat_agent_config", "create_chat_agent"),
                TunePackage.CHAT_AGENT_CLASSES_AND_METHODS);
        assertEquals(List.of("DefaultEvaluator"), TunePackage.EVALUATOR_CLASSES);
        assertEquals(List.of("Trainer"), TunePackage.TRAINER_CLASSES);
        assertEquals(List.of(
                "Case",
                "EvaluatedCase",
                "CaseLoader",
                "InstructionOptimizer",
                "ExampleOptimizer",
                "JointOptimizer",
                "ChatAgent",
                "ChatAgentConfig",
                "create_chat_agent_config",
                "create_chat_agent",
                "DefaultEvaluator",
                "Trainer"
        ), TunePackage.ALL);
    }

    @Test
    void exposesImportedClasses() {
        assertSame(Case.class, TunePackage.caseClass());
        assertSame(EvaluatedCase.class, TunePackage.evaluatedCaseClass());
        assertSame(CaseLoader.class, TunePackage.caseLoaderClass());
        assertSame(InstructionOptimizer.class, TunePackage.instructionOptimizerClass());
        assertSame(ExampleOptimizer.class, TunePackage.exampleOptimizerClass());
        assertSame(JointOptimizer.class, TunePackage.jointOptimizerClass());
        assertSame(ChatAgent.class, TunePackage.chatAgentClass());
        assertSame(ChatAgentConfig.class, TunePackage.chatAgentConfigClass());
        assertSame(DefaultEvaluator.class, TunePackage.defaultEvaluatorClass());
        assertSame(Trainer.class, TunePackage.trainerClass());
    }

    @Test
    void createChatAgentConfigDelegatesToChatAgentFactory() {
        LlmCallConfig model = new LlmCallConfig();

        ChatAgentConfig config = TunePackage.createChatAgentConfig(
                "chat-agent",
                "1.0.0",
                "description",
                model
        );

        assertEquals("chat-agent", config.getId());
        assertEquals("1.0.0", config.getVersion());
        assertEquals("description", config.getDescription());
        assertSame(model, config.getLlmCallConfig());
    }
}
