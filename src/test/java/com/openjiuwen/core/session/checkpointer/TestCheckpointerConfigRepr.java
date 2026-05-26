/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.runner.DistributedConfig;
import com.openjiuwen.core.runner.MessageQueueConfig;
import com.openjiuwen.core.runner.PulsarConfig;
import com.openjiuwen.core.runner.RunnerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CheckpointerConfig, PulsarConfig, and RunnerConfig repr/toString methods.
 * Mirrors Python's tests/unit_tests/core/session/checkpointer/test_checkpointer_config_repr.py
 */
class TestCheckpointerConfigRepr {

    @Nested
    @DisplayName("CheckpointerConfigRepr tests")
    class TestCheckpointerConfigReprClass {

        @Test
        @DisplayName("test redis url password redacted")
        void testRedisUrlPasswordRedacted() {
            Map<String, Object> connection = new HashMap<>();
            connection.put("url", "redis://:My%23SecretPwd@127.0.0.1:6379/0");
            connection.put("connection_args", Map.of("protocol", 2));

            Map<String, Object> ttl = new HashMap<>();
            ttl.put("default_ttl", 60);
            ttl.put("refresh_on_read", true);

            Map<String, Object> conf = new HashMap<>();
            conf.put("connection", connection);
            conf.put("ttl", ttl);

            CheckpointerConfig config = new CheckpointerConfig("redis", conf);
            String reprStr = config.toString();

            assertFalse(reprStr.contains("My%23SecretPwd"));
            assertTrue(reprStr.contains("***"));
            assertTrue(reprStr.contains("redis://:***@127.0.0.1:6379/0"));
        }

        @Test
        @DisplayName("test str also redacts password")
        void testStrAlsoRedactsPassword() {
            Map<String, Object> connection = new HashMap<>();
            connection.put("url", "redis://:secret123@host:6379/0");

            Map<String, Object> conf = new HashMap<>();
            conf.put("connection", connection);

            CheckpointerConfig config = new CheckpointerConfig("redis", conf);
            String strOutput = config.toSimpleString();

            assertFalse(strOutput.contains("secret123"));
            assertTrue(strOutput.contains("***"));
        }

        @Test
        @DisplayName("test url without password not modified")
        void testUrlWithoutPasswordNotModified() {
            Map<String, Object> connection = new HashMap<>();
            connection.put("url", "redis://127.0.0.1:6379/0");

            Map<String, Object> conf = new HashMap<>();
            conf.put("connection", connection);

            CheckpointerConfig config = new CheckpointerConfig("redis", conf);
            String reprStr = config.toString();

            assertTrue(reprStr.contains("redis://127.0.0.1:6379/0"));
        }

        @Test
        @DisplayName("test nested url redacted")
        void testNestedUrlRedacted() {
            Map<String, Object> conf = new HashMap<>();
            conf.put("urls", List.of(
                "redis://:password1@host1:6379/0",
                "redis://:password2@host2:6379/0"
            ));

            CheckpointerConfig config = new CheckpointerConfig("redis", conf);
            String reprStr = config.toString();

            assertFalse(reprStr.contains("password1"));
            assertFalse(reprStr.contains("password2"));
        }

        @Test
        @DisplayName("test empty conf")
        void testEmptyConf() {
            CheckpointerConfig config = new CheckpointerConfig("in_memory", null);
            String reprStr = config.toString();

            assertTrue(reprStr.contains("CheckpointerConfig"));
        }
    }

    @Nested
    @DisplayName("PulsarConfigRepr tests")
    class TestPulsarConfigRepr {

        @Test
        @DisplayName("test url password redacted")
        void testUrlPasswordRedacted() {
            PulsarConfig config = PulsarConfig.builder()
                .url("pulsar://admin:secret@localhost:6650")
                .build();
            String reprStr = config.toString();

            assertFalse(reprStr.contains("secret"));
            assertTrue(reprStr.contains("***"));
        }

        @Test
        @DisplayName("test str also redacts password")
        void testStrAlsoRedactsPassword() {
            PulsarConfig config = PulsarConfig.builder()
                .url("pulsar://user:password123@broker:6650")
                .build();
            String strOutput = config.toSimpleString();

            assertFalse(strOutput.contains("password123"));
            assertTrue(strOutput.contains("***"));
        }

        @Test
        @DisplayName("test url without password")
        void testUrlWithoutPassword() {
            PulsarConfig config = PulsarConfig.builder()
                .url("pulsar://localhost:6650")
                .build();
            String reprStr = config.toString();

            assertTrue(reprStr.contains("pulsar://localhost:6650"));
        }

        @Test
        @DisplayName("test none url")
        void testNoneUrl() {
            PulsarConfig config = PulsarConfig.builder().build();
            String reprStr = config.toString();

            assertTrue(reprStr.contains("url=null"));
        }
    }

    @Nested
    @DisplayName("RunnerConfigRepr tests")
    class TestRunnerConfigRepr {

        @Test
        @DisplayName("test checkpointer config redacted in repr")
        void testCheckpointerConfigRedactedInRepr() {
            Map<String, Object> connection = new HashMap<>();
            connection.put("url", "redis://:SuperSecretPassword@host:6379/0");

            Map<String, Object> conf = new HashMap<>();
            conf.put("connection", connection);
            conf.put("type", "redis");

            RunnerConfig runnerConfig = RunnerConfig.builder()
                .checkpointerConfig(conf)
                .build();

            String reprStr = runnerConfig.toString();
            assertFalse(reprStr.contains("SuperSecretPassword"));
        }

        @Test
        @DisplayName("test distributed config pulsar url redacted")
        void testDistributedConfigPulsarUrlRedacted() {
            PulsarConfig pulsarConfig = PulsarConfig.builder()
                .url("pulsar://user:password@broker:6650")
                .build();

            MessageQueueConfig mqConfig = MessageQueueConfig.builder()
                .pulsarConfig(pulsarConfig)
                .build();

            DistributedConfig distributedConfig = DistributedConfig.builder()
                .messageQueueConfig(mqConfig)
                .build();

            RunnerConfig runnerConfig = RunnerConfig.builder()
                .distributedMode(true)
                .distributedConfig(distributedConfig)
                .build();

            String pulsarUrl = runnerConfig.getDistributedConfig()
                .getMessageQueueConfig()
                .getPulsarConfig()
                .getUrl();

            assertEquals("pulsar://user:password@broker:6650", pulsarUrl);
        }
    }
}
