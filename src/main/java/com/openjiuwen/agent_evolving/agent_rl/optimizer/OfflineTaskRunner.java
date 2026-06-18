// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl.optimizer;

import com.openjiuwen.agent_evolving.agent_rl.DatasetBundle;
import com.openjiuwen.agent_evolving.agent_rl.DatasetFactory;
import com.openjiuwen.agent_evolving.agent_rl.offline.MainTrainer;
import com.openjiuwen.agent_evolving.agent_rl.offline.store.RLMetricsTracker;
import com.openjiuwen.agent_evolving.agent_rl.offline.store.RolloutPersistence;
import com.openjiuwen.agent_evolving.agent_rl.rl_trainer.VerlExecutor;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Offline PPO task runner.
 *
 * <p>Mirrors Python's {@code OfflineTaskRunner} in
 * {@code openjiuwen/agent_evolving/agent_rl/optimizer/task_runner.py}.</p>
 */
public class OfflineTaskRunner extends BaseTaskRunner {

    private static final String GLOBAL_POOL = "global_pool";

    private MainTrainer mainTrainer;
    private RewardManagerLoader rewardManagerLoader;
    private TrainingExecutorFactory trainingExecutorFactory = VerlExecutor.OfflineVerlTrainingExecutor::new;

    public void setRewardManagerLoader(RewardManagerLoader rewardManagerLoader) {
        this.rewardManagerLoader = rewardManagerLoader;
    }

    public void setTrainingExecutorFactory(TrainingExecutorFactory trainingExecutorFactory) {
        this.trainingExecutorFactory = trainingExecutorFactory != null
                ? trainingExecutorFactory
                : VerlExecutor.OfflineVerlTrainingExecutor::new;
    }

    public MainTrainer getMainTrainer() {
        return mainTrainer;
    }

    public Map<String, String> initWorkerMapping(Map<String, Object> targetConfig) {
        String strategy = TaskRunner.stringAt(targetConfig, "actor_rollout_ref", "actor", "strategy");
        if (!"fsdp".equals(strategy) && !"fsdp2".equals(strategy)) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_RL_STRATEGY_NOT_SUPPORTED,
                    "strategy",
                    strategy
            );
        }
        boolean async = "async".equals(TaskRunner.stringAt(targetConfig, "actor_rollout_ref", "rollout", "mode"));
        String actorClass = async ? "AsyncActorRolloutRefWorker" : "ActorRolloutRefWorker";
        Map<String, String> mapping = new LinkedHashMap<>();
        mapping.put("ActorRollout", actorClass);
        mapping.put("Critic", "CriticWorker");
        mapping.put("RefPolicy", actorClass);
        return mapping;
    }

    @Override
    protected DatasetBundle initDatasets(Map<String, Object> targetConfig,
                                         TaskRunner.ModelComponentRef targetTokenizer,
                                         TaskRunner.ModelComponentRef targetProcessor) {
        return DatasetFactory.createOfflineDatasets(targetConfig, targetTokenizer, targetProcessor);
    }

    public void initTrainer(Map<String, Object> targetConfig) {
        initTrainer(targetConfig, null, null, null, null, null, null);
    }

    public void initTrainer(Map<String, Object> targetConfig,
                            Object agentFactory,
                            Function<Map<String, Object>, Map<String, Object>> taskDataFn,
                            Function<?, Map<String, Object>> rewardFn,
                            RLMetricsTracker metricsTracker,
                            RolloutPersistence persistence,
                            RewardManagerLoader rewardLoader) {
        Map<String, Object> effectiveConfig = TaskRunner.deepMutableCopy(targetConfig);
        TaskRunner.ModelComponents modelComponents = initModelComponents(effectiveConfig);
        Map<String, String> roleWorkerMapping = initWorkerMapping(effectiveConfig);
        initResourcePools(effectiveConfig, Map.of(
                "ActorRollout", GLOBAL_POOL,
                "Critic", GLOBAL_POOL,
                "RefPolicy", GLOBAL_POOL
        ));
        initRewardFunctions(effectiveConfig, modelComponents.tokenizer(),
                rewardLoader != null ? rewardLoader : rewardManagerLoader);
        DatasetBundle bundle = initDatasets(effectiveConfig, modelComponents.tokenizer(), modelComponents.processor());

        VerlExecutor executor = trainingExecutorFactory.create(effectiveConfig);
        executor.setUseReferencePolicy(roleWorkerMapping.containsKey("RefPolicy"));
        executor.setUseCritic(roleWorkerMapping.containsKey("Critic"));

        this.verlTrainer = executor;
        this.mainTrainer = new MainTrainer(
                executor,
                effectiveConfig,
                null,
                null,
                agentFactory,
                taskDataFn,
                castRolloutRewardFn(rewardFn),
                metricsTracker,
                persistence
        );
        this.tokenizer = modelComponents.tokenizer();
        this.processor = modelComponents.processor();
        this.config = effectiveConfig;
        this.initialized = true;
        if (bundle.getTrainDataset() != null || bundle.getValDataset() != null) {
            // Dataset construction has side effects in the Python runner; keep the bundle reachable through config.
            effectiveConfig.put("_dataset_bundle", bundle);
        }
    }

    public void startTrainer() {
        if (mainTrainer == null) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_RL_TRAINER_NOT_INITIALIZED,
                    "error_msg",
                    "call init_trainer() before start_trainer"
            );
        }
        mainTrainer.fit();
    }

    @SuppressWarnings("unchecked")
    private static Function<com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage, Map<String, Object>>
            castRolloutRewardFn(Function<?, Map<String, Object>> rewardFn) {
        return (Function<com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage, Map<String, Object>>) rewardFn;
    }

    @FunctionalInterface
    public interface TrainingExecutorFactory {
        VerlExecutor create(Map<String, Object> config);
    }
}
