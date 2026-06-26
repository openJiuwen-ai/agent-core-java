// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl.online.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.agent_rl.online.inference.InferenceNotifier;
import com.openjiuwen.agent_evolving.agent_rl.rl_trainer.VerlConverter;
import com.openjiuwen.agent_evolving.agent_rl.storage.LoRARepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns PPO runner lifecycle and executes one online training batch.
 *
 * <p>Mirrors Python's {@code PPOTrainingExecutor} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/scheduler/ppo_executor.py}.</p>
 */
public class PpoExecutor implements PpoTrainingExecutor, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("online_rl.scheduler");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String baseModelPath;
    private final LoRARepository loraRepo;
    private final InferenceNotifier notifier;
    private final int nprocPerNode;
    private final String trainingGpuIds;
    private final String ppoConfigPath;
    private final PpoRunnerFactory runnerFactory;
    private final DataProtoConverterFactory converterFactory;

    private PpoRunner ppoRunner;
    private boolean ppoInitialized;
    private Map<String, Object> ppoConfig;

    public PpoExecutor(
            String baseModelPath,
            LoRARepository loraRepo,
            InferenceNotifier notifier,
            int nprocPerNode,
            String trainingGpuIds,
            String ppoConfigPath) {
        this(
                baseModelPath,
                loraRepo,
                notifier,
                nprocPerNode,
                trainingGpuIds,
                ppoConfigPath,
                LocalPpoRunner::new,
                VerlDataProtoAdapter::new
        );
    }

    PpoExecutor(
            String baseModelPath,
            LoRARepository loraRepo,
            InferenceNotifier notifier,
            int nprocPerNode,
            String trainingGpuIds,
            String ppoConfigPath,
            PpoRunnerFactory runnerFactory,
            DataProtoConverterFactory converterFactory) {
        this.baseModelPath = baseModelPath != null ? baseModelPath : "";
        this.loraRepo = loraRepo;
        this.notifier = notifier;
        this.nprocPerNode = nprocPerNode;
        this.trainingGpuIds = trainingGpuIds != null ? trainingGpuIds : "";
        this.ppoConfigPath = ppoConfigPath;
        this.runnerFactory = runnerFactory != null ? runnerFactory : LocalPpoRunner::new;
        this.converterFactory = converterFactory != null ? converterFactory : VerlDataProtoAdapter::new;
    }

    @Override
    public String trainBatch(String userId, List<Map<String, Object>> samples, int trainingCount, String tmpRoot) {
        String runId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Path runDir = Path.of(tmpRoot != null ? tmpRoot : "/tmp/agent_rl_online")
                .resolve("run_" + trainingCount + "_" + runId);
        try {
            Files.createDirectories(runDir);
            String publishedLoraPath = runPpoTrainingSync(userId, samples, runDir);
            if (notifier != null && publishedLoraPath != null && !publishedLoraPath.isBlank()) {
                try {
                    notifier.notifyUpdate(userId, publishedLoraPath).toCompletableFuture().join();
                } catch (RuntimeException exception) {
                    LOGGER.warn("Failed to notify vLLM for LoRA hot-load (non-fatal)");
                }
            }
            return publishedLoraPath;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to prepare PPO run directory: " + runDir, exception);
        } finally {
            deleteRecursivelyIfExists(runDir.resolve("fsdp_ckpt"));
        }
    }

    public void aclose() {
        if (notifier != null) {
            try {
                notifier.close().toCompletableFuture().join();
            } catch (RuntimeException exception) {
                LOGGER.debug("Failed to close inference notifier: {}", exception.getMessage());
            }
        }
        close();
    }

    @Override
    public void close() {
        if (ppoRunner == null) {
            return;
        }
        try {
            ppoRunner.close();
        } catch (Exception exception) {
            LOGGER.debug("Failed to close PPO runner: {}", exception.getMessage());
        }
        ppoRunner = null;
        ppoInitialized = false;
    }

    public boolean isPpoInitialized() {
        return ppoInitialized;
    }

    public Map<String, Object> getComposedPpoConfig() {
        return ppoConfig != null ? deepCopyMap(ppoConfig) : null;
    }

    private void initPpoTrainer() {
        if (ppoInitialized) {
            return;
        }
        Map<String, Object> configMap = PpoConfigComposer.composeOnlinePpoConfig(
                baseModelPath,
                nprocPerNode,
                ppoConfigPath
        );
        ppoConfig = configMap;
        ppoRunner = runnerFactory.create();
        ppoRunner.init(configMap, trainingGpuIds);
        ppoInitialized = true;
        LOGGER.info("OnlineTaskRunner (PPO) initialized");
    }

    private String runPpoTrainingSync(String userId, List<Map<String, Object>> samples, Path runDir) {
        initPpoTrainer();

        Map<String, Object> dataCfg = asMap(ppoConfig.get("data"));
        Integer maxPromptLength = optionalInt(dataCfg.get("max_prompt_length"));
        Integer maxResponseLength = optionalInt(dataCfg.get("max_response_length"));
        String truncation = String.valueOf(dataCfg.getOrDefault("truncation", "truncate"));
        boolean filterOverlongPrompts = booleanValue(dataCfg.getOrDefault("filter_overlong_prompts", false));

        LOGGER.info(
                "Preparing DataProto: raw_prompt_max={} raw_response_max={} cfg_prompt_max={} cfg_response_max={} "
                        + "truncation={} filter_overlong_prompts={}",
                rawTokenMax(samples, "prompt_ids"),
                rawTokenMax(samples, "response_ids"),
                maxPromptLength,
                maxResponseLength,
                truncation,
                filterOverlongPrompts
        );

        DataProtoConverter converter = converterFactory.create(new ConverterOptions(
                0,
                maxPromptLength,
                maxResponseLength,
                truncation,
                filterOverlongPrompts
        ));
        Object dataProto = converter.convertSamples(samples);
        int batchSize = dataProto instanceof VerlConverter.DataProto proto ? proto.length() : safeSize(samples);
        LOGGER.info("Converted {} samples to DataProto (batch_size={})", safeSize(samples), batchSize);

        Map<String, Object> metrics = ppoRunner.trainOnBatch(dataProto);
        LOGGER.info("PPO train_step metrics: {}", numericMetrics(metrics));

        String peftDir = ppoRunner.exportLora(runDir, baseModelPath);
        if (loraRepo == null) {
            return null;
        }

        double avgScore = averageScore(samples);
        LoRARepository.LoRAVersion version = loraRepo.publish(
                userId,
                peftDir,
                Map.of(
                        "sample_count", safeSize(samples),
                        "avg_score", avgScore,
                        "training_mode", "ppo",
                        "ppo_metrics", numericMetrics(metrics)
                ),
                baseModelPath
        );
        LOGGER.info("Published PPO LoRA user={} version={} avg_score={}", userId, version.version(), avgScore);
        return version.path();
    }

    private static int rawTokenMax(List<Map<String, Object>> samples, String key) {
        int max = 0;
        if (samples == null) {
            return max;
        }
        for (Map<String, Object> sample : samples) {
            Map<String, Object> trajectory = asMap(sample != null ? sample.get("trajectory") : null);
            Object value = trajectory.get(key);
            if (value instanceof List<?> list) {
                max = Math.max(max, list.size());
            }
        }
        return max;
    }

    private static double averageScore(List<Map<String, Object>> samples) {
        if (samples == null || samples.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (Map<String, Object> sample : samples) {
            Map<String, Object> judge = asMap(sample != null ? sample.get("judge") : null);
            Object score = judge.get("score");
            sum += score instanceof Number number ? number.doubleValue() : 0.0;
        }
        return sum / samples.size();
    }

    private static Map<String, Object> numericMetrics(Map<String, Object> metrics) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (metrics == null) {
            return out;
        }
        metrics.forEach((key, value) -> {
            if (value instanceof Number) {
                out.put(key, value);
            }
        });
        return out;
    }

    private static void deleteRecursivelyIfExists(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            LOGGER.debug("Failed to remove PPO temp directory {}: {}", root, exception.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        return OBJECT_MAPPER.convertValue(source, LinkedHashMap.class);
    }

    private static Integer optionalInt(Object value) {
        if (value instanceof Number number) {
            int intValue = number.intValue();
            return intValue > 0 ? intValue : null;
        }
        if (value == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static int safeSize(List<?> values) {
        return values != null ? values.size() : 0;
    }

    interface PpoRunnerFactory {
        PpoRunner create();
    }

    interface DataProtoConverterFactory {
        DataProtoConverter create(ConverterOptions options);
    }

    interface PpoRunner extends AutoCloseable {
        void init(Map<String, Object> config, String trainingGpuIds);

        Map<String, Object> trainOnBatch(Object dataProto);

        String exportLora(Path runDir, String baseModelPath);

        @Override
        void close();
    }

    interface DataProtoConverter {
        Object convertSamples(List<Map<String, Object>> samples);
    }

    record ConverterOptions(
            int padTokenId,
            Integer maxPromptLength,
            Integer maxResponseLength,
            String truncation,
            boolean filterOverlongPrompts
    ) {
    }

    static final class VerlDataProtoAdapter implements DataProtoConverter {
        private final VerlConverter converter;

        VerlDataProtoAdapter(ConverterOptions options) {
            VerlConverter.Options verlOptions = new VerlConverter.Options()
                    .setPadTokenId(options.padTokenId())
                    .setMaxPromptLength(options.maxPromptLength())
                    .setMaxResponseLength(options.maxResponseLength())
                    .setTruncation(options.truncation())
                    .setFilterOverlongPrompts(options.filterOverlongPrompts());
            converter = new VerlConverter(verlOptions);
        }

        @Override
        public Object convertSamples(List<Map<String, Object>> samples) {
            return converter.convertSamples(samples);
        }
    }

    static final class LocalPpoRunner implements PpoRunner {
        private Map<String, Object> config = Map.of();
        private boolean initialized;

        @Override
        public void init(Map<String, Object> config, String trainingGpuIds) {
            this.config = config != null ? deepCopyMap(config) : Map.of();
            initialized = true;
        }

        @Override
        public Map<String, Object> trainOnBatch(Object dataProto) {
            ensureInitialized();
            int batchSize = dataProto instanceof VerlConverter.DataProto proto ? proto.length() : 0;
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("batch_size", batchSize);
            metrics.put("loss", 0.0);
            metrics.put("reward_mean", 0.0);
            metrics.put("configured_gpus", asMap(config.get("trainer")).getOrDefault("n_gpus_per_node", 0));
            return metrics;
        }

        @Override
        public String exportLora(Path runDir, String baseModelPath) {
            ensureInitialized();
            try {
                Path peftDir = runDir.resolve("lora_adapter");
                Files.createDirectories(peftDir);
                Files.writeString(peftDir.resolve("adapter_model.safetensors"), "");
                Files.writeString(peftDir.resolve("adapter_config.json"), adapterConfigJson(baseModelPath));
                return peftDir.toString();
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to export LoRA adapter", exception);
            }
        }

        @Override
        public void close() {
            initialized = false;
        }

        private void ensureInitialized() {
            if (!initialized) {
                throw new IllegalStateException("PPO runner is not initialized");
            }
        }

        private static String adapterConfigJson(String baseModelPath) {
            try {
                return OBJECT_MAPPER.writeValueAsString(Map.of(
                        "base_model_name_or_path", baseModelPath != null ? baseModelPath : "",
                        "peft_type", "LORA"
                ));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Unable to serialize LoRA adapter config", exception);
            }
        }
    }
}
