// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.dmessagequeue.dsubscription;

import com.openjiuwen.core.runner.DistributedConfig;
import com.openjiuwen.core.runner.MessageQueueConfig;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.drunner.dmessagequeue.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessagequeue.DmqResponseMessage;
import com.openjiuwen.core.runner.drunner.dmessagequeue.FakeMQ;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ReplyTopicSubscription.
 * 
 * <p>Note: The Python tests use Runner.start()/stop() which is not yet available (Batch 8).
 * These tests directly instantiate ReplyTopicSubscription with FakeMQ for isolated testing.
 * 
 * 对应Python: tests/unit_tests/core/runner/drunner/dmessage_queue/dsubscription/test_reply_topic_subscription.py
 */
class ReplyTopicSubscriptionTest {

    private FakeMQ fakeMQ;
    private ReplyTopicSubscription replySub;

    @BeforeEach
    void setUp() {
        // Set up RunnerConfig
        RunnerConfig config = new RunnerConfig();
        config.setDistributedMode(true);
        DistributedConfig distConfig = new DistributedConfig();
        distConfig.setRequestTimeout(5.0);
        distConfig.setMessageQueueConfig(new MessageQueueConfig("fake", null));
        config.setDistributedConfig(distConfig);
        RunnerConfig.setRunnerConfig(config);

        // Create and start FakeMQ
        fakeMQ = new FakeMQ();
        fakeMQ.start();

        // Create subscription with explicit topic
        replySub = new ReplyTopicSubscription(fakeMQ, "test-reply-topic");
    }

    @AfterEach
    void tearDown() {
        if (replySub != null && replySub.isActive()) {
            replySub.deactivate().join();
        }
        if (fakeMQ != null) {
            fakeMQ.stop().join();
        }
    }

    @Test
    @DisplayName("test_normal_message_reception - registration and message reception")
    void testNormalMessageReception() throws TimeoutException {
        replySub.activate();

        String messageId = "test_msg_123";
        String remoteId = "agent_456";
        ResponseCollector collector = replySub.registerCollector(messageId, remoteId, null, 5.0);

        // Verify collector registration
        CollectorKey key = replySub.makeKey(remoteId, messageId, null);
        assertTrue(replySub.getCollectors().containsKey(key));

        // Send test message directly to on_message
        DmqResponseMessage msg = DmqResponseMessage.builder()
            .type(DMessageType.OUTPUT)
            .senderId(remoteId)
            .messageId(messageId)
            .payload("test_payload")
            .lastChunk(true)
            .build();
        replySub.onMessage(msg);

        // Verify message reception
        Object result = collector.result(1.0);
        assertEquals("test_payload", result);
    }

    @Test
    @DisplayName("test_unregistered_message_handling - discard messages for unknown collector")
    void testUnregisteredMessageHandling() {
        replySub.activate();

        DmqResponseMessage unregMsg = DmqResponseMessage.builder()
            .type(DMessageType.OUTPUT)
            .senderId("unknown_agent")
            .messageId("unknown_msg")
            .payload("unregistered")
            .build();

        // Unregistered message handling should not cause exceptions
        assertDoesNotThrow(() -> replySub.onMessage(unregMsg));
    }

    @Test
    @DisplayName("test_register_collector_when_inactive - throws CancellationException")
    void testRegisterCollectorWhenInactive() {
        // Don't activate the subscription
        assertThrows(CancellationException.class,
            () -> replySub.registerCollector("msg1", "remote1", null, 5.0));
    }

    @Test
    @DisplayName("test_register_duplicate_collector - throws RuntimeException")
    void testRegisterDuplicateCollector() {
        replySub.activate();
        replySub.registerCollector("msg1", "remote1", null, 5.0);
        assertThrows(RuntimeException.class,
            () -> replySub.registerCollector("msg1", "remote1", null, 5.0));
    }

    @Test
    @DisplayName("test_unregister_collector - selective and full cleanup")
    void testUnregisterCollector() {
        replySub.activate();

        replySub.registerCollector("msg1", "remote1", null, 5.0);
        replySub.registerCollector("msg2", "remote2", null, 5.0);
        assertEquals(2, replySub.getCollectors().size());

        // Unregister one
        replySub.unregisterCollector("msg1", "remote1", null);
        assertEquals(1, replySub.getCollectors().size());

        // Unregister all
        replySub.unregisterCollector(null, null, null);
        assertEquals(0, replySub.getCollectors().size());
    }

    @Test
    @DisplayName("test_deactivate_cleans_collectors - deactivation cleans up")
    void testDeactivateCleansCollectors() {
        replySub.activate();
        replySub.registerCollector("msg1", "remote1", null, 5.0);
        replySub.registerCollector("msg2", "remote2", null, 5.0);
        assertEquals(2, replySub.getCollectors().size());

        replySub.deactivate().join();

        assertFalse(replySub.isActive());
        assertEquals(0, replySub.getCollectors().size());
    }

    @Test
    @DisplayName("test_make_key_normalization - empty requestId normalized to null")
    void testMakeKeyNormalization() {
        CollectorKey key1 = replySub.makeKey("remote1", "msg1", null);
        CollectorKey key2 = replySub.makeKey("remote1", "msg1", "");
        CollectorKey key3 = replySub.makeKey("remote1", "msg1", "req1");

        assertEquals(key1, key2, "null and empty requestId should produce same key");
        assertNotEquals(key1, key3, "null and non-empty requestId should be different");
    }

    @Test
    @DisplayName("test_default_topic_from_config - topic derived from RunnerConfig")
    void testDefaultTopicFromConfig() {
        ReplyTopicSubscription sub = new ReplyTopicSubscription(fakeMQ, null);
        RunnerConfig config = RunnerConfig.getRunnerConfig();
        String expectedTopic = config.replyTopicTemplate()
            .replace("{instance_id}", config.getInstanceId());
        assertEquals(expectedTopic, sub.getTopic());
    }
}

