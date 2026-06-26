/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.harness.cli.agent.AgentBackend;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's module functions in
 * {@code openjiuwen/harness/cli/ui/runner.py}.
 */
class CliRunnerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void jsonOutputCollectsLlmOutputAndSkipsDuplicateAnswer() throws Exception {
        Fixture fixture = fixture(List.of(
                chunk(CliRenderer.CHUNK_LLM_OUTPUT, 0, Map.of("content", "hel")),
                chunk(CliRenderer.CHUNK_ANSWER, 1, Map.of("content", "ignored")),
                chunk(CliRenderer.CHUNK_LLM_OUTPUT, 2, Map.of("content", "lo"))
        ));

        int exitCode = fixture.runner.runOnce(Map.of("model", "unit-model"), "hello", CliRunner.OUTPUT_JSON);

        assertThat(exitCode).isZero();
        Map<String, Object> output = MAPPER.readValue(fixture.stdoutText(), new TypeReference<>() {
        });
        assertThat(output)
                .containsEntry("result", "hello")
                .containsEntry("chunks", 3)
                .containsEntry("model", "unit-model");
        assertThat(fixture.backend.started).isTrue();
        assertThat(fixture.backend.stopped).isTrue();
    }

    @Test
    void streamJsonOutputsOneLinePerChunk() throws Exception {
        Fixture fixture = fixture(List.of(
                chunk(CliRenderer.CHUNK_MESSAGE, 0, Map.of("content", "ready")),
                chunk(CliRenderer.CHUNK_ANSWER, 1, "done")
        ));

        int exitCode = fixture.runner.runOnce(Map.of("model", "unit-model"), "hello", CliRunner.OUTPUT_STREAM_JSON);

        assertThat(exitCode).isZero();
        String[] lines = fixture.stdoutText().strip().split("\\R");
        assertThat(lines).hasSize(2);
        assertThat(MAPPER.readValue(lines[0], new TypeReference<Map<String, Object>>() {
        })).containsEntry("type", CliRenderer.CHUNK_MESSAGE);
        assertThat(MAPPER.readValue(lines[1], new TypeReference<Map<String, Object>>() {
        })).containsEntry("payload", "done");
    }

    @Test
    void textOutputDelegatesToRenderer() {
        Fixture fixture = fixture(List.of(
                chunk(CliRenderer.CHUNK_LLM_OUTPUT, 0, Map.of("content", "hello text"))
        ));

        int exitCode = fixture.runner.runOnce(Map.of("model", "unit-model"), "hello", CliRunner.OUTPUT_TEXT);

        assertThat(exitCode).isZero();
        assertThat(fixture.stdoutText()).contains("hello text");
    }

    @Test
    void unknownOutputFormatPrintsErrorAndReturnsOne() {
        Fixture fixture = fixture(List.of());

        int exitCode = fixture.runner.runOnce(Map.of("model", "unit-model"), "hello", "xml");

        assertThat(exitCode).isEqualTo(1);
        assertThat(fixture.stderrText()).contains("Unknown output format: xml");
        assertThat(fixture.backend.stopped).isTrue();
    }

    @Test
    void runErrorPrintsFriendlyMessageAndStopsBackend() {
        Fixture fixture = fixture(List.of());
        fixture.backend.runError = new IllegalStateException("429 rate_limit exceeded");

        int exitCode = fixture.runner.runOnce(Map.of("model", "unit-model"), "hello", CliRunner.OUTPUT_JSON);

        assertThat(exitCode).isEqualTo(1);
        assertThat(fixture.stderrText()).contains("Rate limited. Please try again later.");
        assertThat(fixture.backend.stopped).isTrue();
    }

    private static Fixture fixture(List<Object> chunks) {
        FakeBackend backend = new FakeBackend(chunks);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        CliRunner runner = new CliRunner(
                ignored -> backend,
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
        return new Fixture(runner, backend, stdout, stderr);
    }

    private static Map<String, Object> chunk(String type, int index, Object payload) {
        return Map.of("type", type, "index", index, "payload", payload);
    }

    private record Fixture(CliRunner runner, FakeBackend backend,
                           ByteArrayOutputStream stdout, ByteArrayOutputStream stderr) {
        public String stdoutText() {
            return stdout.toString(StandardCharsets.UTF_8);
        }

        public String stderrText() {
            return stderr.toString(StandardCharsets.UTF_8);
        }
    }

    private static final class FakeBackend implements AgentBackend {
        private final List<Object> chunks = new ArrayList<>();
        private boolean started;
        private boolean stopped;
        private RuntimeException runError;

        private FakeBackend(List<Object> chunks) {
            this.chunks.addAll(chunks);
        }

        @Override
        public CompletionStage<Void> start() {
            started = true;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> stop() {
            stopped = true;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Iterator<Object>> runStreaming(Object query, String sessionId) {
            if (runError != null) {
                return CompletableFuture.failedFuture(runError);
            }
            return CompletableFuture.completedFuture(chunks.iterator());
        }

        @Override
        public CompletionStage<Void> abort() {
            return CompletableFuture.completedFuture(null);
        }
    }
}
