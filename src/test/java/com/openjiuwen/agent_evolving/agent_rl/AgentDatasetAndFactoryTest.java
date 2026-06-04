/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code AgentDataset} and dataset helpers in
 * {@code openjiuwen.agent_evolving.agent_rl.dataset}.
 */
class AgentDatasetAndFactoryTest {

    @Test
    void agentDatasetAddsIndexAndFakeIdsFromExtraInfo() {
        Map<String, Object> row = new HashMap<>();
        row.put("extra_info", "{\"index\": 7}");
        AgentDataset dataset = new AgentDataset(new ArrayList<>(List.of(row)), null, null, null);

        Map<String, Object> item = dataset.getItem(0);

        assertFalse(dataset.isFilterOverlongPrompts());
        assertEquals(7, item.get("index"));
        assertEquals(List.of(1), item.get("fake_ids"));
    }

    @Test
    void agentDatasetFallsBackToZeroIndexForInvalidExtraInfo() {
        Map<String, Object> invalidJsonRow = new HashMap<>();
        invalidJsonRow.put("extra_info", "{not-json");
        Map<String, Object> nonMapRow = new HashMap<>();
        nonMapRow.put("extra_info", List.of("index", 9));
        AgentDataset dataset = new AgentDataset(
                new ArrayList<>(List.of(invalidJsonRow, nonMapRow)),
                null,
                null,
                null);

        assertEquals(0, dataset.getItem(0).get("index"));
        assertEquals(List.of(1), dataset.getItem(0).get("fake_ids"));
        assertEquals(0, dataset.getItem(1).get("index"));
        assertEquals(List.of(1), dataset.getItem(1).get("fake_ids"));
    }

    @Test
    void agentDatasetRequiresDataframeBeforeGetItem() {
        AgentDataset dataset = new AgentDataset();

        assertThrows(IllegalStateException.class, () -> dataset.getItem(0));
    }

    @Test
    void setTrainValFilesUpdatesMapConfig() {
        Map<String, Object> dataCfg = new HashMap<>();

        DatasetFactory.setTrainValFiles(dataCfg, "train.parquet", "val.parquet");

        assertEquals("train.parquet", dataCfg.get("train_files"));
        assertEquals("val.parquet", dataCfg.get("val_files"));
        assertEquals("train.parquet", dataCfg.get("trainFiles"));
        assertEquals("val.parquet", dataCfg.get("valFiles"));
    }

    @Test
    void setTrainValFilesUpdatesBeanConfig() {
        DataConfig dataCfg = new DataConfig();

        DatasetFactory.setTrainValFiles(dataCfg, "train.parquet", "val.parquet");

        assertEquals("train.parquet", dataCfg.getTrainFiles());
        assertEquals("val.parquet", dataCfg.getValFiles());
    }

    @Test
    void createOfflineDatasetsUsesConfigDataFilesAndSampler() {
        DataConfig dataCfg = new DataConfig();
        dataCfg.setTrainFiles("train.parquet");
        dataCfg.setValFiles("val.parquet");
        Config config = new Config(dataCfg);
        Object tokenizer = new Object();
        Object processor = new Object();

        DatasetBundle bundle = DatasetFactory.createOfflineDatasets(config, tokenizer, processor);

        AgentDataset trainDataset = (AgentDataset) bundle.getTrainDataset();
        AgentDataset valDataset = (AgentDataset) bundle.getValDataset();
        assertEquals("train.parquet", trainDataset.getDataFiles());
        assertEquals("val.parquet", valDataset.getDataFiles());
        assertSame(tokenizer, trainDataset.getTokenizer());
        assertSame(processor, trainDataset.getProcessor());
        assertSame(dataCfg, trainDataset.getConfig());
        assertTrue(bundle.getTrainSampler() instanceof DatasetFactory.RlSampler);
        DatasetFactory.RlSampler sampler = (DatasetFactory.RlSampler) bundle.getTrainSampler();
        assertSame(dataCfg, sampler.dataConfig());
        assertSame(trainDataset, sampler.trainDataset());
        assertEquals(List.of("a", "b"), bundle.getCollateFn().apply(new Object[] {"a", "b"}));
    }

    @Test
    void createOnlineDatasetsWritesDummyDataUpdatesConfigAndCleansUp() throws Exception {
        Map<String, Object> dataCfg = new HashMap<>();
        Map<String, Object> config = new HashMap<>();
        config.put("data", dataCfg);

        DatasetBundle bundle = DatasetFactory.createOnlineDatasets(config, null, null);

        String trainPath = (String) dataCfg.get("train_files");
        String valPath = (String) dataCfg.get("val_files");
        assertEquals(trainPath, valPath);
        assertEquals(trainPath, dataCfg.get("trainFiles"));
        assertEquals(valPath, dataCfg.get("valFiles"));
        Path path = Path.of(trainPath);
        assertTrue(Files.exists(path));
        String content = Files.readString(path);
        assertTrue(content.contains("\"messages\""));
        assertTrue(content.contains("\"hi\""));
        assertEquals(trainPath, ((AgentDataset) bundle.getTrainDataset()).getDataFiles());
        assertEquals(valPath, ((AgentDataset) bundle.getValDataset()).getDataFiles());

        bundle.cleanup();

        assertFalse(Files.exists(path));
    }

    private static final class DataConfig {
        private String trainFiles;
        private String valFiles;

        String getTrainFiles() {
            return trainFiles;
        }

        public void setTrainFiles(String trainFiles) {
            this.trainFiles = trainFiles;
        }

        String getValFiles() {
            return valFiles;
        }

        public void setValFiles(String valFiles) {
            this.valFiles = valFiles;
        }
    }

    private static final class Config {
        private final DataConfig data;

        private Config(DataConfig data) {
            this.data = data;
        }

        public DataConfig getData() {
            return data;
        }
    }
}
