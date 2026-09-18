/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.MessageQueueConfig;
import com.openjiuwen.core.runner.MessageQueueType;
import com.openjiuwen.core.runner.mq.MessageQueueBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code MessageQueueFactory} behavior in
 * {@code openjiuwen/core/runner/drunner/dmessage_queue/message_queue_factory.py}.
 */
class MessageQueueFactoryTest {

    @Test
    void createReturnsFakeMessageQueueForFakeType() {
        MessageQueueConfig config = MessageQueueConfig.builder()
                .type(MessageQueueType.FAKE.getValue())
                .build();

        MessageQueueBase queue = MessageQueueFactory.create(config);

        assertThat(queue).isInstanceOf(FakeMessageQueue.class);
    }

    @Test
    void createLowercasesMessageQueueTypeBeforeDispatch() {
        MessageQueueConfig config = MessageQueueConfig.builder()
                .type("FAKE")
                .build();

        MessageQueueBase queue = MessageQueueFactory.create(config);

        assertThat(queue).isInstanceOf(FakeMessageQueue.class);
    }

    @Test
    void createUnknownTypeRaisesInitiationError() {
        MessageQueueConfig config = MessageQueueConfig.builder()
                .type("unknown")
                .build();

        assertThatThrownBy(() -> MessageQueueFactory.create(config))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.MESSAGE_QUEUE_INITIATION_ERROR);
    }
}
