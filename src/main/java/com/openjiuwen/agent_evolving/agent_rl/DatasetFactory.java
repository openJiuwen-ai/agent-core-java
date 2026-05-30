/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Factory for creating agent datasets for RL training.
 * <p>
 * Mirrors Python's dataset functions in
 * {@code openjiuwen.agent_evolving.agent_rl.dataset}.
 */
public final class DatasetFactory {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DatasetFactory() {
        // Static utility class
    }

    /**
     * Build agent datasets for training and validation.
     *
     * @param dataCfg Data configuration
     * @param tokenizer Tokenizer instance
     * @param processor Processor instance
     * @param trainFiles Training data files
     * @param valFiles Validation data files
     * @return Array of [trainDataset, valDataset]
     */
    public static AgentDataset[] buildAgentDatasets(
            Object dataCfg,
            Object tokenizer,
            Object processor,
            Object trainFiles,
            Object valFiles) {
        AgentDataset trainDs = new AgentDataset(trainFiles, tokenizer, processor, dataCfg);
        AgentDataset valDs = new AgentDataset(valFiles, tokenizer, processor, dataCfg);
        return new AgentDataset[] {trainDs, valDs};
    }

    /**
     * Set train and val files in data configuration.
     *
     * @param dataCfg Data configuration object
     * @param trainFiles Training files path
     * @param valFiles Validation files path
     */
    public static void setTrainValFiles(Object dataCfg, Object trainFiles, Object valFiles) {
        if (dataCfg == null) {
            throw new IllegalArgumentException("dataCfg must not be null");
        }
        setConfigValue(dataCfg, "train_files", "trainFiles", trainFiles);
        setConfigValue(dataCfg, "val_files", "valFiles", valFiles);
    }

    /**
     * Create offline datasets for RL training.
     *
     * @param config Configuration object
     * @param tokenizer Tokenizer instance
     * @param processor Processor instance
     * @return DatasetBundle with train and val datasets
     */
    public static DatasetBundle createOfflineDatasets(
            Object config,
            Object tokenizer,
            Object processor) {
        Object dataCfg = requireDataConfig(config);
        AgentDataset[] datasets = buildAgentDatasets(
                dataCfg,
                tokenizer,
                processor,
                getConfigValue(dataCfg, "train_files", "trainFiles"),
                getConfigValue(dataCfg, "val_files", "valFiles")
        );

        DatasetBundle bundle = new DatasetBundle(datasets[0], datasets[1], DatasetFactory::defaultCollateFn);
        bundle.setTrainSampler(createRlSampler(dataCfg, datasets[0]));
        return bundle;
    }

    /**
     * Create online datasets for RL training.
     *
     * @param config Configuration object
     * @param tokenizer Tokenizer instance
     * @param processor Processor instance
     * @return DatasetBundle with train and val datasets and cleanup function
     */
    public static DatasetBundle createOnlineDatasets(
            Object config,
            Object tokenizer,
            Object processor) {
        String tmpPath = createDummyParquet();
        Object dataCfg = requireDataConfig(config);
        setTrainValFiles(dataCfg, tmpPath, tmpPath);

        AgentDataset[] datasets = buildAgentDatasets(dataCfg, tokenizer, processor, tmpPath, tmpPath);
        DatasetBundle bundle = new DatasetBundle(datasets[0], datasets[1], DatasetFactory::defaultCollateFn);
        bundle.setCleanupFn(() -> cleanupTempFile(tmpPath));
        return bundle;
    }

    /**
     * Create the dummy online dataset file used by Python online PPO setup.
     *
     * @return Path to the created temporary file
     */
    public static String createDummyParquet() {
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            List<Map<String, String>> dummyMsg = List.of(
                    Map.of("role", "user", "content", "hi"),
                    Map.of("role", "assistant", "content", "hello")
            );
            for (int i = 0; i < 16; i++) {
                rows.add(Map.of("messages", dummyMsg));
            }

            Path tmpPath = Files.createTempFile("online_ppo_dummy_", ".parquet");
            OBJECT_MAPPER.writeValue(tmpPath.toFile(), rows);
            return tmpPath.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create dummy online dataset file", e);
        }
    }

    /**
     * Minimal Java stand-in for Verl's RL sampler object returned by Python.
     */
    public record RlSampler(Object dataConfig, AgentDataset trainDataset) {
    }

    /**
     * Create a sampler tied to the same data config and train dataset as Python.
     *
     * @param dataCfg Data configuration
     * @param trainDataset Training dataset
     * @return sampler descriptor
     */
    public static RlSampler createRlSampler(Object dataCfg, AgentDataset trainDataset) {
        return new RlSampler(dataCfg, trainDataset);
    }

    @SuppressWarnings("unchecked")
    private static void setConfigValue(Object target, String snakeName, String camelName, Object value) {
        if (target instanceof Map<?, ?> map) {
            Map<Object, Object> writable = (Map<Object, Object>) map;
            writable.put(snakeName, value);
            writable.put(camelName, value);
            return;
        }
        if (invokeSetter(target, camelName, value)) {
            return;
        }
        if (writeField(target, camelName, value) || writeField(target, snakeName, value)) {
            return;
        }
        throw new IllegalArgumentException("dataCfg does not expose " + snakeName + " / " + camelName);
    }

    private static boolean invokeSetter(Object target, String camelName, Object value) {
        String methodName = "set" + Character.toUpperCase(camelName.charAt(0)) + camelName.substring(1);
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                continue;
            }
            Object adaptedValue = adaptValue(value, method.getParameterTypes()[0]);
            if (adaptedValue == UnassignableValue.INSTANCE) {
                continue;
            }
            try {
                method.invoke(target, adaptedValue);
                return true;
            } catch (ReflectiveOperationException e) {
                return false;
            }
        }
        return false;
    }

    private static boolean writeField(Object target, String fieldName, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                Object adaptedValue = adaptValue(value, field.getType());
                if (adaptedValue == UnassignableValue.INSTANCE) {
                    return false;
                }
                field.setAccessible(true);
                field.set(target, adaptedValue);
                return true;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException e) {
                return false;
            }
        }
        return false;
    }

    private static Object requireDataConfig(Object config) {
        Object dataCfg = resolveDataConfig(config);
        if (dataCfg == null) {
            throw new IllegalArgumentException("config.data must not be null");
        }
        return dataCfg;
    }

    private static Object resolveDataConfig(Object config) {
        if (config == null) {
            return null;
        }
        if (config instanceof Map<?, ?> map) {
            return map.get("data");
        }
        Object fromGetter = invokeGetter(config, "data");
        if (fromGetter != MissingValue.INSTANCE) {
            return fromGetter;
        }
        Object fromField = readField(config, "data");
        return fromField == MissingValue.INSTANCE ? null : fromField;
    }

    private static Object getConfigValue(Object target, String snakeName, String camelName) {
        if (target instanceof Map<?, ?> map) {
            if (map.containsKey(snakeName)) {
                return map.get(snakeName);
            }
            if (map.containsKey(camelName)) {
                return map.get(camelName);
            }
            throw new IllegalArgumentException("dataCfg does not expose " + snakeName + " / " + camelName);
        }
        Object fromGetter = invokeGetter(target, camelName);
        if (fromGetter != MissingValue.INSTANCE) {
            return fromGetter;
        }
        Object fromCamelField = readField(target, camelName);
        if (fromCamelField != MissingValue.INSTANCE) {
            return fromCamelField;
        }
        Object fromSnakeField = readField(target, snakeName);
        if (fromSnakeField != MissingValue.INSTANCE) {
            return fromSnakeField;
        }
        throw new IllegalArgumentException("dataCfg does not expose " + snakeName + " / " + camelName);
    }

    private static Object invokeGetter(Object target, String camelName) {
        String suffix = Character.toUpperCase(camelName.charAt(0)) + camelName.substring(1);
        for (String methodName : List.of("get" + suffix, "is" + suffix)) {
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (ReflectiveOperationException e) {
                // Try the next access strategy.
            }
        }
        return MissingValue.INSTANCE;
    }

    private static Object readField(Object target, String fieldName) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException e) {
                return MissingValue.INSTANCE;
            }
        }
        return MissingValue.INSTANCE;
    }

    private static Object defaultCollateFn(Object[] args) {
        return args == null ? List.of() : List.of(args);
    }

    private static void cleanupTempFile(String tmpPath) {
        if (tmpPath == null || tmpPath.isEmpty()) {
            return;
        }
        if (!tmpPath.contains("online_ppo_dummy_")) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(tmpPath));
        } catch (IOException e) {
            // Python cleanup ignores OSError, so Java does the same here.
        }
    }

    private static Object adaptValue(Object value, Class<?> targetType) {
        if (value == null) {
            return targetType.isPrimitive() ? UnassignableValue.INSTANCE : null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        if (targetType == String.class) {
            return String.valueOf(value);
        }
        return UnassignableValue.INSTANCE;
    }

    private enum MissingValue {
        INSTANCE
    }

    private enum UnassignableValue {
        INSTANCE
    }
}
