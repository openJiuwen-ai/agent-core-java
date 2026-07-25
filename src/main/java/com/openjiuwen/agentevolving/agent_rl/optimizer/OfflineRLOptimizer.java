// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agentevolving.agent_rl.optimizer;

import com.openjiuwen.agentevolving.agent_rl.RewardRegistry;
import com.openjiuwen.agentevolving.agent_rl.config.AdaConfig;
import com.openjiuwen.agentevolving.agent_rl.config.RLConfig;
import com.openjiuwen.agentevolving.agent_rl.config.RolloutConfig;
import com.openjiuwen.agentevolving.agent_rl.config.TrainingConfig;
import com.openjiuwen.agentevolving.agent_rl.offline.runtime.AgentFactory;
import com.openjiuwen.agentevolving.agent_rl.offline.store.FileRolloutStore;
import com.openjiuwen.agentevolving.agent_rl.offline.store.NullRolloutStore;
import com.openjiuwen.agentevolving.agent_rl.offline.store.RLMetricsTracker;
import com.openjiuwen.agentevolving.agent_rl.offline.store.RolloutPersistence;
import com.openjiuwen.agentevolving.agent_rl.schemas.RolloutMessage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * User-facing offline RL optimizer that composes config and drives the PPO task runner.
 *
 * <p>Mirrors Python's {@code OfflineRLOptimizer} in
 * {@code openjiuwen/agent_evolving/agent_rl/optimizer/rl_optimizer.py}.</p>
 */
public class OfflineRLOptimizer extends BaseRLOptimizer {

    private static final Logger LOGGER = Logger.getLogger(OfflineRLOptimizer.class.getName());

    private OfflineTaskRunner runner;
    private Supplier<?> agentFactory;
    private Function<Map<String, Object>, Map<String, Object>> taskDataFn;
    private String rewardFnName;
    private Function<RolloutMessage, Map<String, Object>> rewardFn;
    private List<Object> tools = new ArrayList<>();
    private List<String> toolNames = new ArrayList<>();

    public OfflineRLOptimizer(RLConfig config) {
        super(config);
    }

    public void setTools(List<?> tools) {
        this.tools = new ArrayList<>(tools == null ? List.of() : tools);
        this.toolNames = this.tools.stream().map(OfflineRLOptimizer::toolName).toList();
    }

    public void setTaskDataFn(Function<Map<String, Object>, Map<String, Object>> fn) {
        this.taskDataFn = fn;
    }

    public void registerReward(Function<RolloutMessage, Map<String, Object>> fn, String name) {
        String rewardName = name != null && !name.isBlank() ? name : fn.getClass().getSimpleName();
        RewardRegistry.rewardRegistry().register(rewardName, value -> {
            Object arg = value;
            if (value instanceof Object[]) {
                Object[] arr = (Object[]) value;
                if (arr.length > 0) {
                    arg = arr[0];
                }
            }
            return fn.apply((RolloutMessage) arg);
        });
        this.rewardFnName = rewardName;
        this.rewardFn = fn;
        LOGGER.info(() -> "Registered reward function: " + rewardName);
    }

    public void setAgentFactory(Supplier<?> factory) {
        this.agentFactory = factory;
    }

    public Function<RolloutMessage, Map<String, Object>> getRolloutRewardFn() {
        return rewardFn;
    }

    public Object resolveAgentFactory() {
        if (agentFactory != null) {
            return agentFactory;
        }
        if (!tools.isEmpty()) {
            return AgentFactory.buildAgentFactory(config.getRuntime(), tools, toolNames);
        }
        return null;
    }

    public RolloutPersistence buildPersistence(RLConfig targetConfig) {
        if (!targetConfig.getPersistence().isEnabled()) {
            return new NullRolloutStore();
        }
        String savePath = targetConfig.getPersistence().getSavePath();
        Path runPath = Path.of(savePath == null ? "" : savePath).resolve(runName);
        return new FileRolloutStore(runPath.toString(), targetConfig.getPersistence().getFlushInterval());
    }

    public RLMetricsTracker buildMetricsTracker(RLConfig targetConfig) {
        TrainingConfig training = targetConfig.getTraining();
        return new RLMetricsTracker(
                training.getProjectName(),
                runName,
                training.getLogger(),
                configAsMap(targetConfig)
        );
    }

    public Map<String, Object> composeHydraConfig() {
        return composeHydraConfig(config, runName);
    }

    public static Map<String, Object> composeHydraConfig(RLConfig targetConfig, String runName) {
        TrainingConfig trainCfg = targetConfig.getTraining();
        RolloutConfig rolloutCfg = targetConfig.getRollout();

        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("algorithm", mapOfEntries(
                "adv_estimator", trainCfg.getAlgorithmAdvEstimator(),
                "use_kl_in_reward", trainCfg.isAlgorithmUseKlInReward(),
                "filter_groups", trainCfg.isAlgorithmFilterGroups(),
                "norm_adv_by_std_in_grpo", trainCfg.isAlgorithmNormAdvByStdInGrpo()
        ));
        cfg.put("data", mapOfEntries(
                "train_files", trainCfg.getResolvedTrainFiles(),
                "val_files", trainCfg.getResolvedValFiles(),
                "train_batch_size", trainCfg.getTrainBatchSize(),
                "max_prompt_length", trainCfg.getMaxPromptLength(),
                "max_response_length", trainCfg.getMaxResponseLength(),
                "truncation", trainCfg.getTruncation(),
                "trust_remote_code", true
        ));
        cfg.put("actor_rollout_ref", mapOfEntries(
                "model", mapOfEntries("path", trainCfg.getModelPath()),
                "actor", mapOfEntries(
                        "strategy", "fsdp",
                        "ppo_micro_batch_size_per_gpu", trainCfg.getMicroBatchSizePerGpu(),
                        "optim", mapOfEntries("lr", rolloutCfg.getActorOptimizerLr()),
                        "use_kl_loss", rolloutCfg.isActorUseKlLoss(),
                        "kl_loss_coef", rolloutCfg.getActorKlLossCoef(),
                        "entropy_coeff", rolloutCfg.getActorEntropyCoef(),
                        "clip_ratio_low", rolloutCfg.getActorClipRatioLow(),
                        "clip_ratio_high", rolloutCfg.getActorClipRatioHigh(),
                        "loss_agg_mode", rolloutCfg.getActorLossAggMode()
                ),
                "rollout", mapOfEntries(
                        "mode", "sync",
                        "n", rolloutCfg.getRolloutN(),
                        "log_prob_micro_batch_size_per_gpu", trainCfg.getMicroBatchSizePerGpu()
                ),
                "ref", mapOfEntries("log_prob_micro_batch_size_per_gpu", trainCfg.getMicroBatchSizePerGpu())
        ));
        cfg.put("trainer", mapOfEntries(
                "val_before_train", trainCfg.isValBeforeTrain(),
                "critic_warmup", trainCfg.getCriticWarmup(),
                "logger", List.copyOf(trainCfg.getLogger()),
                "project_name", trainCfg.getProjectName(),
                "experiment_name", runName,
                "nnodes", trainCfg.getNnodes(),
                "save_freq", trainCfg.getSaveFreq(),
                "test_freq", trainCfg.getTestFreq(),
                "default_local_dir", trainCfg.getSavePath(),
                "total_epochs", trainCfg.getTotalEpochs(),
                "n_gpus_per_node", trainCfg.getNGpusPerNode(),
                "runtime_parallel_num", trainCfg.getRolloutConcurrency()
        ));
        Map<String, Object> jiuwenRl = mapOfEntries("whole_trajectory", trainCfg.isWholeTrajectory());
        AdaConfig adaCfg = targetConfig.getAda();
        if (adaCfg != null) {
            asMutableMap(cfg.get("trainer")).put("rollout_max_round", adaCfg.getRolloutMaxRound());
            jiuwenRl.put("custom_fn", mapOfEntries(
                    "classifier", "default_classify_rollouts",
                    "validator", "validate_stop_balanced",
                    "sampler", "sampling_ada"
            ));
            jiuwenRl.put("final_keep_per_prompt", adaCfg.getFinalKeepPerPrompt());
        }
        cfg.put("JiuwenRL", jiuwenRl);
        cfg.put("reward_model", mapOfEntries("reward_kwargs", Map.of()));
        cfg.put("ray_kwargs", mapOfEntries("ray_init", mapOfEntries("runtime_env", Map.of())));
        return cfg;
    }

    @Override
    public void initTrainer() {
        Map<String, Object> cfg = composeHydraConfig();
        setupEnvironment();
        initRay(cfg);

        RolloutPersistence persistence = buildPersistence(config);
        RLMetricsTracker metricsTracker = buildMetricsTracker(config);
        Object resolvedAgentFactory = resolveAgentFactory();

        runner = new OfflineTaskRunner();
        runner.initTrainer(
                cfg,
                resolvedAgentFactory,
                taskDataFn,
                rewardFn,
                metricsTracker,
                persistence,
                null
        );
        LOGGER.info("Offline trainer initialized successfully");
    }

    @Override
    public void startTraining() {
        if (runner == null) {
            runner = new OfflineTaskRunner();
        }
        if (!runner.isReady()) {
            throw new IllegalStateException("call init_trainer() first");
        }
        runner.startTrainer();
        LOGGER.info("Offline training completed");
    }

    @Override
    public void stop() {
        runner = null;
        rayInitialized = false;
        LOGGER.info("Ray shutdown complete");
    }

    public OfflineTaskRunner getRunner() {
        return runner;
    }

    public String getRewardFnName() {
        return rewardFnName;
    }

    public List<Object> getTools() {
        return List.copyOf(tools);
    }

    public List<String> getToolNames() {
        return List.copyOf(toolNames);
    }

    private static String toolName(Object tool) {
        if (tool == null) {
            return "null";
        }
        for (String methodName : List.of("getName", "name")) {
            try {
                Method method = tool.getClass().getMethod(methodName);
                Object value = method.invoke(tool);
                if (value != null) {
                    return String.valueOf(value);
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next Python-like naming surface.
            }
        }
        try {
            Field field = tool.getClass().getDeclaredField("name");
            field.setAccessible(true);
            Object value = field.get(tool);
            if (value != null) {
                return String.valueOf(value);
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall back to Java's object representation.
        }
        return String.valueOf(tool);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMutableMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> configAsMap(RLConfig targetConfig) {
        TrainingConfig training = targetConfig.getTraining();
        return mapOfEntries(
                "training", mapOfEntries(
                        "project_name", training.getProjectName(),
                        "experiment_name", training.getExperimentName(),
                        "model_path", training.getModelPath(),
                        "save_path", training.getSavePath()
                ),
                "rollout", mapOfEntries(
                        "actor_optimizer_lr", targetConfig.getRollout().getActorOptimizerLr(),
                        "rollout_n", targetConfig.getRollout().getRolloutN()
                )
        );
    }
}
