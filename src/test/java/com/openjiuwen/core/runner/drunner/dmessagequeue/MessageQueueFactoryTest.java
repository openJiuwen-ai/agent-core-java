// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.runner.drunner.dmessagequeue;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.MessageQueueBase;
import com.openjiuwen.core.runner.MessageQueueConfig;
import com.openjiuwen.core.runner.MessageQueueType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 MessageQueueFactory 消息队列工厂。
 * 
 * 对应Python: test_message_queue_factory.py
 */
class MessageQueueFactoryTest {

    @Nested
    @DisplayName("测试 MessageQueueFactory.create 方法")
    class TestMessageQueueFactoryCreate {

        @Test
        @DisplayName("type 为 'fake' 时返回 FakeMQ 实例")
        void testCreateFakeMqWithTypeFake() {
            MessageQueueConfig config = new MessageQueueConfig(MessageQueueType.FAKE.getValue(), null);
            MessageQueueBase mq = MessageQueueFactory.create(config);
            assertInstanceOf(FakeMQ.class, mq);
        }

        @Test
        @DisplayName("type 匹配不区分大小写")
        void testCreateFakeMqCaseInsensitive() {
            MessageQueueConfig config = new MessageQueueConfig("FAKE", null);
            MessageQueueBase mq = MessageQueueFactory.create(config);
            assertInstanceOf(FakeMQ.class, mq);
        }

        @Test
        @DisplayName("type 为未知类型时抛出 MESSAGE_QUEUE_INIT_ERROR")
        void testCreateUnknownTypeRaisesException() {
            MessageQueueConfig config = new MessageQueueConfig("unknown_type", null);
            BaseError error = assertThrows(BaseError.class, () -> MessageQueueFactory.create(config));
            assertEquals(StatusCode.MESSAGE_QUEUE_INIT_ERROR.getCode(), error.getCode());
        }
    }

    @Nested
    @DisplayName("测试 FakeMQ 创建后的基本功能验证")
    class TestMessageQueueFactoryFakeMQIntegration {

        @Test
        @DisplayName("创建的 FakeMQ 可以正常 start 和 stop")
        void testFakeMqCanStartAndStop() {
            MessageQueueConfig config = new MessageQueueConfig(MessageQueueType.FAKE.getValue(), null);
            MessageQueueBase mq = MessageQueueFactory.create(config);

            mq.start();
            assertInstanceOf(FakeMQ.class, mq);
            assertTrue(((FakeMQ) mq).isRunning());
        }

        @Test
        @DisplayName("创建的 FakeMQ 启动后可以订阅")
        void testFakeMqCanSubscribeAfterStart() {
            MessageQueueConfig config = new MessageQueueConfig(MessageQueueType.FAKE.getValue(), null);
            FakeMQ mq = (FakeMQ) MessageQueueFactory.create(config);

            mq.start();
            try {
                FakeSubscription sub = mq.subscribe("test_topic");
                assertNotNull(sub);
                assertEquals("test_topic", sub.getTopic());
            } finally {
                mq.stop().join();
            }
        }
    }
}

