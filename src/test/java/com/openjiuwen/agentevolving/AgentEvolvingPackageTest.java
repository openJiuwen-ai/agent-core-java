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
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.agent_evolving} package facade in
 * {@code openjiuwen/agent_evolving/__init__.py}.
 */
class AgentEvolvingPackageTest {

    @Test
    void exposesPythonModulePathAndAllSymbolsInOrder() {
        assertEquals("openjiuwen/agent_evolving/__init__.py", AgentEvolvingPackage.PYTHON_MODULE);
        assertEquals(
                List.of(
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
                ),
                AgentEvolvingPackage.all()
        );
        assertTrue(AgentEvolvingPackage.exports("TuneConstant"));
        assertTrue(AgentEvolvingPackage.exports("from_evaluated_cases"));
        assertFalse(AgentEvolvingPackage.exports("missing"));
    }

    @Test
    void resolvesExportedTypesAndFunctionOwners() {
        assertSame(TuneConstant.class, AgentEvolvingPackage.getAttribute("TuneConstant"));
        assertSame(EvolveCheckpoint.class, AgentEvolvingPackage.getAttribute("EvolveCheckpoint"));
        assertSame(FileCheckpointStore.class, AgentEvolvingPackage.getAttribute("FileCheckpointStore"));
        assertSame(DefaultCheckpointManager.class, AgentEvolvingPackage.getAttribute("DefaultCheckpointManager"));
        assertSame(CheckpointManager.class, AgentEvolvingPackage.getAttribute("CheckpointManager"));
        assertSame(Case.class, AgentEvolvingPackage.getAttribute("Case"));
        assertSame(EvaluatedCase.class, AgentEvolvingPackage.getAttribute("EvaluatedCase"));
        assertSame(CaseLoader.class, AgentEvolvingPackage.getAttribute("CaseLoader"));
        assertSame(BaseEvaluator.class, AgentEvolvingPackage.getAttribute("BaseEvaluator"));
        assertSame(DefaultEvaluator.class, AgentEvolvingPackage.getAttribute("DefaultEvaluator"));
        assertSame(MetricEvaluator.class, AgentEvolvingPackage.getAttribute("MetricEvaluator"));
        assertSame(Metric.class, AgentEvolvingPackage.getAttribute("Metric"));
        assertSame(ExactMatchMetric.class, AgentEvolvingPackage.getAttribute("ExactMatchMetric"));
        assertSame(LLMAsJudgeMetric.class, AgentEvolvingPackage.getAttribute("LLMAsJudgeMetric"));
        assertSame(BaseOptimizer.class, AgentEvolvingPackage.getAttribute("BaseOptimizer"));
        assertSame(TextualParameter.class, AgentEvolvingPackage.getAttribute("TextualParameter"));
        assertSame(InstructionOptimizer.class, AgentEvolvingPackage.getAttribute("InstructionOptimizer"));
        assertSame(SkillExperienceOptimizer.class, AgentEvolvingPackage.getAttribute("SkillExperienceOptimizer"));
        assertSame(Trainer.class, AgentEvolvingPackage.getAttribute("Trainer"));
        assertSame(Progress.class, AgentEvolvingPackage.getAttribute("Progress"));
        assertSame(Callbacks.class, AgentEvolvingPackage.getAttribute("Callbacks"));
        assertSame(Trajectory.class, AgentEvolvingPackage.getAttribute("Trajectory"));
        assertSame(TrajectoryStep.class, AgentEvolvingPackage.getAttribute("TrajectoryStep"));
        assertSame(UpdateKey.class, AgentEvolvingPackage.getAttribute("UpdateKey"));
        assertSame(Updates.class, AgentEvolvingPackage.getAttribute("Updates"));
        assertSame(TracerTrajectoryExtractor.class, AgentEvolvingPackage.getAttribute("TracerTrajectoryExtractor"));
        assertSame(Updater.class, AgentEvolvingPackage.getAttribute("Updater"));
        assertSame(SingleDimUpdater.class, AgentEvolvingPackage.getAttribute("SingleDimUpdater"));
        assertSame(MultiDimUpdater.class, AgentEvolvingPackage.getAttribute("MultiDimUpdater"));
        assertSame(RLConfig.class, AgentEvolvingPackage.getAttribute("RLConfig"));
        assertSame(OfflineRLOptimizer.class, AgentEvolvingPackage.getAttribute("OfflineRLOptimizer"));
        assertSame(OnlineRLOptimizer.class, AgentEvolvingPackage.getAttribute("OnlineRLOptimizer"));
        assertSame(RewardRegistry.class, AgentEvolvingPackage.getAttribute("RewardRegistry"));
        assertSame(RLTask.class, AgentEvolvingPackage.getAttribute("RLTask"));
        assertSame(Rollout.class, AgentEvolvingPackage.getAttribute("Rollout"));
        assertSame(RolloutMessage.class, AgentEvolvingPackage.getAttribute("RolloutMessage"));
        assertSame(RolloutWithReward.class, AgentEvolvingPackage.getAttribute("RolloutWithReward"));
        assertSame(ConversationSignalDetector.class, AgentEvolvingPackage.getAttribute("ConversationSignalDetector"));
        assertSame(SignalDetector.class, AgentEvolvingPackage.getAttribute("SignalDetector"));
        assertSame(EvolutionSignal.class, AgentEvolvingPackage.getAttribute("EvolutionSignal"));
        assertSame(EvolutionCategory.class, AgentEvolvingPackage.getAttribute("EvolutionCategory"));
        assertSame(EvolutionTarget.class, AgentEvolvingPackage.getAttribute("EvolutionTarget"));
        assertSame(EvolutionSignals.class, AgentEvolvingPackage.getAttribute("make_signal_fingerprint"));
        assertSame(FromEval.class, AgentEvolvingPackage.getAttribute("from_evaluated_case"));
        assertSame(FromEval.class, AgentEvolvingPackage.getAttribute("from_evaluated_cases"));
        assertEquals(null, AgentEvolvingPackage.typeFor("missing"));
    }

    @Test
    void unknownAttributeUsesPythonModuleErrorShape() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AgentEvolvingPackage.getAttribute("missing")
        );
        assertEquals(
                "module 'openjiuwen.agent_evolving' has no attribute 'missing'",
                exception.getMessage()
        );
    }
}
