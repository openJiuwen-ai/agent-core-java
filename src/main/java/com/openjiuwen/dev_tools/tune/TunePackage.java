/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.single_agent.legacy.config.LlmCallConfig;
import com.openjiuwen.dev_tools.tune.chat_agent.ChatAgent;
import com.openjiuwen.dev_tools.tune.chat_agent.ChatAgentConfig;
import com.openjiuwen.dev_tools.tune.chat_agent.TuneChatAgentPackage;
import com.openjiuwen.dev_tools.tune.dataset.CaseLoader;
import com.openjiuwen.dev_tools.tune.evaluator.DefaultEvaluator;
import com.openjiuwen.dev_tools.tune.optimizer.ExampleOptimizer;
import com.openjiuwen.dev_tools.tune.optimizer.InstructionOptimizer;
import com.openjiuwen.dev_tools.tune.optimizer.JointOptimizer;
import com.openjiuwen.dev_tools.tune.trainer.Trainer;

import java.util.ArrayList;
import java.util.List;

/**
 * Package facade for tune exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.tune} module in
 * {@code openjiuwen/dev_tools/tune/__init__.py}.</p>
 */
public final class TunePackage {
    public static final String PYTHON_MODULE = "openjiuwen/dev_tools/tune/__init__.py";
    public static final List<String> CASE_LOADER_CLASSES = List.of(
            "Case",
            "EvaluatedCase",
            "CaseLoader"
    );
    public static final List<String> OPTIMIZER_CLASSES = List.of(
            "InstructionOptimizer",
            "ExampleOptimizer",
            "JointOptimizer"
    );
    public static final List<String> EVALUATOR_CLASSES = List.of("DefaultEvaluator");
    public static final List<String> TRAINER_CLASSES = List.of("Trainer");
    public static final List<String> CHAT_AGENT_CLASSES_AND_METHODS = List.of(
            "ChatAgent",
            "ChatAgentConfig",
            "create_chat_agent_config",
            "create_chat_agent"
    );
    public static final List<String> ALL = allExports();

    private TunePackage() {
    }

    public static Class<Case> caseClass() {
        return Case.class;
    }

    public static Class<EvaluatedCase> evaluatedCaseClass() {
        return EvaluatedCase.class;
    }

    public static Class<CaseLoader> caseLoaderClass() {
        return CaseLoader.class;
    }

    public static Class<InstructionOptimizer> instructionOptimizerClass() {
        return InstructionOptimizer.class;
    }

    public static Class<ExampleOptimizer> exampleOptimizerClass() {
        return ExampleOptimizer.class;
    }

    public static Class<JointOptimizer> jointOptimizerClass() {
        return JointOptimizer.class;
    }

    public static Class<ChatAgent> chatAgentClass() {
        return ChatAgent.class;
    }

    public static Class<ChatAgentConfig> chatAgentConfigClass() {
        return ChatAgentConfig.class;
    }

    public static Class<DefaultEvaluator> defaultEvaluatorClass() {
        return DefaultEvaluator.class;
    }

    public static Class<Trainer> trainerClass() {
        return Trainer.class;
    }

    public static ChatAgentConfig createChatAgentConfig(String agentId,
                                                       String agentVersion,
                                                       String description,
                                                       LlmCallConfig model) {
        return TuneChatAgentPackage.createChatAgentConfig(agentId, agentVersion, description, model);
    }

    public static ChatAgent createChatAgent(ChatAgentConfig agentConfig) {
        return TuneChatAgentPackage.createChatAgent(agentConfig);
    }

    public static ChatAgent createChatAgent(ChatAgentConfig agentConfig, List<? extends Tool> tools) {
        return TuneChatAgentPackage.createChatAgent(agentConfig, tools);
    }

    private static List<String> allExports() {
        List<String> result = new ArrayList<>();
        result.addAll(CASE_LOADER_CLASSES);
        result.addAll(OPTIMIZER_CLASSES);
        result.addAll(CHAT_AGENT_CLASSES_AND_METHODS);
        result.addAll(EVALUATOR_CLASSES);
        result.addAll(TRAINER_CLASSES);
        return List.copyOf(result);
    }
}
