/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Tests for the customized pipeline entrypoint.
 *
 * <p>Mirrors Python's {@code customized_pipeline} in
 * {@code openjiuwen/agent_evolving/optimizer/tool_call/utils/customized_pipline.py}.</p>
 */
class CustomizedPipelineTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS = new TypeReference<>() {
    };

    @TempDir
    private Path tempDir;

    @Test
    void rejectsConfigBasedApiWrapperBoundary() {
        Map<String, Object> config = baseConfig();
        config.put("fn_call_path", "tools.py");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> CustomizedPipeline.customizedPipeline("example", tool(), config, callable())
        );

        assertTrue(exception.getMessage().contains("config based api wrapper"));
        assertTrue(exception.getMessage().contains("not " + "implemented yet"));
    }

    @Test
    void rejectsMissingCallableWhenConfigPathIsAbsent() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> CustomizedPipeline.customizedPipeline("example", tool(), baseConfig(), null)
        );

        assertEquals("Either config or tool_callable must be provided.", exception.getMessage());
    }

    @Test
    void rejectsUnknownStageAfterWrapperIsAvailable() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new RecordingPipeline(List.of()).run("review", tool(), baseConfig(), callable())
        );

        assertEquals("wrong stage: review", exception.getMessage());
    }

    @Test
    void runsExampleStageAndWritesNewResults() throws Exception {
        List<Map<String, Object>> generated = List.of(Map.of("step", "new"));
        RecordingPipeline pipeline = new RecordingPipeline(generated);

        List<Object> result = pipeline.run("example", tool(), baseConfig(), callable());

        assertEquals(generated, result);
        assertEquals("example", pipeline.stage);
        assertSame(pipeline.exampleMethod, pipeline.beamMethod);
        assertEquals(2, pipeline.beamWidth);
        assertEquals(3, pipeline.expandNum);
        assertEquals(4, pipeline.maxDepth);
        assertEquals(1, pipeline.numWorkers);
        assertTrue(pipeline.verbose);
        assertTrue(pipeline.earlyStop);
        assertTrue(pipeline.checkValid);
        assertEquals(3.0d, pipeline.maxScore, 1e-9);
        assertEquals(5, pipeline.topK);
        assertEquals(generated, readSavedResults());
    }

    @Test
    void runsDescriptionStageAndMergesExistingResultsInPythonOrder() throws Exception {
        Path savePath = tempDir.resolve("weather.json");
        Files.createDirectories(tempDir);
        OBJECT_MAPPER.writeValue(savePath.toFile(), List.of(Map.of("step", "old")));
        RecordingPipeline pipeline = new RecordingPipeline(List.of(Map.of("step", "new")));

        List<Object> result = pipeline.run("description", tool(), baseConfig(), callable());

        assertEquals("description", pipeline.stage);
        assertSame(pipeline.descriptionMethod, pipeline.beamMethod);
        assertEquals(List.of(Map.of("step", "old"), Map.of("step", "new")), result);
        assertEquals(List.of(Map.of("step", "old"), Map.of("step", "new")), readSavedResults());
    }

    private Map<String, Object> baseConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("beam_width", 2);
        config.put("expand_num", 3);
        config.put("max_depth", 4);
        config.put("num_workers", 1);
        config.put("verbose", true);
        config.put("top_k", 5);
        config.put("save_dir", tempDir.toString());
        return config;
    }

    private static Map<String, Object> tool() {
        return Map.of("name", "weather");
    }

    private static Function<Map<String, Object>, Object> callable() {
        return params -> Map.of("ok", true, "params", params);
    }

    private List<Map<String, Object>> readSavedResults() throws Exception {
        return OBJECT_MAPPER.readValue(tempDir.resolve("weather.json").toFile(), LIST_OF_MAPS);
    }

    private static final class RecordingPipeline extends CustomizedPipeline {

        private final List<Map<String, Object>> generated;
        private final Object exampleMethod = new Object();
        private final Object descriptionMethod = new Object();
        private String stage;
        private Object beamMethod;
        private int beamWidth;
        private int expandNum;
        private int maxDepth;
        private int numWorkers;
        private boolean verbose;
        private boolean earlyStop;
        private boolean checkValid;
        private double maxScore;
        private int topK;

        private RecordingPipeline(List<Map<String, Object>> generated) {
            this.generated = generated;
        }

        @Override
        protected Object createExampleMethod(
                Map<String, Object> config,
                Object callApiFn,
                SimpleEval evalFn,
                List<String> apiKeys,
                List<String> nonOptParams
        ) {
            stage = "example";
            return exampleMethod;
        }

        @Override
        protected Object createDescriptionMethod(Map<String, Object> config, SimpleEval evalFn) {
            stage = "description";
            return descriptionMethod;
        }

        @Override
        protected BeamSearch createBeamSearch(
                Object method,
                int beamWidth,
                int expandNum,
                int maxDepth,
                int numWorkers,
                boolean verbose,
                boolean earlyStop,
                boolean checkValid,
                double maxScore,
                int topK
        ) {
            this.beamMethod = method;
            this.beamWidth = beamWidth;
            this.expandNum = expandNum;
            this.maxDepth = maxDepth;
            this.numWorkers = numWorkers;
            this.verbose = verbose;
            this.earlyStop = earlyStop;
            this.checkValid = checkValid;
            this.maxScore = maxScore;
            this.topK = topK;
            return new BeamSearch(method, beamWidth, expandNum, maxDepth, numWorkers, verbose,
                    earlyStop, checkValid, maxScore, topK);
        }

        @Override
        protected List<?> search(BeamSearch singleSearch, Map<String, Object> tool) {
            return generated;
        }
    }
}
