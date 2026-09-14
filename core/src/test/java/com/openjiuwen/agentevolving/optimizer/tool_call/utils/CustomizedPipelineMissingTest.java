/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrors Python's {@code test_customized_pipeline} in
 * {@code tests/unit_tests/agent_evolving/optimizer/tool_call/test_customized_pipeline.py}.
 */
class CustomizedPipelineMissingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    private Path tempDir;

    @Test
    void customizedPipelineExampleAndMerge() throws Exception {
        Map<String, Object> tool = Map.of("name", "search");
        Map<String, Object> config = config();
        RecordingPipeline pipeline = new RecordingPipeline();

        List<Object> out = pipeline.run("example", tool, config, callable());

        assertThat(out).isEqualTo(generated("generated-search"));
        Path saved = tempDir.resolve("search.json");
        assertThat(saved).exists();
        assertThat(OBJECT_MAPPER.readValue(saved.toFile(), Object.class)).isEqualTo(out);

        Files.writeString(saved, OBJECT_MAPPER.writeValueAsString(generated("old")));
        List<Object> out2 = pipeline.run("description", tool, config, callable());

        assertThat(out2).hasSize(2);
        assertThat(descriptionAt(out2, 0)).isEqualTo("old");
    }

    @Test
    void customizedPipelineErrorPaths() {
        Map<String, Object> config = config();

        assertThatThrownBy(() -> CustomizedPipeline.customizedPipeline(
                "example",
                Map.of("name", "t"),
                withValue(config, "fn_call_path", "x"),
                callable()
        )).isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> CustomizedPipeline.customizedPipeline("example", Map.of("name", "t"), config, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new RecordingPipeline().run("bad", Map.of("name", "t"), config, callable()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Map<String, Object> config() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("beam_width", 1);
        config.put("expand_num", 1);
        config.put("max_depth", 1);
        config.put("num_workers", 1);
        config.put("verbose", false);
        config.put("top_k", 1);
        config.put("save_dir", tempDir.toString());
        return config;
    }

    private static Function<Map<String, Object>, Object> callable() {
        return value -> value;
    }

    private static Map<String, Object> withValue(Map<String, Object> source, String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(source);
        copy.put(key, value);
        return copy;
    }

    private static List<Object> generated(String description) {
        return List.of(List.of(Map.of("description", description)));
    }

    private static Object descriptionAt(List<Object> rows, int rowIndex) {
        Object row = rows.get(rowIndex);
        assertThat(row).isInstanceOf(List.class);
        Object item = ((List<?>) row).get(0);
        assertThat(item).isInstanceOf(Map.class);
        return ((Map<?, ?>) item).get("description");
    }

    private static final class RecordingPipeline extends CustomizedPipeline {

        @Override
        protected Object createExampleMethod(
                Map<String, Object> config,
                Object callApiFn,
                SimpleEval evalFn,
                List<String> apiKeys,
                List<String> nonOptParams
        ) {
            return "example-method";
        }

        @Override
        protected Object createDescriptionMethod(Map<String, Object> config, SimpleEval evalFn) {
            return "description-method";
        }

        @Override
        protected List<?> search(BeamSearch singleSearch, Map<String, Object> tool) {
            return generated("generated-" + tool.get("name"));
        }
    }
}
