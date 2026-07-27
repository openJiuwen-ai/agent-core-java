package com.openjiuwen.agent_evolving.agent_rl.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class OfflineConfigSchemaTest {

    @Test
    void trainingAliasesPreferLegacyFields() {
        TrainingConfig config = new TrainingConfig();
        config.setTrainDataPath("train-data.jsonl");
        config.setValDataPath("val-data.jsonl");
        config.setTrainFiles("train-files.jsonl");
        config.setValFiles("val-files.jsonl");

        assertEquals("train-files.jsonl", config.getResolvedTrainFiles());
        assertEquals("val-files.jsonl", config.getResolvedValFiles());
    }

    @Test
    void rlConfigCreatesExpectedDefaultNestedSchemas() {
        RLConfig config = new RLConfig(new TrainingConfig());

        assertNotNull(config.getRollout());
        assertNotNull(config.getRuntime());
        assertNotNull(config.getPersistence());
        assertEquals(8, config.getRollout().getRolloutN());
        assertEquals("You are a helpful assistant.", config.getRuntime().getSystemPrompt());
        assertEquals(100, config.getPersistence().getFlushInterval());
    }
}
