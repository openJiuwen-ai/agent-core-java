/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleRecorderTest {

    @TempDir
    Path tempDir;

    @Test
    void recordSampleWritesJsonlAndTracksStats() throws Exception {
        Path output = tempDir.resolve("samples.jsonl");
        SampleRecorder recorder = new SampleRecorder(output, false);

        recorder.recordSample(Map.of(
                "sample_id", "s1",
                "trajectory", Map.of("input_ids", List.of(1, 2, 3), "other", "ok")
        ));

        assertEquals(Map.of("total_samples", 1), recorder.snapshotStats());
        String text = Files.readString(output);
        assertTrue(text.contains("\"sample_id\":\"s1\""));
        assertTrue(text.contains("\"input_ids_len\":3"));
    }
}
