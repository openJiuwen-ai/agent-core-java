/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.retrieval.utils;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.utils.ConfigManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Config manager test cases.
 *
 * <p>Mirrors Python's {@code test_config_manager.py} in
 * {@code tests.unit_tests.core.retrieval.utils.test_config_manager}.</p>
 */
@DisplayName("ConfigManager Tests")
class TestConfigManager {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("Initialization")
    class InitializationTests {

        @Test
        @DisplayName("test_init_with_path - loads config during construction")
        void testInitWithPath() throws Exception {
            Path configFile = writeJson("""
                    {
                      "kb_id": "test_kb",
                      "index_type": "vector",
                      "use_graph": false,
                      "chunk_size": 512,
                      "chunk_overlap": 50
                    }
                    """);

            ConfigManager manager = new ConfigManager(configFile.toString());
            KnowledgeBaseConfig config = manager.getKnowledgeBaseConfig();

            assertThat(config.getKbId()).isEqualTo("test_kb");
            assertThat(config.getIndexType()).isEqualTo("vector");
        }
    }

    @Nested
    @DisplayName("Load Configuration")
    class LoadConfigTests {

        @Test
        @DisplayName("test_load_from_file_json - loads JSON config")
        void testLoadFromFileJson() throws Exception {
            Path configFile = writeJson("""
                    {
                      "kb_id": "test_kb",
                      "index_type": "hybrid"
                    }
                    """);

            ConfigManager manager = new ConfigManager();
            manager.loadFromFile(configFile.toString());

            KnowledgeBaseConfig config = manager.getKnowledgeBaseConfig();
            assertThat(config.getKbId()).isEqualTo("test_kb");
            assertThat(config.getIndexType()).isEqualTo("hybrid");
        }

        @Test
        @DisplayName("test_load_from_file_yaml - loads YAML config")
        void testLoadFromFileYaml() throws Exception {
            Path configFile = tempDir.resolve("config.yaml");
            Files.writeString(configFile, """
                    kb_id: test_kb
                    index_type: vector
                    """);

            ConfigManager manager = new ConfigManager();
            manager.loadFromFile(configFile.toString());

            KnowledgeBaseConfig config = manager.getKnowledgeBaseConfig();
            assertThat(config.getKbId()).isEqualTo("test_kb");
            assertThat(config.getIndexType()).isEqualTo("vector");
        }

        @Test
        @DisplayName("test_load_from_file_not_found - raises for missing file")
        void testLoadFromFileNotFound() {
            ConfigManager manager = new ConfigManager();

            assertThatThrownBy(() -> manager.loadFromFile(tempDir.resolve("nonexistent.json").toString()))
                    .isInstanceOf(BaseError.class);
        }
    }

    @Nested
    @DisplayName("Get Configuration")
    class GetConfigTests {

        @Test
        @DisplayName("test_get_config - returns stored config by type")
        void testGetConfig() {
            ConfigManager manager = new ConfigManager();
            KnowledgeBaseConfig config = new KnowledgeBaseConfig("test_kb");

            manager.updateConfig(config);

            KnowledgeBaseConfig retrievedConfig = manager.getConfig(KnowledgeBaseConfig.class);
            assertThat(retrievedConfig).isNotNull();
            assertThat(retrievedConfig.getKbId()).isEqualTo("test_kb");
        }

        @Test
        @DisplayName("test_get_config_not_found - returns null for missing config")
        void testGetConfigNotFound() {
            ConfigManager manager = new ConfigManager();

            KnowledgeBaseConfig config = manager.getConfig(KnowledgeBaseConfig.class);

            assertThat(config).isNull();
        }
    }

    private Path writeJson(String json) throws Exception {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, json);
        return configFile;
    }
}
