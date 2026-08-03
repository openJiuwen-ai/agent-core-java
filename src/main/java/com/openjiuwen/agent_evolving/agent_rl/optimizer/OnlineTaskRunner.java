// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl.optimizer;

import com.openjiuwen.agent_evolving.agent_rl.DatasetBundle;
import com.openjiuwen.agent_evolving.agent_rl.DatasetFactory;
import com.openjiuwen.agent_evolving.agent_rl.rl_trainer.PpoStep;
import com.openjiuwen.agent_evolving.agent_rl.rl_trainer.VerlConverter;
import com.openjiuwen.agent_evolving.agent_rl.rl_trainer.VerlExecutor;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Online PPO task runner.
 *
 * <p>Mirrors Python's {@code OnlineTaskRunner} in
 * {@code openjiuwen/agent_evolving/agent_rl/optimizer/task_runner.py}.</p>
 */
public class OnlineTaskRunner extends BaseTaskRunner {

    private RewardManagerLoader rewardManagerLoader;
    private TrainingExecutorFactory trainingExecutorFactory = VerlExecutor.OnlineVerlTrainingExecutor::new;

    public void setRewardManagerLoader(RewardManagerLoader rewardManagerLoader) {
        this.rewardManagerLoader = rewardManagerLoader;
    }

    public void setTrainingExecutorFactory(TrainingExecutorFactory trainingExecutorFactory) {
        this.trainingExecutorFactory = trainingExecutorFactory != null
                ? trainingExecutorFactory
                : VerlExecutor.OnlineVerlTrainingExecutor::new;
    }

    public Map<String, String> initWorkerMapping(Map<String, Object> targetConfig) {
        Map<String, String> mapping = new LinkedHashMap<>();
        mapping.put("ActorRollout", "ActorRolloutRefWorker");
        mapping.put("RefPolicy", "ActorRolloutRefWorker");
        return mapping;
    }

    @Override
    protected DatasetBundle initDatasets(Map<String, Object> targetConfig,
                                         TaskRunner.ModelComponentRef targetTokenizer,
                                         TaskRunner.ModelComponentRef targetProcessor) {
        return DatasetFactory.createOnlineDatasets(targetConfig, targetTokenizer, targetProcessor);
    }

    public void initTrainer(Map<String, Object> targetConfig) {
        Map<String, Object> effectiveConfig = TaskRunner.deepMutableCopy(targetConfig);
        TaskRunner.ModelComponents modelComponents = initModelComponents(effectiveConfig);
        Map<String, String> roleWorkerMapping = initWorkerMapping(effectiveConfig);
        initResourcePools(effectiveConfig, Map.of(
                "ActorRollout", "global_pool",
                "RefPolicy", "global_pool"
        ));
        initRewardFunctions(effectiveConfig, modelComponents.tokenizer(), rewardManagerLoader);
        DatasetBundle bundle = initDatasets(effectiveConfig, modelComponents.tokenizer(), modelComponents.processor());
        if (bundle.getCleanupFn() != null) {
            bundle.cleanup();
        }

        VerlExecutor executor = trainingExecutorFactory.create(effectiveConfig);
        executor.setUseReferencePolicy(roleWorkerMapping.containsKey("RefPolicy"));
        executor.setUseCritic(false);
        executor.setGlobalSteps(0);

        this.verlTrainer = executor;
        this.tokenizer = modelComponents.tokenizer();
        this.processor = modelComponents.processor();
        this.config = effectiveConfig;
        this.initialized = true;
        if (bundle.getTrainDataset() != null || bundle.getValDataset() != null) {
            effectiveConfig.put("_dataset_bundle", bundle);
        }
    }

    public Map<String, Object> trainOnBatch(VerlConverter.DataProto dataProto) {
        if (!initialized) {
            throw new IllegalStateException("Call init_trainer() before train_on_batch()");
        }
        verlTrainer.setGlobalSteps(verlTrainer.getGlobalSteps() + 1);
        PpoStep.Batch batch = new PpoStep.Batch(dataProto.batch(), dataProto.nonTensors(), dataProto.length());
        if (batch.getBatch().containsKey("old_log_probs")) {
            batch.getBatch().remove("old_log_probs");
        }
        return verlTrainer.trainStep(batch, batch);
    }

    public String exportLora(String outputDir, String baseModelPath) {
        if (!initialized) {
            throw new IllegalStateException("Call init_trainer() before export_lora()");
        }
        Path outputPath = Path.of(outputDir);
        try {
            Files.createDirectories(outputPath);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to create LoRA output directory: " + outputDir, exception);
        }
        Path peftDir = outputPath.resolve("peft_adapter");
        try {
            Files.createDirectories(peftDir);
            Path adapterConfig = peftDir.resolve("adapter_config.json");
            if (!Files.exists(adapterConfig)) {
                Files.writeString(adapterConfig, "{\"base_model_name_or_path\":\"" + baseModelPath + "\"}");
            }
            return peftDir.toString();
        } catch (IOException exception) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_RL_DEPENDENCY_INIT_FAILED,
                    null,
                    null,
                    exception,
                    Map.of("error_msg", "failed to export LoRA adapter")
            );
        }
    }

    @FunctionalInterface
    public interface TrainingExecutorFactory {
        VerlExecutor create(Map<String, Object> config);
    }
}
