/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code ConfigManager} in
 * {@code openjiuwen/core/retrieval/utils/config_manager.py}.
 */
public class ConfigManager {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, Object> configs = new LinkedHashMap<>();

    public ConfigManager() {
    }

    public ConfigManager(String configPath) {
        if (configPath != null) {
            loadFromFile(configPath);
        }
    }

    public void loadFromFile(String path) {
        Path pathObject = Path.of(path);
        if (!Files.exists(pathObject)) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_UTILS_CONFIG_FILE_NOT_FOUND,
                    "error_msg",
                    "Configuration file does not exist: " + path
            );
        }

        String suffix = fileSuffix(pathObject);
        Map<String, Object> data;
        try {
            if (".json".equals(suffix)) {
                data = MAPPER.readValue(Files.readString(pathObject), new TypeReference<>() { });
            } else if (".yaml".equals(suffix) || ".yml".equals(suffix)) {
                data = loadYaml(pathObject);
            } else {
                throw ErrorHelper.buildError(
                        StatusCode.RETRIEVAL_UTILS_CONFIG_FORMAT_NOT_SUPPORT,
                        "error_msg",
                        "Unsupported configuration file format: " + suffix
                );
            }
        } catch (IOException exception) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_UTILS_CONFIG_PROCESS_ERROR,
                    "error_msg",
                    exception.getMessage()
            );
        }

        configs.put("knowledge_base", MAPPER.convertValue(data, KnowledgeBaseConfig.class));
    }

    public void saveToFile(String path) {
        Object config = configs.get("knowledge_base");
        if (!(config instanceof KnowledgeBaseConfig knowledgeBaseConfig)) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_UTILS_CONFIG_NOT_FOUND,
                    "error_msg",
                    "No configuration to save"
            );
        }

        Path pathObject = Path.of(path);
        String suffix = fileSuffix(pathObject);
        Map<String, Object> data = MAPPER.convertValue(knowledgeBaseConfig, new TypeReference<>() { });
        try {
            if (".json".equals(suffix)) {
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(pathObject.toFile(), data);
            } else if (".yaml".equals(suffix) || ".yml".equals(suffix)) {
                dumpYaml(pathObject, data);
            } else {
                throw ErrorHelper.buildError(
                        StatusCode.RETRIEVAL_UTILS_CONFIG_FORMAT_NOT_SUPPORT,
                        "error_msg",
                        "Unsupported configuration file format: " + suffix
                );
            }
        } catch (IOException exception) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_UTILS_CONFIG_PROCESS_ERROR,
                    "error_msg",
                    exception.getMessage()
            );
        }
    }

    public <T> T getConfig(Class<T> configType) {
        for (Object config : configs.values()) {
            if (configType.isInstance(config)) {
                return configType.cast(config);
            }
        }
        return null;
    }

    public KnowledgeBaseConfig getKnowledgeBaseConfig() {
        Object config = configs.get("knowledge_base");
        if (!(config instanceof KnowledgeBaseConfig knowledgeBaseConfig)) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_UTILS_CONFIG_PROCESS_ERROR,
                    "error_msg",
                    "Knowledge base configuration not loaded"
            );
        }
        return knowledgeBaseConfig;
    }

    public void updateConfig(Object config) {
        configs.put(config.getClass().getSimpleName(), config);
    }

    private static Map<String, Object> loadYaml(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            Object loaded = new Yaml().load(input);
            if (loaded instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                return result;
            }
            return new LinkedHashMap<>();
        } catch (NoClassDefFoundError error) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_UTILS_PYYAML_NOT_FOUND,
                    "error_msg",
                    "PyYAML is required to support YAML configuration files"
            );
        }
    }

    private static void dumpYaml(Path path, Map<String, Object> data) throws IOException {
        try {
            Files.writeString(path, new Yaml().dump(data));
        } catch (NoClassDefFoundError error) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_UTILS_PYYAML_NOT_FOUND,
                    "error_msg",
                    "PyYAML is required to support YAML configuration files"
            );
        }
    }

    private static String fileSuffix(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 ? fileName.substring(dotIndex).toLowerCase() : "";
    }
}
