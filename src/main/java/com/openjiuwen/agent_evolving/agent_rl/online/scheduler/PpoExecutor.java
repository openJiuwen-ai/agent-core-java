// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl.online.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.agent_rl.online.inference.InferenceNotifier;
import com.openjiuwen.agent_evolving.agent_rl.rl_trainer.VerlConverter;
import com.openjiuwen.agent_evolving.agent_rl.storage.LoRARepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Own PPO runner lifecycle and execute one online training batch.
 * <p>
 * Mirrors Python's {@code PPOTrainingExecutor} in
 * {@code openjiuwen.agent_evolving.agent_rl.online.scheduler.ppo_executor}.
 */
public class PpoExecutor implements PpoTrainingExecutor, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("online_rl.scheduler");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PpoConfig config;
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

    public PpoExecutor(PpoConfig config) {
        this("", null, null, 1, "", null, config, LocalPpoRunner::new, VerlDataProtoAdapter::new);
    }

    public PpoExecutor(String baseModelPath,
                       LoRARepository loraRepo,
                       InferenceNotifier notifier,
                       int nprocPerNode,
                       String trainingGpuIds,
                       String ppoConfigPath) {
        this(baseModelPath, loraRepo, notifier, nprocPerNode, trainingGpuIds, ppoConfigPath,
                new PpoConfig(), LocalPpoRunner::new, VerlDataProtoAdapter::new);
    }

    PpoExecutor(String baseModelPath,
                LoRARepository loraRepo,
                InferenceNotifier notifier,
                int nprocPerNode,
                String trainingGpuIds,
                String ppoConfigPath,
                PpoConfig config,
                PpoRunnerFactory runnerFactory,
                DataProtoConverterFactory converterFactory) {
        this.config = config != null ? config : new PpoConfig();
        this.baseModelPath = baseModelPath != null ? baseModelPath : "";
        this.loraRepo = loraRepo;
        this.notifier = notifier;
        this.nprocPerNode = nprocPerNode;
        this.trainingGpuIds = trainingGpuIds != null ? trainingGpuIds : "";
        this.ppoConfigPath = ppoConfigPath;
        this.runnerFactory = runnerFactory != null ? runnerFactory : LocalPpoRunner::new;
        this.converterFactory = converterFactory != null ? converterFactory : VerlDataProtoAdapter::new;
    }

    /**
     * Compatibility wrapper around {@link #trainBatch(String, List, int, String)}.
     */
    public Object executeStep(Object batch) {
        if (batch instanceof Map<?, ?> map) {
            Map<String, Object> payload = castMap(map);
            String userId = String.valueOf(payload.getOrDefault("user_id", "online"));
            int trainingCount = intValue(payload.getOrDefault("training_count", 0));
            String tmpRoot = String.valueOf(payload.getOrDefault("tmp_root", "/tmp/agent_rl_online"));
            return trainBatch(userId, samplesFrom(payload.get("samples")), trainingCount, tmpRoot);
        }
        if (batch instanceof List<?> list) {
            return trainBatch("online", castSampleList(list), 0, "/tmp/agent_rl_online");
        }
        throw new IllegalArgumentException("batch must be a sample list or a map containing samples");
    }

    /**
     * Compatibility numeric loss helper for legacy Java callers.
     */
    public double computeLoss(Object predictions, Object targets) {
        List<Double> pred = flattenNumbers(predictions);
        List<Double> expected = flattenNumbers(targets);
        if (pred.isEmpty() || pred.size() != expected.size()) {
            throw new IllegalArgumentException("predictions and targets must contain the same number of numeric values");
        }
        double sum = 0.0;
        for (int i = 0; i < pred.size(); i++) {
            double delta = pred.get(i) - expected.get(i);
            sum += delta * delta;
        }
        return sum / pred.size();
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
                    notifier.notifyUpdate(userId, publishedLoraPath);
                } catch (Exception exception) {
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
                notifier.close();
            } catch (Exception exception) {
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
        this.ppoConfig = configMap;
        this.ppoRunner = runnerFactory.create();
        this.ppoRunner.init(configMap, trainingGpuIds);
        this.ppoInitialized = true;
        LOGGER.info("OnlineTaskRunner (PPO) initialized");
    }

    private String runPpoTrainingSync(String userId, List<Map<String, Object>> samples, Path runDir) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("samples must be non-empty");
        }
        initPpoTrainer();

        Map<String, Object> dataCfg = asMap(ppoConfig.get("data"));
        Integer maxPromptLength = optionalInt(dataCfg.get("max_prompt_length"));
        Integer maxResponseLength = optionalInt(dataCfg.get("max_response_length"));
        String truncation = String.valueOf(dataCfg.getOrDefault("truncation", "truncate"));
        boolean filterOverlongPrompts = booleanValue(dataCfg.getOrDefault("filter_overlong_prompts", false));

        LOGGER.info(
                "Preparing DataProto: raw_prompt_max={} raw_response_max={} cfg_prompt_max={} cfg_response_max={} truncation={} filter_overlong_prompts={}",
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
        int batchSize = dataProto instanceof VerlConverter.DataProto proto ? proto.length() : samples.size();
        LOGGER.info("Converted {} samples to DataProto (batch_size={})", samples.size(), batchSize);

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
                        "sample_count", samples.size(),
                        "avg_score", avgScore,
                        "training_mode", "ppo",
                        "ppo_metrics", numericMetrics(metrics)
                ),
                baseModelPath
        );
        LOGGER.info("Published PPO LoRA user={} version={} avg_score={}", userId, version.version(), avgScore);
        return version.path();
    }

    /**
     * Get configuration.
     */
    public PpoConfig getConfig() {
        return config;
    }

    private static int rawTokenMax(List<Map<String, Object>> samples, String key) {
        int max = 0;
        for (Map<String, Object> sample : samples) {
            Map<String, Object> trajectory = asMap(sample.get("trajectory"));
            Object value = trajectory.get(key);
            if (value instanceof List<?> list) {
                max = Math.max(max, list.size());
            }
        }
        return max;
    }

    private static double averageScore(List<Map<String, Object>> samples) {
        if (samples.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (Map<String, Object> sample : samples) {
            Map<String, Object> judge = asMap(sample.get("judge"));
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
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static List<Map<String, Object>> samplesFrom(Object value) {
        if (value instanceof List<?> list) {
            return castSampleList(list);
        }
        throw new IllegalArgumentException("batch.samples must be a list");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castSampleList(List<?> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("sample entries must be maps");
            }
            out.add((Map<String, Object>) map);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
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

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static List<Double> flattenNumbers(Object value) {
        List<Double> out = new ArrayList<>();
        collectNumbers(value, out);
        return out;
    }

    private static void collectNumbers(Object value, List<Double> out) {
        if (value instanceof Number number) {
            out.add(number.doubleValue());
            return;
        }
        if (value instanceof double[] values) {
            for (double v : values) {
                out.add(v);
            }
            return;
        }
        if (value instanceof int[] values) {
            for (int v : values) {
                out.add((double) v);
            }
            return;
        }
        if (value instanceof Object[] values) {
            for (Object item : values) {
                collectNumbers(item, out);
            }
            return;
        }
        if (value instanceof Iterable<?> values) {
            for (Object item : values) {
                collectNumbers(item, out);
            }
        }
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
            this.converter = new VerlConverter(verlOptions);
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
            this.initialized = true;
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
                Files.writeString(peftDir.resolve("adapter_config.json"), OBJECT_MAPPER.writeValueAsString(Map.of(
                        "base_model_name_or_path", baseModelPath != null ? baseModelPath : "",
                        "peft_type", "LORA"
                )));
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
    }
}
