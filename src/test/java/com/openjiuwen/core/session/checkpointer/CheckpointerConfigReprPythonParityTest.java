/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.runner.PulsarConfig;
import com.openjiuwen.core.runner.RunnerConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code test_checkpointer_config_repr.py} in
 * {@code tests/unit_tests/core/session/checkpointer/test_checkpointer_config_repr.py}.
 */
class CheckpointerConfigReprPythonParityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testRedisUrlPasswordRedacted() {
        CheckpointerConfig config = new CheckpointerConfig("redis", Map.of(
                "connection", Map.of(
                        "url", "redis://:My%23SecretPwd@127.0.0.1:6379/0",
                        "connection_args", Map.of("protocol", 2)),
                "ttl", Map.of("default_ttl", 60, "refresh_on_read", true)));

        String repr = config.repr();

        assertThat(repr).doesNotContain("My%23SecretPwd");
        assertThat(repr).contains("***");
        assertThat(repr).contains("redis://:***@127.0.0.1:6379/0");
    }

    @Test
    void testStrAlsoRedactsPassword() {
        CheckpointerConfig config = new CheckpointerConfig("redis", Map.of(
                "connection", Map.of("url", "redis://:secret123@host:6379/0")));

        String output = config.toString();

        assertThat(output).doesNotContain("secret123");
        assertThat(output).contains("***");
    }

    @Test
    void testUrlWithoutPasswordNotModified() {
        CheckpointerConfig config = new CheckpointerConfig("redis", Map.of(
                "connection", Map.of("url", "redis://127.0.0.1:6379/0")));

        String repr = config.repr();

        assertThat(repr).contains("redis://127.0.0.1:6379/0");
    }

    @Test
    void testNestedUrlRedacted() {
        CheckpointerConfig config = new CheckpointerConfig("redis", Map.of(
                "urls", List.of(
                        "redis://:password1@host1:6379/0",
                        "redis://:password2@host2:6379/0")));

        String repr = config.repr();

        assertThat(repr).doesNotContain("password1");
        assertThat(repr).doesNotContain("password2");
    }

    @Test
    void testEmptyConf() {
        CheckpointerConfig config = new CheckpointerConfig("in_memory", null);

        String repr = config.repr();

        assertThat(repr).contains("CheckpointerConfig");
    }

    @Test
    void testPulsarUrlPasswordRedacted() {
        PulsarConfig config = PulsarConfig.builder()
                .url("pulsar://admin:secret@localhost:6650")
                .build();

        String repr = config.repr();

        assertThat(repr).doesNotContain("secret");
        assertThat(repr).contains("***");
    }

    @Test
    void testPulsarStrAlsoRedactsPassword() {
        PulsarConfig config = PulsarConfig.builder()
                .url("pulsar://user:password123@broker:6650")
                .build();

        String output = config.toString();

        assertThat(output).doesNotContain("password123");
        assertThat(output).contains("***");
    }

    @Test
    void testPulsarUrlWithoutPassword() {
        PulsarConfig config = PulsarConfig.builder()
                .url("pulsar://localhost:6650")
                .build();

        String repr = config.repr();

        assertThat(repr).contains("pulsar://localhost:6650");
    }

    @Test
    void testPulsarNoneUrl() {
        PulsarConfig config = new PulsarConfig();

        String repr = config.repr();

        assertThat(repr).contains("url=None");
    }

    @Test
    void testRunnerCheckpointerConfigRedactedInRepr() {
        RunnerConfig runnerConfig = RunnerConfig.builder()
                .checkpointerConfig(new CheckpointerConfig("redis", Map.of(
                        "connection", Map.of("url", "redis://:SuperSecretPassword@host:6379/0"))))
                .build();

        String repr = runnerConfig.toString();

        assertThat(repr).doesNotContain("SuperSecretPassword");
    }

    @Test
    void testDistributedConfigPulsarUrlRetainedInModelDump() {
        RunnerConfig runnerConfig = RunnerConfig.builder()
                .distributedMode(true)
                .build();
        runnerConfig.getDistributedConfig().getMessageQueueConfig().setPulsarConfig(PulsarConfig.builder()
                .url("pulsar://user:password@broker:6650")
                .build());

        Map<String, Object> modelDump = MAPPER.convertValue(runnerConfig, new TypeReference<>() {
        });
        Map<String, Object> distributedConfig = asMap(modelDump.get("distributed_config"));
        Map<String, Object> messageQueueConfig = asMap(distributedConfig.get("message_queue_config"));
        Map<String, Object> pulsarConfig = asMap(messageQueueConfig.get("pulsar_config"));

        assertThat(pulsarConfig.get("url")).isEqualTo("pulsar://user:password@broker:6650");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}
