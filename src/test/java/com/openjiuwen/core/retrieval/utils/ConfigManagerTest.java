/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.utils;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code TestConfigManager} in
 * {@code tests/unit_tests/core/retrieval/utils/test_config_manager.py}.
 */
class ConfigManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsJsonKnowledgeBaseConfig() throws Exception {
        Path json = tempDir.resolve("config.json");
        Files.writeString(json, """
                {
                  "kb_id": "kb-1",
                  "index_type": "vector",
                  "use_graph": true,
                  "chunk_size": 256,
                  "chunk_overlap": 32,
                  "use_caption_for_images": true
                }
                """);

        ConfigManager manager = new ConfigManager(json.toString());
        KnowledgeBaseConfig config = manager.getKnowledgeBaseConfig();

        assertThat(config.getKbId()).isEqualTo("kb-1");
        assertThat(config.getIndexType()).isEqualTo("vector");
        assertThat(config.isUseGraph()).isTrue();
        assertThat(config.getChunkSize()).isEqualTo(256);
        assertThat(config.getChunkOverlap()).isEqualTo(32);
        assertThat(config.isUseCaptionForImages()).isTrue();
        assertThat(manager.getConfig(KnowledgeBaseConfig.class)).isSameAs(config);
    }

    @Test
    void loadFromFileJsonStoresKnowledgeBaseConfig() throws Exception {
        ConfigManager manager = new ConfigManager();

        manager.loadFromFile(writeJsonConfig().toString());

        KnowledgeBaseConfig config = manager.getKnowledgeBaseConfig();
        assertThat(config.getKbId()).isEqualTo("kb-1");
        assertThat(config.getIndexType()).isEqualTo("vector");
    }

    @Test
    void loadFromFileYamlStoresKnowledgeBaseConfig() throws Exception {
        Path yaml = tempDir.resolve("config.yaml");
        Files.writeString(yaml, """
                kb_id: test_kb
                index_type: vector
                """);
        ConfigManager manager = new ConfigManager();

        manager.loadFromFile(yaml.toString());

        KnowledgeBaseConfig config = manager.getKnowledgeBaseConfig();
        assertThat(config.getKbId()).isEqualTo("test_kb");
        assertThat(config.getIndexType()).isEqualTo("vector");
    }

    @Test
    void savesYamlKnowledgeBaseConfig() throws Exception {
        ConfigManager manager = new ConfigManager();
        manager.loadFromFile(writeJsonConfig().toString());

        Path yaml = tempDir.resolve("config.yaml");
        manager.saveToFile(yaml.toString());

        String output = Files.readString(yaml);
        assertThat(output).contains("kb_id: kb-1");
        assertThat(output).contains("use_caption_for_images: true");
    }

    @Test
    void rejectsMissingFileAndUnsupportedFormat() {
        ConfigManager manager = new ConfigManager();

        assertThatThrownBy(() -> manager.loadFromFile(tempDir.resolve("missing.json").toString()))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("Configuration file does not exist");

        Path txt = tempDir.resolve("config.txt");
        assertThatThrownBy(() -> manager.saveToFile(txt.toString()))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("No configuration to save");
    }

    @Test
    void getKnowledgeBaseConfigFailsWhenNotLoaded() {
        ConfigManager manager = new ConfigManager();

        assertThatThrownBy(manager::getKnowledgeBaseConfig)
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("Knowledge base configuration not loaded");
    }

    @Test
    void getConfigReturnsUpdatedConfigByType() {
        ConfigManager manager = new ConfigManager();
        KnowledgeBaseConfig config = KnowledgeBaseConfig.builder()
                .kbId("test_kb")
                .build();

        manager.updateConfig(config);

        KnowledgeBaseConfig retrievedConfig = manager.getConfig(KnowledgeBaseConfig.class);
        assertThat(retrievedConfig).isNotNull();
        assertThat(retrievedConfig.getKbId()).isEqualTo("test_kb");
    }

    @Test
    void getConfigReturnsNullWhenConfigTypeIsMissing() {
        ConfigManager manager = new ConfigManager();

        KnowledgeBaseConfig config = manager.getConfig(KnowledgeBaseConfig.class);

        assertThat(config).isNull();
    }

    @Test
    void updateConfigStoresByTypeNameWithoutOverwritingKnowledgeBaseSlot() throws Exception {
        ConfigManager manager = new ConfigManager(writeJsonConfig().toString());
        KnowledgeBaseConfig updated = KnowledgeBaseConfig.builder()
                .kbId("kb-2")
                .indexType("hybrid")
                .useGraph(false)
                .chunkSize(1024)
                .chunkOverlap(8)
                .useCaptionForImages(false)
                .build();

        manager.updateConfig(updated);

        assertThat(manager.getConfig(KnowledgeBaseConfig.class)).isSameAs(manager.getKnowledgeBaseConfig());
        assertThat(manager.getKnowledgeBaseConfig().getKbId()).isEqualTo("kb-1");
    }

    private Path writeJsonConfig() throws Exception {
        Path json = tempDir.resolve("config.json");
        Files.writeString(json, """
                {
                  "kb_id": "kb-1",
                  "index_type": "vector",
                  "use_graph": true,
                  "chunk_size": 256,
                  "chunk_overlap": 32,
                  "use_caption_for_images": true
                }
                """);
        return json;
    }
}
