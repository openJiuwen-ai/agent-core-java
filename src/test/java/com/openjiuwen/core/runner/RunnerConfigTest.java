// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 runner_config.py 对应的配置类:
 * - MessageQueueType: 消息队列类型枚举
 * - PulsarConfig: Pulsar配置
 * - MessageQueueConfig: 消息队列配置
 * - DistributedConfig: 分布式配置
 * - RunnerConfig: Runner全局配置
 */
class RunnerConfigTest {

    @Nested
    @DisplayName("MessageQueueType 枚举测试")
    class MessageQueueTypeTest {

        @Test
        @DisplayName("枚举值PULSAR存在且值正确")
        void testPulsarValue() {
            assertEquals("pulsar", MessageQueueType.PULSAR.getValue());
        }

        @Test
        @DisplayName("枚举值FAKE存在且值正确")
        void testFakeValue() {
            assertEquals("fake", MessageQueueType.FAKE.getValue());
        }

        @Test
        @DisplayName("枚举包含两个值")
        void testEnumCount() {
            assertEquals(2, MessageQueueType.values().length);
        }

        @Test
        @DisplayName("可以通过值获取枚举")
        void testFromValue() {
            assertEquals(MessageQueueType.PULSAR, MessageQueueType.fromValue("pulsar"));
            assertEquals(MessageQueueType.FAKE, MessageQueueType.fromValue("fake"));
        }

        @Test
        @DisplayName("无效值返回null或抛出异常")
        void testFromInvalidValue() {
            assertNull(MessageQueueType.fromValue("invalid"));
        }
    }

    @Nested
    @DisplayName("PulsarConfig 测试")
    class PulsarConfigTest {

        @Test
        @DisplayName("默认构造函数 - url为null，maxWorkers为8")
        void testDefaultValues() {
            PulsarConfig config = new PulsarConfig();
            assertNull(config.url());
            assertEquals(8, config.maxWorkers());
        }

        @Test
        @DisplayName("自定义构造函数")
        void testCustomValues() {
            PulsarConfig config = new PulsarConfig("pulsar://localhost:6650", 16);
            assertEquals("pulsar://localhost:6650", config.url());
            assertEquals(16, config.maxWorkers());
        }
    }

    @Nested
    @DisplayName("MessageQueueConfig 测试")
    class MessageQueueConfigTest {

        @Test
        @DisplayName("默认构造函数 - type为pulsar，pulsarConfig为null")
        void testDefaultValues() {
            MessageQueueConfig config = new MessageQueueConfig();
            assertEquals(MessageQueueType.PULSAR.getValue(), config.type());
            assertNull(config.pulsarConfig());
        }

        @Test
        @DisplayName("自定义构造函数")
        void testCustomValues() {
            PulsarConfig pulsarConfig = new PulsarConfig("pulsar://localhost:6650", 4);
            MessageQueueConfig config = new MessageQueueConfig(MessageQueueType.FAKE.getValue(), pulsarConfig);
            assertEquals(MessageQueueType.FAKE.getValue(), config.type());
            assertNotNull(config.pulsarConfig());
            assertEquals("pulsar://localhost:6650", config.pulsarConfig().url());
        }
    }

    @Nested
    @DisplayName("DistributedConfig 测试")
    class DistributedConfigTest {

        @Test
        @DisplayName("默认值测试")
        void testDefaultValues() {
            DistributedConfig config = new DistributedConfig();
            assertEquals(30.0, config.getRequestTimeout());
            assertEquals(10000, config.getMaxRequestConcurrency());
            assertNotNull(config.getMessageQueueConfig());
            assertEquals("openjiuwen.single_agent.{agent_id}.{version}", config.getAgentTopicTemplate());
            assertEquals("openjiuwen.reply.runner.{instance_id}", config.getReplyTopicTemplate());
        }

        @Test
        @DisplayName("getAgentTopicTemplate 无前缀")
        void testGetAgentTopicTemplateWithoutPrefix() {
            DistributedConfig config = new DistributedConfig();
            String template = config.getAgentTopicTemplate("");
            assertEquals("openjiuwen.single_agent.{agent_id}.{version}", template);
        }

        @Test
        @DisplayName("getAgentTopicTemplate 有前缀")
        void testGetAgentTopicTemplateWithPrefix() {
            DistributedConfig config = new DistributedConfig();
            String template = config.getAgentTopicTemplate("prod");
            assertEquals("prod.openjiuwen.single_agent.{agent_id}.{version}", template);
        }

        @Test
        @DisplayName("getReplyTopicTemplate 无前缀")
        void testGetReplyTopicTemplateWithoutPrefix() {
            DistributedConfig config = new DistributedConfig();
            String template = config.getReplyTopicTemplate("");
            assertEquals("openjiuwen.reply.runner.{instance_id}", template);
        }

        @Test
        @DisplayName("getReplyTopicTemplate 有前缀")
        void testGetReplyTopicTemplateWithPrefix() {
            DistributedConfig config = new DistributedConfig();
            String template = config.getReplyTopicTemplate("prod");
            assertEquals("prod.openjiuwen.reply.runner.{instance_id}", template);
        }

        @Test
        @DisplayName("setter方法测试")
        void testSetters() {
            DistributedConfig config = new DistributedConfig();
            config.setRequestTimeout(60.0);
            config.setMaxRequestConcurrency(5000);
            
            assertEquals(60.0, config.getRequestTimeout());
            assertEquals(5000, config.getMaxRequestConcurrency());
        }
    }

    @Nested
    @DisplayName("RunnerConfig 测试")
    class RunnerConfigClassTest {

        @BeforeEach
        void setUp() {
            // 重置全局配置
            RunnerConfig.setRunnerConfig(null);
        }

        @AfterEach
        void tearDown() {
            // 清理全局配置
            RunnerConfig.setRunnerConfig(null);
        }

        @Test
        @DisplayName("默认值测试")
        void testDefaultValues() {
            RunnerConfig config = new RunnerConfig();
            assertTrue(config.isDistributedMode());
            assertNotNull(config.getDistributedConfig());
            assertEquals("", config.getEnvPrefix());
            assertNotNull(config.getInstanceId());
            assertFalse(config.getInstanceId().isEmpty());
        }

        @Test
        @DisplayName("instanceId是UUID格式")
        void testInstanceIdFormat() {
            RunnerConfig config = new RunnerConfig();
            String instanceId = config.getInstanceId();
            // UUID格式: 8-4-4-4-12
            assertTrue(instanceId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        }

        @Test
        @DisplayName("agentTopicTemplate 方法")
        void testAgentTopicTemplate() {
            RunnerConfig config = new RunnerConfig();
            config.setEnvPrefix("test");
            String template = config.agentTopicTemplate();
            assertEquals("test.openjiuwen.single_agent.{agent_id}.{version}", template);
        }

        @Test
        @DisplayName("replyTopicTemplate 方法")
        void testReplyTopicTemplate() {
            RunnerConfig config = new RunnerConfig();
            config.setEnvPrefix("test");
            String template = config.replyTopicTemplate();
            assertEquals("test.openjiuwen.reply.runner.{instance_id}", template);
        }

        @Test
        @DisplayName("setRunnerConfig 和 getRunnerConfig")
        void testSetAndGetRunnerConfig() {
            RunnerConfig config = new RunnerConfig();
            config.setDistributedMode(false);
            config.setEnvPrefix("custom");
            
            RunnerConfig.setRunnerConfig(config);
            
            RunnerConfig retrieved = RunnerConfig.getRunnerConfig();
            assertSame(config, retrieved);
            assertFalse(retrieved.isDistributedMode());
            assertEquals("custom", retrieved.getEnvPrefix());
        }

        @Test
        @DisplayName("getRunnerConfig 返回DEFAULT当未设置时")
        void testGetRunnerConfigReturnsDefault() {
            RunnerConfig config = RunnerConfig.getRunnerConfig();
            assertNotNull(config);
            // DEFAULT_RUNNER_CONFIG 的特征
            assertFalse(config.isDistributedMode());
            assertEquals(MessageQueueType.FAKE.getValue(), 
                config.getDistributedConfig().getMessageQueueConfig().type());
        }
    }

    @Nested
    @DisplayName("DEFAULT_RUNNER_CONFIG 常量测试")
    class DefaultRunnerConfigTest {

        @Test
        @DisplayName("DEFAULT_RUNNER_CONFIG存在且配置正确")
        void testDefaultRunnerConfigValues() {
            RunnerConfig defaultConfig = RunnerConfig.DEFAULT_RUNNER_CONFIG;
            assertNotNull(defaultConfig);
            assertFalse(defaultConfig.isDistributedMode());
            
            DistributedConfig distConfig = defaultConfig.getDistributedConfig();
            assertNotNull(distConfig);
            assertEquals(30.0, distConfig.getRequestTimeout());
            
            MessageQueueConfig mqConfig = distConfig.getMessageQueueConfig();
            assertNotNull(mqConfig);
            assertEquals(MessageQueueType.FAKE.getValue(), mqConfig.type());
        }
    }
}

