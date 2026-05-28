/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.runner.drunner;

import com.openjiuwen.core.runner.DistributedConfig;
import com.openjiuwen.core.runner.MessageQueueConfig;
import com.openjiuwen.core.runner.MessageQueueType;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription.ReplyTopicSubscription;
import com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription.ResponseCollector;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DMessageType;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReplyTopicSubscription.
 * Mirrors Python's tests/unit_tests/core/runner/dunner/test_reply_topic_subscription.py
 * Ported from Python: agent-core-0.1.12/tests/unit_tests/core/runner/dunner/test_reply_topic_subscription.py
 */
@ExtendWith(MockitoExtension.class)
class TestReplyTopicSubscription {

    private static final RunnerConfig FAKE_MQ_CONFIG = RunnerConfig.builder()
            .distributedMode(true)
            .distributedConfig(DistributedConfig.builder()
                    .requestTimeout(5.0)
                    .messageQueueConfig(MessageQueueConfig.builder()
                            .type(MessageQueueType.FAKE.getValue())
                            .build())
                    .build())
            .build();

    @BeforeEach
    void setUp() {
        Runner.setConfig(FAKE_MQ_CONFIG);
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Nested
    @DisplayName("ReplyTopicSubscription tests")
    class SubscriptionTests {

        @Test
        @DisplayName("test_normal_message_reception: Test normal registration and message reception process")
        void testNormalMessageReception() throws Exception {
            Runner.start();
            ReplyTopicSubscription replySub = Runner.systemReplySub();
            String messageId = "test_msg_123";
            String remoteId = "agent_456";

            ResponseCollector collector = replySub.registerCollector(messageId, remoteId, null, null);

            DmqResponseMessage msg = new DmqResponseMessage();
            msg.setType(DMessageType.OUTPUT);
            msg.setSenderId(remoteId);
            msg.setMessageId(messageId);
            msg.setBody("test_payload");
            msg.setLastChunk(true);

            replySub.onMessage(msg);

            Object result = collector.result(1.0);
            assertEquals("test_payload", result);
        }

        @Test
        @DisplayName("test_unregistered_message_handling: Test message handling when collector is not registered")
        void testUnregisteredMessageHandling() {
            Runner.start();
            ReplyTopicSubscription replySub = Runner.systemReplySub();

            DmqResponseMessage unregMsg = new DmqResponseMessage();
            unregMsg.setType(DMessageType.OUTPUT);
            unregMsg.setSenderId("unknown_agent");
            unregMsg.setMessageId("unknown_msg");
            unregMsg.setBody("unregistered");

            assertDoesNotThrow(() -> replySub.onMessage(unregMsg));
        }
    }
}