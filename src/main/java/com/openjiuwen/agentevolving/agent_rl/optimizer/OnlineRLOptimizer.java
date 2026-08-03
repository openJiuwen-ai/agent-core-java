// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agentevolving.agent_rl.optimizer;

import com.openjiuwen.agentevolving.agent_rl.config.RLConfig;
import com.openjiuwen.agentevolving.agent_rl.online.inference.InferenceNotifier;
import com.openjiuwen.agentevolving.agent_rl.online.scheduler.OnlineTrainingScheduler;
import com.openjiuwen.agentevolving.agent_rl.online.scheduler.PpoConfigComposer;
import com.openjiuwen.agentevolving.agent_rl.rl_trainer.VerlConverter;
import com.openjiuwen.agentevolving.agent_rl.storage.LoRARepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * User-facing online RL optimizer that polls Redis and trains PPO LoRA batches.
 *
 * <p>Mirrors Python's {@code OnlineRLOptimizer} in
 * {@code openjiuwen/agent_evolving/agent_rl/optimizer/rl_optimizer.py}.</p>
 */
public class OnlineRLOptimizer extends BaseRLOptimizer {

    private static final Logger LOGGER = Logger.getLogger(OnlineRLOptimizer.class.getName());

    private String redisUrl = "";
    private String loraRepoRoot = "";
    private String vllmUrl = "";
    private double pollInterval = 30.0d;
    private int minSamples = 4;
    private String ppoConfigPath;
    private String trainingGpuIds = "";
    private int nprocPerNode = 1;
    private OnlineTrainingScheduler scheduler;
    private OnlineTaskRunner ppoRunner;
    private Function<OnlineTrainingScheduler.Options, OnlineTrainingScheduler> schedulerFactory =
            OnlineTrainingScheduler::new;
    private Function<Map<String, Object>, OnlineTaskRunner> onlineTaskRunnerFactory = ignored -> new OnlineTaskRunner();

    public OnlineRLOptimizer(RLConfig config) {
        super(config);
    }

    public OnlineRLOptimizer setupRedis(String redisUrl) {
        return setupRedis(redisUrl, 30.0d, 4);
    }

    public OnlineRLOptimizer setupRedis(String redisUrl, double pollInterval, int minSamples) {
        this.redisUrl = stripTrailingSlash(redisUrl);
        this.pollInterval = pollInterval;
        this.minSamples = minSamples;
        return this;
    }

    public OnlineRLOptimizer setupGateway(String gatewayUrl) {
        return setupGateway(gatewayUrl, 30.0d, 4);
    }

    public OnlineRLOptimizer setupGateway(String gatewayUrl, double pollInterval, int minSamples) {
        LOGGER.warning("setup_gateway() is deprecated for OnlineRLOptimizer; use setup_redis() instead");
        String normalized = gatewayUrl == null ? "" : gatewayUrl.strip();
        if (!normalized.startsWith("redis://") && !normalized.startsWith("rediss://")) {
            throw new IllegalArgumentException(
                    "setup_gateway() no longer accepts HTTP Gateway URLs. "
                            + "OnlineRLOptimizer now polls Redis directly; call setup_redis() "
                            + "with a redis:// or rediss:// URL instead."
            );
        }
        return setupRedis(normalized, pollInterval, minSamples);
    }

    public OnlineRLOptimizer setupLoraRepo(String loraRepoRoot) {
        this.loraRepoRoot = loraRepoRoot != null ? loraRepoRoot : "";
        return this;
    }

    public OnlineRLOptimizer setupInference(String vllmUrl) {
        this.vllmUrl = vllmUrl != null ? vllmUrl : "";
        return this;
    }

    public OnlineRLOptimizer setupTrainingGpu(String gpuIds) {
        return setupTrainingGpu(gpuIds, 1);
    }

    public OnlineRLOptimizer setupTrainingGpu(String gpuIds, int nprocPerNode) {
        this.trainingGpuIds = gpuIds != null ? gpuIds : "";
        this.nprocPerNode = nprocPerNode;
        return this;
    }

    public OnlineRLOptimizer setupPpoConfig(String ppoConfigPath) {
        this.ppoConfigPath = ppoConfigPath;
        return this;
    }

    @Override
    public void initTrainer() {
        setupEnvironment();
        Map<String, Object> runtimeEnv = TaskRunner.getPpoRayRuntimeEnv();
        if (!trainingGpuIds.isBlank()) {
            @SuppressWarnings("unchecked")
            Map<String, String> envVars = (Map<String, String>) runtimeEnv.get("env_vars");
            envVars.put("CUDA_VISIBLE_DEVICES", trainingGpuIds);
        }
        rayRuntimeEnv = runtimeEnv;
        rayInitialized = true;
        LOGGER.info(() -> "Online RL environment initialized (GPUs: " + trainingGpuIds + ")");
    }

    @Override
    public void startTraining() {
        if (redisUrl == null || redisUrl.isBlank()) {
            throw new IllegalArgumentException("Redis URL not configured. Call setup_redis() first.");
        }
        LoRARepository loraRepo = loraRepoRoot.isBlank() ? null : new LoRARepository(loraRepoRoot);
        InferenceNotifier notifier = vllmUrl.isBlank() ? null : new InferenceNotifier(vllmUrl);
        OnlineTrainingScheduler.Options options = new OnlineTrainingScheduler.Options()
                .setRedisUrl(redisUrl)
                .setPollInterval(pollInterval)
                .setMinSamplesForTraining(minSamples)
                .setBaseModelPath(config.getTraining().getModelPath())
                .setLoraRepo(loraRepo)
                .setNotifier(notifier)
                .setNprocPerNode(nprocPerNode)
                .setTrainingGpuIds(trainingGpuIds)
                .setPpoConfigPath(ppoConfigPath);
        scheduler = schedulerFactory.apply(options);
        scheduler.start();
        LOGGER.info(() -> "OnlineTrainingScheduler started: redis=" + redisUrl
                + " min_samples=" + minSamples + " poll=" + Math.round(pollInterval) + "s");
    }

    @Override
    public void stop() {
        if (scheduler != null) {
            scheduler.stop();
            scheduler = null;
        }
        if (rayInitialized) {
            rayInitialized = false;
        }
        LOGGER.info("Online RL stopped");
    }

    public Map<String, Object> trainOnBatch(List<Map<String, Object>> samples) {
        Objects.requireNonNull(samples, "samples must not be null");
        if (ppoRunner == null) {
            Map<String, Object> ppoConfig = PpoConfigComposer.composeOnlinePpoConfig(
                    config.getTraining().getModelPath(),
                    nprocPerNode,
                    ppoConfigPath
            );
            ppoRunner = onlineTaskRunnerFactory.apply(ppoConfig);
            ppoRunner.initTrainer(ppoConfig);
        }
        VerlConverter.DataProto dataProto = new VerlConverter(
                new VerlConverter.Options().setPadTokenId(0)
        ).convertSamples(samples);
        return ppoRunner.trainOnBatch(dataProto);
    }

    public String exportLora(String outputDir) {
        if (ppoRunner == null) {
            throw new IllegalStateException("No PPO runner available. Call train_on_batch() first.");
        }
        return ppoRunner.exportLora(outputDir, config.getTraining().getModelPath());
    }

    public void setSchedulerFactory(Function<OnlineTrainingScheduler.Options, OnlineTrainingScheduler> schedulerFactory) {
        this.schedulerFactory = schedulerFactory != null ? schedulerFactory : OnlineTrainingScheduler::new;
    }

    public void setOnlineTaskRunnerFactory(Function<Map<String, Object>, OnlineTaskRunner> onlineTaskRunnerFactory) {
        this.onlineTaskRunnerFactory = onlineTaskRunnerFactory != null
                ? onlineTaskRunnerFactory
                : ignored -> new OnlineTaskRunner();
    }

    public String getRedisUrl() {
        return redisUrl;
    }

    public String getLoraRepoRoot() {
        return loraRepoRoot;
    }

    public String getVllmUrl() {
        return vllmUrl;
    }

    public double getPollInterval() {
        return pollInterval;
    }

    public int getMinSamples() {
        return minSamples;
    }

    public String getPpoConfigPath() {
        return ppoConfigPath;
    }

    public String getTrainingGpuIds() {
        return trainingGpuIds;
    }

    public int getNprocPerNode() {
        return nprocPerNode;
    }

    public OnlineTrainingScheduler getScheduler() {
        return scheduler;
    }

    public OnlineTaskRunner getPpoRunner() {
        return ppoRunner;
    }

    private static String stripTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("/+$", "");
    }
}
