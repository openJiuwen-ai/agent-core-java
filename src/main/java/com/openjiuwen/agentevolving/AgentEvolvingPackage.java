/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving;

import com.openjiuwen.agentevolving.agent_rl.RewardRegistry;
import com.openjiuwen.agentevolving.agent_rl.config.RLConfig;
import com.openjiuwen.agentevolving.agent_rl.optimizer.OfflineRLOptimizer;
import com.openjiuwen.agentevolving.agent_rl.optimizer.OnlineRLOptimizer;
import com.openjiuwen.agentevolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agentevolving.agent_rl.schemas.Rollout;
import com.openjiuwen.agentevolving.agent_rl.schemas.RolloutMessage;
import com.openjiuwen.agentevolving.agent_rl.schemas.RolloutWithReward;
import com.openjiuwen.agentevolving.checkpointing.CheckpointManager;
import com.openjiuwen.agentevolving.checkpointing.DefaultCheckpointManager;
import com.openjiuwen.agentevolving.checkpointing.EvolveCheckpoint;
import com.openjiuwen.agentevolving.checkpointing.FileCheckpointStore;
import com.openjiuwen.agentevolving.dataset.Case;
import com.openjiuwen.agentevolving.dataset.CaseLoader;
import com.openjiuwen.agentevolving.dataset.EvaluatedCase;
import com.openjiuwen.agentevolving.evaluator.BaseEvaluator;
import com.openjiuwen.agentevolving.evaluator.DefaultEvaluator;
import com.openjiuwen.agentevolving.evaluator.MetricEvaluator;
import com.openjiuwen.agentevolving.evaluator.metrics.ExactMatchMetric;
import com.openjiuwen.agentevolving.evaluator.metrics.LLMAsJudgeMetric;
import com.openjiuwen.agentevolving.evaluator.metrics.Metric;
import com.openjiuwen.agentevolving.optimizer.BaseOptimizer;
import com.openjiuwen.agentevolving.optimizer.TextualParameter;
import com.openjiuwen.agentevolving.optimizer.llm_call.InstructionOptimizer;
import com.openjiuwen.agentevolving.optimizer.skill_call.SkillExperienceOptimizer;
import com.openjiuwen.agentevolving.signal.ConversationSignalDetector;
import com.openjiuwen.agentevolving.signal.EvolutionCategory;
import com.openjiuwen.agentevolving.signal.EvolutionSignal;
import com.openjiuwen.agentevolving.signal.EvolutionSignals;
import com.openjiuwen.agentevolving.signal.EvolutionTarget;
import com.openjiuwen.agentevolving.signal.FromEval;
import com.openjiuwen.agentevolving.signal.SignalDetector;
import com.openjiuwen.agentevolving.trainer.Callbacks;
import com.openjiuwen.agentevolving.trainer.Progress;
import com.openjiuwen.agentevolving.trainer.Trainer;
import com.openjiuwen.agentevolving.trajectory.TracerTrajectoryExtractor;
import com.openjiuwen.agentevolving.trajectory.Trajectory;
import com.openjiuwen.agentevolving.trajectory.TrajectoryStep;
import com.openjiuwen.agentevolving.trajectory.UpdateKey;
import com.openjiuwen.agentevolving.trajectory.Updates;
import com.openjiuwen.agentevolving.updater.MultiDimUpdater;
import com.openjiuwen.agentevolving.updater.SingleDimUpdater;
import com.openjiuwen.agentevolving.updater.Updater;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-level self-evolving package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving} package in
 * {@code openjiuwen/agent_evolving/__init__.py}.</p>
 */
public final class AgentEvolvingPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_evolving/__init__.py";
    public static final String DESCRIPTION = "Self-evolving training and evaluation framework.";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "TuneConstant",
            "EvolveCheckpoint",
            "FileCheckpointStore",
            "DefaultCheckpointManager",
            "CheckpointManager",
            "Case",
            "EvaluatedCase",
            "CaseLoader",
            "BaseEvaluator",
            "DefaultEvaluator",
            "MetricEvaluator",
            "Metric",
            "ExactMatchMetric",
            "LLMAsJudgeMetric",
            "BaseOptimizer",
            "TextualParameter",
            "InstructionOptimizer",
            "SkillExperienceOptimizer",
            "Trainer",
            "Progress",
            "Callbacks",
            "Trajectory",
            "TrajectoryStep",
            "UpdateKey",
            "Updates",
            "TracerTrajectoryExtractor",
            "Updater",
            "SingleDimUpdater",
            "MultiDimUpdater",
            "RLConfig",
            "OfflineRLOptimizer",
            "OnlineRLOptimizer",
            "RewardRegistry",
            "RLTask",
            "Rollout",
            "RolloutMessage",
            "RolloutWithReward",
            "ConversationSignalDetector",
            "SignalDetector",
            "EvolutionSignal",
            "EvolutionCategory",
            "EvolutionTarget",
            "make_signal_fingerprint",
            "from_evaluated_case",
            "from_evaluated_cases"
    );
    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private AgentEvolvingPackage() {
    }

    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    public static boolean exports(String symbolName) {
        return EXPORTED_SYMBOLS.contains(symbolName);
    }

    public static Class<?> typeFor(String exportedName) {
        return EXPORTED_TYPES.get(exportedName);
    }

    public static Class<?> getAttribute(String name) {
        Class<?> exportedType = typeFor(name);
        if (exportedType != null) {
            return exportedType;
        }
        throw new IllegalArgumentException(
                "module 'openjiuwen.agent_evolving' has no attribute '" + name + "'"
        );
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("TuneConstant", TuneConstant.class);
        exports.put("EvolveCheckpoint", EvolveCheckpoint.class);
        exports.put("FileCheckpointStore", FileCheckpointStore.class);
        exports.put("DefaultCheckpointManager", DefaultCheckpointManager.class);
        exports.put("CheckpointManager", CheckpointManager.class);
        exports.put("Case", Case.class);
        exports.put("EvaluatedCase", EvaluatedCase.class);
        exports.put("CaseLoader", CaseLoader.class);
        exports.put("BaseEvaluator", BaseEvaluator.class);
        exports.put("DefaultEvaluator", DefaultEvaluator.class);
        exports.put("MetricEvaluator", MetricEvaluator.class);
        exports.put("Metric", Metric.class);
        exports.put("ExactMatchMetric", ExactMatchMetric.class);
        exports.put("LLMAsJudgeMetric", LLMAsJudgeMetric.class);
        exports.put("BaseOptimizer", BaseOptimizer.class);
        exports.put("TextualParameter", TextualParameter.class);
        exports.put("InstructionOptimizer", InstructionOptimizer.class);
        exports.put("SkillExperienceOptimizer", SkillExperienceOptimizer.class);
        exports.put("Trainer", Trainer.class);
        exports.put("Progress", Progress.class);
        exports.put("Callbacks", Callbacks.class);
        exports.put("Trajectory", Trajectory.class);
        exports.put("TrajectoryStep", TrajectoryStep.class);
        exports.put("UpdateKey", UpdateKey.class);
        exports.put("Updates", Updates.class);
        exports.put("TracerTrajectoryExtractor", TracerTrajectoryExtractor.class);
        exports.put("Updater", Updater.class);
        exports.put("SingleDimUpdater", SingleDimUpdater.class);
        exports.put("MultiDimUpdater", MultiDimUpdater.class);
        exports.put("RLConfig", RLConfig.class);
        exports.put("OfflineRLOptimizer", OfflineRLOptimizer.class);
        exports.put("OnlineRLOptimizer", OnlineRLOptimizer.class);
        exports.put("RewardRegistry", RewardRegistry.class);
        exports.put("RLTask", RLTask.class);
        exports.put("Rollout", Rollout.class);
        exports.put("RolloutMessage", RolloutMessage.class);
        exports.put("RolloutWithReward", RolloutWithReward.class);
        exports.put("ConversationSignalDetector", ConversationSignalDetector.class);
        exports.put("SignalDetector", SignalDetector.class);
        exports.put("EvolutionSignal", EvolutionSignal.class);
        exports.put("EvolutionCategory", EvolutionCategory.class);
        exports.put("EvolutionTarget", EvolutionTarget.class);
        exports.put("make_signal_fingerprint", EvolutionSignals.class);
        exports.put("from_evaluated_case", FromEval.class);
        exports.put("from_evaluated_cases", FromEval.class);
        return Collections.unmodifiableMap(exports);
    }
}
