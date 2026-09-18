// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agentevolving.agent_rl.optimizer;

import com.openjiuwen.agentevolving.agent_rl.DatasetBundle;
import com.openjiuwen.agentevolving.agent_rl.rl_trainer.VerlExecutor;

import java.util.Map;

/**
 * Base task runner state shared by offline and online PPO runners.
 *
 * <p>Mirrors Python's {@code BaseTaskRunner} in
 * {@code openjiuwen/agent_evolving/agent_rl/optimizer/task_runner.py}.</p>
 */
public abstract class BaseTaskRunner {

    protected VerlExecutor verlTrainer;
    protected TaskRunner.ModelComponentRef tokenizer;
    protected TaskRunner.ModelComponentRef processor;
    protected Map<String, Object> config;
    protected boolean initialized;

    protected BaseTaskRunner() {
    }

    public boolean isReady() {
        return initialized;
    }

    public int getGlobalSteps() {
        return verlTrainer == null ? 0 : verlTrainer.getGlobalSteps();
    }

    public VerlExecutor getVerlTrainer() {
        return verlTrainer;
    }

    public TaskRunner.ModelComponentRef getTokenizer() {
        return tokenizer;
    }

    public TaskRunner.ModelComponentRef getProcessor() {
        return processor;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    protected TaskRunner.ModelComponents initModelComponents(Map<String, Object> targetConfig) {
        return TaskRunner.initModelComponents(targetConfig);
    }

    protected TaskRunner.ResourcePoolSpec initResourcePools(Map<String, Object> targetConfig,
                                                            Map<String, String> roleMapping) {
        return TaskRunner.initResourcePools(targetConfig, roleMapping);
    }

    protected RewardFunctions initRewardFunctions(Map<String, Object> targetConfig,
                                                  TaskRunner.ModelComponentRef targetTokenizer,
                                                  RewardManagerLoader loader) {
        if (loader == null) {
            return new RewardFunctions(null, null);
        }
        Map<String, Object> rewardModel = TaskRunner.mapAt(targetConfig, "reward_model");
        Object rewardKwargs = rewardModel.getOrDefault("reward_kwargs", Map.of());
        return new RewardFunctions(
                loader.load(targetConfig, targetTokenizer, 0, rewardKwargs),
                loader.load(targetConfig, targetTokenizer, 1, rewardKwargs)
        );
    }

    protected abstract DatasetBundle initDatasets(Map<String, Object> targetConfig,
                                                  TaskRunner.ModelComponentRef targetTokenizer,
                                                  TaskRunner.ModelComponentRef targetProcessor);

    @FunctionalInterface
    public interface RewardManagerLoader {
        Object load(Map<String, Object> config,
                    TaskRunner.ModelComponentRef tokenizer,
                    int numExamine,
                    Object rewardKwargs);
    }

    public record RewardFunctions(Object rewardFn, Object valRewardFn) {
    }
}
