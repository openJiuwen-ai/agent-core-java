/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Unified configuration manager for retrieval module.
 * 
 * @since 0.1.7
 */
public class ConfigManager {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, Object> configs = new LinkedHashMap<>();

    /**
     * ConfigManager.
     * 
     * @since 0.1.7
     */
    public ConfigManager() {
    }

    /**
     * ConfigManager.
     * 
     * @param configPath configPath
     * @since 0.1.7
     */
    public ConfigManager(String configPath) {
        if (configPath != null) {
            loadFromFile(configPath);
        }
    }

    /**
     * loadFromFile.
     * 
     * @param path path
     * @since 0.1.7
     */
    public void loadFromFile(String path) {
        Path file = Path.of(path);
        if (!Files.exists(file)) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_UTILS_CONFIG_FILE_NOT_FOUND,
                    "Configuration file does not exist: " + path);
        }
        String lowerName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            Map<String, Object> data;
            if (lowerName.endsWith(".json")) {
                data = MAPPER.readValue(Files.readString(file), new TypeReference<>() {
                });
            } else if (lowerName.endsWith(".yaml") || lowerName.endsWith(".yml")) {
                try (InputStream input = Files.newInputStream(file)) {
                    Object loaded = new Yaml().load(input);
                    data = loaded instanceof Map<?, ?> map ? castMap(map) : Map.of();
                }
            } else {
                throw RetrievalExceptions.error(StatusCode.RETRIEVAL_UTILS_CONFIG_FORMAT_NOT_SUPPORT,
                        "Unsupported configuration file format: " + lowerName);
            }
            KnowledgeBaseConfig config = fromMap(data);
            configs.put("knowledge_base", config);
        } catch (IOException e) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_UTILS_CONFIG_PROCESS_ERROR, e.getMessage());
        }
    }

    /**
     * saveToFile.
     * 
     * @param path path
     * @since 0.1.7
     */
    public void saveToFile(String path) {
        KnowledgeBaseConfig config = getKnowledgeBaseConfig();
        Path file = Path.of(path);
        String lowerName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kb_id", config.getKbId());
        data.put("index_type", config.getIndexType());
        data.put("use_graph", config.isUseGraph());
        data.put("chunk_size", config.getChunkSize());
        data.put("chunk_overlap", config.getChunkOverlap());
        try {
            if (lowerName.endsWith(".json")) {
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), data);
            } else if (lowerName.endsWith(".yaml") || lowerName.endsWith(".yml")) {
                Files.writeString(file, new Yaml().dump(data));
            } else {
                throw RetrievalExceptions.error(StatusCode.RETRIEVAL_UTILS_CONFIG_FORMAT_NOT_SUPPORT,
                        "Unsupported configuration file format: " + lowerName);
            }
        } catch (IOException e) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_UTILS_CONFIG_PROCESS_ERROR, e.getMessage());
        }
    }

    /**
     * getConfig.
     * 
     * @param configType configType
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(Class<T> configType) {
        for (Object value : configs.values()) {
            if (configType.isInstance(value)) {
                return (T) value;
            }
        }
        return null;
    }

    /**
     * getKnowledgeBaseConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public KnowledgeBaseConfig getKnowledgeBaseConfig() {
        Object value = configs.get("knowledge_base");
        if (!(value instanceof KnowledgeBaseConfig config)) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_UTILS_CONFIG_PROCESS_ERROR,
                    "Knowledge base configuration not loaded");
        }
        return config;
    }

    /**
     * updateConfig.
     * 
     * @param config config
     * @since 0.1.7
     */
    public void updateConfig(Object config) {
        configs.put(config.getClass().getSimpleName(), config);
    }

    /**
     * fromMap.
     * 
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    private static KnowledgeBaseConfig fromMap(Map<String, Object> data) {
        KnowledgeBaseConfig config = new KnowledgeBaseConfig();
        config.setKbId((String) data.get("kb_id"));
        if (data.containsKey("index_type")) {
            config.setIndexType(String.valueOf(data.get("index_type")));
        }
        if (data.containsKey("use_graph")) {
            config.setUseGraph(Boolean.TRUE.equals(data.get("use_graph")));
        }
        if (data.containsKey("chunk_size")) {
            config.setChunkSize(((Number) data.get("chunk_size")).intValue());
        }
        if (data.containsKey("chunk_overlap")) {
            config.setChunkOverlap(((Number) data.get("chunk_overlap")).intValue());
        }
        return config;
    }

    @SuppressWarnings("unchecked")
    /**
     * castMap.
     * 
     * @param source source
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> castMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }
}
