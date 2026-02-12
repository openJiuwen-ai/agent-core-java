// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.runner.drunner.dmessagequeue;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.AsyncMessageHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 FakeMQ 和 FakeSubscription。
 * 
 * 对应Python: test_message_queue_fake.py
 */
class FakeMQTest {

    @Nested
    @DisplayName("测试 FakeMQ 生命周期管理")
    class TestFakeMQLifecycle {

        @Test
        @DisplayName("测试完整的 start/stop 生命周期")
        void testStartStopLifecycle() {
            FakeMQ mq = new FakeMQ();
            assertFalse(mq.isRunning());

            mq.start();
            assertTrue(mq.isRunning());

            // 重复 start 幂等
            mq.start();
            assertTrue(mq.isRunning());

            mq.stop().join();
            assertFalse(mq.isRunning());

            // 重复 stop 幂等
            mq.stop().join();
            assertFalse(mq.isRunning());
        }

        @Test
        @DisplayName("stop() 调用时清理所有订阅")
        void testStopDeactivatesAllSubscriptions() {
            FakeMQ mq = new FakeMQ();
            mq.start();

            FakeSubscription sub1 = mq.subscribe("topic1");
            sub1.activate();
            FakeSubscription sub2 = mq.subscribe("topic2");
            sub2.activate();

            assertEquals(2, mq.getTopics().size());

            mq.stop().join();

            assertEquals(0, mq.getTopics().size());
            assertFalse(sub1.isActive());
            assertFalse(sub2.isActive());
        }
    }

    @Nested
    @DisplayName("测试 FakeMQ 订阅管理")
    class TestFakeMQSubscription {

        @Test
        @DisplayName("未 start 时调用 subscribe() 抛出异常")
        void testSubscribeBeforeStartRaisesException() {
            FakeMQ mq = new FakeMQ();
            BaseError error = assertThrows(BaseError.class, () -> mq.subscribe("test_topic"));
            assertEquals(StatusCode.MESSAGE_QUEUE_NOT_RUNNING.getCode(), error.getCode());
        }

        @Test
        @DisplayName("同一 topic 可订阅多次（支持多消费者）")
        void testSubscribeMultipleTimesSameTopic() {
            FakeMQ mq = new FakeMQ();
            mq.start();

            FakeSubscription sub1 = mq.subscribe("shared_topic");
            FakeSubscription sub2 = mq.subscribe("shared_topic");

            assertEquals(2, mq.getTopics().get("shared_topic").size());
            assertTrue(mq.getTopics().get("shared_topic").contains(sub1));
            assertTrue(mq.getTopics().get("shared_topic").contains(sub2));
            assertEquals("shared_topic", sub1.getTopic());
        }

        @Test
        @DisplayName("unsubscribe() 停用该 topic 下所有订阅并移除")
        void testUnsubscribeRemovesTopic() {
            FakeMQ mq = new FakeMQ();
            mq.start();

            FakeSubscription sub = mq.subscribe("topic_to_remove");
            sub.activate();

            mq.unsubscribe("topic_to_remove").join();

            assertFalse(mq.getTopics().containsKey("topic_to_remove"));
            assertFalse(sub.isActive());
        }
    }

    @Nested
    @DisplayName("测试 FakeMQ 消息分发")
    class TestFakeMQMessageDistribution {

        @Test
        @DisplayName("produce_message() 将消息推送到订阅者")
        void testProduceMessageDeliversToSubscriber() throws Exception {
            FakeMQ mq = new FakeMQ();
            mq.start();
            List<Object> receivedMessages = Collections.synchronizedList(new ArrayList<>());

            AsyncMessageHandler handler = msg -> {
                receivedMessages.add(msg);
                return CompletableFuture.completedFuture(null);
            };

            FakeSubscription sub = mq.subscribe("delivery_topic");
            sub.setMessageHandler(handler);
            sub.activate();

            DmqRequestMessage msg = DmqRequestMessage.builder()
                    .messageId("test_123")
                    .payload(Map.of("data", "value"))
                    .build();
            mq.produceMessage("delivery_topic", msg).join();

            Thread.sleep(500);

            assertEquals(1, receivedMessages.size());
            assertInstanceOf(DmqRequestMessage.class, receivedMessages.getFirst());
            assertEquals("test_123", ((DmqRequestMessage) receivedMessages.getFirst()).getMessageId());

            mq.stop().join();
        }

        @Test
        @DisplayName("消息被推送到 topic 下所有订阅者")
        void testProduceMessageToMultipleSubscribers() throws Exception {
            FakeMQ mq = new FakeMQ();
            mq.start();

            List<Object> received1 = Collections.synchronizedList(new ArrayList<>());
            List<Object> received2 = Collections.synchronizedList(new ArrayList<>());

            AsyncMessageHandler handler1 = msg -> {
                received1.add(msg);
                return CompletableFuture.completedFuture(null);
            };

            AsyncMessageHandler handler2 = msg -> {
                received2.add(msg);
                return CompletableFuture.completedFuture(null);
            };

            FakeSubscription sub1 = mq.subscribe("multi_sub_topic");
            sub1.setMessageHandler(handler1);
            sub1.activate();

            FakeSubscription sub2 = mq.subscribe("multi_sub_topic");
            sub2.setMessageHandler(handler2);
            sub2.activate();

            DmqRequestMessage msg = DmqRequestMessage.builder()
                    .messageId("broadcast")
                    .payload(Map.of("key", "val"))
                    .build();
            mq.produceMessage("multi_sub_topic", msg).join();

            Thread.sleep(500);

            assertEquals(1, received1.size());
            assertEquals(1, received2.size());
            assertEquals("broadcast", ((DmqRequestMessage) received1.getFirst()).getMessageId());
            assertEquals("broadcast", ((DmqRequestMessage) received2.getFirst()).getMessageId());

            mq.stop().join();
        }
    }

    @Nested
    @DisplayName("测试 FakeSubscription")
    class TestFakeSubscription {

        @Test
        @DisplayName("测试 activate/deactivate 生命周期")
        void testActivateDeactivateLifecycle() {
            FakeSubscription sub = new FakeSubscription("test_topic");
            assertFalse(sub.isActive());

            sub.activate();
            assertTrue(sub.isActive());

            sub.deactivate().join();
            assertFalse(sub.isActive());
        }

        @Test
        @DisplayName("激活状态时消息入队")
        void testPushWhenActiveEnqueues() {
            FakeSubscription sub = new FakeSubscription("test_topic");
            sub.activate();

            byte[] testData = "{\"test\": \"data\"}".getBytes();
            sub.push(testData).join();

            assertEquals(1, sub.getQueueSize());

            sub.deactivate().join();
        }

        @Test
        @DisplayName("未激活状态时消息被丢弃")
        void testPushWhenInactiveDiscards() {
            FakeSubscription sub = new FakeSubscription("test_topic");
            // 不激活

            byte[] testData = "{\"test\": \"data\"}".getBytes();
            sub.push(testData).join();

            assertEquals(0, sub.getQueueSize());
        }

        @Test
        @DisplayName("消费循环正确处理消息")
        void testConsumeLoopProcessesMessages() throws Exception {
            FakeSubscription sub = new FakeSubscription("test_topic");
            List<Object> processed = Collections.synchronizedList(new ArrayList<>());

            AsyncMessageHandler handler = msg -> {
                processed.add(msg);
                return CompletableFuture.completedFuture(null);
            };

            sub.setMessageHandler(handler);
            sub.activate();

            DmqResponseMessage msg = DmqResponseMessage.builder()
                    .messageId("consume_test")
                    .payload("test_payload")
                    .build();
            byte[] serialized = MessageSerializer.serializeMessage(msg);
            sub.push(serialized).join();

            Thread.sleep(500);

            assertEquals(1, processed.size());
            assertInstanceOf(DmqResponseMessage.class, processed.getFirst());
            assertEquals("consume_test", ((DmqResponseMessage) processed.getFirst()).getMessageId());

            sub.deactivate().join();
        }
    }
}

